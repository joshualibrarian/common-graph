package dev.everydaythings.graph.datum;

import dev.everydaythings.graph.identity.VarSig;
import dev.everydaythings.graph.id.DatumRef;
import java.util.List;
import java.util.Objects;

/**
 * A Datum that attests a body. Has a signature slot (varsig-formatted bytes).
 *
 * <p>The head is a {@link DatumRef} pointing at the body this record attests.
 * The simple whole-frame form ({@code #<body-CID>}) is used; record heads do not
 * drill into bindings or portions.
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

    public Record(DatumRef head, List<? extends DatumNode> entries, byte[] signature) {
        super(head, entries);
        Objects.requireNonNull(signature, "signature");
        if (signature.length == 0) {
            throw new IllegalArgumentException("Record signature must not be empty");
        }
        this.signature = signature.clone();
    }

    /**
     * Create a Record with the given head, entries, and signature.
     */
    public static Record of(DatumRef head, List<? extends DatumNode> entries, byte[] signature) {
        return new Record(head, entries, signature);
    }

    /**
     * Create a Record with the given head, entries, and a {@link VarSig}.
     *
     * <p>Convenience for the common case where the signature is being constructed
     * from a typed VarSig rather than raw bytes.
     */
    public static Record of(DatumRef head, List<? extends DatumNode> entries, VarSig signature) {
        Objects.requireNonNull(signature, "signature");
        return new Record(head, entries, signature.encoded());
    }

    /** The head as a {@link DatumRef} (typed accessor; head() returns HashID). */
    public DatumRef headRef() {
        return (DatumRef) head;
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
