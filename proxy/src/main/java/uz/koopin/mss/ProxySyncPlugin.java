package uz.koopin.mss;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.inject.Inject;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.server.ServerInfo;
import lombok.Getter;
import org.slf4j.Logger;
import uz.koopin.mss.commands.SyncCommand;
import uz.koopin.mss.managers.DataManager;
import uz.koopin.mss.managers.PlayerManager;
import uz.koopin.mss.managers.ServerManager;
import uz.koopin.mss.messages.CommandMessage;
import uz.koopin.mss.messages.ServerRegisteredMessage;
import uz.koopin.mss.messages.ServerUnregisteredMessage;
import uz.koopin.mss.messages.ServerUpdateMessage;
import uz.koopin.mss.storage.ServerGroup;
import uz.koopin.mss.storage.SyncServer;
import uz.koopin.mss.utils.ConfigurationUtil;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Plugin(
        id = "multi-server-sync",
        name = "multi-server-sync",
        version = "1.2.0"
)
@Getter
public class ProxySyncPlugin {

    @Getter
    private static ProxySyncPlugin instance;

    private final Logger logger;
    private final ProxyServer server;
    private final Path folder;

    private DataManager dataManager;
    private PlayerManager playerManager;
    private ServerManager serverManager;
    private final Map<String, SyncServer> managedServers = new ConcurrentHashMap<>();

    private int currentOnline = 0;

    @Inject
    public ProxySyncPlugin(ProxyServer server, Logger logger, @DataDirectory Path folder) {
        this.server = server;
        this.logger = logger;
        this.folder = folder;
    }

    @Subscribe
    public void onProxyInitialization(ProxyInitializeEvent event) {
        instance = this;

        try {
            ProxySettings.init(ConfigurationUtil.load(folder, "config.yml"));
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load config.yml", e);
        }

        this.dataManager = new DataManager(ProxySettings.getRedis());
        this.playerManager = new PlayerManager(ProxySettings.getRedis());
        this.serverManager = new ServerManager(ProxySettings.getRedis());

        this.server.getCommandManager().register(
                this.server.getCommandManager().metaBuilder("vsync").build(),
                new SyncCommand(this)
        );

        loadExistingServers();
        this.serverManager.getMessageBroker().subscribeToMessages(this::handleServerMessage);
        registerSelfInRedis();

        this.server.getAllPlayers().forEach(player ->
                player.getCurrentServer().ifPresent(current -> this.playerManager.setPlayerLocation(
                        player.getUsername(),
                        ProxySettings.PROXY_NAME,
                        current.getServerInfo().getName(),
                        current.getServerInfo().getName()
                )));

        this.server.getEventManager().register(this, new ProxyListener());
    }

    private void handleServerMessage(String message) {
        try {
            JsonObject json = JsonParser.parseString(message).getAsJsonObject();
            String messageType = json.get("messageType").getAsString();

            switch (messageType) {
                case "SERVER_REGISTERED" -> registerBackend(ServerRegisteredMessage.fromJson(message));
                case "SERVER_UNREGISTERED" -> unregisterBackend(ServerUnregisteredMessage.fromJson(message));
                case "SERVER_UPDATED" -> updateBackend(ServerUpdateMessage.fromJson(message));
                case "COMMAND" -> handleCommandMessage(CommandMessage.fromJson(message));
            }
        } catch (Exception e) {
            logger.error("Failed to handle sync message", e);
        }
    }

    private void registerSelfInRedis() {
        InetSocketAddress address = new InetSocketAddress(
                server.getBoundAddress().getAddress(), server.getBoundAddress().getPort());
        logger.info("Publishing proxy '{}' presence in Redis at {}", ProxySettings.PROXY_NAME, address);
        this.serverManager.registerServer(ProxySettings.PROXY_NAME, address, false);
    }

    private synchronized void registerBackend(SyncServer syncServer) {
        if (!ProxySettings.PROJECT_NAME.equalsIgnoreCase(syncServer.getProjectName())) {
            return;
        }
        String name = syncServer.getServerName();
        if (name.equalsIgnoreCase(ProxySettings.PROXY_NAME)) {
            return;
        }

        if (this.server.getServer(name).isPresent()) {
            managedServers.put(name, syncServer);
            return;
        }

        this.server.registerServer(new ServerInfo(name, syncServer.getAddress()));
        managedServers.put(name, syncServer);
        logger.info("Backend registered: {} ({}:{}, whitelist={})",
                name, syncServer.getAddress().getHostString(),
                syncServer.getAddress().getPort(), syncServer.isWhitelisted());
    }

    private void registerBackend(ServerRegisteredMessage message) {
        InetSocketAddress address = new InetSocketAddress(message.getAddressHost(), message.getAddressPort());
        registerBackend(new SyncServer(
                message.getProjectName(), message.getServerName(), address, message.isWhitelist()));
    }

    private synchronized void unregisterBackend(ServerUnregisteredMessage message) {
        if (!ProxySettings.PROJECT_NAME.equalsIgnoreCase(message.getProjectName())) {
            return;
        }
        String name = message.getServerName();
        this.server.getServer(name).ifPresent(rs -> this.server.unregisterServer(rs.getServerInfo()));
        managedServers.remove(name);
        logger.info("Backend unregistered: {}", name);
    }

    private void updateBackend(ServerUpdateMessage message) {
        if (!ProxySettings.PROJECT_NAME.equalsIgnoreCase(message.getProjectName())) {
            return;
        }
        InetSocketAddress address = new InetSocketAddress(message.getAddressHost(), message.getAddressPort());
        managedServers.put(message.getServerName(), new SyncServer(
                message.getProjectName(), message.getServerName(), address, message.isWhitelist()));
    }

    private void loadExistingServers() {
        try {
            for (SyncServer syncServer : serverManager.getSyncServers()) {
                registerBackend(syncServer);
            }
        } catch (Exception e) {
            logger.error("Failed to load existing backends from Redis", e);
        }
    }

    private void handleCommandMessage(CommandMessage message) {
        String target = message.getTarget();
        String cmdLine = message.getCommandLine();

        if (!message.isGroup()) {
            if (CommandMessage.isBroadcast(target)) {
                executeProxyCommand(cmdLine);
                return;
            }

            if (target.equalsIgnoreCase(ProxySettings.PROXY_NAME)) {
                executeProxyCommand(cmdLine);
            }
            return;
        }

        Optional<ServerGroup> groupOpt = ProxySettings.getServerGroups().stream()
                .filter(g -> g.name().equalsIgnoreCase(target))
                .findFirst();
        if (groupOpt.isEmpty()) {
            logger.warn("Command targets unknown group: {}", target);
            return;
        }

        for (String serverName : groupOpt.get().servers()) {
            if (serverName.equalsIgnoreCase(ProxySettings.PROXY_NAME)) {
                executeProxyCommand(cmdLine);
            } else {
                this.serverManager.getMessageBroker().publishMessage(new CommandMessage(
                        message.getProjectName(), "PROXY", serverName, false, cmdLine));
            }
        }
    }

    private void executeProxyCommand(String command) {
        logger.info("Proxy executing command: {}", command);
        this.server.getCommandManager().executeImmediatelyAsync(this.server.getConsoleCommandSource(), command);
    }
}
