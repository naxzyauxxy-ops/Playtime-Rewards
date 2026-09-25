package me.naxzyauxxy.playtimerewards.reward;

import org.bukkit.inventory.ItemStack;

import java.util.List;

/**
 * A single milestone. Immutable; item stacks are pre-built once and cloned on delivery.
 *
 * @param id                config key, stored in claim history
 * @param seconds           playtime required
 * @param displayName       value of the {@code <time>} tag ("30m", "2d")
 * @param requirePermission whether {@link #permissionNode()} is required to claim
 * @param lore              lines injected for {@code <rewards>} in menu lore
 */
public record RewardTier(String id, long seconds, String displayName, boolean requirePermission,
                         List<String> lore, List<RewardAction> actions, List<ItemStack> items,
                         List<String> permissions) {

    public static final String PERMISSION_PREFIX = "playtimerewards.tier.";

    public String permissionNode() {
        return PERMISSION_PREFIX + id;
    }
}
