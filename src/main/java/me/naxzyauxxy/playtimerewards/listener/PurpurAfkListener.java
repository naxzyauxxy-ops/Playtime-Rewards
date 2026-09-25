package me.naxzyauxxy.playtimerewards.listener;

import me.naxzyauxxy.playtimerewards.PlaytimeRewardsPlugin;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.purpurmc.purpur.event.PlayerAFKEvent;

/**
 * Purpur-native AFK integration: pauses/resumes the clock at the exact moment Purpur flips a
 * player's AFK state instead of waiting for the next tracker tick.
 * Registered only when running on Purpur.
 */
public final class PurpurAfkListener implements Listener {

    private final PlaytimeRewardsPlugin plugin;

    public PurpurAfkListener(PlaytimeRewardsPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onAfkChange(PlayerAFKEvent event) {
        if (plugin.settings().usePurpurAfk()) {
            plugin.tracker().onAfkEvent(event.getPlayer(), event.isGoingAfk());
        }
    }
}
