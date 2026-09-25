package me.naxzyauxxy.playtimerewards.data;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Optional;
import java.util.UUID;

/**
 * One small JSON file per player: {@code plugins/PlaytimeRewards/data/<uuid>.json}.
 * Writes go to a temp file first and are atomically moved into place, so a crash mid-write
 * can never leave a half-written (corrupt) file behind.
 */
public final class JsonDataStore implements DataStore {

    private final Path directory;
    private final Gson gson = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    public JsonDataStore(Path directory) {
        this.directory = directory;
    }

    @Override
    public void init() throws IOException {
        Files.createDirectories(directory);
    }

    private Path file(UUID uuid) {
        return directory.resolve(uuid + ".json");
    }

    @Override
    public Optional<PlayerSnapshot> load(UUID uuid) throws IOException {
        Path file = file(uuid);
        if (!Files.exists(file)) {
            return Optional.empty();
        }
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            PlayerSnapshot snapshot = gson.fromJson(reader, PlayerSnapshot.class);
            if (snapshot == null) {
                throw new IOException("Empty data file " + file);
            }
            return Optional.of(snapshot);
        } catch (JsonParseException ex) {
            throw new IOException("Corrupt data file " + file, ex);
        }
    }

    @Override
    public void save(PlayerSnapshot snapshot) throws IOException {
        Path target = directory.resolve(snapshot.uuid + ".json");
        Path temp = directory.resolve(snapshot.uuid + ".json.tmp");
        try (Writer writer = Files.newBufferedWriter(temp, StandardCharsets.UTF_8)) {
            gson.toJson(snapshot, writer);
        }
        try {
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException ex) {
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
