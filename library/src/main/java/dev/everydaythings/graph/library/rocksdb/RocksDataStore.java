package dev.everydaythings.graph.library.rocksdb;

import dev.everydaythings.graph.encoding.CgCbor;
import dev.everydaythings.graph.encoding.Encoding;
import dev.everydaythings.graph.library.data.DataByteStore;
import dev.everydaythings.graph.library.data.DataStore;
import lombok.Getter;

import java.nio.file.Path;
import java.util.Objects;

/**
 * RocksDB-backed {@link DataByteStore}. Provides only the encoder and the
 * filesystem path; all RocksDB CRUD/iteration/transaction work comes from
 * {@link RocksStore} defaults, and all Datum-aware logic comes from
 * {@link DataByteStore} defaults.
 */
public final class RocksDataStore
        implements RocksStore<DataStore.Column>, DataByteStore {

    @Getter
    private final RocksStore.Opened<DataStore.Column> opened;

    @Getter
    private final Path path;

    private final Encoding encoder;

    private RocksDataStore(Path path, Encoding encoder) {
        this.path = Objects.requireNonNull(path, "path");
        this.encoder = Objects.requireNonNull(encoder, "encoder");
        this.opened = RocksStore.open(path.resolve("data"), DataStore.Column.class);
    }

    /** Open or create at {@code path} using CG-CBOR-v1 encoding. */
    public static RocksDataStore atPath(Path path) {
        return atPath(path, CgCbor.codec());
    }

    /** Open or create with an explicit encoder. */
    public static RocksDataStore atPath(Path path, Encoding encoder) {
        return new RocksDataStore(path, encoder);
    }

    @Override
    public Encoding rawEncoder() {
        return encoder;
    }
}
