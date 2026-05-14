package dev.everydaythings.graph.datum;

import com.upokecenter.cbor.CBORObject;
import com.upokecenter.cbor.CBORType;
import dev.everydaythings.graph.canonical.Factory;
import dev.everydaythings.graph.encoding.CgCbor;
import dev.everydaythings.graph.id.ItemRef;
import dev.everydaythings.graph.id.HashID;
import java.util.List;
import java.util.Objects;

/**
 * A Datum that asserts content. Has no signature slot.
 *
 * <p>The head is an {@link ItemRef} pointing at a sememe — the predicate (for
 * propositional frames) or the archetype (for archetypal/manifest bodies). The
 * head can be version-pinned ({@code @<IID>\<VID>}) when the schema author wants
 * to bind to a specific version of the head sememe.
 *
 * <p>CBOR encoding: 2-element array {@code [Tag-6(head), [bindings]]}.
 *
 * <p>Construction is permissive — bodies may carry any bindings, including ones
 * beyond what the head sememe's EXPECTS strictly declares. Validation against
 * EXPECTS is a separate concern done at signing/commit time, not at construction.
 */
public final class Body extends Datum {

    public Body(ItemRef head, List<Binding> bindings) {
        super(head, bindings);
        bindId();
    }

    /**
     * Create a Body with the given head and bindings.
     */
    public static Body of(ItemRef head, List<Binding> bindings) {
        return new Body(head, bindings);
    }

    /**
     * Create a Body with the given head and no bindings.
     */
    public static Body of(ItemRef head) {
        return new Body(head, List.of());
    }

    /** The head as an {@link ItemRef} (typed accessor; head() returns HashID). */
    public ItemRef headRef() {
        return (ItemRef) head;
    }

    /**
     * Decode a Body from its CBOR form: {@code Tag-12 [Tag-6(head), [bindings]]}.
     *
     * <p>Tolerates an untagged 2-element array as a transitional fallback so
     * legacy bytes still decode while the encoding shift propagates.
     *
     * @throws IllegalArgumentException if the inner array length is not 2 or
     *         the head is not an ItemRef.
     */
    @Factory
    public static Body fromCborTree(CBORObject node) {
        Objects.requireNonNull(node, "node");
        if (node.isTagged() && node.HasMostOuterTag(CgCbor.TAG_BODY)) {
            node = node.UntagOne();
        }
        if (node.getType() != CBORType.Array || node.size() != 2) {
            throw new IllegalArgumentException(
                    "Body requires a 2-element CBOR array, got " + node.getType()
                            + (node.getType() == CBORType.Array ? " of size " + node.size() : ""));
        }
        HashID headRef = HashID.fromCborTree(node.get(0));
        if (!(headRef instanceof ItemRef itemRef)) {
            throw new IllegalArgumentException(
                    "Body head must be an ItemRef (@-prefix), got " + headRef.variant());
        }
        CBORObject bindingsArr = node.get(1);
        if (bindingsArr.getType() != CBORType.Array) {
            throw new IllegalArgumentException(
                    "Body bindings must be a CBOR array, got " + bindingsArr.getType());
        }
        List<Binding> bindings = decodeBindings(bindingsArr);
        return new Body(itemRef, bindings);
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
        if (!(o instanceof Body other)) return false;
        return head.equals(other.head) && bindings.equals(other.bindings);
    }

    @Override
    public int hashCode() {
        return Objects.hash(head, bindings);
    }

    @Override
    public String toString() {
        return "Body[" + head + ", " + bindings.size() + " bindings]";
    }
}
