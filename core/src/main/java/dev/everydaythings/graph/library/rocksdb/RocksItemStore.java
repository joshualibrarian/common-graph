package dev.everydaythings.graph.library.rocksdb;

import dev.everydaythings.graph.library.ItemStore;
import lombok.Getter;
import lombok.experimental.Accessors;

import java.nio.file.Path;

/**
 * RocksDB-backed ItemStore for block storage.
 *
 * <p>Stores blocks (manifests, relations, payloads) by content-hash.
 * This is infrastructure - part of Library, not a component itself.
 *
 * <p>All persist/retrieve operations are implemented by the ItemStore default
 * methods using the ByteStore methods provided by RocksStore.
 *
 * <p>Service lifecycle (isOpen, start, stop, close) is provided by RocksStore.
 */
public class RocksItemStore implements ItemStore, RocksStore<ItemStore.Column> {

    @Getter
    private final RocksStore.Opened<Column> opened;

    /** Storage path for this store. */
    @Getter
    @Accessors(fluent = false)
    private final Path path;

    /**
     * Open a RocksItemStore at the given path.
     * Creates the database if it doesn't exist.
     */
    public static RocksItemStore open(Path path) {
        return new RocksItemStore(path);
    }

    public RocksItemStore(Path rootPath) {
        this.path = rootPath;
        this.opened = RocksStore.open(rootPath, Column.class);
    }

    /**
     * Resolve conflict between ItemStore.close() and RocksStore.close().
     * RocksStore handles the RocksDB lifecycle.
     */
    @Override
    public void close() {
        RocksStore.super.close();
    }

    /**
     * Resolve conflict between ItemStore.path() and RocksStore.path().
     * Both return the same value, but we must pick one.
     */
    @Override
    public java.util.Optional<Path> path() {
        return java.util.Optional.ofNullable(path);
    }
}
