package me.naxzyauxxy.playtimerewards.listener;

import me.naxzyauxxy.playtimerewards.PlaytimeRewardsPlugin;
import me.naxzyauxxy.playtimerewards.data.PlayerData;
import me.naxzyauxxy.playtimerewards.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Statistic;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.io.IOException;
import java.util.logging.Level;

/**
 * Data lifecycle:
 * <ul>
 *   <li>Pre-login (async thread): load from disk - the main thread never waits on IO.</li>
 *   <li>Join: start the tracking session (falls back to an async load if needed).</li>
 *   <li>Quit: credit the last partial interval, save async, evict.</li>
 * </ul>
 */
public final class ConnectionListener implements Listener {

    private final PlaytimeRewardsPlugin plugin;

    public ConnectionListener(PlaytimeRewardsPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPreLogin(AsyncPlayerPreLoginEvent event) {
        if (event.getLoginResult() != AsyncPlayerPreLoginEvent.Result.ALLOWED) {
            return;
        }
        try {
            plugin.data().loadBlocking(event.getUniqueId(), event.getName());
        } catch (IOException ex) {
            plugin.getLogger().log(Level.SEVERE, "Could not load playtime data for " + event.getName(), ex);
            if (plugin.settings().kickOnLoadFailure()) {
                event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER,
                        Text.parse("<red>Your playtime data failed to load.\n<gray>Please try again in a moment."));
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        PlayerData data = plugin.data().get(player.getUniqueId());
        if (data != null) {
            begin(player, data);
            return;
        }
        // Rare: data was evicted between pre-login and join, or loading was skipped.
        plugin.data().loadAsync(player.getUniqueId(), player.getName()).whenComplete((loaded, error) -> {
            if (error != null) {
                plugin.getLogger().log(Level.SEVERE, "Could not load playtime data for " + player.getName(), error);
                return;
            }
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (player.isOnline()) {
                    begin(player, loaded);
                }
            });
        });
    }

    private void begin(Player player, PlayerData data) {
        data.name(player.getName());
        if (data.isFresh() && data.playtimeMillis() == 0 && plugin.settings().importVanillaPlaytime()) {
            long ticks = player.getStatistic(Statistic.PLAY_ONE_MINUTE); // despite the name: ticks
            if (ticks > 0) {
                data.setMillis(ticks * 50L);
                plugin.getLogger().info("Imported " + (ticks / 20L) + "s of vanilla playtime for " + player.getName());
            }
        }
        plugin.tracker().startSession(player, data);

        if (plugin.settings().joinReminder()) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (!player.isOnline()) {
                    return;
                }
                PlayerData current = plugin.data().get(player.getUniqueId());
                if (current != null && !plugin.rewardService().readyTiers(player, current).isEmpty()) {
                    plugin.messages().send(player, "join-reminder", plugin.rewardService().playerTags(player, current));
                    plugin.settings().readySound().play(player);
                }
            }, plugin.settings().joinReminderDelayTicks());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        plugin.tracker().endSession(player);
        plugin.data().unload(player.getUniqueId());
    }
}
