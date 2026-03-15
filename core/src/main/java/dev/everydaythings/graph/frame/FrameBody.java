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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * The semantic assertion of a frame — the "what is being said."
 *
 * <p>A FrameBody is {@code (predicate, bindings)}. The predicate names the
 * frame type (a Sememe). The bindings fill the frame's semantic roles.
 *
 * <p>The owning item is a regular binding in the bindings list — typically
 * the THEME role for property assertions (AUTHORED, HYPERNYM) or the
 * LOCATION role for events/computations (MOVE, ADD, MESSAGE). Use
 * {@link #homeId()} to get the owning item's IID.
 *
 * <p>Each binding controls whether it contributes to body identity via its
 * {@link Binding#isIdentity()} flag. Two identical identity assertions
 * from different signers produce the same body hash.
 *
 * <p>Each binding carries a transient {@code instance} field for live
 * decoded runtime state — the frame IS the runtime container.
 *
 * @see FrameRecord
 * @see FrameEndorsement
 * @see Binding
 */
public final class FrameBody implements Canonical {

    /** Canonical type key for frame bodies. */
    public static final String TYPE_KEY = "cg:type/frame";

    /** Deterministic ItemID for the frame body type. */
    public static final ItemID TYPE_ID = ItemID.fromString(TYPE_KEY);

    /** The frame type — a sememe that names this kind of assertion. */
    private final ItemID predicate;

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
     * Primary constructor — predicate + bindings.
     *
     * <p>The owning item should be included as a binding (typically THEME role).
     * Use the convenience constructors that accept a theme ItemID to have it
     * prepended automatically.
     */
    public FrameBody(ItemID predicate, List<Binding> frameBindings) {
        this.predicate = Objects.requireNonNull(predicate, "predicate");
        this.frameBindings = frameBindings != null ? List.copyOf(frameBindings) : List.of();
    }

    /**
     * Convenience constructor — prepends a THEME binding for the owning item.
     */
    public FrameBody(ItemID predicate, ItemID theme, List<Binding> frameBindings) {
        this.predicate = Objects.requireNonNull(predicate, "predicate");
        List<Binding> all = new ArrayList<>();
        if (theme != null) all.add(homeBinding(theme));
        if (frameBindings != null) all.addAll(frameBindings);
        this.frameBindings = List.copyOf(all);
    }

    /**
     * Convenience constructor from Map — prepends a THEME binding for the owning item.
     * All map bindings get identity=true, index=false.
     */
    public FrameBody(ItemID predicate, ItemID theme, Map<ItemID, BindingTarget> bindings) {
        this.predicate = Objects.requireNonNull(predicate, "predicate");
        List<Binding> all = new ArrayList<>();
        if (theme != null) all.add(homeBinding(theme));
        if (bindings != null && !bindings.isEmpty()) {
            for (var entry : bindings.entrySet()) {
                all.add(new Binding(entry.getKey(), entry.getValue()));
            }
        }
        this.frameBindings = List.copyOf(all);
    }

    /**
     * Convenience constructor with no bindings — prepends a THEME binding.
     */
    public FrameBody(ItemID predicate, ItemID theme) {
        this.predicate = Objects.requireNonNull(predicate, "predicate");
        Objects.requireNonNull(theme, "theme");
        this.frameBindings = List.of(homeBinding(theme));
    }

    /**
     * No-arg constructor for Canonical decode support.
     */
    @SuppressWarnings("unused")
    private FrameBody() {
        this.predicate = null;
        this.frameBindings = null;
    }

    // ==================================================================================
    // Accessors
    // ==================================================================================

    /** The frame type — a sememe that names this kind of assertion. */
    public ItemID predicate() { return predicate; }

    /** Role bindings (semantic, with identity/index flags and live instances). */
    public List<Binding> frameBindings() { return frameBindings; }

    // ==================================================================================
    // Home Binding (owning item)
    // ==================================================================================

    /**
     * The binding that connects this frame to its owning item.
     *
     * <p>Scans for THEME first (property assertions), then LOCATION
     * (events/computations).
     */
    public Binding home() {
        Binding theme = getBinding(ThematicRole.Theme.SEED.iid());
        if (theme != null) return theme;
        return getBinding(ThematicRole.Location.SEED.iid());
    }

    /**
     * The owning item's IID (convenience).
     */
    public ItemID homeId() {
        Binding h = home();
        return h != null ? h.targetId() : null;
    }

    /**
     * Create a home binding for the given item (THEME role, identity=true).
     */
    public static Binding homeBinding(ItemID itemId) {
        return new Binding(ThematicRole.Theme.SEED.iid(), BindingTarget.iid(itemId));
    }

    /**
     * The owning item's IID.
     *
     * @deprecated Use {@link #homeId()} — theme is now a regular THEME binding.
     */
    @Deprecated
    public ItemID theme() {
        return homeId();
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
     * Computed from predicate and identity bindings only.
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
     * Custom CBOR encoding: 2-element array [predicate, bindings].
     * BODY scope includes only identity bindings, RECORD scope includes all.
     */
    @Override
    public CBORObject toCborTree(Scope scope) {
        CBORObject array = CBORObject.NewArray();
        array.Add(predicate != null ? predicate.toCborTree(scope) : CBORObject.Null);

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
     * Decode from CBOR. Handles both:
     * <ul>
     *   <li>New format: 2-element array [predicate, bindings]</li>
     *   <li>Old format: 3-element array [predicate, theme, bindings]</li>
     * </ul>
     */
    @Factory
    public static FrameBody fromCborTree(CBORObject node) {
        if (node == null || node.isNull()) return null;
        if (node.getType() != CBORType.Array || node.size() < 2) return null;

        ItemID pred = new ItemID(node.get(0).GetByteString());

        if (node.size() == 2) {
            // New format: [predicate, bindings]
            List<Binding> bindings = decodeBindings(node.get(1));
            return new FrameBody(pred, bindings);
        }

        // Old format: [predicate, theme, bindings]
        ItemID thm = new ItemID(node.get(1).GetByteString());
        List<Binding> bindings = new ArrayList<>();
        bindings.add(homeBinding(thm));
        if (node.size() > 2) {
            bindings.addAll(decodeBindings(node.get(2)));
        }
        return new FrameBody(pred, bindings);
    }

    private static List<Binding> decodeBindings(CBORObject bindingsNode) {
        List<Binding> bindings = new ArrayList<>();
        if (bindingsNode == null || bindingsNode.isNull()) return bindings;
        if (bindingsNode.getType() == CBORType.Array) {
            for (CBORObject bNode : bindingsNode.getValues()) {
                Binding b = Canonical.fromCborTree(bNode, Binding.class, Scope.RECORD);
                if (b != null) bindings.add(b);
            }
        } else if (bindingsNode.getType() == CBORType.Map) {
            // Backward compat: old format was Map<ItemID, BindingTarget>
            for (CBORObject key : bindingsNode.getKeys()) {
                ItemID role = new ItemID(key.GetByteString());
                BindingTarget target = BindingTarget.fromCborTree(bindingsNode.get(key));
                if (role != null && target != null) {
                    bindings.add(new Binding(role, target));
                }
            }
        }
        return bindings;
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
     * Get ALL bindings for a given simple role (for multi-parent chains like FOLLOWS).
     *
     * <p>Returns every binding whose single-element key matches the given role.
     * Compound keys are excluded. Returns an empty list if no matches found.
     */
    public List<Binding> getAllBindings(ItemID role) {
        if (frameBindings == null) return List.of();
        return frameBindings.stream()
                .filter(b -> b.isSimpleKey() && role.equals(b.role()))
                .toList();
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
        sb.append(predicate != null ? predicate.displayAtWidth(16) : "null");
        ItemID home = homeId();
        if (home != null) {
            sb.append(" about ");
            sb.append(home.displayAtWidth(16));
        }
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
                && Objects.equals(frameBindings, other.frameBindings);
    }

    @Override
    public int hashCode() {
        return Objects.hash(predicate, frameBindings);
    }
}
