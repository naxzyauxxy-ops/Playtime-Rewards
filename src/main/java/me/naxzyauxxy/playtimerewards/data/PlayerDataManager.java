package me.naxzyauxxy.playtimerewards.data;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;
import java.util.logging.Level;

/**
 * Owns the in-memory cache and all disk IO.
 *
 * <p>Every read and write runs on ONE dedicated IO thread. That gives two guarantees for free:
 * the main thread never blocks on disk, and operations on the same player are strictly ordered
 * (a quick relog can never read a file before the previous quit-save has finished).</p>
 */
public final class PlayerDataManager {

    private final Plugin plugin;
    private final DataStore store;
    private final ExecutorService io;
    private final Map<UUID, PlayerData> cache = new ConcurrentHashMap<>();

    public PlayerDataManager(Plugin plugin, DataStore store) throws IOException {
        this.plugin = plugin;
        this.store = store;
        this.io = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "PlaytimeRewards-IO");
            t.setDaemon(true);
            return t;
        });
        store.init();
    }

    // ---- lookups ------------------------------------------------------------

    @Nullable
    public PlayerData get(UUID uuid) {
        return cache.get(uuid);
    }

    public boolean isLoaded(UUID uuid) {
        return cache.containsKey(uuid);
    }

    public Collection<PlayerData> loaded() {
        return cache.values();
    }

    // ---- loading ------------------------------------------------------------

    private PlayerData readOrCreate(UUID uuid, String name) throws IOException {
        return store.load(uuid)
                .map(snapshot -> {
                    PlayerData data = PlayerData.fromSnapshot(snapshot, uuid);
                    data.name(name);
                    return data;
                })
                .orElseGet(() -> new PlayerData(uuid, name, 0L, List.of(), true));
    }

    /**
     * Loads and caches a player, blocking the CALLING thread (never call on the main thread).
     * Used from AsyncPlayerPreLoginEvent, which already runs off-main.
     */
    public PlayerData loadBlocking(UUID uuid, String name) throws IOException {
        PlayerData existing = cache.get(uuid);
        if (existing != null) {
            return existing;
        }
        try {
            PlayerData data = io.submit(() -> readOrCreate(uuid, name)).get(15, TimeUnit.SECONDS);
            PlayerData raced = cache.putIfAbsent(uuid, data);
            return raced != null ? raced : data;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while loading " + uuid, ex);
        } catch (ExecutionException ex) {
            throw ex.getCause() instanceof IOException io ? io : new IOException(ex.getCause());
        } catch (TimeoutException ex) {
            throw new IOException("Timed out loading " + uuid, ex);
        }
    }

    /** Non-blocking load; the future completes on the IO thread. */
    public CompletableFuture<PlayerData> loadAsync(UUID uuid, String name) {
        PlayerData existing = cache.get(uuid);
        if (existing != null) {
            return CompletableFuture.completedFuture(existing);
        }
        return CompletableFuture.supplyAsync(() -> {
            try {
                PlayerData data = readOrCreate(uuid, name);
                PlayerData raced = cache.putIfAbsent(uuid, data);
                return raced != null ? raced : data;
            } catch (IOException ex) {
                throw new RuntimeException(ex);
            }
        }, io);
    }

    // ---- saving -------------------------------------------------------------

    /** Snapshots on the calling (main) thread, writes on the IO thread. */
    public void saveAsync(PlayerData data) {
        PlayerSnapshot snapshot = takeSnapshot(data);
        io.execute(() -> write(data, snapshot));
    }

    private PlayerSnapshot takeSnapshot(PlayerData data) {
        data.clearDirty(); // cleared BEFORE snapshotting: later changes re-dirty the record
        return data.snapshot();
    }

    private void write(PlayerData data, PlayerSnapshot snapshot) {
        try {
            store.save(snapshot);
        } catch (IOException ex) {
            data.markDirty(); // retry on next autosave
            plugin.getLogger().log(Level.SEVERE, "Failed to save playtime data for " + snapshot.uuid, ex);
        }
    }

    /** Saves and evicts a player that left. */
    public void unload(UUID uuid) {
        PlayerData data = cache.remove(uuid);
        if (data != null) {
            saveAsync(data);
        }
    }

    /**
     * Periodic task (main thread): saves dirty records and evicts entries for players who are no
     * longer online (e.g. logins that were denied after the async pre-login had already loaded them).
     */
    public void autosave() {
        for (PlayerData data : new ArrayList<>(cache.values())) {
            boolean online = Bukkit.getPlayer(data.uuid()) != null;
            if (data.isDirty()) {
                saveAsync(data);
            }
            if (!online) {
                cache.remove(data.uuid(), data);
            }
        }
    }

    /**
     * Applies a change to any player, online or offline.
     * Online -> applied immediately on the calling (main) thread and saved async.
     * Offline -> loaded, modified and saved entirely on the IO thread.
     *
     * @return future with the modified data (completes off-main for offline players)
     */
    public CompletableFuture<PlayerData> modify(UUID uuid, String name, Consumer<PlayerData> change) {
        PlayerData cached = cache.get(uuid);
        if (cached != null) {
            change.accept(cached);
            saveAsync(cached);
            return CompletableFuture.completedFuture(cached);
        }
        return CompletableFuture.supplyAsync(() -> {
            try {
                PlayerData data = cache.get(uuid); // may have logged in meanwhile
                if (data == null) {
                    data = readOrCreate(uuid, name);
                }
                change.accept(data);
                PlayerSnapshot snapshot = takeSnapshot(data);
                store.save(snapshot);
                return data;
            } catch (IOException ex) {
                throw new RuntimeException(ex);
            }
        }, io);
    }

    /** Read-only access to any player (cached or from disk). */
    public CompletableFuture<PlayerData> lookup(UUID uuid, String name) {
        PlayerData cached = cache.get(uuid);
        if (cached != null) {
            return CompletableFuture.completedFuture(cached);
        }
        return CompletableFuture.supplyAsync(() -> {
            try {
                return readOrCreate(uuid, name);
            } catch (IOException ex) {
                throw new RuntimeException(ex);
            }
        }, io);
    }

    /** Flushes everything and stops the IO thread. Blocks - only call from onDisable. */
    public void shutdown() {
        List<Runnable> writes = new ArrayList<>();
        for (PlayerData data : cache.values()) {
            PlayerSnapshot snapshot = takeSnapshot(data);
            writes.add(() -> write(data, snapshot));
        }
        writes.forEach(io::execute);
        io.shutdown();
        try {
            if (!io.awaitTermination(30, TimeUnit.SECONDS)) {
                plugin.getLogger().severe("Timed out waiting for playtime data to save!");
                io.shutdownNow();
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
        store.close();
        cache.clear();
    }
}
