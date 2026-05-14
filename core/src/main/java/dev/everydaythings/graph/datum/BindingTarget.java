package dev.everydaythings.graph.datum;

import dev.everydaythings.graph.canonical.CgTag;

import dev.everydaythings.graph.canonical.Scope;

import dev.everydaythings.graph.canonical.Canonical;
import dev.everydaythings.graph.id.*;
import dev.everydaythings.graph.canonical.Factory;

import com.upokecenter.cbor.CBORObject;
import com.upokecenter.cbor.CBORType;
import dev.everydaythings.graph.value.Literal;

import java.util.Arrays;
import java.util.Objects;

/**
 * Value type for frame binding targets.
 *
 * <p>A binding target is the value side of a role binding in a frame.
 * Implementations:
 * <ul>
 *   <li>{@link RefTarget} — unified reference (Tag 6) to any HashID (ItemID or ContentID)</li>
 *   <li>{@link IidTarget} — legacy reference to another Item (encodes as bare ByteString)</li>
 *   <li>{@link FrameTarget} — inline nested frame (Tag 23) for expression trees</li>
 *   <li>{@link RedactedTarget} — Merkle redaction marker (Tag 11) wrapping the
 *       hash of an elided subtree. Short-circuits during structural Merkle hashing.</li>
 *   <li>{@link Literal} — inline typed value</li>
 * </ul>
 */
public interface BindingTarget extends Canonical {

    /**
     * Decode a BindingTarget from CBOR.
     *
     * <p>Dispatch:
     * <ul>
     *   <li>Tag 6 (REF)     → {@link RefTarget}      — reference</li>
     *   <li>Tag 11 (REDACTED) → {@link RedactedTarget} — Merkle elision</li>
     *   <li>Tag 12 (DATUM)  → {@link FrameTarget}    — inline nested datum</li>
     *   <li>Tag 1           → {@link Literal}         — instant (epoch millis)</li>
     *   <li>Bare TextString / Integer / Boolean / ByteString → {@link Literal}</li>
     * </ul>
     */
    @Factory
    static BindingTarget fromCborTree(CBORObject node) {
        if (node == null || node.isNull()) return null;
        if (node.isTagged()) {
            int tag = node.getMostOuterTag().ToInt32Checked();
            if (tag == CgTag.REF) return RefTarget.fromCborTree(node);
            if (tag == CgTag.REDACTED) return RedactedTarget.fromCborTree(node);
            if (tag == CgTag.DATUM) return FrameTarget.fromCborTree(node);
            if (tag == 1) return Literal.fromCborTree(node);
        }
        return switch (node.getType()) {
            case TextString, Integer, Boolean, ByteString
                    -> Literal.fromCborTree(node);
            default -> throw new IllegalArgumentException(
                    "Cannot decode BindingTarget from CBOR type: " + node.getType());
        };
    }

    /**
     * Convenience factory for item references in bindings — produces a
     * {@link RefTarget} wrapping an {@link ItemRef}.
     */
    static RefTarget iid(ItemID iid) { return new RefTarget(ItemRef.of(iid)); }

    /**
     * Convenience factory wrapping a {@link DatumID} as a {@link FrameRef} —
     * common for ENDORSES bindings and version-id references.
     */
    static RefTarget ref(DatumID datumId) { return new RefTarget(FrameRef.of(datumId)); }

    /**
     * Convenience factory wrapping a {@link ContentID} as a {@link ContentRef}.
     */
    static RefTarget ref(ContentID cid) { return new RefTarget(ContentRef.of(cid)); }

    /** Convenience factory wrapping any {@link Reference} (caller picks the variant). */
    static RefTarget ref(Reference reference) { return new RefTarget(reference); }

    /** Convenience factory for inline nested datums (Tag 12). */
    static FrameTarget frame(Body body) { return new FrameTarget(body); }

    /**
     * Reference-valued binding target — wraps a {@link Reference} (the sealed
     * {@link ItemRef}/{@link ContentRef}/{@link FrameRef} sum type).
     *
     * <p>Encoded as CG-CBOR Tag 6 wrapping the Reference's binary payload.
     *
     * <p>The convenience accessors return the underlying target of the
     * appropriate variant, or {@code null} when the variant doesn't match —
     * callers gate on {@link #isCompound()} or directly inspect
     * {@link #asReference()} when they need disambiguation.
     */
    final class RefTarget implements BindingTarget {
        private final Reference reference;

        public RefTarget(Reference reference) {
            this.reference = Objects.requireNonNull(reference, "reference");
        }

        /** The underlying Reference (use {@code instanceof} to disambiguate the variant). */
        public Reference asReference() { return reference; }

        /**
         * For an {@link ItemRef}, the target {@link ItemID}; {@code null} otherwise.
         */
        public ItemID asItemId() {
            return reference instanceof ItemRef ir ? ir.iid() : null;
        }

        /**
         * For a {@link ContentRef}, the target {@link ContentID}; {@code null} otherwise.
         */
        public ContentID asCid() {
            return reference instanceof ContentRef cr ? cr.cid() : null;
        }

        /**
         * For a {@link FrameRef}, the body's {@link DatumID}; {@code null} otherwise.
         */
        public DatumID asDatumId() {
            return reference instanceof FrameRef fr ? fr.bodyId() : null;
        }

        /**
         * Whether this is anything other than a bare unpinned {@link ItemRef}.
         *
         * <p>True for ItemRefs pinned to a version, ContentRefs, and FrameRefs.
         * False for the common case: a bare item reference with no version/key/portion.
         */
        public boolean isCompound() {
            if (reference instanceof ItemRef ir) return ir.isPinned();
            return true;
        }

        @Override
        public CBORObject toCborTree(Scope scope) {
            return reference.toCborTree(scope);
        }

        @Factory
        public static RefTarget fromCborTree(CBORObject node) {
            if (node == null || node.isNull()) return null;
            return new RefTarget(Reference.fromCborTree(node));
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof RefTarget other)) return false;
            return Objects.equals(reference, other.reference);
        }

        @Override
        public int hashCode() {
            return Objects.hash(reference);
        }

        @Override
        public String toString() {
            return "Ref(" + reference.encodeText() + ")";
        }
    }

    /**
     * Inline nested datum — enables expression trees and compositional frames.
     *
     * <p>Encodes as CG-CBOR Tag 12 (DATUM) wrapping the inner body's CBOR. Same
     * tag as outer-level Body/Record encoding — the wire shape of an inline
     * datum and an outer datum is identical, and decoders dispatch by context.
     */
    final class FrameTarget implements BindingTarget {
        private final Body body;

        public FrameTarget(Body body) {
            this.body = Objects.requireNonNull(body, "body");
        }

        public Body body() { return body; }

        @Override
        public CBORObject toCborTree(Scope scope) {
            return CBORObject.FromObjectAndTag(body.toCborTree(scope), CgTag.DATUM);
        }

        @Factory
        public static FrameTarget fromCborTree(CBORObject node) {
            if (node == null || node.isNull()) return null;
            CBORObject inner = node.isTagged() ? node.UntagOne() : node;
            // TODO: Body.fromCborTree once wired up; for now FrameTarget decode is
            // a no-op stub. Inline-datum targets are written but not yet round-tripped.
            return null;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof FrameTarget other)) return false;
            return Objects.equals(body, other.body);
        }

        @Override
        public int hashCode() {
            return Objects.hash(body);
        }

        @Override
        public String toString() {
            return "Frame(" + body + ")";
        }
    }

    /**
     * Merkle elision marker — wraps the structural Merkle hash of an elided
     * subtree, encoded as CG-CBOR Tag 11.
     *
     * <p>A RedactedTarget replaces what would otherwise be the actual target of
     * a binding (or appears anywhere a binding target could) when the producer
     * is sharing a redacted view. The wrapped hash IS the structural Merkle hash
     * the original target contributed; using it directly in the parent's hash
     * computation produces the same parent hash as if the original target were
     * present. This is what makes signatures survive redaction: the signed
     * DatumID is invariant across redactions because the Merkle root is.
     *
     * <p>The marker carries only the raw multihash bytes. It carries no type
     * information, no predicate, no size — by design. Redaction context (who,
     * when, why, scope) belongs in separate attestation records (REDACT frames)
     * that reference the redacted Datum, not in the marker.
     *
     * <p>Wire format: {@code Tag(11, byte-string<multihash-bytes>)}.
     */
    final class RedactedTarget implements BindingTarget {
        private final byte[] wrappedHash;

        /**
         * Construct a RedactedTarget wrapping the given raw multihash bytes.
         *
         * <p>The bytes are the multihash representation of the structural hash
         * that the redacted subtree would have contributed. Callers must supply
         * the correct hash; the marker takes the bytes on faith.
         */
        public RedactedTarget(byte[] wrappedHash) {
            this.wrappedHash = Objects.requireNonNull(wrappedHash, "wrappedHash").clone();
        }

        /** The wrapped structural Merkle hash (raw multihash bytes). */
        public byte[] wrappedHash() {
            return wrappedHash.clone();
        }

        @Override
        public CBORObject toCborTree(Scope scope) {
            return CBORObject.FromObjectAndTag(
                    CBORObject.FromByteArray(wrappedHash),
                    CgTag.REDACTED);
        }

        @Factory
        public static RedactedTarget fromCborTree(CBORObject node) {
            if (node == null || node.isNull()) return null;
            CBORObject inner = node.isTagged() ? node.UntagOne() : node;
            if (inner.getType() != CBORType.ByteString) {
                throw new IllegalArgumentException(
                        "RedactedTarget payload must be a byte string (multihash); got " + inner.getType());
            }
            return new RedactedTarget(inner.GetByteString());
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof RedactedTarget other)) return false;
            return Arrays.equals(wrappedHash, other.wrappedHash);
        }

        @Override
        public int hashCode() {
            return Arrays.hashCode(wrappedHash);
        }

        @Override
        public String toString() {
            // Display the hash compactly for diagnostics.
            StringBuilder sb = new StringBuilder("Redacted(");
            int show = Math.min(8, wrappedHash.length);
            for (int i = 0; i < show; i++) {
                sb.append(String.format("%02x", wrappedHash[i]));
            }
            if (wrappedHash.length > show) sb.append("…");
            sb.append(")");
            return sb.toString();
        }
    }
}
