package dev.everydaythings.graph.library.mapdb;

import dev.everydaythings.graph.encoding.CgCbor;
import dev.everydaythings.graph.encoding.Encoding;
import dev.everydaythings.graph.library.data.DataByteStore;
import dev.everydaythings.graph.library.data.DataStore;
import lombok.Getter;

import java.nio.file.Path;
import java.util.Objects;

/**
 * MapDB-backed {@link DataByteStore}. Provides encoder; all storage work
 * comes from {@link MapDbStore} defaults and all Datum-aware logic from
 * {@link DataByteStore} defaults.
 */
public final class MapDbDataStore
        implements MapDbStore<DataStore.Column>, DataByteStore {

    @Getter
    private final MapDbStore.Opened<DataStore.Column> opened;

    @Getter
    private final Path path;

    private final Encoding encoder;

    private MapDbDataStore(MapDbStore.Opened<DataStore.Column> opened, Path path, Encoding encoder) {
        this.opened = Objects.requireNonNull(opened, "opened");
        this.path = path;
        this.encoder = Objects.requireNonNull(encoder, "encoder");
    }

    /** Open or create a file-backed MapDB DataStore at {@code path/data.mapdb}. */
    public static MapDbDataStore atPath(Path path) {
        return atPath(path, CgCbor.codec());
    }

    /** Open or create with an explicit encoder. */
    public static MapDbDataStore atPath(Path path, Encoding encoder) {
        Objects.requireNonNull(path, "path");
        try {
            java.nio.file.Files.createDirectories(path);
        } catch (java.io.IOException e) {
            throw new RuntimeException("Failed to create " + path, e);
        }
        Path file = path.resolve("data.mapdb");
        return new MapDbDataStore(MapDbStore.file(file, DataStore.Column.class), file, encoder);
    }

    /** In-memory MapDB DataStore — for tests that want MapDB semantics without disk. */
    public static MapDbDataStore inMemory() {
        return new MapDbDataStore(
                MapDbStore.memory(DataStore.Column.class), null, CgCbor.codec());
    }

    @Override
    public Encoding rawEncoder() {
        return encoder;
    }
}
