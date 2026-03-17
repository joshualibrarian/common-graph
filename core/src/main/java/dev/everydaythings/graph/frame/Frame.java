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
public final class Frame implements Canonical {

    // ==================================================================================
    // Serialized fields (@Canon)
    // ==================================================================================

    /** The semantic address within the item. */
    @Canon(order = 0)
    private FrameKey key;

    /** The type ID (predicate of the body — defines codec/behavior). */
    @Canon(order = 1)
    private ItemID type;

    /** Whether this frame contributes to version identity. */
    @Canon(order = 2)
    private boolean identity;

    /** Hash of the body (for endorsement). */
    @Canon(order = 3)
    private ContentID bodyHash;

    /** Mounts — owned by EndorsementsTable at runtime, serialized here for CBOR. */
    @Canon(order = 4)
    private List<Mount> mounts = List.of();

    // alias field REMOVED — display names come from TokenDictionary/sememe resolution

    // ==================================================================================
    // Transient runtime fields
    // ==================================================================================

    /** The semantic assertion (predicate, theme, bindings). */
    private transient FrameBody body;

    /** Live decoded instance (e.g., the Vault, Log, or String). */
    private transient Object instance;

    /** Parent item reference (for Ref navigation). */
    private transient Item owner;

    /** Per-frame policy override (transient — encoded into FrameBody Config binding on commit). */
    private transient PolicySet policy;

    /** Attestation records (transient — populated from RECORD_BY_BODY index for unendorsed frames). */
    private transient List<FrameRecord> records;

    // ==================================================================================
    // Constructors
    // ==================================================================================

    public Frame(FrameKey key, ItemID type, FrameBody body, ContentID bodyHash, boolean identity) {
        this.key = Objects.requireNonNull(key, "key");
        this.type = Objects.requireNonNull(type, "type");
        this.body = body;
        this.bodyHash = bodyHash;
        this.identity = identity;
    }

    /** No-arg constructor for Canonical decode. */
    @SuppressWarnings("unused")
    private Frame() {}

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
    // alias() DELETED — display names resolved via TokenDictionary
    public List<FrameRecord> records() { return records != null ? records : List.of(); }

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
    public void setRecords(List<FrameRecord> records) { this.records = records; }

    /**
     * Set the per-frame policy override.
     *
     * <p>Stores the policy in the transient field and encodes it into the
     * FrameBody's config map so it is committed with the frame.
     */
    public void setPolicy(PolicySet policy) {
        this.policy = policy;
        if (body == null) return;
        // Encode the policy into a FrameConfig and store in the config map
        FrameConfig cfg = FrameConfig.builder().policy(policy).build();
        byte[] configBytes = cfg.encodeBinary(Canonical.Scope.RECORD);
        Literal configLiteral = new Literal(Literal.TYPE_CBOR, configBytes);
        setBody(body.withConfig(ThematicRole.Config.SEED.iid(), configLiteral));
    }

    // ==================================================================================
    // Convenience
    // ==================================================================================

    /** Is this a bare frame body (no component wrapper — type == FrameBody.TYPE_ID)? */
    public boolean isBareFrame() {
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
    public static Frame snapshot(FrameKey key, ItemID type, ContentID cid, boolean identity) {
        List<Binding> bindings = new ArrayList<>();
        if (cid != null) {
            bindings.add(new Binding(ThematicRole.Topic.SEED.iid(),
                    BindingTarget.ref(cid), true, false));
        }
        FrameBody body = new FrameBody(type, bindings);
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
        FrameBody body = new FrameBody(type, bindings);
        return new Frame(key, type, body, null, identity);
    }

    /** Create a local/external resource frame (identity defaults to false). */
    public static Frame localResource(FrameKey key, ItemID type, boolean identity) {
        List<Binding> bindings = new ArrayList<>();
        List<ItemID> externalKey = List.of(
                ThematicRole.Topic.SEED.iid(), CoreVocabulary.External.SEED.iid());
        bindings.add(Binding.compound(externalKey,
                Literal.ofText(""), false, false));
        FrameBody body = new FrameBody(type, bindings);
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
        FrameBody body = new FrameBody(type, bindings);
        return new Frame(key, type, body, null, false);
    }

    /** Create a reference frame with explicit identity flag. */
    public static Frame reference(FrameKey key, ItemID type, ItemID target, boolean identity) {
        Objects.requireNonNull(target, "reference target");
        List<Binding> bindings = new ArrayList<>();
        bindings.add(new Binding(ThematicRole.Goal.SEED.iid(),
                BindingTarget.iid(target), true, false));
        FrameBody body = new FrameBody(type, bindings);
        return new Frame(key, type, body, null, identity);
    }

    /** Create a bare frame (type = FrameBody.TYPE_ID, for unendorsed semantic assertions). */
    public static Frame forFrameBody(ItemID predicate, ContentID cid, boolean identity, String displayName) {
        FrameKey key = FrameKey.literal("frame:" + cid.encodeText());
        List<Binding> bindings = new ArrayList<>();
        if (cid != null) {
            bindings.add(new Binding(ThematicRole.Topic.SEED.iid(),
                    BindingTarget.ref(cid), true, false));
        }
        FrameBody body = new FrameBody(FrameBody.TYPE_ID, bindings);
        return new Frame(key, FrameBody.TYPE_ID, body, null, identity);
    }

    /** Create a bare frame (no display name). */
    public static Frame forFrameBody(ItemID predicate, ContentID cid, boolean identity) {
        return forFrameBody(predicate, cid, identity, null);
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

    /**
     * Reconstruct a Frame from a {@link FrameBody} alone (unendorsed frames).
     *
     * <p>Used for frames loaded from the index that are NOT in the item's
     * endorsement table — likes, annotations, trust attestations. The key
     * is derived from predicate + body hash.
     */
    public static Frame fromBody(FrameBody body) {
        Objects.requireNonNull(body, "body");
        ContentID hash = body.hash();
        FrameKey key = FrameKey.literal(body.predicate().encodeText() + ":" + hash.displayAtWidth(12));
        return new Frame(key, body.predicate(), body, hash, false);
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
    public static Frame decodeCbor(byte[] bytes) {
        return Canonical.decodeBinary(bytes, Frame.class, Scope.RECORD);
    }

    /**
     * Result of decoding a Frame — carries both the Frame and its mounts.
     */
    public record FrameWithMounts(Frame frame, List<Mount> mounts) {}

    /**
     * Decode a Frame and its mounts from CBOR bytes.
     */
    public static FrameWithMounts decodeCborWithMounts(byte[] bytes) {
        Frame frame = decodeCbor(bytes);
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
