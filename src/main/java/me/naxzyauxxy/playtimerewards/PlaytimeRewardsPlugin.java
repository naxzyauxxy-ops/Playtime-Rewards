package me.naxzyauxxy.playtimerewards;

import me.naxzyauxxy.playtimerewards.command.PlaytimeCommand;
import me.naxzyauxxy.playtimerewards.config.MenuSettings;
import me.naxzyauxxy.playtimerewards.config.Messages;
import me.naxzyauxxy.playtimerewards.config.PluginSettings;
import me.naxzyauxxy.playtimerewards.data.JsonDataStore;
import me.naxzyauxxy.playtimerewards.data.PlayerData;
import me.naxzyauxxy.playtimerewards.data.PlayerDataManager;
import me.naxzyauxxy.playtimerewards.gui.RewardsMenu;
import me.naxzyauxxy.playtimerewards.hook.LuckPermsGranter;
import me.naxzyauxxy.playtimerewards.hook.PapiHook;
import me.naxzyauxxy.playtimerewards.hook.PlaytimeExpansion;
import me.naxzyauxxy.playtimerewards.listener.ActivityListener;
import me.naxzyauxxy.playtimerewards.listener.ConnectionListener;
import me.naxzyauxxy.playtimerewards.listener.MenuListener;
import me.naxzyauxxy.playtimerewards.listener.PurpurAfkListener;
import me.naxzyauxxy.playtimerewards.reward.CommandPermissionGranter;
import me.naxzyauxxy.playtimerewards.reward.PermissionGranter;
import me.naxzyauxxy.playtimerewards.reward.RewardRegistry;
import me.naxzyauxxy.playtimerewards.reward.RewardService;
import me.naxzyauxxy.playtimerewards.tracking.AfkManager;
import me.naxzyauxxy.playtimerewards.tracking.PlaytimeTracker;
import me.naxzyauxxy.playtimerewards.util.TimeFormat;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.io.IOException;
import java.util.function.UnaryOperator;
import java.util.logging.Level;

/**
 * Entry point. Owns every service and the (re)load cycle; services read cached config
 * through the accessors below, which are swapped atomically on /playtime admin reload.
 */
public final class PlaytimeRewardsPlugin extends JavaPlugin {

    // Cached configuration (volatile: read by async PAPI requests)
    private volatile PluginSettings settings;
    private volatile MenuSettings menu;
    private volatile Messages messages;
    private volatile RewardRegistry rewards;

    // Services
    private PlayerDataManager dataManager;
    private PlaytimeTracker tracker;
    private AfkManager afkManager;
    private RewardService rewardService;
    private PermissionGranter permissionGranter;
    private boolean papiEnabled;

    // Tasks
    private BukkitTask autosaveTask;
    private BukkitTask menuRefreshTask;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        try {
            loadConfiguration();
        } catch (RuntimeException ex) {
            getLogger().log(Level.SEVERE, "config.yml is invalid - disabling PlaytimeRewards.", ex);
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        try {
            dataManager = new PlayerDataManager(this, new JsonDataStore(getDataFolder().toPath().resolve("data")));
        } catch (IOException ex) {
            getLogger().log(Level.SEVERE, "Could not create the data folder - disabling.", ex);
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        PluginManager pm = getServer().getPluginManager();
        afkManager = new AfkManager(this);
        tracker = new PlaytimeTracker(this);
        rewardService = new RewardService(this);
        permissionGranter = pm.isPluginEnabled("LuckPerms")
                ? new LuckPermsGranter(getLogger())
                : new CommandPermissionGranter(() -> settings.permissionFallbackCommand());
        getLogger().info("Permission rewards granted via " + permissionGranter.name() + ".");

        // Listeners
        pm.registerEvents(new ConnectionListener(this), this);
        pm.registerEvents(new MenuListener(), this);
        pm.registerEvents(new ActivityListener(this), this);
        if (afkManager.purpurAvailable()) {
            pm.registerEvents(new PurpurAfkListener(this), this);
            getLogger().info("Purpur detected - using native AFK API.");
        } else {
            getLogger().warning("Not running on Purpur - Purpur AFK integration disabled (idle timer still works).");
        }

        // Command
        PluginCommand command = getCommand("playtime");
        if (command != null) {
            PlaytimeCommand executor = new PlaytimeCommand(this);
            command.setExecutor(executor);
            command.setTabCompleter(executor);
        }

        // PlaceholderAPI
        papiEnabled = pm.isPluginEnabled("PlaceholderAPI");
        if (papiEnabled) {
            new PlaytimeExpansion(this).register();
            getLogger().info("Registered PlaceholderAPI expansion %playtimerewards_*%.");
        }

        startTasks();

        // Support /reload & plugin managers: pick up players who are already online.
        for (Player player : Bukkit.getOnlinePlayers()) {
            try {
                PlayerData data = dataManager.loadBlocking(player.getUniqueId(), player.getName());
                tracker.startSession(player, data);
            } catch (IOException ex) {
                getLogger().log(Level.SEVERE, "Could not load data for online player " + player.getName(), ex);
            }
        }
        getLogger().info("Enabled with " + rewards.size() + " reward tiers.");
    }

    @Override
    public void onDisable() {
        cancelTasks();
        if (tracker != null) {
            tracker.stop();
            tracker.flushAll(); // credit time up to this exact moment
        }
        // Close open menus so nobody keeps a dead inventory.
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getOpenInventory().getTopInventory().getHolder(false) instanceof RewardsMenu) {
                player.closeInventory();
            }
        }
        if (dataManager != null) {
            dataManager.shutdown(); // blocking flush of all cached data
        }
    }

    // ---- configuration ------------------------------------------------------

    private void loadConfiguration() {
        reloadConfig();
        PluginSettings newSettings = PluginSettings.load(getConfig(), getLogger());
        MenuSettings newMenu = MenuSettings.load(getConfig().getConfigurationSection("gui"), getLogger());
        Messages newMessages = Messages.load(getConfig().getConfigurationSection("messages"));
        TimeFormat.configure(newSettings.timeStyle());
        RewardRegistry newRewards = RewardRegistry.load(getConfig().getConfigurationSection("tiers"), getLogger());
        // Publish only after everything parsed successfully.
        this.settings = newSettings;
        this.menu = newMenu;
        this.messages = newMessages;
        this.rewards = newRewards;
    }

    /** /playtime admin reload */
    public void reload() {
        loadConfiguration();
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getOpenInventory().getTopInventory().getHolder(false) instanceof RewardsMenu) {
                player.closeInventory(); // layout may have changed
            }
        }
        tracker.resyncAll();
        startTasks();
    }

    private void startTasks() {
        cancelTasks();
        tracker.start();
        long autosaveTicks = settings.autosaveMinutes() * 60L * 20L;
        autosaveTask = Bukkit.getScheduler().runTaskTimer(this, dataManager::autosave, autosaveTicks, autosaveTicks);
        long refresh = menu.autoRefreshTicks();
        if (refresh > 0) {
            menuRefreshTask = Bukkit.getScheduler().runTaskTimer(this, () -> {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    if (player.getOpenInventory().getTopInventory().getHolder(false) instanceof RewardsMenu m) {
                        m.render();
                    }
                }
            }, refresh, refresh);
        }
    }

    private void cancelTasks() {
        if (autosaveTask != null) {
            autosaveTask.cancel();
            autosaveTask = null;
        }
        if (menuRefreshTask != null) {
            menuRefreshTask.cancel();
            menuRefreshTask = null;
        }
    }

    // ---- accessors ----------------------------------------------------------

    public PluginSettings settings() {
        return settings;
    }

    public MenuSettings menu() {
        return menu;
    }

    public Messages messages() {
        return messages;
    }

    public RewardRegistry rewards() {
        return rewards;
    }

    public PlayerDataManager data() {
        return dataManager;
    }

    public PlaytimeTracker tracker() {
        return tracker;
    }

    public AfkManager afk() {
        return afkManager;
    }

    public RewardService rewardService() {
        return rewardService;
    }

    public PermissionGranter permissionGranter() {
        return permissionGranter;
    }

    /** PlaceholderAPI pre-processor for menu text, or identity when disabled/unavailable. */
    public UnaryOperator<String> preprocessor(Player player) {
        if (papiEnabled && settings.papiInMenus()) {
            return text -> PapiHook.apply(player, text);
        }
        return UnaryOperator.identity();
    }
}
