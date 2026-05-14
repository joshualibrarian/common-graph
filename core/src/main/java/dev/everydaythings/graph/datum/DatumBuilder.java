package dev.everydaythings.graph.datum;

import dev.everydaythings.graph.item.ManifestBuilder;
import dev.everydaythings.graph.value.Literal;
import dev.everydaythings.graph.id.ItemID;
import dev.everydaythings.graph.semantics.ThematicRole;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Shared base for fluent builders that accumulate bindings into a Datum.
 *
 * <p>Subclasses: {@link BodyBuilder} (and its subclasses {@link FrameBuilder},
 * {@link ManifestBuilder}) for top-level body construction; {@link RecordBuilder}
 * for record bindings nested inside a body builder.
 *
 * <p>Role-helper methods (e.g., {@link #theme(ItemID)}, {@link #agent(ItemID)})
 * append bindings to the current builder's list. Any open {@link BindingBuilder}
 * sub-builder is materialized into the list first.
 *
 * <p>The {@code SELF} type parameter enables proper return types for chaining
 * on subclasses (Curiously Recurring Template Pattern in Java's flavor).
 */
public abstract class DatumBuilder<SELF extends DatumBuilder<SELF>> {

    /** Accumulated bindings for the Datum being built. */
    protected final List<Binding> bindings = new ArrayList<>();

    /** A currently-open binding sub-builder, or {@code null}. */
    protected BindingBuilder<SELF> openBinding;

    @SuppressWarnings("unchecked")
    SELF self() { return (SELF) this; }

    /**
     * Close any open binding sub-builder, materializing it into this builder's
     * bindings list. Idempotent.
     */
    void closeOpenBinding() {
        if (openBinding != null) {
            BindingBuilder<SELF> b = openBinding;
            openBinding = null;
            bindings.add(b.materialize());
        }
    }

    /** Internal: record a fully-built binding directly. */
    void addBindingDirect(Binding b) { bindings.add(b); }

    // ==================================================================================
    // Role helpers — common thematic roles
    //
    // Each role helper auto-closes any open binding sub-builder, then appends a new
    // simple binding (no qualifiers) with the role and the given target.
    // ==================================================================================

    public SELF theme(ItemID target)      { return withSimple(ThematicRole.Theme.IID, BindingTarget.iid(target)); }
    public SELF theme(String text)        { return withSimple(ThematicRole.Theme.IID, Literal.ofText(text)); }
    public SELF theme(long value)         { return withSimple(ThematicRole.Theme.IID, Literal.ofInteger(value)); }
    public SELF theme(BindingTarget t)    { return withSimple(ThematicRole.Theme.IID, t); }

    public SELF agent(ItemID target)      { return withSimple(ThematicRole.Agent.IID, BindingTarget.iid(target)); }
    public SELF agent(BindingTarget t)    { return withSimple(ThematicRole.Agent.IID, t); }

    public SELF location(ItemID target)   { return withSimple(ThematicRole.Location.IID, BindingTarget.iid(target)); }
    public SELF location(BindingTarget t) { return withSimple(ThematicRole.Location.IID, t); }

    public SELF goal(ItemID target)       { return withSimple(ThematicRole.Goal.IID, BindingTarget.iid(target)); }
    public SELF goal(BindingTarget t)     { return withSimple(ThematicRole.Goal.IID, t); }

    public SELF source(ItemID target)     { return withSimple(ThematicRole.Source.IID, BindingTarget.iid(target)); }
    public SELF source(BindingTarget t)   { return withSimple(ThematicRole.Source.IID, t); }

    public SELF value(ItemID target)      { return withSimple(ThematicRole.Value.IID, BindingTarget.iid(target)); }
    public SELF value(String text)        { return withSimple(ThematicRole.Value.IID, Literal.ofText(text)); }
    public SELF value(long n)             { return withSimple(ThematicRole.Value.IID, Literal.ofInteger(n)); }
    public SELF value(boolean b)          { return withSimple(ThematicRole.Value.IID, Literal.ofBoolean(b)); }
    public SELF value(BindingTarget t)    { return withSimple(ThematicRole.Value.IID, t); }

    public SELF time(Instant instant)     { return withSimple(ThematicRole.Time.IID, Literal.ofInstant(instant)); }
    public SELF time(BindingTarget t)     { return withSimple(ThematicRole.Time.IID, t); }

    public SELF instrument(ItemID target) { return withSimple(ThematicRole.Instrument.IID, BindingTarget.iid(target)); }
    public SELF instrument(BindingTarget t) { return withSimple(ThematicRole.Instrument.IID, t); }

    public SELF recipient(ItemID target)  { return withSimple(ThematicRole.Recipient.IID, BindingTarget.iid(target)); }
    public SELF recipient(BindingTarget t){ return withSimple(ThematicRole.Recipient.IID, t); }

    public SELF topic(ItemID target)      { return withSimple(ThematicRole.Topic.IID, BindingTarget.iid(target)); }
    public SELF topic(BindingTarget t)    { return withSimple(ThematicRole.Topic.IID, t); }

    // ==================================================================================
    // Generic with()
    // ==================================================================================

    /** Add a simple binding (no qualifiers) with the given role and target. */
    public SELF with(ItemID role, ItemID target) {
        return withSimple(role, BindingTarget.iid(target));
    }

    /** Add a simple binding with a text literal target. */
    public SELF with(ItemID role, String text) {
        return withSimple(role, Literal.ofText(text));
    }

    /** Add a simple binding with an integer literal target. */
    public SELF with(ItemID role, long n) {
        return withSimple(role, Literal.ofInteger(n));
    }

    /** Add a simple binding with a boolean literal target. */
    public SELF with(ItemID role, boolean b) {
        return withSimple(role, Literal.ofBoolean(b));
    }

    /** Add a simple binding with an instant literal target. */
    public SELF with(ItemID role, Instant t) {
        return withSimple(role, Literal.ofInstant(t));
    }

    /** Add a simple binding with an explicit target. */
    public SELF with(ItemID role, BindingTarget target) {
        return withSimple(role, target);
    }

    /** Add a pre-built binding. */
    public SELF with(Binding b) {
        Objects.requireNonNull(b, "binding");
        closeOpenBinding();
        bindings.add(b);
        return self();
    }

    private SELF withSimple(ItemID role, BindingTarget target) {
        Objects.requireNonNull(role, "role");
        Objects.requireNonNull(target, "target");
        closeOpenBinding();
        bindings.add(new Binding(role, target));
        return self();
    }

    // ==================================================================================
    // Nested binding (for qualifiers)
    // ==================================================================================

    /**
     * Open a binding sub-builder for the given role. Use this when the binding
     * needs qualifiers (compound key). Auto-closes any prior open binding.
     *
     * <p>The returned {@link BindingBuilder} exposes {@link BindingBuilder#qualifier},
     * {@link BindingBuilder#target}, plus forwarding methods for every parent-shape
     * method so the chain can continue without an explicit close.
     */
    public BindingBuilder<SELF> binding(ItemID role) {
        Objects.requireNonNull(role, "role");
        closeOpenBinding();
        openBinding = new BindingBuilder<>(self(), role);
        return openBinding;
    }
}
