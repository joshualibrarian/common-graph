package dev.everydaythings.graph.library.data;

import dev.everydaythings.graph.datum.Datum;
import dev.everydaythings.graph.encoding.Encoding;
import dev.everydaythings.graph.id.ContentRef;
import dev.everydaythings.graph.id.DatumRef;

import dev.everydaythings.graph.library.bytestore.ColumnSchema;
import dev.everydaythings.graph.library.bytestore.KeyEncoder;
import dev.everydaythings.graph.library.index.RefIndexStore;
import dev.everydaythings.graph.library.index.TokenIndexStore;
import lombok.Getter;

import java.util.Optional;

/**
 * The primary-truth store in a Common Graph node's local storage.
 *
 * <p>Holds {@link Datum}s (Bodies and Records) and content blobs that cannot be
 * derived from anything else. Together with a {@link RefIndexStore} and a
 * {@link TokenIndexStore}, it backs a {@code Library}.
 *
 * <p>Domain-shaped: methods speak in Datums and Datum/Content IDs, not bytes.
 * Implementations may store live Java objects (pure-map backing) or serialize
 * to bytes ({@link DataByteStore} family — SkipList / MapDB / RocksDB).
 *
 * <p>Two parallel APIs:
 * <ul>
 *   <li><b>Datum API</b> — {@link #put(Datum)} / {@link #get(DatumRef)} etc.
 *       The DataStore handles the DatumRef → realized-bytes lookup internally
 *       (byte-backed impls maintain an internal DATUM_INDEX column for this
 *       bridge; pure-map impls hold live references directly).</li>
 *   <li><b>Content-blob API</b> — {@link #putContent(byte[])} /
 *       {@link #getContent(ContentRef)} etc. For arbitrary bytes (audio, video,
 *       images, large binary data) addressed by ContentRef, not Datums.</li>
 * </ul>
 *
 * @see DataByteStore
 * @see RefIndexStore
 * @see TokenIndexStore
 * @see <a href="../../../../../../../../../docs/storage.md">storage.md</a>
 */
public interface DataStore extends AutoCloseable {

    /**
     * The encoder used by this store to serialize Datums to bytes, if any.
     * Byte-backed stores ({@link DataByteStore}) return {@code Optional.of(...)};
     * pure-map stores hold live objects and return {@code Optional.empty()}.
     */
    Optional<Encoding> encoder();

    // ==================================================================================
    // Datum API
    // ==================================================================================

    /** Persist a Datum. Returns the Datum's semantic identity. */
    DatumRef put(Datum datum);

    /** Fetch a Datum by its semantic identity. */
    Optional<Datum> get(DatumRef datumId);

    /** Whether this store has the given Datum. */
    boolean has(DatumRef datumId);

    /** Remove the given Datum. Returns true if anything was removed. */
    boolean delete(DatumRef datumId);

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
         * Internal DatumRef → ContentRef bridge. Lets {@link #get(DatumRef)}
         * resolve a semantic identity to its wire-form realization(s).
         *
         * <p>Key: {@code DatumRef-bytes | ContentRef-bytes}, value: empty. Most
         * Datums map 1→1; multiple realizations arise only when the same
         * semantic Datum is held in multiple wire forms (full + redacted).
         *
         * <p>Internal infrastructure for the byte-backed DataStore, not a
         * query-driven derived index — those live in {@link RefIndexStore}.
         */
        DATUM_INDEX("datum_index", null, 10, KeyEncoder.RAW),

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
