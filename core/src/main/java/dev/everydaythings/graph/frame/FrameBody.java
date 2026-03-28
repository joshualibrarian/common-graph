package dev.everydaythings.graph.frame;

import dev.everydaythings.graph.item.Factory;
import dev.everydaythings.graph.item.Implements;
import dev.everydaythings.graph.item.ItemSeed;

import com.upokecenter.cbor.CBORObject;
import com.upokecenter.cbor.CBORType;
import dev.everydaythings.graph.Canonical;
import dev.everydaythings.graph.frame.ItemFrame.Bind;
import dev.everydaythings.graph.item.Literal;
import dev.everydaythings.graph.item.id.ContentID;
import dev.everydaythings.graph.item.id.FrameKey;
import dev.everydaythings.graph.item.id.HashID;
import dev.everydaythings.graph.item.id.ItemID;
import dev.everydaythings.graph.language.CoreVocabulary;
import dev.everydaythings.graph.language.Language;
import dev.everydaythings.graph.language.SememeGloss;
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
@Implements(FrameBody.TYPE_KEY)
@ItemSeed(key = FrameBody.TYPE_KEY)
public final class FrameBody implements Canonical {

    public static final String TYPE_KEY = "cg.sememe:frame";
    public static final ItemID TYPE_ID = ItemID.fromString(TYPE_KEY);

    @ItemFrame(predicate = SememeGloss.KEY,
               fieldAs = @Bind(role = ThematicRole.Name.KEY, qualifiers = {Language.ENGLISH_KEY}))
    static final String gloss = "a semantic assertion — predicate with role bindings";

    // EXPECTS — array position 0, 1, 2 (declaration order = position)
    @ItemFrame(predicate = CoreVocabulary.Expects.KEY,
               fieldAs = @Bind(role = ThematicRole.Topic.KEY, qualifiers = {Predicate.KEY}))
    static final ItemID expectPredicate = Predicate.IID;

    @ItemFrame(predicate = CoreVocabulary.Expects.KEY,
               fieldAs = @Bind(role = ThematicRole.Topic.KEY, qualifiers = {Bindings.KEY}))
    static final ItemID expectBindings = Bindings.IID;

    @ItemFrame(predicate = CoreVocabulary.Expects.KEY,
               fieldAs = @Bind(role = ThematicRole.Topic.KEY, qualifiers = {Config.KEY}))
    static final ItemID expectConfig = Config.IID;

    // Field-name sememes for array positions
    @ItemSeed(key = Predicate.KEY)
    static class Predicate {
        static final String KEY = "cg.structure:predicate";
        static final ItemID IID = ItemID.fromString(KEY);
        @ItemFrame(predicate = SememeGloss.KEY,
                   fieldAs = @Bind(role = ThematicRole.Name.KEY, qualifiers = {Language.ENGLISH_KEY}))
        static final String gloss = "the predicate of a frame — what kind of assertion";
    }

    @ItemSeed(key = Bindings.KEY)
    static class Bindings {
        static final String KEY = "cg.structure:bindings";
        static final ItemID IID = ItemID.fromString(KEY);
        @ItemFrame(predicate = SememeGloss.KEY,
                   fieldAs = @Bind(role = ThematicRole.Name.KEY, qualifiers = {Language.ENGLISH_KEY}))
        static final String gloss = "the role bindings of a frame";
    }

    @ItemSeed(key = Config.KEY)
    static class Config {
        static final String KEY = "cg.structure:config";
        static final ItemID IID = ItemID.fromString(KEY);
        @ItemFrame(predicate = SememeGloss.KEY,
                   fieldAs = @Bind(role = ThematicRole.Name.KEY, qualifiers = {Language.ENGLISH_KEY}))
        static final String gloss = "non-identity configuration bindings";
    }

    /** The frame type — a sememe that names this kind of assertion. */
    private final ItemID predicate;

    /** Role bindings (semantic, with identity/index flags and live instances). */
    private final List<Binding> frameBindings;

    /** Config bindings (record-scope only, not part of body identity). */
    private final List<Binding> config;

    /** Cached body hash. */
    private transient ContentID cachedHash;

    /** Cached body bytes (CBOR encoding). */
    private transient byte[] cachedBytes;

    // ==================================================================================
    // Constructors
    // ==================================================================================

    /**
     * Primary constructor — predicate + bindings (no config).
     *
     * <p>The owning item should be included as a binding (typically THEME role).
     * Use the convenience constructors that accept a theme ItemID to have it
     * prepended automatically.
     */
    public FrameBody(ItemID predicate, List<Binding> frameBindings) {
        this(predicate, frameBindings, List.of());
    }

    /**
     * Full constructor — predicate + bindings + config map.
     *
     * <p>Config bindings are record-scope only and do not affect body identity.
     * They carry presentation, vocabulary, and general config data that is
     * separate from the semantic assertion.
     */
    public FrameBody(ItemID predicate, List<Binding> frameBindings, List<Binding> config) {
        this.predicate = Objects.requireNonNull(predicate, "predicate");
        this.frameBindings = frameBindings != null ? List.copyOf(frameBindings) : List.of();
        this.config = config != null ? List.copyOf(config) : List.of();
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
        this.config = List.of();
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
        this.config = List.of();
    }

    /**
     * Convenience constructor with no bindings — prepends a THEME binding.
     */
    public FrameBody(ItemID predicate, ItemID theme) {
        this.predicate = Objects.requireNonNull(predicate, "predicate");
        Objects.requireNonNull(theme, "theme");
        this.frameBindings = List.of(homeBinding(theme));
        this.config = List.of();
    }

    /**
     * No-arg constructor for Canonical decode support.
     */
    @SuppressWarnings("unused")
    private FrameBody() {
        this.predicate = null;
        this.frameBindings = null;
        this.config = null;
    }

    // ==================================================================================
    // Accessors
    // ==================================================================================

    /** The frame type — a sememe that names this kind of assertion. */
    public ItemID predicate() { return predicate; }

    /**
     * Derive the semantic selector for this frame body.
     *
     * <p>The selector is computed from the predicate (head) plus all qualifier
     * IIDs from compound binding keys. A binding with key {@code [NAME, ENGLISH, VERB, LEMMA]}
     * contributes qualifiers {@code ENGLISH, VERB, LEMMA}. Single-element keys
     * like {@code [THEME]} contribute nothing.
     *
     * <p>This is the "address" of the frame — what you'd use to find it.
     * For example, a LEXEME frame with {@code [NAME, ENGLISH, VERB, LEMMA] → "create"}
     * produces selector {@code (LEXEME, ENGLISH, VERB, LEMMA)}.
     */
    public FrameKey selector() {
        List<Object> quals = new ArrayList<>();
        if (frameBindings != null) {
            for (Binding b : frameBindings) {
                for (FrameKey.FrameToken q : b.qualifiers()) {
                    if (q instanceof FrameKey.Sememe s) quals.add(s.id());
                    else if (q instanceof FrameKey.Literal l) quals.add(l.value());
                }
            }
        }
        return FrameKey.of(predicate, quals.toArray());
    }

    /** Role bindings (semantic, with identity/index flags and live instances). */
    public List<Binding> frameBindings() { return frameBindings; }

    /** Config bindings (record-scope, non-identity). */
    public List<Binding> config() { return config != null ? config : List.of(); }

    /**
     * Look up a config binding by qualifier (simple key match in the config list).
     *
     * @param qualifier the config qualifier IID (e.g., PRESENTATION, VOCABULARY, CONFIG)
     * @return the matching binding, or null if not found
     */
    public Binding configBinding(ItemID qualifier) {
        if (config == null || config.isEmpty()) return null;
        for (Binding b : config) {
            if (b.isSimpleKey() && qualifier.equals(b.role())) return b;
        }
        return null;
    }

    /**
     * Create a new FrameBody with a config binding added or replaced.
     *
     * <p>Returns a new instance — FrameBody is immutable. The semantic bindings
     * and body hash are unchanged since config is record-scope only.
     *
     * @param qualifier the config qualifier IID (e.g., PRESENTATION, VOCABULARY, CONFIG)
     * @param target the config value
     */
    public FrameBody withConfig(ItemID qualifier, BindingTarget target) {
        List<Binding> newConfig = new ArrayList<>(config());
        newConfig.removeIf(b -> b.isSimpleKey() && qualifier.equals(b.role()));
        newConfig.add(Binding.nonIdentity(qualifier, target));
        return new FrameBody(predicate, frameBindings, newConfig);
    }

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
        Binding theme = getBinding(ThematicRole.Theme.IID);
        if (theme != null) return theme;
        return getBinding(ThematicRole.Location.IID);
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
        return new Binding(ThematicRole.Theme.IID, BindingTarget.iid(itemId));
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
     * Custom CBOR encoding.
     * <ul>
     *   <li>BODY scope: {@code [predicate, identity-bindings]} (2-element, for hashing)</li>
     *   <li>RECORD scope: {@code [predicate, all-bindings]} or
     *       {@code [predicate, all-bindings, config]} (3-element if config present)</li>
     * </ul>
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

        // Config map — record-scope only, omitted when empty for backward compat
        if (scope == Scope.RECORD && config != null && !config.isEmpty()) {
            CBORObject configArray = CBORObject.NewArray();
            for (Binding b : config) {
                configArray.Add(b.toCborTree(scope));
            }
            array.Add(configArray);
        }

        return array;
    }

    /**
     * Decode from CBOR. Handles three formats:
     * <ul>
     *   <li>2-element: {@code [predicate, bindings]} — current format, no config</li>
     *   <li>3-element, element 1 is Array: {@code [predicate, bindings, config]} — new format with config</li>
     *   <li>3-element, element 1 is ByteString: {@code [predicate, theme, bindings]} — legacy format</li>
     * </ul>
     */
    @Factory
    public static FrameBody fromCborTree(CBORObject node) {
        if (node == null || node.isNull()) return null;
        if (node.getType() != CBORType.Array || node.size() < 2) return null;

        ItemID pred = new ItemID(node.get(0).GetByteString());

        if (node.size() == 2) {
            // Current format: [predicate, bindings]
            List<Binding> bindings = decodeBindings(node.get(1));
            return new FrameBody(pred, bindings);
        }

        CBORObject second = node.get(1);
        if (second.getType() == CBORType.ByteString) {
            // Legacy format: [predicate, theme, bindings]
            ItemID thm = new ItemID(second.GetByteString());
            List<Binding> bindings = new ArrayList<>();
            bindings.add(homeBinding(thm));
            if (node.size() > 2) {
                bindings.addAll(decodeBindings(node.get(2)));
            }
            return new FrameBody(pred, bindings);
        }

        // New format: [predicate, bindings, config]
        List<Binding> bindings = decodeBindings(second);
        List<Binding> config = node.size() > 2 ? decodeBindings(node.get(2)) : List.of();
        return new FrameBody(pred, bindings, config);
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
        if (target instanceof BindingTarget.IidTarget iidTarget) return iidTarget.iid();
        if (target instanceof BindingTarget.RefTarget refTarget) return refTarget.asItemId();
        return null;
    }

    /**
     * Get the Ref bound to a specific role (works for both IidTarget and RefTarget).
     *
     * <p>IidTarget is wrapped as a simple Ref. RefTarget returns its full Ref
     * (which may include a compound frame key path).
     */
    public dev.everydaythings.graph.item.id.Ref bindingRef(ItemID role) {
        BindingTarget target = binding(role);
        if (target instanceof BindingTarget.RefTarget refTarget) return refTarget.asRef();
        if (target instanceof BindingTarget.IidTarget iidTarget) {
            return iidTarget.iid() != null ? dev.everydaythings.graph.item.id.Ref.of(iidTarget.iid()) : null;
        }
        return null;
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
        return extractCid(ThematicRole.Topic.IID);
    }

    /**
     * The encrypted content CID for this frame — (TOPIC, ENCRYPTED) compound binding.
     *
     * <p>Equivalent to the legacy {@code EntryPayload.encryptedCid()}.
     */
    public ContentID encryptedCid() {
        Binding b = getCompoundBinding(ThematicRole.Topic.IID, CoreVocabulary.Encrypted.IID);
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
        return bindingId(ThematicRole.Goal.IID);
    }

    /**
     * Whether this frame has a content snapshot (simple TOPIC binding is present).
     *
     * <p>A simple (TOPIC) binding means snapshot content. Compound keys like
     * (TOPIC, STREAM) or (TOPIC, EXTERNAL) are different content modes.
     */
    public boolean hasContent() {
        return binding(ThematicRole.Topic.IID) != null;
    }

    /**
     * Whether this frame is a reference to another item (Goal binding is present).
     */
    public boolean isReference() {
        return binding(ThematicRole.Goal.IID) != null;
    }

    /**
     * Whether this frame has encrypted content — (TOPIC, ENCRYPTED) compound binding is present.
     */
    public boolean isEncrypted() {
        return getCompoundBinding(ThematicRole.Topic.IID, CoreVocabulary.Encrypted.IID) != null;
    }

    /**
     * The config payload bytes for this frame (Config role binding).
     *
     * <p>Checks the config map first (direct CONFIG key), then falls back
     * to a CONFIG binding in the semantic bindings for backward compat.
     *
     * @return raw CBOR bytes from the Config binding's Literal payload, or null
     */
    public byte[] configPayload() {
        // Config map path (new)
        Binding cb = configBinding(ThematicRole.Config.IID);
        if (cb != null && cb.target() instanceof Literal lit) {
            return lit.payload();
        }
        // Body binding path (backward compat)
        BindingTarget target = binding(ThematicRole.Config.IID);
        if (target instanceof Literal lit) {
            return lit.payload();
        }
        return null;
    }

    /**
     * Presentation config payload bytes for this frame.
     *
     * <p>Checks the config map first (direct PRESENTATION key), then falls back
     * to the compound (CONFIG, PRESENTATION) binding in semantic bindings.
     *
     * @return raw CBOR bytes from the presentation binding's Literal payload, or null
     */
    public byte[] configPresentationPayload() {
        // Config map path (new) — direct PRESENTATION key
        Binding cb = configBinding(ThematicRole.Presentation.IID);
        if (cb != null && cb.target() instanceof Literal lit) {
            return lit.payload();
        }
        // Body binding path (backward compat) — compound (CONFIG, PRESENTATION)
        Binding b = getCompoundBinding(ThematicRole.Config.IID,
                ThematicRole.Presentation.IID);
        if (b != null && b.target() instanceof Literal lit) {
            return lit.payload();
        }
        return null;
    }

    /**
     * Vocabulary config payload bytes for this frame.
     *
     * <p>Checks the config map first (direct VOCABULARY key), then falls back
     * to the compound (CONFIG, VOCABULARY) binding in semantic bindings.
     *
     * @return raw CBOR bytes from the vocabulary binding's Literal payload, or null
     */
    public byte[] configVocabularyPayload() {
        // Config map path (new) — direct VOCABULARY key
        Binding cb = configBinding(ThematicRole.Vocabulary.IID);
        if (cb != null && cb.target() instanceof Literal lit) {
            return lit.payload();
        }
        // Body binding path (backward compat) — compound (CONFIG, VOCABULARY)
        Binding b = getCompoundBinding(ThematicRole.Config.IID,
                ThematicRole.Vocabulary.IID);
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
        return getCompoundBinding(ThematicRole.Topic.IID, CoreVocabulary.Stream.IID) != null;
    }

    /**
     * Whether this frame carries external data — a (TOPIC, EXTERNAL) compound binding.
     */
    public boolean isExternal() {
        return getCompoundBinding(ThematicRole.Topic.IID, CoreVocabulary.External.IID) != null;
    }

    /**
     * The stream head CID for stream-mode frames.
     *
     * <p>Returns the CID from the (TOPIC, STREAM) compound binding, or null
     * if this frame is not stream-based.
     */
    public ContentID streamHeadCid() {
        Binding b = getCompoundBinding(ThematicRole.Topic.IID, CoreVocabulary.Stream.IID);
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
        Binding b = getCompoundBinding(ThematicRole.Topic.IID, CoreVocabulary.External.IID);
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
            return ref.asCid();
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
                && Objects.equals(frameBindings, other.frameBindings)
                && Objects.equals(config(), other.config());
    }

    @Override
    public int hashCode() {
        return Objects.hash(predicate, frameBindings, config());
    }

    // ==================================================================================
    // Builder
    // ==================================================================================

    /**
     * Create a builder for a FrameBody with the given predicate.
     */
    public static Builder builder(ItemID predicate) {
        return new Builder(predicate);
    }

    /**
     * Fluent builder for FrameBody.
     *
     * <p>Two forms for adding bindings:
     * <ul>
     *   <li><b>Short form</b>: {@code .bind(role, itemId)} — defaults to identity=true, index=true</li>
     *   <li><b>Long form</b>: {@code .bind(role).qualified(...).to(target).identity(false).index(false)} — full control</li>
     * </ul>
     *
     * <p>Example:
     * <pre>{@code
     * FrameBody.builder(CoreVocabulary.Lexeme.IID)
     *     .bind(ThematicRole.Theme.IID, sememe.iid())
     *     .bind(ThematicRole.Name.IID)
     *         .qualified(Language.ENGLISH, PartOfSpeech.Verb.IID, GrammaticalFeature.Lemma.IID)
     *         .to("create")
     *         .identity(true).index(true)
     *     .build();
     * }</pre>
     */
    public static final class Builder {
        private final ItemID predicate;
        private final List<Binding> bindings = new ArrayList<>();

        private Builder(ItemID predicate) {
            this.predicate = Objects.requireNonNull(predicate, "predicate");
        }

        /** Short form — item ref, identity=true, index=true, no qualifiers. */
        public Builder bind(ItemID role, ItemID target) {
            bindings.add(new Binding(role, BindingTarget.iid(target), true, true));
            return this;
        }

        /** Short form — text literal, identity=true, index=true, no qualifiers. */
        public Builder bind(ItemID role, String text) {
            bindings.add(new Binding(role, Literal.ofText(text), true, true));
            return this;
        }

        /** Short form — explicit BindingTarget, identity=true, index=true, no qualifiers. */
        public Builder bind(ItemID role, BindingTarget target) {
            bindings.add(new Binding(role, target, true, true));
            return this;
        }

        /** Long form — opens a BindingBuilder for qualifiers, target, and flags. */
        public BindingBuilder bind(ItemID role) {
            return new BindingBuilder(this, role);
        }

        /** Build the FrameBody. */
        public FrameBody build() {
            return new FrameBody(predicate, List.copyOf(bindings));
        }

        // Used by BindingBuilder to add the completed binding
        private Builder addBinding(Binding binding) {
            bindings.add(binding);
            return this;
        }
    }

    /**
     * Sub-builder for a single binding with qualifiers and flags.
     */
    public static final class BindingBuilder {
        private final Builder parent;
        private final ItemID role;
        private final List<FrameKey.FrameToken> qualifiers = new ArrayList<>();
        private BindingTarget target;
        private boolean identity = true;
        private boolean index = true;

        private BindingBuilder(Builder parent, ItemID role) {
            this.parent = parent;
            this.role = role;
        }

        /** Add qualifiers (sememe IDs). */
        public BindingBuilder qualified(ItemID... qualifierIds) {
            for (ItemID q : qualifierIds) {
                qualifiers.add(new FrameKey.Sememe(q));
            }
            return this;
        }

        /** Set target to an item reference. */
        public BindingBuilder to(ItemID target) {
            this.target = BindingTarget.iid(target);
            return this;
        }

        /** Set target to a text literal. */
        public BindingBuilder to(String text) {
            this.target = Literal.ofText(text);
            return this;
        }

        /** Set target to an explicit BindingTarget. */
        public BindingBuilder to(BindingTarget target) {
            this.target = target;
            return this;
        }

        /** Set identity flag (default true). */
        public BindingBuilder identity(boolean identity) {
            this.identity = identity;
            return this;
        }

        /** Set index flag (default true). */
        public BindingBuilder index(boolean index) {
            this.index = index;
            return this;
        }

        /** Complete this binding and return to the parent builder. */
        public Builder bind(ItemID nextRole, ItemID nextTarget) {
            finish();
            return parent.bind(nextRole, nextTarget);
        }

        /** Complete this binding and return to the parent builder. */
        public Builder bind(ItemID nextRole, String nextText) {
            finish();
            return parent.bind(nextRole, nextText);
        }

        /** Complete this binding and open another long-form binding. */
        public BindingBuilder bind(ItemID nextRole) {
            finish();
            return parent.bind(nextRole);
        }

        /** Complete this binding and build the FrameBody. */
        public FrameBody build() {
            finish();
            return parent.build();
        }

        private void finish() {
            Objects.requireNonNull(target, "binding target must be set via to()");
            parent.addBinding(new Binding(role, qualifiers, target, identity, index));
        }
    }
}
