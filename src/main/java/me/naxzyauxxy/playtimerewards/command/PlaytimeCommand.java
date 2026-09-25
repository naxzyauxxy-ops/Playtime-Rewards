package me.naxzyauxxy.playtimerewards.command;

import me.naxzyauxxy.playtimerewards.PlaytimeRewardsPlugin;
import me.naxzyauxxy.playtimerewards.data.PlayerData;
import me.naxzyauxxy.playtimerewards.gui.RewardsMenu;
import me.naxzyauxxy.playtimerewards.reward.RewardTier;
import me.naxzyauxxy.playtimerewards.util.TimeFormat;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.stream.Stream;

/**
 * <pre>
 * /playtime                              open menu            playtimerewards.use
 * /playtime check [player]               chat readout         playtimerewards.check(.others)
 * /playtime claim                        claim all ready      playtimerewards.claim
 * /playtime admin reload                                      playtimerewards.admin.reload
 * /playtime admin set|add|remove &lt;p&gt; &lt;t&gt;                     playtimerewards.admin.modify
 * /playtime admin reset &lt;p&gt; [tier|all]                     playtimerewards.admin.reset
 * /playtime admin info &lt;p&gt;                                 playtimerewards.admin.info
 * </pre>
 */
public final class PlaytimeCommand implements TabExecutor {

    private static final String P_USE = "playtimerewards.use";
    private static final String P_CLAIM = "playtimerewards.claim";
    private static final String P_CHECK = "playtimerewards.check";
    private static final String P_CHECK_OTHERS = "playtimerewards.check.others";
    private static final String P_ADMIN = "playtimerewards.admin";
    private static final String P_RELOAD = "playtimerewards.admin.reload";
    private static final String P_MODIFY = "playtimerewards.admin.modify";
    private static final String P_RESET = "playtimerewards.admin.reset";
    private static final String P_INFO = "playtimerewards.admin.info";

    private final PlaytimeRewardsPlugin plugin;

    public PlaytimeCommand(PlaytimeRewardsPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (args.length == 0) {
            openMenu(sender);
            return true;
        }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "check" -> check(sender, args);
            case "claim", "claimall" -> claimAll(sender);
            case "admin" -> admin(sender, args);
            case "help" -> help(sender);
            default -> {
                // "/playtime <player>" shortcut for staff
                if (sender.hasPermission(P_CHECK_OTHERS)) {
                    check(sender, new String[]{"check", args[0]});
                } else {
                    help(sender);
                }
            }
        }
        return true;
    }

    // ---- player commands ----------------------------------------------------

    private void openMenu(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            help(sender);
            return;
        }
        if (!player.hasPermission(P_USE)) {
            msg(sender, "no-permission");
            return;
        }
        RewardsMenu.open(plugin, player);
    }

    private void check(CommandSender sender, String[] args) {
        if (args.length < 2) {
            if (!(sender instanceof Player player)) {
                msg(sender, "player-only");
                return;
            }
            if (!sender.hasPermission(P_CHECK)) {
                msg(sender, "no-permission");
                return;
            }
            PlayerData data = plugin.data().get(player.getUniqueId());
            if (data == null) {
                msg(sender, "data-loading");
                return;
            }
            plugin.messages().send(player, "playtime-self", plugin.rewardService().playerTags(player, data));
            return;
        }
        if (!sender.hasPermission(P_CHECK_OTHERS)) {
            msg(sender, "no-permission");
            return;
        }
        withTarget(sender, args[1], target -> plugin.data().lookup(target.uuid, target.name)
                .whenComplete((data, err) -> sync(() -> {
                    if (err != null) {
                        fail(sender, err);
                        return;
                    }
                    plugin.messages().send(sender, "playtime-other", targetTags(target.name, data));
                })));
    }

    private void claimAll(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            msg(sender, "player-only");
            return;
        }
        if (!player.hasPermission(P_CLAIM)) {
            msg(sender, "no-permission");
            return;
        }
        PlayerData data = plugin.data().get(player.getUniqueId());
        if (data == null) {
            msg(sender, "data-loading");
            return;
        }
        int count = plugin.rewardService().claimAll(player, data);
        if (count == 0) {
            msg(sender, "claim-all-none");
            plugin.settings().denySound().play(player);
        } else {
            plugin.messages().send(sender, "claim-all-done", Placeholder.unparsed("count", Integer.toString(count)));
        }
    }

    private void help(CommandSender sender) {
        msg(sender, "help");
        if (sender.hasPermission(P_ADMIN) || hasAnyAdmin(sender)) {
            msg(sender, "admin-help");
        }
    }

    // ---- admin --------------------------------------------------------------

    private void admin(CommandSender sender, String[] args) {
        if (!hasAnyAdmin(sender)) {
            msg(sender, "no-permission");
            return;
        }
        if (args.length < 2) {
            msg(sender, "admin-help");
            return;
        }
        String sub = args[1].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "reload" -> {
                if (!sender.hasPermission(P_RELOAD)) {
                    msg(sender, "no-permission");
                    return;
                }
                plugin.reload();
                plugin.messages().send(sender, "reloaded",
                        Placeholder.unparsed("count", Integer.toString(plugin.rewards().size())));
            }
            case "set", "add", "remove" -> modifyTime(sender, sub, args);
            case "reset" -> reset(sender, args);
            case "info" -> info(sender, args);
            default -> msg(sender, "admin-help");
        }
    }

    private void modifyTime(CommandSender sender, String mode, String[] args) {
        if (!sender.hasPermission(P_MODIFY)) {
            msg(sender, "no-permission");
            return;
        }
        if (args.length < 4) {
            msg(sender, "admin-help");
            return;
        }
        long seconds = TimeFormat.parse(args[3]);
        if (seconds < 0) {
            plugin.messages().send(sender, "invalid-time", Placeholder.unparsed("input", args[3]));
            return;
        }
        long millis = seconds * 1000L;
        Consumer<PlayerData> change = switch (mode) {
            case "set" -> d -> d.setMillis(millis);
            case "add" -> d -> d.addMillis(millis);
            default -> d -> d.addMillis(-millis);
        };
        withTarget(sender, args[2], target -> plugin.data().modify(target.uuid, target.name, change)
                .whenComplete((data, err) -> sync(() -> {
                    if (err != null) {
                        fail(sender, err);
                        return;
                    }
                    afterEdit(target.uuid);
                    plugin.messages().send(sender, "admin-" + mode, TagResolver.resolver(
                            targetTags(target.name, data),
                            Placeholder.unparsed("amount", TimeFormat.compact(seconds))));
                })));
    }

    private void reset(CommandSender sender, String[] args) {
        if (!sender.hasPermission(P_RESET)) {
            msg(sender, "no-permission");
            return;
        }
        if (args.length < 3) {
            msg(sender, "admin-help");
            return;
        }
        String which = args.length >= 4 ? args[3] : "all";
        boolean all = which.equalsIgnoreCase("all");
        RewardTier tier = all ? null : plugin.rewards().byId(which);
        if (!all && tier == null) {
            plugin.messages().send(sender, "unknown-tier", Placeholder.unparsed("input", which));
            return;
        }
        Consumer<PlayerData> change = all ? PlayerData::clearClaimed : d -> d.unclaim(tier.id());
        withTarget(sender, args[2], target -> plugin.data().modify(target.uuid, target.name, change)
                .whenComplete((data, err) -> sync(() -> {
                    if (err != null) {
                        fail(sender, err);
                        return;
                    }
                    afterEdit(target.uuid);
                    plugin.messages().send(sender, all ? "admin-reset-all" : "admin-reset-tier",
                            TagResolver.resolver(targetTags(target.name, data),
                                    Placeholder.unparsed("tier", all ? "all" : tier.id())));
                })));
    }

    private void info(CommandSender sender, String[] args) {
        if (!sender.hasPermission(P_INFO)) {
            msg(sender, "no-permission");
            return;
        }
        if (args.length < 3) {
            msg(sender, "admin-help");
            return;
        }
        withTarget(sender, args[2], target -> plugin.data().lookup(target.uuid, target.name)
                .whenComplete((data, err) -> sync(() -> {
                    if (err != null) {
                        fail(sender, err);
                        return;
                    }
                    List<String> claimed = plugin.rewards().tiers().stream()
                            .filter(t -> data.isClaimed(t.id())).map(RewardTier::displayName).toList();
                    plugin.messages().send(sender, "admin-info", TagResolver.resolver(
                            targetTags(target.name, data),
                            Placeholder.unparsed("target_claimed_list", claimed.isEmpty() ? "-" : String.join(", ", claimed))));
                })));
    }

    /** Re-sync milestone counters and refresh an open menu after an admin edit. */
    private void afterEdit(UUID uuid) {
        plugin.tracker().resync(uuid);
        Player online = Bukkit.getPlayer(uuid);
        if (online != null && online.getOpenInventory().getTopInventory().getHolder(false) instanceof RewardsMenu menu) {
            menu.render();
        }
    }

    // ---- helpers ------------------------------------------------------------

    private record Target(UUID uuid, String name) {
    }

    /** Resolves online players first, then Paper's offline cache (no blocking web lookup). */
    private void withTarget(CommandSender sender, String input, Consumer<Target> action) {
        Player online = Bukkit.getPlayerExact(input);
        if (online != null) {
            action.accept(new Target(online.getUniqueId(), online.getName()));
            return;
        }
        OfflinePlayer offline = Bukkit.getOfflinePlayerIfCached(input);
        if (offline == null || (!offline.hasPlayedBefore() && !offline.isOnline())) {
            plugin.messages().send(sender, "player-not-found", Placeholder.unparsed("input", input));
            return;
        }
        String name = offline.getName() != null ? offline.getName() : input;
        action.accept(new Target(offline.getUniqueId(), name));
    }

    private TagResolver targetTags(String name, PlayerData data) {
        long seconds = data.playtimeSeconds();
        return TagResolver.resolver(
                Placeholder.unparsed("target", name),
                Placeholder.unparsed("target_playtime", TimeFormat.full(seconds)),
                Placeholder.unparsed("target_claimed", Integer.toString(plugin.rewardService().claimedCount(data))),
                Placeholder.unparsed("total", Integer.toString(plugin.rewards().size())));
    }

    private void sync(Runnable task) {
        if (Bukkit.isPrimaryThread()) {
            task.run();
        } else {
            Bukkit.getScheduler().runTask(plugin, task);
        }
    }

    private void fail(CommandSender sender, Throwable err) {
        plugin.getLogger().log(Level.SEVERE, "Playtime admin command failed", err);
        sender.sendMessage(Component.text("Internal error - see console.", NamedTextColor.RED));
    }

    private void msg(CommandSender sender, String key) {
        plugin.messages().send(sender, key);
    }

    private static boolean hasAnyAdmin(CommandSender s) {
        return s.hasPermission(P_ADMIN) || s.hasPermission(P_RELOAD) || s.hasPermission(P_MODIFY)
                || s.hasPermission(P_RESET) || s.hasPermission(P_INFO);
    }

    // ---- tab completion -----------------------------------------------------

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                                @NotNull String alias, @NotNull String[] args) {
        List<String> options = new ArrayList<>();
        if (args.length == 1) {
            options.add("check");
            options.add("claim");
            options.add("help");
            if (hasAnyAdmin(sender)) {
                options.add("admin");
            }
        } else if (args.length == 2 && args[0].equalsIgnoreCase("check") && sender.hasPermission(P_CHECK_OTHERS)) {
            return players(args[1]);
        } else if (args[0].equalsIgnoreCase("admin") && hasAnyAdmin(sender)) {
            if (args.length == 2) {
                Stream.of("reload", "set", "add", "remove", "reset", "info").forEach(options::add);
            } else if (args.length == 3 && !args[1].equalsIgnoreCase("reload")) {
                return players(args[2]);
            } else if (args.length == 4) {
                switch (args[1].toLowerCase(Locale.ROOT)) {
                    case "set", "add", "remove" -> Stream.of("30m", "1h", "12h", "1d", "7d").forEach(options::add);
                    case "reset" -> {
                        options.add("all");
                        plugin.rewards().tiers().forEach(t -> options.add(t.id()));
                    }
                    default -> {
                    }
                }
            }
        }
        String last = args[args.length - 1].toLowerCase(Locale.ROOT);
        return options.stream().filter(o -> o.toLowerCase(Locale.ROOT).startsWith(last)).toList();
    }

    private static List<String> players(String prefix) {
        String p = prefix.toLowerCase(Locale.ROOT);
        return Bukkit.getOnlinePlayers().stream().map(Player::getName)
                .filter(n -> n.toLowerCase(Locale.ROOT).startsWith(p)).toList();
    }
}
