package dev.everydaythings.graph.library.bytestore;

import dev.everydaythings.graph.library.Service;
import dev.everydaythings.graph.library.WriteTransaction;

import java.util.Iterator;
import java.util.function.BiConsumer;

/**
 * Generic byte-level key-value store interface.
 *
 * <p>ByteStore provides a backend-agnostic interface for storing and retrieving
 * byte arrays organized by column schemas. Implementations can use various backends:
 * <ul>
 *   <li>RocksDB for persistent, high-performance storage</li>
 *   <li>MapDB for in-memory or file-based storage</li>
 *   <li>Simple maps for testing</li>
 * </ul>
 *
 * <p>All ByteStores are {@link Service}s with lifecycle management (start/stop/status).
 *
 * <p>The fluent API provides a clean way to work with stores:
 * <pre>{@code
 * // Simple operations
 * store.db(USERS).key(userId).put(userData);
 * byte[] data = store.db(USERS).key(userId).get();
 * store.db(USERS).key(userId).delete();
 *
 * // With transactions
 * try (var tx = store.beginTransaction()) {
 *     store.db(USERS).transaction(tx).key(userId).put(userData);
 *     tx.commit();
 * }
 * }</pre>
 *
 * @param <E> the column schema enum type
 */
public interface ByteStore<E extends Enum<E> & ColumnSchema> extends Service {

    // ==================================================================================
    // Service Implementation
    // ==================================================================================

    /**
     * Check if the store is currently open.
     *
     * <p>Implementations should return true if the underlying database is open
     * and ready for operations.
     *
     * @return true if open
     */
    boolean isOpen();

    /**
     * Derive status from open state.
     */
    @Override
    default Status status() {
        return isOpen() ? Status.RUNNING : Status.STOPPED;
    }

    /**
     * Start is a no-op if already running.
     *
     * <p>ByteStores are typically opened during construction.
     * Restart requires creating a new instance.
     */
    @Override
    default void start() {
        if (isOpen()) return;
        throw new UnsupportedOperationException(
            "ByteStore cannot be restarted after close. Create a new instance.");
    }

    /**
     * Stop delegates to close.
     */
    @Override
    default void stop() {
        // Subclasses provide actual close implementation
    }

    // ==================================================================================
    // Fluent API
    // ==================================================================================

    /**
     * Start a fluent operation on a column.
     *
     * @param column the column to operate on
     * @return an Ops instance for fluent chaining
     */
    default Ops<E> db(E column) {
        return new Ops<>(this, column, null);
    }

    /**
     * Fluent operations builder for a column.
     *
     * @param <E> the column schema enum type
     */
    final class Ops<E extends Enum<E> & ColumnSchema> {
        private final ByteStore<E> store;
        private final E column;
        private final WriteTransaction tx;

        Ops(ByteStore<E> store, E column, WriteTransaction tx) {
            this.store = store;
            this.column = column;
            this.tx = tx;
        }

        /**
         * Bind this operation to a transaction.
         */
        public Ops<E> transaction(WriteTransaction tx) {
            return new Ops<>(store, column, tx);
        }

        /**
         * Specify the key parts for the operation.
         */
        public Bound key(Object... parts) {
            return new Bound(column.key(parts));
        }

        /**
         * Bound operations for a specific key.
         */
        public final class Bound {
            private final byte[] key;

            Bound(byte[] key) {
                this.key = key;
            }

            /**
             * Get the value at this key.
             */
            public byte[] get() {
                return store.get(column, key);
            }

            /**
             * Put a value at this key.
             */
            public void put(byte[] value) {
                if (tx != null) {
                    store.put(column, key, value, tx);
                } else {
                    store.put(column, key, value);
                }
            }

            /**
             * Delete this key.
             */
            public void delete() {
                if (tx != null) {
                    store.delete(column, key, tx);
                } else {
                    store.delete(column, key);
                }
            }

            /**
             * Check if this key exists.
             */
            public boolean exists() {
                return store.exists(column, key);
            }
        }
    }

    // ==================================================================================
    // Basic Operations
    // ==================================================================================

    /**
     * Get a value by key.
     *
     * @param column the column schema
     * @param key the key bytes
     * @return the value bytes, or null if not found
     */
    byte[] get(E column, byte[] key);

    /**
     * Put a key-value pair.
     *
     * @param column the column schema
     * @param key the key bytes
     * @param value the value bytes
     */
    void put(E column, byte[] key, byte[] value);

    /**
     * Delete a key.
     *
     * @param column the column schema
     * @param key the key bytes
     */
    void delete(E column, byte[] key);

    /**
     * Check if a key exists (may be approximate for bloom-filtered stores).
     *
     * @param column the column schema
     * @param key the key bytes
     * @return true if the key may exist
     */
    default boolean exists(E column, byte[] key) {
        return get(column, key) != null;
    }

    // ==================================================================================
    // Iteration
    // ==================================================================================

    /**
     * Iterate over all entries matching a prefix.
     *
     * <p>The returned iterator must be closed after use to release resources.
     *
     * @param column the column schema
     * @param prefix the key prefix (empty for all entries)
     * @return a closeable iterator over matching key-value pairs
     */
    CloseableIterator<KeyValue> iterate(E column, byte[] prefix);

    /**
     * Iterate over all entries in a column.
     */
    default CloseableIterator<KeyValue> iterateAll(E column) {
        return iterate(column, new byte[0]);
    }

    /**
     * Process all entries matching a prefix.
     *
     * @param column the column schema
     * @param prefix the key prefix
     * @param consumer consumer for each key-value pair
     */
    default void forEach(E column, byte[] prefix, BiConsumer<byte[], byte[]> consumer) {
        try (CloseableIterator<KeyValue> it = iterate(column, prefix)) {
            while (it.hasNext()) {
                KeyValue kv = it.next();
                consumer.accept(kv.key(), kv.value());
            }
        }
    }

    // ==================================================================================
    // Transactions
    // ==================================================================================

    /**
     * Begin a write transaction.
     *
     * <p>Use try-with-resources to ensure proper cleanup:
     * <pre>{@code
     * try (var tx = store.beginTransaction()) {
     *     store.put(col, key, value, tx);
     *     tx.commit();
     * }
     * }</pre>
     *
     * @return a new write transaction
     */
    StoreTransaction beginTransaction();

    /**
     * Put within a transaction.
     */
    void put(E column, byte[] key, byte[] value, WriteTransaction tx);

    /**
     * Delete within a transaction.
     */
    void delete(E column, byte[] key, WriteTransaction tx);

    // ==================================================================================
    // Types
    // ==================================================================================

    /**
     * A key-value pair.
     */
    record KeyValue(byte[] key, byte[] value) {}

    /**
     * An iterator that must be closed to release resources.
     */
    interface CloseableIterator<T> extends Iterator<T>, AutoCloseable {
        @Override
        void close();  // no checked exception
    }

    /**
     * Store-specific transaction that can provide access to the underlying batch.
     */
    interface StoreTransaction extends WriteTransaction {
        /**
         * Unwrap to a backend-specific type.
         *
         * @param type the expected type
         * @return the unwrapped object, or null if not that type
         */
        <T> T unwrap(Class<T> type);
    }
}
