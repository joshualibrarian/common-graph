package dev.everydaythings.graph.library.mapdb;

import dev.everydaythings.graph.library.index.RefIndexByteStore;
import dev.everydaythings.graph.library.index.RefIndexStore;
import lombok.Getter;

import java.nio.file.Path;
import java.util.Objects;

/**
 * MapDB-backed {@link RefIndexByteStore}. Thin wrapper — composes
 * {@link MapDbStore} (storage) with {@link RefIndexByteStore} (indexing logic).
 */
public final class MapDbRefIndexStore
        implements MapDbStore<RefIndexStore.Column>, RefIndexByteStore {

    @Getter
    private final MapDbStore.Opened<RefIndexStore.Column> opened;

    @Getter
    private final Path path;

    private MapDbRefIndexStore(MapDbStore.Opened<RefIndexStore.Column> opened, Path path) {
        this.opened = Objects.requireNonNull(opened, "opened");
        this.path = path;
    }

    /** Open or create at {@code path/ref-index.mapdb}. */
    public static MapDbRefIndexStore atPath(Path path) {
        Objects.requireNonNull(path, "path");
        try {
            java.nio.file.Files.createDirectories(path);
        } catch (java.io.IOException e) {
            throw new RuntimeException("Failed to create " + path, e);
        }
        Path file = path.resolve("ref-index.mapdb");
        return new MapDbRefIndexStore(MapDbStore.file(file, RefIndexStore.Column.class), file);
    }

    /** In-memory MapDB RefIndexStore. */
    public static MapDbRefIndexStore inMemory() {
        return new MapDbRefIndexStore(MapDbStore.memory(RefIndexStore.Column.class), null);
    }
}
