package me.naxzyauxxy.playtimerewards.reward;

import me.naxzyauxxy.playtimerewards.PlaytimeRewardsPlugin;
import me.naxzyauxxy.playtimerewards.data.PlayerData;
import me.naxzyauxxy.playtimerewards.util.Text;
import me.naxzyauxxy.playtimerewards.util.TimeFormat;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;

/** Reward state calculation and claim/delivery logic. Main-thread only for claims. */
public final class RewardService {

    public static final String CLAIM_PERMISSION = "playtimerewards.claim";

    public enum ClaimResult { SUCCESS, ALREADY_CLAIMED, NOT_READY, NO_PERMISSION }

    private final PlaytimeRewardsPlugin plugin;

    public RewardService(PlaytimeRewardsPlugin plugin) {
        this.plugin = plugin;
    }

    private RewardRegistry registry() {
        return plugin.rewards();
    }

    // ---- state --------------------------------------------------------------

    public boolean hasAccess(Player player, RewardTier tier) {
        return !tier.requirePermission() || player.hasPermission(tier.permissionNode());
    }

    public TierState state(Player player, PlayerData data, RewardTier tier) {
        if (data.isClaimed(tier.id())) {
            return TierState.CLAIMED;
        }
        if (data.playtimeSeconds() < tier.seconds()) {
            return TierState.LOCKED;
        }
        return hasAccess(player, tier) ? TierState.READY : TierState.RESTRICTED;
    }

    public List<RewardTier> readyTiers(Player player, PlayerData data) {
        List<RewardTier> ready = new ArrayList<>();
        long seconds = data.playtimeSeconds();
        for (RewardTier tier : registry().tiers()) {
            if (tier.seconds() > seconds) {
                break; // sorted
            }
            if (!data.isClaimed(tier.id()) && hasAccess(player, tier)) {
                ready.add(tier);
            }
        }
        return ready;
    }

    /** Ready count without a Player (used by async PAPI requests; ignores permission-gating). */
    public int readyCount(PlayerData data) {
        int count = 0;
        long seconds = data.playtimeSeconds();
        for (RewardTier tier : registry().tiers()) {
            if (tier.seconds() > seconds) {
                break;
            }
            if (!data.isClaimed(tier.id())) {
                count++;
            }
        }
        return count;
    }

    public int claimedCount(PlayerData data) {
        int count = 0;
        for (RewardTier tier : registry().tiers()) {
            if (data.isClaimed(tier.id())) {
                count++;
            }
        }
        return count;
    }

    /** "3h 10m" until the next unreached tier, or the configured maxed-out text. */
    public String nextRewardIn(PlayerData data) {
        long seconds = data.playtimeSeconds();
        RewardTier next = registry().next(seconds);
        return next == null ? plugin.settings().maxedOut() : TimeFormat.briefCeil(next.seconds() - seconds, 2);
    }

    public String nextRewardName(PlayerData data) {
        RewardTier next = registry().next(data.playtimeSeconds());
        return next == null ? plugin.settings().maxedOut() : next.displayName();
    }

    /** Tags shared by every message/menu for a given player. */
    public TagResolver playerTags(Player player, PlayerData data) {
        long seconds = data.playtimeSeconds();
        return TagResolver.resolver(
                Placeholder.unparsed("player", player.getName()),
                Placeholder.unparsed("playtime", TimeFormat.full(seconds)),
                Placeholder.unparsed("playtime_short", TimeFormat.brief(seconds, 2)),
                Placeholder.unparsed("claimed", Integer.toString(claimedCount(data))),
                Placeholder.unparsed("total", Integer.toString(registry().size())),
                Placeholder.unparsed("ready", Integer.toString(readyTiers(player, data).size())),
                Placeholder.unparsed("next", nextRewardIn(data)));
    }

    public TagResolver tierTags(RewardTier tier, PlayerData data) {
        return TagResolver.resolver(
                Placeholder.unparsed("time", tier.displayName()),
                Placeholder.unparsed("reward", tier.displayName()),
                Placeholder.unparsed("tier", tier.id()),
                Placeholder.unparsed("remaining",
                        TimeFormat.briefCeil(Math.max(0, tier.seconds() - data.playtimeSeconds()), 2)));
    }

    // ---- claiming -----------------------------------------------------------

    public ClaimResult claim(Player player, PlayerData data, RewardTier tier, boolean feedback) {
        ClaimResult result = tryClaim(player, data, tier);
        if (feedback) {
            TagResolver tags = TagResolver.resolver(playerTags(player, data), tierTags(tier, data));
            switch (result) {
                case SUCCESS -> {
                    plugin.messages().send(player, "reward-claimed", tags);
                    plugin.settings().claimSound().play(player);
                }
                case ALREADY_CLAIMED -> {
                    plugin.messages().send(player, "reward-already-claimed", tags);
                    plugin.settings().denySound().play(player);
                }
                case NOT_READY -> {
                    plugin.messages().send(player, "reward-not-ready", tags);
                    plugin.settings().denySound().play(player);
                }
                case NO_PERMISSION -> {
                    plugin.messages().send(player, "reward-no-permission", tags);
                    plugin.settings().denySound().play(player);
                }
            }
        }
        return result;
    }

    private ClaimResult tryClaim(Player player, PlayerData data, RewardTier tier) {
        if (data.isClaimed(tier.id())) {
            return ClaimResult.ALREADY_CLAIMED;
        }
        if (data.playtimeSeconds() < tier.seconds()) {
            return ClaimResult.NOT_READY;
        }
        if (!player.hasPermission(CLAIM_PERMISSION) || !hasAccess(player, tier)) {
            return ClaimResult.NO_PERMISSION;
        }
        // Mark first (atomic) so double-clicks / macros can never deliver twice,
        // and persist immediately so a crash can't re-enable the reward.
        if (!data.markClaimed(tier.id())) {
            return ClaimResult.ALREADY_CLAIMED;
        }
        plugin.data().saveAsync(data);
        deliver(player, tier);
        return ClaimResult.SUCCESS;
    }

    public int claimAll(Player player, PlayerData data) {
        int count = 0;
        for (RewardTier tier : readyTiers(player, data)) {
            if (claim(player, data, tier, false) == ClaimResult.SUCCESS) {
                count++;
            }
        }
        if (count > 0) {
            plugin.settings().claimSound().play(player);
        }
        return count;
    }

    private void deliver(Player player, RewardTier tier) {
        TagResolver tags = TagResolver.resolver(plugin.messages().prefix(),
                Placeholder.unparsed("player", player.getName()),
                Placeholder.unparsed("time", tier.displayName()),
                Placeholder.unparsed("tier", tier.id()));

        for (RewardAction action : tier.actions()) {
            String value = action.value()
                    .replace("%player%", player.getName())
                    .replace("%uuid%", player.getUniqueId().toString())
                    .replace("%tier%", tier.id())
                    .replace("%time%", tier.displayName());
            try {
                switch (action.type()) {
                    case CONSOLE -> Bukkit.dispatchCommand(Bukkit.getConsoleSender(), value);
                    case PLAYER -> player.performCommand(value);
                    case MESSAGE -> player.sendMessage(Text.parse(value, tags));
                    case BROADCAST -> Bukkit.getServer().sendMessage(Text.parse(value, tags));
                }
            } catch (Exception ex) {
                plugin.getLogger().log(Level.SEVERE,
                        "Reward action failed for tier '" + tier.id() + "': " + action.value(), ex);
            }
        }

        if (!tier.items().isEmpty()) {
            ItemStack[] stacks = tier.items().stream().map(ItemStack::clone).toArray(ItemStack[]::new);
            Map<Integer, ItemStack> overflow = player.getInventory().addItem(stacks);
            overflow.values().forEach(stack ->
                    player.getWorld().dropItemNaturally(player.getLocation(), stack, item -> item.setOwner(player.getUniqueId())));
        }

        for (String node : tier.permissions()) {
            try {
                plugin.permissionGranter().grant(player, node);
            } catch (Exception ex) {
                plugin.getLogger().log(Level.SEVERE, "Failed to grant " + node + " to " + player.getName(), ex);
            }
        }
    }
}
