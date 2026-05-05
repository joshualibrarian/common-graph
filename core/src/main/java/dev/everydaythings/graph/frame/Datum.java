package dev.everydaythings.graph.frame;

import com.upokecenter.cbor.CBORObject;
import dev.everydaythings.graph.Canonical;
import dev.everydaythings.graph.Hash;
import dev.everydaythings.graph.item.id.CompoundKey;
import dev.everydaythings.graph.item.id.ContentID;
import dev.everydaythings.graph.item.id.Reference;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * The unified structural primitive of Common Graph.
 *
 * <p>A Datum is a head reference plus a list of bindings. Two concrete shapes:
 * <ul>
 *   <li>{@link Body} — head references a sememe (the predicate or archetype). No
 *       signature. CBOR encoding: 2-element array {@code [head, [bindings]]}.</li>
 *   <li>{@link Record} — head references a body's content (a frame body's CID).
 *       Carries a signature in varsig format. CBOR encoding: 3-element array
 *       {@code [head, [bindings], signature]}.</li>
 * </ul>
 *
 * <p>Decoding dispatches on array length: 2 elements → Body, 3 elements → Record.
 * The first element being a Tag-6 reference distinguishes Datum arrays from
 * generic CBOR arrays a decoder might encounter; storage context provides the
 * expected type for content-addressed retrieval.
 *
 * <p>Body construction is permissive — it does not validate against the head
 * sememe's EXPECTS at construction. Validation happens at signing or commit time
 * via separate validation passes; bodies may legitimately carry bindings beyond
 * what EXPECTS strictly demands (TIME, DEBUG, supplementary content).
 */
public sealed abstract class Datum implements Canonical permits Body, Record {

    /** The head reference: the sememe (for bodies) or body CID (for records). */
    protected final Reference head;

    /** The bindings carried by this Datum. */
    protected final List<Binding> bindings;

    protected Datum(Reference head, List<Binding> bindings) {
        this.head = Objects.requireNonNull(head, "head");
        this.bindings = List.copyOf(Objects.requireNonNull(bindings, "bindings"));
    }

    /** The head reference. */
    public Reference head() {
        return head;
    }

    /** The bindings carried by this Datum. */
    public List<Binding> bindings() {
        return bindings;
    }

    /**
     * Find the first binding whose (role, qualifiers) matches the given CompoundKey.
     */
    public Optional<Binding> binding(CompoundKey key) {
        Objects.requireNonNull(key, "key");
        for (Binding b : bindings) {
            if (matches(b, key)) return Optional.of(b);
        }
        return Optional.empty();
    }

    /**
     * Find all bindings whose (role, qualifiers) match the given CompoundKey.
     */
    public List<Binding> bindings(CompoundKey key) {
        Objects.requireNonNull(key, "key");
        return bindings.stream().filter(b -> matches(b, key)).collect(Collectors.toList());
    }

    /**
     * Find all bindings with the given role (any qualifiers).
     */
    public List<Binding> bindingsByRole(dev.everydaythings.graph.item.id.ItemID role) {
        Objects.requireNonNull(role, "role");
        return bindings.stream().filter(b -> b.role().equals(role)).collect(Collectors.toList());
    }

    private static boolean matches(Binding binding, CompoundKey key) {
        if (!binding.role().equals(key.head())) return false;
        return binding.qualifiers().equals(key.qualifiers());
    }

    /**
     * Compute the CID of this Datum's encoded form.
     *
     * <p>For a Body, this is the VID (Version ID) when the Body is the body of a
     * manifest. For a Record, this is just the record's hash identity.
     */
    public ContentID cid() {
        byte[] encoded = encodeBinary(Scope.BODY);
        return new ContentID(Hash.DEFAULT.digest(encoded), Hash.DEFAULT);
    }

    /**
     * Encode the bindings list as a CBOR array.
     */
    protected CBORObject encodeBindingsArray(Scope scope) {
        CBORObject arr = CBORObject.NewArray();
        for (Binding b : bindings) {
            arr.Add(b.toCborTree(scope));
        }
        return arr;
    }
}
