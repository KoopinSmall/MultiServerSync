package uz.koopin.mss.managers;

import redis.clients.jedis.Jedis;
import uz.koopin.mss.storage.RedisProvider;

import java.util.Map;

public class DataManager {

    private final RedisProvider redis;

    public DataManager(RedisProvider redis) {
        this.redis = redis;
    }

    public void setData(String path, String playerName, Map<String, String> fields) {
        try (Jedis jedis = redis.getResource()) {
            String key = buildKey(path, playerName);
            jedis.hset(key, fields);
        }
    }

    public void setData(String path, String playerName, Map<String, String> fields, int ttl) {
        try (Jedis jedis = redis.getResource()) {
            String key = buildKey(path, playerName);
            jedis.hset(key, fields);
            jedis.expire(key, ttl);
        }
    }

    public Map<String, String> getData(String path, String playerName) {
        try (Jedis jedis = redis.getResource()) {
            String key = buildKey(path, playerName);
            return jedis.hgetAll(key);
        }
    }

    public String getField(String path, String playerName, String field) {
        try (Jedis jedis = redis.getResource()) {
            String key = buildKey(path, playerName);
            return jedis.hget(key, field);
        }
    }

    public void deleteField(String path, String playerName, String field) {
        try (Jedis jedis = redis.getResource()) {
            String key = buildKey(path, playerName);
            jedis.hdel(key, field);
        }
    }

    public void deleteKey(String path, String playerName) {
        try (Jedis jedis = redis.getResource()) {
            String key = buildKey(path, playerName);
            jedis.del(key);
        }
    }

    private String buildKey(String path, String playerName) {
        return "mss:" + path + ":" + redis.project() + ":" + playerName;
    }
}
