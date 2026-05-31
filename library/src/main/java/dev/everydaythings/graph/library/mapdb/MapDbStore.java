package dev.everydaythings.graph.library.mapdb;

import dev.everydaythings.graph.library.WriteTransaction;
import dev.everydaythings.graph.library.bytestore.ByteStore;
import dev.everydaythings.graph.library.bytestore.ColumnSchema;
import org.mapdb.BTreeMap;
import org.mapdb.DB;
import org.mapdb.DBMaker;
import org.mapdb.Serializer;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.concurrent.ConcurrentNavigableMap;

/**
 * Foundation interface for MapDB-backed {@link ByteStore} implementations.
 *
 * <p>Concrete stores ({@code MapDbDataStore}, {@code MapDbRefIndexStore},
 * {@code MapDbTokenIndexStore}) only need to supply an opened DB holder; all
 * CRUD operations, prefix iteration, and transactions come from defaults here.
 *
 * <p>MapDB is a lightweight pure-Java embedded database — file-backed with
 * memory-mapped I/O when supported. Good fit for tests that need persistence
 * without RocksDB's native-library overhead, or for embedded deployments.
 */
public interface MapDbStore<E extends Enum<E> & ColumnSchema> extends ByteStore<E> {

    Opened<E> opened();

    // ==================================================================================
    // Open
    // ==================================================================================

    /** Open an in-memory MapDB. */
    static <E extends Enum<E> & ColumnSchema> Opened<E> memory(Class<E> schemaClass) {
        DB db = DBMaker.memoryDB().make();
        return new Opened<>(db, schemaClass);
    }

    /** Open a file-backed MapDB at {@code path}. */
    static <E extends Enum<E> & ColumnSchema> Opened<E> file(Path path, Class<E> schemaClass) {
        DB db = DBMaker.fileDB(path.toFile())
                .fileMmapEnableIfSupported()
                .transactionEnable()
                .make();
        return new Opened<>(db, schemaClass);
    }

    // ==================================================================================
    // ByteStore impl
    // ==================================================================================

    @Override
    default byte[] get(E column, byte[] key) {
        return opened().maps().get(column).get(key);
    }

    @Override
    default void put(E column, byte[] key, byte[] value) {
        opened().maps().get(column).put(key, value);
        if (!opened().db().isClosed()) opened().db().commit();
    }

    @Override
    default void delete(E column, byte[] key) {
        opened().maps().get(column).remove(key);
        if (!opened().db().isClosed()) opened().db().commit();
    }

    @Override
    default boolean exists(E column, byte[] key) {
        return opened().maps().get(column).containsKey(key);
    }

    @Override
    default CloseableIterator<KeyValue> iterate(E column, byte[] prefix) {
        BTreeMap<byte[], byte[]> map = opened().maps().get(column);
        if (prefix == null || prefix.length == 0) {
            return new MapDbIterator(map.entryIterator(), null);
        }
        byte[] endKey = incrementPrefix(prefix);
        ConcurrentNavigableMap<byte[], byte[]> subMap = (endKey != null)
                ? map.subMap(prefix, true, endKey, false)
                : map.tailMap(prefix, true);
        return new MapDbIterator(subMap.entrySet().iterator(), prefix);
    }

    @Override
    default StoreTransaction beginTransaction() {
        return new MapDbTx<>(opened());
    }

    @Override
    default void put(E column, byte[] key, byte[] value, WriteTransaction tx) {
        if (tx instanceof MapDbTx<?> mt) mt.addPut(column, key, value);
        else put(column, key, value);
    }

    @Override
    default void delete(E column, byte[] key, WriteTransaction tx) {
        if (tx instanceof MapDbTx<?> mt) mt.addDelete(column, key);
        else delete(column, key);
    }

    @Override
    default void close() {
        opened().close();
    }

    // ==================================================================================
    // Opened DB holder
    // ==================================================================================

    final class Opened<E extends Enum<E> & ColumnSchema> implements AutoCloseable {
        private final DB db;
        private final EnumMap<E, BTreeMap<byte[], byte[]>> maps;
        private volatile boolean closed = false;

        Opened(DB db, Class<E> schemaClass) {
            this.db = db;
            this.maps = new EnumMap<>(schemaClass);
            for (E column : schemaClass.getEnumConstants()) {
                BTreeMap<byte[], byte[]> map = db.treeMap(column.schemaName())
                        .keySerializer(Serializer.BYTE_ARRAY)
                        .valueSerializer(Serializer.BYTE_ARRAY)
                        .createOrOpen();
                maps.put(column, map);
            }
        }

        public DB db() { return db; }
        public EnumMap<E, BTreeMap<byte[], byte[]>> maps() { return maps; }
        public boolean isClosed() { return closed; }

        @Override
        public void close() {
            if (closed) return;
            closed = true;
            if (!db.isClosed()) db.close();
        }
    }

    // ==================================================================================
    // Transactions — buffered ops, applied on commit
    // ==================================================================================

    final class MapDbTx<E extends Enum<E> & ColumnSchema> implements StoreTransaction {
        private final Opened<E> opened;
        private final List<TxOp> ops = new ArrayList<>();
        private boolean done = false;

        MapDbTx(Opened<E> opened) { this.opened = opened; }

        void addPut(Enum<?> column, byte[] key, byte[] value) {
            ops.add(new TxOp(column, key, value, false));
        }

        void addDelete(Enum<?> column, byte[] key) {
            ops.add(new TxOp(column, key, null, true));
        }

        @Override
        @SuppressWarnings("unchecked")
        public void commit() {
            if (done) return;
            done = true;
            for (TxOp op : ops) {
                E col = (E) op.column;
                BTreeMap<byte[], byte[]> map = opened.maps().get(col);
                if (op.isDelete) map.remove(op.key);
                else map.put(op.key, op.value);
            }
            if (!opened.db().isClosed()) opened.db().commit();
        }

        @Override
        public void rollback() {
            if (done) return;
            done = true;
            ops.clear();
        }

        @Override
        public void close() {
            if (!done) rollback();
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> T unwrap(Class<T> type) {
            if (type.isAssignableFrom(List.class)) {
                return (T) Collections.unmodifiableList(ops);
            }
            return null;
        }

        private record TxOp(Enum<?> column, byte[] key, byte[] value, boolean isDelete) {}
    }

    // ==================================================================================
    // Iterator
    // ==================================================================================

    final class MapDbIterator implements CloseableIterator<KeyValue> {
        private final Iterator<Map.Entry<byte[], byte[]>> delegate;
        private final byte[] prefix;
        private Map.Entry<byte[], byte[]> next;
        private boolean primed = false;

        MapDbIterator(Iterator<Map.Entry<byte[], byte[]>> delegate, byte[] prefix) {
            this.delegate = delegate;
            this.prefix = prefix;
        }

        @Override
        public boolean hasNext() {
            if (primed) return next != null;
            primed = true;
            if (!delegate.hasNext()) { next = null; return false; }
            next = delegate.next();
            if (prefix != null && !startsWith(next.getKey(), prefix)) {
                next = null;
                return false;
            }
            return true;
        }

        @Override
        public KeyValue next() {
            if (!primed) hasNext();
            if (next == null) throw new NoSuchElementException();
            primed = false;
            return new KeyValue(next.getKey(), next.getValue());
        }

        @Override
        public void close() {
            // MapDB iterators don't need explicit closing.
        }

        private static boolean startsWith(byte[] a, byte[] p) {
            if (a.length < p.length) return false;
            for (int i = 0; i < p.length; i++) {
                if (a[i] != p[i]) return false;
            }
            return true;
        }
    }

    // ==================================================================================
    // Helpers
    // ==================================================================================

    private static byte[] incrementPrefix(byte[] prefix) {
        byte[] r = Arrays.copyOf(prefix, prefix.length);
        for (int i = r.length - 1; i >= 0; i--) {
            if ((r[i] & 0xFF) < 0xFF) {
                r[i]++;
                return r;
            }
            r[i] = 0;
        }
        return null;
    }
}
