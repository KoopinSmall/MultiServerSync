package uz.koopin.mss.managers;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import redis.clients.jedis.Jedis;
import uz.koopin.mss.messages.MessageBroker;
import uz.koopin.mss.storage.RedisProvider;
import uz.koopin.mss.storage.SyncServer;
import uz.koopin.mss.sync.SyncBus;
import uz.koopin.mss.sync.packets.ServerRegisterPacket;
import uz.koopin.mss.sync.packets.ServerUnregisterPacket;
import uz.koopin.mss.sync.packets.ServerUpdatePacket;

import java.net.InetSocketAddress;
import java.util.*;

public class ServerManager {

    private static final Logger LOG = LoggerFactory.getLogger(ServerManager.class);

    private final RedisProvider redis;
    private final MessageBroker messageBroker;
    private final SyncBus syncBus;

    public ServerManager(RedisProvider redis, String origin) {
        this.redis = redis;
        this.messageBroker = new MessageBroker(redis.pool(), redis.project());
        this.syncBus = SyncBus.builder()
                .redis(redis.credentials())
                .channel("mss:servers:" + redis.project())
                .origin(origin)
                .build();
    }

    public void registerServer(String serverName, InetSocketAddress address, boolean whitelist) {
        String host = address.getAddress().getHostAddress();
        try (Jedis jedis = redis.getResource()) {
            jedis.hset("mss:server:" + serverName, serverFields(redis.project(), host, address.getPort(), whitelist));
        }

        syncBus.send(new ServerRegisterPacket(redis.project(), serverName, host, address.getPort(), whitelist));
    }

    public void unregisterServer(String serverName) {
        SyncServer server = getSyncServer(serverName);
        if (server != null) {
            try (Jedis jedis = redis.getResource()) {
                jedis.del("mss:server:" + serverName);
            }

            syncBus.send(new ServerUnregisterPacket(redis.project(), serverName));
        }
    }

    public void updateSyncServer(SyncServer server) {
        try (Jedis jedis = redis.getResource()) {
            String redisKey = "mss:server:" + server.getServerName();
            if (!jedis.exists(redisKey)) {
                return;
            }

            String host = server.getAddress().getAddress().getHostAddress();
            jedis.hset(redisKey, serverFields(server.getProjectName(), host, server.getAddress().getPort(), server.isWhitelisted()));
            syncBus.send(new ServerUpdatePacket(
                    server.getProjectName(), server.getServerName(), host, server.getAddress().getPort(), server.isWhitelisted()));
        }
    }

    public SyncServer getSyncServer(String serverName) {
        try (Jedis jedis = redis.getResource()) {
            Map<String, String> serverData = jedis.hgetAll("mss:server:" + serverName);
            if (serverData.isEmpty()) {
                return null;
            }
            return toSyncServer(serverName, serverData);
        }
    }

    public List<SyncServer> getSyncServers() {
        List<SyncServer> serverList = new ArrayList<>();
        try (Jedis jedis = redis.getResource()) {
            for (String key : jedis.keys("mss:server:*")) {
                Map<String, String> serverData = jedis.hgetAll(key);
                if (serverData.isEmpty()) {
                    continue;
                }
                serverList.add(toSyncServer(key.substring("mss:server:".length()), serverData));
            }
        }
        return serverList;
    }

    public void setServerOnline(String server, int count) {
        try (Jedis jedis = redis.getResource()) {
            jedis.hset("mss:online:" + redis.project(), server, String.valueOf(count));
        }
    }

    public int getServerOnline(String server) {
        try (Jedis jedis = redis.getResource()) {
            String onlineCountStr = jedis.hget("mss:online:" + redis.project(), server);
            if (onlineCountStr == null) {
                return 0;
            }
            try {
                return Integer.parseInt(onlineCountStr);
            } catch (NumberFormatException e) {
                LOG.warn("Invalid online count for server {}: {}", server, onlineCountStr);
                return 0;
            }
        }
    }

    public int getProjectOnline() {
        try (Jedis jedis = redis.getResource()) {
            Map<String, String> serverCounts = jedis.hgetAll("mss:online:" + redis.project());
            int totalOnline = 0;
            for (String countStr : serverCounts.values()) {
                try {
                    totalOnline += Integer.parseInt(countStr);
                } catch (NumberFormatException ignored) {
                }
            }
            return totalOnline;
        }
    }

    public Map<String, String> getAllServersOnline() {
        try (Jedis jedis = redis.getResource()) {
            return jedis.hgetAll("mss:online:" + redis.project());
        }
    }

    public void removeServer(String server) {
        try (Jedis jedis = redis.getResource()) {
            jedis.hdel("mss:online:" + redis.project(), server);
        }
    }

    public MessageBroker getMessageBroker() {
        return messageBroker;
    }

    public SyncBus getSyncBus() {
        return syncBus;
    }

    public void close() {
        syncBus.close();
        messageBroker.close();
    }

    private static Map<String, String> serverFields(String project, String host, int port, boolean whitelist) {
        return Map.of(
                "project", project,
                "address", host + ":" + port,
                "whitelist", String.valueOf(whitelist)
        );
    }

    private static SyncServer toSyncServer(String serverName, Map<String, String> data) {
        return new SyncServer(
                data.get("project"),
                serverName,
                parseAddress(data.get("address")),
                Boolean.parseBoolean(data.get("whitelist"))
        );
    }

    private static InetSocketAddress parseAddress(String raw) {
        int split = raw.lastIndexOf(':');
        String host = raw.substring(0, split);
        int port = Integer.parseInt(raw.substring(split + 1));
        return new InetSocketAddress(host, port);
    }
}
