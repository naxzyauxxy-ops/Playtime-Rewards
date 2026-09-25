package me.naxzyauxxy.playtimerewards.hook;

import me.clip.placeholderapi.PlaceholderAPI;
import org.bukkit.entity.Player;

/**
 * Isolates every direct PlaceholderAPI reference so the class is only loaded when
 * PlaceholderAPI is actually installed (callers check {@code PluginManager#isPluginEnabled} first).
 */
public final class PapiHook {

    private PapiHook() {
    }

    public static String apply(Player player, String text) {
        if (text == null || text.indexOf('%') < 0) {
            return text;
        }
        return PlaceholderAPI.setPlaceholders(player, text);
    }
}
