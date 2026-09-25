package me.naxzyauxxy.playtimerewards.hook;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import me.naxzyauxxy.playtimerewards.PlaytimeRewardsPlugin;
import me.naxzyauxxy.playtimerewards.data.PlayerData;
import me.naxzyauxxy.playtimerewards.tracking.PlayerSession;
import me.naxzyauxxy.playtimerewards.util.TimeFormat;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;

/**
 * PlaceholderAPI expansion - identifier {@code playtimerewards}.
 *
 * <table>
 *   <tr><td>%playtimerewards_time%</td><td>1d 20h 49m 29s  (scoreboard format)</td></tr>
 *   <tr><td>%playtimerewards_time_short%</td><td>1d 20h</td></tr>
 *   <tr><td>%playtimerewards_days% / _hours% / _minutes% / _seconds%</td><td>unit components: 1 / 20 / 49 / 29</td></tr>
 *   <tr><td>%playtimerewards_total_seconds% / _total_minutes% / _total_hours%</td><td>totals</td></tr>
 *   <tr><td>%playtimerewards_claimed% / _total% / _ready%</td><td>10 / 28 / 0</td></tr>
 *   <tr><td>%playtimerewards_next%</td><td>3h 10m (time to next reward)</td></tr>
 *   <tr><td>%playtimerewards_next_name%</td><td>2d (label of next reward)</td></tr>
 *   <tr><td>%playtimerewards_afk%</td><td>true / false</td></tr>
 * </table>
 *
 * Safe to call from any thread: it only reads the concurrent cache and atomic fields.
 */
public final class PlaytimeExpansion extends PlaceholderExpansion {

    private final PlaytimeRewardsPlugin plugin;

    public PlaytimeExpansion(PlaytimeRewardsPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "playtimerewards";
    }

    @Override
    public @NotNull String getAuthor() {
        return String.join(", ", plugin.getPluginMeta().getAuthors());
    }

    @Override
    public @NotNull String getVersion() {
        return plugin.getPluginMeta().getVersion();
    }

    @Override
    public boolean persist() {
        return true; // survive /papi reload
    }

    @Override
    public boolean canRegister() {
        return true;
    }

    @Override
    public @Nullable String onRequest(OfflinePlayer player, @NotNull String params) {
        String key = params.toLowerCase(Locale.ROOT);
        if (key.equals("total")) {
            return Integer.toString(plugin.rewards().size());
        }
        if (player == null) {
            return "";
        }
        PlayerData data = plugin.data().get(player.getUniqueId());
        if (data == null) {
            return ""; // offline / not loaded yet
        }
        long seconds = data.playtimeSeconds();
        return switch (key) {
            case "time" -> TimeFormat.full(seconds);
            case "time_short" -> TimeFormat.brief(seconds, 2);
            case "time_compact" -> TimeFormat.compact(seconds);
            case "days" -> Long.toString(TimeFormat.component(seconds, 0));
            case "hours" -> Long.toString(TimeFormat.component(seconds, 1));
            case "minutes" -> Long.toString(TimeFormat.component(seconds, 2));
            case "seconds" -> Long.toString(TimeFormat.component(seconds, 3));
            case "total_seconds" -> Long.toString(seconds);
            case "total_minutes" -> Long.toString(seconds / 60L);
            case "total_hours" -> Long.toString(seconds / 3600L);
            case "claimed" -> Integer.toString(plugin.rewardService().claimedCount(data));
            case "ready" -> Integer.toString(plugin.rewardService().readyCount(data));
            case "next" -> plugin.rewardService().nextRewardIn(data);
            case "next_name" -> plugin.rewardService().nextRewardName(data);
            case "afk" -> {
                PlayerSession session = plugin.tracker().session(player.getUniqueId());
                yield Boolean.toString(session != null && session.isAfk());
            }
            default -> null; // unknown placeholder -> PAPI leaves it untouched
        };
    }
}
