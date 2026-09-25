package me.naxzyauxxy.playtimerewards.tracking;

import me.naxzyauxxy.playtimerewards.PlaytimeRewardsPlugin;
import me.naxzyauxxy.playtimerewards.data.PlayerData;
import me.naxzyauxxy.playtimerewards.reward.RewardService;
import me.naxzyauxxy.playtimerewards.reward.RewardTier;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Accrues playtime using the wall clock (System.nanoTime), not tick counts, so TPS drops never
 * shorten anyone's playtime. Accrual is done right before any AFK state change, so the exact
 * moment a player goes AFK is respected rather than rounded to the next tick of the task.
 */
public final class PlaytimeTracker {

    /** Max time credited in one step - protects against clock jumps / long server freezes. */
    private static final long MAX_STEP_NANOS = 60_000_000_000L;

    private final PlaytimeRewardsPlugin plugin;
    private final Map<UUID, PlayerSession> sessions = new ConcurrentHashMap<>();
    private BukkitTask task;

    public PlaytimeTracker(PlaytimeRewardsPlugin plugin) {
        this.plugin = plugin;
    }

    public void start() {
        stop();
        long interval = plugin.settings().trackingIntervalTicks();
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, interval, interval);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    @Nullable
    public PlayerSession session(UUID uuid) {
        return sessions.get(uuid);
    }

    // ---- lifecycle ----------------------------------------------------------

    /** Called once the player's data is loaded and they are online (main thread). */
    public void startSession(Player player, PlayerData data) {
        PlayerSession session = new PlayerSession(System.nanoTime(), System.currentTimeMillis());
        session.reachedCount = plugin.rewards().countReached(data.playtimeSeconds());
        sessions.put(player.getUniqueId(), session);
    }

    /** Credits the final partial interval and forgets the session (main thread). */
    public void endSession(Player player) {
        PlayerSession session = sessions.remove(player.getUniqueId());
        PlayerData data = plugin.data().get(player.getUniqueId());
        if (session != null && data != null) {
            accrue(session, data);
        }
    }

    /** Flush every online player's pending time (before saving on shutdown). */
    public void flushAll() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            PlayerSession session = sessions.get(player.getUniqueId());
            PlayerData data = plugin.data().get(player.getUniqueId());
            if (session != null && data != null) {
                accrue(session, data);
            }
        }
    }

    /** Recomputes milestone counters without notifying (after reload or an admin edit). */
    public void resync(UUID uuid) {
        PlayerSession session = sessions.get(uuid);
        PlayerData data = plugin.data().get(uuid);
        if (session != null && data != null) {
            session.reachedCount = plugin.rewards().countReached(data.playtimeSeconds());
        }
    }

    public void resyncAll() {
        sessions.keySet().forEach(this::resync);
    }

    public void markActivity(Player player) {
        PlayerSession session = sessions.get(player.getUniqueId());
        if (session != null) {
            session.lastActivityMillis = System.currentTimeMillis();
        }
    }

    // ---- ticking ------------------------------------------------------------

    private void tick() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            PlayerSession session = sessions.get(player.getUniqueId());
            PlayerData data = plugin.data().get(player.getUniqueId());
            if (session == null || data == null) {
                continue; // still loading
            }
            accrue(session, data);                      // credit the interval with the OLD afk state
            updateAfk(player, session, null);           // then re-evaluate
            checkMilestones(player, session, data);
        }
    }

    private void accrue(PlayerSession session, PlayerData data) {
        long now = System.nanoTime();
        long delta = now - session.lastAccrualNanos;
        session.lastAccrualNanos = now;
        if (delta <= 0 || session.afk) {
            session.nanoRemainder = 0;
            return;
        }
        delta = Math.min(delta, MAX_STEP_NANOS) + session.nanoRemainder;
        long millis = delta / 1_000_000L;
        session.nanoRemainder = delta % 1_000_000L;
        data.addMillis(millis);
    }

    /** Called by the Purpur AFK listener with the new state, and by the tick with null. */
    public void onAfkEvent(Player player, boolean goingAfk) {
        PlayerSession session = sessions.get(player.getUniqueId());
        PlayerData data = plugin.data().get(player.getUniqueId());
        if (session == null || data == null) {
            return;
        }
        accrue(session, data);
        if (!goingAfk) {
            session.lastActivityMillis = System.currentTimeMillis();
        }
        updateAfk(player, session, goingAfk);
    }

    private void updateAfk(Player player, PlayerSession session, Boolean purpurOverride) {
        boolean nowAfk = plugin.afk().evaluate(player, session, purpurOverride);
        if (nowAfk == session.afk) {
            return;
        }
        session.afk = nowAfk;
        if (plugin.settings().notifyAfk()) {
            plugin.messages().send(player, nowAfk ? "afk-paused" : "afk-resumed");
        }
    }

    private void checkMilestones(Player player, PlayerSession session, PlayerData data) {
        List<RewardTier> tiers = plugin.rewards().tiers();
        long seconds = data.playtimeSeconds();
        RewardService service = plugin.rewardService();
        while (session.reachedCount < tiers.size() && seconds >= tiers.get(session.reachedCount).seconds()) {
            RewardTier tier = tiers.get(session.reachedCount++);
            if (data.isClaimed(tier.id()) || !service.hasAccess(player, tier)) {
                continue;
            }
            if (plugin.settings().autoClaim()) {
                service.claim(player, data, tier, true);
            } else if (plugin.settings().notifyReady()) {
                plugin.messages().send(player, "reward-ready",
                        TagResolver.resolver(service.playerTags(player, data), service.tierTags(tier, data)));
                plugin.settings().readySound().play(player);
            }
        }
    }
}
