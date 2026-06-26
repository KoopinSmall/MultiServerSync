package uz.koopin.mss.sync;

import java.util.concurrent.CompletableFuture;

public interface SyncBus extends AutoCloseable {

    void send(SyncPacket packet);

    <R extends SyncPacket> CompletableFuture<R> request(ReplyablePacket<R> packet);

    @Override
    void close();

    static SyncBusBuilder builder() {
        return new SyncBusBuilder();
    }
}
