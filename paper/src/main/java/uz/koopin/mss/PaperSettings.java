package uz.koopin.mss;

import org.bukkit.configuration.file.FileConfiguration;
import uz.koopin.mss.config.ConfigOverrides;
import uz.koopin.mss.storage.RedisCredentials;

public class PaperSettings {

    public static String PROJECT_NAME;
    public static boolean SERVER_REGISTER;
    public static String SERVER_HOST;
    public static int SERVER_PORT;

    private static RedisCredentials redis;

    public static void init(FileConfiguration config) {
        PROJECT_NAME = ConfigOverrides.getString("project", config.getString("project", "koopin"));
        SERVER_REGISTER = ConfigOverrides.getBoolean("server.register", config.getBoolean("server.register", true));
        SERVER_HOST = ConfigOverrides.getString("server.host", config.getString("server.host", "auto"));
        SERVER_PORT = ConfigOverrides.getInt("server.port", config.getInt("server.port", 0));

        String host = ConfigOverrides.getString("redis.host", config.getString("redis.host", "localhost"));
        int port = ConfigOverrides.getInt("redis.port", config.getInt("redis.port", 6379));
        String password = ConfigOverrides.getString("redis.password", config.getString("redis.password", ""));

        redis = new RedisCredentials(PROJECT_NAME, host, port, password);
    }

    public static RedisCredentials redis() {
        return redis;
    }
}
