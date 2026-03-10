package dev.everydaythings.graph.library;

import dev.everydaythings.graph.item.id.ContentID;
import dev.everydaythings.graph.item.id.ItemID;
import dev.everydaythings.graph.item.id.RelationID;
import dev.everydaythings.graph.item.id.VersionID;
import dev.everydaythings.graph.item.relation.Relation;
import dev.everydaythings.graph.library.bytestore.ByteStore;
import dev.everydaythings.graph.library.bytestore.ColumnSchema;
import dev.everydaythings.graph.library.bytestore.KeyEncoder;
import lombok.Getter;

import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Stream;

/**
 * Unified index for relation queries across all stores in a Library.
 *
 * <p>Fan-out indexes map binding participants to RECORD CIDs, which are then
 * used to load the actual relation bytes from {@link ItemStore}.
 *
 * <p>Two fan-out columns:
 * <ul>
 *   <li>{@code REL_BY_ITEM} — indexes every IidTarget binding:
 *       Key: itemIID | predicate | recordCID → empty value.
 *       Enables "all relations involving item X" and "all relations
 *       involving item X via predicate P".</li>
 *   <li>{@code REL_BY_PRED} — indexes by predicate alone:
 *       Key: predicate | recordCID → empty value.
 *       Enables "all relations of type P".</li>
 * </ul>
 *
 * <p>Implementations must also implement a ByteStore with {@link Column}.
 * All operations are provided as default methods using the underlying ByteStore.
 *
 * <p>Example implementation:
 * <pre>{@code
 * public class SkipListLibraryIndex implements LibraryIndex, SkipListStore<LibraryIndex.Column> {
 *     @Getter private final SkipListStore.Opened<Column> opened;
 *     // That's it - all methods are default!
 * }
 * }</pre>
 */
public interface LibraryIndex extends Service {

    /**
     * A lightweight reference to a relation: its RID and RECORD CID.
     * RECORD CID is used to load bytes from ItemStore.RELATION column.
     * RID is the semantic identity (hash of BODY).
     */
    record RelationRef(RelationID rid, ContentID recordCid) {}

    // ==================================================================================
    // ByteStore Access
    // ==================================================================================

    /**
     * Access the underlying ByteStore.
     */
    @SuppressWarnings("unchecked")
    private ByteStore<Column> store() {
        return (ByteStore<Column>) this;
    }

    // ==================================================================================
    // Query API
    // ==================================================================================

    /**
     * Query relation refs by a participating item (any role).
     */
    default Stream<RelationRef> byItem(ItemID item) {
        Objects.requireNonNull(item, "item");
        byte[] prefix = Column.REL_BY_ITEM.keyPrefix(item);
        return streamRefsWithPrefix(Column.REL_BY_ITEM, prefix);
    }

    /**
     * Query relation refs by a participating item and predicate.
     */
    default Stream<RelationRef> byItemPredicate(ItemID item, ItemID predicate) {
        Objects.requireNonNull(item, "item");
        Objects.requireNonNull(predicate, "predicate");
        byte[] prefix = Column.REL_BY_ITEM.keyPrefix(item, predicate);
        return streamRefsWithPrefix(Column.REL_BY_ITEM, prefix);
    }

    /**
     * Query relation refs by predicate only.
     */
    default Stream<RelationRef> byPredicate(ItemID predicate) {
        Objects.requireNonNull(predicate, "predicate");
        byte[] prefix = Column.REL_BY_PRED.keyPrefix(predicate);
        return streamRefsWithPrefix(Column.REL_BY_PRED, prefix);
    }

    /**
     * Extract RelationRefs from fan-out index entries.
     *
     * <p>The RECORD CID is extracted from the last 34 bytes of the key.
     * The RID is stored as the value.
     */
    private Stream<RelationRef> streamRefsWithPrefix(Column cf, byte[] prefix) {
        final int cidSize = 34;
        List<RelationRef> results = new ArrayList<>();

        try (var it = store().iterate(cf, prefix)) {
            while (it.hasNext()) {
                var kv = it.next();
                byte[] key = kv.key();

                if (key.length < cidSize) continue;

                // Extract RECORD CID from end of key
                byte[] cidBytes = new byte[cidSize];
                System.arraycopy(key, key.length - cidSize, cidBytes, 0, cidSize);
                ContentID recordCid = new ContentID(cidBytes);

                // Extract RID from value
                byte[] value = kv.value();
                if (value != null && value.length > 0) {
                    RelationID rid = new RelationID(value);
                    results.add(new RelationRef(rid, recordCid));
                }
            }
        }
        return results.stream();
    }

    // ==================================================================================
    // Item Records
    // ==================================================================================

    /**
     * Get the opaque record bytes for an item.
     */
    default Optional<byte[]> getItemRecord(ItemID iid) {
        Objects.requireNonNull(iid, "iid");
        byte[] raw = store().get(Column.ITEMS, Column.ITEMS.key(iid));
        return Optional.ofNullable(raw);
    }

    /**
     * Store/update an item's record bytes.
     */
    default void putItemRecord(ItemID iid, byte[] recordBytes, WriteTransaction wtx) {
        Objects.requireNonNull(iid, "iid");
        Objects.requireNonNull(recordBytes, "recordBytes");
        Objects.requireNonNull(wtx, "wtx");
        store().put(Column.ITEMS, Column.ITEMS.key(iid), recordBytes, wtx);
    }

    /**
     * Ensure an item exists in the index.
     */
    default void touchItem(ItemID iid, WriteTransaction wtx) {
        Objects.requireNonNull(iid, "iid");
        Objects.requireNonNull(wtx, "wtx");
        byte[] existing = store().get(Column.ITEMS, Column.ITEMS.key(iid));
        if (existing == null) {
            store().put(Column.ITEMS, Column.ITEMS.key(iid), new byte[0], wtx);
        }
    }

    // ==================================================================================
    // Relation Indexing
    // ==================================================================================

    /**
     * Index a relation by its bindings.
     *
     * <p>Creates fan-out entries for every IidTarget binding, enabling
     * lookup by any participating item. The RECORD CID is the storage key;
     * the RID (semantic identity) is stored as the value.
     *
     * @param relation The relation to index
     * @param recordCid The content ID of the stored RECORD bytes
     * @param wtx Write transaction
     */
    default void indexRelation(Relation relation, ContentID recordCid, WriteTransaction wtx) {
        Objects.requireNonNull(relation, "relation");
        Objects.requireNonNull(recordCid, "recordCid");
        Objects.requireNonNull(wtx, "wtx");

        ItemID predicate = relation.predicate();
        byte[] ridBytes = relation.rid().encodeBinary();

        // Index by predicate
        store().put(Column.REL_BY_PRED, Column.REL_BY_PRED.key(predicate, recordCid), ridBytes, wtx);

        // Index by each IidTarget binding (any role)
        if (relation.bindings() != null) {
            for (var entry : relation.bindings().entrySet()) {
                if (entry.getValue() instanceof Relation.IidTarget iidTarget) {
                    ItemID participantIid = iidTarget.iid();
                    store().put(Column.REL_BY_ITEM,
                            Column.REL_BY_ITEM.key(participantIid, predicate, recordCid),
                            ridBytes, wtx);
                }
            }
        }
    }

    // ==================================================================================
    // Head Tracking
    // ==================================================================================

    /**
     * Set the head version for an item as seen by a principal.
     */
    default void setHead(ItemID principal, ItemID item, String channel, VersionID vid, WriteTransaction wtx) {
        Objects.requireNonNull(principal, "principal");
        Objects.requireNonNull(item, "item");
        Objects.requireNonNull(vid, "vid");
        Objects.requireNonNull(wtx, "wtx");
        store().put(Column.HEADS, Column.HEADS.key(principal, item), vid.encodeBinary(), wtx);
    }

    /**
     * Get the head version for an item as seen by a principal.
     */
    default VersionID getHead(ItemID principal, ItemID item, String channel) {
        Objects.requireNonNull(principal, "principal");
        Objects.requireNonNull(item, "item");
        byte[] vidBytes = store().get(Column.HEADS, Column.HEADS.key(principal, item));
        return vidBytes != null ? new VersionID(vidBytes) : null;
    }

    /**
     * Get all items that a principal has head versions for.
     */
    default Stream<Map.Entry<ItemID, VersionID>> headsFor(ItemID principal) {
        Objects.requireNonNull(principal, "principal");
        byte[] prefix = Column.HEADS.keyPrefix(principal);

        List<Map.Entry<ItemID, VersionID>> results = new ArrayList<>();
        try (var it = store().iterate(Column.HEADS, prefix)) {
            while (it.hasNext()) {
                var kv = it.next();
                byte[] key = kv.key();

                // Extract ItemID (second 34 bytes)
                byte[] iidBytes = new byte[34];
                System.arraycopy(key, 34, iidBytes, 0, 34);
                ItemID iid = new ItemID(iidBytes);

                VersionID vid = new VersionID(kv.value());
                results.add(Map.entry(iid, vid));
            }
        }
        return results.stream();
    }

    // ==================================================================================
    // Transactions
    // ==================================================================================

    /**
     * Begin a write transaction.
     */
    default WriteTransaction beginWriteTransaction() {
        return store().beginTransaction();
    }

    /**
     * Run work in a write transaction.
     */
    default void runInWriteTransaction(Consumer<WriteTransaction> work) {
        try (WriteTransaction tx = beginWriteTransaction()) {
            work.accept(tx);
            tx.commit();
        }
    }

    // ==================================================================================
    // Lifecycle & Utilities
    // ==================================================================================

    default boolean isWritable() {
        return true;
    }

    default void exportTo(LibraryIndex target) {
        // Default: no-op
    }

    // close() provided by ByteStore implementation

    // ==================================================================================
    // Column Schema
    // ==================================================================================

    @Getter
    enum Column implements ColumnSchema {
        DEFAULT("default", null, null, KeyEncoder.RAW),
        ITEMS("item.index", null, 10, KeyEncoder.ID),

        /** Fan-out by participating item: itemIID | predicate | recordCID → RID bytes. */
        REL_BY_ITEM("rel.item.index", 34, 10, KeyEncoder.ID, KeyEncoder.ID, KeyEncoder.ID),

        /** Fan-out by predicate: predicate | recordCID → RID bytes. */
        REL_BY_PRED("rel.predicate.index", 34, 10, KeyEncoder.ID, KeyEncoder.ID),

        REL_HANDLE("rel.handle.index", 32, 10, KeyEncoder.ID, KeyEncoder.HANDLE),
        HEADS("heads", 34, 10, KeyEncoder.ID, KeyEncoder.ID);

        private final String schemaName;
        private final Integer prefixLen;
        private final Integer bloomBits;
        private final KeyEncoder[] keyComposition;

        Column(String schemaName, Integer prefixLen, Integer bloomBits, KeyEncoder... keyComposition) {
            this.schemaName = schemaName;
            this.prefixLen = prefixLen;
            this.bloomBits = bloomBits;
            this.keyComposition = keyComposition;
        }
    }
}
