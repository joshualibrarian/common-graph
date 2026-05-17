package dev.everydaythings.graph.datum;

import dev.everydaythings.graph.canonical.HashTree;
import dev.everydaythings.graph.canonical.CanonWalker;
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
 *
 * TODO: we need a thorough going through of this whole package.  There's still lots of CBOR references in it and it should be encoding agnostic.
 * TODO: also the builders could use unification and improvement
 */
public sealed abstract class Datum implements DatumNode permits Body, Record {

    /** The head reference: the sememe (for bodies) or body CID (for records). */
    @Getter
    protected final HashID head;

    /**
     * The Datum's body-tree entries, in canonical order.  Each entry is a
     * {@link DatumNode} — most commonly a {@link Binding}, but possibly an
     * {@link Opaque} standing in for an elided/compressed/encrypted
     * binding.
     */
    protected final List<DatumNode> entries;

    /**
     * The {@link ContentRef} of the specific byte realization this Datum was decoded
     * from, if known. {@code null} for in-memory constructed Datums.
     */
    @Getter
    protected ContentRef source;

    /**
     * The Datum's structural semantic identity — the encoding-independent
     * Merkle root, multihash-framed.  Computed lazily on first access via
     * {@link #datumId()} (or {@link #getId()}); never set eagerly at
     * construction.  In-VM construction and mutation are cost-free until
     * something actually asks for the hash (serialization, indexing,
     * cross-datum reference).
     *
     * <p>{@code volatile} for safe lazy publication under double-checked
     * locking.  The algorithm choice is in the multihash framing of the ID
     * itself — given a DatumRef, you know which algorithm was used.
     */
    private volatile DatumRef id;

    protected Datum(HashID head, List<? extends DatumNode> entries) {
        this.head = Objects.requireNonNull(head, "head");
        Objects.requireNonNull(entries, "entries");
        this.entries = canonicalSort(entries);
        this.source = null;
    }

    /**
     * All body-tree entries in canonical order — both {@link Binding}s and
     * {@link Opaque} stand-ins.  Iterating this is the way to see the body
     * faithfully (the soft-deprecated {@link #bindings()} accessor silently
     * filters Opaques out).
     */
    public List<DatumNode> entries() {
        return entries;
    }

    /**
     * The {@link Binding} entries only — {@link Opaque} stand-ins are
     * filtered out.  Use this when you only care about the visible bindings
     * (construction code, simple lookups).  Use {@link #entries()} when you
     * need to see the full body tree — walkers, validators, the codec.
     */
    public List<Binding> bindings() {
        List<Binding> result = new ArrayList<>(entries.size());
        for (DatumNode e : entries) {
            if (e instanceof Binding b) result.add(b);
        }
        return result;
    }

    /**
     * Record the byte realization this Datum was decoded from.
     */
    public void bindSource(ContentRef source) {
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
    private static List<DatumNode> canonicalSort(List<? extends DatumNode> entries) {
        if (entries.size() < 2) return List.copyOf(entries);
        List<DatumNode> sorted = new ArrayList<>(entries);
        sorted.sort(HashTree.CANONICAL);
        return List.copyOf(sorted);
    }

    /**
     * Find the first binding whose (role, qualifiers) matches the given CompoundKey.
     */
    public Optional<Binding> binding(CompoundKey key) {
        Objects.requireNonNull(key, "key");
        for (DatumNode e : entries) {
            if (e instanceof Binding b && matches(b, key)) return Optional.of(b);
        }
        return Optional.empty();
    }

    /**
     * Find all bindings whose (role, qualifiers) match the given CompoundKey.
     */
    public List<Binding> bindings(CompoundKey key) {
        Objects.requireNonNull(key, "key");
        List<Binding> result = new ArrayList<>();
        for (DatumNode e : entries) {
            if (e instanceof Binding b && matches(b, key)) result.add(b);
        }
        return result;
    }

    /**
     * Find all bindings with the given role (any qualifiers).
     */
    public List<Binding> bindingsByRole(ItemRef role) {
        Objects.requireNonNull(role, "role");
        List<Binding> result = new ArrayList<>();
        for (DatumNode e : entries) {
            if (e instanceof Binding b && role.equals(b.role())) result.add(b);
        }
        return result;
    }

    private static boolean matches(Binding binding, CompoundKey key) {
        if (!binding.role().equals(key.head())) return false;
        return binding.qualifiers().equals(key.qualifiers());
    }

    /**
     * The DatumRef — structural semantic identity.  Computes the Merkle hash on
     * first access and caches it; subsequent calls are a single volatile read.
     */
    public DatumRef datumId() {
        DatumRef result = id;
        if (result == null) {
            synchronized (this) {
                result = id;
                if (result == null) {
                    byte[] digest = HashTree.hash(CanonWalker.walk(this), HashTree.DEFAULT_DIGEST);
                    result = new DatumRef(digest, HashTree.DEFAULT_DIGEST);
                    id = result;
                }
            }
        }
        return result;
    }

    /** Backwards-compat accessor; prefer {@link #datumId()}. */
    public DatumRef getId() {
        return datumId();
    }

}
