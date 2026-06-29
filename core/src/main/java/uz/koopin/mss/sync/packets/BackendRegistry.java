package uz.koopin.mss.sync.packets;

public interface BackendRegistry {

    void register(String project, String server, String host, int port, boolean whitelist);

    void update(String project, String server, String host, int port, boolean whitelist);

    void unregister(String project, String server);
}
