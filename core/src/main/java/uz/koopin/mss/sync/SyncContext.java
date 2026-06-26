package uz.koopin.mss.sync;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public final class SyncContext {

    private static final SyncContext INSTANCE = new SyncContext();

    private final Map<Class<?>, Object> services = new ConcurrentHashMap<>();

    private SyncContext() {}

    public static SyncContext current() {
        return INSTANCE;
    }

    public static <T> void put(Class<T> type, T instance) {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(instance, "instance");
        INSTANCE.services.put(type, instance);
    }

    public static void remove(Class<?> type) {
        INSTANCE.services.remove(type);
    }

    public static void clear() {
        INSTANCE.services.clear();
    }

    public static <T> Optional<T> lookup(Class<T> type) {
        return INSTANCE.find(type);
    }

    public <T> Optional<T> find(Class<T> type) {
        Object value = services.get(type);
        return value == null ? Optional.empty() : Optional.of(type.cast(value));
    }

    public <T> T get(Class<T> type) {
        return find(type).orElseThrow(() ->
            new IllegalStateException("SyncContext has no service registered for " + type.getName()));
    }
}
