package uz.koopin.mss;

import net.md_5.bungee.api.config.ServerInfo;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.event.PlayerDisconnectEvent;
import net.md_5.bungee.api.event.ServerKickEvent;
import net.md_5.bungee.api.event.ServerSwitchEvent;
import net.md_5.bungee.api.plugin.Listener;
import net.md_5.bungee.event.EventHandler;
import uz.koopin.mss.managers.PlayerManager;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public class BungeeListener implements Listener {

    private static final int MAX_ATTEMPTS = 3;
    private final PlayerManager playerManager = BungeeSyncPlugin.getInstance().getPlayerManager();
    private final Map<UUID, Integer> reconnectAttempts = new ConcurrentHashMap<>();

    @EventHandler
    public void onConnected(ServerSwitchEvent event) {
        ProxiedPlayer player = event.getPlayer();
        if (player.getServer() == null) {
            return;
        }
        String serverName = player.getServer().getInfo().getName();
        ServerInfo previous = event.getFrom();
        String previousServerName = previous != null ? previous.getName() : "";

        this.reconnectAttempts.remove(player.getUniqueId());

        BungeeSyncPlugin plugin = BungeeSyncPlugin.getInstance();
        plugin.getProxy().getScheduler().schedule(plugin, () ->
                this.playerManager.setPlayerLocation(
                        player.getName(),
                        BungeeSettings.PROXY_NAME,
                        previousServerName,
                        serverName
                ), 1L, TimeUnit.SECONDS);
    }

    @EventHandler
    public void onKickedFromServer(ServerKickEvent event) {
        ProxiedPlayer player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        int attempts = this.reconnectAttempts.merge(uuid, 1, Integer::sum);

        if (attempts >= MAX_ATTEMPTS) {
            this.reconnectAttempts.remove(uuid);
            return;
        }

        Optional<ServerInfo> fallback = pickFallback(event.getKickedFrom());
        if (fallback.isPresent()) {
            event.setCancelled(true);
            event.setCancelServer(fallback.get());
        } else {
            this.reconnectAttempts.remove(uuid);
        }
    }

    @EventHandler
    public void onDisconnect(PlayerDisconnectEvent event) {
        ProxiedPlayer player = event.getPlayer();

        this.reconnectAttempts.remove(player.getUniqueId());

        BungeeSyncPlugin plugin = BungeeSyncPlugin.getInstance();
        plugin.getProxy().getScheduler().schedule(plugin, () ->
                this.playerManager.removePlayer(player.getName()), 1L, TimeUnit.SECONDS);
    }

    private Optional<ServerInfo> pickFallback(ServerInfo kickedFrom) {
        BungeeSyncPlugin plugin = BungeeSyncPlugin.getInstance();
        String kickedName = kickedFrom != null ? kickedFrom.getName() : "";

        return BungeeSettings.getHubServers().stream()
                .filter(name -> !name.equalsIgnoreCase(kickedName))
                .map(name -> plugin.getProxy().getServerInfo(name))
                .filter(java.util.Objects::nonNull)
                .findFirst();
    }
}
