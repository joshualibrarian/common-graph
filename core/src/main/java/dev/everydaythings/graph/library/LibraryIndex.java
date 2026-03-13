package dev.everydaythings.graph.library;

import dev.everydaythings.graph.frame.BindingTarget;
import dev.everydaythings.graph.frame.FrameBody;
import dev.everydaythings.graph.frame.FrameEntry;
import dev.everydaythings.graph.item.id.ContentID;
import dev.everydaythings.graph.item.id.ItemID;
import dev.everydaythings.graph.library.bytestore.ByteStore;
import dev.everydaythings.graph.library.bytestore.ColumnSchema;
import dev.everydaythings.graph.library.bytestore.KeyEncoder;
import lombok.Getter;

import java.nio.ByteBuffer;
import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Stream;

/**
 * Four derived indexes, all rebuildable from the object store.
 *
 * <ul>
 *   <li>{@code ITEMS} — version history: IID | VID → timestamp.
 *       Prefix scan by IID returns all versions. Latest found by highest timestamp.</li>
 *   <li>{@code FRAME_BY_ITEM} — indexes every ItemID participant (including predicate):
 *       Key: itemIID | predicate | bodyHash → storageCid bytes.
 *       Predicates are indexed as participants (predicate | predicate | bodyHash),
 *       so querying by predicate is just a prefix scan.</li>
 *   <li>{@code RECORD_BY_BODY} — indexes records by body hash:
 *       Key: bodyHash | signerKeyId → storageCid bytes.
 *       Enables "who attests this assertion?" and attestation counting.</li>
 *   <li>{@code HEADS} — current version per principal:
 *       Key: principal | IID → VID bytes.</li>
 * </ul>
 *
 * <p>Implementations must also implement a ByteStore with {@link Column}.
 */
public interface LibraryIndex extends Service {

    /**
     * A lightweight reference to a frame: its body hash and storage CID.
     */
    record FrameRef(ContentID bodyHash, ContentID storageCid) {}

    /**
     * A lightweight reference to a record attesting a frame body.
     */
    record RecordRef(ContentID signerKeyId, ContentID storageCid) {}


    // ==================================================================================
    // ByteStore Access
    // ==================================================================================

    @SuppressWarnings("unchecked")
    private ByteStore<Column> store() {
        return (ByteStore<Column>) this;
    }

    // ==================================================================================
    // Version Index (ITEMS)
    // ==================================================================================

    /**
     * Index a version in ITEMS: IID | VID → timestamp.
     *
     * @param iid       the item ID
     * @param vid       the version ID
     * @param timestamp storage timestamp (millis since epoch)
     * @param wtx       write transaction
     */
    default void indexVersion(ItemID iid, ContentID vid, long timestamp, WriteTransaction wtx) {
        Objects.requireNonNull(iid, "iid");
        Objects.requireNonNull(vid, "vid");
        Objects.requireNonNull(wtx, "wtx");

        byte[] key = Column.ITEMS.key(iid, vid);
        byte[] value = ByteBuffer.allocate(8).putLong(timestamp).array();
        store().put(Column.ITEMS, key, value, wtx);
    }

    /**
     * Find the latest version for an item by scanning ITEMS[IID] and
     * selecting the VID with the highest timestamp.
     *
     * @param iid the item ID
     * @return the latest VID, or empty if no versions exist
     */
    default Optional<ContentID> latestVersion(ItemID iid) {
        Objects.requireNonNull(iid, "iid");
        byte[] prefix = Column.ITEMS.keyPrefix(iid);

        ContentID bestVid = null;
        long bestTimestamp = Long.MIN_VALUE;
        final int idSize = 34;

        try (var it = store().iterate(Column.ITEMS, prefix)) {
            while (it.hasNext()) {
                var kv = it.next();
                byte[] key = kv.key();

                // Key is IID(34) | VID(34) = 68 bytes
                if (key.length < idSize * 2) continue;

                byte[] vidBytes = new byte[idSize];
                System.arraycopy(key, idSize, vidBytes, 0, idSize);

                // Value is timestamp (8 bytes)
                byte[] value = kv.value();
                long ts = (value != null && value.length >= 8)
                        ? ByteBuffer.wrap(value).getLong()
                        : 0L;

                if (ts >= bestTimestamp) {
                    bestTimestamp = ts;
                    bestVid = new ContentID(vidBytes);
                }
            }
        }

        return Optional.ofNullable(bestVid);
    }

    /**
     * Check if any version exists for an item.
     */
    default boolean hasItem(ItemID iid) {
        return latestVersion(iid).isPresent();
    }

    // ==================================================================================
    // Deprecated Item Record API (replaced by indexVersion/latestVersion)
    // ==================================================================================

    /** @deprecated Use {@link #latestVersion(ItemID)} */
    @Deprecated
    default Optional<byte[]> getItemRecord(ItemID iid) {
        return latestVersion(iid).map(ContentID::encodeBinary);
    }

    /** @deprecated Use {@link #indexVersion(ItemID, ContentID, long, WriteTransaction)} */
    @Deprecated
    default void putItemRecord(ItemID iid, byte[] recordBytes, WriteTransaction wtx) {
        Objects.requireNonNull(iid, "iid");
        Objects.requireNonNull(recordBytes, "recordBytes");
        Objects.requireNonNull(wtx, "wtx");
        // Legacy: recordBytes is VID bytes, timestamp defaults to now
        ContentID vid = new ContentID(recordBytes);
        indexVersion(iid, vid, System.currentTimeMillis(), wtx);
    }

    /** @deprecated Use {@link #indexVersion} */
    @Deprecated
    default void touchItem(ItemID iid, WriteTransaction wtx) {
        // No-op in new model — items are tracked via ITEMS index entries
    }

    // ==================================================================================
    // Frame Query API
    // ==================================================================================

    /**
     * Query frame refs by a participating item (any role).
     */
    default Stream<FrameRef> framesByItem(ItemID item) {
        Objects.requireNonNull(item, "item");
        byte[] prefix = Column.FRAME_BY_ITEM.keyPrefix(item);
        return streamFrameRefsWithPrefix(Column.FRAME_BY_ITEM, prefix);
    }

    /**
     * Query frame refs by a participating item and predicate.
     */
    default Stream<FrameRef> framesByItemPredicate(ItemID item, ItemID predicate) {
        Objects.requireNonNull(item, "item");
        Objects.requireNonNull(predicate, "predicate");
        byte[] prefix = Column.FRAME_BY_ITEM.keyPrefix(item, predicate);
        return streamFrameRefsWithPrefix(Column.FRAME_BY_ITEM, prefix);
    }

    /**
     * Query frame refs by predicate.
     *
     * <p>Predicates are indexed as participants in FRAME_BY_ITEM
     * (key: predicate | predicate | bodyHash). A prefix scan on
     * the predicate ID returns all frames where it participates,
     * which includes its self-index entries.
     */
    default Stream<FrameRef> framesByPredicate(ItemID predicate) {
        Objects.requireNonNull(predicate, "predicate");
        byte[] prefix = Column.FRAME_BY_ITEM.keyPrefix(predicate);
        return streamFrameRefsWithPrefix(Column.FRAME_BY_ITEM, prefix);
    }

    /**
     * Query records attesting a specific frame body.
     */
    default Stream<RecordRef> recordsByBody(ContentID bodyHash) {
        Objects.requireNonNull(bodyHash, "bodyHash");
        byte[] prefix = Column.RECORD_BY_BODY.keyPrefix(bodyHash);

        List<RecordRef> results = new ArrayList<>();
        try (var it = store().iterate(Column.RECORD_BY_BODY, prefix)) {
            while (it.hasNext()) {
                var kv = it.next();
                byte[] key = kv.key();
                final int idSize = 34;

                if (key.length < idSize * 2) continue;

                byte[] signerKeyIdBytes = new byte[idSize];
                System.arraycopy(key, idSize, signerKeyIdBytes, 0, idSize);
                ContentID signerKeyId = new ContentID(signerKeyIdBytes);

                byte[] value = kv.value();
                if (value != null && value.length > 0) {
                    results.add(new RecordRef(signerKeyId, new ContentID(value)));
                }
            }
        }
        return results.stream();
    }

    /**
     * Count independent attestations for a frame body.
     */
    default long attestationCount(ContentID bodyHash) {
        return recordsByBody(bodyHash).count();
    }


    /**
     * Extract FrameRefs from fan-out index entries.
     *
     * <p>The body hash is extracted from the last 34 bytes of the key.
     * The storage CID is stored as the value.
     */
    private Stream<FrameRef> streamFrameRefsWithPrefix(Column cf, byte[] prefix) {
        final int idSize = 34;
        List<FrameRef> results = new ArrayList<>();

        try (var it = store().iterate(cf, prefix)) {
            while (it.hasNext()) {
                var kv = it.next();
                byte[] key = kv.key();

                if (key.length < idSize) continue;

                byte[] hashBytes = new byte[idSize];
                System.arraycopy(key, key.length - idSize, hashBytes, 0, idSize);
                ContentID bodyHash = new ContentID(hashBytes);

                byte[] value = kv.value();
                if (value != null && value.length > 0) {
                    ContentID storageCid = new ContentID(value);
                    results.add(new FrameRef(bodyHash, storageCid));
                }
            }
        }
        return results.stream();
    }

    // ==================================================================================
    // Frame Indexing
    // ==================================================================================

    /**
     * Index a frame by its predicate and bindings.
     *
     * <p>Creates fan-out entries in FRAME_BY_ITEM for the predicate (as its own
     * participant) and every ItemID binding, enabling lookup by any participating
     * item including by predicate.
     *
     * @param predicate  the frame's predicate
     * @param bindings   role→target bindings to fan out on
     * @param bodyHash   hash of the frame body (semantic identity)
     * @param storageCid content ID for retrieving the frame bytes
     * @param wtx        write transaction
     */
    default void indexFrame(ItemID predicate,
                            Map<ItemID, BindingTarget> bindings,
                            ContentID bodyHash,
                            ContentID storageCid,
                            WriteTransaction wtx) {
        Objects.requireNonNull(predicate, "predicate");
        Objects.requireNonNull(bodyHash, "bodyHash");
        Objects.requireNonNull(storageCid, "storageCid");
        Objects.requireNonNull(wtx, "wtx");

        byte[] storageCidBytes = storageCid.encodeBinary();

        // Index predicate as its own participant: predicate | predicate | bodyHash
        store().put(Column.FRAME_BY_ITEM,
                Column.FRAME_BY_ITEM.key(predicate, predicate, bodyHash),
                storageCidBytes, wtx);

        // Index each ItemID binding as participant: binding | predicate | bodyHash
        if (bindings != null) {
            for (var entry : bindings.entrySet()) {
                if (entry.getValue() instanceof BindingTarget.IidTarget iidTarget) {
                    store().put(Column.FRAME_BY_ITEM,
                            Column.FRAME_BY_ITEM.key(iidTarget.iid(), predicate, bodyHash),
                            storageCidBytes, wtx);
                }
            }
        }
    }

    /**
     * Index a record attesting a frame body.
     *
     * @param bodyHash    hash of the body being attested
     * @param signerKeyId the signer's key ID
     * @param storageCid  content ID for retrieving the record bytes
     * @param wtx         write transaction
     */
    default void indexRecord(ContentID bodyHash,
                             ContentID signerKeyId,
                             ContentID storageCid,
                             WriteTransaction wtx) {
        Objects.requireNonNull(bodyHash, "bodyHash");
        Objects.requireNonNull(signerKeyId, "signerKeyId");
        Objects.requireNonNull(storageCid, "storageCid");
        Objects.requireNonNull(wtx, "wtx");

        store().put(Column.RECORD_BY_BODY,
                Column.RECORD_BY_BODY.key(bodyHash, signerKeyId),
                storageCid.encodeBinary(), wtx);
    }

    /**
     * Index a frame body.
     *
     * @param body      the frame body to index
     * @param recordCid content ID of the stored RECORD bytes
     * @param wtx       write transaction
     */
    default void indexFrameBody(FrameBody body, ContentID recordCid, WriteTransaction wtx) {
        Objects.requireNonNull(body, "body");
        Objects.requireNonNull(recordCid, "recordCid");
        Objects.requireNonNull(wtx, "wtx");

        ContentID bodyHash = body.hash();
        Map<ItemID, BindingTarget> allBindings = new HashMap<>(body.bindings());
        allBindings.put(body.theme(), BindingTarget.iid(body.theme()));
        indexFrame(body.predicate(), allBindings, bodyHash, recordCid, wtx);
    }

    /**
     * Index an endorsed frame from a manifest's frame table.
     */
    default void indexEndorsedFrame(ItemID ownerIid, FrameEntry entry, WriteTransaction wtx) {
        Objects.requireNonNull(ownerIid, "ownerIid");
        Objects.requireNonNull(entry, "entry");
        Objects.requireNonNull(wtx, "wtx");

        ContentID bodyHash = entry.bodyHash();
        if (bodyHash == null && entry.hasSnapshot()) {
            bodyHash = entry.payload().snapshotCid();
        }
        if (bodyHash == null) return;

        ContentID storageCid = entry.hasSnapshot() ? entry.payload().snapshotCid() : bodyHash;

        ItemID predicate = entry.frameKey().headSememe();
        if (predicate == null) return;

        Map<ItemID, BindingTarget> bindings = new HashMap<>();
        bindings.put(ownerIid, BindingTarget.iid(ownerIid));

        if (entry.isReference() && entry.payload().referenceTarget() != null) {
            bindings.put(entry.payload().referenceTarget(),
                    BindingTarget.iid(entry.payload().referenceTarget()));
        }

        indexFrame(predicate, bindings, bodyHash, storageCid, wtx);
    }

    // ==================================================================================
    // Head Tracking
    // ==================================================================================

    default void setHead(ItemID principal, ItemID item, String channel, ContentID vid, WriteTransaction wtx) {
        Objects.requireNonNull(principal, "principal");
        Objects.requireNonNull(item, "item");
        Objects.requireNonNull(vid, "vid");
        Objects.requireNonNull(wtx, "wtx");
        store().put(Column.HEADS, Column.HEADS.key(principal, item), vid.encodeBinary(), wtx);
    }

    default ContentID getHead(ItemID principal, ItemID item, String channel) {
        Objects.requireNonNull(principal, "principal");
        Objects.requireNonNull(item, "item");
        byte[] vidBytes = store().get(Column.HEADS, Column.HEADS.key(principal, item));
        return vidBytes != null ? new ContentID(vidBytes) : null;
    }

    default Stream<Map.Entry<ItemID, ContentID>> headsFor(ItemID principal) {
        Objects.requireNonNull(principal, "principal");
        byte[] prefix = Column.HEADS.keyPrefix(principal);

        List<Map.Entry<ItemID, ContentID>> results = new ArrayList<>();
        try (var it = store().iterate(Column.HEADS, prefix)) {
            while (it.hasNext()) {
                var kv = it.next();
                byte[] key = kv.key();

                byte[] iidBytes = new byte[34];
                System.arraycopy(key, 34, iidBytes, 0, 34);
                ItemID iid = new ItemID(iidBytes);

                ContentID vid = new ContentID(kv.value());
                results.add(Map.entry(iid, vid));
            }
        }
        return results.stream();
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

    // ==================================================================================
    // Lifecycle & Utilities
    // ==================================================================================

    default boolean isWritable() {
        return true;
    }

    default void exportTo(LibraryIndex target) {
        // Default: no-op
    }

    // ==================================================================================
    // Column Schema
    // ==================================================================================

    @Getter
    enum Column implements ColumnSchema {
        DEFAULT("default", null, null, KeyEncoder.RAW),

        /**
         * Version history: IID | VID → timestamp (8 bytes).
         * Prefix scan by IID returns all versions. Latest by highest timestamp.
         */
        ITEMS("item.index", 34, 10, KeyEncoder.ID, KeyEncoder.ID),

        /**
         * Unified frame index by participant.
         * Key: itemIID | predicate | bodyHash → storageCid bytes.
         * Predicates indexed as their own participant (predicate | predicate | bodyHash).
         */
        FRAME_BY_ITEM("frame.item.index", 34, 10, KeyEncoder.ID, KeyEncoder.ID, KeyEncoder.ID),

        /** Records by body: bodyHash | signerKeyId → storageCid bytes. */
        RECORD_BY_BODY("record.body.index", 34, 10, KeyEncoder.ID, KeyEncoder.ID),

        /** Current version per principal: principal | IID → VID bytes. */
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
