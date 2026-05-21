package dev.everydaythings.graph.item;


import dev.everydaythings.graph.CoreVocabulary;
import dev.everydaythings.graph.SchemaVocabulary;
import dev.everydaythings.graph.datum.AttributedBody;
import dev.everydaythings.graph.datum.Binding;
import dev.everydaythings.graph.datum.Body;
import dev.everydaythings.graph.datum.Record;
import dev.everydaythings.graph.ref.CompoundKey;
import dev.everydaythings.graph.ref.DatumRef;
import dev.everydaythings.graph.ref.HashID;
import dev.everydaythings.graph.ref.ItemRef;
import dev.everydaythings.graph.runtime.RuntimeVocabulary;

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

    /** Canonical key for the CONFIG structural sememe. */
    public static final String CONFIG_KEY = CoreVocabulary.Config.KEY;

    /** ItemRef of the structural CONFIG sememe. */
    public static final ItemRef CONFIG = ItemRef.iid(CoreVocabulary.Config.KEY);

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
     * Implementation reference — the first binding whose role is a known runtime
     * language sememe (Java, Python, Lisp, JavaScript, Clojure, Rust).
     *
     * <p>A manifest can have multiple implementation bindings under different
     * language roles (e.g., {@code JAVA:[ClassName]} alongside
     * {@code PYTHON:[SourceCode]}). This convenience returns the first one;
     * callers needing all variants should use {@link #implementations()} or
     * the language-specific filter {@link #implementationFor(ItemRef)}.
     *
     * <p>Returns empty if no implementation is declared.
     */
    public Optional<Binding> implementation() {
        for (Binding b : body().bindings()) {
            if (isLanguageRole(b.role())) return Optional.of(b);
        }
        return Optional.empty();
    }

    /**
     * All implementation bindings on this manifest — bindings whose role is a
     * known runtime language sememe.
     */
    public List<Binding> implementations() {
        return body().bindings().stream()
                .filter(b -> isLanguageRole(b.role()))
                .toList();
    }

    /**
     * Find the implementation binding for a specific language, if any.
     *
     * <p>Example: {@code manifest.implementationFor(ItemRef.iid(RuntimeVocabulary.Java.KEY))}
     * returns the Java implementation binding (if this manifest declares one).
     */
    public Optional<Binding> implementationFor(ItemRef language) {
        return body().bindingsByRole(language).stream().findFirst();
    }

    /** Whether this role is one of the known runtime language sememes. */
    private static boolean isLanguageRole(HashID role) {
        return ItemRef.iid(RuntimeVocabulary.Java.KEY).equals(role)
                || ItemRef.iid(RuntimeVocabulary.Python.KEY).equals(role)
                || ItemRef.iid(RuntimeVocabulary.Lisp.KEY).equals(role)
                || ItemRef.iid(RuntimeVocabulary.JavaScript.KEY).equals(role)
                || ItemRef.iid(RuntimeVocabulary.Clojure.KEY).equals(role)
                || ItemRef.iid(RuntimeVocabulary.Rust.KEY).equals(role);
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
     * sememe ({@link RuntimeVocabulary.Java}, {@link RuntimeVocabulary.Python},
     * {@link RuntimeVocabulary.Lisp}, etc.); the qualifier is the form
     * ({@link RuntimeVocabulary.ClassName} for class identifiers,
     * {@link RuntimeVocabulary.SourceCode} for source text); the target is the
     * actual reference (text for class/source, CID for bytecode eventually).
     *
     * <p>Examples:
     * <pre>
     * Manifest.implementation(Java.IID,   ClassName.IID,  "com.example.AddJava")
     * Manifest.implementation(Python.IID, SourceCode.IID, "def evaluate(b): ...")
     * Manifest.implementation(Lisp.IID,   SourceCode.IID, "(defun evaluate ...)")
     * </pre>
     *
     * <p>The whole item is the implementation; this binding just declares the
     * language and the actual code reference within it. There is no separate
     * {@code IMPLEMENTATION} role — the language sememe occupies that slot.
     */
    public static Binding implementation(ItemRef language, ItemRef form, Object target) {
        return new Binding(
                language,
                List.of(new CompoundKey.Sememe(form)),
                target);
    }

    /**
     * Java convenience: build an implementation binding for the given Java class.
     *
     * <p>Shape: {@code JAVA:[ClassName] → fqcn}.
     */
    public static Binding implementation(Class<?> clazz) {
        return implementation(
                ItemRef.iid(RuntimeVocabulary.Java.KEY),
                ItemRef.iid(RuntimeVocabulary.ClassName.KEY),
                clazz.getName());
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
     * Whether a binding is a Java implementation binding under the new shape —
     * role is {@link RuntimeVocabulary.Java} and the qualifier list includes
     * {@link RuntimeVocabulary.ClassName}.
     */
    public static boolean isJavaImplementation(Binding b) {
        if (!ItemRef.iid(RuntimeVocabulary.Java.KEY).equals(b.role())) {
            return false;
        }
        for (var q : b.qualifiers()) {
            if (q instanceof CompoundKey.Sememe s
                    && ItemRef.iid(RuntimeVocabulary.ClassName.KEY).equals(s.id())) {
                return true;
            }
        }
        return false;
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
