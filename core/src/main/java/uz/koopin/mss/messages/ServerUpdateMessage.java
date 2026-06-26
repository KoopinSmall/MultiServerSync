package uz.koopin.mss.messages;

import com.google.gson.Gson;
import java.net.InetSocketAddress;

public class ServerUpdateMessage extends ServerMessage {
    private static final Gson gson = new Gson();

    public ServerUpdateMessage(String projectName, String serverName, InetSocketAddress address, boolean whitelist) {
        super(projectName, serverName, address, whitelist);
    }

    @Override
    public String getMessageType() {
        return "SERVER_UPDATED";
    }

    @Override
    public String toJson() {
        return gson.toJson(this);
    }

    public static ServerUpdateMessage fromJson(String json) {
        return gson.fromJson(json, ServerUpdateMessage.class);
    }
}