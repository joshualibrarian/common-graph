package dev.everydaythings.graph.datum;

import dev.everydaythings.graph.canonical.Factory;
import dev.everydaythings.graph.encoding.CgCbor;
import dev.everydaythings.graph.id.*;
import com.upokecenter.cbor.CBORObject;
import com.upokecenter.cbor.CBORType;
import java.util.Arrays;
import java.util.Objects;

/**
 * Value type for frame binding targets.
 *
 * <p>A binding target is the value side of a role binding in a frame.
 * Implementations:
 * <ul>
 *   <li>{@link RefTarget} — unified reference (Tag 6) to any HashID (ItemRef or ContentRef)</li>
 *   <li>{@link IidTarget} — legacy reference to another Item (encodes as bare ByteString)</li>
 *   <li>{@link FrameTarget} — inline nested frame (Tag 23) for expression trees</li>
 *   <li>{@link RedactedTarget} — Merkle redaction marker (Tag 11) wrapping the
 *       hash of an elided subtree. Short-circuits during structural Merkle hashing.</li>
 *   <li>Raw Java values ({@link String}, {@link Long}, {@link Boolean},
 *       {@code byte[]}, {@link java.time.Instant}, etc.) — inline typed value</li>
 * </ul>
 */
public interface BindingTarget {

    /**
     * Decode a binding target from CBOR. Returns raw types (no wrapper):
     *
     * <ul>
     *   <li>Tag {@link CgCbor#TAG_REF}      → {@link HashID}               — bare reference</li>
     *   <li>Tag {@link CgCbor#TAG_REDACTED} → {@link RedactedTarget}       — Merkle elision marker</li>
     *   <li>Tag {@link CgCbor#TAG_BODY}     → {@link Body}                 — inline nested body</li>
     *   <li>Tag 1                            → {@link java.time.Instant}   — epoch-millis time</li>
     *   <li>Bare {@code TextString}          → {@link String}</li>
     *   <li>Bare {@code Integer}             → {@link Long}</li>
     *   <li>Bare {@code Boolean}             → {@link Boolean}</li>
     *   <li>Bare {@code ByteString}          → {@code byte[]}</li>
     * </ul>
     */
    @Factory
    static Object fromCborTree(CBORObject node) {
        if (node == null || node.isNull()) return null;
        if (node.isTagged()) {
            int tag = node.getMostOuterTag().ToInt32Checked();
            if (tag == CgCbor.TAG_REF) return HashID.fromCborTree(node);
            if (tag == CgCbor.TAG_REDACTED) return RedactedTarget.fromCborTree(node);
            if (tag == CgCbor.TAG_BODY) return Body.fromCborTree(node);
            if (tag == 1) return java.time.Instant.ofEpochMilli(node.UntagOne().AsInt64Value());
        }
        return switch (node.getType()) {
            case TextString -> node.AsString();
            case Integer    -> node.AsInt64Value();
            case Boolean    -> node.AsBoolean();
            case ByteString -> node.GetByteString();
            default -> throw new IllegalArgumentException(
                    "Cannot decode binding target from CBOR type: " + node.getType());
        };
    }

    /**
     * Convenience factory for item references in bindings — produces a
     * {@link RefTarget} wrapping an {@link ItemRef}.
     */
    static RefTarget iid(ItemRef iid) { return new RefTarget(ItemRef.of(iid)); }

    /**
     * Convenience factory wrapping a {@link DatumRef} as a {@link DatumRef} —
     * common for ENDORSES bindings and version-id references.
     */
    static RefTarget ref(DatumRef datumId) { return new RefTarget(DatumRef.of(datumId)); }

    /**
     * Convenience factory wrapping a {@link ContentRef} as a {@link ContentRef}.
     */
    static RefTarget ref(ContentRef cid) { return new RefTarget(ContentRef.of(cid)); }

    /** Convenience factory wrapping any {@link HashID} (caller picks the variant). */
    static RefTarget ref(HashID reference) { return new RefTarget(reference); }

    /** Convenience factory for inline nested datums (Tag 12). */
    static FrameTarget frame(Body body) { return new FrameTarget(body); }

    /**
     * HashID-valued binding target — wraps a {@link HashID} (the sealed
     * {@link ItemRef}/{@link ContentRef}/{@link DatumRef} sum type).
     *
     * <p>Encoded as CG-CBOR Tag 6 wrapping the HashID's binary payload.
     *
     * <p>The convenience accessors return the underlying target of the
     * appropriate variant, or {@code null} when the variant doesn't match —
     * callers gate on {@link #isCompound()} or directly inspect
     * {@link #asReference()} when they need disambiguation.
     */
    final class RefTarget implements BindingTarget {
        private final HashID reference;

        public RefTarget(HashID reference) {
            this.reference = Objects.requireNonNull(reference, "reference");
        }

        /** The underlying HashID (use {@code instanceof} to disambiguate the variant). */
        public HashID asReference() { return reference; }

        /**
         * For an {@link ItemRef}, the target {@link ItemRef}; {@code null} otherwise.
         */
        public ItemRef asItemId() {
            return reference instanceof ItemRef ir ? ir.iid() : null;
        }

        /**
         * For a {@link ContentRef}, the target {@link ContentRef}; {@code null} otherwise.
         */
        public ContentRef asCid() {
            return reference instanceof ContentRef cr ? cr.cid() : null;
        }

        /**
         * For a {@link DatumRef}, the body's {@link DatumRef}; {@code null} otherwise.
         */
        public DatumRef asDatumId() {
            return reference instanceof DatumRef fr ? fr.bodyId() : null;
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

        @Factory
        public static RefTarget fromCborTree(CBORObject node) {
            if (node == null || node.isNull()) return null;
            return new RefTarget(HashID.fromCborTree(node));
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
     * DatumRef is invariant across redactions because the Merkle root is.
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
