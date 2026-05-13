package dev.everydaythings.graph.library.rocksdb;

import dev.everydaythings.graph.library.index.TokenIndexByteStore;
import dev.everydaythings.graph.library.index.TokenIndexStore;
import lombok.Getter;

import java.nio.file.Path;
import java.util.Objects;

/**
 * RocksDB-backed {@link TokenIndexByteStore}. Composes {@link RocksStore}
 * defaults (RocksDB CRUD/iteration/transactions) with
 * {@link TokenIndexByteStore} defaults (Datum text-binding extraction +
 * posting assembly). Concrete class is a thin wrapper.
 */
public final class RocksTokenIndexStore
        implements RocksStore<TokenIndexStore.Column>, TokenIndexByteStore {

    @Getter
    private final RocksStore.Opened<TokenIndexStore.Column> opened;

    @Getter
    private final Path path;

    private RocksTokenIndexStore(Path path) {
        this.path = Objects.requireNonNull(path, "path");
        this.opened = RocksStore.open(path.resolve("token-index"), TokenIndexStore.Column.class);
    }

    /** Open or create at {@code path}. */
    public static RocksTokenIndexStore atPath(Path path) {
        return new RocksTokenIndexStore(path);
    }
}
