package dev.everydaythings.graph.library.directory;

import dev.everydaythings.graph.item.id.ItemID;
import dev.everydaythings.graph.library.ItemStore;
import dev.everydaythings.graph.library.Service;
import dev.everydaythings.graph.library.WriteTransaction;
import dev.everydaythings.graph.library.bytestore.ByteStore;
import dev.everydaythings.graph.library.bytestore.ColumnSchema;
import dev.everydaythings.graph.library.bytestore.KeyEncoder;
import lombok.Getter;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Directory for locating items across multiple stores.
 *
 * <p>Implementations must also implement a ByteStore with {@link Column}.
 * All operations are provided as default methods.
 *
 * <p>Implementations must also provide a store registry for resolving paths to stores.
 *
 * <p>ItemDirectory extends {@link dev.everydaythings.graph.library.Service} for lifecycle
 * management and UI presentation. Service methods are provided by the underlying ByteStore.
 *
 * <p>Example implementation:
 * <pre>{@code
 * public class SkipListItemDirectory implements ItemDirectory, SkipListStore<ItemDirectory.Column> {
 *     @Getter private final SkipListStore.Opened<Column> opened;
 *     @Getter private final Map<Path, ItemStore> storeRegistry = new ConcurrentHashMap<>();
 *     // That's it!
 * }
 * }</pre>
 */
public interface ItemDirectory extends Service {

    /** Logger for default method implementations. */
    Logger log = LogManager.getLogger(ItemDirectory.class);

    // ==================================================================================
    // Location Types
    // ==================================================================================

    sealed interface Location permits InStore, Rumor {}

    record InStore(ItemStore store) implements Location {}

    record Rumor(ItemID principal, String hints) implements Location {}

    record Entry(ItemID iid, Location location) {}

    // ==================================================================================
    // Store Registry - Must be provided by implementations
    // ==================================================================================

    /**
     * Get the store registry for path-to-store resolution.
     */
    Map<Path, ItemStore> storeRegistry();

    /**
     * Register a store for runtime resolution.
     */
    default void registerStore(Path workingTreePath, ItemStore store) {
        storeRegistry().put(workingTreePath.toAbsolutePath().normalize(), store);
    }

    /**
     * Unregister a store.
     */
    default void unregisterStore(Path workingTreePath) {
        storeRegistry().remove(workingTreePath.toAbsolutePath().normalize());
    }

    // ==================================================================================
    // ByteStore Access
    // ==================================================================================

    @SuppressWarnings("unchecked")
    private ByteStore<Column> store() {
        return (ByteStore<Column>) this;
    }

    // ==================================================================================
    // Lookup
    // ==================================================================================

    /**
     * Locate an item.
     */
    default Optional<Entry> locate(ItemID iid) {
        Objects.requireNonNull(iid, "iid");

        byte[] key = Column.LOCATIONS.key(iid);
        byte[] data = store().get(Column.LOCATIONS, key);
        if (data == null) {
            log.trace("locate() - not found: iid={}", iid.encodeText());
            return Optional.empty();
        }

        log.trace("locate() - found: iid={}", iid.encodeText());
        Location location = deserializeLocation(data);
        return Optional.of(new Entry(iid, location));
    }

    default boolean contains(ItemID iid) {
        return locate(iid).isPresent();
    }

    // ==================================================================================
    // Registration
    // ==================================================================================

    /**
     * Register an item's location in a store.
     */
    default void register(ItemID iid, ItemStore store, WriteTransaction wtx) {
        Objects.requireNonNull(iid, "iid");
        Objects.requireNonNull(store, "store");
        Objects.requireNonNull(wtx, "wtx");

        Path storePath = findPathForStore(store);
        if (storePath == null) {
            log.warn("register() - Store not in registry, iid={}", iid.encodeText());
            throw new IllegalArgumentException("Store not registered in directory's store registry");
        }

        byte[] key = Column.LOCATIONS.key(iid);
        byte[] data = serializeInStore(storePath);
        log.trace("register() - iid={}, path={}", iid.encodeText(), storePath);
        store().put(Column.LOCATIONS, key, data, wtx);
    }

    /**
     * Register an item with a specific path.
     */
    default void register(ItemID iid, Path path, WriteTransaction wtx) {
        Objects.requireNonNull(iid, "iid");
        Objects.requireNonNull(path, "path");
        Objects.requireNonNull(wtx, "wtx");

        byte[] data = serializeInStore(path.toAbsolutePath().normalize());
        store().put(Column.LOCATIONS, Column.LOCATIONS.key(iid), data, wtx);
    }

    /**
     * Register a rumor about an item.
     */
    default void registerRumor(ItemID iid, Rumor rumor, WriteTransaction wtx) {
        Objects.requireNonNull(iid, "iid");
        Objects.requireNonNull(rumor, "rumor");
        Objects.requireNonNull(wtx, "wtx");

        byte[] data = serializeRumor(rumor);
        store().put(Column.LOCATIONS, Column.LOCATIONS.key(iid), data, wtx);
    }

    /**
     * Remove an item from the directory.
     */
    default void unregister(ItemID iid, WriteTransaction wtx) {
        Objects.requireNonNull(iid, "iid");
        Objects.requireNonNull(wtx, "wtx");

        store().delete(Column.LOCATIONS, Column.LOCATIONS.key(iid), wtx);
    }

    // ==================================================================================
    // Transactions
    // ==================================================================================

    default WriteTransaction beginWriteTransaction() {
        return store().beginTransaction();
    }

    default void runInWriteTransaction(Consumer<WriteTransaction> work) {
        try (WriteTransaction tx = beginWriteTransaction()) {
            work.accept(tx);
            tx.commit();
        }
    }

    default boolean isWritable() {
        return true;
    }

    // ==================================================================================
    // Serialization Helpers
    // ==================================================================================

    byte TYPE_IN_STORE = 0x01;
    byte TYPE_RUMOR = 0x02;

    private byte[] serializeInStore(Path path) {
        byte[] pathBytes = path.toString().getBytes(StandardCharsets.UTF_8);
        ByteBuffer buf = ByteBuffer.allocate(1 + 4 + pathBytes.length);
        buf.put(TYPE_IN_STORE);
        buf.putInt(pathBytes.length);
        buf.put(pathBytes);
        return buf.array();
    }

    private byte[] serializeRumor(Rumor rumor) {
        byte[] principalBytes = rumor.principal() != null
                ? rumor.principal().encodeBinary()
                : new byte[0];
        byte[] hintsBytes = rumor.hints() != null
                ? rumor.hints().getBytes(StandardCharsets.UTF_8)
                : new byte[0];

        ByteBuffer buf = ByteBuffer.allocate(1 + 4 + principalBytes.length + 4 + hintsBytes.length);
        buf.put(TYPE_RUMOR);
        buf.putInt(principalBytes.length);
        buf.put(principalBytes);
        buf.putInt(hintsBytes.length);
        buf.put(hintsBytes);
        return buf.array();
    }

    private Location deserializeLocation(byte[] data) {
        ByteBuffer buf = ByteBuffer.wrap(data);
        byte type = buf.get();

        return switch (type) {
            case TYPE_IN_STORE -> {
                int pathLen = buf.getInt();
                byte[] pathBytes = new byte[pathLen];
                buf.get(pathBytes);
                Path path = Path.of(new String(pathBytes, StandardCharsets.UTF_8));

                ItemStore store = storeRegistry().get(path);
                if (store == null) {
                    throw new IllegalStateException("Store not found in registry for path: " + path);
                }
                yield new InStore(store);
            }
            case TYPE_RUMOR -> {
                int principalLen = buf.getInt();
                ItemID principal = principalLen > 0
                        ? new ItemID(readBytes(buf, principalLen))
                        : null;
                int hintsLen = buf.getInt();
                String hints = hintsLen > 0
                        ? new String(readBytes(buf, hintsLen), StandardCharsets.UTF_8)
                        : null;
                yield new Rumor(principal, hints);
            }
            default -> throw new IllegalStateException("Unknown location type: " + type);
        };
    }

    private byte[] readBytes(ByteBuffer buf, int len) {
        byte[] bytes = new byte[len];
        buf.get(bytes);
        return bytes;
    }

    private Path findPathForStore(ItemStore store) {
        for (Map.Entry<Path, ItemStore> entry : storeRegistry().entrySet()) {
            if (entry.getValue() == store) {
                return entry.getKey();
            }
        }
        return null;
    }

    // ==================================================================================
    // Lifecycle
    // ==================================================================================

    // close() provided by ByteStore implementation - but clear registry too
    // Implementations should override close() to call storeRegistry().clear()

    // ==================================================================================
    // Column Schema
    // ==================================================================================

    @Getter
    enum Column implements ColumnSchema {
        DEFAULT("default", null, null, KeyEncoder.RAW),
        LOCATIONS("item.locations", 32, 10, KeyEncoder.ID);

        private final String schemaName;
        private final Integer prefixLen;
        private final Integer bloomBits;
        private final KeyEncoder[] keyComposition;

        Column(String schemaName, Integer prefixLen, Integer bloomBits, KeyEncoder... keyParts) {
            this.schemaName = schemaName;
            this.prefixLen = prefixLen;
            this.bloomBits = bloomBits;
            this.keyComposition = keyParts;
        }
    }
}
