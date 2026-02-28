package dev.everydaythings.graph.library.mapdb;

import dev.everydaythings.graph.library.ItemStore;
import dev.everydaythings.graph.library.directory.ItemDirectory;
import lombok.Getter;

import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * MapDB-backed ItemDirectory for tracking item locations.
 *
 * <p>Supports both in-memory and file-based storage.
 */
public final class MapDBItemDirectory implements ItemDirectory, MapDBStore<ItemDirectory.Column> {

    @Getter
    private final MapDBStore.Opened<Column> opened;

    @Getter
    private final Map<Path, ItemStore> storeRegistry = new ConcurrentHashMap<>();

    public static MapDBItemDirectory memory() {
        return new MapDBItemDirectory(MapDBStore.memory(Column.class));
    }

    public static MapDBItemDirectory file(Path path) {
        return new MapDBItemDirectory(MapDBStore.file(path, Column.class));
    }

    private MapDBItemDirectory(MapDBStore.Opened<Column> opened) {
        this.opened = opened;
    }

    @Override
    public void close() {
        opened.close();
        storeRegistry.clear();
    }
}
