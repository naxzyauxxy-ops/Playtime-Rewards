package me.naxzyauxxy.playtimerewards.data;

import java.io.IOException;
import java.util.Optional;
import java.util.UUID;

/**
 * Storage backend. Implementations are only ever called from the single IO thread owned by
 * {@link PlayerDataManager}, so they don't need their own synchronisation.
 */
public interface DataStore {

    void init() throws IOException;

    Optional<PlayerSnapshot> load(UUID uuid) throws IOException;

    void save(PlayerSnapshot snapshot) throws IOException;

    default void close() {
    }
}
