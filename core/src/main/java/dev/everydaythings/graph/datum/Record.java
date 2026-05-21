package dev.everydaythings.graph.datum;

import dev.everydaythings.graph.cryptography.VarSig;
import dev.everydaythings.graph.ref.ContentRef;
import dev.everydaythings.graph.ref.DatumRef;
import dev.everydaythings.graph.ref.HashID;
import java.util.List;
import java.util.Objects;

/**
 * A Datum that attests a body. Has a signature slot (varsig-formatted bytes).
 *
 * <p>The head is a {@link HashID} pointing at what this record attests.
 * Two head types are valid:
 * <ul>
 *   <li>{@link DatumRef} — the body's semantic Merkle identity.  Used for
 *       records that commit to a body's MEANING (Created, Verified, ...).
 *       Invariant under re-encoding and redaction: the same signature
 *       still validates against a redacted form of the same semantic
 *       body.</li>
 *   <li>{@link ContentRef} — the body's exact byte identity.  Used for
 *       records that commit to specific stored bytes (Materialized).  Not
 *       invariant under re-encoding: a re-encoded body produces a fresh
 *       MATERIALIZED record over the new ContentRef.  The intended trade:
 *       physical storage commitment instead of semantic invariance.</li>
 * </ul>
 *
 * <p>The signature is varsig-formatted: a varint codec prefix identifying the
 * signature algorithm, followed by the raw signature bytes. The signature is
 * computed over the encoded record body — that is, the 2-element array
 * {@code [head, [bindings]]} excluding the signature slot itself.
 *
 * <p>Per-record metadata (signer, claimed time, role, AAD-equivalents) lives in
 * the bindings, not in the signature slot. The signature slot carries only
 * cryptographic bytes.
 *
 * <p>CBOR encoding: 3-element array {@code [Tag-6(head), [bindings], signature-bytes]}.
 */
public final class Record extends Datum {

    private final byte[] signature;

    public Record(HashID head, List<? extends DatumNode> entries, byte[] signature) {
        super(head, entries);
        if (!(head instanceof DatumRef) && !(head instanceof ContentRef)) {
            throw new IllegalArgumentException(
                    "Record head must be a DatumRef or ContentRef, got "
                            + head.getClass().getSimpleName());
        }
        Objects.requireNonNull(signature, "signature");
        if (signature.length == 0) {
            throw new IllegalArgumentException("Record signature must not be empty");
        }
        this.signature = signature.clone();
    }

    /**
     * Create a Record with the given head, entries, and signature.
     */
    public static Record of(HashID head, List<? extends DatumNode> entries, byte[] signature) {
        return new Record(head, entries, signature);
    }

    /**
     * Create a Record with the given head, entries, and a {@link VarSig}.
     *
     * <p>Convenience for the common case where the signature is being constructed
     * from a typed VarSig rather than raw bytes.
     */
    public static Record of(HashID head, List<? extends DatumNode> entries, VarSig signature) {
        Objects.requireNonNull(signature, "signature");
        return new Record(head, entries, signature.encoded());
    }

    /**
     * The head as a {@link DatumRef} (typed accessor for the semantic-identity
     * case).  Throws if this record's head is a {@link ContentRef} instead.
     */
    public DatumRef headRef() {
        if (!(head instanceof DatumRef d)) {
            throw new IllegalStateException(
                    "Record head is a " + head.getClass().getSimpleName()
                            + ", not a DatumRef; use head() or contentRefHead()");
        }
        return d;
    }

    /**
     * The head as a {@link ContentRef} (typed accessor for the byte-identity
     * case, e.g. Materialized records).  Throws if this record's head is a
     * {@link DatumRef} instead.
     */
    public ContentRef contentRefHead() {
        if (!(head instanceof ContentRef c)) {
            throw new IllegalStateException(
                    "Record head is a " + head.getClass().getSimpleName()
                            + ", not a ContentRef; use head() or headRef()");
        }
        return c;
    }

    /** The raw varsig-encoded signature bytes (defensive copy). */
    public byte[] signature() {
        return signature.clone();
    }

    /** The signature decoded as a {@link VarSig}. */
    public VarSig varsig() {
        return VarSig.decode(signature);
    }

    // merkleDigest is inherited from Datum — the encoding-agnostic walker
    // includes the signature element when walking a Record, so the inherited
    // implementation produces the right distinct-from-Body hash.

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Record other)) return false;
        return head.equals(other.head)
                && entries.equals(other.entries)
                && java.util.Arrays.equals(signature, other.signature);
    }

    @Override
    public int hashCode() {
        return Objects.hash(head, entries, java.util.Arrays.hashCode(signature));
    }

    @Override
    public String toString() {
        return "Record[" + head + ", " + entries.size() + " entries, "
                + signature.length + "-byte sig]";
    }
}
