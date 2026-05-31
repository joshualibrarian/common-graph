package dev.everydaythings.graph.library.rocksdb;

import dev.everydaythings.graph.encoding.CgCbor;
import dev.everydaythings.graph.encoding.Encoding;
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

    private final Encoding encoder;

    private RocksTokenIndexStore(Path path, Encoding encoder) {
        this.path = Objects.requireNonNull(path, "path");
        this.encoder = Objects.requireNonNull(encoder, "encoder");
        this.opened = RocksStore.open(path.resolve("token-index"), TokenIndexStore.Column.class);
    }

    /** Open or create at {@code path} with the default {@link CgCbor} encoder. */
    public static RocksTokenIndexStore atPath(Path path) {
        return atPath(path, CgCbor.codec());
    }

    /** Open or create at {@code path} with the given encoder. */
    public static RocksTokenIndexStore atPath(Path path, Encoding encoder) {
        return new RocksTokenIndexStore(path, encoder);
    }

    @Override
    public Encoding rawEncoder() {
        return encoder;
    }
}
