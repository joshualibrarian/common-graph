package dev.everydaythings.graph.item.component;

import com.upokecenter.cbor.CBORObject;
import dev.everydaythings.graph.Canonical;
import dev.everydaythings.graph.Hash;
import dev.everydaythings.graph.item.id.ContentID;
import dev.everydaythings.graph.item.id.FrameKey;
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
 * <p>A FrameBody contains only the assertion fields: predicate, theme,
 * and bindings. These are the fields that contribute to body identity.
 * Two identical assertions from different signers produce the same body hash.
 *
 * <p>The body hash is computed from deterministic CBOR encoding of these
 * fields. Records (envelopes with signer, timestamp, signature) reference
 * bodies by hash, enabling deduplication and multi-attestation.
 *
 * <p>For endorsed frames, the body hash lives in the manifest's FrameEntry.
 * For unendorsed frames, it lives in the FrameRecord envelope.
 *
 * @see FrameRecord
 * @see FrameEntry
 */
@Getter
public final class FrameBody implements Canonical {

    /** Canonical type key for frame bodies (replaces Relation.TYPE_KEY). */
    public static final String TYPE_KEY = "cg:type/relation";

    /** Deterministic ItemID for the frame body type. */
    public static final ItemID TYPE_ID = ItemID.fromString(TYPE_KEY);

    /** The frame type — a sememe that names this kind of assertion. */
    @Canon(order = 0)
    private final ItemID predicate;

    /** What this frame is about — the item this assertion lives on. */
    @Canon(order = 1)
    private final ItemID theme;

    /** Additional role bindings beyond predicate and theme. */
    @Canon(order = 2)
    private final Map<ItemID, BindingTarget> bindings;

    /** Cached body hash. */
    private transient ContentID cachedHash;

    /** Cached body bytes (CBOR encoding). */
    private transient byte[] cachedBytes;

    /**
     * Construct a FrameBody.
     *
     * @param predicate the frame type (required)
     * @param theme     what this frame is about (required)
     * @param bindings  additional role bindings (nullable — treated as empty)
     */
    public FrameBody(ItemID predicate, ItemID theme, Map<ItemID, BindingTarget> bindings) {
        this.predicate = Objects.requireNonNull(predicate, "predicate");
        this.theme = Objects.requireNonNull(theme, "theme");
        this.bindings = bindings != null ? Map.copyOf(bindings) : Map.of();
    }

    /**
     * Construct a FrameBody with no additional bindings.
     */
    public FrameBody(ItemID predicate, ItemID theme) {
        this(predicate, theme, null);
    }

    /**
     * No-arg constructor for Canonical decode support.
     */
    @SuppressWarnings("unused")
    private FrameBody() {
        this.predicate = null;
        this.theme = null;
        this.bindings = null;
    }

    /**
     * The deterministic CBOR encoding of this body (for hashing).
     */
    public byte[] bodyBytes() {
        if (cachedBytes == null) {
            cachedBytes = encodeBinary(Scope.BODY);
        }
        return cachedBytes;
    }

    /**
     * The content identity of this assertion.
     *
     * <p>Computed from the deterministic CBOR encoding of predicate,
     * theme, and bindings. Two identical assertions from different
     * signers produce the same hash.
     */
    public ContentID hash() {
        if (cachedHash == null) {
            cachedHash = ContentID.of(bodyBytes());
        }
        return cachedHash;
    }

    // ==================================================================================
    // Binding Accessors
    // ==================================================================================

    /**
     * Get the target bound to a specific role.
     *
     * @param role The role IID (e.g., ThematicRole.Theme.SEED.iid())
     * @return The target, or null if role not filled
     */
    public BindingTarget binding(ItemID role) {
        return bindings != null ? bindings.get(role) : null;
    }

    /**
     * Get the ItemID bound to a specific role (convenience for IidTarget bindings).
     *
     * @param role The role IID
     * @return The bound item's IID, or null if role not filled or not an IidTarget
     */
    public ItemID bindingId(ItemID role) {
        BindingTarget target = binding(role);
        return target instanceof BindingTarget.IidTarget iidTarget ? iidTarget.iid() : null;
    }

    // ==================================================================================
    // Factories
    // ==================================================================================

    /**
     * Create a FrameBody from a predicate, theme, and bindings.
     */
    public static FrameBody of(ItemID predicate, ItemID theme, Map<ItemID, BindingTarget> bindings) {
        return new FrameBody(predicate, theme, bindings);
    }

    /**
     * Create a FrameBody with no additional bindings.
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
        if (bindings != null && !bindings.isEmpty()) {
            sb.append(", ").append(bindings.size()).append(" bindings");
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
                && Objects.equals(bindings, other.bindings);
    }

    @Override
    public int hashCode() {
        return Objects.hash(predicate, theme, bindings);
    }
}
