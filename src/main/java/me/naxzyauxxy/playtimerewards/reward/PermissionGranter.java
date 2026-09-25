package me.naxzyauxxy.playtimerewards.reward;

import org.bukkit.entity.Player;

/** Grants a permanent permission node as a reward. */
public interface PermissionGranter {

    void grant(Player player, String node);

    String name();
}
