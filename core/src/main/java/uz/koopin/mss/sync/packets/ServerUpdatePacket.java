package uz.koopin.mss.sync.packets;

import uz.koopin.mss.sync.SyncContext;
import uz.koopin.mss.sync.SyncPacket;

public class ServerUpdatePacket implements SyncPacket {

    public String project;
    public String server;
    public String host;
    public int port;
    public boolean whitelist;

    public ServerUpdatePacket() {
    }

    public ServerUpdatePacket(String project, String server, String host, int port, boolean whitelist) {
        this.project = project;
        this.server = server;
        this.host = host;
        this.port = port;
        this.whitelist = whitelist;
    }

    @Override
    public void onReceive() {
        SyncContext.lookup(BackendRegistry.class)
                .ifPresent(registry -> registry.update(project, server, host, port, whitelist));
    }
}
