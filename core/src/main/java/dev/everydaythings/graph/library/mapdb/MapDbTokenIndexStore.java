package dev.everydaythings.graph.library.mapdb;

import dev.everydaythings.graph.library.index.TokenIndexByteStore;
import dev.everydaythings.graph.library.index.TokenIndexStore;
import lombok.Getter;

import java.nio.file.Path;
import java.util.Objects;

/**
 * MapDB-backed {@link TokenIndexByteStore}. Thin wrapper — composes
 * {@link MapDbStore} (storage) with {@link TokenIndexByteStore} (tokenization +
 * posting assembly).
 */
public final class MapDbTokenIndexStore
        implements MapDbStore<TokenIndexStore.Column>, TokenIndexByteStore {

    @Getter
    private final MapDbStore.Opened<TokenIndexStore.Column> opened;

    @Getter
    private final Path path;

    private MapDbTokenIndexStore(MapDbStore.Opened<TokenIndexStore.Column> opened, Path path) {
        this.opened = Objects.requireNonNull(opened, "opened");
        this.path = path;
    }

    /** Open or create at {@code path/token-index.mapdb}. */
    public static MapDbTokenIndexStore atPath(Path path) {
        Objects.requireNonNull(path, "path");
        try {
            java.nio.file.Files.createDirectories(path);
        } catch (java.io.IOException e) {
            throw new RuntimeException("Failed to create " + path, e);
        }
        Path file = path.resolve("token-index.mapdb");
        return new MapDbTokenIndexStore(MapDbStore.file(file, TokenIndexStore.Column.class), file);
    }

    /** In-memory MapDB TokenIndexStore. */
    public static MapDbTokenIndexStore inMemory() {
        return new MapDbTokenIndexStore(MapDbStore.memory(TokenIndexStore.Column.class), null);
    }
}
