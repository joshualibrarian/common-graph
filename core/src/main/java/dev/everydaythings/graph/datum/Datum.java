package dev.everydaythings.graph.datum;

import dev.everydaythings.graph.canonical.Scope;

import com.upokecenter.cbor.CBORObject;
import dev.everydaythings.graph.canonical.Canonical;
import dev.everydaythings.graph.canonical.HashTree;
import dev.everydaythings.graph.canonical.Walker;
import dev.everydaythings.graph.id.*;
import lombok.Getter;

import java.util.ArrayList;
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
 * <p>Body construction is permissive — it does not validate against the head
 * sememe's EXPECTS at construction. Validation happens at signing or commit time
 * via separate validation passes; bodies may legitimately carry bindings beyond
 * what EXPECTS strictly demands (TIME, DEBUG, supplementary content).
 *
 * <p>Datum is a POJO with respect to encoders — no knowledge of CG-CBOR or any
 * other wire format. Identity (the {@link #id} field) is computed via the
 * encoder-agnostic {@link HashTree} protocol at construction time.
 */
public sealed abstract class Datum implements Canonical permits Body, Record {

    /** The head reference: the sememe (for bodies) or body CID (for records). */
    @Getter
    protected final Reference head;

    /** The bindings carried by this Datum, in canonical order. */
    @Getter
    protected final List<Binding> bindings;

    /**
     * The {@link ContentID} of the specific byte realization this Datum was decoded
     * from, if known. {@code null} for in-memory constructed Datums.
     */
    @Getter
    protected ContentID source;

    /**
     * The Datum's structural semantic identity — the encoding-independent
     * Merkle root, multihash-framed. Set eagerly at construction by the
     * concrete subclass via {@link #bindId()} after all its fields are
     * populated.
     *
     * <p>The algorithm choice is in the multihash framing of the ID itself —
     * given a DatumID, you know which algorithm was used.
     */
    @Getter
    protected DatumID id;

    protected Datum(Reference head, List<Binding> bindings) {
        this.head = Objects.requireNonNull(head, "head");
        Objects.requireNonNull(bindings, "bindings");
        this.bindings = canonicalSort(bindings);
        this.source = null;
        // id is set by the concrete subclass via bindId() after its own fields
        // (e.g. Record's signature) are populated.
    }

    /**
     * Concrete subclass invokes this at the end of its constructor, after its
     * own fields are populated. Computes the Merkle hash of the now-complete
     * Datum and binds it as the {@link #id}.
     */
    protected final void bindId() {
        byte[] digest = HashTree.hash(Walker.walk(this), HashTree.DEFAULT_DIGEST);
        this.id = new DatumID(digest, HashTree.DEFAULT_DIGEST);
    }

    /**
     * Record the byte realization this Datum was decoded from.
     */
    public void bindSource(ContentID source) {
        this.source = source;
    }

    /**
     * Canonicalize a binding list. Bindings are a multiset; the order in which
     * the caller assembled them must not affect identity.
     *
     * <p>Sorted by {@link HashTree#CANONICAL} — bitwise comparison of each
     * binding's structural hash under the identity protocol. Encoder-
     * independent: the sort is determined by HashTree, not by CG-CBOR or any
     * other wire format.
     */
    private static List<Binding> canonicalSort(List<Binding> bindings) {
        if (bindings.size() < 2) return List.copyOf(bindings);
        List<Binding> sorted = new ArrayList<>(bindings);
        sorted.sort(HashTree.CANONICAL);
        return List.copyOf(sorted);
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
    public List<Binding> bindingsByRole(ItemID role) {
        Objects.requireNonNull(role, "role");
        return bindings.stream().filter(b -> b.role().equals(role)).collect(Collectors.toList());
    }

    private static boolean matches(Binding binding, CompoundKey key) {
        if (!binding.role().equals(key.head())) return false;
        return binding.qualifiers().equals(key.qualifiers());
    }

    /**
     * The DatumID — structural semantic identity. Alias for {@link #getId()},
     * preserved for legacy API ergonomics.
     */
    public DatumID datumId() {
        return id;
    }

    /**
     * Encode the bindings list as a CBOR array. Used by legacy Canonical
     * encoding paths inside Body/Record; will be retired with the long-tail
     * Canonical-interface migration.
     */
    protected CBORObject encodeBindingsArray(Scope scope) {
        CBORObject arr = CBORObject.NewArray();
        for (Binding b : bindings) {
            arr.Add(b.toCborTree(scope));
        }
        return arr;
    }
}
