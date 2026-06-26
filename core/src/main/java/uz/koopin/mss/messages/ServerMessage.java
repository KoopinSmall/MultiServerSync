package uz.koopin.mss.messages;

import java.net.InetSocketAddress;

public abstract class ServerMessage {
    protected final String messageType;
    protected final String projectName;
    protected final String serverName;
    protected final String addressHost;
    protected final int addressPort;
    protected final boolean whitelist;

    public ServerMessage(String projectName, String serverName, InetSocketAddress address, boolean whitelist) {
        this.projectName = projectName;
        this.messageType = getMessageType();
        this.serverName = serverName;
        this.addressHost = address.getHostString();
        this.addressPort = address.getPort();
        this.whitelist = whitelist;
    }

    public String getProjectName() { return projectName; }
    public String getServerName() { return serverName; }
    public InetSocketAddress getAddress() { return new InetSocketAddress(addressHost, addressPort); }
    public boolean isWhitelist() { return whitelist; }

    public abstract String getMessageType();
    public abstract String toJson();

    public String getAddressHost() { return addressHost; }
    public int getAddressPort() { return addressPort; }
}