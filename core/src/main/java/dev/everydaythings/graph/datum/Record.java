package dev.everydaythings.graph.datum;

import dev.everydaythings.graph.cryptography.VarSig;
import dev.everydaythings.graph.ref.ContentRef;
import dev.everydaythings.graph.ref.DatumRef;
import dev.everydaythings.graph.ref.HashID;
import java.util.List;
import java.util.Objects;

/**
 * A Datum that attests a body. Has a signature slot (varsig-formatted bytes),
 * which may be empty to indicate an <i>unsigned</i> record.
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
 * <p><b>Unsigned records.</b>  A zero-length signature slot means the record
 * carries no cryptographic attestation.  This is the shape used for bootstrap
 * seed data — the shared vocabulary that ships embedded in the librarian
 * binary, materialized before any signer identity exists.  Trust derives from
 * code provenance, not cryptographic verification.  Distributed seed data
 * published by the real graph carries real signatures from the publishing
 * party; the same Record structure simply gains a signature.  Callers that
 * verify signatures must check {@link #isSigned()} first; {@link #varsig()}
 * throws on unsigned records.
 *
 * <p>Per-record metadata (signer, claimed time, role, AAD-equivalents) lives in
 * the bindings, not in the signature slot. The signature slot carries only
 * cryptographic bytes.
 *
 * <p>CBOR encoding: 3-element array {@code [Tag-6(head), [bindings], signature-bytes]}.
 * Unsigned records encode the same way with a zero-length byte string.
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
        this.signature = signature.clone();
    }

    /**
     * Create a signed (or, if {@code signature} is empty, unsigned) Record.
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
     * Create an <i>unsigned</i> Record.  Used by the bootstrap path for shared
     * vocabulary materialization, where the data is trusted by code provenance
     * rather than cryptographic attestation.  See the class doc for the full
     * unsigned-records rationale.
     */
    public static Record unsigned(HashID head, List<? extends DatumNode> entries) {
        return new Record(head, entries, new byte[0]);
    }

    /**
     * True iff this record carries a non-empty signature.  Verification code
     * must gate on this before calling {@link #varsig()}.
     */
    public boolean isSigned() {
        return signature.length > 0;
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

    /**
     * The raw varsig-encoded signature bytes (defensive copy).  Zero-length
     * for unsigned records; see {@link #isSigned()}.
     */
    public byte[] signature() {
        return signature.clone();
    }

    /**
     * The signature decoded as a {@link VarSig}.  Throws if this record is
     * unsigned; callers must gate on {@link #isSigned()}.
     */
    public VarSig varsig() {
        if (signature.length == 0) {
            throw new IllegalStateException(
                    "Cannot read varsig from an unsigned Record (head=" + head
                            + "); check isSigned() first");
        }
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
                + (signature.length == 0 ? "unsigned" : signature.length + "-byte sig") + "]";
    }
}
