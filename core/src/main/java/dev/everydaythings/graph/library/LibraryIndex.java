package dev.everydaythings.graph.library;

import dev.everydaythings.graph.item.id.ItemID;
import dev.everydaythings.graph.item.id.RelationID;
import dev.everydaythings.graph.item.id.VersionID;
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
 * <p>Implementations must also implement a ByteStore with {@link Column}.
 * All operations are provided as default methods using the underlying ByteStore.
 *
 * <p>LibraryIndex extends {@link Service} for lifecycle management and UI presentation.
 * Service methods are provided by the underlying ByteStore implementation.
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
     * A lightweight reference to a relation: its subject and RID.
     * Used to resolve relation bytes from ItemStore.RELATION column.
     */
    record RelationRef(ItemID subject, RelationID rid) {}

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
     * Query relation refs by subject.
     */
    default Stream<RelationRef> bySubject(ItemID subject) {
        Objects.requireNonNull(subject, "subject");
        byte[] prefix = Column.REL_BY_SUBJ.keyPrefix(subject);
        return streamRefsWithPrefix(Column.REL_BY_SUBJ, prefix);
    }

    /**
     * Query relation refs by subject and predicate.
     */
    default Stream<RelationRef> bySubjectPredicate(ItemID subject, ItemID predicate) {
        Objects.requireNonNull(subject, "subject");
        Objects.requireNonNull(predicate, "predicate");
        byte[] prefix = Column.REL_BY_SUBJ.keyPrefix(subject, predicate);
        return streamRefsWithPrefix(Column.REL_BY_SUBJ, prefix);
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
     * Query relation refs by object.
     */
    default Stream<RelationRef> byObject(ItemID object) {
        Objects.requireNonNull(object, "object");
        byte[] prefix = Column.REL_BY_OBJ.keyPrefix(object);
        return streamRefsWithPrefix(Column.REL_BY_OBJ, prefix);
    }

    /**
     * Query relation refs by object and predicate.
     */
    default Stream<RelationRef> byObjectPredicate(ItemID object, ItemID predicate) {
        Objects.requireNonNull(object, "object");
        Objects.requireNonNull(predicate, "predicate");
        byte[] prefix = Column.REL_BY_OBJ.keyPrefix(object, predicate);
        return streamRefsWithPrefix(Column.REL_BY_OBJ, prefix);
    }

    /**
     * Extract (subject, rid) pairs from fan-out index entries.
     *
     * <p>Fan-out values store the subject IID bytes. RID is extracted from
     * the end of the key. Together they form the lookup key for
     * ItemStore.RELATION column.
     */
    private Stream<RelationRef> streamRefsWithPrefix(Column cf, byte[] prefix) {
        final int ridSize = 34;
        List<RelationRef> results = new ArrayList<>();

        try (var it = store().iterate(cf, prefix)) {
            while (it.hasNext()) {
                var kv = it.next();
                byte[] key = kv.key();

                if (key.length < ridSize) continue;

                // Extract RID from end of key
                byte[] ridBytes = new byte[ridSize];
                System.arraycopy(key, key.length - ridSize, ridBytes, 0, ridSize);
                RelationID rid = new RelationID(ridBytes);

                // Extract subject from value (stored during indexing)
                byte[] value = kv.value();
                if (value != null && value.length >= ridSize) {
                    ItemID subject = new ItemID(value);
                    results.add(new RelationRef(subject, rid));
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
     * Index a relation.
     */
    default void indexRelation(ItemID subject, ItemID predicate, ItemID object,
                               RelationID rid, byte[] relationRecord, WriteTransaction wtx) {
        Objects.requireNonNull(subject, "subject");
        Objects.requireNonNull(predicate, "predicate");
        Objects.requireNonNull(rid, "rid");
        Objects.requireNonNull(relationRecord, "relationRecord");
        Objects.requireNonNull(wtx, "wtx");

        // Fan-out values store subject IID for later resolution via ItemStore.RELATION
        byte[] subjectBytes = subject.encodeBinary();

        // Index by subject + predicate
        store().put(Column.REL_BY_SUBJ, Column.REL_BY_SUBJ.key(subject, predicate, rid), subjectBytes, wtx);

        // Index by predicate alone
        store().put(Column.REL_BY_PRED, Column.REL_BY_PRED.key(predicate, rid), subjectBytes, wtx);

        // Index by object + predicate (only if object is an IID)
        if (object != null) {
            store().put(Column.REL_BY_OBJ, Column.REL_BY_OBJ.key(object, predicate, rid), subjectBytes, wtx);
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
        REL_BY_SUBJ("rel.subject.index", 64, 10, KeyEncoder.ID, KeyEncoder.ID, KeyEncoder.ID),
        REL_BY_OBJ("rel.object.index", 64, 10, KeyEncoder.ID, KeyEncoder.ID, KeyEncoder.ID),
        REL_BY_PRED("rel.predicate.index", 32, 10, KeyEncoder.ID, KeyEncoder.ID),
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
