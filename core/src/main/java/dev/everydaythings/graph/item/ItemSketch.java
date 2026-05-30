package dev.everydaythings.graph.item;

import dev.everydaythings.graph.datum.Binding;
import dev.everydaythings.graph.datum.Body;
import dev.everydaythings.graph.library.SchemaWalker;
import dev.everydaythings.graph.ref.ItemRef;
import dev.everydaythings.graph.runtime.librarian.Librarian;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Transient assembly buffer for an item being created — the convergence point
 * where partially-supplied field values accumulate before an item is minted.
 *
 * <p>Pure in-VM plumbing, like {@code FrameMap} is for parsing.  Not a graph
 * type, not persisted, not signed.  It holds the target archetype, the roles
 * that archetype expects its instances to carry (its EXPECTS declarations), and
 * whatever values have been filled so far.  Multiple front-ends feed the same
 * sketch:
 *
 * <ul>
 *   <li>a parsed CREATE frame's content bindings (one-shot / eval),</li>
 *   <li>positional / quoted literals folded against the EXPECTS order,</li>
 *   <li>interactive prompts for whatever remains unfilled.</li>
 * </ul>
 *
 * <p>The sketch only tracks <i>presence</i> against the archetype's expected
 * roles, mirroring {@link SchemaWalker}'s first-cut validation.  Type-pattern
 * conformance (the {@code ?Type} constraint on each EXPECTS) is a later layer,
 * wired when the matcher orchestrator is available.
 */
public final class ItemSketch {

    private final ItemRef archetype;
    private final List<ItemRef> expectedRoles;
    private final Map<ItemRef, Object> filled = new LinkedHashMap<>();
    /** Optional — when present, completeness is subtype-aware (a filled subtype role satisfies an expected supertype role). */
    private final Librarian librarian;

    private ItemSketch(ItemRef archetype, List<ItemRef> expectedRoles, Librarian librarian) {
        this.archetype = archetype;
        this.expectedRoles = expectedRoles;
        this.librarian = librarian;
    }

    /**
     * Begin a sketch for {@code archetype}, reading its expected roles from
     * {@code archetypeManifest}.  A null manifest (archetype not locally
     * materialized) yields a sketch with no expectations — we can't enforce
     * what we can't see.  Completeness is exact-match (no subtype resolution);
     * use {@link #forArchetype(ItemRef, Body, Librarian)} for subtype-aware
     * matching.
     */
    public static ItemSketch forArchetype(ItemRef archetype, Body archetypeManifest) {
        return forArchetype(archetype, archetypeManifest, null);
    }

    /**
     * Begin a sketch with subtype-aware completeness.  A filled binding whose
     * role is a subtype of an expected role satisfies that expectation — e.g.
     * a {@code Name}-roled fill satisfies an expected {@code Identifier}, since
     * Name is-a Identifier.  Subtype is resolved by walking the filled role's
     * manifest-head chain via {@code librarian}.
     */
    public static ItemSketch forArchetype(ItemRef archetype, Body archetypeManifest, Librarian librarian) {
        Objects.requireNonNull(archetype, "archetype");
        List<ItemRef> expected = archetypeManifest == null
                ? List.of()
                : SchemaWalker.expectedRoles(archetypeManifest);
        return new ItemSketch(archetype, expected, librarian);
    }

    /** The archetype this sketch will mint an instance of. */
    public ItemRef archetype() {
        return archetype;
    }

    /**
     * Fill the slot for {@code role} with {@code value}.  Filling a role that
     * isn't among the expected ones is allowed: the value carries forward as an
     * initial binding on the minted item, it just doesn't satisfy an EXPECTS.
     * Re-filling a role replaces the prior value.  Returns this sketch for
     * chaining.
     */
    public ItemSketch fill(ItemRef role, Object value) {
        Objects.requireNonNull(role, "role");
        Objects.requireNonNull(value, "value");
        filled.put(role, value);
        return this;
    }

    /** The roles the archetype expects instances to carry. */
    public List<ItemRef> expectedRoles() {
        return expectedRoles;
    }

    /**
     * Expected roles that no filled binding satisfies.  An expected role is
     * satisfied by an exact-role fill, or — when this sketch has a librarian —
     * by a fill whose role is a subtype of the expected role.
     */
    public List<ItemRef> unfilledRoles() {
        List<ItemRef> out = new ArrayList<>();
        for (ItemRef role : expectedRoles) {
            if (!isSatisfied(role)) {
                out.add(role);
            }
        }
        return out;
    }

    /** True iff some filled binding's role satisfies {@code expected} (exactly or as a subtype). */
    private boolean isSatisfied(ItemRef expected) {
        if (filled.containsKey(expected)) return true;
        if (librarian == null) return false;
        for (ItemRef filledRole : filled.keySet()) {
            if (isSubtypeOf(filledRole, expected)) return true;
        }
        return false;
    }

    /**
     * Walk {@code sub}'s manifest-head chain (its supertype chain) looking for
     * {@code sup}.  {@code @Seed.Item(head=...)} makes a sememe's manifest head
     * its supertype, so this is the type-hierarchy walk: Name → Identifier → ...
     */
    private boolean isSubtypeOf(ItemRef sub, ItemRef sup) {
        ItemRef current = sub;
        Set<ItemRef> visited = new HashSet<>();
        while (current != null && visited.add(current)) {
            Item item = librarian.fetchItem(current).orElse(null);
            if (item == null) return false;
            Manifest manifest = item.current();
            ItemRef head = manifest != null ? manifest.body().headRef() : item.archetype();
            if (head == null || head.equals(current)) return false;
            if (head.equals(sup)) return true;
            current = head;
        }
        return false;
    }

    /** True iff every expected role has been filled. */
    public boolean isComplete() {
        return unfilledRoles().isEmpty();
    }

    /**
     * The filled values as content bindings, ready to pass to
     * {@link Item#commit}.  Insertion order is preserved.
     */
    public List<Binding> bindings() {
        List<Binding> out = new ArrayList<>(filled.size());
        filled.forEach((role, value) -> out.add(new Binding(role, value)));
        return out;
    }
}
