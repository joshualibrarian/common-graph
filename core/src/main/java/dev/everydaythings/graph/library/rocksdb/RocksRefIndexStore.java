package dev.everydaythings.graph.library.rocksdb;

import dev.everydaythings.graph.library.index.RefIndexByteStore;
import dev.everydaythings.graph.library.index.RefIndexStore;
import lombok.Getter;

import java.nio.file.Path;
import java.util.Objects;

/**
 * RocksDB-backed {@link RefIndexByteStore}. Composes {@link RocksStore} defaults
 * (RocksDB CRUD/iteration/transactions) with {@link RefIndexByteStore} defaults
 * (Datum-aware index/unindex/query). Concrete class is a thin wrapper.
 */
public final class RocksRefIndexStore
        implements RocksStore<RefIndexStore.Column>, RefIndexByteStore {

    @Getter
    private final RocksStore.Opened<RefIndexStore.Column> opened;

    @Getter
    private final Path path;

    private RocksRefIndexStore(Path path) {
        this.path = Objects.requireNonNull(path, "path");
        this.opened = RocksStore.open(path.resolve("ref-index"), RefIndexStore.Column.class);
    }

    /** Open or create at {@code path}. */
    public static RocksRefIndexStore atPath(Path path) {
        return new RocksRefIndexStore(path);
    }
}
