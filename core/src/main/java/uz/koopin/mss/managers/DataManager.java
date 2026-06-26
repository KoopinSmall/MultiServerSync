package uz.koopin.mss.managers;

import redis.clients.jedis.DefaultJedisClientConfig;
import redis.clients.jedis.HostAndPort;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import uz.koopin.mss.storage.RedisCredentials;

import java.util.Map;

public class DataManager {

    private final RedisCredentials credentials;
    private final JedisPool jedisPool;

    public DataManager(RedisCredentials credentials) {
        this.jedisPool = new JedisPool(
                HostAndPort.from(credentials.host() + ":" + credentials.port()),
                DefaultJedisClientConfig.builder()
                        .password(credentials.password())
                        .build()
        );

        this.credentials = credentials;
    }

    public void setData(String path, String playerName, Map<String, String> fields) {
        try (Jedis jedis = jedisPool.getResource()) {
            String key = buildKey(path, playerName);
            jedis.hset(key, fields);
        }
    }

    public void setData(String path, String playerName, Map<String, String> fields, int ttl) {
        try (Jedis jedis = jedisPool.getResource()) {
            String key = buildKey(path, playerName);
            jedis.hset(key, fields);
            jedis.expire(key, ttl);
        }
    }

    public Map<String, String> getData(String path, String playerName) {
        try (Jedis jedis = jedisPool.getResource()) {
            String key = buildKey(path, playerName);
            return jedis.hgetAll(key); // возвращаем всю мапу
        }
    }

    public String getField(String path, String playerName, String field) {
        try (Jedis jedis = jedisPool.getResource()) {
            String key = buildKey(path, playerName);
            return jedis.hget(key, field);
        }
    }

    public void deleteField(String path, String playerName, String field) {
        try (Jedis jedis = jedisPool.getResource()) {
            String key = buildKey(path, playerName);
            jedis.hdel(key, field);
        }
    }

    public void deleteKey(String path, String playerName) {
        try (Jedis jedis = jedisPool.getResource()) {
            String key = buildKey(path, playerName);
            jedis.del(key);
        }
    }

    private String buildKey(String path, String playerName) {
        return "mss:" + path + ":" + this.credentials.project() + ":" + playerName;
    }
}