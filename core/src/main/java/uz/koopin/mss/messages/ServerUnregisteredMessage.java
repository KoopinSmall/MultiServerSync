package uz.koopin.mss.messages;

import com.google.gson.Gson;
import java.net.InetSocketAddress;

import com.google.gson.Gson;
import java.net.InetSocketAddress;

public class ServerUnregisteredMessage extends ServerMessage {
    private static final Gson gson = new Gson();

    public ServerUnregisteredMessage(String projectName, String serverName, InetSocketAddress address, boolean whitelist) {
        super(projectName, serverName, address, whitelist);
    }

    @Override
    public String getMessageType() {
        return "SERVER_UNREGISTERED";
    }

    @Override
    public String toJson() {
        return gson.toJson(this);
    }

    public static ServerUnregisteredMessage fromJson(String json) {
        return gson.fromJson(json, ServerUnregisteredMessage.class);
    }
}