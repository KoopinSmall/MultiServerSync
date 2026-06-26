package uz.koopin.mss;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import lombok.Getter;
import lombok.SneakyThrows;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import uz.koopin.mss.commands.SyncCommand;
import uz.koopin.mss.config.ConfigOverrides;
import uz.koopin.mss.managers.DataManager;
import uz.koopin.mss.managers.PlayerManager;
import uz.koopin.mss.managers.ServerManager;
import uz.koopin.mss.messages.CommandMessage;

import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;

@Getter
public final class PaperSyncPlugin extends JavaPlugin {

    public static String SERVER_NAME;
    @Getter private static PaperSyncPlugin instance;

    private DataManager dataManager;
    private PlayerManager playerManager;
    private ServerManager serverManager;

    private PaperPlaceholders placeholders;

    @Override
    @SneakyThrows
    public void onEnable() {
        instance = this;
        this.saveDefaultConfig();

        SERVER_NAME = ConfigOverrides.getString("server.name",
                this.getConfig().getString("server.name",
                        Bukkit.getWorldContainer().getCanonicalFile().getName()));

        PaperSettings.init(this.getConfig());

        this.dataManager = new DataManager(PaperSettings.redis());
        this.playerManager = new PlayerManager(PaperSettings.redis());
        this.serverManager = new ServerManager(PaperSettings.redis());

        if (PaperSettings.SERVER_REGISTER) {
            this.registerServer();
        } else {
            getLogger().info("server.register is false; this server will not register itself with the network");
        }

        Bukkit.getPluginManager().registerEvents(new PaperListener(), this);
        Bukkit.getScheduler().runTaskTimerAsynchronously(this, () -> {
            this.serverManager.setServerOnline(SERVER_NAME, Bukkit.getOnlinePlayers().size());
        }, 20L, 60L);

        if (Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            this.placeholders = new PaperPlaceholders();
            this.placeholders.register();
        }

        this.getCommand("sync").setExecutor(new SyncCommand(this));
        this.serverManager.getMessageBroker().subscribeToMessages(this::handleMessage);
    }

    private void registerServer() {
        InetAddress host = resolveHost();
        int port = PaperSettings.SERVER_PORT > 0 ? PaperSettings.SERVER_PORT : this.getServer().getPort();
        InetSocketAddress address = new InetSocketAddress(host, port);
        boolean whitelist = this.getServer().hasWhitelist();

        System.out.println("Registering server: " + address + ", whitelist: " + whitelist);
        this.serverManager.registerServer(SERVER_NAME, address, whitelist);
    }

    private InetAddress resolveHost() {
        String configured = PaperSettings.SERVER_HOST;
        if (configured != null && !configured.isEmpty() && !configured.equalsIgnoreCase("auto")) {
            try {
                return InetAddress.getByName(configured);
            } catch (Exception e) {
                getLogger().warning("Could not resolve configured server.host '" + configured
                        + "', falling back to auto-detection: " + e.getMessage());
            }
        }

        try (DatagramSocket socket = new DatagramSocket()) {
            socket.connect(InetAddress.getByName("8.8.8.8"), 53);
            InetAddress local = socket.getLocalAddress();
            if (local != null && !local.isAnyLocalAddress()) {
                return local;
            }
        } catch (Exception ignored) {
        }

        try {
            return InetAddress.getLocalHost();
        } catch (Exception e) {
            getLogger().warning("Could not detect local host address, advertising 127.0.0.1. "
                    + "Set server.host explicitly in config.yml if the proxy is on another machine.");
            return InetAddress.getLoopbackAddress();
        }
    }

    @Override
    public void onDisable() {
        if (PaperSettings.SERVER_REGISTER) {
            System.out.println("Unregistering server: " + SERVER_NAME);
            this.serverManager.unregisterServer(SERVER_NAME);
            this.serverManager.removeServer(SERVER_NAME);
        }

        if (this.placeholders != null) {
            this.placeholders.unregister();
            this.placeholders = null;
        }
    }

    private void handleMessage(String message) {
        try {
            JsonParser jsonParser = new JsonParser();
            JsonObject json = jsonParser.parse(message).getAsJsonObject();
            String messageType = json.get("messageType").getAsString();
            String projectName = json.get("projectName").getAsString();

            if (!projectName.equalsIgnoreCase(PaperSettings.PROJECT_NAME))
                return;

            if (messageType.equals("COMMAND")) {
                CommandMessage cmdMsg = CommandMessage.fromJson(message);

                if (cmdMsg.isGroup()) {
                    return;
                }

                String target = cmdMsg.getTarget();
                boolean forMe = CommandMessage.isBroadcast(target) || target.equalsIgnoreCase(SERVER_NAME);
                if (forMe) {
                    String commandToRun = cmdMsg.getCommandLine();
                    System.out.println("Получена команда от " + cmdMsg.getServerName() + ": " + commandToRun);

                    Bukkit.getScheduler().runTask(this, () -> Bukkit.dispatchCommand(Bukkit.getConsoleSender(), commandToRun));
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
