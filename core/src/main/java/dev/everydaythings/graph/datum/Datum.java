package dev.everydaythings.graph.datum;

import dev.everydaythings.graph.canonical.HashTree;
import dev.everydaythings.graph.canonical.CanonWalker;
import dev.everydaythings.graph.ref.*;
import lombok.Getter;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * The unified structural primitive of Common Graph.
 *
 * <p>A Datum is a head reference plus a list of bindings.  Two concrete shapes:
 * <ul>
 *   <li>{@link Body} — head references a sememe (the predicate or archetype).
 *       No signature.  Structurally: a head plus a list of bindings.</li>
 *   <li>{@link Record} — head references a body's content (a frame body's
 *       CID).  Adds a signature in varsig format.  Structurally: a head, a
 *       list of bindings, and a signature.</li>
 * </ul>
 *
 * <p>Body construction is permissive — it does not validate against the head
 * sememe's EXPECTS at construction.  Validation happens at signing or commit
 * time via separate validation passes; bodies may legitimately carry bindings
 * beyond what EXPECTS strictly demands (TIME, DEBUG, supplementary content).
 *
 * <p>Datum is encoder-agnostic — no knowledge of any wire format.  Identity
 * (the {@link #id} field) is computed via the encoder-independent
 * {@link HashTree} protocol on first access.  Concrete wire formats
 * (CG-CBOR is the reference one) live in the {@code encoding/} package
 * and consume the same structural model.
 *
 * <p>Builders in this package: {@link DatumBuilder} (abstract base for
 * binding accumulation), {@link BodyBuilder} (bare body, no records),
 * {@link AttributedBodyBuilder} (abstract, body + records), {@link
 * FrameBuilder} / {@code ManifestBuilder} (concrete attributed-body
 * builders), {@link RecordBuilder} (record sub-builder), {@link
 * BindingBuilder} (binding sub-builder).
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
     * The {@link ContentRef} of the specific byte realization this Datum
     * was decoded from (or encoded to and persisted), if known.  {@code
     * null} for in-memory constructed Datums that have never been
     * serialized.  Set lazily by the storage layer at persist time via
     * {@link #bindSource(ContentRef)} — Datum itself never encodes.
     */
    @Getter
    protected ContentRef source;

    /**
     * The Datum's structural semantic identity — the encoding-independent
     * Merkle root, multihash-framed.  Computed lazily on first access via
     * {@link #datumId()}; never set eagerly at construction.  In-VM
     * construction and mutation are cost-free until something actually
     * asks for the hash (serialization, indexing, cross-datum reference).
     * Lightweight scene trees that never escape to storage pay zero hash
     * cost.
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
     * {@link Opaque} stand-ins.  Use this when you need a faithful view of
     * the body tree (walkers, validators, the codec).  Use {@link
     * #bindings()} when you only care about the visible bindings.
     */
    public List<DatumNode> entries() {
        return entries;
    }

    /**
     * The {@link Binding} entries only — {@link Opaque} stand-ins are
     * filtered out.  The common-case accessor; most callers want this.
     * For a complete body-tree view including Opaques, use {@link
     * #entries()}.
     */
    public List<Binding> bindings() {
        List<Binding> result = new ArrayList<>(entries.size());
        for (DatumNode e : entries) {
            if (e instanceof Binding b) result.add(b);
        }
        return result;
    }

    /**
     * Record the byte realization this Datum was decoded from (or just
     * encoded to and persisted).  Intentional mutator on an otherwise-
     * immutable type: paired with the lazy {@link #datumId() hash}, this
     * lets the storage layer attach a ContentRef when it materializes
     * bytes without forcing every Datum to know its source at
     * construction.  Callers other than the storage layer should not call
     * this.
     */
    public void bindSource(ContentRef source) {
        this.source = source;
    }

    /**
     * Canonicalize a binding list. Bindings are a multiset; the order in which
     * the caller assembled them must not affect identity.
     *
     * <p>Sorted by {@link HashTree#CANONICAL} — bitwise comparison of each
     * binding's structural hash under the identity protocol.  Encoder-
     * independent: the sort is determined by HashTree, not by any
     * particular wire format.
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
     * The DatumRef — structural semantic identity.  Computed lazily: the
     * Merkle hash is built (via {@link CanonWalker} and {@link HashTree})
     * on first access and cached for subsequent reads.  In-memory datums
     * that never need their identity (lightweight scene trees,
     * intermediate construction values, etc.) pay zero hash cost.
     *
     * <p>Thread-safe via double-checked locking on the volatile {@link
     * #id} field; concurrent readers either see the cached value or
     * cooperate at the synchronized block.
     *
     * <p>TODO: replace the hand-rolled DCL with Lombok's {@code @Lazy}
     * once the field can be reshaped to suit it.
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

}
