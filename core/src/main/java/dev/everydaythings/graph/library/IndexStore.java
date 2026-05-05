package dev.everydaythings.graph.library;

import dev.everydaythings.graph.library.bytestore.ByteStore;
import dev.everydaythings.graph.library.bytestore.ColumnSchema;
import dev.everydaythings.graph.library.bytestore.KeyEncoder;
import lombok.Getter;

/**
 * Derived-data column-family set for a Common Graph node's local storage.
 *
 * <p>All columns here are rebuildable by walking {@link DataStore.Column#OBJECTS}.
 * Indexes are projections of the binding patterns and head references inside
 * Datums — what role binds what to what, what archetypes manifests use, what
 * bodies records attest — surfaced for query.
 *
 * <p><b>Invariant: indexes are purely associative.</b> Values are empty; the
 * key encodes the entire lookup. Drop the IndexStore entirely and walk
 * {@link DataStore.Column#OBJECTS} to regenerate every index — no information
 * lives only in indexes.
 *
 * <p>Four indexes:
 * <ul>
 *   <li>{@link Column#FORWARD_BINDINGS} — keyed by role-then-target. Answers
 *       "frames whose binding has this role and target."</li>
 *   <li>{@link Column#REVERSE_BINDINGS} — keyed by target-then-role. Answers
 *       "everything pointing at this thing." Subsumes token resolution and
 *       time-range queries via type-discriminated target encoding.</li>
 *   <li>{@link Column#TYPE_INDEX} — keyed on archetypal-body heads. Only for
 *       bodies with an ITEM_ID binding. Answers "all instances of this
 *       archetype."</li>
 *   <li>{@link Column#RECORDS_BY_BODY} — keyed on record heads (body CIDs).
 *       Answers "all attestations for this body."</li>
 * </ul>
 *
 * <p>Index keys use raw byte encoding because the indexing logic composes them
 * from variable-length parts (role IID + variable-count qualifiers + type-discriminated
 * target). The {@link KeyEncoder#RAW} encoder lets the indexer build keys directly.
 *
 * @see DataStore
 * @see Library
 * @see <a href="../../../../../../../../../docs/storage.md">storage.md</a>
 */
public interface IndexStore extends ByteStore<IndexStore.Column> {

    /**
     * Column schema for the index-side column families.
     */
    @Getter
    enum Column implements ColumnSchema {

        /**
         * Default column required by some backends. Not used directly.
         */
        DEFAULT("default", null, null, KeyEncoder.RAW),

        /**
         * Forward binding index: key =
         * {@code role-IID | qual-count | qualifiers... | target-bytes | datum-CID},
         * value = empty.
         *
         * <p>Layout components, all self-delimiting:
         * <ul>
         *   <li>role-IID: leading multihash of the binding's role sememe.</li>
         *   <li>qual-count: 1 byte counting the qualifiers that follow (0 for simple keys).</li>
         *   <li>qualifiers: each one CBOR-encoded ({@code FrameToken.toCbor}); back-to-back.</li>
         *   <li>target-bytes: normalized target encoding. For reference-typed targets (IidTarget,
         *       RefTarget wrapping a simple ItemRef), this is the bare multihash of the referenced
         *       hash — prefix bytes and versions stripped, so different binding-construction
         *       styles index identically.</li>
         *   <li>datum-CID: trailing multihash of the body or record carrying the binding.</li>
         * </ul>
         *
         * <p>Prefix-scan with {@code role | 0x00 | target} returns all body-CIDs whose binding
         * has that role (no qualifiers) and that reference target — the lookup that powers
         * "manifests by item-IID" via the {@code ITEM_ID} role.
         *
         * <p>Phase 1 implementation indexes only reference-typed targets; literal and inline-frame
         * targets are deferred. The layout supports them when the encoding is settled.
         */
        FORWARD_BINDINGS("forward_bindings", null, 10, KeyEncoder.RAW),

        /**
         * Reverse binding index: key = {@code target-bytes | role-IID | qualifiers... | body-CID},
         * value = empty.
         *
         * <p>For target-driven queries. Subsumes token resolution (text targets cluster
         * by their CBOR major-type prefix), time-range queries (timestamp targets cluster
         * under CBOR Tag 1, range-scannable), and reference reverse lookup (IIDs cluster
         * under byte-string major type).
         *
         * <p>The key's leading bytes are CBOR-tag discriminated, so prefix scans by type
         * are clean.
         */
        REVERSE_BINDINGS("reverse_bindings", null, 10, KeyEncoder.RAW),

        /**
         * Type index (head-index for archetypal bodies):
         * key = {@code head-IID | head-VID-or-empty | item-IID | body-CID}, value = empty.
         *
         * <p>Only archetypal bodies (those with an {@code ITEM_ID} binding) are indexed
         * here. Used to enumerate items by kind: "all Documents", "all Chess games".
         *
         * <p>The head-VID slot allows for version-pinned archetype references; an empty
         * sentinel means "any version of the archetype." Prefix-scan with just the head-IID
         * returns all instances regardless of which version of the archetype was used.
         */
        TYPE_INDEX("type_index", null, 10, KeyEncoder.RAW),

        /**
         * Records-by-body head-index: key = {@code body-CID | record-CID}, value = empty.
         *
         * <p>Records are Datums whose head reference IS a body's CID; this index keys them
         * by that head so a fetch of "this body's attestations" is a prefix scan on the
         * body CID. Subsequent ordering by record CID makes iteration deterministic.
         *
         * <p>Used during {@code fetchFrame} / {@code fetchManifest} to assemble the
         * body+records aggregate without a full record scan.
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
