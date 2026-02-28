package dev.everydaythings.graph.library.mapdb;

import dev.everydaythings.graph.library.WriteTransaction;
import dev.everydaythings.graph.library.bytestore.ByteStore;
import dev.everydaythings.graph.library.bytestore.ColumnSchema;
import org.mapdb.BTreeMap;
import org.mapdb.DB;
import org.mapdb.DBMaker;
import org.mapdb.Serializer;

import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentNavigableMap;

/**
 * MapDB-backed implementation of ByteStore.
 *
 * <p>Provides both in-memory and file-based storage options, making it ideal for:
 * <ul>
 *   <li>Testing - fast in-memory stores with no cleanup needed</li>
 *   <li>Embedded use - lightweight file-based persistence</li>
 *   <li>Development - quick iteration without RocksDB setup</li>
 * </ul>
 *
 * <p>Uses BTreeMap for sorted key storage, enabling efficient prefix iteration.
 *
 * <p>This is an interface with default methods, similar to RocksStore.
 * Implementations just need to provide {@link #opened()}.
 *
 * @param <E> the column schema enum type
 */
public interface MapDBStore<E extends Enum<E> & ColumnSchema> extends ByteStore<E> {

    /**
     * Get the opened store holder.
     */
    Opened<E> opened();

    // ==================================================================================
    // Static Factory Methods
    // ==================================================================================

    /**
     * Open an in-memory MapDB store.
     */
    static <E extends Enum<E> & ColumnSchema> Opened<E> memory(Class<E> schemaClass) {
        DB db = DBMaker.memoryDB().make();
        return new Opened<>(db, schemaClass);
    }

    /**
     * Open a file-backed MapDB store.
     */
    static <E extends Enum<E> & ColumnSchema> Opened<E> file(Path path, Class<E> schemaClass) {
        DB db = DBMaker.fileDB(path.toFile())
                .fileMmapEnableIfSupported()
                .transactionEnable()
                .make();
        return new Opened<>(db, schemaClass);
    }

    // ==================================================================================
    // ByteStore Implementation - Basic Operations
    // ==================================================================================

    @Override
    default byte[] get(E column, byte[] key) {
        opened().checkNotClosed();
        return opened().maps().get(column).get(key);
    }

    @Override
    default void put(E column, byte[] key, byte[] value) {
        opened().checkNotClosed();
        opened().maps().get(column).put(key, value);
        if (!opened().db().isClosed()) {
            opened().db().commit();
        }
    }

    @Override
    default void delete(E column, byte[] key) {
        opened().checkNotClosed();
        opened().maps().get(column).remove(key);
        if (!opened().db().isClosed()) {
            opened().db().commit();
        }
    }

    @Override
    default boolean exists(E column, byte[] key) {
        opened().checkNotClosed();
        return opened().maps().get(column).containsKey(key);
    }

    // ==================================================================================
    // ByteStore Implementation - Iteration
    // ==================================================================================

    @Override
    default CloseableIterator<KeyValue> iterate(E column, byte[] prefix) {
        opened().checkNotClosed();
        BTreeMap<byte[], byte[]> map = opened().maps().get(column);

        if (prefix == null || prefix.length == 0) {
            return new MapIterator(map.entryIterator(), null);
        }

        byte[] endKey = incrementPrefix(prefix);
        ConcurrentNavigableMap<byte[], byte[]> subMap;
        if (endKey != null) {
            subMap = map.subMap(prefix, true, endKey, false);
        } else {
            subMap = map.tailMap(prefix, true);
        }

        return new MapIterator(subMap.entrySet().iterator(), prefix);
    }

    // ==================================================================================
    // ByteStore Implementation - Transactions
    // ==================================================================================

    @Override
    default StoreTransaction beginTransaction() {
        opened().checkNotClosed();
        return new MapDBTx<>(opened());
    }

    @Override
    default void put(E column, byte[] key, byte[] value, WriteTransaction tx) {
        opened().checkNotClosed();
        if (tx instanceof MapDBTx<?> mapTx) {
            mapTx.addPut(column, key, value);
        } else {
            put(column, key, value);
        }
    }

    @Override
    default void delete(E column, byte[] key, WriteTransaction tx) {
        opened().checkNotClosed();
        if (tx instanceof MapDBTx<?> mapTx) {
            mapTx.addDelete(column, key);
        } else {
            delete(column, key);
        }
    }

    // ==================================================================================
    // Lifecycle
    // ==================================================================================

    @Override
    default void close() {
        opened().close();
    }

    default boolean isOpen() {
        return !opened().closed && !opened().db().isClosed();
    }

    // ==================================================================================
    // Holder Record
    // ==================================================================================

    /**
     * Holds the opened MapDB state.
     */
    final class Opened<E extends Enum<E> & ColumnSchema> implements AutoCloseable {
        private final DB db;
        private final EnumMap<E, BTreeMap<byte[], byte[]>> maps;
        private volatile boolean closed = false;

        public Opened(DB db, Class<E> schemaClass) {
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

        void checkNotClosed() {
            if (closed) {
                throw new IllegalStateException("Store is closed");
            }
        }

        @Override
        public void close() {
            if (closed) return;
            closed = true;
            if (!db.isClosed()) {
                db.close();
            }
        }
    }

    // ==================================================================================
    // Transaction
    // ==================================================================================

    final class MapDBTx<E extends Enum<E> & ColumnSchema> implements StoreTransaction {
        private final Opened<E> opened;
        private final List<TxOp> operations = new ArrayList<>();
        private boolean done = false;

        MapDBTx(Opened<E> opened) {
            this.opened = opened;
        }

        @SuppressWarnings("unchecked")
        void addPut(Enum<?> column, byte[] key, byte[] value) {
            operations.add(new TxOp(column, key, value, false));
        }

        void addDelete(Enum<?> column, byte[] key) {
            operations.add(new TxOp(column, key, null, true));
        }

        @Override
        @SuppressWarnings("unchecked")
        public void commit() {
            if (done) return;
            done = true;

            for (TxOp op : operations) {
                E col = (E) op.column;
                BTreeMap<byte[], byte[]> map = opened.maps().get(col);
                if (op.isDelete) {
                    map.remove(op.key);
                } else {
                    map.put(op.key, op.value);
                }
            }

            if (!opened.db().isClosed()) {
                opened.db().commit();
            }
        }

        @Override
        public void rollback() {
            if (done) return;
            done = true;
            operations.clear();
        }

        @Override
        public void close() {
            if (!done) rollback();
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> T unwrap(Class<T> type) {
            if (type.isAssignableFrom(List.class)) {
                return (T) Collections.unmodifiableList(operations);
            }
            return null;
        }

        private record TxOp(Enum<?> column, byte[] key, byte[] value, boolean isDelete) {}
    }

    // ==================================================================================
    // Iterator
    // ==================================================================================

    final class MapIterator implements CloseableIterator<KeyValue> {
        private final Iterator<Map.Entry<byte[], byte[]>> delegate;
        private final byte[] prefix;
        private Map.Entry<byte[], byte[]> next;
        private boolean hasNextCalled = false;

        MapIterator(Iterator<Map.Entry<byte[], byte[]>> delegate, byte[] prefix) {
            this.delegate = delegate;
            this.prefix = prefix;
        }

        @Override
        public boolean hasNext() {
            if (hasNextCalled) {
                return next != null;
            }
            hasNextCalled = true;

            if (!delegate.hasNext()) {
                next = null;
                return false;
            }

            next = delegate.next();

            if (prefix != null && !startsWith(next.getKey(), prefix)) {
                next = null;
                return false;
            }

            return true;
        }

        @Override
        public KeyValue next() {
            if (!hasNextCalled) {
                hasNext();
            }
            if (next == null) {
                throw new NoSuchElementException();
            }
            hasNextCalled = false;
            return new KeyValue(next.getKey(), next.getValue());
        }

        @Override
        public void close() {
            // MapDB iterators don't need explicit closing
        }

        private static boolean startsWith(byte[] array, byte[] prefix) {
            if (array.length < prefix.length) return false;
            for (int i = 0; i < prefix.length; i++) {
                if (array[i] != prefix[i]) return false;
            }
            return true;
        }
    }

    // ==================================================================================
    // Helpers
    // ==================================================================================

    private static byte[] incrementPrefix(byte[] prefix) {
        byte[] result = Arrays.copyOf(prefix, prefix.length);

        for (int i = result.length - 1; i >= 0; i--) {
            if ((result[i] & 0xFF) < 0xFF) {
                result[i]++;
                return result;
            }
            result[i] = 0;
        }

        return null;
    }
}
