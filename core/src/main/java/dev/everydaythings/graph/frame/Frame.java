package dev.everydaythings.graph.frame;

import com.upokecenter.cbor.CBORObject;
import dev.everydaythings.graph.Canonical;
import dev.everydaythings.graph.item.Item;
import dev.everydaythings.graph.item.Literal;
import dev.everydaythings.graph.item.id.ContentID;
import dev.everydaythings.graph.item.id.FrameKey;
import dev.everydaythings.graph.item.id.ItemID;
import dev.everydaythings.graph.item.id.Ref;
import dev.everydaythings.graph.item.mount.Mount;
import dev.everydaythings.graph.language.CoreVocabulary;
import dev.everydaythings.graph.language.ThematicRole;
import dev.everydaythings.graph.policy.PolicySet;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * A frame within an Item — the single primitive for endorsed content.
 *
 * <p>Frame is the minimal runtime wrapper around a {@link FrameBody}
 * (the semantic assertion). It carries the key (address within the item),
 * a reference to the body, and transient runtime state (live decoded
 * instance, owner item).
 *
 * <p>Mounts do NOT live on Frame — they live on {@link EndorsementsTable}
 * in a parallel map. "Where is this frame mounted?" is a question you ask
 * the table, not the frame.
 *
 * @see FrameBody
 * @see FrameEndorsement
 * @see EndorsementsTable
 */
public final class Frame {

    /** The semantic address within the item. */
    private final FrameKey key;

    /** The type ID (predicate of the body — defines codec/behavior). */ // TODO: so... this is the predicate FROM the body, just referenfed out here?
    private final ItemID type;

    /** The semantic assertion (predicate, theme, bindings). */
    private FrameBody body;

    /** Hash of the body (for endorsement). */
    private ContentID bodyHash;

    /** Whether this frame contributes to version identity. */
    private final boolean identity;

    /** Human-facing display alias (transient, deprecated — use TokenDictionary). */
    private transient String alias;

    /** Live decoded instance (e.g., the Vault, Log, or String). */
    private transient Object instance;

    /** Parent item reference (for Ref navigation). */
    private transient Item owner;

    /** Per-frame policy override (transient — encoded into FrameBody Config binding on commit). */
    private transient PolicySet policy;

    // ==================================================================================
    // Constructor
    // ==================================================================================

    public Frame(FrameKey key, ItemID type, FrameBody body, ContentID bodyHash, boolean identity) {
        this.key = Objects.requireNonNull(key, "key");
        this.type = Objects.requireNonNull(type, "type");
        this.body = body;
        this.bodyHash = bodyHash;
        this.identity = identity;
    }

    // ==================================================================================
    // Accessors
    // ==================================================================================

    public FrameKey frameKey() { return key; }
    public ItemID type() { return type; }
    public FrameBody body() { return body; }
    public ContentID bodyHash() { return bodyHash; }
    public boolean identity() { return identity; }
    public Object instance() { return instance; }
    public Item owner() { return owner; }
    public String alias() { return alias; }

    /**
     * Per-frame policy override.
     *
     * <p>Returns the policy stored in the transient policy field, falling back to
     * decoding the Config binding from the FrameBody if present.
     */
    public PolicySet policy() {
        if (policy != null) return policy;
        if (body != null) {
            byte[] configBytes = body.configPayload();
            if (configBytes != null) {
                try {
                    FrameConfig cfg = Canonical.decodeBinary(
                            configBytes, FrameConfig.class, Canonical.Scope.RECORD);
                    if (cfg != null) return cfg.policy();
                } catch (Exception ignored) {}
            }
        }
        return null;
    }

    // ==================================================================================
    // Mutators (runtime state)
    // ==================================================================================

    public void setBody(FrameBody body) { this.body = body; }
    public void setBodyHash(ContentID bodyHash) { this.bodyHash = bodyHash; }
    public void setInstance(Object instance) { this.instance = instance; }
    public void setOwner(Item owner) { this.owner = owner; }
    public void setAlias(String alias) { this.alias = alias; }

    /**
     * Set the per-frame policy override.
     *
     * <p>Stores the policy in the transient field and encodes it into the
     * FrameBody's Config binding so it is committed with the frame.
     */
    public void setPolicy(PolicySet policy) {
        this.policy = policy;
        if (body == null) return;
        // Encode the policy into a FrameConfig and store as Config binding in the body
        FrameConfig cfg = FrameConfig.builder().policy(policy).build();
        byte[] configBytes = cfg.encodeBinary(Canonical.Scope.RECORD);
        Literal configLiteral = new Literal(Literal.TYPE_CBOR, configBytes);
        // Rebuild the body replacing the Config binding
        List<Binding> bindings = new ArrayList<>();
        if (body.frameBindings() != null) {
            for (Binding b : body.frameBindings()) {
                if (b.isSimpleKey() && ThematicRole.Config.SEED.iid().equals(b.role())) {
                    continue; // will replace
                }
                bindings.add(b);
            }
        }
        bindings.add(Binding.nonIdentity(ThematicRole.Config.SEED.iid(), configLiteral));
        setBody(new FrameBody(body.predicate(), body.theme(), bindings));
    }

    // ==================================================================================
    // Convenience
    // ==================================================================================

    /** Is this a relation frame? (type == FrameBody.TYPE_ID) */
    public boolean isRelation() {
        return FrameBody.TYPE_ID.equals(type);
    }

    /** Build a Ref from owner IID + frame key. */
    public Ref ref() {
        if (owner == null) return null;
        return Ref.of(owner.iid(), key);
    }

    /**
     * Build a {@link FrameEndorsement} for manifest serialization.
     *
     * @param mounts the mount list from EndorsementsTable (may be empty)
     */
    public FrameEndorsement toEndorsement(List<Mount> mounts) {
        ContentID hash = bodyHash != null ? bodyHash : ContentID.of(new byte[0]);
        return new FrameEndorsement(key, hash, mounts != null ? mounts : List.of());
    }

    /** Convenience: toEndorsement with no mounts. */
    public FrameEndorsement toEndorsement() {
        return toEndorsement(List.of());
    }

    // ==================================================================================
    // Display (deprecated — will move to @Surface annotations and CONFIG)
    // ==================================================================================

    /** Display token for tree/inspector views. */
    public String displayToken() {
        if (alias != null && !alias.isBlank()) return alias;
        return key.toCanonicalString();
    }

    /** Emoji glyph for this frame. */
    public String emoji() {
        if (isRelation()) return "🔗";
        if (body != null && body.isStream()) return "📜";
        if (body != null && body.isExternal()) return "📁";
        if (body != null && body.isReference()) return "↗";
        return "📦";
    }

    // ==================================================================================
    // Static Factories
    // ==================================================================================

    /** Placeholder theme for factory-created frames (set to real IID during commit). */
    private static final ItemID PLACEHOLDER = ItemID.fromString("cg:placeholder");

    /** Create a snapshot frame. */
    public static Frame snapshot(FrameKey key, ItemID type, ContentID cid, boolean identity) {
        List<Binding> bindings = new ArrayList<>();
        if (cid != null) {
            bindings.add(new Binding(ThematicRole.Topic.SEED.iid(),
                    BindingTarget.ref(cid), true, false));
        }
        FrameBody body = new FrameBody(type, PLACEHOLDER, bindings);
        return new Frame(key, type, body, null, identity);
    }

    /** Create a snapshot frame (identity=true). */
    public static Frame snapshot(FrameKey key, ItemID type, ContentID cid) {
        return snapshot(key, type, cid, true);
    }

    /** Create a stream frame. */
    public static Frame stream(FrameKey key, ItemID type, List<ContentID> heads, boolean identity) {
        List<Binding> bindings = new ArrayList<>();
        List<ItemID> streamKey = List.of(
                ThematicRole.Topic.SEED.iid(), CoreVocabulary.Stream.SEED.iid());
        if (heads != null && !heads.isEmpty()) {
            bindings.add(Binding.compound(streamKey,
                    BindingTarget.ref(heads.getFirst()), true, false));
        } else {
            bindings.add(Binding.compound(streamKey,
                    Literal.ofText(""), false, false));
        }
        FrameBody body = new FrameBody(type, PLACEHOLDER, bindings);
        return new Frame(key, type, body, null, identity);
    }

    /** Create a local/external resource frame (identity defaults to false). */
    public static Frame localResource(FrameKey key, ItemID type, boolean identity) {
        List<Binding> bindings = new ArrayList<>();
        List<ItemID> externalKey = List.of(
                ThematicRole.Topic.SEED.iid(), CoreVocabulary.External.SEED.iid());
        bindings.add(Binding.compound(externalKey,
                Literal.ofText(""), false, false));
        FrameBody body = new FrameBody(type, PLACEHOLDER, bindings);
        return new Frame(key, type, body, null, identity);
    }

    /** Create a local/external resource frame (identity=false). */
    public static Frame localResource(FrameKey key, ItemID type) {
        return localResource(key, type, false);
    }

    /** Create a reference frame pointing to another item (identity defaults to false). */
    public static Frame reference(FrameKey key, ItemID type, ItemID target) {
        Objects.requireNonNull(target, "reference target");
        List<Binding> bindings = new ArrayList<>();
        bindings.add(new Binding(ThematicRole.Goal.SEED.iid(),
                BindingTarget.iid(target), true, false));
        FrameBody body = new FrameBody(type, PLACEHOLDER, bindings);
        return new Frame(key, type, body, null, false);
    }

    /** Create a reference frame with explicit identity flag. */
    public static Frame reference(FrameKey key, ItemID type, ItemID target, boolean identity) {
        Objects.requireNonNull(target, "reference target");
        List<Binding> bindings = new ArrayList<>();
        bindings.add(new Binding(ThematicRole.Goal.SEED.iid(),
                BindingTarget.iid(target), true, false));
        FrameBody body = new FrameBody(type, PLACEHOLDER, bindings);
        return new Frame(key, type, body, null, identity);
    }

    /** Create a relation frame. */
    public static Frame forRelation(ItemID predicate, ContentID cid, boolean identity, String displayName) {
        String alias = displayName != null ? displayName : formatPredicate(predicate);
        FrameKey key = FrameKey.literal("rel:" + cid.encodeText());
        List<Binding> bindings = new ArrayList<>();
        if (cid != null) {
            bindings.add(new Binding(ThematicRole.Topic.SEED.iid(),
                    BindingTarget.ref(cid), true, false));
        }
        FrameBody body = new FrameBody(FrameBody.TYPE_ID, PLACEHOLDER, bindings);
        Frame frame = new Frame(key, FrameBody.TYPE_ID, body, null, identity);
        frame.alias = alias;
        return frame;
    }

    /** Create a relation frame (no display name). */
    public static Frame forRelation(ItemID predicate, ContentID cid, boolean identity) {
        return forRelation(predicate, cid, identity, null);
    }

    /**
     * Reconstruct a Frame from a {@link FrameBody} and {@link FrameEndorsement}.
     *
     * <p>Used during hydration when manifests contain endorsements.
     */
    public static Frame fromFrameBody(FrameBody body, FrameEndorsement endorsement) {
        Frame frame = new Frame(
                endorsement.key(),
                body.predicate(),
                body,
                endorsement.bodyHash(),
                hasIdentityBindings(body));
        return frame;
    }

    // ==================================================================================
    // Helpers
    // ==================================================================================

    private static boolean hasIdentityBindings(FrameBody body) {
        if (body.frameBindings() == null) return true;
        return body.frameBindings().stream().anyMatch(Binding::identity);
    }

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
    // Legacy CBOR Encode/Decode (FrameEntry-compatible wire format)
    // ==================================================================================

    /**
     * Encode this Frame to CBOR bytes in the legacy FrameEntry wire format.
     *
     * <p>Used by WorkingTreeStore and EndorsementsTable for backward-compatible
     * serialization. Delegates to the package-private FrameEntry class.
     *
     * @param mounts mounts from EndorsementsTable (may be empty)
     * @return CBOR bytes
     */
    public byte[] encodeLegacyCbor(List<Mount> mounts) {
        return encodeLegacyCborTree(mounts).EncodeToBytes(
                com.upokecenter.cbor.CBOREncodeOptions.DefaultCtap2Canonical);
    }

    /**
     * Encode this Frame to a CBOR tree in the legacy FrameEntry wire format.
     */
    CBORObject encodeLegacyCborTree(List<Mount> mounts) {
        FrameEntry entry = FrameEntry.builder()
                .frameKey(key)
                .type(type)
                .identity(identity)
                .mounts(mounts != null ? mounts : List.of())
                .bodyHash(bodyHash)
                .build();
        if (body != null) entry.setBody(body);
        if (alias != null) entry.setAlias(alias);
        return entry.toCborTree(Canonical.Scope.RECORD);
    }

    /**
     * Decode a Frame from CBOR bytes in the legacy FrameEntry wire format.
     *
     * @param bytes CBOR bytes
     * @return decoded Frame
     */
    public static Frame decodeLegacyCbor(byte[] bytes) {
        FrameEntry entry = Canonical.decodeBinary(bytes, FrameEntry.class, Canonical.Scope.RECORD);
        return fromLegacyEntry(entry);
    }

    /**
     * Decode a Frame from a CBOR tree node in the legacy FrameEntry wire format.
     */
    static Frame decodeLegacyCborTree(CBORObject node) {
        FrameEntry entry = Canonical.fromCborTree(node, FrameEntry.class, Canonical.Scope.RECORD);
        return entry != null ? fromLegacyEntry(entry) : null;
    }

    /**
     * Decode a list of Frames from a CBOR array of legacy FrameEntry nodes.
     */
    static List<Frame> decodeLegacyCborArray(CBORObject arrayNode) {
        List<Frame> result = new ArrayList<>();
        if (arrayNode != null && arrayNode.getType() == com.upokecenter.cbor.CBORType.Array) {
            for (CBORObject entryNode : arrayNode.getValues()) {
                Frame frame = decodeLegacyCborTree(entryNode);
                if (frame != null) {
                    result.add(frame);
                }
            }
        }
        return result;
    }

    /**
     * Convert a decoded FrameEntry to a Frame, extracting mounts.
     *
     * @return a Frame (mounts are returned separately via the MountResult overload)
     */
    private static Frame fromLegacyEntry(FrameEntry entry) {
        Frame frame = new Frame(
                entry.frameKey(), entry.type(), entry.body(),
                entry.bodyHash(), entry.identity());
        frame.setAlias(entry.alias());
        return frame;
    }

    /**
     * Result of decoding a legacy FrameEntry — carries both the Frame and its mounts.
     */
    public record FrameWithMounts(Frame frame, List<Mount> mounts) {}

    /**
     * Decode a Frame and its mounts from CBOR bytes in the legacy FrameEntry wire format.
     */
    public static FrameWithMounts decodeLegacyCborWithMounts(byte[] bytes) {
        FrameEntry entry = Canonical.decodeBinary(bytes, FrameEntry.class, Canonical.Scope.RECORD);
        Frame frame = fromLegacyEntry(entry);
        List<Mount> mounts = entry.mounts() != null ? entry.mounts() : List.of();
        return new FrameWithMounts(frame, mounts);
    }

    /**
     * Decode a Frame and its mounts from a CBOR tree node.
     */
    static FrameWithMounts decodeLegacyCborTreeWithMounts(CBORObject node) {
        FrameEntry entry = Canonical.fromCborTree(node, FrameEntry.class, Canonical.Scope.RECORD);
        if (entry == null) return null;
        Frame frame = fromLegacyEntry(entry);
        List<Mount> mounts = entry.mounts() != null ? entry.mounts() : List.of();
        return new FrameWithMounts(frame, mounts);
    }

    // ==================================================================================
    // Mount helpers
    // ==================================================================================

    /**
     * Get path mounts from a mount list (utility for EndorsementsTable).
     */
    static List<Mount.PathMount> filterPathMounts(List<Mount> mounts) {
        if (mounts == null || mounts.isEmpty()) return List.of();
        return mounts.stream()
                .filter(m -> m instanceof Mount.PathMount)
                .map(m -> (Mount.PathMount) m)
                .toList();
    }

    /**
     * Does a mount list contain any path mounts?
     */
    static boolean hasPathMount(List<Mount> mounts) {
        if (mounts == null) return false;
        return mounts.stream().anyMatch(m -> m instanceof Mount.PathMount);
    }

    /**
     * Get the primary (first) path mount from a mount list.
     */
    static Mount.PathMount primaryPathMount(List<Mount> mounts) {
        if (mounts == null) return null;
        return mounts.stream()
                .filter(m -> m instanceof Mount.PathMount)
                .map(m -> (Mount.PathMount) m)
                .findFirst()
                .orElse(null);
    }

    @Override
    public String toString() {
        return "Frame{" + key.toCanonicalString()
                + ", type=" + type.displayAtWidth(16)
                + (bodyHash != null ? ", hash=" + bodyHash.displayAtWidth(12) : "")
                + "}";
    }
}
