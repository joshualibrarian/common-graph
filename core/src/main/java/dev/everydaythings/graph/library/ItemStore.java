package dev.everydaythings.graph.library;

import dev.everydaythings.graph.Canonical;
import dev.everydaythings.graph.Hash;
import dev.everydaythings.graph.item.Item;
import dev.everydaythings.graph.item.Literal;
import dev.everydaythings.graph.item.component.Type;
import dev.everydaythings.graph.item.component.ComponentEntry;
import dev.everydaythings.graph.item.id.*;
import dev.everydaythings.graph.item.Manifest;
import dev.everydaythings.graph.item.relation.Relation;
import dev.everydaythings.graph.language.Sememe;
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
 * Core storage interface for Items, manifests, relations, and content.
 *
 * <p>This interface has two conceptual APIs:
 * <ul>
 *   <li><b>Consumer API</b>: High-level methods using domain objects (Manifest, Relation, Item)</li>
 *   <li><b>Implementor API</b>: Low-level byte operations (persist/retrieve) for concrete stores</li>
 * </ul>
 *
 * <p>The consumer API uses unified naming: {@code manifest}, {@code relation}, {@code content}, {@code item}.
 * Each has getter (by ID) and setter (by object) overloads.
 *
 * <p>The implementor API uses {@code persistX} for storage and {@code retrieve} (overloaded) for retrieval.
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
     * Get a manifest by item ID and version ID.
     *
     * @param iid The item ID
     * @param vid The version ID
     * @return The manifest, or empty if not found
     */
    default Optional<Manifest> manifest(ItemID iid, VersionID vid) {
        byte[] bytes = retrieveManifest(iid, vid);
        if (bytes == null) return Optional.empty();
        return Optional.of(Manifest.decode(bytes));
    }

    /**
     * Check if a manifest exists for the given item and version.
     *
     * <p>This is more efficient than {@code manifest(iid, vid).isPresent()}
     * as it only checks for key existence without reading the full manifest.
     *
     * @param iid The item ID
     * @param vid The version ID
     * @return true if a manifest exists
     */
    default boolean hasManifest(ItemID iid, VersionID vid) {
        Objects.requireNonNull(iid, "iid");
        Objects.requireNonNull(vid, "vid");
        return store().db(Column.MANIFEST).key(iid, vid).exists();
    }

    /**
     * Store a manifest.
     *
     * @param m The manifest to store
     * @return The version ID (hash of the body)
     */
    default VersionID manifest(Manifest m) {
        byte[] record = m.encodeBinary(Canonical.Scope.RECORD);
        var vid = new VersionID[1];
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
        runInWriteTransaction(tx -> cid[0] = persistRelation(record, tx));
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
     * Persist a manifest record. Computes VID from the record and stores by IID|VID.
     *
     * @param iid    The item ID
     * @param record The manifest RECORD bytes (includes signature)
     * @param tx     Write transaction
     * @return The version ID (hash of BODY extracted from record)
     */
    default VersionID persistManifest(ItemID iid, byte[] record, WriteTransaction tx) {
        Objects.requireNonNull(iid, "iid");
        Objects.requireNonNull(record, "record");
        Objects.requireNonNull(tx, "tx");

        // Decode to compute VID (hash of BODY)
        Manifest m = Manifest.decode(record);
        byte[] body = m.encodeBinary(Canonical.Scope.BODY);
        VersionID vid = new VersionID(Hash.DEFAULT.digest(body), Hash.DEFAULT);

        byte[] key = Column.MANIFEST.key(iid, vid);
        store().put(Column.MANIFEST, key, record, tx);

        return vid;
    }

    /**
     * Persist a relation record. Content-addressed by RECORD CID.
     *
     * @param record The relation RECORD bytes (includes signature)
     * @param tx     Write transaction
     * @return The content ID of the RECORD bytes
     */
    default ContentID persistRelation(byte[] record, WriteTransaction tx) {
        Objects.requireNonNull(record, "record");
        Objects.requireNonNull(tx, "tx");

        ContentID cid = ContentID.of(record);
        byte[] key = Column.RELATION.key(cid);

        // Content-addressed dedup
        if (!store().exists(Column.RELATION, key)) {
            store().put(Column.RELATION, key, record, tx);
        }

        return cid;
    }

    /**
     * Persist content data. Computes CID from the data and stores by CID.
     *
     * @param data The content bytes
     * @param tx   Write transaction
     * @return The content ID (hash of the data)
     */
    default ContentID persistContent(byte[] data, WriteTransaction tx) {
        Objects.requireNonNull(data, "data");
        Objects.requireNonNull(tx, "tx");

        ContentID cid = new ContentID(Hash.DEFAULT.digest(data), Hash.DEFAULT);
        byte[] key = Column.PAYLOAD.key(cid);

        // Only store if not already present (content-addressed dedup)
        if (!store().exists(Column.PAYLOAD, key)) {
            store().put(Column.PAYLOAD, key, data, tx);
        }

        return cid;
    }

    // --- Retrieve (get raw bytes) ---

    /**
     * Retrieve raw manifest bytes by IID and VID.
     *
     * @param iid The item ID
     * @param vid The version ID
     * @return The manifest record bytes, or null if not found
     */
    default byte[] retrieveManifest(ItemID iid, VersionID vid) {
        Objects.requireNonNull(iid, "iid");
        Objects.requireNonNull(vid, "vid");

        byte[] key = Column.MANIFEST.key(iid, vid);
        return store().get(Column.MANIFEST, key);
    }

    /**
     * Retrieve raw relation bytes by record CID.
     *
     * @param recordCid The CID of the RECORD bytes
     * @return The relation record bytes, or null if not found
     */
    default byte[] retrieveRelation(ContentID recordCid) {
        Objects.requireNonNull(recordCid, "recordCid");

        byte[] key = Column.RELATION.key(recordCid);
        return store().get(Column.RELATION, key);
    }

    /**
     * Retrieve raw content bytes by CID.
     *
     * @param cid The content ID
     * @return The content bytes, or null if not found
     */
    default byte[] retrieveContent(ContentID cid) {
        Objects.requireNonNull(cid, "cid");

        byte[] key = Column.PAYLOAD.key(cid);
        return store().get(Column.PAYLOAD, key);
    }

    // --- Chunk ---

    /**
     * Persist a chunk of large content.
     *
     * @param cid   The parent content ID
     * @param index The chunk index
     * @param data  The chunk bytes
     * @param tx    Write transaction
     */
    default void persistChunk(ContentID cid, long index, byte[] data, WriteTransaction tx) {
        Objects.requireNonNull(cid, "cid");
        Objects.requireNonNull(data, "data");
        Objects.requireNonNull(tx, "tx");

        byte[] key = Column.CHUNK.key(cid, index);
        store().put(Column.CHUNK, key, data, tx);
    }

    /**
     * Retrieve a chunk of large content.
     *
     * @param cid   The parent content ID
     * @param index The chunk index
     * @return The chunk bytes, or null if not found
     */
    default byte[] retrieveChunk(ContentID cid, long index) {
        Objects.requireNonNull(cid, "cid");

        byte[] key = Column.CHUNK.key(cid, index);
        return store().get(Column.CHUNK, key);
    }

    // --- Bundle ---

    /**
     * Persist a bundle (packaged collection of items).
     *
     * @param data The bundle bytes
     * @param tx   Write transaction
     * @return The bundle ID
     */
    default ContentID persistBundle(byte[] data, WriteTransaction tx) {
        Objects.requireNonNull(data, "data");
        Objects.requireNonNull(tx, "tx");

        ContentID bid = new ContentID(Hash.DEFAULT.digest(data), Hash.DEFAULT);
        byte[] key = Column.BUNDLE.key(bid);
        store().put(Column.BUNDLE, key, data, tx);

        return bid;
    }

    /**
     * Retrieve a bundle by ID.
     *
     * @param bid The bundle ID
     * @return The bundle bytes, or null if not found
     */
    default byte[] retrieveBundle(ContentID bid) {
        Objects.requireNonNull(bid, "bid");

        byte[] key = Column.BUNDLE.key(bid);
        return store().get(Column.BUNDLE, key);
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
                .filter(rel -> rel.predicate().equals(Sememe.IMPLEMENTED_BY.iid()))
                .filter(rel -> typeId.equals(rel.bindingId(ItemID.fromString("cg.role:theme"))))
                .findFirst()
                .map(rel -> {
                    Relation.Target target = rel.binding(ItemID.fromString("cg.role:target"));
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
    default void saveHeadComponents(List<ComponentEntry> entries, WriteTransaction tx) {
        // Default: no-op
    }

    /**
     * Load component metadata for the item's current head.
     *
     * @return List of component entries, or empty if not supported
     */
    default List<ComponentEntry> loadHeadComponents() {
        return List.of();
    }

    /**
     * Get locally stored component content by handle.
     *
     * @param handle The component handle
     * @return The component bytes, or empty if not found
     */
    default Optional<byte[]> getLocalContent(HandleID handle) {
        return Optional.empty();
    }

    // ==================================================================================
    // Consumer API - Streaming (decoded domain objects)
    // ==================================================================================

    /**
     * Stream manifests, optionally filtered by item.
     *
     * @param iid The item ID to filter by, or null for all manifests
     * @return Stream of decoded manifests
     */
    default Stream<Manifest> manifests(ItemID iid) {
        return StreamSupport.stream(iterateManifests(iid).spliterator(), false)
                .map(Manifest::decode);
    }

    /**
     * Stream all relations.
     *
     * @return Stream of decoded relations
     */
    default Stream<Relation> relations() {
        return StreamSupport.stream(iterateRelations().spliterator(), false)
                .map(Relation::decode);
    }

    /**
     * Stream all content blocks.
     *
     * @return Stream of content bytes
     */
    default Stream<byte[]> contents() {
        return StreamSupport.stream(iterateContent().spliterator(), false);
    }

    // ==================================================================================
    // Implementor API - Iteration (raw bytes)
    // ==================================================================================

    /**
     * Iterate manifest records, optionally filtered by item.
     *
     * @param iid The item ID to filter by, or null for all manifests
     * @return Iterable of manifest record bytes
     */
    default Iterable<byte[]> iterateManifests(ItemID iid) {
        List<byte[]> results = new ArrayList<>();
        byte[] prefix = iid != null ? Column.MANIFEST.keyPrefix(iid) : new byte[0];

        try (var it = store().iterate(Column.MANIFEST, prefix)) {
            while (it.hasNext()) {
                results.add(it.next().value());
            }
        }

        return results;
    }

    /**
     * Iterate all relation records.
     *
     * @return Iterable of relation record bytes
     */
    default Iterable<byte[]> iterateRelations() {
        List<byte[]> results = new ArrayList<>();

        try (var it = store().iterate(Column.RELATION, new byte[0])) {
            while (it.hasNext()) {
                results.add(it.next().value());
            }
        }

        return results;
    }

    /**
     * Iterate all content blocks.
     *
     * @return Iterable of content bytes
     */
    default Iterable<byte[]> iterateContent() {
        List<byte[]> results = new ArrayList<>();

        try (var it = store().iterate(Column.PAYLOAD, new byte[0])) {
            while (it.hasNext()) {
                results.add(it.next().value());
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
     * <p>This enum defines the storage layout used by all ItemStore backends
     * (RocksDB, MapDB, etc.). Each column has:
     * <ul>
     *   <li>A unique name</li>
     *   <li>Optional prefix length for optimized iteration</li>
     *   <li>Key composition (sequence of encoders)</li>
     *   <li>Optional bloom filter bits (used by RocksDB, ignored by MapDB)</li>
     * </ul>
     */
    @Getter
    enum Column implements ColumnSchema {
        /**
         * Default column (required by some backends).
         */
        DEFAULT("default", null, null, KeyEncoder.RAW),

        /**
         * Manifest blocks (version records).
         * Key: IID|VID → manifest record bytes
         * Enables prefix scan by IID to find all versions of an item.
         */
        MANIFEST("manifest", 34, 10, KeyEncoder.ID, KeyEncoder.ID),

        /**
         * Relation blocks (content-addressed frames).
         * Key: RECORD CID → relation record bytes
         * All queries go through LibraryIndex fan-outs → RECORD CID → load bytes.
         */
        RELATION("relation", null, 10, KeyEncoder.ID),

        /**
         * Payload blocks (content blobs).
         * Key: CID → content bytes
         */
        PAYLOAD("payload", null, 10, KeyEncoder.ID),

        /**
         * Chunk blocks (parts of large content).
         * Key: CID|index → chunk bytes
         */
        CHUNK("chunk", null, 10, KeyEncoder.ID, KeyEncoder.U64),

        /**
         * Bundle blocks (packaged collections).
         * Key: BID|index → bundle bytes
         */
        BUNDLE("bundle", null, 10, KeyEncoder.ID, KeyEncoder.U64);

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
