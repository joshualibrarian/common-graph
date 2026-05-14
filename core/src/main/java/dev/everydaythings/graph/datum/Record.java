package dev.everydaythings.graph.datum;

import dev.everydaythings.graph.canonical.CgTag;

import dev.everydaythings.graph.canonical.Scope;

import com.upokecenter.cbor.CBORObject;
import com.upokecenter.cbor.CBORType;
import dev.everydaythings.graph.canonical.Canonical;
import dev.everydaythings.graph.identity.VarSig;
import dev.everydaythings.graph.canonical.Factory;
import dev.everydaythings.graph.id.FrameRef;
import dev.everydaythings.graph.id.Reference;

import java.util.List;
import java.util.Objects;

/**
 * A Datum that attests a body. Has a signature slot (varsig-formatted bytes).
 *
 * <p>The head is a {@link FrameRef} pointing at the body this record attests.
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

    public Record(FrameRef head, List<Binding> bindings, byte[] signature) {
        super(head, bindings);
        Objects.requireNonNull(signature, "signature");
        if (signature.length == 0) {
            throw new IllegalArgumentException("Record signature must not be empty");
        }
        this.signature = signature.clone();
        bindId();
    }

    /**
     * Create a Record with the given head, bindings, and signature.
     */
    public static Record of(FrameRef head, List<Binding> bindings, byte[] signature) {
        return new Record(head, bindings, signature);
    }

    /**
     * Create a Record with the given head, bindings, and a {@link VarSig}.
     *
     * <p>Convenience for the common case where the signature is being constructed
     * from a typed VarSig rather than raw bytes.
     */
    public static Record of(FrameRef head, List<Binding> bindings, VarSig signature) {
        Objects.requireNonNull(signature, "signature");
        return new Record(head, bindings, signature.encoded());
    }

    /** The head as a {@link FrameRef} (typed accessor; head() returns Reference). */
    public FrameRef headRef() {
        return (FrameRef) head;
    }

    /** The raw varsig-encoded signature bytes (defensive copy). */
    public byte[] signature() {
        return signature.clone();
    }

    /** The signature decoded as a {@link VarSig}. */
    public VarSig varsig() {
        return VarSig.decode(signature);
    }

    @Override
    public CBORObject toCborTree(Scope scope) {
        CBORObject arr = CBORObject.NewArray();
        arr.Add(head.toCborTree(scope));
        arr.Add(encodeBindingsArray(scope));
        arr.Add(CBORObject.FromByteArray(signature));
        return CBORObject.FromObjectAndTag(arr, CgTag.DATUM);
    }

    // merkleDigest is inherited from Datum — the encoding-agnostic walker
    // includes the signature element when walking a Record, so the inherited
    // implementation produces the right distinct-from-Body hash.

    /**
     * Decode a Record from its CBOR form: {@code Tag-12 [Tag-6(head), [bindings], signature]}.
     *
     * <p>Tolerates an untagged 3-element array as a transitional fallback.
     *
     * @throws IllegalArgumentException if the inner array length is not 3 or
     *         the head is not a FrameRef.
     */
    @Factory
    public static Record fromCborTree(CBORObject node) {
        Objects.requireNonNull(node, "node");
        if (node.isTagged() && node.HasMostOuterTag(CgTag.DATUM)) {
            node = node.UntagOne();
        }
        if (node.getType() != CBORType.Array || node.size() != 3) {
            throw new IllegalArgumentException(
                    "Record requires a 3-element CBOR array, got " + node.getType()
                            + (node.getType() == CBORType.Array ? " of size " + node.size() : ""));
        }
        Reference headRef = Reference.fromCborTree(node.get(0));
        if (!(headRef instanceof FrameRef frameRef)) {
            throw new IllegalArgumentException(
                    "Record head must be a FrameRef (#-prefix), got " + headRef.variant());
        }
        if (frameRef.key().isPresent() || frameRef.portion().isPresent()) {
            throw new IllegalArgumentException(
                    "Record head must be a whole-frame FrameRef (#<CID>); drill-down not allowed");
        }
        CBORObject bindingsArr = node.get(1);
        if (bindingsArr.getType() != CBORType.Array) {
            throw new IllegalArgumentException(
                    "Record bindings must be a CBOR array, got " + bindingsArr.getType());
        }
        List<Binding> bindings = decodeBindings(bindingsArr);
        CBORObject sigNode = node.get(2);
        if (sigNode.getType() != CBORType.ByteString) {
            throw new IllegalArgumentException(
                    "Record signature must be a CBOR byte string, got " + sigNode.getType());
        }
        return new Record(frameRef, bindings, sigNode.GetByteString());
    }

    private static List<Binding> decodeBindings(CBORObject arr) {
        List<Binding> result = new java.util.ArrayList<>(arr.size());
        for (CBORObject element : arr.getValues()) {
            result.add(Binding.fromCborTree(element));
        }
        return List.copyOf(result);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Record other)) return false;
        return head.equals(other.head)
                && bindings.equals(other.bindings)
                && java.util.Arrays.equals(signature, other.signature);
    }

    @Override
    public int hashCode() {
        return Objects.hash(head, bindings, java.util.Arrays.hashCode(signature));
    }

    @Override
    public String toString() {
        return "Record[" + head + ", " + bindings.size() + " bindings, "
                + signature.length + "-byte sig]";
    }
}
