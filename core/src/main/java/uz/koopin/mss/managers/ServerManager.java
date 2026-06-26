package uz.koopin.mss.managers;

import redis.clients.jedis.DefaultJedisClientConfig;
import redis.clients.jedis.HostAndPort;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import uz.koopin.mss.messages.MessageBroker;
import uz.koopin.mss.messages.ServerRegisteredMessage;
import uz.koopin.mss.messages.ServerUnregisteredMessage;
import uz.koopin.mss.messages.ServerUpdateMessage;
import uz.koopin.mss.storage.RedisCredentials;
import uz.koopin.mss.storage.SyncServer;

import java.net.InetSocketAddress;
import java.util.*;

public class ServerManager {
    private final JedisPool jedisPool;
    private final MessageBroker messageBroker;
    private final RedisCredentials credentials;

    public ServerManager(RedisCredentials credentials) {
        this.credentials = credentials;
        this.jedisPool = new JedisPool(
                HostAndPort.from(credentials.host() + ":" + credentials.port()),
                DefaultJedisClientConfig.builder()
                        .password(credentials.password())
                        .build()
        );

        this.messageBroker = new MessageBroker(jedisPool, credentials.project());
    }

    public void registerServer(String serverName, InetSocketAddress address, boolean whitelist) {
        try (Jedis jedis = jedisPool.getResource()) {
            Map<String, String> serverData = Map.of(
                    "project", this.credentials.project(),
                    "address", address.getAddress().getHostAddress() + ":" + address.getPort(),
                    "whitelist", String.valueOf(whitelist)
            );
            jedis.hset("mss:server:" + serverName, serverData);

            // Отправляем сообщение о регистрации
            messageBroker.publishMessage(new ServerRegisteredMessage(this.credentials.project(), serverName, address, whitelist));
        }
    }

    public void unregisterServer(String serverName) {
        SyncServer server = getSyncServer(serverName);
        if (server != null) {
            try (Jedis jedis = jedisPool.getResource()) {
                jedis.del("mss:server:" + serverName);

                // Отправляем сообщение о снятии с регистрации
                messageBroker.publishMessage(new ServerUnregisteredMessage(
                        this.credentials.project(),
                        serverName,
                        server.getAddress(),
                        server.isWhitelisted()
                ));
            }
        }
    }

    /**
     * Обновляет данные сервера в Redis на основе предоставленного объекта SyncServer.
     * Этот метод заменяет старый метод updateServerField.
     *
     * @param server Объект SyncServer с обновленными данными.
     */
    public void updateSyncServer(SyncServer server) {
        try (Jedis jedis = jedisPool.getResource()) {
            String serverName = server.getServerName();
            String redisKey = "mss:server:" + serverName;

            // Проверяем, существует ли сервер, прежде чем обновлять
            if (jedis.exists(redisKey)) {
                // Создаем Map с новыми данными сервера
                Map<String, String> serverData = Map.of(
                        "project", server.getProjectName(),
                        "address", server.getAddress().getAddress().getHostAddress() + ":" + server.getAddress().getPort(),
                        "whitelist", String.valueOf(server.isWhitelisted())
                );

                // Обновляем все поля сервера в Redis
                jedis.hset(redisKey, serverData);

                // Отправляем сообщение об обновлении.
                // Предполагается, что у ServerUpdateMessage есть конструктор,
                // принимающий (serverName, address, whitelist) для оповещения об общем обновлении.
                messageBroker.publishMessage(new ServerUpdateMessage(server.getProjectName(), server.getServerName(), server.getAddress(), server.isWhitelisted()));
            }
        }
    }

    public SyncServer getSyncServer(String serverName) {
        try (Jedis jedis = jedisPool.getResource()) {
            Map<String, String> serverData = jedis.hgetAll("mss:server:" + serverName);

            if (serverData.isEmpty()) {
                return null;
            }

            String projectName = serverData.get("project");
            String[] addressParts = serverData.get("address").split(":");
            InetSocketAddress address = new InetSocketAddress(addressParts[0], Integer.parseInt(addressParts[1]));

            boolean whitelist = Boolean.parseBoolean(serverData.get("whitelist"));

            return new SyncServer(projectName, serverName, address, whitelist);
        }
    }

    public List<SyncServer> getSyncServers() {
        List<SyncServer> serverList = new ArrayList<>();
        try (Jedis jedis = jedisPool.getResource()) {
            Set<String> serverKeys = jedis.keys("mss:server:*");

            for (String key : serverKeys) {
                Map<String, String> serverData = jedis.hgetAll(key);

                String projectName = serverData.get("project");
                String serverName = key.substring("mss:server:".length());

                String[] addressParts = serverData.get("address").split(":");
                InetSocketAddress address = new InetSocketAddress(addressParts[0], Integer.parseInt(addressParts[1]));

                boolean whitelist = Boolean.parseBoolean(serverData.get("whitelist"));

                serverList.add(new SyncServer(projectName, serverName, address, whitelist));
            }
        }

        return serverList;
    }

    /**
     * Sets the online player count for a specific server within a project.
     * This uses a Redis Hash with the key "mss:online:{project}".
     * The field is the server name and the value is the player count.
     * @param server The server name (e.g., "lobby-1", "bedwars-4").
     * @param count The number of players currently on the server.
     */
    public void setServerOnline(String server, int count) {
        try (Jedis jedis = jedisPool.getResource()) {
            String key = "mss:online:" + this.credentials.project();
            jedis.hset(key, server, String.valueOf(count));
        }
    }

    /**
     * Gets the online player count for a specific server within a project.
     * @param server The name of the server.
     * @return The number of online players as an integer. Returns 0 if the server is not found or the value is invalid.
     */
    public int getServerOnline(String server) {
        try (Jedis jedis = jedisPool.getResource()) {
            String key = "mss:online:" + this.credentials.project();
            String onlineCountStr = jedis.hget(key, server);

            // If the server doesn't exist in the hash, return 0.
            if (onlineCountStr == null) {
                return 0;
            }

            // Try to parse the string value to an integer.
            try {
                return Integer.parseInt(onlineCountStr);
            } catch (NumberFormatException e) {
                // If the value stored in Redis is not a valid number, return 0.
                System.err.println("Invalid number format for server online count: " + onlineCountStr);
                return 0;
            }
        }
    }

    /**
     * Calculates the total number of players online for an entire project by summing
     * the counts from all servers in that project.
     * @return The total number of players online in the project.
     */
    public int getProjectOnline() {
        try (Jedis jedis = jedisPool.getResource()) {
            String key = "mss:online:" + this.credentials.project();
            // hgetAll returns a Map of all fields and values in the hash.
            Map<String, String> serverCounts = jedis.hgetAll(key);
            int totalOnline = 0;
            // Iterate over the values (player counts) and sum them up.
            for (String countStr : serverCounts.values()) {
                try {
                    totalOnline += Integer.parseInt(countStr);
                } catch (NumberFormatException e) {
                    // Silently ignore if a value is not a valid number.
                }
            }
            return totalOnline;
        }
    }

    /**
     * Retrieves a map of all servers and their respective online counts for a given project.
     * @return A Map where the key is the server name and the value is the online count as a String.
     */
    public Map<String, String> getAllServersOnline() {
        try (Jedis jedis = jedisPool.getResource()) {
            return jedis.hgetAll("mss:online:" + this.credentials.project());
        }
    }

    /**
     * Removes a server's online data. This is useful when a server shuts down.
     * @param server The name of the server to remove.
     */
    public void removeServer(String server) {
        try (Jedis jedis = jedisPool.getResource()) {
            jedis.hdel("mss:online:" + this.credentials.project(), server);
        }
    }

    public MessageBroker getMessageBroker() {
        return messageBroker;
    }
}
