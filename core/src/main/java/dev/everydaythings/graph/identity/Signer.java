package dev.everydaythings.graph.identity;

import dev.everydaythings.graph.canonical.HashTree;
import dev.everydaythings.graph.identity.IdentityVocabulary.Inception;
import dev.everydaythings.graph.identity.vault.DelegationConditions;
import dev.everydaythings.graph.identity.vault.InMemoryVault;
import dev.everydaythings.graph.identity.vault.Vault;
import dev.everydaythings.graph.datum.Binding;
import dev.everydaythings.graph.datum.BindingTarget;
import dev.everydaythings.graph.datum.Body;
import dev.everydaythings.graph.datum.Frame;
import dev.everydaythings.graph.datum.Record;
import dev.everydaythings.graph.item.Item;
import dev.everydaythings.graph.value.Literal;
import dev.everydaythings.graph.id.CompoundKey;
import dev.everydaythings.graph.id.ContentID;
import dev.everydaythings.graph.id.DatumID;
import dev.everydaythings.graph.id.ItemID;
import dev.everydaythings.graph.id.ItemRef;
import dev.everydaythings.graph.runtime.Librarian;
import dev.everydaythings.graph.identity.IdentityVocabulary.Multikey;
import dev.everydaythings.graph.identity.IdentityVocabulary.Next;
import dev.everydaythings.graph.semantics.CoreVocabulary.Expires;
import dev.everydaythings.graph.semantics.CoreVocabulary.Sequence;
import dev.everydaythings.graph.semantics.ThematicRole;

import java.security.PublicKey;
import java.security.Signature;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * An Item that can sign things on its own behalf.
 *
 * <p>A Signer carries a {@link Vault} holding its private signing material. The
 * vault produces signed cryptographic events (INCEPTION, ROTATION, DELEGATION,
 * REVOCATION) as {@link Frame}s; this Signer orchestrates their persistence via
 * its librarian.
 *
 * <p>The Signer's {@link #iid()} is derived from the vault's initial signing
 * public key — cryptographically binding identity to key (an attacker can't
 * preemptively claim a Signer's IID). The IID is stable across key rotations.
 *
 * <h2>Construction lifecycle</h2>
 *
 * <p>Three construction modes:
 * <ul>
 *   <li><b>{@link #Signer(Vault, Librarian)}</b> — full Signer with vault and
 *       librarian. The constructor itself produces the four-datum genesis:
 *       INCEPTION body + record (via {@code vault.incept(Signing)}) and
 *       manifest body + record (via {@code commit}). When construction returns
 *       the Signer is fully formed — incepted, signed, and persisted.</li>
 *   <li><b>{@link #Signer(Vault)}</b> (protected) — vault-only Signer with no
 *       librarian. Useful for tests or for subclasses (like
 *       {@link Librarian} itself) that need to bind their librarian
 *       <i>after</i> the super-constructor returns. Inception is the caller's
 *       responsibility.</li>
 *   <li><b>{@link #Signer(ItemID)}</b> — identity-only Signer with no vault,
 *       no signing capability. Used when hydrating remote/observed Signers
 *       whose private key isn't local.</li>
 * </ul>
 */
public class Signer extends Item {

    /** Canonical key for Signer-the-archetype. */
    public static final String KEY = "cg.archetype:signer";

    /** The archetype IID for Signer instances. */
    public static final ItemID ARCHETYPE = ItemID.fromString(KEY);

    /** Default signing algorithm for in-memory Signers. */
    public static final Algorithm.Sign DEFAULT_ALGORITHM = Algorithm.Sign.ED25519;

    /** The vault holding this Signer's private signing material; null for identity-only. */
    private final Vault vault;

    @Override
    public ItemID archetype() {
        return ARCHETYPE;
    }

    /**
     * Identity-only constructor. The resulting Signer has no vault, no signing
     * capability — {@link #sign(byte[])} will throw, {@link #signingPublicKey()}
     * returns empty. Used when hydrating a Signer from a manifest where the
     * local node doesn't hold this principal's private key.
     */
    public Signer(ItemID iid) {
        super(iid);
        this.vault = null;
    }

    /**
     * Vault-only constructor — bind a vault, derive the IID from it, but do
     * <i>not</i> auto-incept (no librarian yet). Used by subclasses that need to
     * wire their librarian binding after super-construction (notably
     * {@link Librarian} itself, which is its own librarian). External callers
     * generally want {@link #Signer(Vault, Librarian)}; the vault-only variant
     * is also exposed via {@link #inMemory()} for tests that need a signing
     * object without persistence.
     */
    protected Signer(Vault vault) {
        super(vault.identity());
        this.vault = vault;
    }

    /**
     * Full Signer constructor — vault + librarian + auto-incepted signing track.
     *
     * <p>Four datums get persisted during construction: the INCEPTION body and
     * its self-attesting record (produced by {@code vault.incept(Signing)}), and
     * the manifest body and record (produced by {@link #commit}). When this
     * constructor returns, the Signer is a fully-published graph identity.
     *
     * <p>Idempotent on re-construction: if the vault's signing chain has already
     * been incepted (chainHead present), the INCEPTION step is skipped.
     */
    public Signer(Vault vault, Librarian librarian) {
        super(vault.identity(), librarian);
        this.vault = vault;
        selfIncept();
    }

    /**
     * Generate a fresh vault-bearing Signer with no librarian binding. The
     * returned Signer can sign in-place but does not auto-publish an INCEPTION.
     * For a fully-published Signer use {@link #inMemory(Librarian)}.
     */
    public static Signer inMemory() {
        return new Signer(InMemoryVault.generate(DEFAULT_ALGORITHM));
    }

    /**
     * Generate a fresh Signer bound to the given librarian, with its INCEPTION
     * frame and manifest already persisted as part of construction.
     */
    public static Signer inMemory(Librarian librarian) {
        return new Signer(InMemoryVault.generate(DEFAULT_ALGORITHM), librarian);
    }

    /** The vault holding this Signer's private cryptographic material, if any. */
    public Optional<Vault> vault() {
        return Optional.ofNullable(vault);
    }

    /** Whether this Signer holds private signing material and can produce signatures. */
    public boolean canSign() {
        return vault != null && vault.canSign();
    }

    /**
     * The signing algorithm this Signer uses, if it can sign.
     */
    public Optional<Algorithm.Sign> signingAlgorithm() {
        return vault == null ? Optional.empty() : vault.signingAlgorithm();
    }

    /**
     * Sign the given message bytes, returning a self-describing {@link VarSig}.
     *
     * @throws IllegalStateException if this Signer has no signing capability.
     */
    public VarSig sign(byte[] message) {
        if (vault == null) {
            throw new IllegalStateException("Signer has no signing key (identity-only)");
        }
        return vault.sign(message);
    }

    /**
     * The current signing public key as a self-describing {@link MultiKey}, or
     * empty if this Signer has no signing capability.
     */
    public Optional<MultiKey> signingPublicKey() {
        return vault == null ? Optional.empty() : vault.signingPublicKey();
    }

    /**
     * The pre-rotation commitment for the signing track — content-hash digest
     * over the next signing public key's multikey-encoded bytes. Empty if no
     * vault is attached.
     */
    public Optional<ContentID> signingNextKeyDigest() {
        return vault == null ? Optional.empty() : vault.signingNextKeyDigest();
    }

    // ==================================================================================
    // Event one-liners — protected, intended for subclass use and tests.
    //
    // Each asks the vault to produce a signed Frame and persists the result via
    // the librarian. State (chain heads, sequence) advances inside the vault.
    // Empty Optional returned if the Signer lacks vault or librarian binding.
    // ==================================================================================

    /**
     * Publish an INCEPTION for the given purpose. Most Signers are auto-incepted
     * on the signing track during construction; this method is for incepting
     * additional purposes (e.g., the encryption track) after construction, or
     * for subclasses that need explicit control.
     */
    protected Optional<Frame> inception(ItemID purpose) {
        if (vault == null || librarian == null) return Optional.empty();
        Frame frame = vault.incept(purpose);
        persistFrame(frame);
        return Optional.of(frame);
    }

    /** Publish a ROTATION for the given purpose's key track. */
    protected Optional<Frame> rotation(ItemID purpose) {
        if (vault == null || librarian == null) return Optional.empty();
        Frame frame = vault.rotate(purpose);
        persistFrame(frame);
        return Optional.of(frame);
    }

    /** Publish a DELEGATION granting the given delegate authority on the given purpose. */
    protected Optional<Frame> delegation(ItemID delegateId, ItemID purpose,
                                         DelegationConditions conditions) {
        if (vault == null || librarian == null) return Optional.empty();
        Frame frame = vault.delegate(delegateId, purpose, conditions);
        persistFrame(frame);
        return Optional.of(frame);
    }

    /** Publish a REVOCATION targeting the given binding-target with an optional reason. */
    protected Optional<Frame> revocation(BindingTarget target, ItemID reason) {
        if (vault == null || librarian == null) return Optional.empty();
        Frame frame = vault.revoke(target, reason);
        persistFrame(frame);
        return Optional.of(frame);
    }

    private void persistFrame(Frame frame) {
        librarian.persist(frame.body());
        for (Record r : frame.records()) {
            librarian.persist(r);
        }
    }

    /**
     * Self-publish the four-datum genesis for this Signer: INCEPTION body+record
     * (via {@code vault.incept(Signing)}) and manifest body+record (via
     * {@link #commit}). Idempotent — re-running on an already-incepted vault
     * skips the INCEPTION step.
     *
     * <p>Called automatically by {@link #Signer(Vault, Librarian)}. Subclasses
     * (notably {@link Librarian}) that use the protected vault-only constructor
     * must invoke this explicitly after binding their librarian.
     */
    protected void selfIncept() {
        if (vault == null) {
            throw new IllegalStateException("Signer has no vault; cannot self-incept");
        }
        if (librarian == null) {
            throw new IllegalStateException("Signer has no librarian; cannot self-incept");
        }
        if (vault.chainHead(IdentityVocabulary.Signing.IID).isEmpty()) {
            Frame inception = vault.incept(IdentityVocabulary.Signing.IID);
            persistFrame(inception);
        }
        if (this.current == null) {
            commit(this, List.of());
        }
    }

    // ==================================================================================
    // Public KEL walk — works against any Signer's published events
    // ==================================================================================

    /**
     * Walk the published KEL for the given identity and purpose; return the
     * currently-committed public keys.
     *
     * <p>Pure graph lookup — works regardless of this Signer's vault state, and
     * works for any identity (not just this Signer's own). Returns empty list
     * if the librarian binding is absent, or if no valid INCEPTION exists for
     * {@code identity} on {@code purpose}.
     *
     * <p>Phase 1 simplification: only INCEPTION frames are consulted. When
     * multiple INCEPTIONs exist for one (identity, purpose) (which shouldn't
     * happen normally — the bootstrap-twice case), the latest by {@code TIME}
     * binding wins. ROTATION chain replay comes in a later phase.
     */
    public List<MultiKey> currentKeys(ItemID identity, ItemID purpose) {
        if (librarian == null) return List.of();
        List<DatumID> candidates = librarian.library()
                .bodyCidsForReferenceBinding(ThematicRole.Theme.IID, identity);

        Frame chosen = null;
        java.time.Instant chosenTime = java.time.Instant.MIN;
        for (DatumID cid : candidates) {
            Frame frame = librarian.fetchFrame(cid).orElse(null);
            if (frame == null) continue;
            if (!isInceptionFor(frame, identity, purpose)) continue;
            java.time.Instant frameTime = readTime(frame.body()).orElse(java.time.Instant.MIN);
            if (frameTime.isAfter(chosenTime)) {
                chosen = frame;
                chosenTime = frameTime;
            }
        }
        if (chosen == null) return List.of();
        return committedKeys(chosen.body());
    }

    private static boolean isInceptionFor(Frame frame, ItemID identity, ItemID purpose) {
        if (!(frame.body().head() instanceof ItemRef ref)) return false;
        if (!Inception.IID.equals(ref.iid())) return false;
        return readTheme(frame.body()).filter(identity::equals).isPresent()
                && readPurpose(frame.body()).filter(purpose::equals).isPresent();
    }

    // ==================================================================================
    // Body-reader helpers — extract typed values from identity-event bodies.
    //
    // Hosted here on Signer because the predicate classes (Inception/Rotation/etc.)
    // hold no behavior of their own. Several of these aren't predicate-specific
    // (readTheme/readPurpose work on any body with those roles) — but housing them
    // on Signer is fine for now. If broader use emerges they can promote to Body.
    // ==================================================================================

    /** Read THEME → ItemID (non-compound ref target). */
    public static Optional<ItemID> readTheme(Body body) {
        return body.binding(CompoundKey.of(ThematicRole.Theme.IID))
                .map(b -> extractIid(b.target()))
                .filter(java.util.Objects::nonNull);
    }

    /** Read PURPOSE → ItemID. Used by INCEPTION/ROTATION/DELEGATION (single purpose). */
    public static Optional<ItemID> readPurpose(Body body) {
        return body.binding(CompoundKey.of(ThematicRole.Purpose.IID))
                .map(b -> extractIid(b.target()))
                .filter(java.util.Objects::nonNull);
    }

    /** Read AGENT → ItemID (delegator on a DELEGATION body). */
    public static Optional<ItemID> readAgent(Body body) {
        return body.binding(CompoundKey.of(ThematicRole.Agent.IID))
                .map(b -> extractIid(b.target()))
                .filter(java.util.Objects::nonNull);
    }

    /** Read FOLLOWS → ContentID (prior-event reference on a ROTATION body). */
    public static Optional<ContentID> readFollows(Body body) {
        return body.binding(CompoundKey.of(ThematicRole.Follows.IID))
                .map(b -> extractContentId(b.target()))
                .filter(java.util.Objects::nonNull);
    }

    /**
     * Read THEME as a content reference — for REVOCATION targeting a frame body's
     * CID rather than an item's IID.
     */
    public static Optional<ContentID> readThemeCid(Body body) {
        return body.binding(CompoundKey.of(ThematicRole.Theme.IID))
                .map(b -> extractContentId(b.target()))
                .filter(java.util.Objects::nonNull);
    }

    /**
     * Extract the committed (non-NEXT, Multikey-qualified) INSTRUMENT public keys
     * from an INCEPTION or ROTATION body.
     */
    public static List<MultiKey> committedKeys(Body body) {
        List<MultiKey> keys = new ArrayList<>();
        for (Binding b : body.bindings()) {
            if (!ThematicRole.Instrument.IID.equals(b.role())) continue;
            if (hasQualifier(b, Next.IID)) continue;
            if (!hasQualifier(b, Multikey.IID)) continue;
            if (!(b.target() instanceof Literal lit)) continue;
            try {
                keys.add(lit.asMultiKey());
            } catch (RuntimeException ignored) {
                // bad key bytes; skip
            }
        }
        return keys;
    }

    /**
     * Self-attestation check for an INCEPTION frame: at least one record's
     * signature must verify against one of the keys committed in the body.
     */
    public static boolean isSelfAttested(Frame frame) {
        List<MultiKey> keys = committedKeys(frame.body());
        if (keys.isEmpty()) return false;
        byte[] signedBytes = HashTree.signingPayload(frame.body());
        for (Record record : frame.records()) {
            VarSig sig = record.varsig();
            for (MultiKey key : keys) {
                if (verify(key, signedBytes, sig)) return true;
            }
        }
        return false;
    }

    /** Read ATTRIBUTE[Sequence] → ordinal position (ROTATION body). */
    public static Optional<Long> readSequence(Body body) {
        for (Binding b : body.bindings()) {
            if (!ThematicRole.Attribute.IID.equals(b.role())) continue;
            if (!hasQualifier(b, Sequence.IID)) continue;
            if (!(b.target() instanceof Literal lit)) continue;
            if (!Literal.TYPE_INTEGER.equals(lit.valueType())) continue;
            try {
                return Optional.of(lit.asInteger());
            } catch (RuntimeException ignored) {
                return Optional.empty();
            }
        }
        return Optional.empty();
    }

    /** Extract INSTRUMENT[NEXT] pre-rotation commitments from an INCEPTION/ROTATION body. */
    public static List<ContentID> nextKeyDigests(Body body) {
        List<ContentID> digests = new ArrayList<>();
        for (Binding b : body.bindings()) {
            if (!ThematicRole.Instrument.IID.equals(b.role())) continue;
            if (!hasQualifier(b, Next.IID)) continue;
            ContentID cid = extractContentId(b.target());
            if (cid != null) digests.add(cid);
        }
        return digests;
    }

    /**
     * Read all PURPOSE bindings (multiset) from a DELEGATION body — the scope
     * sememes this delegation covers. Empty list means fully general authority.
     */
    public static List<ItemID> readPurposes(Body body) {
        List<ItemID> purposes = new ArrayList<>();
        for (Binding b : body.bindings()) {
            if (!ThematicRole.Purpose.IID.equals(b.role())) continue;
            ItemID iid = extractIid(b.target());
            if (iid != null) purposes.add(iid);
        }
        return purposes;
    }

    /** Read ATTRIBUTE[Expires] → Instant from a DELEGATION body. */
    public static Optional<Instant> readExpires(Body body) {
        for (Binding b : body.bindings()) {
            if (!ThematicRole.Attribute.IID.equals(b.role())) continue;
            if (!hasQualifier(b, Expires.IID)) continue;
            if (!(b.target() instanceof Literal lit)) continue;
            if (!Literal.TYPE_INSTANT.equals(lit.valueType())) continue;
            try {
                return Optional.of(lit.asInstantMillis());
            } catch (RuntimeException ignored) {
                return Optional.empty();
            }
        }
        return Optional.empty();
    }

    private static Optional<Instant> readTime(Body body) {
        return body.binding(CompoundKey.of(ThematicRole.Time.IID))
                .flatMap(b -> {
                    if (!(b.target() instanceof Literal lit)) return Optional.empty();
                    try {
                        return Optional.of(lit.asInstantMillis());
                    } catch (RuntimeException ignored) {
                        return Optional.empty();
                    }
                });
    }

    private static ItemID extractIid(BindingTarget target) {
        if (target instanceof BindingTarget.RefTarget ref && !ref.isCompound()) {
            return ref.asItemId();
        }
        return null;
    }

    private static ContentID extractContentId(BindingTarget target) {
        if (target instanceof BindingTarget.RefTarget ref) {
            // Prefer ContentRef-shaped targets (the canonical encoding for
            // raw content like next-key digests). Fall back to FrameRef body
            // CIDs (for content-of-a-datum references like FOLLOWS).
            ContentID cid = ref.asCid();
            if (cid != null) return cid;
            DatumID datumId = ref.asDatumId();
            if (datumId != null) return ContentID.of(datumId.encodeBinary());
        }
        return null;
    }

    private static boolean hasQualifier(Binding b, ItemID qualifierIid) {
        return b.qualifiers().stream()
                .anyMatch(q -> q instanceof CompoundKey.Sememe s
                        && qualifierIid.equals(s.id()));
    }

    /**
     * Verify a signature against a public key and message bytes.
     *
     * @return true if the signature is valid; false otherwise (including all error cases)
     */
    public static boolean verify(MultiKey publicKey, byte[] message, VarSig varsig) {
        try {
            Algorithm.Sign alg = Algorithm.Sign.byVarsigCode(varsig.code());
            PublicKey pub = InMemoryVault.publicKeyFromRaw(publicKey.rawKey(), alg);
            Signature sig = Signature.getInstance(alg.signatureName());
            sig.initVerify(pub);
            sig.update(message);
            return sig.verify(varsig.rawSig());
        } catch (Exception e) {
            return false;
        }
    }
}
