package dev.everydaythings.graph.identity.vault;


import dev.everydaythings.graph.identity.MultiKey;
import dev.everydaythings.graph.identity.VarSig;
import dev.everydaythings.graph.datum.BindingTarget;
import dev.everydaythings.graph.datum.Frame;
import dev.everydaythings.graph.identity.IdentityVocabulary;
import dev.everydaythings.graph.identity.Signer;
import dev.everydaythings.graph.id.ContentRef;
import dev.everydaythings.graph.id.DatumRef;
import dev.everydaythings.graph.id.ItemRef;

import java.util.Optional;

/**
 * The custodial home for a {@link Signer}'s
 * private cryptographic material.
 *
 * <p>A Vault holds private keys and produces signed cryptographic events
 * ({@link Frame}s for INCEPTION, ROTATION, DELEGATION, REVOCATION) — frames are
 * the vault's native output format. The vault is the only place that can produce
 * them because it's the only place with the private keys to sign them.
 *
 * <p>Vaults are runtime-only — not graph objects, not addressable, not persisted
 * in the graph. They're attached to a Signer at construction. Frames produced by
 * the vault <i>are</i> graph objects: they're the public record of what the
 * vault did.
 *
 * <p>Multi-purpose key management: a vault can hold independent key tracks for
 * different cryptographic purposes (signing, encryption, key-agreement). Each
 * purpose has its own KEL (Key Event Log) chain. Per-purpose methods on this
 * interface let callers query the right keys and produce the right events.
 *
 * <p>Locking: implementations that wrap encrypted-at-rest material can lock to
 * burn the in-memory plaintext. {@link #isLocked()} reports the state. When
 * locked, signing and event-producing methods throw {@link VaultLockedException};
 * state queries return empty {@link Optional}s rather than throw. Public-key
 * lookup for a given identity does <i>not</i> depend on vault state — keys live
 * in the published INCEPTION/ROTATION frames and are reachable through normal
 * graph queries, regardless of which vault (if any) is currently unlocked.
 *
 * <p>Implementations vary by storage and hardware:
 * <ul>
 *   <li>{@link InMemoryVault} — keypairs in process memory; ephemeral; tests/demos</li>
 *   <li>(future) encrypted-file vault — passphrase-protected disk storage</li>
 *   <li>(future) OS-keychain vault — delegates to platform secure storage</li>
 *   <li>(future) TPM / Secure Enclave / HSM / FIDO2 vaults — hardware-backed</li>
 *   <li>(future) personal-device proxy — keys live on user's phone; vault relays</li>
 * </ul>
 */
public interface Vault {

    // ==================================================================================
    // Identity
    // ==================================================================================

    /**
     * The ItemRef this vault is bound to — derived from the initial signing public
     * key. Stable across rotations: the signing key changes, the IID does not.
     *
     * <p>Default implementation derives the IID from the current signing public
     * key encoding. Implementations holding multiple historical signing keypairs
     * (post-rotation) should override to return the IID derived from the
     * <i>first</i> signing public key — not the current one.
     *
     * @throws IllegalStateException if this vault has no signing material to
     *         derive an identity from
     */
    default ItemRef identity() {
        return signingPublicKey()
                .map(mk -> ItemRef.fromMultikeyBytes(mk.encoded()))
                .orElseThrow(() -> new IllegalStateException(
                        "Vault has no signing key; cannot derive identity"));
    }

    // ==================================================================================
    // Per-purpose state queries
    //
    // All return empty if the vault is locked or this purpose has not been incepted.
    // Locked-ness does NOT block reading public keys from the graph — that path
    // goes through the published frame chain, not through any vault.
    // ==================================================================================

    /** The current public key for this purpose, if any. */
    default Optional<MultiKey> publicKey(ItemRef purpose) {
        if (ItemRef.iid(IdentityVocabulary.Signing.KEY).equals(purpose)) return signingPublicKey();
        if (ItemRef.iid(IdentityVocabulary.KeyAgreement.KEY).equals(purpose)) return keyAgreementPublicKey();
        return Optional.empty();
    }

    /**
     * The pre-rotation commitment for this purpose — content-hash digest over the
     * next public key's multikey-encoded bytes. Empty if this purpose has no
     * forward commitment or the vault is locked.
     */
    default Optional<ContentRef> nextKeyDigest(ItemRef purpose) {
        if (ItemRef.iid(IdentityVocabulary.Signing.KEY).equals(purpose)) return signingNextKeyDigest();
        if (ItemRef.iid(IdentityVocabulary.KeyAgreement.KEY).equals(purpose)) return keyAgreementNextKeyDigest();
        return Optional.empty();
    }

    /**
     * The {@link DatumRef} of the most recent establishment-event body on this
     * purpose's KEL — INCEPTION on first establish, then each ROTATION. Empty if
     * this purpose has not been incepted yet (or the vault is locked).
     */
    default Optional<DatumRef> chainHead(ItemRef purpose) {
        return Optional.empty();
    }

    /**
     * The ordinal position of this purpose's KEL head. Zero before INCEPTION;
     * one after INCEPTION; increments with each ROTATION.
     */
    default long sequence(ItemRef purpose) {
        return 0L;
    }

    // ==================================================================================
    // Events — each mutates internal vault state and returns a SIGNED Frame ready
    // to publish via the librarian. Default implementations throw because Phase 1
    // wires these on InMemoryVault (Vault stage 2 of the refactor).
    //
    // All event methods throw VaultLockedException when the vault is locked.
    // ==================================================================================

    /**
     * Establish the first key-state for the given purpose. Generates the
     * INCEPTION body, signs it with the just-committed key (self-attestation),
     * advances vault state to {@code sequence=1}, and returns the assembled
     * {@link Frame}.
     *
     * @throws IllegalStateException if this purpose has already been incepted
     * @throws VaultLockedException  if the vault is locked
     */
    default Frame incept(ItemRef purpose) {
        throw new UnsupportedOperationException(
                getClass().getSimpleName() + ".incept(purpose) not yet implemented");
    }

    /**
     * Advance keys for the given purpose. The current keypair is burned, the
     * pre-committed next keypair becomes current, and a fresh next keypair is
     * generated. The ROTATION body reveals the preimage of the prior next-key
     * commitment, follows the prior establishment event by content-reference, and
     * carries the new pre-rotation commitment. The body is signed by BOTH the
     * old and new signing keys (rigorous form: authorized by old, proven by new).
     *
     * @throws IllegalStateException if this purpose has not been incepted yet
     * @throws VaultLockedException  if the vault is locked
     */
    default Frame rotate(ItemRef purpose) {
        throw new UnsupportedOperationException(
                getClass().getSimpleName() + ".rotate(purpose) not yet implemented");
    }

    /**
     * Author a delegation from this vault's identity (delegator) to the given
     * delegate identity, scoped to the given purpose, with the given conditions.
     *
     * @throws VaultLockedException if the vault is locked
     */
    default Frame delegate(ItemRef delegateId, ItemRef purpose, DelegationConditions conditions) {
        throw new UnsupportedOperationException(
                getClass().getSimpleName() + ".delegate(...) not yet implemented");
    }

    /**
     * Author a revocation of the given target — an identity, an event body, a
     * delegation, a trust assertion, or any prior signed assertion. The
     * optional reason names a formal cause-sememe (e.g.,
     * {@code cg.reason:compromise}); pass {@code null} for no formal reason.
     *
     * @throws VaultLockedException if the vault is locked
     */
    default Frame revoke(Object target, ItemRef reason) {
        throw new UnsupportedOperationException(
                getClass().getSimpleName() + ".revoke(...) not yet implemented");
    }

    // ==================================================================================
    // Generic signing primitive — for signing arbitrary frame bodies, not identity events
    // ==================================================================================

    /**
     * Produce a self-describing {@link VarSig} over the given message bytes using
     * the current signing-track private key.
     *
     * @throws IllegalStateException if this vault holds no signing keys
     * @throws VaultLockedException  if the vault is locked
     */
    VarSig sign(byte[] message);

    /**
     * Sign with a specific purpose's current private key. Most callers want
     * {@link #sign(byte[])} which targets the signing track.
     *
     * @throws IllegalStateException if this vault holds no keys for the given purpose
     * @throws VaultLockedException  if the vault is locked
     */
    default VarSig sign(byte[] message, ItemRef purpose) {
        if (ItemRef.iid(IdentityVocabulary.Signing.KEY).equals(purpose)) return sign(message);
        throw new UnsupportedOperationException(
                "Sign with purpose " + purpose + " not yet implemented");
    }

    // ==================================================================================
    // Locking — encrypted vaults can be locked; in-memory vaults are always unlocked.
    //
    // The interface deliberately does NOT commit to a credential type for unlock —
    // that's implementation-specific (passphrase, biometric, hardware presence,
    // peer message from another device). Locking implementations expose their own
    // typed unlock methods. Pure interface callers only need to read isLocked().
    // ==================================================================================

    /** Whether this vault is currently locked (cannot sign or produce events). */
    default boolean isLocked() {
        return false;
    }

    /**
     * Lock the vault. After locking, signing and event-producing methods throw
     * {@link VaultLockedException}; state queries return empty. No-op for vaults
     * that don't support locking.
     */
    default void lock() {}

    // ==================================================================================
    // Signing track (legacy single-purpose API — preserved for callers; see
    // publicKey(purpose) / nextKeyDigest(purpose) for the per-purpose form)
    // ==================================================================================

    /**
     * The signing-algorithm sememe IID in use, if this vault holds signing keys.
     * Returns the algorithm sememe's identity (e.g.,
     * {@code @cg.algorithm:ed25519}) — callers can resolve to a runtime
     * {@link dev.everydaythings.graph.identity.algorithm.Signing} via the
     * librarian when verification machinery is needed.
     */
    Optional<ItemRef> signingAlgorithm();

    /** The current signing public key, if this vault holds signing keys. */
    Optional<MultiKey> signingPublicKey();

    /**
     * The pre-rotation commitment for the signing track — content-hash digest over
     * the next signing public key's multikey-encoded bytes. Published in
     * {@code INSTRUMENT [NEXT]} on signing-track INCEPTION / ROTATION events.
     */
    Optional<ContentRef> signingNextKeyDigest();

    /** Whether this vault can currently sign (has a signing key and isn't locked). */
    default boolean canSign() {
        return signingAlgorithm().isPresent() && !isLocked();
    }

    // ==================================================================================
    // Key-agreement track (Phase 2 — methods declared for forward extension;
    // current InMemoryVault returns empty until X25519-in-Vault work begins).
    //
    // Holds the long-term keypair (typically X25519) used to derive shared
    // secrets with peers.  The keypair is NOT used to encrypt content directly
    // — that's an AEAD step at the content layer with a derived symmetric key.
    // The vault's role here is only the Diffie-Hellman half: combining its
    // private key with a peer's public key to produce a shared secret.
    // ==================================================================================

    /**
     * The key-agreement-algorithm sememe IID in use, if this vault holds
     * key-agreement keys.  Returns the algorithm sememe's identity (e.g.,
     * {@code @cg.algorithm:x25519} or {@code @cg.algorithm:ecdh-es-hkdf-256}).
     */
    default Optional<ItemRef> keyAgreementAlgorithm() {
        return Optional.empty();
    }

    /** The current key-agreement public key, if this vault holds one. */
    default Optional<MultiKey> keyAgreementPublicKey() {
        return Optional.empty();
    }

    /** The pre-rotation commitment for the key-agreement track. */
    default Optional<ContentRef> keyAgreementNextKeyDigest() {
        return Optional.empty();
    }

    /** Whether this vault can currently perform key agreement. */
    default boolean canKeyAgree() {
        return keyAgreementAlgorithm().isPresent() && !isLocked();
    }

    /**
     * Derive a shared secret by pairing this vault's key-agreement private key
     * with the given peer's public key.  Returns the raw shared-secret bytes;
     * callers feed them into a KDF (HKDF-SHA-256 etc.) to produce a content
     * key.
     *
     * @throws IllegalStateException if this vault holds no key-agreement keys
     * @throws VaultLockedException  if the vault is locked
     */
    default byte[] agree(java.security.PublicKey peerPublicKey) {
        throw new UnsupportedOperationException(
                getClass().getSimpleName() + ".agree(...) not yet implemented");
    }

    // ==================================================================================
    // Signed pre-key (for asynchronous Double-Ratchet session establishment via X3DH)
    // ==================================================================================

    /**
     * The signer's published X25519 signed pre-key, if this vault holds one.
     * Used by peers as input to X3DH when opening a Double-Ratchet session.
     * The private side stays in the vault; the public side is published as a
     * SignedPreKey frame.
     */
    default Optional<MultiKey> signedPreKeyPublicKey() {
        return Optional.empty();
    }

    /**
     * Build a self-signed SignedPreKey {@link Frame} ready to publish: body
     * declaring the signer's current X25519 pre-key with thematic-role
     * bindings (THEME=identity, INSTRUMENT[Multikey]=key, PURPOSE=KeyAgreement,
     * TIME=now), record signed by the signing track.  Caller persists the
     * frame.
     */
    default Frame signedPreKeyFrame() {
        throw new UnsupportedOperationException(
                getClass().getSimpleName() + ".signedPreKeyFrame() not yet implemented");
    }

    // ==================================================================================
    // Double Ratchet sessions
    //
    // Session state lives in the vault, not in the graph.  Keyed by peer IID.
    // Sessions are 1↔1 with each peer; group communication is composed at a
    // layer above (pairwise fanout or hybrid key-wrap, see project docs).
    // ==================================================================================

    /**
     * Open a Double-Ratchet session to the named peer as the initiator.
     * Runs X3DH against the peer's identity key and signed pre-key, seeds the
     * Double-Ratchet root key, and stores the session state in the vault
     * keyed by {@code peerIid}.
     *
     * <p>The initiator's identity and ephemeral pubkeys are recorded as
     * "bootstrap bindings" on outgoing messages so the recipient can run
     * X3DH's responder path on the first message they receive.  The
     * recipient does not need a prior {@code acceptSessionFrom} call — they
     * just call {@link #decryptInSession} and the vault auto-bootstraps if
     * the message carries INITIATOR_IDENTITY_KEY / INITIATOR_EPHEMERAL_KEY
     * bindings and no session exists.
     */
    default void openSessionTo(ItemRef peerIid, MultiKey peerIkPub, MultiKey peerSpkPub) {
        throw new UnsupportedOperationException(
                getClass().getSimpleName() + ".openSessionTo(...) not yet implemented");
    }

    /**
     * Encrypt {@code plaintext} for the named peer using the established
     * session.  Returns the AEAD ciphertext (to live in the
     * {@code Opaque.Encrypted} body) plus the bindings list to put on the
     * accompanying record.  See {@link
     * dev.everydaythings.graph.identity.DoubleRatchet.EncryptedMessage}.
     */
    default dev.everydaythings.graph.identity.DoubleRatchet.EncryptedMessage encryptInSession(
            ItemRef peerIid, byte[] plaintext) {
        throw new UnsupportedOperationException(
                getClass().getSimpleName() + ".encryptInSession(...) not yet implemented");
    }

    /**
     * Decrypt a session message from the named peer.  Takes the ciphertext
     * (from the {@code Opaque.Encrypted} body) plus the bindings (from the
     * accompanying record) and returns the plaintext.
     */
    default byte[] decryptInSession(ItemRef peerIid,
                                    dev.everydaythings.graph.identity.DoubleRatchet.EncryptedMessage message) {
        throw new UnsupportedOperationException(
                getClass().getSimpleName() + ".decryptInSession(...) not yet implemented");
    }

    /** Whether a session exists with the given peer. */
    default boolean hasSessionWith(ItemRef peerIid) {
        return false;
    }

    /** Discard the session with the given peer.  Idempotent. */
    default void closeSession(ItemRef peerIid) {
        // no-op default
    }
}
