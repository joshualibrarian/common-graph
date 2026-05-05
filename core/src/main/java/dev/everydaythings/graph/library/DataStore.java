package dev.everydaythings.graph.library;

import dev.everydaythings.graph.library.bytestore.ByteStore;
import dev.everydaythings.graph.library.bytestore.ColumnSchema;
import dev.everydaythings.graph.library.bytestore.KeyEncoder;
import lombok.Getter;

/**
 * Primary-truth column-family set for a Common Graph node's local storage.
 *
 * <p>Holds the data that cannot be derived from anything else:
 * <ul>
 *   <li>{@link Column#OBJECTS} — content-addressed Datum bodies, records, and content
 *       blobs, all keyed by their CID.</li>
 *   <li>{@link Column#ITEM_DIRECTORY} — directory of item locations external to the
 *       local store (mounted WorkingTreeStores, peer caches, mentioned-but-not-yet-fetched).</li>
 * </ul>
 *
 * <p>Together with {@link IndexStore} (which is rebuildable from the OBJECTS column),
 * the {@code DataStore} forms the storage backing a {@link LibraryOld}. Indexes are kept
 * conceptually separate so they can be dropped and rebuilt without losing primary data.
 *
 * <p>This is a marker-style interface that pins {@link ByteStore}'s column-schema type
 * parameter to the {@link Column} enum. Concrete implementations (RocksDB-backed,
 * MapDB-backed, in-memory) provide the storage substrate; the Library composes them
 * with an {@link IndexStore} of the same backend kind.
 *
 * @see IndexStore
 * @see LibraryOld
 * @see <a href="../../../../../../../../../docs/storage.md">storage.md</a>
 */
public interface DataStore extends ByteStore<DataStore.Column> {

    /**
     * Column schema for the data-side column families.
     */
    @Getter
    enum Column implements ColumnSchema {

        /**
         * Default column required by some backends (e.g., RocksDB always opens the
         * default CF). Not used directly for reads/writes.
         */
        DEFAULT("default", null, null, KeyEncoder.RAW),

        /**
         * Unified content-addressed object store.
         *
         * <p>Holds all Datums (Bodies and Records) and content blobs as bytes-by-CID.
         * Type discrimination is by content (2-element CBOR array = Body, 3-element =
         * Record, anything else = content blob), not by separate columns. Retrieval
         * is context-driven — callers know what they're fetching because they followed
         * a typed reference to obtain the CID.
         *
         * <p>Key: CID (multihash bytes) → value: encoded bytes.
         */
        OBJECTS("objects", null, 10, KeyEncoder.ID),

        /**
         * Item directory: locations of items NOT in our local OBJECTS store.
         *
         * <p>Tracks where to find items we know about but don't have locally —
         * mounted WorkingTreeStores, trusted peers, mentioned-but-never-fetched
         * references. The Librarian uses these as routing hints when the local
         * store doesn't have a requested item.
         *
         * <p>Key: ItemID → value: encoded directory entry (locations, last-known
         * VID, last-seen timestamp).
         */
        ITEM_DIRECTORY("item_directory", null, 10, KeyEncoder.ID);

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
