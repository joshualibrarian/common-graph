package dev.everydaythings.graph.datum;

import dev.everydaythings.graph.encoding.Canonical;
import dev.everydaythings.graph.item.Factory;

import com.upokecenter.cbor.CBORObject;
import com.upokecenter.cbor.CBORType;
import dev.everydaythings.graph.frame.FrameBodyOld;
import dev.everydaythings.graph.item.Literal;
import dev.everydaythings.graph.item.id.ContentID;
import dev.everydaythings.graph.item.id.HashID;
import dev.everydaythings.graph.item.id.ItemID;
import dev.everydaythings.graph.item.id.Ref;

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
            if (tag == Canonical.CgTag.REF) return RefTarget.fromCborTree(node);
            if (tag == Canonical.CgTag.REDACTED) return RedactedTarget.fromCborTree(node);
            if (tag == Canonical.CgTag.DATUM) return FrameTarget.fromCborTree(node);
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
     * {@link RefTarget} (Tag 6 encoding) wrapping the given ItemID.
     *
     * <p>Item references are always Tag-6 encoded now; the legacy bare-ByteString
     * IidTarget encoding has been retired. Callers can keep using the same
     * factory; the returned type is just a different concrete BindingTarget.
     */
    static RefTarget iid(ItemID iid) { return new RefTarget(Ref.of(iid)); }

    /** Convenience factory for unified references (Tag 6). */
    static RefTarget ref(HashID ref) { return new RefTarget(Ref.of(new ItemID(ref.encodeBinary()))); }

    /** Convenience factory for compound references (Tag 6 with frame key). */
    static RefTarget ref(Ref ref) { return new RefTarget(ref); }

    /** Convenience factory for inline nested frames (Tag 23). */
    static FrameTarget frame(FrameBodyOld body) { return new FrameTarget(body); }

    /**
     * Unified reference target using CG-CBOR Tag 6 (REF).
     *
     * <p>Holds a full {@link Ref} which supports both simple item references
     * (bare ItemID) and compound references (item + frame key path).
     * The wire format uses the Ref binary encoding inside Tag 6.
     *
     * <p>For simple references, {@link #asItemId()} returns the target.
     * For compound references, {@link #asRef()} returns the full Ref
     * including the frame key path.
     */
    final class RefTarget implements BindingTarget {
        private final Ref ref;

        public RefTarget(Ref ref) {
            this.ref = Objects.requireNonNull(ref, "ref");
        }

        /** The full Ref (may include frame key for compound references). */
        public Ref asRef() { return ref; }

        /** The target ItemID (ignoring any frame key). */
        public ItemID asItemId() { return ref.target(); }

        /** The target as a ContentID. */
        public ContentID asCid() {
            return new ContentID(ref.target().encodeBinary());
        }

        /** The target as a DatumID (semantic identity of a referenced Datum). */
        public dev.everydaythings.graph.item.id.DatumID asDatumId() {
            return new dev.everydaythings.graph.item.id.DatumID(ref.target().encodeBinary());
        }

        /** Whether this is a compound reference (has a frame key path). */
        public boolean isCompound() { return ref.frameKey() != null; }

        @Override
        public CBORObject toCborTree(Scope scope) {
            return ref.toCborTree(scope);
        }

        @Factory
        public static RefTarget fromCborTree(CBORObject node) {
            if (node == null || node.isNull()) return null;
            Ref decoded = Ref.fromCborTree(node);
            return new RefTarget(decoded);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof RefTarget other)) return false;
            return Objects.equals(ref, other.ref);
        }

        @Override
        public int hashCode() {
            return Objects.hash(ref);
        }

        @Override
        public String toString() {
            return "Ref(" + ref.encodeText() + ")";
        }
    }

    /**
     * Inline nested datum — enables expression trees and compositional frames.
     *
     * <p>Encodes as CG-CBOR Tag 12 (DATUM) wrapping the inner body's CBOR. Same
     * tag as outer-level Body/Record encoding — the wire shape of an inline
     * datum and an outer datum is identical, and decoders dispatch by context.
     *
     * <p>This class still wraps the legacy {@link FrameBodyOld}; it will be
     * superseded by a generic DatumTarget wrapping a {@link Datum} when
     * FrameBodyOld is retired.
     */
    final class FrameTarget implements BindingTarget {
        private final FrameBodyOld body;

        public FrameTarget(FrameBodyOld body) {
            this.body = Objects.requireNonNull(body, "body");
        }

        public FrameBodyOld body() { return body; }

        @Override
        public CBORObject toCborTree(Scope scope) {
            return CBORObject.FromObjectAndTag(body.toCborTree(scope), Canonical.CgTag.DATUM);
        }

        @Factory
        public static FrameTarget fromCborTree(CBORObject node) {
            if (node == null || node.isNull()) return null;
            CBORObject inner = node.isTagged() ? node.UntagOne() : node;
            FrameBodyOld body = FrameBodyOld.fromCborTree(inner);
            return body != null ? new FrameTarget(body) : null;
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
                    Canonical.CgTag.REDACTED);
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
