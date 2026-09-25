package me.naxzyauxxy.playtimerewards.config;

import me.naxzyauxxy.playtimerewards.util.TimeFormat;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.logging.Logger;

/**
 * Every scalar setting from config.yml, read once per (re)load so hot paths never
 * touch the YAML tree.
 */
public record PluginSettings(
        long autosaveMinutes,
        long trackingIntervalTicks,
        boolean importVanillaPlaytime,
        boolean kickOnLoadFailure,
        boolean papiInMenus,
        boolean notifyReady,
        boolean autoClaim,
        boolean sendPlaytimeOnOpen,
        boolean joinReminder,
        long joinReminderDelayTicks,
        // AFK
        boolean pauseWhenAfk,
        boolean usePurpurAfk,
        boolean useEssentialsAfk,
        long idleTimeoutMillis,
        boolean movementResetsIdle,
        boolean notifyAfk,
        // formatting
        TimeFormat.Style timeStyle,
        String maxedOut,
        // permissions
        String permissionFallbackCommand,
        // sounds
        SoundSetting claimSound,
        SoundSetting readySound,
        SoundSetting refreshSound,
        SoundSetting denySound,
        SoundSetting pageSound) {

    public static PluginSettings load(FileConfiguration c, Logger log) {
        TimeFormat.Style style = new TimeFormat.Style(
                c.getString("time-format.day", "d"),
                c.getString("time-format.hour", "h"),
                c.getString("time-format.minute", "m"),
                c.getString("time-format.second", "s"),
                c.getString("time-format.separator", " "),
                c.getBoolean("time-format.hide-leading-zero-units", true));

        return new PluginSettings(
                Math.max(1, c.getLong("settings.autosave-interval-minutes", 5)),
                Math.max(1, c.getLong("settings.tracking-interval-ticks", 20)),
                c.getBoolean("settings.import-vanilla-playtime", true),
                c.getBoolean("settings.kick-on-load-failure", true),
                c.getBoolean("settings.use-placeholderapi-in-menus", true),
                c.getBoolean("settings.notify-when-reward-ready", true),
                c.getBoolean("settings.auto-claim", false),
                c.getBoolean("settings.send-playtime-on-open", true),
                c.getBoolean("settings.join-reminder.enabled", true),
                Math.max(1, c.getLong("settings.join-reminder.delay-ticks", 60)),
                c.getBoolean("afk.pause-when-afk", true),
                c.getBoolean("afk.use-purpur-afk", true),
                c.getBoolean("afk.use-essentials-afk", true),
                Math.max(0, c.getLong("afk.idle-timeout-seconds", 300)) * 1000L,
                c.getBoolean("afk.movement-resets-idle", false),
                c.getBoolean("afk.notify", true),
                style,
                c.getString("time-format.maxed-out", "Completed"),
                c.getString("permissions.fallback-command", "lp user %player% permission set %node% true"),
                SoundSetting.load(c.getConfigurationSection("sounds.claim"), log),
                SoundSetting.load(c.getConfigurationSection("sounds.ready"), log),
                SoundSetting.load(c.getConfigurationSection("sounds.refresh"), log),
                SoundSetting.load(c.getConfigurationSection("sounds.deny"), log),
                SoundSetting.load(c.getConfigurationSection("sounds.page"), log));
    }
}
