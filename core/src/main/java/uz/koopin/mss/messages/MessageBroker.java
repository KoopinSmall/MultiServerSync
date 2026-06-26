package uz.koopin.mss.messages;

import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPubSub;

import java.util.function.Consumer;
import java.util.concurrent.CompletableFuture;

public class MessageBroker {
    private final JedisPool jedisPool;
    private static String CHANNEL;

    public MessageBroker(JedisPool jedisPool, String projectName) {
        CHANNEL = "mss:server-events:" + projectName;

        this.jedisPool = jedisPool;
    }

    public void publishMessage(ServerMessage message) {
        try (Jedis jedis = jedisPool.getResource()) {
            jedis.publish(CHANNEL, message.toJson());
        }
    }

    public void subscribeToMessages(Consumer<String> messageHandler) {
        CompletableFuture.runAsync(() -> {
            try (Jedis jedis = jedisPool.getResource()) {
                jedis.subscribe(new JedisPubSub() {
                    @Override
                    public void onMessage(String channel, String message) {
                        messageHandler.accept(message);
                    }
                }, CHANNEL);
            }
        });
    }
}