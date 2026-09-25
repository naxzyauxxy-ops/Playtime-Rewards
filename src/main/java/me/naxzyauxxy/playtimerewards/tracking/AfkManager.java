package me.naxzyauxxy.playtimerewards.tracking;

import me.naxzyauxxy.playtimerewards.PlaytimeRewardsPlugin;
import me.naxzyauxxy.playtimerewards.config.PluginSettings;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;
import java.util.logging.Level;

/**
 * Decides whether a player is AFK by combining:
 * <ol>
 *   <li>Purpur's native AFK API ({@code Player#isAfk()})</li>
 *   <li>EssentialsX (soft, via reflection - no compile dependency)</li>
 *   <li>A built-in idle timer fed by {@code ActivityListener}</li>
 * </ol>
 */
public final class AfkManager {

    public static final String EXEMPT_PERMISSION = "playtimerewards.afk.exempt";

    private final PlaytimeRewardsPlugin plugin;
    private final boolean purpurAvailable;

    // Essentials reflection cache
    private Plugin essentials;
    private Method essGetUser;
    private Method essIsAfk;

    public AfkManager(PlaytimeRewardsPlugin plugin) {
        this.plugin = plugin;
        this.purpurAvailable = classExists("org.purpurmc.purpur.event.PlayerAFKEvent");
        hookEssentials();
    }

    public boolean purpurAvailable() {
        return purpurAvailable;
    }

    private static boolean classExists(String name) {
        try {
            Class.forName(name);
            return true;
        } catch (ClassNotFoundException ex) {
            return false;
        }
    }

    private void hookEssentials() {
        Plugin ess = plugin.getServer().getPluginManager().getPlugin("Essentials");
        if (ess == null || !ess.isEnabled()) {
            return;
        }
        try {
            essGetUser = ess.getClass().getMethod("getUser", Player.class);
            Class<?> userClass = essGetUser.getReturnType();
            essIsAfk = userClass.getMethod("isAfk");
            essentials = ess;
            plugin.getLogger().info("Hooked into Essentials AFK status.");
        } catch (ReflectiveOperationException ex) {
            plugin.getLogger().warning("Essentials found but its AFK API could not be hooked: " + ex.getMessage());
        }
    }

    /**
     * @param purpurOverride non-null while handling a PlayerAFKEvent, where {@code isAfk()} still
     *                       returns the OLD value; null means "ask Purpur".
     */
    public boolean evaluate(Player player, PlayerSession session, Boolean purpurOverride) {
        PluginSettings s = plugin.settings();
        if (!s.pauseWhenAfk() || player.hasPermission(EXEMPT_PERMISSION)) {
            return false;
        }
        if (s.usePurpurAfk() && purpurAvailable) {
            boolean purpurAfk = purpurOverride != null ? purpurOverride : player.isAfk();
            if (purpurAfk) {
                return true;
            }
        }
        if (s.useEssentialsAfk() && essentials != null && isEssentialsAfk(player)) {
            return true;
        }
        long idle = s.idleTimeoutMillis();
        return idle > 0 && System.currentTimeMillis() - session.lastActivityMillis >= idle;
    }

    private boolean isEssentialsAfk(Player player) {
        try {
            Object user = essGetUser.invoke(essentials, player);
            return user != null && (boolean) essIsAfk.invoke(user);
        } catch (ReflectiveOperationException | RuntimeException ex) {
            plugin.getLogger().log(Level.WARNING, "Essentials AFK check failed - disabling the hook.", ex);
            essentials = null;
            return false;
        }
    }
}
