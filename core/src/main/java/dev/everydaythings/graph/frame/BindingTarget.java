package dev.everydaythings.graph.frame;

import dev.everydaythings.graph.item.Factory;

import com.upokecenter.cbor.CBORObject;
import com.upokecenter.cbor.CBORType;
import dev.everydaythings.graph.Canonical;
import dev.everydaythings.graph.item.Literal;
import dev.everydaythings.graph.item.id.ContentID;
import dev.everydaythings.graph.item.id.HashID;
import dev.everydaythings.graph.item.id.ItemID;

import java.util.Objects;

/**
 * Value type for frame binding targets.
 *
 * <p>A binding target is the value side of a role binding in a frame.
 * Implementations:
 * <ul>
 *   <li>{@link RefTarget} — unified reference (Tag 6) to any HashID (ItemID or ContentID)</li>
 *   <li>{@link IidTarget} — legacy reference to another Item (encodes as bare ByteString)</li>
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
     *   <li>Array → {@link Literal}</li>
     *   <li>Bare ByteString → {@link IidTarget} (backward compat for old data)</li>
     * </ul>
     */
    @Factory
    static BindingTarget fromCborTree(CBORObject node) {
        if (node == null || node.isNull()) return null;
        // Tag 6 = REF (new unified reference format)
        if (node.isTagged() && node.getMostInnerTag().ToInt32Checked() == 6) {
            return RefTarget.fromCborTree(node);
        }
        if (node.getType() == CBORType.ByteString) {
            return IidTarget.fromCborTree(node);
        }
        if (node.getType() == CBORType.Array) {
            return Canonical.fromCborTree(node, Literal.class, Scope.RECORD);
        }
        throw new IllegalArgumentException("Cannot decode BindingTarget from CBOR type: " + node.getType());
    }

    /** Convenience factory for item references in bindings. */
    static IidTarget iid(ItemID iid) { return IidTarget.of(iid); }

    /** Convenience factory for unified references (Tag 6). */
    static RefTarget ref(HashID ref) { return new RefTarget(ref); }

    /**
     * Unified reference target using CG-CBOR Tag 6 (REF).
     *
     * <p>Handles both ItemID and ContentID references — both are HashIDs.
     * The wire format is identical: {@code Tag(6, bytes)}. On decode,
     * consumers use {@link #asItemId()} or {@link #asCid()} based on
     * context (the role tells you what to expect).
     */
    final class RefTarget implements BindingTarget {
        private final HashID ref;

        public RefTarget(HashID ref) {
            this.ref = Objects.requireNonNull(ref, "ref");
        }

        public HashID ref() { return ref; }

        public ItemID asItemId() {
            return ref instanceof ItemID iid ? iid : new ItemID(ref.encodeBinary());
        }

        public ContentID asCid() {
            return ref instanceof ContentID cid ? cid : new ContentID(ref.encodeBinary());
        }

        @Override
        public CBORObject toCborTree(Scope scope) {
            return CBORObject.FromObjectAndTag(
                    CBORObject.FromByteArray(ref.encodeBinary()), 6);
        }

        @Factory
        public static RefTarget fromCborTree(CBORObject node) {
            if (node == null || node.isNull()) return null;
            CBORObject inner = node.isTagged() ? node.UntagOne() : node;
            byte[] bytes = inner.GetByteString();
            // Default to ItemID — callers use asCid() when the role indicates content
            return new RefTarget(new ItemID(bytes));
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
            return "Ref(" + ref.displayAtWidth(12) + ")";
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
    }
}
