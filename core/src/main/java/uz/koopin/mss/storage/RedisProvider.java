package uz.koopin.mss.storage;

import redis.clients.jedis.DefaultJedisClientConfig;
import redis.clients.jedis.HostAndPort;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

public final class RedisProvider implements AutoCloseable {

    private final RedisCredentials credentials;
    private final JedisPool pool;

    public RedisProvider(RedisCredentials credentials) {
        this.credentials = credentials;
        this.pool = new JedisPool(
                HostAndPort.from(credentials.host() + ":" + credentials.port()),
                DefaultJedisClientConfig.builder()
                        .password(credentials.password())
                        .build()
        );
    }

    public Jedis getResource() {
        return pool.getResource();
    }

    public JedisPool pool() {
        return pool;
    }

    public RedisCredentials credentials() {
        return credentials;
    }

    public String project() {
        return credentials.project();
    }

    @Override
    public void close() {
        pool.close();
    }
}
