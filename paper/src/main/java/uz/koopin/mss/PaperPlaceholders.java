package uz.koopin.mss;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import uz.koopin.mss.managers.PlayerManager;
import uz.koopin.mss.managers.ServerManager;
import uz.koopin.mss.storage.SyncServer;
import uz.koopin.mss.storage.SyncUser;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PaperPlaceholders extends PlaceholderExpansion {

    private int cachedProjectOnline = 0;
    private final Map<String, Integer> cachedServerOnline = new HashMap<>();
    private long lastCacheUpdateTime = 0;
    private static final long CACHE_DURATION_MS = 3000;

    @Override
    public @NotNull String getIdentifier() {
        return "mss";
    }

    @Override
    public @NotNull String getAuthor() {
        return "Koopin";
    }

    @Override
    public @NotNull String getVersion() {
        return "1.0.0";
    }

    private void refreshCacheIfNeeded() {
        if (System.currentTimeMillis() - lastCacheUpdateTime > CACHE_DURATION_MS) {
            ServerManager serverManager = PaperSyncPlugin.getInstance().getServerManager();

            this.cachedProjectOnline = serverManager.getProjectOnline();
            this.cachedServerOnline.clear();

            List<SyncServer> allServers = serverManager.getSyncServers();
            for (SyncServer server : allServers) {
                this.cachedServerOnline.put(
                        server.getServerName(),
                        serverManager.getServerOnline(server.getServerName())
                );
            }

            this.lastCacheUpdateTime = System.currentTimeMillis();
        }
    }

    @Override
    public @Nullable String onPlaceholderRequest(Player player, @NotNull String params) {
        this.refreshCacheIfNeeded();

        String[] args = params.split("_");

        if (args[0].equals("server")) {
            PlayerManager playerManager = PaperSyncPlugin.getInstance().getPlayerManager();
            SyncUser user = playerManager.getPlayerLocation(player.getName());

            if (user != null) {
                if (user.server().contains("anarchy")) {
                    return "Анархия-" + user.server().split("-")[1];
                }
                if (user.server().contains("grief")) {
                    return "Гриф-" + user.server().split("-")[1];
                }
                if (user.server().contains("hub") || user.server().contains("lobby")) {
                    return "Хаб-" + user.server().split("-")[1];
                }
            }
        }

        if (args[0].equals("online")) {
            if (args.length == 1) {
                return String.valueOf(this.cachedProjectOnline);
            } else {
                String target = args[1];
                if (target.startsWith("@")) {
                    String groupName = target.substring(1);

                    int totalOnlineInGroup = this.cachedServerOnline.entrySet().stream()
                            .filter(entry -> entry.getKey().contains(groupName))
                            .mapToInt(Map.Entry::getValue)
                            .sum();

                    return String.valueOf(totalOnlineInGroup);
                } else {
                    return String.valueOf(this.cachedServerOnline.getOrDefault(target, 0));
                }
            }
        }

        return null;
    }
}
