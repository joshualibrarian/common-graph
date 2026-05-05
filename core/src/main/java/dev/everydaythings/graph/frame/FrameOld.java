package dev.everydaythings.graph.frame;

import com.upokecenter.cbor.CBORObject;
import dev.everydaythings.graph.Canonical;
import dev.everydaythings.graph.item.ItemOld;
import dev.everydaythings.graph.item.Literal;
import dev.everydaythings.graph.item.id.ContentID;
import dev.everydaythings.graph.item.id.CompoundKey;
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
 * <p>Frame is the minimal runtime wrapper around a {@link FrameBodyOld}
 * (the semantic assertion). It carries the key (address within the item),
 * a reference to the body, and transient runtime state (live decoded
 * instance, owner item).
 *
 * <p>Mounts do NOT live on Frame — they live on {@link EndorsementsTable}
 * in a parallel map. "Where is this frame mounted?" is a question you ask
 * the table, not the frame.
 *
 * @see FrameBodyOld
 * @see FrameEndorsement
 * @see EndorsementsTable
 */
public final class FrameOld implements Canonical {

    // ==================================================================================
    // Serialized fields (@Canon)
    // ==================================================================================

    /** The semantic address within the item. */
    @Canon(order = 0)
    private CompoundKey key;

    /** The type ID (predicate of the body — defines codec/behavior). */
    @Canon(order = 1)
    private ItemID type;    //TODO: should be a ref, so specific version can be saved

    /** Whether this frame contributes to version identity. */
    @Canon(order = 2)
    private boolean identity;   //TODO: this should be... just on the binding?

    /** Hash of the body (for endorsement). */
    @Canon(order = 3)
    private ContentID bodyHash;

    /** Mounts — owned by EndorsementsTable at runtime, serialized here for CBOR. */
    @Canon(order = 4)   // TODO: ?
    private List<Mount> mounts = List.of();

    // alias field REMOVED — display names come from TokenDictionary/sememe resolution

    // ==================================================================================
    // Transient runtime fields
    // ==================================================================================

    /** The semantic assertion (predicate, theme, bindings). */
    private transient FrameBodyOld body;

    /** Live decoded instance (e.g., the Vault, Log, or String). */
    private transient Object instance;

    /** Parent item reference (for Ref navigation). */
    private transient ItemOld owner;

    /** Per-frame policy override (transient — encoded into FrameBody Config binding on commit). */
    private transient PolicySet policy;

    /** Attestation records (transient — populated from RECORD_BY_BODY index for unendorsed frames). */
    private transient List<FrameRecordOld> records;

    // ==================================================================================
    // Constructors
    // ==================================================================================

    public FrameOld(CompoundKey key, ItemID type, FrameBodyOld body, ContentID bodyHash, boolean identity) {
        this.key = Objects.requireNonNull(key, "key");
        this.type = Objects.requireNonNull(type, "type");
        this.body = body;
        this.bodyHash = bodyHash;
        this.identity = identity;
    }

    /** No-arg constructor for Canonical decode. */
    @SuppressWarnings("unused")
    private FrameOld() {}

    // ==================================================================================
    // Accessors
    // ==================================================================================

    public CompoundKey frameKey() { return key; }
    public ItemID type() { return type; }
    public FrameBodyOld body() { return body; }
    public ContentID bodyHash() { return bodyHash; }
    public boolean identity() { return identity; }
    public Object instance() { return instance; }
    public ItemOld owner() { return owner; }
    // alias() DELETED — display names resolved via TokenDictionary
    public List<FrameRecordOld> records() { return records != null ? records : List.of(); }

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
                    // Try direct PolicySet decode first (new format)
                    PolicySet direct = Canonical.decodeBinary(
                            configBytes, PolicySet.class, Canonical.Scope.RECORD);
                    if (direct != null) return direct;
                } catch (Exception ignored) {}
                try {
                    // Fall back to FrameConfig wrapper (legacy format)
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

    public void setBody(FrameBodyOld body) { this.body = body; }
    public void setBodyHash(ContentID bodyHash) { this.bodyHash = bodyHash; }
    public void setInstance(Object instance) { this.instance = instance; }
    public void setOwner(ItemOld owner) { this.owner = owner; }
    public void setRecords(List<FrameRecordOld> records) { this.records = records; }

    /**
     * Set the per-frame policy override.
     *
     * <p>Stores the policy in the transient field and encodes it into the
     * FrameBody's config map so it is committed with the frame.
     */
    public void setPolicy(PolicySet policy) {
        this.policy = policy;
        if (body == null) return;
        // Encode PolicySet directly into the config map
        byte[] configBytes = policy.encodeBinary(Canonical.Scope.RECORD);
        Literal configLiteral = new Literal(Literal.TYPE_CBOR, configBytes);
        setBody(body.withConfig(ThematicRole.Config.IID, configLiteral));
    }

    // ==================================================================================
    // Convenience
    // ==================================================================================

    /** Is this a bare frame body (no component wrapper — type == FrameBody.TYPE_ID)? */
    public boolean isBareFrame() {
        return FrameBodyOld.TYPE_ID.equals(type);
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
        return key.toCanonicalString();
    }

    /** Emoji glyph for this frame. */
    public String emoji() {
        if (isBareFrame()) return "🔗";
        if (body != null && body.isStream()) return "📜";
        if (body != null && body.isExternal()) return "📁";
        if (body != null && body.isReference()) return "↗";
        return "📦";
    }

    // ==================================================================================
    // Static Factories
    // ==================================================================================

    /** Create a snapshot frame. */
    public static FrameOld snapshot(CompoundKey key, ItemID type, ContentID cid, boolean identity) {
        List<Binding> bindings = new ArrayList<>();
        if (cid != null) {
            bindings.add(new Binding(ThematicRole.Topic.IID,
                    BindingTarget.ref(cid), true, false));
        }
        FrameBodyOld body = new FrameBodyOld(type, bindings);
        return new FrameOld(key, type, body, null, identity);
    }

    /** Create a snapshot frame (identity=true). */
    public static FrameOld snapshot(CompoundKey key, ItemID type, ContentID cid) {
        return snapshot(key, type, cid, true);
    }

    /** Create a stream frame. */
    public static FrameOld stream(CompoundKey key, ItemID type, List<ContentID> heads, boolean identity) {
        List<Binding> bindings = new ArrayList<>();
        List<CompoundKey.FrameToken> streamQualifiers = List.of(new CompoundKey.Sememe(CoreVocabulary.Stream.IID));
        if (heads != null && !heads.isEmpty()) {
            bindings.add(Binding.qualified(ThematicRole.Topic.IID, streamQualifiers,
                    BindingTarget.ref(heads.getFirst()), true, false));
        } else {
            bindings.add(Binding.qualified(ThematicRole.Topic.IID, streamQualifiers,
                    Literal.ofText(""), false, false));
        }
        FrameBodyOld body = new FrameBodyOld(type, bindings);
        return new FrameOld(key, type, body, null, identity);
    }

    /** Create a local/external resource frame (identity defaults to false). */
    public static FrameOld localResource(CompoundKey key, ItemID type, boolean identity) {
        List<Binding> bindings = new ArrayList<>();
        List<CompoundKey.FrameToken> externalQualifiers = List.of(new CompoundKey.Sememe(CoreVocabulary.External.IID));
        bindings.add(Binding.qualified(ThematicRole.Topic.IID, externalQualifiers,
                Literal.ofText(""), false, false));
        FrameBodyOld body = new FrameBodyOld(type, bindings);
        return new FrameOld(key, type, body, null, identity);
    }

    /** Create a local/external resource frame (identity=false). */
    public static FrameOld localResource(CompoundKey key, ItemID type) {
        return localResource(key, type, false);
    }

    /** Create a reference frame pointing to another item (identity defaults to false). */
    public static FrameOld reference(CompoundKey key, ItemID type, ItemID target) {
        Objects.requireNonNull(target, "reference target");
        List<Binding> bindings = new ArrayList<>();
        bindings.add(new Binding(ThematicRole.Goal.IID,
                BindingTarget.iid(target), true, false));
        FrameBodyOld body = new FrameBodyOld(type, bindings);
        return new FrameOld(key, type, body, null, false);
    }

    /** Create a reference frame with explicit identity flag. */
    public static FrameOld reference(CompoundKey key, ItemID type, ItemID target, boolean identity) {
        Objects.requireNonNull(target, "reference target");
        List<Binding> bindings = new ArrayList<>();
        bindings.add(new Binding(ThematicRole.Goal.IID,
                BindingTarget.iid(target), true, false));
        FrameBodyOld body = new FrameBodyOld(type, bindings);
        return new FrameOld(key, type, body, null, identity);
    }

    /** Create a bare frame wrapping a stored FrameBody, using the actual predicate. */
    public static FrameOld forFrameBody(ItemID predicate, ContentID cid, boolean identity, String displayName) {
        CompoundKey key = CompoundKey.of(predicate, cid != null ? cid.encodeText() : "?");
        List<Binding> bindings = new ArrayList<>();
        if (cid != null) {
            bindings.add(new Binding(ThematicRole.Topic.IID,
                    BindingTarget.ref(cid), true, false));
        }
        FrameBodyOld body = new FrameBodyOld(predicate, bindings);
        return new FrameOld(key, predicate, body, null, identity);
    }

    /** Create a bare frame (no display name). */
    public static FrameOld forFrameBody(ItemID predicate, ContentID cid, boolean identity) {
        return forFrameBody(predicate, cid, identity, null);
    }

    /**
     * Reconstruct a Frame from a {@link FrameBodyOld} and {@link FrameEndorsement}.
     *
     * <p>Used during hydration when manifests contain endorsements.
     */
    public static FrameOld fromFrameBody(FrameBodyOld body, FrameEndorsement endorsement) {
        FrameOld frame = new FrameOld(
                endorsement.key(),
                body.predicate(),
                body,
                endorsement.bodyHash(),
                hasIdentityBindings(body));
        return frame;
    }

    /**
     * Reconstruct a Frame from a {@link FrameBodyOld} alone (unendorsed frames).
     *
     * <p>Used for frames loaded from the index that are NOT in the item's
     * endorsement table — likes, annotations, trust attestations. The key
     * is derived from predicate + body hash.
     */
    public static FrameOld fromBody(FrameBodyOld body) {
        Objects.requireNonNull(body, "body");
        ContentID hash = body.hash();
        CompoundKey key = CompoundKey.of(body.predicate(), hash.displayAtWidth(12));
        return new FrameOld(key, body.predicate(), body, hash, false);
    }

    // ==================================================================================
    // Helpers
    // ==================================================================================

    private static boolean hasIdentityBindings(FrameBodyOld body) {
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
    // CBOR Encode/Decode
    // ==================================================================================

    /**
     * Encode this Frame to CBOR bytes, including mounts from EndorsementsTable.
     */
    public byte[] encodeCbor(List<Mount> frameMounts) {
        this.mounts = frameMounts != null ? frameMounts : List.of();
        return encodeBinary(Scope.RECORD);
    }

    /**
     * Encode this Frame to a CBOR tree, including mounts from EndorsementsTable.
     */
    CBORObject toCborTree(List<Mount> frameMounts) {
        this.mounts = frameMounts != null ? frameMounts : List.of();
        return toCborTree(Scope.RECORD);
    }

    /**
     * Decode a Frame from CBOR bytes.
     */
    public static FrameOld decodeCbor(byte[] bytes) {
        return Canonical.decodeBinary(bytes, FrameOld.class, Scope.RECORD);
    }

    /**
     * Result of decoding a Frame — carries both the Frame and its mounts.
     */
    public record FrameWithMounts(FrameOld frame, List<Mount> mounts) {}

    /**
     * Decode a Frame and its mounts from CBOR bytes.
     */
    public static FrameWithMounts decodeCborWithMounts(byte[] bytes) {
        FrameOld frame = decodeCbor(bytes);
        return new FrameWithMounts(frame, frame.decodedMounts());
    }

    /**
     * Mounts decoded from CBOR (extracted by EndorsementsTable after decode).
     */
    List<Mount> decodedMounts() {
        return mounts != null ? mounts : List.of();
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
