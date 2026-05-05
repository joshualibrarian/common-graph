package dev.everydaythings.graph.frame;

import com.upokecenter.cbor.CBORObject;
import com.upokecenter.cbor.CBORType;
import dev.everydaythings.graph.Canonical;
import dev.everydaythings.graph.crypt.VarSig;
import dev.everydaythings.graph.item.Factory;
import dev.everydaythings.graph.item.id.FrameRef;
import dev.everydaythings.graph.item.id.Reference;

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

    /**
     * Encode the body portion of this record (head + bindings only, no signature).
     *
     * <p>This is the byte sequence over which the signature is computed.
     */
    public byte[] encodeBodyForSigning(Scope scope) {
        CBORObject arr = CBORObject.NewArray();
        arr.Add(head.toCborTree(scope));
        arr.Add(encodeBindingsArray(scope));
        return arr.EncodeToBytes();
    }

    @Override
    public CBORObject toCborTree(Scope scope) {
        CBORObject arr = CBORObject.NewArray();
        arr.Add(head.toCborTree(scope));
        arr.Add(encodeBindingsArray(scope));
        arr.Add(CBORObject.FromByteArray(signature));
        return arr;
    }

    /**
     * Decode a Record from its CBOR form: {@code [Tag-6(head), [bindings], signature]}.
     *
     * @throws IllegalArgumentException if the array length is not 3 or the head is
     *         not a FrameRef.
     */
    @Factory
    public static Record fromCborTree(CBORObject node) {
        Objects.requireNonNull(node, "node");
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
