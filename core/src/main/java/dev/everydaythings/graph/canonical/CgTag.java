package dev.everydaythings.graph.canonical;

import dev.everydaythings.graph.canonical.CgTag;

/**
 * CG-CBOR tag constants.
 *
 * <p>CG-CBOR uses CBOR tags 6-22 (the 1-byte encoding range) for type
 * discrimination. These tags enable polymorphic decoding without a separate
 * "kind" discriminator field.
 *
 * <p>These are <em>wire-format</em> constants specific to CG-CBOR encoding —
 * they don't belong to the canonical Merkle walk, which is encoder-agnostic.
 * Other codecs (Cap'n Proto, Protobuf, future formats) use their own
 * discriminators and don't reference these values.
 *
 * @see <a href="docs/cg-cbor.md">CG-CBOR Specification</a>
 */
public final class CgTag {

    private CgTag() {}

    /** No tag (default). */
    public static final int NONE = -1;

    /** Tag 6: Reference (Tag-6 byte string carrying a {@code Reference} payload). */
    public static final int REF = 6;

    /** Tag 8: Signed envelope. Payload is {@code [body, signature-block]}. */
    public static final int SIG = 8;

    /**
     * Tag 9: Quantity (magnitude + unit). Payload is {@code [magnitude, unit-iid]}.
     *
     * <p>The magnitude can be a CBOR integer, Tag 4 decimal, or array
     * {@code [num, den]} for rational. The unit-iid is a 34-byte ItemID
     * referencing the unit definition.
     */
    public static final int QTY = 9;

    /** Tag 10: Encrypted envelope. Reserved. */
    public static final int ENCRYPTED = 10;

    /**
     * Tag 11: Redaction marker. Payload is a multihash byte string — the hash
     * of an elided subtree of a structurally-hashed Datum.
     *
     * <p>Used for Merkle elision: replacing a subtree of a Datum with a hash
     * preserves the parent Datum's structural Merkle hash (DatumID) because
     * the parent hashes over child hashes, not child bytes. Verifiers
     * encountering a Tag 11 marker during the structural hash walk use the
     * wrapped hash directly without recursing.
     *
     * <p>Distinct from encryption (Tag 10): redaction is lossy without the
     * original; encryption is recoverable with the key. Both compose.
     */
    public static final int REDACTED = 11;

    /**
     * Tag 12: Datum (head + bindings, optionally with signature).
     *
     * <p>Universal marker for "this CBOR value is a Datum." Used both at the
     * top of an encoded blob (Body / Record) and for inline nested datums
     * inside a binding's target — same wire shape either way.
     */
    public static final int DATUM = 12;

    // Tags 13-22: protocol-layer tags previously used by the OLD peer/session
    // protocols. Vacated; Parley uses codec-self-describing bytes rather than
    // tag-discriminated message types.
}
