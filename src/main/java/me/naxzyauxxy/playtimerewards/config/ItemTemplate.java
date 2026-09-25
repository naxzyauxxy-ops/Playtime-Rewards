package me.naxzyauxxy.playtimerewards.config;

import me.naxzyauxxy.playtimerewards.util.Text;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.UnaryOperator;
import java.util.logging.Logger;

/**
 * Cached, config-driven description of a menu item. Strings stay as MiniMessage templates
 * and are resolved per viewer when the item is built.
 */
public record ItemTemplate(Material material, int customModelData, @Nullable NamespacedKey itemModel,
                           boolean glow, String name, List<String> lore, int slot) {

    /** Lore line that expands into the tier's own reward lines. */
    public static final String REWARDS_TOKEN = "<rewards>";

    public static ItemTemplate load(@Nullable ConfigurationSection section, Material fallback, Logger logger) {
        if (section == null) {
            return new ItemTemplate(fallback, 0, null, false, "", List.of(), -1);
        }
        Material material = Material.matchMaterial(section.getString("material", fallback.name()));
        if (material == null || !material.isItem()) {
            logger.warning("Invalid material at " + section.getCurrentPath() + ", using " + fallback);
            material = fallback;
        }
        NamespacedKey model = null;
        String modelRaw = section.getString("item-model", "");
        if (modelRaw != null && !modelRaw.isBlank()) {
            model = NamespacedKey.fromString(modelRaw.trim());
            if (model == null) {
                logger.warning("Invalid item-model '" + modelRaw + "' at " + section.getCurrentPath());
            }
        }
        return new ItemTemplate(material,
                section.getInt("custom-model-data", 0),
                model,
                section.getBoolean("glow", false),
                section.getString("name", ""),
                List.copyOf(section.getStringList("lore")),
                section.getInt("slot", -1));
    }

    /**
     * @param resolver     tag values for this viewer / tier
     * @param rewardLines  lines that replace a {@code <rewards>} lore line (may be empty)
     * @param preprocess   optional string pre-processor (PlaceholderAPI), identity when unused
     * @param headOwner    if the material is PLAYER_HEAD, whose skin to show
     */
    @SuppressWarnings("deprecation") // setCustomModelData(Integer) is still the portable CMD path
    public ItemStack build(TagResolver resolver, List<String> rewardLines,
                           UnaryOperator<String> preprocess, @Nullable Player headOwner) {
        ItemStack item = new ItemStack(material);
        item.editMeta(meta -> {
            meta.displayName(Text.item(preprocess.apply(name), resolver));
            List<Component> built = new ArrayList<>(lore.size() + rewardLines.size());
            for (String line : lore) {
                if (line.trim().equalsIgnoreCase(REWARDS_TOKEN)) {
                    for (String reward : rewardLines) {
                        built.add(Text.item(preprocess.apply(reward), resolver));
                    }
                    continue;
                }
                built.add(Text.item(preprocess.apply(line), resolver));
            }
            meta.lore(built);
            if (customModelData > 0) {
                meta.setCustomModelData(customModelData);
            }
            if (itemModel != null) {
                meta.setItemModel(itemModel);
            }
            if (glow) {
                meta.setEnchantmentGlintOverride(true);
            }
            meta.addItemFlags(ItemFlag.values());
            if (headOwner != null && meta instanceof SkullMeta skull) {
                skull.setPlayerProfile(headOwner.getPlayerProfile());
            }
        });
        return item;
    }
}
