package dev.everydaythings.graph.item.user;

import dev.everydaythings.graph.crypt.Algorithm;
import dev.everydaythings.graph.crypt.MultiKey;
import dev.everydaythings.graph.crypt.VarSig;
import dev.everydaythings.graph.crypt.vault.InMemoryVault;
import dev.everydaythings.graph.crypt.vault.Vault;
import dev.everydaythings.graph.frame.Binding;
import dev.everydaythings.graph.frame.BindingTarget;
import dev.everydaythings.graph.frame.Body;
import dev.everydaythings.graph.frame.Frame;
import dev.everydaythings.graph.identity.IdentityVocabulary;
import dev.everydaythings.graph.identity.Inception;
import dev.everydaythings.graph.item.Item;
import dev.everydaythings.graph.item.Literal;
import dev.everydaythings.graph.item.id.CompoundKey;
import dev.everydaythings.graph.item.id.ContentID;
import dev.everydaythings.graph.item.id.ItemID;
import dev.everydaythings.graph.item.id.ItemRef;
import dev.everydaythings.graph.semantics.CoreVocabulary;
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
 * <p>A Signer carries a {@link Vault} holding its private signing material —
 * current keypair plus a pre-rotation next keypair. The vault encapsulates key
 * custody (in-memory, encrypted file, OS keychain, hardware-backed, etc.) so the
 * Signer's API is uniform regardless of where keys live.
 *
 * <p>The Signer's {@link #iid()} is independent of its keys. The IID is the
 * stable cryptographic identity of the principal; the public key is one of
 * potentially many keys this principal has held over time. Key rotation does
 * not change the IID.
 *
 * <p>Two construction modes:
 * <ul>
 *   <li>{@link #inMemory()} — fresh Signer with a freshly-generated InMemoryVault
 *       (random IID, two Ed25519 keypairs). Used for tests, demos, ephemeral runs.</li>
 *   <li>{@link #Signer(ItemID)} — identity-only construction with no Vault.
 *       {@link #sign(byte[])} will throw, {@link #publicKey()} returns empty.
 *       Used when hydrating a Signer from a manifest where the local node
 *       doesn't hold this principal's private key (e.g., observing another
 *       principal in the trust graph).</li>
 * </ul>
 *
 * <p>Phase 1 vault implementation is in-memory only. Persistent vaults
 * (encrypted file, OS keychain, hardware-backed) come in later phases.
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
     * Construct a Signer bound to a vault. Protected — external callers should
     * use a factory like {@link #inMemory()} that creates a fresh vault internally.
     * This constructor exists for factories and for subclasses (e.g., Librarian).
     */
    protected Signer(ItemID iid, Vault vault) {
        super(iid);
        this.vault = vault;
    }

    /**
     * Generate a fresh in-memory Signer with a freshly-generated
     * {@link InMemoryVault} (two Ed25519 keypairs: current + pre-rotation next).
     * The IID is derived from the initial signing public key
     * ({@link ItemID#fromMultikeyBytes}) — cryptographically binding identity to
     * key, so an attacker can't preemptively claim this Signer's IID.
     *
     * <p>The vault is created internally — callers don't pass one in.
     *
     * <p>No librarian binding by default — call {@link #publishSelfInception()}
     * after binding to a librarian if you want the self-INCEPTION published.
     */
    public static Signer inMemory() {
        InMemoryVault vault = InMemoryVault.generate(DEFAULT_ALGORITHM);
        ItemID iid = ItemID.fromMultikeyBytes(
                vault.signingPublicKey().orElseThrow().encoded());
        return new Signer(iid, vault);
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
     *
     * <p>This digest is what gets committed in {@code INSTRUMENT [NEXT]} on the
     * INCEPTION event. The next public key itself is held privately in the vault
     * and revealed only on rotation.
     */
    public Optional<ContentID> signingNextKeyDigest() {
        return vault == null ? Optional.empty() : vault.signingNextKeyDigest();
    }

    /**
     * Publish this Signer's self-attested INCEPTION on the signing track via its
     * bound librarian. The body commits the current public key (in {@code INSTRUMENT}),
     * the digest of the next public key (in {@code INSTRUMENT [NEXT]} for pre-rotation),
     * and a timestamp.
     *
     * <p>No-op if the Signer has no librarian binding or no signing capability.
     * Intended to be called once per Signer; idempotency guard (don't republish if
     * already incepted) will be added when the key-state cache lands. For now,
     * repeated calls publish duplicate INCEPTIONs with different timestamps.
     *
     * @return the assembled INCEPTION frame, or empty if this Signer can't publish
     */
    public Optional<Frame> publishSelfInception() {
        if (librarian == null) return Optional.empty();
        Optional<MultiKey> currentKey = signingPublicKey();
        if (currentKey.isEmpty()) return Optional.empty();

        ArrayList<Binding> bindings = new ArrayList<>();
        bindings.add(Binding.ref(ThematicRole.Theme.IID, this.iid));
        bindings.add(Binding.ref(ThematicRole.Purpose.IID, IdentityVocabulary.Signing.IID));
        bindings.add(new Binding(
                ThematicRole.Instrument.IID,
                List.of(),
                Literal.ofMultiKey(currentKey.get())));
        signingNextKeyDigest().ifPresent(digest -> bindings.add(new Binding(
                ThematicRole.Instrument.IID,
                List.of(new CompoundKey.Sememe(CoreVocabulary.Next.IID)),
                BindingTarget.ref(digest))));
        bindings.add(new Binding(
                ThematicRole.Time.IID,
                List.of(),
                Literal.ofInstant(Instant.now())));

        Body inceptionBody = Body.of(ItemRef.of(Inception.IID), bindings);
        return Optional.of(librarian.assembleFrame(inceptionBody, this));
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
