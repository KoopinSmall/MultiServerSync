package uz.koopin.mss.messages;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPubSub;
import redis.clients.jedis.exceptions.JedisConnectionException;

import java.util.function.Consumer;

public class MessageBroker {

    private static final Logger LOG = LoggerFactory.getLogger(MessageBroker.class);

    private static final long BACKOFF_INITIAL_MS = 1_000L;
    private static final long BACKOFF_MAX_MS = 30_000L;

    private final JedisPool jedisPool;
    private final String channel;

    private volatile boolean closed = false;
    private volatile JedisPubSub pubSub;

    public MessageBroker(JedisPool jedisPool, String projectName) {
        this.jedisPool = jedisPool;
        this.channel = "mss:server-events:" + projectName;
    }

    public void publishMessage(ServerMessage message) {
        try (Jedis jedis = jedisPool.getResource()) {
            jedis.publish(channel, message.toJson());
        }
    }

    public void subscribeToMessages(Consumer<String> messageHandler) {
        Thread thread = new Thread(() -> subscribeLoop(messageHandler), "MessageBroker-Sub-" + channel);
        thread.setDaemon(true);
        thread.start();
    }

    public void close() {
        closed = true;
        JedisPubSub current = this.pubSub;
        if (current != null) {
            try {
                current.unsubscribe();
            } catch (Exception ignored) {
            }
        }
    }

    private void subscribeLoop(Consumer<String> messageHandler) {
        long backoff = BACKOFF_INITIAL_MS;
        while (!closed) {
            try (Jedis jedis = jedisPool.getResource()) {
                JedisPubSub sub = new JedisPubSub() {
                    @Override
                    public void onMessage(String channel, String message) {
                        try {
                            messageHandler.accept(message);
                        } catch (Exception e) {
                            LOG.error("MessageBroker handler threw for message: {}", message, e);
                        }
                    }
                };
                this.pubSub = sub;
                jedis.subscribe(sub, channel);
                backoff = BACKOFF_INITIAL_MS;
                if (closed) return;
            } catch (JedisConnectionException e) {
                if (closed) return;
                LOG.warn("MessageBroker subscribe lost on '{}', reconnecting in {}ms", channel, backoff);
                sleepQuiet(backoff);
                backoff = Math.min(backoff * 2, BACKOFF_MAX_MS);
            } catch (Exception e) {
                if (closed) return;
                LOG.error("MessageBroker unexpected subscribe error on '{}'", channel, e);
                sleepQuiet(backoff);
                backoff = Math.min(backoff * 2, BACKOFF_MAX_MS);
            }
        }
    }

    private static void sleepQuiet(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
