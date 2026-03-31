package dev.everydaythings.graph.frame;

import dev.everydaythings.graph.item.Factory;

import com.upokecenter.cbor.CBORObject;
import com.upokecenter.cbor.CBORType;
import dev.everydaythings.graph.Canonical;
import dev.everydaythings.graph.item.Literal;
import dev.everydaythings.graph.item.id.ContentID;
import dev.everydaythings.graph.item.id.HashID;
import dev.everydaythings.graph.item.id.ItemID;
import dev.everydaythings.graph.item.id.Ref;

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
 *   <li>{@link Literal} — inline typed value</li>
 * </ul>
 */
public interface BindingTarget extends Canonical {

    /**
     * Decode a BindingTarget from CBOR.
     *
     * <p>Dispatch:
     * <ul>
     *   <li>Tag 6 → {@link RefTarget} (unified reference)</li>
     *   <li>Tag 23 → {@link FrameTarget} (inline nested frame)</li>
     *   <li>Array → {@link Literal}</li>
     *   <li>Bare ByteString → {@link IidTarget} (backward compat for old data)</li>
     * </ul>
     */
    @Factory
    static BindingTarget fromCborTree(CBORObject node) {
        if (node == null || node.isNull()) return null;
        if (node.isTagged()) {
            int tag = node.getMostOuterTag().ToInt32Checked();
            if (tag == Canonical.CgTag.REF) return RefTarget.fromCborTree(node);
            if (tag == Canonical.CgTag.FRAME) return FrameTarget.fromCborTree(node);
            // Tag 1 (instant), Tag 7 (explicit typed value) → Literal
            if (tag == 1 || tag == Canonical.CgTag.VALUE) return Literal.fromCborTree(node);
        }
        if (node.getType() == CBORType.ByteString) {
            return IidTarget.fromCborTree(node);
        }
        // Bare primitives (text, integer, boolean) and arrays → Literal
        if (node.getType() == CBORType.TextString
                || node.getType() == CBORType.Integer
                || node.getType() == CBORType.Boolean) {
            return Literal.fromCborTree(node);
        }
        if (node.getType() == CBORType.Array) {
            return Literal.fromCborTree(node);
        }
        throw new IllegalArgumentException("Cannot decode BindingTarget from CBOR type: " + node.getType());
    }

    /** Convenience factory for item references in bindings. */
    static IidTarget iid(ItemID iid) { return IidTarget.of(iid); }

    /** Convenience factory for unified references (Tag 6). */
    static RefTarget ref(HashID ref) { return new RefTarget(Ref.of(new ItemID(ref.encodeBinary()))); }

    /** Convenience factory for compound references (Tag 6 with frame key). */
    static RefTarget ref(Ref ref) { return new RefTarget(ref); }

    /** Convenience factory for inline nested frames (Tag 23). */
    static FrameTarget frame(FrameBody body) { return new FrameTarget(body); }

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
     * Binding value that references another Item.
     *
     * <p>Legacy encoding: bare ByteString. New code should prefer {@link RefTarget}
     * which uses Tag 6 and supports both ItemID and ContentID.
     */
    final class IidTarget implements BindingTarget {
        private ItemID iid;

        public IidTarget() {}
        public IidTarget(ItemID iid) { this.iid = Objects.requireNonNull(iid, "iid"); }
        public IidTarget(byte[] bytes) { this.iid = new ItemID(bytes); }

        public ItemID iid() { return iid; }
        public static IidTarget of(ItemID iid) { return new IidTarget(iid); }

        @Override
        public CBORObject toCborTree(Scope scope) {
            return iid != null ? CBORObject.FromByteArray(iid.encodeBinary()) : CBORObject.Null;
        }

        @Factory
        public static IidTarget fromCborTree(CBORObject node) {
            if (node == null || node.isNull()) return null;
            return new IidTarget(node.GetByteString());
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof IidTarget other)) return false;
            return Objects.equals(iid, other.iid);
        }

        @Override
        public int hashCode() {
            return Objects.hash(iid);
        }

        @Override
        public String toString() {
            return iid != null ? iid.displayAtWidth(12) : "null";
        }
    }

    /**
     * Inline nested frame — enables expression trees and compositional frames.
     *
     * <p>Encodes as CG-CBOR Tag 23 wrapping the FrameBody's CBOR encoding.
     * This allows bindings to contain entire sub-frames, enabling nested
     * expressions like {@code MUL { THEME → ADD { THEME→3, INSTRUMENT→5 }, INSTRUMENT → 2 }}.
     */
    final class FrameTarget implements BindingTarget {
        private final FrameBody body;

        public FrameTarget(FrameBody body) {
            this.body = Objects.requireNonNull(body, "body");
        }

        public FrameBody body() { return body; }

        @Override
        public CBORObject toCborTree(Scope scope) {
            return CBORObject.FromObjectAndTag(body.toCborTree(scope), Canonical.CgTag.FRAME);
        }

        @Factory
        public static FrameTarget fromCborTree(CBORObject node) {
            if (node == null || node.isNull()) return null;
            CBORObject inner = node.isTagged() ? node.UntagOne() : node;
            FrameBody body = FrameBody.fromCborTree(inner);
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
}
