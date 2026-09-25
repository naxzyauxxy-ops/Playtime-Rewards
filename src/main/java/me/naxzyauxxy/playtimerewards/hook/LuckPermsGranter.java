package me.naxzyauxxy.playtimerewards.hook;

import me.naxzyauxxy.playtimerewards.reward.PermissionGranter;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.node.Node;
import org.bukkit.entity.Player;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Adds nodes through the LuckPerms API (async, persistent, synced across a network).
 * Only instantiated when LuckPerms is enabled, so the class never loads otherwise.
 */
public final class LuckPermsGranter implements PermissionGranter {

    private final LuckPerms luckPerms;
    private final Logger logger;

    public LuckPermsGranter(Logger logger) {
        this.luckPerms = LuckPermsProvider.get();
        this.logger = logger;
    }

    @Override
    public void grant(Player player, String node) {
        luckPerms.getUserManager()
                .modifyUser(player.getUniqueId(), user -> user.data().add(Node.builder(node).value(true).build()))
                .exceptionally(ex -> {
                    logger.log(Level.SEVERE, "LuckPerms failed to grant " + node + " to " + player.getName(), ex);
                    return null;
                });
    }

    @Override
    public String name() {
        return "LuckPerms";
    }
}
