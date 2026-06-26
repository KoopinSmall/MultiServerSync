package uz.koopin.mss.config;

import java.util.Locale;
import java.util.Optional;

/**
 * Resolves configuration overrides from JVM system properties and environment
 * variables, so any value can be injected per-instance at startup without baking
 * a config file into the image. This is what makes the plugins safe to scale
 * horizontally under Docker/Kubernetes, where every replica shares one image but
 * needs its own identity.
 *
 * <p>For a dotted key such as {@code redis.host}, resolution order is:
 * <ol>
 *   <li>system property — {@code -Dmss.redis.host=...}</li>
 *   <li>environment variable — {@code MSS_REDIS_HOST}</li>
 *   <li>the supplied fallback (typically the value from config.yml)</li>
 * </ol>
 */
public final class ConfigOverrides {

    private static final String PROPERTY_PREFIX = "mss.";
    private static final String ENV_PREFIX = "MSS_";

    private ConfigOverrides() {
    }

    public static Optional<String> find(String key) {
        String property = System.getProperty(PROPERTY_PREFIX + key);
        if (property != null && !property.isBlank()) {
            return Optional.of(property.trim());
        }
        String env = System.getenv(ENV_PREFIX + toEnvKey(key));
        if (env != null && !env.isBlank()) {
            return Optional.of(env.trim());
        }
        return Optional.empty();
    }

    public static String getString(String key, String fallback) {
        return find(key).orElse(fallback);
    }

    public static int getInt(String key, int fallback) {
        Optional<String> value = find(key);
        if (value.isEmpty()) {
            return fallback;
        }
        try {
            return Integer.parseInt(value.get());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    public static boolean getBoolean(String key, boolean fallback) {
        return find(key).map(Boolean::parseBoolean).orElse(fallback);
    }

    private static String toEnvKey(String key) {
        return key.toUpperCase(Locale.ROOT).replace('.', '_').replace('-', '_');
    }
}
