package me.naxzyauxxy.playtimerewards.gui;

import me.naxzyauxxy.playtimerewards.PlaytimeRewardsPlugin;
import me.naxzyauxxy.playtimerewards.config.ItemTemplate;
import me.naxzyauxxy.playtimerewards.config.MenuSettings;
import me.naxzyauxxy.playtimerewards.data.PlayerData;
import me.naxzyauxxy.playtimerewards.reward.RewardService;
import me.naxzyauxxy.playtimerewards.reward.RewardTier;
import me.naxzyauxxy.playtimerewards.reward.TierState;
import me.naxzyauxxy.playtimerewards.util.Text;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.UnaryOperator;

/**
 * The "✹ Playtime | Rewards" chest menu.
 *
 * <pre>
 *  row 1  . . . . . . . . .
 *  row 2  . R R R R R R R .      R = reward tier (claimed / ready / in progress)
 *  row 3  . R R R R R R R .
 *  row 4  . R R R R R R R .
 *  row 5  . R R R R R R R .
 *  row 6  &lt; . . . H . . . &gt;      H = "YOUR PLAYTIME" head, arrows only if &gt;1 page
 * </pre>
 *
 * A custom {@link InventoryHolder} identifies the menu safely (titles are never compared).
 */
public final class RewardsMenu implements InventoryHolder {

    private final PlaytimeRewardsPlugin plugin;
    private final Player viewer;
    private final MenuSettings layout;
    private final Inventory inventory;
    private final Map<Integer, RewardTier> slotTiers = new HashMap<>();
    private int page;
    private int pages = 1;
    private long lastClick;

    private RewardsMenu(PlaytimeRewardsPlugin plugin, Player viewer, PlayerData data, int page) {
        this.plugin = plugin;
        this.viewer = viewer;
        this.layout = plugin.menu();
        this.page = page;
        TagResolver tags = plugin.rewardService().playerTags(viewer, data);
        this.inventory = Bukkit.createInventory(this, layout.size(),
                Text.parse(plugin.preprocessor(viewer).apply(layout.title()), tags));
    }

    /** Opens the menu (main thread). */
    public static void open(PlaytimeRewardsPlugin plugin, Player player) {
        PlayerData data = plugin.data().get(player.getUniqueId());
        if (data == null) {
            plugin.messages().send(player, "data-loading");
            return;
        }
        RewardsMenu menu = new RewardsMenu(plugin, player, data, 0);
        menu.render();
        player.openInventory(menu.inventory);
        if (plugin.settings().sendPlaytimeOnOpen()) {
            plugin.messages().send(player, "playtime-self", plugin.rewardService().playerTags(player, data));
        }
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inventory;
    }

    public Player viewer() {
        return viewer;
    }

    // ---- rendering ----------------------------------------------------------

    public void render() {
        PlayerData data = plugin.data().get(viewer.getUniqueId());
        if (data == null) {
            viewer.closeInventory();
            return;
        }
        RewardService service = plugin.rewardService();
        UnaryOperator<String> pre = plugin.preprocessor(viewer);
        TagResolver playerTags = service.playerTags(viewer, data);

        ItemStack[] contents = new ItemStack[inventory.getSize()];
        if (layout.fillerEnabled()) {
            ItemStack filler = layout.filler().build(playerTags, List.of(), pre, null);
            Arrays.fill(contents, filler);
        }

        // --- reward tiers (paginated)
        List<RewardTier> tiers = plugin.rewards().tiers();
        int[] slots = layout.rewardSlots();
        pages = Math.max(1, (tiers.size() + slots.length - 1) / slots.length);
        page = Math.max(0, Math.min(page, pages - 1));
        slotTiers.clear();
        for (int i = 0; i < slots.length; i++) {
            int index = page * slots.length + i;
            if (index >= tiers.size()) {
                break;
            }
            RewardTier tier = tiers.get(index);
            TierState state = service.state(viewer, data, tier);
            TagResolver tags = TagResolver.resolver(playerTags, service.tierTags(tier, data));
            contents[slots[i]] = layout.state(state).build(tags, tier.lore(), pre, null);
            slotTiers.put(slots[i], tier);
        }

        // --- player head
        ItemTemplate info = layout.info();
        contents[info.slot()] = info.build(playerTags, List.of(), pre, viewer);

        // --- page arrows
        if (pages > 1) {
            TagResolver pageTags = TagResolver.resolver(playerTags,
                    Placeholder.unparsed("page", Integer.toString(page + 1)),
                    Placeholder.unparsed("pages", Integer.toString(pages)));
            if (page > 0) {
                contents[layout.previousPage().slot()] = layout.previousPage().build(pageTags, List.of(), pre, null);
            }
            if (page < pages - 1) {
                contents[layout.nextPage().slot()] = layout.nextPage().build(pageTags, List.of(), pre, null);
            }
        }
        inventory.setContents(contents);
    }

    // ---- interaction --------------------------------------------------------

    public void handleClick(int slot) {
        long now = System.currentTimeMillis();
        if (now - lastClick < layout.clickCooldownMs()) {
            return;
        }
        lastClick = now;

        PlayerData data = plugin.data().get(viewer.getUniqueId());
        if (data == null) {
            return;
        }

        if (slot == layout.info().slot()) {
            plugin.settings().refreshSound().play(viewer);
            render();
            return;
        }
        if (pages > 1 && slot == layout.previousPage().slot() && page > 0) {
            page--;
            plugin.settings().pageSound().play(viewer);
            render();
            return;
        }
        if (pages > 1 && slot == layout.nextPage().slot() && page < pages - 1) {
            page++;
            plugin.settings().pageSound().play(viewer);
            render();
            return;
        }

        RewardTier tier = slotTiers.get(slot);
        if (tier == null) {
            return;
        }
        switch (plugin.rewardService().state(viewer, data, tier)) {
            case READY -> plugin.rewardService().claim(viewer, data, tier, true);
            case LOCKED -> plugin.settings().refreshSound().play(viewer); // "CLICK To Refresh"
            case CLAIMED, RESTRICTED -> plugin.settings().denySound().play(viewer);
        }
        render();
    }
}
