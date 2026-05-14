package dev.everydaythings.graph.library.index;

import dev.everydaythings.graph.datum.Datum;
import dev.everydaythings.graph.id.DatumRef;
import dev.everydaythings.graph.id.ItemRef;

import dev.everydaythings.graph.library.bytestore.ColumnSchema;
import dev.everydaythings.graph.library.bytestore.KeyEncoder;
import dev.everydaythings.graph.library.data.DataStore;
import lombok.Getter;

import java.util.List;

/**
 * Derived-data index store. Owns the query indexes for {@link Datum}s.
 *
 * <p>Domain-shaped: methods speak in Datums and Datum/Item IDs. Implementations
 * may be byte-backed ({@link RefIndexByteStore} family) or in-memory.
 *
 * <p>Indexes are rebuildable by walking primary storage. Nothing here is
 * primary truth.
 *
 * @see DataStore
 * @see RefIndexByteStore
 * @see <a href="../../../../../../../../../docs/storage.md">storage.md</a>
 */
public interface RefIndexStore extends AutoCloseable {

    // ==================================================================================
    // Write API — index/unindex a Datum
    // ==================================================================================

    /** Walk the Datum's bindings and head, write all applicable index entries. */
    void index(Datum datum, DatumRef id);

    /** Reverse of {@link #index} — remove all entries this Datum's indexing wrote. */
    void unindex(Datum datum, DatumRef id);

    // ==================================================================================
    // Query API
    // ==================================================================================

    /** DatumIDs of all records attesting the given body. */
    List<DatumRef> recordsForBody(DatumRef bodyId);

    /** Body-DatumIDs of all archetypal manifests claiming the given item identity. */
    List<DatumRef> manifestsForItem(ItemRef itemIid);

    /** Body-DatumIDs of all archetypal manifests whose head references the given type sememe. */
    List<DatumRef> manifestsForType(ItemRef typeIid);

    /**
     * Body-DatumIDs of all bodies with a simple-key (no qualifiers) binding
     * for {@code role} pointing at {@code target} as a reference.
     */
    List<DatumRef> bodiesByReferenceBinding(ItemRef role, ItemRef target);

    // ==================================================================================
    // Column schema (used by byte-backed backends; in-memory backends ignore)
    // ==================================================================================

    /** Column schema for byte-backed {@link RefIndexByteStore} implementations. */
    @Getter
    enum Column implements ColumnSchema {

        /** Default column required by some backends. Not used directly. */
        DEFAULT("default", null, null, KeyEncoder.RAW),

        /**
         * Forward binding index: key = {@code role-IID | qual-count | qualifiers... |
         * target-bytes | datum-CID}, value = empty. Prefix-scan with
         * {@code role | 0x00 | target} returns body-CIDs with that binding.
         */
        FORWARD_BINDINGS("forward_bindings", null, 10, KeyEncoder.RAW),

        /**
         * Reverse binding index: key = {@code target-bytes | role-IID | qualifiers... |
         * body-CID}. For target-driven queries.
         */
        REVERSE_BINDINGS("reverse_bindings", null, 10, KeyEncoder.RAW),

        /**
         * Type index for archetypal bodies. Key = {@code head-IID | head-VID-or-empty |
         * item-IID | body-CID}, value = empty. Prefix by head-IID returns all instances
         * of the archetype.
         */
        TYPE_INDEX("type_index", null, 10, KeyEncoder.RAW),

        /**
         * Records-by-body head-index: key = {@code body-CID | record-CID}, value = empty.
         */
        RECORDS_BY_BODY("records_by_body", null, 10, KeyEncoder.RAW);

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
