package uz.koopin.mss.sync;

import com.google.gson.Gson;

import java.time.Duration;
import java.util.Objects;

import uz.koopin.mss.storage.RedisCredentials;
import uz.koopin.mss.sync.internal.SyncBusImpl;

public final class SyncBusBuilder {

    private RedisCredentials redis;
    private String channel;
    private String origin;
    private int publishThreads = 8;
    private boolean autoReconnect = true;
    private long requestTimeoutMs = 10_000L;
    private Gson gson;

    SyncBusBuilder() { }

    public SyncBusBuilder redis(RedisCredentials redis) {
        this.redis = redis;
        return this;
    }

    public SyncBusBuilder channel(String channel) {
        this.channel = channel;
        return this;
    }

    public SyncBusBuilder origin(String origin) {
        this.origin = origin;
        return this;
    }

    public SyncBusBuilder publishThreads(int publishThreads) {
        if (publishThreads < 1) {
            throw new IllegalArgumentException("publishThreads must be >= 1, got " + publishThreads);
        }
        this.publishThreads = publishThreads;
        return this;
    }

    public SyncBusBuilder autoReconnect(boolean autoReconnect) {
        this.autoReconnect = autoReconnect;
        return this;
    }

    public SyncBusBuilder requestTimeout(Duration timeout) {
        if (timeout == null || timeout.isZero() || timeout.isNegative()) {
            throw new IllegalArgumentException("requestTimeout must be positive");
        }
        this.requestTimeoutMs = timeout.toMillis();
        return this;
    }

    public SyncBusBuilder gson(Gson gson) {
        this.gson = gson;
        return this;
    }

    public SyncBus build() {
        Objects.requireNonNull(redis, "redis credentials are required");
        Objects.requireNonNull(channel, "channel is required");
        Objects.requireNonNull(origin, "origin is required");
        return new SyncBusImpl(this);
    }

    public RedisCredentials redis() { return redis; }
    public String channel() { return channel; }
    public String origin() { return origin; }
    public int publishThreads() { return publishThreads; }
    public boolean autoReconnect() { return autoReconnect; }
    public long requestTimeoutMs() { return requestTimeoutMs; }
    public Gson gson() { return gson; }
}
