package dev.everydaythings.graph.library;

import dev.everydaythings.graph.Canonical;
import dev.everydaythings.graph.Hash;
import dev.everydaythings.graph.crypt.AtRestEncryption;
import dev.everydaythings.graph.item.Item;
import dev.everydaythings.graph.item.Literal;
import dev.everydaythings.graph.item.component.BindingTarget;
import dev.everydaythings.graph.item.component.Type;
import dev.everydaythings.graph.item.component.FrameEntry;
import dev.everydaythings.graph.item.id.*;
import dev.everydaythings.graph.item.Manifest;
import dev.everydaythings.graph.item.relation.Relation;
import dev.everydaythings.graph.language.VerbSememe;
import dev.everydaythings.graph.library.bytestore.ColumnSchema;
import dev.everydaythings.graph.library.bytestore.ByteStore;
import dev.everydaythings.graph.library.bytestore.KeyEncoder;
import lombok.Getter;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/**
 * Core storage interface — a single content-addressed object store.
 *
 * <p>All data (manifests, frame bodies, frame records, content blobs) lives
 * in one OBJECTS column keyed by content hash. The store doesn't interpret
 * what's inside — callers know what they're fetching because they followed
 * a typed reference to get the CID.
 *
 * <p>This interface has two conceptual APIs:
 * <ul>
 *   <li><b>Consumer API</b>: High-level methods using domain objects (Manifest, Relation, Item)</li>
 *   <li><b>Implementor API</b>: Low-level byte operations (persist/retrieve) for concrete stores</li>
 * </ul>
 *
 * <p>ItemStore extends {@link Service} for lifecycle management and UI presentation.
 * Implementations typically also implement {@link dev.everydaythings.graph.library.bytestore.ByteStore}
 * which provides default Service implementations based on {@code isOpen()} state.
 */
public interface ItemStore extends Service {

    // ==================================================================================
    // Consumer API - High-level operations with domain objects
    // ==================================================================================

    // --- Item ---

    /**
     * Get an item by ID.
     *
     * @param iid The item ID
     * @return The item, or empty if not found
     */
    default Optional<Item> item(ItemID iid) {
        // TODO: implement via manifest lookup and hydration
        return Optional.empty();
    }

    // --- Manifest ---

    /**
     * Get a manifest by version ID.
     *
     * <p>Fetches from OBJECTS by VID. The iid parameter is accepted for
     * backward compatibility but ignored for ByteStore-backed stores
     * (manifests are keyed solely by VID in the unified object store).
     *
     * @param iid The item ID (ignored for OBJECTS lookup, used by WorkingTreeStore)
     * @param vid The version ID (hash of manifest body)
     * @return The manifest, or empty if not found
     */
    default Optional<Manifest> manifest(ItemID iid, ContentID vid) {
        byte[] bytes = retrieveManifest(iid, vid);
        if (bytes == null) return Optional.empty();
        return Optional.of(Manifest.decode(bytes));
    }

    /**
     * Check if a manifest exists for the given version.
     *
     * @param iid The item ID (ignored for OBJECTS lookup)
     * @param vid The version ID
     * @return true if a manifest exists in OBJECTS
     */
    default boolean hasManifest(ItemID iid, ContentID vid) {
        Objects.requireNonNull(vid, "vid");
        byte[] key = Column.OBJECTS.key(vid);
        return store().exists(Column.OBJECTS, key);
    }

    /**
     * Store a manifest.
     *
     * @param m The manifest to store
     * @return The version ID (hash of the body)
     */
    default ContentID manifest(Manifest m) {
        byte[] record = m.encodeBinary(Canonical.Scope.RECORD);
        var vid = new ContentID[1];
        runInWriteTransaction(tx -> vid[0] = persistManifest(m.iid(), record, tx));
        return vid[0];
    }

    // --- Relation ---

    /**
     * Get a relation by its record CID (content-addressed).
     *
     * @param recordCid The CID of the RECORD bytes
     * @return The relation, or empty if not found
     */
    default Optional<Relation> relation(ContentID recordCid) {
        byte[] bytes = retrieveRelation(recordCid);
        if (bytes == null) return Optional.empty();
        return Optional.of(Relation.decode(bytes));
    }

    /**
     * Store a relation. Content-addressed by RECORD CID.
     *
     * @param r The relation to store
     * @return The content ID of the stored RECORD bytes
     */
    default ContentID relation(Relation r) {
        byte[] record = r.encodeBinary(Canonical.Scope.RECORD);
        var cid = new ContentID[1];
        runInWriteTransaction(tx -> cid[0] = persistContent(record, tx));
        return cid[0];
    }

    // --- Content ---

    /**
     * Get content by content ID.
     *
     * @param cid The content ID
     * @return The content bytes, or empty if not found
     */
    default Optional<byte[]> content(ContentID cid) {
        return Optional.ofNullable(retrieveContent(cid));
    }

    /**
     * Store content.
     *
     * @param data The content bytes
     * @return The content ID (hash of the data)
     */
    default ContentID content(byte[] data) {
        var cid = new ContentID[1];
        runInWriteTransaction(tx -> cid[0] = persistContent(data, tx));
        return cid[0];
    }

    // ==================================================================================
    // ByteStore Access - Private helper for default implementations
    // ==================================================================================

    /**
     * Access the underlying ByteStore.
     *
     * <p>This cast is safe because all ItemStore implementations must also
     * implement a ByteStore (e.g., RocksStore or MapDBStore) with Column schema.
     *
     * @return this as a ByteStore
     */
    private ByteStore<Column> store() {
        return (ByteStore<Column>) this;
    }

    // ==================================================================================
    // Implementor API - Default implementations using ByteStore methods
    // ==================================================================================

    // --- Persist (store raw bytes) ---

    /**
     * Persist a manifest record. Computes VID and stores in OBJECTS by VID.
     *
     * <p>The VID (hash of the BODY portion) is used as the key. The full
     * record bytes (including signature) are the value. This means
     * manifests are keyed by body hash, not by record hash — the VID
     * is the meaningful identity of the version.
     *
     * @param iid    The item ID (used by WorkingTreeStore, ignored by ByteStore default)
     * @param record The manifest RECORD bytes (includes signature)
     * @param tx     Write transaction
     * @return The version ID (hash of BODY extracted from record)
     */
    default ContentID persistManifest(ItemID iid, byte[] record, WriteTransaction tx) {
        Objects.requireNonNull(record, "record");
        Objects.requireNonNull(tx, "tx");

        // Decode to compute VID (hash of BODY)
        Manifest m = Manifest.decode(record);
        byte[] body = m.encodeBinary(Canonical.Scope.BODY);
        ContentID vid = new ContentID(Hash.DEFAULT.digest(body), Hash.DEFAULT);

        byte[] key = Column.OBJECTS.key(vid);
        if (!store().exists(Column.OBJECTS, key)) {
            store().put(Column.OBJECTS, key, encryptValue(record), tx);
        }

        return vid;
    }

    /**
     * Persist content data. Content-addressed: computes CID and stores in OBJECTS.
     *
     * @param data The content bytes
     * @param tx   Write transaction
     * @return The content ID (hash of the data)
     */
    default ContentID persistContent(byte[] data, WriteTransaction tx) {
        Objects.requireNonNull(data, "data");
        Objects.requireNonNull(tx, "tx");

        ContentID cid = new ContentID(Hash.DEFAULT.digest(data), Hash.DEFAULT);
        byte[] key = Column.OBJECTS.key(cid);

        // Only store if not already present (content-addressed dedup)
        if (!store().exists(Column.OBJECTS, key)) {
            store().put(Column.OBJECTS, key, encryptValue(data), tx);
        }

        return cid;
    }

    // --- Retrieve (get raw bytes) ---

    /**
     * Retrieve raw manifest bytes by VID from OBJECTS.
     *
     * @param iid The item ID (ignored — manifests keyed by VID in OBJECTS)
     * @param vid The version ID
     * @return The manifest record bytes, or null if not found
     */
    default byte[] retrieveManifest(ItemID iid, ContentID vid) {
        Objects.requireNonNull(vid, "vid");

        byte[] key = Column.OBJECTS.key(vid);
        return decryptValue(store().get(Column.OBJECTS, key));
    }

    /**
     * Retrieve raw relation bytes by record CID from OBJECTS.
     *
     * @param recordCid The CID of the RECORD bytes
     * @return The relation record bytes, or null if not found
     */
    default byte[] retrieveRelation(ContentID recordCid) {
        return retrieveContent(recordCid);
    }

    /**
     * Retrieve raw content bytes by CID from OBJECTS.
     *
     * @param cid The content ID
     * @return The content bytes, or null if not found
     */
    default byte[] retrieveContent(ContentID cid) {
        Objects.requireNonNull(cid, "cid");

        byte[] key = Column.OBJECTS.key(cid);
        return decryptValue(store().get(Column.OBJECTS, key));
    }

    // ==================================================================================
    // Implementor API - Transactions
    // ==================================================================================

    /**
     * Begin a write transaction.
     */
    default WriteTransaction beginWriteTransaction() {
        return store().beginTransaction();
    }

    /**
     * Run work in a write transaction (convenience method).
     */
    default void runInWriteTransaction(Consumer<WriteTransaction> work) {
        Objects.requireNonNull(work, "work");
        try (WriteTransaction tx = beginWriteTransaction()) {
            work.accept(tx);
            tx.commit();
        }
    }

    // ==================================================================================
    // Writability
    // ==================================================================================

    /**
     * Check if this store is writable.
     *
     * @return true if this store supports write operations
     */
    default boolean isWritable() {
        return true;
    }

    // ==================================================================================
    // At-Rest Encryption
    // ==================================================================================

    /**
     * Get the at-rest encryption instance, if configured.
     *
     * <p>When non-null, all values stored in the OBJECTS column are encrypted
     * before writing and decrypted after reading. Keys remain in the clear
     * for prefix scanning.
     *
     * @return The encryption instance, or null if not encrypted
     */
    default AtRestEncryption atRestEncryption() {
        return null;
    }

    /**
     * Enable at-rest encryption on this store.
     *
     * <p>Implementations must override to accept the encryption instance.
     *
     * @param encryption The at-rest encryption to use
     */
    default void enableEncryption(AtRestEncryption encryption) {
        throw new UnsupportedOperationException(
                getClass().getSimpleName() + " does not support at-rest encryption");
    }

    private byte[] encryptValue(byte[] value) {
        AtRestEncryption enc = atRestEncryption();
        return enc != null ? enc.encrypt(value) : value;
    }

    private byte[] decryptValue(byte[] value) {
        AtRestEncryption enc = atRestEncryption();
        return enc != null ? enc.decrypt(value) : value;
    }

    // ==================================================================================
    // Type Resolution API
    // ==================================================================================

    /**
     * Find the implementing Java class for a type ID.
     *
     * <p>Queries stored relations directly for IMPLEMENTED_BY relations
     * where the type ID is the subject.
     *
     * @param typeId The type's ItemID
     * @return The implementing Java class, or empty if not found
     */
    default Optional<Class<?>> findImplementation(ItemID typeId) {
        return relations()
                .filter(rel -> rel.predicate().equals(VerbSememe.ImplementedBy.SEED.iid()))
                .filter(rel -> typeId.equals(rel.bindingId(ItemID.fromString("cg.role:theme"))))
                .findFirst()
                .map(rel -> {
                    BindingTarget target = rel.binding(ItemID.fromString("cg.role:target"));
                    if (target instanceof Literal lit) {
                        return lit.asJavaClass();
                    }
                    return null;
                });
    }

    /**
     * Find the implementing component class for a type ID.
     *
     * <p>Returns any class annotated with {@code @Type}, regardless of base class.
     *
     * @param typeId The component type's ItemID
     * @return The implementing component class, or empty if not found
     */
    default Optional<Class<?>> findComponentImplementation(ItemID typeId) {
        return findImplementation(typeId)
                .filter(c -> c.isAnnotationPresent(Type.class));
    }

    /**
     * Find the implementing Item class for a type ID.
     *
     * @param typeId The item type's ItemID
     * @return The implementing Item class, or empty if not found
     */
    @SuppressWarnings("unchecked")
    default Optional<Class<? extends Item>> findItemImplementation(ItemID typeId) {
        return findImplementation(typeId)
                .filter(Item.class::isAssignableFrom)
                .map(c -> (Class<? extends Item>) c);
    }

    // ==================================================================================
    // Materialized Item API - For path-based items with working trees
    // ==================================================================================

    /**
     * Get the root path for this store.
     *
     * @return The root path, or null if not a materialized store
     */
    default Path root() {
        return null;
    }

    /**
     * Save component metadata for the item's current head.
     *
     * @param entries Component entries to save
     * @param tx      Write transaction
     */
    default void saveHeadComponents(List<FrameEntry> entries, WriteTransaction tx) {
        // Default: no-op
    }

    /**
     * Load component metadata for the item's current head.
     *
     * @return List of component entries, or empty if not supported
     */
    default List<FrameEntry> loadHeadComponents() {
        return List.of();
    }

    /**
     * Get locally stored component content by frame key.
     *
     * @param key The component frame key
     * @return The component bytes, or empty if not found
     */
    default Optional<byte[]> getLocalContent(FrameKey key) {
        return Optional.empty();
    }

    // ==================================================================================
    // Consumer API - Streaming (decoded domain objects)
    // ==================================================================================

    /**
     * Stream manifests by trial-decoding objects.
     *
     * <p>With the unified OBJECTS store, manifests are not in a separate column.
     * This iterates all objects and trial-decodes each as a Manifest, skipping
     * non-manifest content.
     *
     * @param iid The item ID to filter by, or null for all manifests
     * @return Stream of decoded manifests
     */
    default Stream<Manifest> manifests(ItemID iid) {
        return StreamSupport.stream(iterateObjects().spliterator(), false)
                .flatMap(bytes -> {
                    try {
                        Manifest m = Manifest.decode(bytes);
                        if (m == null || m.iid() == null) return Stream.empty();
                        if (iid != null && !iid.equals(m.iid())) return Stream.empty();
                        return Stream.of(m);
                    } catch (Exception e) {
                        return Stream.empty();
                    }
                });
    }

    /**
     * Stream all relations by trial-decoding objects.
     *
     * <p>Iterates OBJECTS and attempts to decode each as a Relation,
     * silently skipping non-relation content.
     *
     * @return Stream of decoded relations
     * @deprecated Relations are frames. Use frame index queries instead.
     */
    @Deprecated
    default Stream<Relation> relations() {
        return StreamSupport.stream(iterateObjects().spliterator(), false)
                .flatMap(bytes -> {
                    try {
                        Relation r = Relation.decode(bytes);
                        if (r == null || r.predicate() == null) return Stream.empty();
                        return Stream.of(r);
                    } catch (Exception e) {
                        return Stream.empty();
                    }
                });
    }

    /**
     * Stream all content blocks from OBJECTS.
     *
     * @return Stream of content bytes
     */
    default Stream<byte[]> contents() {
        return StreamSupport.stream(iterateObjects().spliterator(), false);
    }

    // ==================================================================================
    // Implementor API - Iteration (raw bytes)
    // ==================================================================================

    /**
     * Iterate all objects in the store.
     *
     * @return Iterable of object bytes
     */
    default Iterable<byte[]> iterateObjects() {
        List<byte[]> results = new ArrayList<>();

        try (var it = store().iterate(Column.OBJECTS, new byte[0])) {
            while (it.hasNext()) {
                results.add(decryptValue(it.next().value()));
            }
        }

        return results;
    }

    // ==================================================================================
    // Service Implementation
    // ==================================================================================

    // Note: Service lifecycle methods (status, start, stop, close) are provided by
    // ByteStore implementations. ItemStore relies on the underlying ByteStore for
    // proper lifecycle management.

    /**
     * Get the storage path for file-backed stores.
     *
     * <p>Overrides {@link Service#path()} to use the store's root path.
     */
    @Override
    default Optional<Path> path() {
        Path root = root();
        return root != null ? Optional.of(root) : Optional.empty();
    }

    // ==================================================================================
    // Shared Column Schema
    // ==================================================================================

    /**
     * Column schema for ItemStore implementations.
     *
     * <p>Unified object store: all content-addressed data (manifests, frame bodies,
     * frame records, content blobs) lives in a single OBJECTS column keyed by
     * content hash (CID or VID).
     */
    @Getter
    enum Column implements ColumnSchema {
        /**
         * Default column (required by some backends).
         */
        DEFAULT("default", null, null, KeyEncoder.RAW),

        /**
         * Unified object store. All content-addressed data:
         * manifests (keyed by VID), frame bodies, frame records,
         * content blobs, chunks, bundles (keyed by CID).
         * Key: hash → bytes
         */
        OBJECTS("objects", null, 10, KeyEncoder.ID);

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
