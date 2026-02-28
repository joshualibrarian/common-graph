package dev.everydaythings.graph.library.rocksdb;

import dev.everydaythings.graph.library.WriteTransaction;
import dev.everydaythings.graph.library.bytestore.ByteStore;
import dev.everydaythings.graph.library.bytestore.ColumnSchema;
import org.rocksdb.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.function.UnaryOperator;

public interface RocksStore<E extends Enum<E> & ColumnSchema> extends ByteStore<E> {

    /**
     * Get the opened database holder.
     *
     * @return the opened holder
     */
    Opened<E> opened();

    /**
     * Get the storage path for this store.
     *
     * <p>Implementations should have a {@code path} field annotated with {@code @Getter}.
     *
     * @return the path
     */
    Path getPath();

    // ==================================================================================
    // Service Implementation (all provided by interface!)
    // ==================================================================================

    /**
     * Check if the RocksDB is open.
     */
    @Override
    default boolean isOpen() {
        Opened<E> o = opened();
        return o != null && !o.isClosed();
    }

    /**
     * Get the storage path as Optional.
     */
    @Override
    default java.util.Optional<Path> path() {
        return java.util.Optional.ofNullable(getPath());
    }

    /**
     * Stop delegates to close.
     */
    @Override
    default void stop() {
        close();
    }

    /**
     * Close the RocksDB.
     */
    @Override
    default void close() {
        Opened<E> o = opened();
        if (o != null && !o.isClosed()) {
            o.close();
        }
    }

    /* ---------- open helpers ---------- */

    static <E extends Enum<E> & ColumnSchema>
    Opened<E> open(Path dir, Class<E> cfEnum) {
        return open(dir, cfEnum, opts -> opts);
    }

    static <E extends Enum<E> & ColumnSchema>
    Opened<E> open(Path dir, Class<E> cfEnum, UnaryOperator<DBOptions> customize) {
        Objects.requireNonNull(dir, "dir");
        Objects.requireNonNull(cfEnum, "cfEnum");

        try {
            RocksDB.loadLibrary();
            Files.createDirectories(dir);

            E[] cfs = cfEnum.getEnumConstants();
            CfDescriptor[] tmp = new CfDescriptor[cfs.length];
            List<ColumnFamilyDescriptor> descs = new ArrayList<>(cfs.length);
            for (int i = 0; i < cfs.length; i++) {
                tmp[i] = newDescriptor(cfs[i]);
                descs.add(tmp[i].desc);
            }

            DBOptions dbOpts = customize.apply(new DBOptions()
                    .setCreateIfMissing(true)
                    .setCreateMissingColumnFamilies(true));

            List<ColumnFamilyHandle> handlesList = new ArrayList<>(cfs.length);
            RocksDB db = RocksDB.open(dbOpts, dir.toString(), descs, handlesList);

            EnumMap<E, ColumnFamilyHandle> handles = new EnumMap<>(cfEnum);
            for (int i = 0; i < cfs.length; i++) handles.put(cfs[i], handlesList.get(i));

            for (var d : tmp) safeClose(d);
            safeClose(dbOpts);

            return new Opened<>(db, handles);
        } catch (Exception e) {
            throw new RuntimeException("RocksStore.open failed", e);
        }
    }

    /**
     * Create a RocksDB column family descriptor from a ColumnSchema.
     */
    private static CfDescriptor newDescriptor(ColumnSchema schema) {
        BlockBasedTableConfig table = new BlockBasedTableConfig().setWholeKeyFiltering(true);
        BloomFilter bloom = (schema.bloomBits() != null) ? new BloomFilter(schema.bloomBits(), false) : null;
        if (bloom != null) table.setFilterPolicy(bloom);

        ColumnFamilyOptions opts = new ColumnFamilyOptions()
                .setTableFormatConfig(table)
                .setLevelCompactionDynamicLevelBytes(true);
        if (schema.prefixLen() != null) opts.useFixedLengthPrefixExtractor(schema.prefixLen());

        ColumnFamilyDescriptor desc =
                new ColumnFamilyDescriptor(schema.schemaName().getBytes(StandardCharsets.UTF_8), opts);
        return new CfDescriptor(desc, opts, bloom);
    }

    /**
     * Holder for descriptor + native resources to close after DB.open().
     */
    record CfDescriptor(ColumnFamilyDescriptor desc, ColumnFamilyOptions opts, BloomFilter bloom)
            implements AutoCloseable {
        @Override
        public void close() {
            if (bloom != null) safeClose(bloom);
            safeClose(opts);
        }
    }

    /* ---------- ByteStore implementation ---------- */

    @Override
    default byte[] get(E column, byte[] key) {
        try {
            return opened().db().get(opened().handles().get(column), key);
        } catch (RocksDBException e) {
            throw new RuntimeException("RocksStore.get failed", e);
        }
    }

    @Override
    default void put(E column, byte[] key, byte[] value) {
        try {
            opened().db().put(opened().handles().get(column), key, value);
        } catch (RocksDBException e) {
            throw new RuntimeException("RocksStore.put failed", e);
        }
    }

    @Override
    default void delete(E column, byte[] key) {
        try {
            opened().db().delete(opened().handles().get(column), key);
        } catch (RocksDBException e) {
            throw new RuntimeException("RocksStore.delete failed", e);
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
        return new RocksCloseableIterator(it, prefix);
    }

    @Override
    default StoreTransaction beginTransaction() {
        return new RocksTx<>(opened());
    }

    @Override
    default void put(E column, byte[] key, byte[] value, WriteTransaction tx) {
        if (tx instanceof RocksTx<?> rtx) {
            try {
                rtx.batch().put(opened().handles().get(column), key, value);
            } catch (RocksDBException e) {
                throw new RuntimeException("RocksStore.put (tx) failed", e);
            }
        } else {
            put(column, key, value);
        }
    }

    @Override
    default void delete(E column, byte[] key, WriteTransaction tx) {
        if (tx instanceof RocksTx<?> rtx) {
            try {
                rtx.batch().delete(opened().handles().get(column), key);
            } catch (RocksDBException e) {
                throw new RuntimeException("RocksStore.delete (tx) failed", e);
            }
        } else {
            delete(column, key);
        }
    }

    /* ---------- holder ---------- */

    /**
     * Holder for an opened RocksDB and its column family handles.
     * Tracks closed state for service status.
     */
    final class Opened<E extends Enum<E> & ColumnSchema> implements AutoCloseable {
        private final RocksDB db;
        private final EnumMap<E, ColumnFamilyHandle> handles;
        private volatile boolean closed = false;

        public Opened(RocksDB db, EnumMap<E, ColumnFamilyHandle> handles) {
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
            for (var h : handles.values()) safeClose(h);
            safeClose(db);
        }
    }

    /* ---------- transactions ---------- */

    final class RocksTx<E extends Enum<E> & ColumnSchema> implements StoreTransaction, AutoCloseable {
        private final RocksDB db;
        private final WriteBatch batch = new WriteBatch();
        private boolean done;

        public RocksTx(Opened<E> opened) {
            this.db = opened.db();
        }

        WriteBatch batch() {
            return batch;
        }

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
            if (type.isAssignableFrom(WriteBatch.class)) {
                return (T) batch;
            }
            return null;
        }
    }

    /* ---------- ByteStore iterator ---------- */

    final class RocksCloseableIterator implements CloseableIterator<KeyValue> {
        private final RocksIterator it;
        private final byte[] prefix;

        RocksCloseableIterator(RocksIterator it, byte[] prefix) {
            this.it = it;
            this.prefix = prefix;
        }

        @Override
        public boolean hasNext() {
            if (!it.isValid()) return false;
            if (prefix != null && prefix.length > 0) {
                byte[] key = it.key();
                return startsWith(key, prefix);
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

    /* ---------- utils & default close ---------- */

    static void safeClose(AutoCloseable c) {
        if (c == null) return;
        try {
            c.close();
        } catch (Exception ignore) { }
    }
}
