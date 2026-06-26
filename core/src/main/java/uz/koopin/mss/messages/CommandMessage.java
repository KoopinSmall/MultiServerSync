package uz.koopin.mss.messages;

import com.google.gson.Gson;
import java.net.InetSocketAddress;

public class CommandMessage extends ServerMessage {
    private static final Gson gson = new Gson();

    /** Спец-таргет: команда выполняется на ВСЕХ подключённых серверах без исключения. */
    public static final String BROADCAST_TARGET = "*";

    /**
     * Является ли target широковещательным (все серверы). Принимает "*" или "all".
     */
    public static boolean isBroadcast(String target) {
        return BROADCAST_TARGET.equals(target) || "all".equalsIgnoreCase(target);
    }

    private final String target;
    private final boolean isGroup;
    private final String command;

    public CommandMessage(String projectName, String sourceServer, String target, boolean isGroup, String command) {
        super(projectName, sourceServer, InetSocketAddress.createUnresolved("127.0.0.1", 0), false);
        this.target = target;
        this.isGroup = isGroup;
        this.command = command;
    }

    @Override
    public String getMessageType() {
        return "COMMAND";
    }

    @Override
    public String toJson() {
        return gson.toJson(this);
    }

    public static CommandMessage fromJson(String json) {
        return gson.fromJson(json, CommandMessage.class);
    }

    public String getTarget() {
        return target;
    }

    public boolean isGroup() {
        return isGroup;
    }

    public String getCommandLine() {
        return command;
    }
}