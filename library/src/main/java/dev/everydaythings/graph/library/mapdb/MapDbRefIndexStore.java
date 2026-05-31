package dev.everydaythings.graph.library.mapdb;

import dev.everydaythings.graph.encoding.CgCbor;
import dev.everydaythings.graph.encoding.Encoding;
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

    private final Encoding encoder;

    private MapDbRefIndexStore(MapDbStore.Opened<RefIndexStore.Column> opened, Path path,
                               Encoding encoder) {
        this.opened = Objects.requireNonNull(opened, "opened");
        this.path = path;
        this.encoder = Objects.requireNonNull(encoder, "encoder");
    }

    /** Open or create at {@code path/ref-index.mapdb} with default {@link CgCbor} encoder. */
    public static MapDbRefIndexStore atPath(Path path) {
        return atPath(path, CgCbor.codec());
    }

    /** Open or create at {@code path/ref-index.mapdb} with the given encoder. */
    public static MapDbRefIndexStore atPath(Path path, Encoding encoder) {
        Objects.requireNonNull(path, "path");
        try {
            java.nio.file.Files.createDirectories(path);
        } catch (java.io.IOException e) {
            throw new RuntimeException("Failed to create " + path, e);
        }
        Path file = path.resolve("ref-index.mapdb");
        return new MapDbRefIndexStore(MapDbStore.file(file, RefIndexStore.Column.class), file, encoder);
    }

    /** In-memory MapDB RefIndexStore with default {@link CgCbor} encoder. */
    public static MapDbRefIndexStore inMemory() {
        return inMemory(CgCbor.codec());
    }

    /** In-memory MapDB RefIndexStore with the given encoder. */
    public static MapDbRefIndexStore inMemory(Encoding encoder) {
        return new MapDbRefIndexStore(MapDbStore.memory(RefIndexStore.Column.class), null, encoder);
    }

    @Override
    public Encoding rawEncoder() {
        return encoder;
    }
}
