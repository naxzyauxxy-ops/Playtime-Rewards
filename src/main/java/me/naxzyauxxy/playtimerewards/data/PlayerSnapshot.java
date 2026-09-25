package me.naxzyauxxy.playtimerewards.data;

import java.util.ArrayList;
import java.util.List;

/**
 * Immutable-by-convention JSON DTO. Taken on the main thread, written on the IO thread,
 * so the live {@link PlayerData} is never serialised while it is being mutated.
 */
public final class PlayerSnapshot {
    public int version = 1;
    public String uuid;
    public String name;
    public long playtimeMillis;
    public List<String> claimed = new ArrayList<>();
    public long lastSaved;
}
