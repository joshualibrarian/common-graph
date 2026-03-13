package dev.everydaythings.graph.frame;

import dev.everydaythings.graph.Canonical;
import dev.everydaythings.graph.item.id.ItemID;
import lombok.Getter;

import java.util.List;
import java.util.Objects;

/**
 * A single role binding within a frame.
 *
 * <p>A binding fills a semantic role with a target value. The target can be
 * a CID (content reference), a stream head, an item reference, an inline
 * literal, or a local path. The predicate and role define what the target
 * means; the binding carries the actual value.
 *
 * <p>Two orthogonal flags control behavior:
 * <ul>
 *   <li><b>identity</b> — does this binding contribute to the frame's body hash?
 *       Non-identity bindings ride along but don't affect version identity.
 *       Example: a chess game's move log head (identity) vs. last-viewed timestamp (non-identity).</li>
 *   <li><b>index</b> — does this binding's target create a reverse-lookup entry
 *       in the FRAME_BY_ITEM index? Controls discoverability without affecting identity.</li>
 * </ul>
 *
 * <p>The {@code instance} field is the live decoded runtime value — the actual
 * Java object this binding resolves to. It is transient (never serialized).
 * When a frame is hydrated, each binding's target is decoded and the result
 * stored here. This is how frames carry live state: the binding IS the
 * runtime container.
 *
 * @see FrameBody
 * @see BindingTarget
 */
@Getter
public final class Binding implements Canonical {

    /** Compound role key — semantic address within the frame. */
    @Canon(order = 0)
    private final List<ItemID> key;

    /** The bound value — CID, item ref, literal, or path. */
    @Canon(order = 1)
    private final BindingTarget target;

    /** Does this binding contribute to the frame's body hash? */
    @Canon(order = 2)
    private final boolean identity;

    /** Does this binding's target create a reverse-lookup index entry? */
    @Canon(order = 3)
    private final boolean index;

    /** Live decoded value (transient, runtime only). */
    private transient Object instance;

    public Binding(List<ItemID> key, BindingTarget target, boolean identity, boolean index) {
        this.key = key != null ? List.copyOf(key) : List.of();
        this.target = Objects.requireNonNull(target, "target");
        this.identity = identity;
        this.index = index;
    }

    /**
     * Simple single-key binding (most common case).
     */
    public Binding(ItemID role, BindingTarget target, boolean identity, boolean index) {
        this(List.of(Objects.requireNonNull(role, "role")), target, identity, index);
    }

    /**
     * Simple single-key binding with default flags (identity=true, index=false).
     */
    public Binding(ItemID role, BindingTarget target) {
        this(role, target, true, false);
    }

    /**
     * No-arg constructor for Canonical decode support.
     */
    @SuppressWarnings("unused")
    private Binding() {
        this.key = null;
        this.target = null;
        this.identity = true;
        this.index = false;
    }

    // ==================================================================================
    // Instance Management
    // ==================================================================================

    /**
     * Set the live decoded instance for this binding.
     */
    public void setInstance(Object instance) {
        this.instance = instance;
    }

    /**
     * Get the live decoded instance, cast to the expected type.
     */
    @SuppressWarnings("unchecked")
    public <T> T instance(Class<T> type) {
        return type.isInstance(instance) ? (T) instance : null;
    }

    // ==================================================================================
    // Key Accessors
    // ==================================================================================

    /**
     * The primary role (first element of the compound key).
     */
    public ItemID role() {
        return key != null && !key.isEmpty() ? key.getFirst() : null;
    }

    /**
     * Whether this is a simple (single-element) key.
     */
    public boolean isSimpleKey() {
        return key != null && key.size() == 1;
    }

    // ==================================================================================
    // Target Convenience
    // ==================================================================================

    /**
     * If the target is an IidTarget, return the referenced ItemID.
     */
    public ItemID targetId() {
        return target instanceof BindingTarget.IidTarget iid ? iid.iid() : null;
    }

    // ==================================================================================
    // Factories
    // ==================================================================================

    /**
     * Create an identity binding referencing an item.
     */
    public static Binding ref(ItemID role, ItemID target) {
        return new Binding(role, BindingTarget.iid(target));
    }

    /**
     * Create an identity binding with a literal value.
     */
    public static Binding literal(ItemID role, BindingTarget target) {
        return new Binding(role, target);
    }

    /**
     * Create a binding with explicit flags.
     */
    public static Binding of(ItemID role, BindingTarget target, boolean identity, boolean index) {
        return new Binding(role, target, identity, index);
    }

    /**
     * Create a non-identity binding (doesn't affect body hash).
     */
    public static Binding nonIdentity(ItemID role, BindingTarget target) {
        return new Binding(role, target, false, false);
    }

    /**
     * Create an indexed binding (creates reverse-lookup entry).
     */
    public static Binding indexed(ItemID role, BindingTarget target) {
        return new Binding(role, target, true, true);
    }

    // ==================================================================================
    // Display
    // ==================================================================================

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("Binding{");
        if (key != null && !key.isEmpty()) {
            sb.append(key.getFirst().displayAtWidth(12));
            if (key.size() > 1) sb.append("+").append(key.size() - 1);
        }
        sb.append(" -> ").append(target);
        if (!identity) sb.append(" [non-id]");
        if (index) sb.append(" [indexed]");
        if (instance != null) sb.append(" [live]");
        sb.append('}');
        return sb.toString();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Binding other)) return false;
        return identity == other.identity
                && index == other.index
                && Objects.equals(key, other.key)
                && Objects.equals(target, other.target);
    }

    @Override
    public int hashCode() {
        return Objects.hash(key, target, identity, index);
    }
}
