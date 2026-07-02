package uz.koopin.mss;

import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.player.KickedFromServerEvent;
import com.velocitypowered.api.event.player.ServerConnectedEvent;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import net.kyori.adventure.text.Component;
import uz.koopin.mss.managers.PlayerManager;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public class VelocityListener {

    private static final int MAX_ATTEMPTS = 3;
    private final PlayerManager playerManager = VelocitySyncPlugin.getInstance().getPlayerManager();
    private final Map<UUID, Integer> reconnectAttempts = new ConcurrentHashMap<>();

    @Subscribe
    public void onConnected(ServerConnectedEvent event) {
        Player player = event.getPlayer();
        RegisteredServer server = event.getServer();
        Optional<RegisteredServer> previousServer = event.getPreviousServer();

        this.reconnectAttempts.remove(player.getUniqueId());

        String previousServerName;

        if (previousServer.isPresent()) {
            previousServerName = previousServer.get().getServerInfo().getName();
        } else {
            previousServerName = "";
        }

        VelocitySyncPlugin.getInstance().getServer()
                .getScheduler()
                .buildTask(VelocitySyncPlugin.getInstance(), () -> {
                    this.playerManager.setPlayerLocation(
                            player.getUsername(),
                            VelocitySettings.PROXY_NAME,
                            previousServerName,
                            server.getServerInfo().getName()
                    );
                })
                .delay(1L, TimeUnit.SECONDS)
                .schedule();
    }

    @Subscribe
    public void onKickedFromServer(KickedFromServerEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        Component reason = event.getServerKickReason()
                .orElse(Component.text("Вы были отключены от сервера"));

        int attempts = this.reconnectAttempts.merge(uuid, 1, Integer::sum);

        if (attempts >= MAX_ATTEMPTS) {
            this.reconnectAttempts.remove(uuid);
            event.setResult(KickedFromServerEvent.DisconnectPlayer.create(reason));
            return;
        }

        Optional<RegisteredServer> fallback = pickFallback(event.getServer());
        if (fallback.isPresent()) {
            event.setResult(KickedFromServerEvent.RedirectPlayer.create(fallback.get()));
        } else {
            this.reconnectAttempts.remove(uuid);
            event.setResult(KickedFromServerEvent.DisconnectPlayer.create(reason));
        }
    }

    @Subscribe
    public void onDisconnect(DisconnectEvent event) {
        Player player = event.getPlayer();

        this.reconnectAttempts.remove(player.getUniqueId());

        VelocitySyncPlugin.getInstance().getServer()
                .getScheduler()
                .buildTask(VelocitySyncPlugin.getInstance(), () -> {
                    this.playerManager.removePlayer(player.getUsername());
                })
                .delay(1L, TimeUnit.SECONDS)
                .schedule();
    }

    private Optional<RegisteredServer> pickFallback(RegisteredServer kickedFrom) {
        ProxyServer proxy = VelocitySyncPlugin.getInstance().getServer();
        String kickedName = kickedFrom != null ? kickedFrom.getServerInfo().getName() : "";

        return VelocitySettings.getHubServers().stream()
                .filter(name -> !name.equalsIgnoreCase(kickedName))
                .map(proxy::getServer)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .findFirst();
    }
}
