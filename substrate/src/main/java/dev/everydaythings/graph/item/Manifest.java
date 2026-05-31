package dev.everydaythings.graph.item;


import dev.everydaythings.graph.CoreVocabulary;
import dev.everydaythings.graph.SchemaVocabulary;
import dev.everydaythings.graph.datum.AttributedBody;
import dev.everydaythings.graph.datum.Binding;
import dev.everydaythings.graph.datum.Body;
import dev.everydaythings.graph.datum.Record;
import dev.everydaythings.graph.ref.CompoundKey;
import dev.everydaythings.graph.ref.DatumRef;
import dev.everydaythings.graph.ref.ItemRef;

import java.util.List;
import java.util.Optional;

/**
 * A {@link AttributedBody} whose body represents a version of an item.
 *
 * <p>A Manifest is structurally just a Frame whose body has an {@code ITEM_ID}
 * binding. The wrapper concept dissolves — the IID lives as a binding inside the
 * body, not as a separate envelope field. This class adds archetypal-flavored
 * convenience methods on top of {@link AttributedBody} for the common
 * version-DAG and curation queries.
 *
 * <p>Common bindings on a manifest body:
 * <table>
 *   <tr><th>Role</th><th>Purpose</th></tr>
 *   <tr><td>{@code ITEM_ID}</td><td>The item's identity (the IID this is a version of)</td></tr>
 *   <tr><td>{@code FOLLOWS}</td><td>Parent VIDs (zero for inception, multiple for merges)</td></tr>
 *   <tr><td>{@code ENDORSES}</td><td>Frame body CIDs that this version endorses as its content</td></tr>
 *   <tr><td>{@code IMPLEMENTATION}</td><td>HashID to the implementation that produced this version</td></tr>
 *   <tr><td>{@code CONFIG:[...]}</td><td>Per-purpose configuration (retention, presentation, etc.)</td></tr>
 * </table>
 */
public final class Manifest extends AttributedBody {

    // Aliases to the structural sememes defined in CoreVocabulary — preserved here
    // for callers that reference Manifest.ITEM_ID, Manifest.FOLLOWS, etc., directly.

    /** Canonical key for the ITEM_ID structural sememe. */
    public static final String ITEM_ID_KEY = CoreVocabulary.ItemId.KEY;

    /** ItemRef of the structural ITEM_ID sememe. */
    public static final ItemRef ITEM_ID = ItemRef.iid(CoreVocabulary.ItemId.KEY);

    /** Canonical key for the FOLLOWS structural sememe. */
    public static final String FOLLOWS_KEY = CoreVocabulary.Follows.KEY;

    /** ItemRef of the structural FOLLOWS sememe. */
    public static final ItemRef FOLLOWS = ItemRef.iid(CoreVocabulary.Follows.KEY);

    /** Canonical key for the ENDORSES structural sememe. */
    public static final String ENDORSES_KEY = CoreVocabulary.Endorses.KEY;

    /** ItemRef of the structural ENDORSES sememe. */
    public static final ItemRef ENDORSES = ItemRef.iid(CoreVocabulary.Endorses.KEY);

    /** Canonical key for the HANDLES sememe — used as a binding role on
     *  archetype manifests to declare which predicates the archetype receives. */
    public static final String HANDLES_KEY = CoreVocabulary.Handles.KEY;

    /** ItemRef of the HANDLES sememe. */
    public static final ItemRef HANDLES = ItemRef.iid(CoreVocabulary.Handles.KEY);

    /** Canonical key for the IMPLEMENTS sememe — used as a binding role on
     *  code-item manifests to declare which archetype the code realizes. */
    public static final String IMPLEMENTS_KEY = SchemaVocabulary.Implements.KEY;

    /** ItemRef of the IMPLEMENTS sememe. */
    public static final ItemRef IMPLEMENTS = ItemRef.iid(SchemaVocabulary.Implements.KEY);

    public Manifest(Body body, List<Record> records) {
        super(body, records);
        if (body.binding(CompoundKey.of(ITEM_ID)).isEmpty()) {
            throw new IllegalArgumentException(
                    "Manifest body must have an ITEM_ID binding "
                            + "(use Frame for non-archetypal bodies)");
        }
    }

    /** Construct a Manifest with the given body and no records. */
    public static Manifest of(Body body) {
        return new Manifest(body, List.of());
    }

    /** Construct a Manifest with the given body and records. */
    public static Manifest of(Body body, List<Record> records) {
        return new Manifest(body, records);
    }

    /**
     * Open a fluent builder for a manifest with the given archetype and item IID.
     * The builder auto-injects the {@code ITEM_ID} binding.
     */
    public static ManifestBuilder compose(ItemRef archetype, ItemRef itemId) {
        return new ManifestBuilder(archetype, itemId);
    }

    /**
     * The item's identity — read from the {@code ITEM_ID} binding.
     */
    public ItemRef itemId() {
        Binding b = body().binding(CompoundKey.of(ITEM_ID))
                .orElseThrow(() -> new IllegalStateException(
                        "Manifest body missing ITEM_ID binding (should have been validated at construction)"));
        return readIidFromTarget(b.target(), ITEM_ID_KEY);
    }

    /**
     * The version ID — the body's structural identity (DatumRef).
     */
    public DatumRef versionId() {
        return body().datumId();
    }

    /**
     * Parent version IDs — read from {@code FOLLOWS} bindings.
     *
     * <p>Returns an empty list for an inception manifest (no parents).
     * Multi-IID FOLLOWS indicates a merge of multiple parent versions.
     */
    public List<DatumRef> parents() {
        return body().bindingsByRole(FOLLOWS).stream()
                .map(b -> readDatumIdFromTarget(b.target(), FOLLOWS_KEY))
                .toList();
    }

    /**
     * Endorsed frame bindings — the bindings whose role is {@code ENDORSES}.
     *
     * <p>Each endorsed binding's target is the body CID of a frame this manifest
     * endorses as part of its content state.
     */
    public List<Binding> endorses() {
        return body().bindingsByRole(ENDORSES);
    }

    /**
     * Find the implementation binding for a specific language, if any.
     *
     * <p>Example: {@code manifest.implementationFor(ItemRef.iid(Java.KEY))}
     * returns the Java implementation binding (if this manifest declares one).
     */
    public Optional<Binding> implementationFor(ItemRef language) {
        return body().bindingsByRole(language).stream().findFirst();
    }

    /**
     * Find a CONFIG binding with the given qualifier list.
     *
     * <p>Example: {@code config(CompoundKey.of(CONFIG, RETENTION))} returns the
     * binding declaring this manifest's retention policy.
     */
    public Optional<Binding> config(CompoundKey configKey) {
        return body().binding(configKey);
    }

    /**
     * Build an implementation binding declaring the language and code-reference
     * form for an item's runtime realization.
     *
     * <p>Shape: {@code <language>:[<form>] → target}. The role is the language
     * sememe (Java, Python, Lisp, etc.); the qualifier is the form
     * (ClassName for class identifiers, SourceCode for source text); the target
     * is the actual reference (text for class/source, CID for bytecode).
     *
     * <p>Language-specific convenience constructors live in
     * {@code dev.everydaythings.graph.runtime.Implementations} (e.g.
     * {@code Implementations.forJava(Class)}); Manifest itself stays
     * language-agnostic.
     */
    public static Binding implementation(ItemRef language, ItemRef form, Object target) {
        return Binding.qualified(
                language,
                List.of(new CompoundKey.Sememe(form)),
                target);
    }

    /**
     * Build a HANDLES binding declaring this manifest's owner processes frames
     * headed by {@code predicate}.
     *
     * <p>Shape: {@code @HANDLES → @<predicate>}.  Typically lives on an
     * archetype manifest, declaring the API surface inherited by all instances.
     */
    public static Binding handles(ItemRef predicate) {
        return Binding.ref(HANDLES, predicate);
    }

    /**
     * Build a self-handling HANDLES binding — null target.  Asserts that the
     * manifest's owner processes frames headed by itself, without naming the
     * predicate redundantly.  Used by operators and other predicates that are
     * their own actors.
     *
     * <p>Shape: {@code @HANDLES → ∅}.
     */
    public static Binding handlesSelf() {
        return new Binding(HANDLES, null);
    }

    /**
     * Build an IMPLEMENTS binding declaring this manifest's owner is a
     * realization of {@code archetype}.
     *
     * <p>Shape: {@code @IMPLEMENTS → @<archetype>}.  Typically lives on a
     * code-item manifest pointing at the archetype whose contract it fulfills.
     */
    public static Binding implementsArchetype(ItemRef archetype) {
        return Binding.ref(IMPLEMENTS, archetype);
    }

    /**
     * The predicate IIDs this manifest declares it HANDLES, in binding order.
     *
     * <p>A null-target HANDLES binding (self-handling) yields {@code null} in
     * the returned list — callers that need to substitute the manifest's own
     * ITEM_ID can do so explicitly.
     */
    public List<ItemRef> handles() {
        return body().bindingsByRole(HANDLES).stream()
                .map(b -> b.target() == null ? null
                        : readIidFromTarget(b.target(), HANDLES_KEY))
                .toList();
    }

    /**
     * The archetype IIDs this manifest declares it IMPLEMENTS, in binding order.
     *
     * <p>A code item with multiple IMPLEMENTS bindings claims to realize each
     * named archetype.  Rare today; common for code items that span concept
     * boundaries (an adapter that implements both Source and Sink, etc.).
     */
    public List<ItemRef> implementsArchetypes() {
        return body().bindingsByRole(IMPLEMENTS).stream()
                .map(b -> readIidFromTarget(b.target(), IMPLEMENTS_KEY))
                .toList();
    }

    /**
     * Read an ItemRef from a binding target, expecting an ItemRef.
     */
    private static ItemRef readIidFromTarget(Object target, String role) {
        if (target instanceof ItemRef ir) return ir;
        throw new IllegalStateException(
                role + " binding target must be an ItemRef, got "
                        + target.getClass().getSimpleName());
    }

    /**
     * Read a DatumRef from a binding target, expecting a DatumRef (the typical
     * FOLLOWS/ENDORSES shape — a body CID). Accepts ItemRef as a transitional
     * fallback for legacy bindings.
     */
    private static DatumRef readDatumIdFromTarget(Object target, String role) {
        if (target instanceof DatumRef dr) return dr;
        if (target instanceof ItemRef ir) return new DatumRef(ir.encodeBinary());
        throw new IllegalStateException(
                role + " binding target must be a DatumRef, got "
                        + target.getClass().getSimpleName());
    }
}
