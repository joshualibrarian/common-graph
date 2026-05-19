package dev.everydaythings.graph.library.rocksdb;

import dev.everydaythings.graph.encoding.CgCbor;
import dev.everydaythings.graph.encoding.Encoding;
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

    private final Encoding encoder;

    private RocksRefIndexStore(Path path, Encoding encoder) {
        this.path = Objects.requireNonNull(path, "path");
        this.encoder = Objects.requireNonNull(encoder, "encoder");
        this.opened = RocksStore.open(path.resolve("ref-index"), RefIndexStore.Column.class);
    }

    /** Open or create at {@code path} with the default {@link CgCbor} encoder. */
    public static RocksRefIndexStore atPath(Path path) {
        return atPath(path, CgCbor.codec());
    }

    /** Open or create at {@code path} with the given encoder. */
    public static RocksRefIndexStore atPath(Path path, Encoding encoder) {
        return new RocksRefIndexStore(path, encoder);
    }

    @Override
    public Encoding rawEncoder() {
        return encoder;
    }
}
