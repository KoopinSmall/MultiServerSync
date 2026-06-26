package uz.koopin.mss;

import com.destroystokyo.paper.event.server.WhitelistToggleEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import uz.koopin.mss.storage.SyncServer;

public class PaperListener implements Listener {

    @EventHandler
    public void onWhitelistedToggle(WhitelistToggleEvent event) {
        boolean state = event.isEnabled();

        SyncServer syncServer = PaperSyncPlugin.getInstance()
                .getServerManager()
                .getSyncServer(PaperSyncPlugin.SERVER_NAME);

        syncServer.setWhitelisted(state);

        PaperSyncPlugin.getInstance()
                .getServerManager()
                .updateSyncServer(syncServer);
    }
}
