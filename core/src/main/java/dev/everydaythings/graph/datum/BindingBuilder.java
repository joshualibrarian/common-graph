package dev.everydaythings.graph.datum;

import dev.everydaythings.graph.id.CompoundKey;
import dev.everydaythings.graph.id.CompoundKey.Qualifier;
import dev.everydaythings.graph.id.ItemRef;
import dev.everydaythings.graph.identity.Signer;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Fluent sub-builder for a single binding with qualifiers (compound key).
 *
 * <p>Returned by {@link DatumBuilder#binding(ItemRef)}. Accumulates qualifiers via
 * {@link #qualifier(ItemRef)} / {@link #qualifier(String)} and an optional target
 * via {@link #target(ItemRef)} / {@link #target(String)} / etc.
 *
 * <p>The binding does not need to be explicitly closed. Any parent-shape method
 * invoked on this builder auto-closes the current binding (materializing it
 * into the parent's bindings list) and forwards the call to the parent. So
 * {@code .binding(R).qualifier(Q).target(T).theme(other)} reads naturally —
 * {@code .theme(other)} closes the binding and adds a theme to the parent.
 *
 * <p>Leaving the target unset is permitted (for query-shaped frames, where a
 * binding without a target acts as a wildcard match). Body-shaped builders may
 * later validate that all bindings have targets; this builder doesn't enforce.
 */
public final class BindingBuilder<P extends DatumBuilder<P>> {

    private final P parent;
    private final ItemRef role;
    private final List<Qualifier> qualifiers = new ArrayList<>();
    private Object target;
    private Long index;

    BindingBuilder(P parent, ItemRef role) {
        this.parent = parent;
        this.role = role;
    }

    // ==================================================================================
    // Builder-specific setters
    // ==================================================================================

    /** Add a sememe qualifier to this binding's compound key. */
    public BindingBuilder<P> qualifier(ItemRef sememe) {
        qualifiers.add(new CompoundKey.Sememe(Objects.requireNonNull(sememe, "sememe")));
        return this;
    }

    /** Add a string-literal qualifier (for local variables in queries). */
    public BindingBuilder<P> qualifier(String literal) {
        qualifiers.add(new CompoundKey.Text(Objects.requireNonNull(literal, "literal")));
        return this;
    }

    /** Set the binding's target (item reference). */
    public BindingBuilder<P> target(ItemRef target) {
        this.target = Objects.requireNonNull(target, "target");
        return this;
    }

    /** Set the binding's target (text literal). */
    public BindingBuilder<P> target(String text) {
        this.target = Objects.requireNonNull(text, "text");
        return this;
    }

    /** Set the binding's target (integer literal). */
    public BindingBuilder<P> target(long n) {
        this.target = (long) (n);
        return this;
    }

    /** Set the binding's target (boolean literal). */
    public BindingBuilder<P> target(boolean b) {
        this.target = b;
        return this;
    }

    /** Set the binding's target (instant literal). */
    public BindingBuilder<P> target(Instant t) {
        this.target = Objects.requireNonNull(t, "instant");
        return this;
    }

    /** Set the binding's target (explicit BindingTarget). */
    public BindingBuilder<P> target(Object t) {
        this.target = Objects.requireNonNull(t, "target");
        return this;
    }

    /**
     * Set this binding's ordinal position within an ordered same-compound-key
     * group. Optional — leave unset for unordered bindings.
     */
    public BindingBuilder<P> index(long position) {
        this.index = position;
        return this;
    }

    // ==================================================================================
    // Internal: materialize this binding (called by parent.closeOpenBinding)
    // ==================================================================================

    Binding materialize() {
        // Target may be null (query-style). Use a sentinel-free Binding that allows null
        // target if needed; for now, require non-null for assertion-mode builders.
        // Phase 1: tolerate null target; downstream may reject.
        return new Binding(role, qualifiers, target, index);
    }

    // ==================================================================================
    // Forwarding to parent — auto-closes this binding, then delegates
    // ==================================================================================

    private P seal() {
        parent.closeOpenBinding();   // parent will find `this` as openBinding and materialize it
        return parent;
    }

    // Role helpers (forward to parent)
    public P theme(ItemRef t)          { return seal().theme(t); }
    public P theme(String t)          { return seal().theme(t); }
    public P theme(long n)            { return seal().theme(n); }
    public P theme(Object t)   { return seal().theme(t); }
    public P agent(ItemRef t)          { return seal().agent(t); }
    public P agent(Object t)   { return seal().agent(t); }
    public P location(ItemRef t)       { return seal().location(t); }
    public P location(Object t){ return seal().location(t); }
    public P goal(ItemRef t)           { return seal().goal(t); }
    public P goal(Object t)    { return seal().goal(t); }
    public P source(ItemRef t)         { return seal().source(t); }
    public P source(Object t)  { return seal().source(t); }
    public P value(ItemRef t)          { return seal().value(t); }
    public P value(String t)          { return seal().value(t); }
    public P value(long n)            { return seal().value(n); }
    public P value(boolean b)         { return seal().value(b); }
    public P value(Object t)   { return seal().value(t); }
    public P time(Instant t)          { return seal().time(t); }
    public P time(Object t)    { return seal().time(t); }
    public P instrument(ItemRef t)     { return seal().instrument(t); }
    public P instrument(Object t) { return seal().instrument(t); }
    public P recipient(ItemRef t)      { return seal().recipient(t); }
    public P recipient(Object t) { return seal().recipient(t); }
    public P topic(ItemRef t)          { return seal().topic(t); }
    public P topic(Object t)   { return seal().topic(t); }

    // Generic with()
    public P with(ItemRef role, ItemRef target)        { return seal().with(role, target); }
    public P with(ItemRef role, String text)          { return seal().with(role, text); }
    public P with(ItemRef role, long n)               { return seal().with(role, n); }
    public P with(ItemRef role, boolean b)            { return seal().with(role, b); }
    public P with(ItemRef role, Instant t)            { return seal().with(role, t); }
    public P with(ItemRef role, Object target) { return seal().with(role, target); }
    public P with(Binding b)                         { return seal().with(b); }

    // Open a new binding (closes self, opens new)
    public BindingBuilder<P> binding(ItemRef role)    { return seal().binding(role); }

    // Forwarding to BodyBuilder-only methods (record, build) —
    // these require the parent to be a BodyBuilder. They cast and delegate.
    // If the parent isn't a BodyBuilder (e.g., RecordBuilder), the cast still
    // works because RecordBuilder also exposes record() and build() via delegation.

    /**
     * Open a record sub-builder. Auto-closes this binding first. Only valid if
     * the parent is a {@link BodyBuilder} or a {@link RecordBuilder} (which
     * itself delegates record() to its body parent).
     */
    public Object record(Signer signer) {
        P p = seal();
        if (p instanceof BodyBuilder<?, ?> bb) {
            return bb.record(signer);
        }
        if (p instanceof RecordBuilder<?, ?> rb) {
            return rb.record(signer);
        }
        throw new IllegalStateException(
                "record() invalid on this parent: " + p.getClass().getSimpleName());
    }

    /**
     * Build the parent. Auto-closes this binding first. Only valid if the
     * parent is a {@link BodyBuilder} or a {@link RecordBuilder}.
     */
    public Object build() {
        P p = seal();
        if (p instanceof BodyBuilder<?, ?> bb) {
            return bb.build();
        }
        if (p instanceof RecordBuilder<?, ?> rb) {
            return rb.build();
        }
        throw new IllegalStateException(
                "build() invalid on this parent: " + p.getClass().getSimpleName());
    }
}
