package dev.everydaythings.graph.frame;

import dev.everydaythings.graph.item.Factory;

import com.upokecenter.cbor.CBORObject;
import com.upokecenter.cbor.CBORType;
import dev.everydaythings.graph.Canonical;
import dev.everydaythings.graph.item.id.ContentID;
import dev.everydaythings.graph.item.id.ItemID;
import lombok.Getter;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * The semantic assertion of a frame — the "what is being said."
 *
 * <p>A FrameBody contains the assertion fields: predicate, theme,
 * and bindings. Each binding controls whether it contributes to body
 * identity via its {@link Binding#isIdentity()} flag. Two identical
 * identity assertions from different signers produce the same body hash.
 *
 * <p>The body hash is computed from deterministic CBOR encoding of
 * predicate, theme, and identity bindings only. Non-identity bindings
 * ride along but don't affect the hash.
 *
 * <p>Each binding carries a transient {@code instance} field for live
 * decoded runtime state — the frame IS the runtime container.
 *
 * @see FrameRecord
 * @see FrameEndorsement
 * @see Binding
 */
@Getter
public final class FrameBody implements Canonical {

    /** Canonical type key for frame bodies. */
    public static final String TYPE_KEY = "cg:type/relation";

    /** Deterministic ItemID for the frame body type. */
    public static final ItemID TYPE_ID = ItemID.fromString(TYPE_KEY);

    /** The frame type — a sememe that names this kind of assertion. */
    private final ItemID predicate;

    /** What this frame is about — the item this assertion lives on. */
    private final ItemID theme;

    /** Role bindings (semantic, with identity/index flags and live instances). */
    private final List<Binding> frameBindings;

    /** Cached body hash. */
    private transient ContentID cachedHash;

    /** Cached body bytes (CBOR encoding). */
    private transient byte[] cachedBytes;

    // ==================================================================================
    // Constructors
    // ==================================================================================

    /**
     * Primary constructor with explicit Binding list.
     */
    public FrameBody(ItemID predicate, ItemID theme, List<Binding> frameBindings) {
        this.predicate = Objects.requireNonNull(predicate, "predicate");
        this.theme = Objects.requireNonNull(theme, "theme");
        this.frameBindings = frameBindings != null ? List.copyOf(frameBindings) : List.of();
    }

    /**
     * Backward-compatible constructor from Map (all bindings identity=true, index=false).
     */
    public FrameBody(ItemID predicate, ItemID theme, Map<ItemID, BindingTarget> bindings) {
        this.predicate = Objects.requireNonNull(predicate, "predicate");
        this.theme = Objects.requireNonNull(theme, "theme");
        if (bindings != null && !bindings.isEmpty()) {
            List<Binding> list = new ArrayList<>(bindings.size());
            for (var entry : bindings.entrySet()) {
                list.add(new Binding(entry.getKey(), entry.getValue()));
            }
            this.frameBindings = List.copyOf(list);
        } else {
            this.frameBindings = List.of();
        }
    }

    /**
     * Construct with no bindings.
     */
    public FrameBody(ItemID predicate, ItemID theme) {
        this.predicate = Objects.requireNonNull(predicate, "predicate");
        this.theme = Objects.requireNonNull(theme, "theme");
        this.frameBindings = List.of();
    }

    /**
     * No-arg constructor for Canonical decode support.
     */
    @SuppressWarnings("unused")
    private FrameBody() {
        this.predicate = null;
        this.theme = null;
        this.frameBindings = null;
    }

    // ==================================================================================
    // Hashing
    // ==================================================================================

    /**
     * The deterministic CBOR encoding of this body (for hashing).
     * Only identity bindings are included.
     */
    public byte[] bodyBytes() {
        if (cachedBytes == null) {
            cachedBytes = encodeBinary(Scope.BODY);
        }
        return cachedBytes;
    }

    /**
     * The content identity of this assertion.
     * Computed from predicate, theme, and identity bindings only.
     */
    public ContentID hash() {
        if (cachedHash == null) {
            cachedHash = ContentID.of(bodyBytes());
        }
        return cachedHash;
    }

    // ==================================================================================
    // CBOR Encoding (scope-aware)
    // ==================================================================================

    /**
     * Custom CBOR encoding: BODY scope includes only identity bindings,
     * RECORD scope includes all bindings.
     */
    @Override
    public CBORObject toCborTree(Scope scope) {
        CBORObject array = CBORObject.NewArray();
        array.Add(predicate != null ? predicate.toCborTree(scope) : CBORObject.Null);
        array.Add(theme != null ? theme.toCborTree(scope) : CBORObject.Null);

        CBORObject bindingsArray = CBORObject.NewArray();
        if (frameBindings != null) {
            for (Binding b : frameBindings) {
                if (scope == Scope.BODY && !b.identity()) continue;
                bindingsArray.Add(b.toCborTree(scope));
            }
        }
        array.Add(bindingsArray);
        return array;
    }

    /**
     * Decode from CBOR. Handles both new format (array of Binding) and
     * old format (map of ItemID → BindingTarget) for backward compat.
     */
    @Factory
    public static FrameBody fromCborTree(CBORObject node) {
        if (node == null || node.isNull()) return null;
        if (node.getType() != CBORType.Array || node.size() < 2) return null;

        ItemID pred = new ItemID(node.get(0).GetByteString());
        ItemID thm = new ItemID(node.get(1).GetByteString());

        List<Binding> bindings = new ArrayList<>();
        if (node.size() > 2) {
            CBORObject bindingsNode = node.get(2);
            if (bindingsNode != null && bindingsNode.getType() == CBORType.Array) {
                for (CBORObject bNode : bindingsNode.getValues()) {
                    Binding b = Canonical.fromCborTree(bNode, Binding.class, Scope.RECORD);
                    if (b != null) bindings.add(b);
                }
            } else if (bindingsNode != null && bindingsNode.getType() == CBORType.Map) {
                // Backward compat: old format was Map<ItemID, BindingTarget>
                for (CBORObject key : bindingsNode.getKeys()) {
                    ItemID role = new ItemID(key.GetByteString());
                    BindingTarget target = BindingTarget.fromCborTree(bindingsNode.get(key));
                    if (role != null && target != null) {
                        bindings.add(new Binding(role, target));
                    }
                }
            }
        }

        return new FrameBody(pred, thm, bindings);
    }

    // ==================================================================================
    // Binding Accessors
    // ==================================================================================

    /**
     * Get the full Binding for a specific role (first match by primary key).
     */
    public Binding getBinding(ItemID role) {
        if (frameBindings == null) return null;
        for (Binding b : frameBindings) {
            if (b.role() != null && b.role().equals(role)) return b;
        }
        return null;
    }

    /**
     * Get the target bound to a specific role.
     */
    public BindingTarget binding(ItemID role) {
        Binding b = getBinding(role);
        return b != null ? b.target() : null;
    }

    /**
     * Get the ItemID bound to a specific role (convenience for IidTarget bindings).
     */
    public ItemID bindingId(ItemID role) {
        BindingTarget target = binding(role);
        return target instanceof BindingTarget.IidTarget iidTarget ? iidTarget.iid() : null;
    }

    /**
     * Get the live decoded instance for a specific role.
     */
    public <T> T instance(ItemID role, Class<T> type) {
        Binding b = getBinding(role);
        return b != null ? b.instance(type) : null;
    }

    /**
     * Backward-compatible map view of bindings (role → target).
     * Returns only simple-key bindings.
     */
    public Map<ItemID, BindingTarget> bindings() {
        if (frameBindings == null || frameBindings.isEmpty()) return Map.of();
        Map<ItemID, BindingTarget> map = new LinkedHashMap<>(frameBindings.size());
        for (Binding b : frameBindings) {
            if (b.isSimpleKey() && b.role() != null) {
                map.put(b.role(), b.target());
            }
        }
        return Map.copyOf(map);
    }

    // ==================================================================================
    // Factories
    // ==================================================================================

    /**
     * Create from predicate, theme, and bindings map (backward compatible).
     */
    public static FrameBody of(ItemID predicate, ItemID theme, Map<ItemID, BindingTarget> bindings) {
        return new FrameBody(predicate, theme, bindings);
    }

    /**
     * Create from predicate, theme, and binding list.
     */
    public static FrameBody of(ItemID predicate, ItemID theme, List<Binding> bindings) {
        return new FrameBody(predicate, theme, bindings);
    }

    /**
     * Create with no bindings.
     */
    public static FrameBody of(ItemID predicate, ItemID theme) {
        return new FrameBody(predicate, theme);
    }

    // ==================================================================================
    // Display
    // ==================================================================================

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("FrameBody{");
        sb.append(predicate.displayAtWidth(16));
        sb.append(" about ");
        sb.append(theme.displayAtWidth(16));
        if (frameBindings != null && !frameBindings.isEmpty()) {
            sb.append(", ").append(frameBindings.size()).append(" bindings");
        }
        sb.append('}');
        return sb.toString();
    }

    // ==================================================================================
    // Equality
    // ==================================================================================

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof FrameBody other)) return false;
        return Objects.equals(predicate, other.predicate)
                && Objects.equals(theme, other.theme)
                && Objects.equals(frameBindings, other.frameBindings);
    }

    @Override
    public int hashCode() {
        return Objects.hash(predicate, theme, frameBindings);
    }
}
