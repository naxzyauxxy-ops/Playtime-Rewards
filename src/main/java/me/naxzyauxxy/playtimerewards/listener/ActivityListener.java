package me.naxzyauxxy.playtimerewards.listener;

import io.papermc.paper.event.player.AsyncChatEvent;
import me.naxzyauxxy.playtimerewards.PlaytimeRewardsPlugin;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;

/**
 * Feeds the built-in idle detector. Handlers are O(1) and allocation-free because
 * PlayerMoveEvent fires very often.
 */
public final class ActivityListener implements Listener {

    private final PlaytimeRewardsPlugin plugin;

    public ActivityListener(PlaytimeRewardsPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        // Looking around is real input; plain position changes can be water streams / pistons.
        if (event.hasChangedOrientation()
                || (plugin.settings().movementResetsIdle() && event.hasChangedBlock())) {
            plugin.tracker().markActivity(event.getPlayer());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        plugin.tracker().markActivity(event.getPlayer()); // only writes a volatile long - async safe
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        plugin.tracker().markActivity(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onInteract(PlayerInteractEvent event) {
        plugin.tracker().markActivity(event.getPlayer());
    }
}
