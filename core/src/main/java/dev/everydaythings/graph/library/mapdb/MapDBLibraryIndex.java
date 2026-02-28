package dev.everydaythings.graph.library.mapdb;

import dev.everydaythings.graph.library.LibraryIndex;
import lombok.Getter;

import java.nio.file.Path;

/**
 * MapDB-backed LibraryIndex for relation queries.
 *
 * <p>Supports both in-memory and file-based storage.
 */
public final class MapDBLibraryIndex implements LibraryIndex, MapDBStore<LibraryIndex.Column> {

    @Getter
    private final MapDBStore.Opened<Column> opened;

    public static MapDBLibraryIndex memory() {
        return new MapDBLibraryIndex(MapDBStore.memory(Column.class));
    }

    public static MapDBLibraryIndex file(Path path) {
        return new MapDBLibraryIndex(MapDBStore.file(path, Column.class));
    }

    private MapDBLibraryIndex(MapDBStore.Opened<Column> opened) {
        this.opened = opened;
    }
}
