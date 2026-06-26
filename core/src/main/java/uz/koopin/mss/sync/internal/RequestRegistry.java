package uz.koopin.mss.sync.internal;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

import uz.koopin.mss.sync.SyncPacket;

public final class RequestRegistry {

    private final Map<String, CompletableFuture<SyncPacket>> pending = new ConcurrentHashMap<>();

    public CompletableFuture<SyncPacket> register(String corrId) {
        CompletableFuture<SyncPacket> future = new CompletableFuture<>();
        pending.put(corrId, future);
        future.whenComplete((result, error) -> pending.remove(corrId));
        return future;
    }

    public boolean complete(String corrId, SyncPacket reply) {
        CompletableFuture<SyncPacket> future = pending.remove(corrId);
        if (future == null) return false;
        return future.complete(reply);
    }

    public int pendingSize() {
        return pending.size();
    }
}
