package dev.everydaythings.graph.datum;

import dev.everydaythings.graph.ref.CompoundKey;
import dev.everydaythings.graph.ref.CompoundKey.Qualifier;
import dev.everydaythings.graph.ref.ItemRef;
import dev.everydaythings.graph.cryptography.Signer;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Fluent sub-builder for a single binding with qualifiers (compound key).
 *
 * <p>Returned by {@link DatumBuilder#binding(ItemRef)}.  Accumulates qualifiers
 * via {@link #qualifier(ItemRef)} / {@link #qualifier(String)}, an optional
 * target via {@link #target(ItemRef)} / {@link #target(String)} / etc., and an
 * optional ordinal {@link #index(long)}.
 *
 * <p>The binding does not need to be explicitly closed for the common
 * chain-terminators.  {@link #build()}, {@link #record(Signer)}, and {@link
 * #binding(ItemRef)} all auto-close the current binding (materializing it
 * into the parent's bindings list) before delegating to the parent.  For any
 * other parent method, call {@link #done()} to escape back to the typed
 * parent reference.
 *
 * <p>Leaving the target unset is permitted (for query-shaped frames, where a
 * binding without a target acts as a wildcard match).  Body-shaped builders
 * may later validate that all bindings have targets; this builder doesn't
 * enforce.
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
    // Binding-state setters
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
        this.target = n;
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

    /** Set the binding's target (explicit object — any supported target type). */
    public BindingBuilder<P> target(Object t) {
        this.target = Objects.requireNonNull(t, "target");
        return this;
    }

    /**
     * Set this binding's ordinal position within an ordered same-compound-key
     * group.  Optional — leave unset for unordered bindings.
     */
    public BindingBuilder<P> index(long position) {
        this.index = position;
        return this;
    }

    // ==================================================================================
    // Chain terminators / parent escape
    // ==================================================================================

    /**
     * Close this binding and return the typed parent builder.  Use this to
     * continue with any parent method not covered by the convenience
     * forwarders ({@link #binding(ItemRef)}, {@link #build()},
     * {@link #record(Signer)}).
     */
    public P done() {
        return seal();
    }

    /**
     * Open a new binding for {@code role} on the parent.  Auto-closes this
     * binding first.  Convenience for the common "chain straight into the
     * next compound-key binding" pattern.
     */
    public BindingBuilder<P> binding(ItemRef role) {
        return seal().binding(role);
    }

    /**
     * Open a record sub-builder on the parent.  Auto-closes this binding
     * first.  Valid only when the parent supports records ({@link
     * AttributedBodyBuilder} subclasses, {@link RecordBuilder}); bare-body
     * builders ({@link BodyBuilder}) reject this.
     */
    public Object record(Signer signer) {
        P p = seal();
        if (p instanceof AttributedBodyBuilder<?, ?> ab) {
            return ab.record(signer);
        }
        if (p instanceof RecordBuilder<?, ?> rb) {
            return rb.record(signer);
        }
        throw new IllegalStateException(
                "record() invalid on parent: " + p.getClass().getSimpleName());
    }

    /**
     * Build the parent's result.  Auto-closes this binding first.  Dispatches
     * over the parent's concrete type to its own {@code build()}.
     */
    public Object build() {
        P p = seal();
        if (p instanceof BodyBuilder bb) {
            return bb.build();
        }
        if (p instanceof AttributedBodyBuilder<?, ?> ab) {
            return ab.build();
        }
        if (p instanceof RecordBuilder<?, ?> rb) {
            return rb.build();
        }
        throw new IllegalStateException(
                "build() invalid on parent: " + p.getClass().getSimpleName());
    }

    // ==================================================================================
    // Internal — materialize this binding into the parent's bindings list
    // ==================================================================================

    Binding materialize() {
        return index != null
                ? Binding.qualified(role, qualifiers, target, index)
                : Binding.qualified(role, qualifiers, target);
    }

    private P seal() {
        parent.closeOpenBinding();   // parent will find `this` as openBinding and materialize it
        return parent;
    }
}
