package uz.koopin.mss.sync.internal;

import com.google.gson.Gson;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import redis.clients.jedis.DefaultJedisClientConfig;
import redis.clients.jedis.HostAndPort;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPubSub;
import redis.clients.jedis.exceptions.JedisConnectionException;

import uz.koopin.mss.storage.RedisCredentials;
import uz.koopin.mss.sync.ReplyablePacket;
import uz.koopin.mss.sync.SyncBus;
import uz.koopin.mss.sync.SyncBusBuilder;
import uz.koopin.mss.sync.SyncPacket;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public final class SyncBusImpl implements SyncBus {

    private static final Logger LOG = LoggerFactory.getLogger(SyncBusImpl.class);

    private static final long BACKOFF_INITIAL_MS = 1_000L;
    private static final long BACKOFF_MAX_MS = 30_000L;

    private final JedisPool jedisPool;
    private final String channel;
    private final String origin;
    private final boolean autoReconnect;
    private final PacketCodec codec;
    private final RequestRegistry requests = new RequestRegistry();

    private final ExecutorService publishExec;
    private final Thread subscribeThread;
    private final JedisPubSub pubSub;

    private volatile boolean closed = false;

    public SyncBusImpl(SyncBusBuilder b) {
        RedisCredentials creds = b.redis();
        this.jedisPool = new JedisPool(
            HostAndPort.from(creds.host() + ":" + creds.port()),
            DefaultJedisClientConfig.builder()
                .password(creds.password() != null && !creds.password().isEmpty() ? creds.password() : null)
                .build()
        );
        this.channel = b.channel();
        this.origin = b.origin();
        this.autoReconnect = b.autoReconnect();

        Gson gson = b.gson() != null ? b.gson() : new Gson();
        this.codec = new PacketCodec(gson);

        this.publishExec = Executors.newFixedThreadPool(b.publishThreads(), namedDaemon("SyncBus-Pub-" + origin));

        this.pubSub = new JedisPubSub() {
            @Override
            public void onMessage(String ch, String message) {
                if (!channel.equals(ch)) return;
                handleIncoming(message);
            }
        };

        this.subscribeThread = namedDaemon("SyncBus-Sub-" + origin).newThread(this::subscribeLoop);
        this.subscribeThread.start();

        LOG.info("SyncBus[{}] started on channel '{}'", origin, channel);
    }

    @Override
    public void send(SyncPacket packet) {
        publishAsync(codec.encode(packet, origin, null, false));
    }

    @Override
    public <R extends SyncPacket> CompletableFuture<R> request(ReplyablePacket<R> packet) {
        String corrId = UUID.randomUUID().toString();
        CompletableFuture<SyncPacket> raw = requests.register(corrId);
        publishAsync(codec.encode(packet, origin, corrId, false));

        @SuppressWarnings("unchecked")
        CompletableFuture<R> typed = (CompletableFuture<R>) raw;
        return typed;
    }

    @Override
    public void close() {
        if (closed) return;
        closed = true;
        LOG.info("SyncBus[{}] closing…", origin);

        try {
            pubSub.unsubscribe();
        } catch (Exception ignored) {
            // может быть не подписан
        }

        subscribeThread.interrupt();
        publishExec.shutdown();
        try {
            if (!publishExec.awaitTermination(5, TimeUnit.SECONDS)) {
                publishExec.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            publishExec.shutdownNow();
        }

        try {
            jedisPool.close();
        } catch (Exception e) {
            LOG.warn("Error closing jedis pool", e);
        }
    }

    public int pendingRequests() {
        return requests.pendingSize();
    }

    private void publishAsync(String message) {
        if (closed) {
            LOG.warn("SyncBus[{}] is closed; dropping publish", origin);
            return;
        }
        publishExec.submit(() -> {
            try (Jedis jedis = jedisPool.getResource()) {
                jedis.publish(channel, message);
            } catch (Exception e) {
                LOG.error("SyncBus[{}] publish failed", origin, e);
            }
        });
    }

    private void subscribeLoop() {
        long backoff = BACKOFF_INITIAL_MS;
        while (!closed) {
            try (Jedis jedis = jedisPool.getResource()) {
                jedis.subscribe(pubSub, channel);
                backoff = BACKOFF_INITIAL_MS;
                if (closed) break;
                return;
            } catch (JedisConnectionException e) {
                if (closed) return;
                if (!autoReconnect) {
                    LOG.error("SyncBus[{}] subscribe failed, auto-reconnect disabled", origin, e);
                    return;
                }
                LOG.warn("SyncBus[{}] subscribe lost, reconnecting in {}ms", origin, backoff, e);
                sleepQuiet(backoff);
                backoff = Math.min(backoff * 2, BACKOFF_MAX_MS);
            } catch (Exception e) {
                if (closed) return;
                LOG.error("SyncBus[{}] unexpected subscribe error", origin, e);
                sleepQuiet(backoff);
                backoff = Math.min(backoff * 2, BACKOFF_MAX_MS);
            }
        }
    }

    private void handleIncoming(String message) {
        PacketCodec.Decoded decoded;
        try {
            decoded = codec.decode(message);
        } catch (Exception e) {
            LOG.error("SyncBus[{}] failed to decode message: {}", origin, message, e);
            return;
        }

        if (origin.equals(decoded.origin())) {
            return;
        }

        try {
            if (decoded.isReply()) {
                if (decoded.corrId() != null) {
                    boolean delivered = requests.complete(decoded.corrId(), decoded.packet());
                    if (!delivered) {
                        LOG.debug("SyncBus[{}] reply for unknown/expired corrId={}", origin, decoded.corrId());
                    }
                }
                return;
            }

            if (decoded.packet() instanceof ReplyablePacket<?> request && decoded.corrId() != null) {
                SyncPacket reply = request.handle();
                if (reply != null) {
                    publishAsync(codec.encode(reply, origin, decoded.corrId(), true));
                }
                return;
            }

            decoded.packet().onReceive();
        } catch (Exception e) {
            LOG.error("SyncBus[{}] handler threw for packet {}", origin, decoded.packet().getClass().getName(), e);
        }
    }

    private static void sleepQuiet(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static ThreadFactory namedDaemon(String baseName) {
        AtomicInteger counter = new AtomicInteger();
        return runnable -> {
            Thread t = new Thread(runnable, baseName + "-" + counter.incrementAndGet());
            t.setDaemon(true);
            return t;
        };
    }
}
