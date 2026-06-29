package uz.koopin.mss;

import lombok.Getter;
import uz.koopin.mss.config.ConfigOverrides;
import uz.koopin.mss.storage.RedisCredentials;
import uz.koopin.mss.storage.ServerGroup;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class BungeeSettings {

    public static String PROJECT_NAME;
    public static String PROXY_NAME;
    @Getter
    private static RedisCredentials redis;
    @Getter
    private static List<ServerGroup> serverGroups;

    private BungeeSettings() {
    }

    public static void init(Map<String, Object> config) {
        PROJECT_NAME = ConfigOverrides.getString("project", string(config, "project", "koopin"));
        PROXY_NAME = ConfigOverrides.getString("proxy.name", string(config, "proxy", "proxy-1"));

        Map<String, Object> redisSection = section(config, "redis");
        redis = new RedisCredentials(
                PROJECT_NAME,
                ConfigOverrides.getString("redis.host", string(redisSection, "host", "localhost")),
                ConfigOverrides.getInt("redis.port", integer(redisSection, "port", 6379)),
                ConfigOverrides.getString("redis.password", string(redisSection, "password", ""))
        );

        serverGroups = new ArrayList<>();
        section(config, "server-groups").forEach((name, value) ->
                serverGroups.add(new ServerGroup(name, toStringList(value))));
    }

    public static List<String> getHubServers() {
        return serverGroups.stream()
                .filter(group -> "hub".equals(group.name()))
                .flatMap(group -> group.servers().stream())
                .toList();
    }

    private static String string(Map<String, Object> map, String key, String fallback) {
        Object value = map.get(key);
        return value != null ? value.toString() : fallback;
    }

    private static int integer(Map<String, Object> map, String key, int fallback) {
        Object value = map.get(key);
        return value instanceof Number number ? number.intValue() : fallback;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> section(Map<String, Object> map, String key) {
        Object value = map.get(key);
        return value instanceof Map ? (Map<String, Object>) value : Map.of();
    }

    private static List<String> toStringList(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        return list.stream().map(String::valueOf).toList();
    }
}
