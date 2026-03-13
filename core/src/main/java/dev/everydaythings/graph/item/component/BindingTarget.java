package dev.everydaythings.graph.item.component;

import com.upokecenter.cbor.CBORObject;
import com.upokecenter.cbor.CBORType;
import dev.everydaythings.graph.Canonical;
import dev.everydaythings.graph.item.Literal;
import dev.everydaythings.graph.item.id.ItemID;

import java.util.Objects;

/**
 * Value type for frame binding targets.
 *
 * <p>A binding target is the value side of a role binding in a frame.
 * Implementations:
 * <ul>
 *   <li>{@link IidTarget} — reference to another Item</li>
 *   <li>{@link Literal} — inline typed value</li>
 * </ul>
 */
public interface BindingTarget extends Canonical {

    /**
     * Decode a BindingTarget from CBOR.
     */
    @Factory
    static BindingTarget fromCborTree(CBORObject node) {
        if (node == null || node.isNull()) return null;
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

    /**
     * Binding value that references another Item.
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
