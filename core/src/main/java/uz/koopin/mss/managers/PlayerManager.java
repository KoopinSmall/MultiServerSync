package uz.koopin.mss.managers;

import redis.clients.jedis.Jedis;
import uz.koopin.mss.storage.RedisProvider;
import uz.koopin.mss.storage.SyncUser;

import java.util.HashMap;
import java.util.Map;

public class PlayerManager {

    private final RedisProvider redis;

    public PlayerManager(RedisProvider redis) {
        this.redis = redis;
    }

    public void setPlayerLocation(String playerName, String proxy, String previousServer, String server) {
        try (Jedis jedis = redis.getResource()) {
            Map<String, String> data = new HashMap<>();
            data.put("project", redis.project());
            data.put("proxy", proxy);
            data.put("previousServer", previousServer);
            data.put("server", server);
            jedis.hset("mss:location:" + playerName, data);
        }
    }

    public SyncUser getPlayerLocation(String playerName) {
        try (Jedis jedis = redis.getResource()) {
            String key = "mss:location:" + playerName;
            String project = jedis.hget(key, "project");
            String proxy = jedis.hget(key, "proxy");
            String previousServer = jedis.hget(key, "previousServer");
            String server = jedis.hget(key, "server");

            if (proxy == null || server == null) return null;
            return new SyncUser(project, playerName, proxy, previousServer, server);
        }
    }

    public String getPlayerProject(String playerName) {
        try (Jedis jedis = redis.getResource()) {
            return jedis.hget("mss:location:" + playerName, "project");
        }
    }

    public String getPlayerPreviousServer(String playerName) {
        try (Jedis jedis = redis.getResource()) {
            return jedis.hget("mss:location:" + playerName, "previousServer");
        }
    }

    public String getPlayerServer(String playerName) {
        try (Jedis jedis = redis.getResource()) {
            return jedis.hget("mss:location:" + playerName, "server");
        }
    }

    public String getPlayerProxy(String playerName) {
        try (Jedis jedis = redis.getResource()) {
            return jedis.hget("mss:location:" + playerName, "proxy");
        }
    }

    public void removePlayer(String playerName) {
        try (Jedis jedis = redis.getResource()) {
            jedis.del("mss:location:" + playerName);
        }
    }
}
