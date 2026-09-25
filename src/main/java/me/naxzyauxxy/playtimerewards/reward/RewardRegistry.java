package me.naxzyauxxy.playtimerewards.reward;

import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import me.naxzyauxxy.playtimerewards.util.Text;
import me.naxzyauxxy.playtimerewards.util.TimeFormat;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

/** Parses and caches reward tiers, sorted by required time. Immutable after load. */
public final class RewardRegistry {

    private final List<RewardTier> tiers;
    private final Map<String, RewardTier> byId;

    private RewardRegistry(List<RewardTier> tiers) {
        this.tiers = List.copyOf(tiers);
        Map<String, RewardTier> map = new LinkedHashMap<>();
        tiers.forEach(t -> map.put(t.id().toLowerCase(), t));
        this.byId = Map.copyOf(map);
    }

    public List<RewardTier> tiers() {
        return tiers;
    }

    public int size() {
        return tiers.size();
    }

    @Nullable
    public RewardTier byId(String id) {
        return id == null ? null : byId.get(id.toLowerCase());
    }

    /** Number of tiers whose required time is &lt;= seconds (tiers are sorted). */
    public int countReached(long seconds) {
        int lo = 0;
        int hi = tiers.size();
        while (lo < hi) {
            int mid = (lo + hi) >>> 1;
            if (tiers.get(mid).seconds() <= seconds) {
                lo = mid + 1;
            } else {
                hi = mid;
            }
        }
        return lo;
    }

    /** First tier the player has NOT reached yet, or null when every tier is reached. */
    @Nullable
    public RewardTier next(long seconds) {
        int idx = countReached(seconds);
        return idx < tiers.size() ? tiers.get(idx) : null;
    }

    // ---- loading ------------------------------------------------------------

    public static RewardRegistry load(@Nullable ConfigurationSection section, Logger log) {
        List<RewardTier> list = new ArrayList<>();
        if (section == null) {
            log.warning("No 'tiers' section found - the rewards menu will be empty.");
            return new RewardRegistry(list);
        }
        for (String id : section.getKeys(false)) {
            ConfigurationSection t = section.getConfigurationSection(id);
            if (t == null) {
                continue;
            }
            long seconds = TimeFormat.parse(t.getString("time", ""));
            if (seconds <= 0) {
                log.warning("Tier '" + id + "' has an invalid or missing 'time' - skipped.");
                continue;
            }
            String display = t.getString("display", "");
            if (display == null || display.isBlank()) {
                display = TimeFormat.compact(seconds);
            }
            List<RewardAction> actions = t.getStringList("actions").stream()
                    .filter(s -> !s.isBlank()).map(RewardAction::parse).toList();
            List<ItemStack> items = new ArrayList<>();
            for (Map<?, ?> raw : t.getMapList("items")) {
                ItemStack stack = parseItem(raw, id, log);
                if (stack != null) {
                    items.add(stack);
                }
            }
            list.add(new RewardTier(id, seconds, display, t.getBoolean("require-permission", false),
                    List.copyOf(t.getStringList("lore")), actions, List.copyOf(items),
                    List.copyOf(t.getStringList("permissions"))));
        }
        list.sort(Comparator.comparingLong(RewardTier::seconds).thenComparing(RewardTier::id));
        return new RewardRegistry(list);
    }

    @Nullable
    @SuppressWarnings("deprecation")
    private static ItemStack parseItem(Map<?, ?> raw, String tierId, Logger log) {
        Object matRaw = raw.get("material");
        Material material = matRaw == null ? null : Material.matchMaterial(matRaw.toString());
        if (material == null || !material.isItem()) {
            log.warning("Tier '" + tierId + "' has an item with invalid material '" + matRaw + "' - skipped.");
            return null;
        }
        int amount = raw.get("amount") instanceof Number n ? Math.max(1, n.intValue()) : 1;
        ItemStack stack = new ItemStack(material, amount);
        stack.editMeta(meta -> {
            if (raw.get("name") instanceof String name && !name.isEmpty()) {
                meta.displayName(Text.item(name));
            }
            if (raw.get("lore") instanceof List<?> lore) {
                List<Component> lines = new ArrayList<>();
                lore.forEach(l -> lines.add(Text.item(String.valueOf(l))));
                meta.lore(lines);
            }
            if (raw.get("custom-model-data") instanceof Number cmd && cmd.intValue() > 0) {
                meta.setCustomModelData(cmd.intValue());
            }
            if (raw.get("item-model") instanceof String model && !model.isBlank()) {
                NamespacedKey key = NamespacedKey.fromString(model);
                if (key != null) {
                    meta.setItemModel(key);
                }
            }
            if (Boolean.TRUE.equals(raw.get("glow"))) {
                meta.setEnchantmentGlintOverride(true);
            }
            if (raw.get("enchantments") instanceof Map<?, ?> enchants) {
                Registry<Enchantment> registry = RegistryAccess.registryAccess().getRegistry(RegistryKey.ENCHANTMENT);
                enchants.forEach((k, v) -> {
                    NamespacedKey key = NamespacedKey.fromString(String.valueOf(k).toLowerCase());
                    Enchantment ench = key == null ? null : registry.get(key);
                    if (ench == null) {
                        log.warning("Tier '" + tierId + "': unknown enchantment '" + k + "'");
                        return;
                    }
                    int level = v instanceof Number n ? n.intValue() : 1;
                    meta.addEnchant(ench, level, true);
                });
            }
        });
        return stack;
    }
}
