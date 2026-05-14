package dev.everydaythings.graph.item;

import dev.everydaythings.graph.CoreVocabulary;
import dev.everydaythings.graph.datum.AttributedBody;
import dev.everydaythings.graph.datum.Binding;
import dev.everydaythings.graph.datum.BindingTarget;
import dev.everydaythings.graph.datum.Body;
import dev.everydaythings.graph.datum.Record;
import dev.everydaythings.graph.id.CompoundKey;
import dev.everydaythings.graph.id.DatumID;
import dev.everydaythings.graph.id.FrameRef;
import dev.everydaythings.graph.id.ItemID;
import dev.everydaythings.graph.value.Literal;

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
 *   <tr><td>{@code IMPLEMENTATION}</td><td>Reference to the implementation that produced this version</td></tr>
 *   <tr><td>{@code CONFIG:[...]}</td><td>Per-purpose configuration (retention, presentation, etc.)</td></tr>
 * </table>
 */
public final class Manifest extends AttributedBody {

    // Aliases to the structural sememes defined in CoreVocabulary — preserved here
    // for callers that reference Manifest.ITEM_ID, Manifest.FOLLOWS, etc., directly.

    /** Canonical key for the ITEM_ID structural sememe. */
    public static final String ITEM_ID_KEY = CoreVocabulary.ItemId.KEY;

    /** ItemID of the structural ITEM_ID sememe. */
    public static final ItemID ITEM_ID = CoreVocabulary.ItemId.IID;

    /** Canonical key for the FOLLOWS structural sememe. */
    public static final String FOLLOWS_KEY = CoreVocabulary.Follows.KEY;

    /** ItemID of the structural FOLLOWS sememe. */
    public static final ItemID FOLLOWS = CoreVocabulary.Follows.IID;

    /** Canonical key for the ENDORSES structural sememe. */
    public static final String ENDORSES_KEY = CoreVocabulary.Endorses.KEY;

    /** ItemID of the structural ENDORSES sememe. */
    public static final ItemID ENDORSES = CoreVocabulary.Endorses.IID;

    /** Canonical key for the IMPLEMENTATION structural sememe. */
    public static final String IMPLEMENTATION_KEY = CoreVocabulary.Implementation.KEY;

    /** ItemID of the structural IMPLEMENTATION sememe. */
    public static final ItemID IMPLEMENTATION = CoreVocabulary.Implementation.IID;

    /** Canonical key for the CONFIG structural sememe. */
    public static final String CONFIG_KEY = CoreVocabulary.Config.KEY;

    /** ItemID of the structural CONFIG sememe. */
    public static final ItemID CONFIG = CoreVocabulary.Config.IID;

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
    public static ManifestBuilder compose(ItemID archetype, ItemID itemId) {
        return new ManifestBuilder(archetype, itemId);
    }

    /**
     * The item's identity — read from the {@code ITEM_ID} binding.
     */
    public ItemID itemId() {
        Binding b = body().binding(CompoundKey.of(ITEM_ID))
                .orElseThrow(() -> new IllegalStateException(
                        "Manifest body missing ITEM_ID binding (should have been validated at construction)"));
        return readIidFromTarget(b.target(), ITEM_ID_KEY);
    }

    /**
     * The version ID — the body's structural identity (DatumID).
     */
    public DatumID versionId() {
        return body().datumId();
    }

    /**
     * Parent version IDs — read from {@code FOLLOWS} bindings.
     *
     * <p>Returns an empty list for an inception manifest (no parents).
     * Multi-IID FOLLOWS indicates a merge of multiple parent versions.
     */
    public List<DatumID> parents() {
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
     * Implementation reference — the first binding with role {@code IMPLEMENTATION},
     * regardless of qualifiers.
     *
     * <p>A manifest can have multiple IMPLEMENTATION bindings narrowed by different
     * qualifiers (e.g., {@code IMPLEMENTATION:[JavaClass]} alongside
     * {@code IMPLEMENTATION:[Wasm]}). This convenience returns the first one;
     * callers needing all variants should use {@link #implementations()}.
     *
     * <p>Returns empty if no implementation is declared.
     */
    public Optional<Binding> implementation() {
        List<Binding> all = body().bindingsByRole(IMPLEMENTATION);
        return all.isEmpty() ? Optional.empty() : Optional.of(all.get(0));
    }

    /**
     * All IMPLEMENTATION bindings on this manifest, regardless of qualifiers.
     * Callers filter by qualifier (e.g., {@link #isJavaClassBinding}) to pick the
     * right implementation form.
     */
    public List<Binding> implementations() {
        return body().bindingsByRole(IMPLEMENTATION);
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
     * Build an IMPLEMENTATION binding pointing at the given Java class. Used by
     * {@link dev.everydaythings.graph.item.Item#commit} to declare which Java
     * class should hydrate this item's manifests, and by callers who want to
     * include it explicitly in their bindings.
     *
     * <p>Shape: {@code IMPLEMENTATION:[JavaClass] → text(fqcn)}. The qualifier
     * names the binding's semantic narrowing — "this text is a Java class name."
     * The target is plain text; encoding-primitive typing (text vs binary) is
     * CBOR's job, semantic narrowing is the qualifier's job.
     */
    public static Binding javaImplementation(Class<?> clazz) {
        return new Binding(
                IMPLEMENTATION,
                java.util.List.of(new CompoundKey.Sememe(
                        CoreVocabulary.JavaClass.IID)),
                Literal.ofText(clazz.getName()));
    }

    /**
     * Whether a binding is a Java-class binding — a binding whose qualifiers
     * include {@link CoreVocabulary.JavaClass}. Readers consult this to decide
     * whether to interpret the binding's text target as a fully-qualified Java
     * class name.
     */
    public static boolean isJavaClassBinding(Binding b) {
        for (var q : b.qualifiers()) {
            if (q instanceof CompoundKey.Sememe s
                    && CoreVocabulary.JavaClass.IID.equals(s.id())) {
                return true;
            }
        }
        return false;
    }

    /**
     * Read an ItemID from a binding target, expecting a reference target shape.
     */
    private static ItemID readIidFromTarget(BindingTarget target, String role) {
        if (target instanceof BindingTarget.RefTarget refTarget) {
            return refTarget.asItemId();
        }
        throw new IllegalStateException(
                role + " binding target must be a reference, got "
                        + target.getClass().getSimpleName());
    }

    /**
     * Read a DatumID from a binding target, expecting a {@link FrameRef}-shaped
     * reference target (the typical FOLLOWS/ENDORSES shape — a body CID).
     */
    private static DatumID readDatumIdFromTarget(BindingTarget target, String role) {
        if (target instanceof BindingTarget.RefTarget refTarget) {
            DatumID datumId = refTarget.asDatumId();
            if (datumId != null) return datumId;
            // Backwards-compatible fallback: derive a DatumID from an ItemRef's
            // bytes (handles older callers that wrapped a CID as an ItemRef).
            ItemID iid = refTarget.asItemId();
            if (iid != null) return new DatumID(iid.encodeBinary());
        }
        throw new IllegalStateException(
                role + " binding target must be a frame reference, got "
                        + target.getClass().getSimpleName());
    }
}
