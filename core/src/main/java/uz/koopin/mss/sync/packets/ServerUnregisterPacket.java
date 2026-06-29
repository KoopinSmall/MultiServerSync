package uz.koopin.mss.sync.packets;

import uz.koopin.mss.sync.SyncContext;
import uz.koopin.mss.sync.SyncPacket;

public class ServerUnregisterPacket implements SyncPacket {

    public String project;
    public String server;

    public ServerUnregisterPacket() {
    }

    public ServerUnregisterPacket(String project, String server) {
        this.project = project;
        this.server = server;
    }

    @Override
    public void onReceive() {
        SyncContext.lookup(BackendRegistry.class)
                .ifPresent(registry -> registry.unregister(project, server));
    }
}
