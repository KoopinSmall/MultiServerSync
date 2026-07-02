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
import uz.koopin.mss.storage.RedisProvider;
import uz.koopin.mss.storage.ServerGroup;
import uz.koopin.mss.storage.SyncServer;
import uz.koopin.mss.sync.SyncContext;
import uz.koopin.mss.sync.packets.BackendRegistry;
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
        version = "1.2.2"
)
@Getter
public class VelocitySyncPlugin implements BackendRegistry {

    @Getter
    private static VelocitySyncPlugin instance;

    private final Logger logger;
    private final ProxyServer server;
    private final Path folder;

    private RedisProvider redis;
    private DataManager dataManager;
    private PlayerManager playerManager;
    private ServerManager serverManager;
    private final Map<String, SyncServer> managedServers = new ConcurrentHashMap<>();

    private int currentOnline = 0;

    @Inject
    public VelocitySyncPlugin(ProxyServer server, Logger logger, @DataDirectory Path folder) {
        this.server = server;
        this.logger = logger;
        this.folder = folder;
    }

    @Subscribe
    public void onProxyInitialization(ProxyInitializeEvent event) {
        instance = this;

        try {
            VelocitySettings.init(ConfigurationUtil.load(folder, "config.yml"));
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load config.yml", e);
        }

        this.redis = new RedisProvider(VelocitySettings.getRedis());
        this.dataManager = new DataManager(redis);
        this.playerManager = new PlayerManager(redis);

        SyncContext.put(BackendRegistry.class, this);
        this.serverManager = new ServerManager(redis, VelocitySettings.PROXY_NAME);

        this.server.getCommandManager().register(
                this.server.getCommandManager().metaBuilder("vsync").build(),
                new SyncCommand(this)
        );

        loadExistingServers();
        this.serverManager.getMessageBroker().subscribeToMessages(this::handleCommandChannel);
        registerSelfInRedis();

        this.server.getAllPlayers().forEach(player ->
                player.getCurrentServer().ifPresent(current -> this.playerManager.setPlayerLocation(
                        player.getUsername(),
                        VelocitySettings.PROXY_NAME,
                        current.getServerInfo().getName(),
                        current.getServerInfo().getName()
                )));

        this.server.getEventManager().register(this, new VelocityListener());
    }

    private void handleCommandChannel(String message) {
        try {
            JsonObject json = JsonParser.parseString(message).getAsJsonObject();
            if ("COMMAND".equals(json.get("messageType").getAsString())) {
                handleCommandMessage(CommandMessage.fromJson(message));
            }
        } catch (Exception e) {
            logger.error("Failed to handle sync message", e);
        }
    }

    private void registerSelfInRedis() {
        InetSocketAddress address = new InetSocketAddress(
                server.getBoundAddress().getAddress(), server.getBoundAddress().getPort());
        logger.info("Publishing proxy '{}' presence in Redis at {}", VelocitySettings.PROXY_NAME, address);
        this.serverManager.registerServer(VelocitySettings.PROXY_NAME, address, false);
    }

    private synchronized void registerBackend(SyncServer syncServer) {
        if (!VelocitySettings.PROJECT_NAME.equalsIgnoreCase(syncServer.getProjectName())) {
            return;
        }
        String name = syncServer.getServerName();
        if (name.equalsIgnoreCase(VelocitySettings.PROXY_NAME)) {
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

    @Override
    public void register(String project, String serverName, String host, int port, boolean whitelist) {
        registerBackend(new SyncServer(project, serverName, new InetSocketAddress(host, port), whitelist));
    }

    @Override
    public synchronized void unregister(String project, String serverName) {
        if (!VelocitySettings.PROJECT_NAME.equalsIgnoreCase(project)) {
            return;
        }
        this.server.getServer(serverName).ifPresent(rs -> this.server.unregisterServer(rs.getServerInfo()));
        managedServers.remove(serverName);
        logger.info("Backend unregistered: {}", serverName);
    }

    @Override
    public void update(String project, String serverName, String host, int port, boolean whitelist) {
        if (!VelocitySettings.PROJECT_NAME.equalsIgnoreCase(project)) {
            return;
        }
        managedServers.put(serverName, new SyncServer(
                project, serverName, new InetSocketAddress(host, port), whitelist));
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

            if (target.equalsIgnoreCase(VelocitySettings.PROXY_NAME)) {
                executeProxyCommand(cmdLine);
            }
            return;
        }

        Optional<ServerGroup> groupOpt = VelocitySettings.getServerGroups().stream()
                .filter(g -> g.name().equalsIgnoreCase(target))
                .findFirst();
        if (groupOpt.isEmpty()) {
            logger.warn("Command targets unknown group: {}", target);
            return;
        }

        for (String serverName : groupOpt.get().servers()) {
            if (serverName.equalsIgnoreCase(VelocitySettings.PROXY_NAME)) {
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
