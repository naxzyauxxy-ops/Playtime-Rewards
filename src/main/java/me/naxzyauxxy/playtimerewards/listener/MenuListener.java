package me.naxzyauxxy.playtimerewards.listener;

import me.naxzyauxxy.playtimerewards.gui.RewardsMenu;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;

/**
 * Makes the rewards menu read-only (blocks shift-click, number keys, double-click collect,
 * drags, offhand swaps) and forwards top-inventory clicks to the menu.
 */
public final class MenuListener implements Listener {

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onClick(InventoryClickEvent event) {
        Inventory top = event.getView().getTopInventory();
        if (!(top.getHolder(false) instanceof RewardsMenu menu)) {
            return;
        }
        event.setCancelled(true);
        if (event.getWhoClicked() instanceof Player && event.getClickedInventory() == top) {
            menu.handleClick(event.getRawSlot());
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder(false) instanceof RewardsMenu) {
            event.setCancelled(true);
        }
    }
}
