package dev.everydaythings.graph.item;

import dev.everydaythings.graph.frame.AttributedBody;
import dev.everydaythings.graph.frame.Binding;
import dev.everydaythings.graph.frame.BindingTarget;
import dev.everydaythings.graph.frame.Body;
import dev.everydaythings.graph.frame.Record;
import dev.everydaythings.graph.item.id.CompoundKey;
import dev.everydaythings.graph.item.id.ContentID;
import dev.everydaythings.graph.item.id.ItemID;

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

    /** Canonical key for the ITEM_ID structural sememe. */
    public static final String ITEM_ID_KEY = "cg.structural:item-id";

    /** ItemID of the structural ITEM_ID sememe. */
    public static final ItemID ITEM_ID = ItemID.fromString(ITEM_ID_KEY);

    /** Canonical key for the FOLLOWS structural sememe. */
    public static final String FOLLOWS_KEY = "cg.structural:follows";

    /** ItemID of the structural FOLLOWS sememe. */
    public static final ItemID FOLLOWS = ItemID.fromString(FOLLOWS_KEY);

    /** Canonical key for the ENDORSES structural sememe. */
    public static final String ENDORSES_KEY = "cg.structural:endorses";

    /** ItemID of the structural ENDORSES sememe. */
    public static final ItemID ENDORSES = ItemID.fromString(ENDORSES_KEY);

    /** Canonical key for the IMPLEMENTATION structural sememe. */
    public static final String IMPLEMENTATION_KEY = "cg.structural:implementation";

    /** ItemID of the structural IMPLEMENTATION sememe. */
    public static final ItemID IMPLEMENTATION = ItemID.fromString(IMPLEMENTATION_KEY);

    /** Canonical key for the CONFIG structural sememe. */
    public static final String CONFIG_KEY = "cg.structural:config";

    /** ItemID of the structural CONFIG sememe. */
    public static final ItemID CONFIG = ItemID.fromString(CONFIG_KEY);

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
     * The item's identity — read from the {@code ITEM_ID} binding.
     */
    public ItemID itemId() {
        Binding b = body().binding(CompoundKey.of(ITEM_ID))
                .orElseThrow(() -> new IllegalStateException(
                        "Manifest body missing ITEM_ID binding (should have been validated at construction)"));
        return readIidFromTarget(b.target(), ITEM_ID_KEY);
    }

    /**
     * The version ID — equivalent to the body's CID.
     */
    public ContentID versionId() {
        return bodyCID();
    }

    /**
     * Parent version IDs — read from {@code FOLLOWS} bindings.
     *
     * <p>Returns an empty list for an inception manifest (no parents).
     * Multi-IID FOLLOWS indicates a merge of multiple parent versions.
     */
    public List<ContentID> parents() {
        return body().bindingsByRole(FOLLOWS).stream()
                .map(b -> readCidFromTarget(b.target(), FOLLOWS_KEY))
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
     * Implementation reference — read from the {@code IMPLEMENTATION} binding.
     *
     * <p>Returns empty if no implementation is declared.
     */
    public Optional<Binding> implementation() {
        return body().binding(CompoundKey.of(IMPLEMENTATION));
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
     * Read an ItemID from a binding target, expecting a reference target shape.
     */
    private static ItemID readIidFromTarget(BindingTarget target, String role) {
        if (target instanceof BindingTarget.IidTarget iidTarget) {
            return iidTarget.iid();
        }
        if (target instanceof BindingTarget.RefTarget refTarget) {
            return refTarget.asItemId();
        }
        throw new IllegalStateException(
                role + " binding target must be a reference, got "
                        + target.getClass().getSimpleName());
    }

    /**
     * Read a ContentID from a binding target, expecting a reference target shape.
     */
    private static ContentID readCidFromTarget(BindingTarget target, String role) {
        if (target instanceof BindingTarget.RefTarget refTarget) {
            // RefTarget wraps a Ref; for FOLLOWS the target is conceptually a VID
            // (which is a ContentID). We return ContentID derived from the ref's bytes.
            return new ContentID(refTarget.asItemId().encodeBinary());
        }
        if (target instanceof BindingTarget.IidTarget iidTarget) {
            return new ContentID(iidTarget.iid().encodeBinary());
        }
        throw new IllegalStateException(
                role + " binding target must be a reference, got "
                        + target.getClass().getSimpleName());
    }
}
