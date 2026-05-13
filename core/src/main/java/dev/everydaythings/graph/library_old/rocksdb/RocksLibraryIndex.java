package dev.everydaythings.graph.library_old.rocksdb;

import dev.everydaythings.graph.library_old.LibraryIndex;
import lombok.Getter;
import lombok.experimental.Accessors;

import java.nio.file.Path;

/**
 * RocksDB-backed LibraryIndex for frame and version queries.
 *
 * <p>All operations are provided by {@link LibraryIndex} default methods.
 * Service lifecycle (isOpen, start, stop, close) is provided by RocksStore.
 */
public final class RocksLibraryIndex implements LibraryIndex, RocksStore<LibraryIndex.Column> {

    @Getter
    private final Opened<Column> opened;

    /** Storage path for this index. */
    @Getter
    @Accessors(fluent = false)
    private final Path path;

    public static RocksLibraryIndex open(Path path) {
        return new RocksLibraryIndex(path);
    }

    private RocksLibraryIndex(Path rootPath) {
        this.path = rootPath;
        this.opened = RocksStore.open(rootPath, Column.class);
    }
}
