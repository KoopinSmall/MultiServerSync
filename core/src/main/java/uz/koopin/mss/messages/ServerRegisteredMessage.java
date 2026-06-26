package uz.koopin.mss.messages;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

import java.net.InetSocketAddress;

import com.google.gson.Gson;
import java.net.InetSocketAddress;

public class ServerRegisteredMessage extends ServerMessage {
    private static final Gson gson = new Gson();

    public ServerRegisteredMessage(String projectName, String serverName, InetSocketAddress address, boolean whitelist) {
        super(projectName, serverName, address, whitelist);
    }

    @Override
    public String getMessageType() {
        return "SERVER_REGISTERED";
    }

    @Override
    public String toJson() {
        return gson.toJson(this);
    }

    public static ServerRegisteredMessage fromJson(String json) {
        return gson.fromJson(json, ServerRegisteredMessage.class);
    }
}