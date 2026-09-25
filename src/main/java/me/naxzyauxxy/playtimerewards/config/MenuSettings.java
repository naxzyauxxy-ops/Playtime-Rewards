package me.naxzyauxxy.playtimerewards.config;

import me.naxzyauxxy.playtimerewards.reward.TierState;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;

import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

/** Immutable, cached menu layout. Rebuilt on every reload. */
public record MenuSettings(String title, int rows, int[] rewardSlots, long clickCooldownMs, long autoRefreshTicks,
                           ItemTemplate filler, boolean fillerEnabled, Map<TierState, ItemTemplate> states,
                           ItemTemplate info, ItemTemplate previousPage, ItemTemplate nextPage) {

    private static final int[] DEFAULT_SLOTS = {
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34,
            37, 38, 39, 40, 41, 42, 43};

    public int size() {
        return rows * 9;
    }

    public ItemTemplate state(TierState state) {
        return states.get(state);
    }

    public static MenuSettings load(ConfigurationSection gui, Logger log) {
        if (gui == null) {
            throw new IllegalStateException("Missing 'gui' section in config.yml");
        }
        int rows = Math.max(1, Math.min(6, gui.getInt("rows", 6)));
        int size = rows * 9;

        Set<Integer> slots = new LinkedHashSet<>();
        for (int slot : gui.getIntegerList("reward-slots")) {
            if (slot >= 0 && slot < size) {
                slots.add(slot);
            } else {
                log.warning("Ignoring reward slot " + slot + " (menu has " + size + " slots)");
            }
        }
        int[] rewardSlots = slots.isEmpty() ? DEFAULT_SLOTS : slots.stream().mapToInt(Integer::intValue).toArray();

        Map<TierState, ItemTemplate> states = new EnumMap<>(TierState.class);
        ConfigurationSection stateSection = gui.getConfigurationSection("states");
        states.put(TierState.CLAIMED, ItemTemplate.load(sub(stateSection, "claimed"), Material.LIME_DYE, log));
        states.put(TierState.READY, ItemTemplate.load(sub(stateSection, "ready"), Material.YELLOW_DYE, log));
        states.put(TierState.LOCKED, ItemTemplate.load(sub(stateSection, "locked"), Material.RED_DYE, log));
        states.put(TierState.RESTRICTED, ItemTemplate.load(sub(stateSection, "restricted"), Material.GRAY_DYE, log));

        ItemTemplate info = ItemTemplate.load(gui.getConfigurationSection("info-item"), Material.PLAYER_HEAD, log);
        ItemTemplate prev = ItemTemplate.load(gui.getConfigurationSection("previous-page"), Material.ARROW, log);
        ItemTemplate next = ItemTemplate.load(gui.getConfigurationSection("next-page"), Material.ARROW, log);
        ItemTemplate filler = ItemTemplate.load(gui.getConfigurationSection("filler"), Material.BLACK_STAINED_GLASS_PANE, log);

        return new MenuSettings(
                gui.getString("title", "<dark_gray>✹ Playtime | Rewards"),
                rows, rewardSlots,
                Math.max(0, gui.getLong("click-cooldown-ms", 250)),
                Math.max(0, gui.getLong("auto-refresh-ticks", 0)),
                filler, gui.getBoolean("filler.enabled", false),
                states,
                withSlot(info, 49, size), withSlot(prev, 45, size), withSlot(next, 53, size));
    }

    private static ConfigurationSection sub(ConfigurationSection parent, String key) {
        return parent == null ? null : parent.getConfigurationSection(key);
    }

    private static ItemTemplate withSlot(ItemTemplate t, int fallback, int size) {
        int slot = t.slot() >= 0 && t.slot() < size ? t.slot() : Math.min(fallback, size - 1);
        return new ItemTemplate(t.material(), t.customModelData(), t.itemModel(), t.glow(), t.name(), t.lore(), slot);
    }
}
