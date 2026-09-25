package me.naxzyauxxy.playtimerewards.reward;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.function.Supplier;

/** Fallback when LuckPerms is absent: runs the configured console command per node. */
public final class CommandPermissionGranter implements PermissionGranter {

    private final Supplier<String> template;

    public CommandPermissionGranter(Supplier<String> template) {
        this.template = template;
    }

    @Override
    public void grant(Player player, String node) {
        String cmd = template.get()
                .replace("%player%", player.getName())
                .replace("%uuid%", player.getUniqueId().toString())
                .replace("%node%", node);
        if (!cmd.isBlank()) {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd.startsWith("/") ? cmd.substring(1) : cmd);
        }
    }

    @Override
    public String name() {
        return "console-command";
    }
}
