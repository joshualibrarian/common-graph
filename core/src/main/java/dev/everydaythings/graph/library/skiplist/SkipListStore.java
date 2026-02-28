package dev.everydaythings.graph.library.skiplist;

import dev.everydaythings.graph.library.WriteTransaction;
import dev.everydaythings.graph.library.bytestore.ByteStore;
import dev.everydaythings.graph.library.bytestore.ColumnSchema;

import java.util.*;
import java.util.concurrent.ConcurrentNavigableMap;
import java.util.concurrent.ConcurrentSkipListMap;

/**
 * In-memory ByteStore using ConcurrentSkipListMap.
 *
 * <p>Zero dependencies, pure Java. Ideal for tests and small dev datasets.
 * Keys are compared lexicographically by unsigned bytes, matching RocksDB behavior.
 *
 * <p>This is an interface with default methods, similar to RocksStore and MapDBStore.
 * Implementations just need to provide {@link #opened()}.
 *
 * @param <E> the column schema enum type
 */
public interface SkipListStore<E extends Enum<E> & ColumnSchema> extends ByteStore<E> {

    Opened<E> opened();

    // ==================================================================================
    // Service Implementation
    // ==================================================================================

    /**
     * Check if the store is open.
     */
    @Override
    default boolean isOpen() {
        Opened<E> o = opened();
        return o != null && !o.isClosed();
    }

    // ==================================================================================
    // Static Factory
    // ==================================================================================

    static <E extends Enum<E> & ColumnSchema> Opened<E> create(Class<E> schemaClass) {
        return new Opened<>(schemaClass);
    }

    // ==================================================================================
    // ByteStore Implementation
    // ==================================================================================

    @Override
    default byte[] get(E column, byte[] key) {
        opened().checkNotClosed();
        byte[] value = opened().maps().get(column).get(new Key(key));
        return value != null ? Arrays.copyOf(value, value.length) : null;
    }

    @Override
    default void put(E column, byte[] key, byte[] value) {
        opened().checkNotClosed();
        opened().maps().get(column).put(new Key(key), Arrays.copyOf(value, value.length));
    }

    @Override
    default void delete(E column, byte[] key) {
        opened().checkNotClosed();
        opened().maps().get(column).remove(new Key(key));
    }

    @Override
    default boolean exists(E column, byte[] key) {
        opened().checkNotClosed();
        return opened().maps().get(column).containsKey(new Key(key));
    }

    @Override
    default CloseableIterator<KeyValue> iterate(E column, byte[] prefix) {
        opened().checkNotClosed();
        ConcurrentNavigableMap<Key, byte[]> map = opened().maps().get(column);

        if (prefix == null || prefix.length == 0) {
            return new MapIterator(map.entrySet().iterator());
        }

        Key fromKey = new Key(prefix);
        Key toKey = prefixEnd(prefix);

        NavigableMap<Key, byte[]> subMap = (toKey != null)
                ? map.subMap(fromKey, true, toKey, false)
                : map.tailMap(fromKey, true);

        return new MapIterator(subMap.entrySet().iterator());
    }

    @Override
    default StoreTransaction beginTransaction() {
        opened().checkNotClosed();
        return new MemoryTx<>(this);
    }

    @Override
    default void put(E column, byte[] key, byte[] value, WriteTransaction tx) {
        if (tx instanceof MemoryTx<?> mtx) {
            mtx.buffer.add(new Op(column, key, value, false));
        } else {
            put(column, key, value);
        }
    }

    @Override
    default void delete(E column, byte[] key, WriteTransaction tx) {
        if (tx instanceof MemoryTx<?> mtx) {
            mtx.buffer.add(new Op(column, key, null, true));
        } else {
            delete(column, key);
        }
    }

    @Override
    default void close() {
        opened().close();
    }

    // ==================================================================================
    // Opened Holder
    // ==================================================================================

    final class Opened<E extends Enum<E> & ColumnSchema> implements AutoCloseable {
        private final EnumMap<E, ConcurrentNavigableMap<Key, byte[]>> maps;
        private volatile boolean closed = false;

        public Opened(Class<E> schemaClass) {
            this.maps = new EnumMap<>(schemaClass);
            for (E column : schemaClass.getEnumConstants()) {
                maps.put(column, new ConcurrentSkipListMap<>());
            }
        }

        public EnumMap<E, ConcurrentNavigableMap<Key, byte[]>> maps() { return maps; }

        public boolean isClosed() { return closed; }

        void checkNotClosed() {
            if (closed) throw new IllegalStateException("Store is closed");
        }

        @Override
        public void close() {
            if (closed) return;
            closed = true;
            maps.values().forEach(Map::clear);
        }
    }

    // ==================================================================================
    // Key Wrapper - lexicographic unsigned byte comparison
    // ==================================================================================

    final class Key implements Comparable<Key> {
        final byte[] bytes;

        Key(byte[] bytes) {
            this.bytes = Arrays.copyOf(bytes, bytes.length);
        }

        @Override
        public int compareTo(Key o) {
            int n = Math.min(bytes.length, o.bytes.length);
            for (int i = 0; i < n; i++) {
                int a = bytes[i] & 0xFF;
                int b = o.bytes[i] & 0xFF;
                if (a != b) return Integer.compare(a, b);
            }
            return Integer.compare(bytes.length, o.bytes.length);
        }

        @Override
        public boolean equals(Object o) {
            return (o instanceof Key other) && Arrays.equals(bytes, other.bytes);
        }

        @Override
        public int hashCode() {
            return Arrays.hashCode(bytes);
        }
    }

    // ==================================================================================
    // Transaction
    // ==================================================================================

    final class MemoryTx<E extends Enum<E> & ColumnSchema> implements StoreTransaction {
        private final SkipListStore<E> store;
        final List<Op> buffer = new ArrayList<>();
        private boolean done = false;

        MemoryTx(SkipListStore<E> store) {
            this.store = store;
        }

        @Override
        @SuppressWarnings("unchecked")
        public void commit() {
            if (done) return;
            done = true;
            for (Op op : buffer) {
                E col = (E) op.column;
                if (op.isDelete) {
                    store.delete(col, op.key);
                } else {
                    store.put(col, op.key, op.value);
                }
            }
        }

        @Override
        public void rollback() {
            if (!done) {
                done = true;
                buffer.clear();
            }
        }

        @Override
        public void close() {
            if (!done) rollback();
        }

        @Override
        public <T> T unwrap(Class<T> type) {
            return null;
        }
    }

    record Op(Enum<?> column, byte[] key, byte[] value, boolean isDelete) {}

    // ==================================================================================
    // Iterator
    // ==================================================================================

    final class MapIterator implements CloseableIterator<KeyValue> {
        private final Iterator<Map.Entry<Key, byte[]>> delegate;

        MapIterator(Iterator<Map.Entry<Key, byte[]>> delegate) {
            this.delegate = delegate;
        }

        @Override
        public boolean hasNext() {
            return delegate.hasNext();
        }

        @Override
        public KeyValue next() {
            Map.Entry<Key, byte[]> entry = delegate.next();
            byte[] key = Arrays.copyOf(entry.getKey().bytes, entry.getKey().bytes.length);
            byte[] value = Arrays.copyOf(entry.getValue(), entry.getValue().length);
            return new KeyValue(key, value);
        }

        @Override
        public void close() {}
    }

    // ==================================================================================
    // Helpers
    // ==================================================================================

    private static Key prefixEnd(byte[] prefix) {
        byte[] end = Arrays.copyOf(prefix, prefix.length);
        for (int i = end.length - 1; i >= 0; i--) {
            if ((end[i] & 0xFF) < 0xFF) {
                end[i]++;
                return new Key(end);
            }
            end[i] = 0;
        }
        return null;
    }
}
