package uz.koopin.mss.storage;

import java.util.List;

public record ServerGroup(String name, List<String> servers) {
    
    public boolean contains(String serverName) {
        return servers.contains(serverName);
    }
    
    public boolean isEmpty() {
        return servers.isEmpty();
    }
}