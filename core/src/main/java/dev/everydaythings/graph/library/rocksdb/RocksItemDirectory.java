package dev.everydaythings.graph.library.rocksdb;

import dev.everydaythings.graph.library.ItemStore;
import dev.everydaythings.graph.library.directory.ItemDirectory;
import lombok.Getter;
import lombok.experimental.Accessors;

import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * RocksDB-backed ItemDirectory for tracking item locations.
 *
 * <p>All operations are provided by {@link ItemDirectory} default methods.
 * Service lifecycle (isOpen, start, stop, close) is provided by RocksStore.
 */
public final class RocksItemDirectory implements ItemDirectory, RocksStore<ItemDirectory.Column> {

    @Getter
    private final Opened<Column> opened;

    @Getter
    private final Map<Path, ItemStore> storeRegistry = new ConcurrentHashMap<>();

    /** Storage path for this directory. */
    @Getter
    @Accessors(fluent = false)
    private final Path path;

    public static RocksItemDirectory open(Path path) {
        return new RocksItemDirectory(path);
    }

    private RocksItemDirectory(Path rootPath) {
        this.path = rootPath;
        this.opened = RocksStore.open(rootPath, Column.class);
    }

    /**
     * Override close to also clear the store registry.
     */
    @Override
    public void close() {
        RocksStore.super.close();
        storeRegistry.clear();
    }
}
