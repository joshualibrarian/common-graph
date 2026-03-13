package dev.everydaythings.graph.frame;

import dev.everydaythings.graph.item.Factory;

import dev.everydaythings.graph.item.Type;

import dev.everydaythings.graph.Canonical;
import dev.everydaythings.graph.item.DisplayInfo;
import dev.everydaythings.graph.item.Item;
import dev.everydaythings.graph.item.id.Ref;
import dev.everydaythings.graph.item.id.ContentID;
import dev.everydaythings.graph.item.id.FrameKey;
import dev.everydaythings.graph.item.id.ItemID;
import dev.everydaythings.graph.item.mount.Mount;
import dev.everydaythings.graph.policy.PolicySet;
import dev.everydaythings.graph.ui.scene.ViewNode;
import dev.everydaythings.graph.value.Color;
import lombok.Builder;
import lombok.Getter;
import lombok.Singular;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * An entry in an item's frame table — an endorsed semantic frame.
 *
 * <p>Each frame has a key ({@link FrameKey}, compound semantic address),
 * a type (defines codec/behavior), and a payload (snapshot content,
 * stream heads, or item reference).
 *
 * <p>The identity flag determines whether this frame's content contributes
 * to the item's version identity (VID). Non-identity frames are "registered"
 * (we know they exist) but their content doesn't affect the VID.
 */
@Getter
public final class FrameEntry implements Canonical {

    /** Type reference - defines codec, capabilities, supported selectors. */
    @Canon(order = 1)
    private ItemID type;

    /** Whether this component contributes to version identity. */
    @Canon(order = 2)
    private boolean identity;

    /**
     * Human-facing name for this component.
     *
     * <p>Can be a sememe token (for i18n) or a literal string.
     * Displayed in tree/inspector. Falls back to type short name
     * or raw HID if unset.
     *
     * <p>Wire-compatible with the old "handleText" field (same Canon order).
     */
    @Canon(order = 6)
    private String alias;

    /**
     * Semantic alias key for this component (typically a Sememe ItemID).
     *
     * <p>This is the structured counterpart to {@link #alias}. Use when the
     * component label should be vocabulary-backed and language-agnostic.
     */
    @Canon(order = 9)
    private ItemID aliasRef;

    /**
     * Compound semantic key for this frame.
     *
     * <p>The FrameKey is the primary address for this entry. If null, a
     * FrameKey is derived from {@link #handle} (literal key) or
     * {@link #aliasRef} (semantic key) for backward compatibility.
     *
     * <p>When set explicitly, the handle is derived from it via
     * the FrameKey directly.
     */
    @Canon(order = 10)
    private FrameKey frameKey;

    /**
     * Hash of the frame's body — the semantic assertion identity.
     *
     * <p>Computed from the deterministic CBOR encoding of the frame's
     * predicate, theme, and bindings ({@link FrameBody}). Two identical
     * assertions from different signers produce the same body hash.
     *
     * <p>For endorsed frames, the manifest signature covers this hash.
     * For unendorsed frames, the body hash lives in the {@link FrameRecord}.
     */
    @Canon(order = 11)
    private ContentID bodyHash;

    /** Structured payload facet (content mode and references). */
    @Canon(order = 20)
    private EntryPayload payload;

    /** Structured config facet (settings + policy). */
    @Canon(order = 21)
    private EntryConfig config;

    /** Structured presentation facet (layout mounts + scene overrides). */
    @Canon(order = 22)
    private EntryPresentation presentation;

    /** Structured vocabulary facet (context-scoped contributions). */
    @Canon(order = 23)
    private EntryVocabulary vocabulary;

    /** Owner Item (for Ref - transient, set during hydration). */
    private transient Item owner;

    /** Live decoded instance for this component (runtime-only, never serialized). */
    private transient Object instance;

    @Builder
    public FrameEntry(
            FrameKey frameKey,
            String alias,
            ItemID aliasRef,
            ContentID bodyHash,
            ItemID type,
            boolean identity,
            @Singular List<Mount> mounts,
            EntryPayload payload,
            EntryConfig config,
            EntryPresentation presentation,
            EntryVocabulary vocabulary
    ) {
        this.frameKey = Objects.requireNonNull(frameKey, "frameKey");
        this.alias = alias;  // May be null — display override only
        this.aliasRef = aliasRef;
        this.bodyHash = bodyHash;
        this.type = Objects.requireNonNull(type, "type");
        this.identity = identity;

        this.payload = payload != null ? payload : EntryPayload.builder().build();

        this.config = config != null ? config : EntryConfig.empty();
        this.presentation = presentation != null
                ? presentation
                : EntryPresentation.withMounts(mounts == null ? List.of() : mounts);
        this.vocabulary = vocabulary != null ? vocabulary : EntryVocabulary.empty();
    }

    /**
     * No-arg constructor for Canonical decode support.
     */
    @SuppressWarnings("unused")
    private FrameEntry() {}

    /**
     * Create a snapshot-only component entry (identity=true by default).
     */
    public static FrameEntry snapshot(FrameKey key, ItemID type, ContentID cid) {
        return FrameEntry.builder()
                .frameKey(key)
                .type(type)
                .identity(true)
                .payload(EntryPayload.builder().snapshotCid(cid).build())
                .build();
    }

    /**
     * Create a snapshot-only component entry with alias.
     */
    public static FrameEntry snapshot(String alias, ItemID type, ContentID cid) {
        return FrameEntry.builder()
                .frameKey(FrameKey.literal(alias))
                .alias(alias)
                .type(type)
                .identity(true)
                .payload(EntryPayload.builder().snapshotCid(cid).build())
                .build();
    }

    /**
     * Create a snapshot-only component entry with explicit identity flag.
     */
    public static FrameEntry snapshot(FrameKey key, ItemID type, ContentID cid, boolean identity) {
        return FrameEntry.builder()
                .frameKey(key)
                .type(type)
                .identity(identity)
                .payload(EntryPayload.builder().snapshotCid(cid).build())
                .build();
    }

    /**
     * Create a snapshot-only component entry with alias and explicit identity flag.
     */
    public static FrameEntry snapshot(String alias, ItemID type, ContentID cid, boolean identity) {
        return FrameEntry.builder()
                .frameKey(FrameKey.literal(alias))
                .alias(alias)
                .type(type)
                .identity(identity)
                .payload(EntryPayload.builder().snapshotCid(cid).build())
                .build();
    }

    /**
     * Create a stream-only component entry.
     */
    public static FrameEntry stream(FrameKey key, ItemID type, List<ContentID> heads, boolean identity) {
        return FrameEntry.builder()
                .frameKey(key)
                .type(type)
                .identity(identity)
                .payload(EntryPayload.builder().streamHeads(heads).streamBased(true).build())
                .build();
    }

    /**
     * Create a stream-only component entry with alias.
     */
    public static FrameEntry stream(String alias, ItemID type, List<ContentID> heads, boolean identity) {
        return FrameEntry.builder()
                .frameKey(FrameKey.literal(alias))
                .alias(alias)
                .type(type)
                .identity(identity)
                .payload(EntryPayload.builder().streamHeads(heads).streamBased(true).build())
                .build();
    }

    /**
     * Create a local resource component entry.
     *
     * <p>Local resource components have null snapshotCid and empty streamHeads.
     * They require a mount to define their path in the working tree.
     * They never sync — the absence of content references implies locality.
     *
     * @param key  The frame key
     * @param type The component type
     * @return A local resource component entry
     */
    public static FrameEntry localResource(FrameKey key, ItemID type) {
        return FrameEntry.builder()
                .frameKey(key)
                .type(type)
                .identity(false)  // local resources don't affect VID
                .build();
    }

    /**
     * Create a local resource component entry with alias.
     */
    public static FrameEntry localResource(String alias, ItemID type) {
        return FrameEntry.builder()
                .frameKey(FrameKey.literal(alias))
                .alias(alias)
                .type(type)
                .identity(false)
                .build();
    }

    /**
     * Create a local resource component entry with explicit identity flag.
     *
     * @param key      The frame key
     * @param type     The component type
     * @param identity Whether this component contributes to item identity
     * @return A local resource component entry
     */
    public static FrameEntry localResource(FrameKey key, ItemID type, boolean identity) {
        return FrameEntry.builder()
                .frameKey(key)
                .type(type)
                .identity(identity)
                .build();
    }

    /**
     * Create a local resource component entry with alias and identity flag.
     */
    public static FrameEntry localResource(String alias, ItemID type, boolean identity) {
        return FrameEntry.builder()
                .frameKey(FrameKey.literal(alias))
                .alias(alias)
                .type(type)
                .identity(identity)
                .build();
    }

    // ==================================================================================
    // Relation Factory
    // ==================================================================================

    /**
     * Create a component entry representing an endorsed relation.
     *
     * <p>Relations stored in the FrameTable use the relation's content hash
     * as the snapshot CID, and a handle derived from that CID for uniqueness
     * (an item may endorse multiple relations with the same predicate).
     *
     * @param predicate The relation's predicate ItemID (for display)
     * @param cid       Content ID of the relation bytes in PAYLOAD
     * @param identity  Whether this relation contributes to version identity
     * @return A component entry for the relation
     */
    public static FrameEntry forRelation(ItemID predicate, ContentID cid, boolean identity) {
        String alias = formatPredicate(predicate);
        return FrameEntry.builder()
                .frameKey(FrameKey.literal("rel:" + cid.encodeText()))
                .alias(alias)
                .type(FrameBody.TYPE_ID)
                .identity(identity)
                .payload(EntryPayload.builder().snapshotCid(cid).build())
                .build();
    }

    /**
     * Format a predicate ItemID for human-readable display.
     */
    private static String formatPredicate(ItemID predicate) {
        if (predicate == null) return "?";
        String text = predicate.encodeText();
        int colonIdx = text.lastIndexOf(':');
        if (colonIdx >= 0 && colonIdx < text.length() - 1) {
            return text.substring(colonIdx + 1);
        }
        return predicate.displayAtWidth(12);
    }

    // ==================================================================================
    // Reference Factories
    // ==================================================================================

    /**
     * Create a reference component entry pointing to another item.
     *
     * <p>Reference components store no content bytes. The target item
     * has its own storage and versioning. References default to
     * identity=false (they don't affect the referring item's VID).
     *
     * @param handle The component handle
     * @param type   The component type (typically the target item's type)
     * @param target The referenced item
     * @return A reference component entry
     */
    public static FrameEntry reference(FrameKey key, ItemID type, ItemID target) {
        Objects.requireNonNull(target, "reference target");
        return FrameEntry.builder()
                .frameKey(key)
                .type(type)
                .identity(false)
                .payload(EntryPayload.builder().referenceTarget(target).build())
                .build();
    }

    /**
     * Create a reference component entry with alias.
     */
    public static FrameEntry reference(String alias, ItemID type, ItemID target) {
        Objects.requireNonNull(target, "reference target");
        return FrameEntry.builder()
                .frameKey(FrameKey.literal(alias))
                .alias(alias)
                .type(type)
                .identity(false)
                .payload(EntryPayload.builder().referenceTarget(target).build())
                .build();
    }

    /**
     * Create a reference component entry with alias and explicit identity flag.
     */
    public static FrameEntry reference(String alias, ItemID type, ItemID target, boolean identity) {
        Objects.requireNonNull(target, "reference target");
        return FrameEntry.builder()
                .frameKey(FrameKey.literal(alias))
                .alias(alias)
                .type(type)
                .identity(identity)
                .payload(EntryPayload.builder().referenceTarget(target).build())
                .build();
    }

    // ==================================================================================
    // Content Mode Predicates
    // ==================================================================================

    /**
     * Does this component have snapshot content?
     */
    public boolean hasSnapshot() {
        return payload().snapshotCid != null;
    }

    /**
     * Is this a stream-based component?
     *
     * <p>Returns true if this component is stream-based, even if it has no
     * heads yet. This allows distinguishing fresh stream components from
     * local resources.
     */
    public boolean hasStream() {
        return payload().streamBased;
    }

    /**
     * Does this stream component have any heads?
     */
    public boolean hasStreamHeads() {
        return payload().streamHeads != null && !payload().streamHeads.isEmpty();
    }

    /**
     * Is this a reference to another item?
     *
     * <p>Reference components point to another item by ItemID rather than
     * storing content bytes. The referenced item has its own versioning.
     * This is the containment primitive: items "inside" a container are
     * reference entries in that container's FrameTable.
     */
    public boolean isReference() {
        return payload().referenceTarget != null;
    }

    /**
     * Is this a relation component entry?
     *
     * <p>Relation entries have type == FrameBody.TYPE_ID. They store
     * the relation's content-addressed bytes as a snapshot.
     */
    public boolean isRelation() {
        return FrameBody.TYPE_ID.equals(type);
    }

    /**
     * Is this a local resource component?
     *
     * <p>Local resources have no content references (null snapshotCid,
     * not stream-based, and not a reference). They require a mount to
     * define their path and cannot be synced.
     */
    public boolean isLocalResource() {
        return !hasSnapshot() && !payload().streamBased && payload().referenceTarget == null;
    }

    /**
     * Decode a FrameEntry from CBOR bytes.
     */
    public static FrameEntry decode(byte[] bytes) {
        return Canonical.decodeBinary(bytes, FrameEntry.class, Canonical.Scope.RECORD);
    }

    // ==================================================================================
    // Owner Management
    // ==================================================================================

    /**
     * Set the owner Item. Called when adding to a FrameTable.
     */
    public void setOwner(Item owner) {
        this.owner = owner;
    }

    /**
     * Set the alias for display. Called during hydration when the
     * alias is known from the annotation or dynamic addition.
     */
    public void setAlias(String alias) {
        this.alias = alias;
    }

    /**
     * Set a semantic alias reference.
     */
    public void setAliasRef(ItemID aliasRef) {
        this.aliasRef = aliasRef;
    }

    /**
     * Get the FrameKey for this entry. Always non-null.
     */
    public FrameKey frameKey() {
        return frameKey;
    }

    /**
     * Set an explicit FrameKey for this entry.
     */
    public void setFrameKey(FrameKey frameKey) {
        this.frameKey = frameKey;
    }

    /**
     * Get the body hash for this frame entry.
     *
     * <p>Returns the explicitly set body hash. May be null for entries
     * that predate the body/record split or that derive identity from
     * the payload CID rather than a FrameBody.
     */
    public ContentID bodyHash() {
        return bodyHash;
    }

    /**
     * Set the body hash for this frame entry.
     */
    public void setBodyHash(ContentID bodyHash) {
        this.bodyHash = bodyHash;
    }

    /**
     * Set the live runtime instance for this entry.
     */
    public void setInstance(Object instance) {
        this.instance = instance;
        payload().instance = instance;
    }

    /**
     * Get the live runtime instance for this entry.
     */
    public Object instance() {
        if (instance != null) return instance;
        return payload().instance;
    }

    /**
     * Structured payload facet. Always non-null.
     */
    public EntryPayload payload() {
        if (payload == null) {
            payload = EntryPayload.builder().build();
        }
        if (payload.instance == null) payload.instance = instance;
        return payload;
    }

    /**
     * Structured config facet. Always non-null.
     */
    public EntryConfig config() {
        if (config == null) config = EntryConfig.empty();
        return config;
    }

    /**
     * Structured presentation facet. Always non-null.
     */
    public EntryPresentation presentation() {
        if (presentation == null) {
            presentation = EntryPresentation.withMounts(List.of());
        }
        if (presentation.layout == null) {
            presentation.layout = PresentationLayout.withMounts(List.of());
        }
        return presentation;
    }

    /**
     * Structured vocabulary facet. Always non-null.
     */
    public EntryVocabulary vocabulary() {
        if (vocabulary == null) vocabulary = EntryVocabulary.empty();
        return vocabulary;
    }

    /**
     * Convenience access to mounts through the presentation facet.
     */
    public List<Mount> mounts() {
        List<Mount> fromPresentation = presentation().layout().mounts();
        return fromPresentation == null ? List.of() : fromPresentation;
    }

    // ==================================================================================
    // Mount Management
    // ==================================================================================

    /**
     * Add a mount to this entry.
     */
    public void addMount(Mount mount) {
        List<Mount> localMounts = mounts();
        if (localMounts == null || localMounts.isEmpty()) {
            localMounts = new ArrayList<>();
        } else if (!(localMounts instanceof ArrayList)) {
            localMounts = new ArrayList<>(localMounts);
        }
        localMounts.add(mount);
        presentation().layout().mounts = List.copyOf(localMounts);
    }

    /**
     * Get the path mounts for this entry.
     */
    public List<Mount.PathMount> pathMounts() {
        return mounts().stream()
                .filter(m -> m instanceof Mount.PathMount)
                .map(m -> (Mount.PathMount) m)
                .toList();
    }

    /**
     * Check if this entry has any path mounts.
     */
    public boolean hasPathMount() {
        return mounts().stream().anyMatch(m -> m instanceof Mount.PathMount);
    }

    /**
     * Get the primary path mount (first path mount), or null if none.
     */
    public Mount.PathMount primaryPathMount() {
        return mounts().stream()
                .filter(m -> m instanceof Mount.PathMount)
                .map(m -> (Mount.PathMount) m)
                .findFirst()
                .orElse(null);
    }

    // ==================================================================================
    // Display Methods
    // ==================================================================================

    public Ref ref() {
        if (owner == null || frameKey == null) return null;
        return Ref.of(owner.iid(), frameKey);
    }

    /**
     * Ref for presentation navigation — uses the frame key directly.
     */
    public Ref presentationRef() {
        if (owner == null) return null;
        return ref();
    }

    public String displayToken() {
        // Prefer the alias if available
        if (alias != null && !alias.isBlank()) {
            return alias;
        }
        // Next prefer semantic alias key (language-neutral stable identifier)
        if (aliasRef != null) {
            String encoded = aliasRef.encodeText();
            int colon = encoded.lastIndexOf(':');
            if (colon >= 0 && colon < encoded.length() - 1) {
                return encoded.substring(colon + 1);
            }
            return aliasRef.displayAtWidth(24);
        }
        // Extract readable name from type (e.g., "cg:type/log" → "Log")
        if (type != null) {
            String typeName = extractTypeShortName(type);
            if (typeName != null) {
                return typeName;
            }
        }
        // Fall back to frameKey display
        return frameKey != null ? frameKey.toCanonicalString() : "(unnamed)";
    }

    /**
     * Extract a short readable name from a type ItemID.
     * <p>e.g., "cg:type/log" → "Log", "cg:type/expression" → "Expression"
     */
    private static String extractTypeShortName(ItemID typeId) {
        String text = typeId.encodeText();
        // Find last segment after '/'
        int lastSlash = text.lastIndexOf('/');
        if (lastSlash >= 0 && lastSlash < text.length() - 1) {
            String shortName = text.substring(lastSlash + 1);
            // Capitalize first letter
            if (!shortName.isEmpty()) {
                return Character.toUpperCase(shortName.charAt(0)) + shortName.substring(1);
            }
        }
        return null;
    }

    public String displaySubtitle() {
        if (type == null) return null;
        if (payload().referenceTarget != null) {
            return "\u2192 " + payload().referenceTarget.displayAtWidth(12);
        }
        return type.displayAtWidth(16);
    }

    public boolean isExpandable() {
        return true;  // Expandable to show the actual component content
    }

    public String colorCategory() {
        return "component";
    }

    public String emoji() {
        // Different emoji based on content mode
        if (isRelation()) return "🔗";  // Relation
        if (payload().referenceTarget != null) return "🔗";  // Reference
        if (payload().streamBased) return "📡";  // Stream
        if (payload().snapshotCid != null) return "📄";  // Snapshot
        return "📦";  // Local resource
    }

    public DisplayInfo displayInfo() {
        String name = displayToken();
        String typeName = type != null ? type.displayAtWidth(20) : "Component";
        return DisplayInfo.builder()
                .name(name)
                .typeName(typeName)
                .color(Color.rgb(100, 180, 100))  // Component green
                .iconText(emoji())
                .build();
    }

    // ==================================================================================
    // Facet Convenience
    // ==================================================================================

    /**
     * Effective policy for this entry (null if unset).
     */
    public PolicySet policy() {
        return config().policy;
    }

    /**
     * Set/replace policy in this entry's config facet.
     */
    public void setPolicy(PolicySet policy) {
        config().policy = policy;
    }

    /**
     * Effective scene override for this entry (null if unset).
     */
    public ViewNode sceneOverride() {
        EntryPresentation p = presentation();
        PresentationSkin skin = p.skin();
        return skin != null ? skin.sceneOverride : null;
    }

    /**
     * Set/replace scene override in this entry's presentation skin facet.
     */
    public void setSceneOverride(ViewNode sceneOverride) {
        presentation().skin().sceneOverride = sceneOverride;
    }

    public void addSetting(ScopedSetting setting) {
        EntryConfig cfg = config();
        List<ScopedSetting> current = cfg.settings != null ? cfg.settings : List.of();
        ArrayList<ScopedSetting> updated = new ArrayList<>(current);
        updated.add(setting);
        cfg.settings = List.copyOf(updated);
    }

    /**
     * Upsert a setting by scope + key.
     *
     * <p>If a setting with the same normalized scope and key already exists,
     * it is replaced in-place; otherwise the setting is appended.
     */
    public void putSetting(ScopedSetting setting) {
        Objects.requireNonNull(setting, "setting");

        EntryConfig cfg = config();
        List<ScopedSetting> current = cfg.settings != null ? cfg.settings : List.of();
        ArrayList<ScopedSetting> updated = new ArrayList<>(current.size() + 1);

        String targetScope = normalizeScope(setting.scopePath());
        String targetKey = setting.key();
        boolean replaced = false;

        for (ScopedSetting existing : current) {
            String existingScope = normalizeScope(existing.scopePath());
            if (Objects.equals(existingScope, targetScope) && Objects.equals(existing.key(), targetKey)) {
                if (!replaced) {
                    updated.add(setting);
                    replaced = true;
                }
                continue;
            }
            updated.add(existing);
        }

        if (!replaced) {
            updated.add(setting);
        }
        cfg.settings = List.copyOf(updated);
    }

    private static String normalizeScope(String scopePath) {
        return scopePath == null || scopePath.isBlank() ? "/" : scopePath;
    }

    public void addVocabularyContribution(VocabularyContribution term) {
        EntryVocabulary vocab = vocabulary();
        List<VocabularyContribution> current = vocab.contributions != null ? vocab.contributions : List.of();
        ArrayList<VocabularyContribution> updated = new ArrayList<>(current);
        updated.add(term);
        vocab.contributions = List.copyOf(updated);
    }

    // ==================================================================================
    // Facet Types
    // ==================================================================================

    @Getter
    public static final class EntryPayload implements Canonical {
        @Canon(order = 0) private ContentID snapshotCid;
        @Canon(order = 1) private List<ContentID> streamHeads;
        @Canon(order = 2) private boolean streamBased;
        @Canon(order = 3) private ItemID referenceTarget;

        /**
         * CID of the encrypted envelope (Tag 10) in the object store.
         *
         * <p>When set, the actual content in the object store is a Tag 10 encrypted
         * envelope stored at this CID. The {@code snapshotCid} still holds the
         * <b>plaintext</b> CID for identity/VID purposes — the item's version
         * identity is based on semantic content, not encryption artifacts.
         *
         * <p>When null, the frame content is stored cleartext at {@code snapshotCid}.
         */
        @Canon(order = 4) private ContentID encryptedCid;

        private transient Object instance;

        @Builder
        public EntryPayload(ContentID snapshotCid,
                            @Singular List<ContentID> streamHeads,
                            boolean streamBased,
                            ItemID referenceTarget,
                            ContentID encryptedCid) {
            this.snapshotCid = snapshotCid;
            this.streamHeads = streamHeads == null ? List.of() : List.copyOf(streamHeads);
            this.streamBased = streamBased || !this.streamHeads.isEmpty();
            this.referenceTarget = referenceTarget;
            this.encryptedCid = encryptedCid;
        }

        /** True if this frame's content is encrypted. */
        public boolean isEncrypted() {
            return encryptedCid != null;
        }

        @SuppressWarnings("unused")
        private EntryPayload() {}
    }

    @Getter
    public static final class EntryConfig implements Canonical {
        @Canon(order = 0) private List<ScopedSetting> settings;
        @Canon(order = 1) private PolicySet policy;

        @Builder
        public EntryConfig(@Singular List<ScopedSetting> settings, PolicySet policy) {
            this.settings = settings == null ? List.of() : List.copyOf(settings);
            this.policy = policy;
        }

        public static EntryConfig empty() {
            return EntryConfig.builder().build();
        }

        @SuppressWarnings("unused")
        private EntryConfig() {}
    }

    @Getter
    public static final class EntryPresentation implements Canonical {
        @Canon(order = 0) private PresentationLayout layout;
        @Canon(order = 1) private PresentationSkin skin;

        @Builder
        public EntryPresentation(PresentationLayout layout, PresentationSkin skin) {
            this.layout = layout != null ? layout : PresentationLayout.withMounts(List.of());
            this.skin = skin != null ? skin : PresentationSkin.empty();
        }

        public static EntryPresentation withMounts(List<Mount> mounts) {
            return EntryPresentation.builder()
                    .layout(PresentationLayout.withMounts(mounts))
                    .skin(PresentationSkin.empty())
                    .build();
        }

        @SuppressWarnings("unused")
        private EntryPresentation() {}
    }

    @Getter
    public static final class PresentationLayout implements Canonical {
        @Canon(order = 0) private List<Mount> mounts;

        @Builder
        public PresentationLayout(@Singular List<Mount> mounts) {
            this.mounts = mounts == null ? List.of() : List.copyOf(mounts);
        }

        public static PresentationLayout withMounts(List<Mount> mounts) {
            return PresentationLayout.builder().mounts(mounts == null ? List.of() : mounts).build();
        }

        @SuppressWarnings("unused")
        private PresentationLayout() {}
    }

    @Getter
    public static final class PresentationSkin implements Canonical {
        @Canon(order = 0) private ViewNode sceneOverride;

        @Builder
        public PresentationSkin(ViewNode sceneOverride) {
            this.sceneOverride = sceneOverride;
        }

        public static PresentationSkin empty() {
            return PresentationSkin.builder().build();
        }

        @SuppressWarnings("unused")
        private PresentationSkin() {}
    }

    @Getter
    public static final class EntryVocabulary implements Canonical {
        @Canon(order = 0) private List<VocabularyContribution> contributions;

        @Builder
        public EntryVocabulary(@Singular("contribution") List<VocabularyContribution> contributions) {
            this.contributions = contributions == null ? List.of() : List.copyOf(contributions);
        }

        public static EntryVocabulary empty() {
            return EntryVocabulary.builder().build();
        }

        @SuppressWarnings("unused")
        private EntryVocabulary() {}
    }

    @Getter
    public static final class ScopedSetting implements Canonical {
        @Canon(order = 0) private String scopePath;
        @Canon(order = 1) private String key;
        @Canon(order = 2) private String value;
        @Canon(order = 3) private String valueType;

        @Builder
        public ScopedSetting(String scopePath, String key, String value, String valueType) {
            this.scopePath = scopePath == null || scopePath.isBlank() ? "/" : scopePath;
            this.key = key;
            this.value = value;
            this.valueType = valueType;
        }

        @SuppressWarnings("unused")
        private ScopedSetting() {}
    }

    @Getter
    public static final class VocabularyContribution implements Canonical {
        /** Scope path within the component ("/" = root). */
        @Canon(order = 0) private String scope;

        /** Target sememe or concept this contribution maps to. */
        @Canon(order = 1) private ItemID termRef;

        /** Trigger token (literal text that activates this contribution). */
        @Canon(order = 2) private String token;

        /** Target expression to evaluate when this contribution triggers. */
        @Canon(order = 3) private String expression;

        @Builder
        public VocabularyContribution(String scope, ItemID termRef, String token, String expression) {
            this.scope = scope == null || scope.isBlank() ? "/" : scope;
            this.termRef = termRef;
            this.token = token;
            this.expression = expression;
        }

        /** Whether this contribution targets an expression rather than a sememe. */
        public boolean isExpression() {
            return expression != null && !expression.isBlank();
        }

        @SuppressWarnings("unused")
        private VocabularyContribution() {}
    }

    @Override
    public String toString() {
        // Provide a concise toString for debugging
        return "FrameEntry{" + displayToken() + "}";
    }
}
