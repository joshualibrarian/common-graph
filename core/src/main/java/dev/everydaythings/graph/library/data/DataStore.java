package dev.everydaythings.graph.library.data;

import dev.everydaythings.graph.encoding.Encoding;
import dev.everydaythings.graph.ref.ContentRef;

import dev.everydaythings.graph.library.bytestore.ColumnSchema;
import dev.everydaythings.graph.library.bytestore.KeyEncoder;
import dev.everydaythings.graph.library.index.RefIndexStore;
import dev.everydaythings.graph.library.index.TokenIndexStore;
import lombok.Getter;

import java.util.Optional;

/**
 * The primary-truth content store in a Common Graph node's local storage.
 *
 * <p>Holds content blobs addressed by {@link ContentRef}.  Together with a
 * {@link RefIndexStore} (which owns the {@code DatumRef → ContentRef} mapping
 * along with the other query indexes) and a {@link TokenIndexStore}, it backs
 * a {@code Library}.  Library does the {@code Datum ↔ bytes} encode/decode
 * composition — DataStore itself is byte-only.
 *
 * <p>Implementations may keep bytes in-memory (pure-map backing) or persist
 * them to disk ({@link DataByteStore} family — SkipList / MapDB / RocksDB),
 * or to a materialized {@code .item/} directory
 * ({@code MaterializedDataStore}).
 *
 * @see DataByteStore
 * @see RefIndexStore
 * @see TokenIndexStore
 * @see <a href="../../../../../../../../../docs/storage.md">storage.md</a>
 */
public interface DataStore extends AutoCloseable {

    /**
     * The encoder used to serialize Datums to bytes for storage in this
     * store.  Library reads this once at composition and uses it for
     * encode/decode at the Library boundary.  Every {@link DataStore}
     * implementation declares an encoder; there is no "encoder-less" mode.
     */
    Optional<Encoding> encoder();

    // ==================================================================================
    // Content-blob API
    // ==================================================================================

    /** Persist arbitrary content bytes, returning the CID computed from them. */
    ContentRef putContent(byte[] bytes);

    /** Fetch raw bytes by CID. */
    Optional<byte[]> getContent(ContentRef cid);

    /** Whether this store has bytes for the given CID. */
    boolean hasContent(ContentRef cid);

    /** Remove the content at the given CID. Returns true if anything was removed. */
    boolean deleteContent(ContentRef cid);

    // ==================================================================================
    // Column schema (used by byte-backed backends; pure-map backends ignore)
    // ==================================================================================

    /**
     * Column schema used by byte-backed {@link DataStore} implementations
     * ({@link DataByteStore}). Pure-map implementations do not consult this.
     */
    @Getter
    enum Column implements ColumnSchema {

        /**
         * Default column required by some backends (e.g., RocksDB always opens
         * the default CF). Not used directly for reads/writes.
         */
        DEFAULT("default", null, null, KeyEncoder.RAW),

        /**
         * Unified content-addressed object store — Datums and content blobs as
         * bytes-by-CID. Type discrimination is by content (2-element CBOR
         * array = Body, 3-element = Record, otherwise = content blob).
         */
        OBJECTS("objects", null, 10, KeyEncoder.ID),

        /**
         * Directory of items NOT in our local OBJECTS column — locations of
         * known-but-not-locally-held items (mounted stores, peers, references).
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
