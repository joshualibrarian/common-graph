package dev.everydaythings.graph.library.rocksdb;

import dev.everydaythings.graph.library.WriteTransaction;
import dev.everydaythings.graph.library.bytestore.ByteStore;
import dev.everydaythings.graph.library.bytestore.ColumnSchema;
import org.rocksdb.BlockBasedTableConfig;
import org.rocksdb.BloomFilter;
import org.rocksdb.ColumnFamilyDescriptor;
import org.rocksdb.ColumnFamilyHandle;
import org.rocksdb.ColumnFamilyOptions;
import org.rocksdb.DBOptions;
import org.rocksdb.RocksDB;
import org.rocksdb.RocksDBException;
import org.rocksdb.RocksIterator;
import org.rocksdb.WriteBatch;
import org.rocksdb.WriteOptions;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.function.UnaryOperator;

/**
 * Foundation interface for RocksDB-backed {@link ByteStore} implementations.
 *
 * <p>Concrete stores ({@code RocksDbDataStore}, {@code RocksDbRefIndexStore},
 * {@code RocksDbTokenIndexStore}) only need to supply an opened DB handle
 * ({@link Opened}); all CRUD operations, iteration, and transactions are
 * provided as default methods here.
 *
 * <p>Open a DB via the static {@link #open(Path, Class)} factory; close
 * propagates via {@link #close()} → {@link Opened#close()}.
 */
public interface RocksStore<E extends Enum<E> & ColumnSchema> extends ByteStore<E> {

    /** Opened DB + column-family handles. */
    Opened<E> opened();

    /** The filesystem path this store is rooted at. */
    Path path();

    // ==================================================================================
    // Open / close
    // ==================================================================================

    /** Open a RocksDB at {@code dir} for the given column-family enum. */
    static <E extends Enum<E> & ColumnSchema>
    Opened<E> open(Path dir, Class<E> cfEnum) {
        return open(dir, cfEnum, opts -> opts);
    }

    /** Open with a {@link DBOptions} customizer. */
    static <E extends Enum<E> & ColumnSchema>
    Opened<E> open(Path dir, Class<E> cfEnum, UnaryOperator<DBOptions> customize) {
        Objects.requireNonNull(dir, "dir");
        Objects.requireNonNull(cfEnum, "cfEnum");
        try {
            RocksDB.loadLibrary();
            Files.createDirectories(dir);

            E[] cfs = cfEnum.getEnumConstants();
            List<ColumnFamilyDescriptor> descs = new ArrayList<>(cfs.length);
            List<CfResources> resources = new ArrayList<>(cfs.length);
            for (E cf : cfs) {
                CfResources r = newDescriptor(cf);
                descs.add(r.desc);
                resources.add(r);
            }

            DBOptions dbOpts = customize.apply(new DBOptions()
                    .setCreateIfMissing(true)
                    .setCreateMissingColumnFamilies(true));

            List<ColumnFamilyHandle> handlesList = new ArrayList<>(cfs.length);
            RocksDB db = RocksDB.open(dbOpts, dir.toString(), descs, handlesList);

            EnumMap<E, ColumnFamilyHandle> handles = new EnumMap<>(cfEnum);
            for (int i = 0; i < cfs.length; i++) handles.put(cfs[i], handlesList.get(i));

            for (CfResources r : resources) r.close();
            safeClose(dbOpts);

            return new Opened<>(db, handles);
        } catch (Exception e) {
            throw new RuntimeException("RocksDbStore.open failed at " + dir, e);
        }
    }

    @Override
    default void close() {
        Opened<E> o = opened();
        if (o != null && !o.isClosed()) {
            o.close();
        }
    }

    // ==================================================================================
    // ByteStore impl — delegates to the opened handle
    // ==================================================================================

    @Override
    default byte[] get(E column, byte[] key) {
        try {
            return opened().db().get(opened().handles().get(column), key);
        } catch (RocksDBException e) {
            throw new RuntimeException("get failed", e);
        }
    }

    @Override
    default void put(E column, byte[] key, byte[] value) {
        try {
            opened().db().put(opened().handles().get(column), key, value);
        } catch (RocksDBException e) {
            throw new RuntimeException("put failed", e);
        }
    }

    @Override
    default void delete(E column, byte[] key) {
        try {
            opened().db().delete(opened().handles().get(column), key);
        } catch (RocksDBException e) {
            throw new RuntimeException("delete failed", e);
        }
    }

    @Override
    default boolean exists(E column, byte[] key) {
        return opened().db().keyMayExist(opened().handles().get(column), key, null);
    }

    @Override
    default CloseableIterator<KeyValue> iterate(E column, byte[] prefix) {
        RocksIterator it = opened().db().newIterator(opened().handles().get(column));
        if (prefix == null || prefix.length == 0) {
            it.seekToFirst();
        } else {
            it.seek(prefix);
        }
        return new RocksDbIterator(it, prefix);
    }

    @Override
    default StoreTransaction beginTransaction() {
        return new RocksDbTx<>(opened());
    }

    @Override
    default void put(E column, byte[] key, byte[] value, WriteTransaction tx) {
        if (tx instanceof RocksDbTx<?> rtx) {
            try {
                rtx.batch().put(opened().handles().get(column), key, value);
            } catch (RocksDBException e) {
                throw new RuntimeException("put(tx) failed", e);
            }
        } else {
            put(column, key, value);
        }
    }

    @Override
    default void delete(E column, byte[] key, WriteTransaction tx) {
        if (tx instanceof RocksDbTx<?> rtx) {
            try {
                rtx.batch().delete(opened().handles().get(column), key);
            } catch (RocksDBException e) {
                throw new RuntimeException("delete(tx) failed", e);
            }
        } else {
            delete(column, key);
        }
    }

    // ==================================================================================
    // Internal helpers
    // ==================================================================================

    private static CfResources newDescriptor(ColumnSchema schema) {
        BlockBasedTableConfig table = new BlockBasedTableConfig().setWholeKeyFiltering(true);
        BloomFilter bloom = (schema.bloomBits() != null) ? new BloomFilter(schema.bloomBits(), false) : null;
        if (bloom != null) table.setFilterPolicy(bloom);

        ColumnFamilyOptions opts = new ColumnFamilyOptions()
                .setTableFormatConfig(table)
                .setLevelCompactionDynamicLevelBytes(true);

        ColumnFamilyDescriptor desc = new ColumnFamilyDescriptor(
                schema.schemaName().getBytes(StandardCharsets.UTF_8), opts);
        return new CfResources(desc, opts, bloom);
    }

    /** Native column-family resources to release after DB.open(). */
    record CfResources(ColumnFamilyDescriptor desc, ColumnFamilyOptions opts, BloomFilter bloom) {
        void close() {
            if (bloom != null) safeClose(bloom);
            safeClose(opts);
        }
    }

    static void safeClose(AutoCloseable c) {
        if (c == null) return;
        try {
            c.close();
        } catch (Exception ignored) {
            // best-effort
        }
    }

    // ==================================================================================
    // Opened DB holder
    // ==================================================================================

    final class Opened<E extends Enum<E> & ColumnSchema> implements AutoCloseable {
        private final RocksDB db;
        private final EnumMap<E, ColumnFamilyHandle> handles;
        private volatile boolean closed = false;

        Opened(RocksDB db, EnumMap<E, ColumnFamilyHandle> handles) {
            this.db = db;
            this.handles = handles;
        }

        public RocksDB db() { return db; }
        public EnumMap<E, ColumnFamilyHandle> handles() { return handles; }
        public boolean isClosed() { return closed; }

        @Override
        public void close() {
            if (closed) return;
            closed = true;
            for (ColumnFamilyHandle h : handles.values()) safeClose(h);
            safeClose(db);
        }
    }

    // ==================================================================================
    // Transactions
    // ==================================================================================

    final class RocksDbTx<E extends Enum<E> & ColumnSchema>
            implements StoreTransaction, AutoCloseable {
        private final RocksDB db;
        private final WriteBatch batch = new WriteBatch();
        private boolean done;

        RocksDbTx(Opened<E> opened) {
            this.db = opened.db();
        }

        WriteBatch batch() { return batch; }

        @Override
        public void commit() {
            if (done) return;
            try (WriteOptions wo = new WriteOptions()) {
                db.write(wo, batch);
                done = true;
            } catch (RocksDBException e) {
                throw new RuntimeException("commit failed", e);
            } finally {
                batch.close();
            }
        }

        @Override
        public void rollback() {
            if (!done) {
                done = true;
                batch.close();
            }
        }

        @Override
        public void close() {
            if (!done) rollback();
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> T unwrap(Class<T> type) {
            if (type.isAssignableFrom(WriteBatch.class)) return (T) batch;
            return null;
        }
    }

    // ==================================================================================
    // Iterator
    // ==================================================================================

    final class RocksDbIterator implements CloseableIterator<KeyValue> {
        private final RocksIterator it;
        private final byte[] prefix;

        RocksDbIterator(RocksIterator it, byte[] prefix) {
            this.it = it;
            this.prefix = prefix;
        }

        @Override
        public boolean hasNext() {
            if (!it.isValid()) return false;
            if (prefix != null && prefix.length > 0) {
                return startsWith(it.key(), prefix);
            }
            return true;
        }

        @Override
        public KeyValue next() {
            if (!hasNext()) throw new NoSuchElementException();
            KeyValue kv = new KeyValue(it.key(), it.value());
            it.next();
            return kv;
        }

        @Override
        public void close() {
            it.close();
        }

        private static boolean startsWith(byte[] a, byte[] p) {
            if (a.length < p.length) return false;
            for (int i = 0; i < p.length; i++) {
                if (a[i] != p[i]) return false;
            }
            return true;
        }
    }
}
