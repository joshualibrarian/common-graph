package dev.everydaythings.graph.frame;

import dev.everydaythings.graph.item.Factory;

import com.upokecenter.cbor.CBORObject;
import com.upokecenter.cbor.CBORType;
import dev.everydaythings.graph.Canonical;
import dev.everydaythings.graph.item.Literal;
import dev.everydaythings.graph.item.id.ContentID;
import dev.everydaythings.graph.item.id.HashID;
import dev.everydaythings.graph.item.id.ItemID;
import dev.everydaythings.graph.language.CoreVocabulary;
import dev.everydaythings.graph.language.ThematicRole;
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

    /** Canonical type key for frame bodies. */ // TODO: I'm confused by this... I changed it from "relation" to "frame", but still... why the different pattern.  "Type" is a kinda slippery word, I'm not sure your meaning here.
    public static final String TYPE_KEY = "cg:type/frame";

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
     * Get the full Binding for a specific simple (single-element) key.
     *
     * <p>Only matches bindings with exactly one key element. Compound keys
     * like (TOPIC, STREAM) are NOT matched — use {@link #getCompoundBinding}
     * for those.
     */
    public Binding getBinding(ItemID role) {
        if (frameBindings == null) return null;
        for (Binding b : frameBindings) {
            if (b.isSimpleKey() && b.role() != null && b.role().equals(role)) return b;
        }
        return null;
    }

    /**
     * Get the full Binding whose primary role matches, regardless of key length.
     *
     * <p>Matches both simple keys like (TOPIC) and compound keys like
     * (TOPIC, STREAM). Returns the first match.
     */
    public Binding getBindingByRole(ItemID role) {
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
    // Semantic Accessors (role-based convenience methods)
    // ==================================================================================

    /**
     * The content CID for this frame (Topic binding).
     *
     * <p>For component frames, this is the snapshot content CID.
     * Equivalent to the legacy {@code EntryPayload.snapshotCid()}.
     */
    public ContentID contentCid() {
        return extractCid(ThematicRole.Topic.SEED.iid());
    }

    /**
     * The encrypted content CID for this frame — (TOPIC, ENCRYPTED) compound binding.
     *
     * <p>Equivalent to the legacy {@code EntryPayload.encryptedCid()}.
     */
    public ContentID encryptedCid() {
        Binding b = getCompoundBinding(ThematicRole.Topic.SEED.iid(), CoreVocabulary.Encrypted.SEED.iid());
        if (b == null) return null;
        return extractCidFromTarget(b.target());
    }

    /**
     * The reference target ItemID for this frame (Goal binding).
     *
     * <p>For reference frames that point to another item.
     * Equivalent to the legacy {@code EntryPayload.referenceTarget()}.
     */
    public ItemID referenceTargetId() {
        return bindingId(ThematicRole.Goal.SEED.iid());
    }

    /**
     * Whether this frame has a content snapshot (simple TOPIC binding is present).
     *
     * <p>A simple (TOPIC) binding means snapshot content. Compound keys like
     * (TOPIC, STREAM) or (TOPIC, EXTERNAL) are different content modes.
     */
    public boolean hasContent() {
        return binding(ThematicRole.Topic.SEED.iid()) != null;
    }

    /**
     * Whether this frame is a reference to another item (Goal binding is present).
     */
    public boolean isReference() {
        return binding(ThematicRole.Goal.SEED.iid()) != null;
    }

    /**
     * Whether this frame has encrypted content — (TOPIC, ENCRYPTED) compound binding is present.
     */
    public boolean isEncrypted() {
        return getCompoundBinding(ThematicRole.Topic.SEED.iid(), CoreVocabulary.Encrypted.SEED.iid()) != null;
    }

    /**
     * The config payload bytes for this frame (Config role binding).
     *
     * <p>Returns the raw CBOR bytes from the Config binding's Literal payload,
     * or null if no config is bound.
     */
    public byte[] configPayload() {
        BindingTarget target = binding(ThematicRole.Config.SEED.iid());
        if (target instanceof Literal lit) {
            return lit.payload();
        }
        return null;
    }

    /**
     * Presentation config payload bytes for this frame: (CONFIG, PRESENTATION) compound binding.
     *
     * @return raw CBOR bytes from the presentation binding's Literal payload, or null
     */
    public byte[] configPresentationPayload() {
        Binding b = getCompoundBinding(ThematicRole.Config.SEED.iid(),
                ThematicRole.Presentation.SEED.iid());
        if (b != null && b.target() instanceof Literal lit) {
            return lit.payload();
        }
        return null;
    }

    /**
     * Vocabulary config payload bytes for this frame: (CONFIG, VOCABULARY) compound binding.
     *
     * @return raw CBOR bytes from the vocabulary binding's Literal payload, or null
     */
    public byte[] configVocabularyPayload() {
        Binding b = getCompoundBinding(ThematicRole.Config.SEED.iid(),
                ThematicRole.Vocabulary.SEED.iid());
        if (b != null && b.target() instanceof Literal lit) {
            return lit.payload();
        }
        return null;
    }

    // ==================================================================================
    // Compound-Key Content Mode Accessors
    // ==================================================================================

    /**
     * Whether this frame carries stream-mode content — a (TOPIC, STREAM) compound binding.
     */
    public boolean isStream() {
        return getCompoundBinding(ThematicRole.Topic.SEED.iid(), CoreVocabulary.Stream.SEED.iid()) != null;
    }

    /**
     * Whether this frame carries external data — a (TOPIC, EXTERNAL) compound binding.
     */
    public boolean isExternal() {
        return getCompoundBinding(ThematicRole.Topic.SEED.iid(), CoreVocabulary.External.SEED.iid()) != null;
    }

    /**
     * The stream head CID for stream-mode frames.
     *
     * <p>Returns the CID from the (TOPIC, STREAM) compound binding, or null
     * if this frame is not stream-based.
     */
    public ContentID streamHeadCid() {
        Binding b = getCompoundBinding(ThematicRole.Topic.SEED.iid(), CoreVocabulary.Stream.SEED.iid());
        if (b == null) return null;
        return extractCidFromTarget(b.target());
    }

    /**
     * The external filesystem path for external-data frames.
     *
     * <p>Returns the path string from the (TOPIC, EXTERNAL) compound binding's
     * Literal target, or null if this frame is not external.
     */
    public String externalPath() {
        Binding b = getCompoundBinding(ThematicRole.Topic.SEED.iid(), CoreVocabulary.External.SEED.iid());
        if (b == null) return null;
        if (b.target() instanceof Literal lit) {
            try { return lit.asText(); } catch (Exception e) { return null; }
        }
        return null;
    }

    /**
     * Get a binding by compound key (two-element key match).
     */
    public Binding getCompoundBinding(ItemID first, ItemID second) {
        if (frameBindings == null) return null;
        for (Binding b : frameBindings) {
            if (b.keyEquals(first, second)) return b;
        }
        return null;
    }

    /**
     * Extract a ContentID from a binding that uses RefTarget or IidTarget.
     */
    private ContentID extractCid(ItemID role) {
        return extractCidFromTarget(binding(role));
    }

    /**
     * Extract a ContentID from a BindingTarget (RefTarget or IidTarget).
     */
    private static ContentID extractCidFromTarget(BindingTarget target) {
        if (target instanceof BindingTarget.RefTarget ref) {
            HashID id = ref.ref();
            return id instanceof ContentID cid ? cid : new ContentID(id.encodeBinary());
        }
        if (target instanceof BindingTarget.IidTarget iid) {
            return new ContentID(iid.iid().encodeBinary());
        }
        return null;
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
