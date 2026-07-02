package uz.koopin.mss;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import lombok.AccessLevel;
import lombok.Getter;
import net.md_5.bungee.api.config.ListenerInfo;
import net.md_5.bungee.api.config.ServerInfo;
import net.md_5.bungee.api.plugin.Plugin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Getter
public class BungeeSyncPlugin extends Plugin implements BackendRegistry {

    @Getter
    private static BungeeSyncPlugin instance;

    @Getter(AccessLevel.NONE)
    private final Logger logger = LoggerFactory.getLogger(BungeeSyncPlugin.class);

    private RedisProvider redis;
    private DataManager dataManager;
    private PlayerManager playerManager;
    private ServerManager serverManager;
    private final Map<String, SyncServer> managedServers = new ConcurrentHashMap<>();

    @Override
    public void onEnable() {
        instance = this;

        try {
            BungeeSettings.init(ConfigurationUtil.load(getDataFolder().toPath(), "config.yml"));
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load config.yml", e);
        }

        this.redis = new RedisProvider(BungeeSettings.getRedis());
        this.dataManager = new DataManager(redis);
        this.playerManager = new PlayerManager(redis);

        SyncContext.put(BackendRegistry.class, this);
        this.serverManager = new ServerManager(redis, BungeeSettings.PROXY_NAME);

        getProxy().getPluginManager().registerCommand(this, new SyncCommand(this));

        loadExistingServers();
        this.serverManager.getMessageBroker().subscribeToMessages(this::handleCommandChannel);
        registerSelfInRedis();

        getProxy().getPlayers().forEach(player -> {
            if (player.getServer() != null) {
                String current = player.getServer().getInfo().getName();
                this.playerManager.setPlayerLocation(
                        player.getName(),
                        BungeeSettings.PROXY_NAME,
                        current,
                        current
                );
            }
        });

        getProxy().getPluginManager().registerListener(this, new BungeeListener());
    }

    @Override
    public void onDisable() {
        if (this.serverManager != null) {
            this.serverManager.close();
        }
        if (this.redis != null) {
            this.redis.close();
        }
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
        ListenerInfo listener = getProxy().getConfig().getListeners().iterator().next();
        InetSocketAddress bound = (InetSocketAddress) listener.getSocketAddress();
        InetSocketAddress address = new InetSocketAddress(bound.getAddress(), bound.getPort());
        logger.info("Publishing proxy '{}' presence in Redis at {}", BungeeSettings.PROXY_NAME, address);
        this.serverManager.registerServer(BungeeSettings.PROXY_NAME, address, false);
    }

    private synchronized void registerBackend(SyncServer syncServer) {
        if (!BungeeSettings.PROJECT_NAME.equalsIgnoreCase(syncServer.getProjectName())) {
            return;
        }
        String name = syncServer.getServerName();
        if (name.equalsIgnoreCase(BungeeSettings.PROXY_NAME)) {
            return;
        }

        if (getProxy().getServerInfo(name) != null) {
            managedServers.put(name, syncServer);
            return;
        }

        ServerInfo info = getProxy().constructServerInfo(name, syncServer.getAddress(), "", false);
        getProxy().getServers().put(name, info);
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
        if (!BungeeSettings.PROJECT_NAME.equalsIgnoreCase(project)) {
            return;
        }
        getProxy().getServers().remove(serverName);
        managedServers.remove(serverName);
        logger.info("Backend unregistered: {}", serverName);
    }

    @Override
    public void update(String project, String serverName, String host, int port, boolean whitelist) {
        if (!BungeeSettings.PROJECT_NAME.equalsIgnoreCase(project)) {
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

            if (target.equalsIgnoreCase(BungeeSettings.PROXY_NAME)) {
                executeProxyCommand(cmdLine);
            }
            return;
        }

        Optional<ServerGroup> groupOpt = BungeeSettings.getServerGroups().stream()
                .filter(g -> g.name().equalsIgnoreCase(target))
                .findFirst();
        if (groupOpt.isEmpty()) {
            logger.warn("Command targets unknown group: {}", target);
            return;
        }

        for (String serverName : groupOpt.get().servers()) {
            if (serverName.equalsIgnoreCase(BungeeSettings.PROXY_NAME)) {
                executeProxyCommand(cmdLine);
            } else {
                this.serverManager.getMessageBroker().publishMessage(new CommandMessage(
                        message.getProjectName(), "PROXY", serverName, false, cmdLine));
            }
        }
    }

    private void executeProxyCommand(String command) {
        logger.info("Proxy executing command: {}", command);
        getProxy().getPluginManager().dispatchCommand(getProxy().getConsole(), command);
    }
}
