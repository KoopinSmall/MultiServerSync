package uz.koopin.mss.config;

import java.util.Locale;
import java.util.Optional;

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
