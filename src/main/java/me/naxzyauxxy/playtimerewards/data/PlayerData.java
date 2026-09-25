package me.naxzyauxxy.playtimerewards.data;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Live, thread-safe player record. Written on the main thread (tracker, claims) and read from
 * any thread (PlaceholderAPI is frequently called async by scoreboard plugins).
 */
public final class PlayerData {

    private final UUID uuid;
    private volatile String name;
    private final AtomicLong playtimeMillis;
    private final Set<String> claimed = ConcurrentHashMap.newKeySet();
    private volatile boolean dirty;
    /** True when no file existed - used to seed from the vanilla statistic once. */
    private final boolean fresh;

    public PlayerData(UUID uuid, String name, long playtimeMillis, Iterable<String> claimed, boolean fresh) {
        this.uuid = uuid;
        this.name = name;
        this.playtimeMillis = new AtomicLong(Math.max(0, playtimeMillis));
        if (claimed != null) {
            claimed.forEach(this.claimed::add);
        }
        this.fresh = fresh;
        this.dirty = fresh;
    }

    public static PlayerData fromSnapshot(PlayerSnapshot s, UUID uuid) {
        return new PlayerData(uuid, s.name, s.playtimeMillis, s.claimed, false);
    }

    public PlayerSnapshot snapshot() {
        PlayerSnapshot s = new PlayerSnapshot();
        s.uuid = uuid.toString();
        s.name = name;
        s.playtimeMillis = playtimeMillis.get();
        s.claimed = new ArrayList<>(claimed);
        Collections.sort(s.claimed);
        s.lastSaved = System.currentTimeMillis();
        return s;
    }

    public UUID uuid() {
        return uuid;
    }

    public String name() {
        return name;
    }

    public void name(String name) {
        if (name != null && !name.equals(this.name)) {
            this.name = name;
            dirty = true;
        }
    }

    public boolean isFresh() {
        return fresh;
    }

    // ---- playtime -----------------------------------------------------------

    public long playtimeMillis() {
        return playtimeMillis.get();
    }

    public long playtimeSeconds() {
        return playtimeMillis.get() / 1000L;
    }

    public void addMillis(long millis) {
        if (millis == 0) {
            return;
        }
        playtimeMillis.updateAndGet(v -> Math.max(0, v + millis));
        dirty = true;
    }

    public void setMillis(long millis) {
        playtimeMillis.set(Math.max(0, millis));
        dirty = true;
    }

    // ---- claims -------------------------------------------------------------

    public boolean isClaimed(String tierId) {
        return claimed.contains(tierId);
    }

    /** Atomically marks a tier claimed. @return false if it was already claimed (dupe guard). */
    public boolean markClaimed(String tierId) {
        boolean added = claimed.add(tierId);
        if (added) {
            dirty = true;
        }
        return added;
    }

    public boolean unclaim(String tierId) {
        boolean removed = claimed.remove(tierId);
        if (removed) {
            dirty = true;
        }
        return removed;
    }

    public void clearClaimed() {
        claimed.clear();
        dirty = true;
    }

    public Set<String> claimedView() {
        return Collections.unmodifiableSet(claimed);
    }

    // ---- persistence state --------------------------------------------------

    public boolean isDirty() {
        return dirty;
    }

    public void markDirty() {
        dirty = true;
    }

    void clearDirty() {
        dirty = false;
    }
}
