package uz.koopin.mss.storage;

import java.net.InetSocketAddress;


public class SyncServer {

    private String projectName;
    private String serverName;
    private InetSocketAddress address;
    private boolean whitelisted;

    public SyncServer(String projectName, String serverName, InetSocketAddress address, boolean whitelisted) {
        this.projectName = projectName;
        this.serverName = serverName;
        this.address = address;
        this.whitelisted = whitelisted;
    }

    public String getProjectName() {
        return projectName;
    }

    public void setProjectName(String projectName) {
        this.projectName = projectName;
    }

    public String getServerName() {
        return serverName;
    }

    public void setServerName(String serverName) {
        this.serverName = serverName;
    }

    public InetSocketAddress getAddress() {
        return address;
    }

    public void setAddress(InetSocketAddress address) {
        this.address = address;
    }

    public boolean isWhitelisted() {
        return whitelisted;
    }

    public void setWhitelisted(boolean whitelisted) {
        this.whitelisted = whitelisted;
    }
}
