package dev.everydaythings.graph.identity.vault;


import dev.everydaythings.graph.identity.Algorithm;
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
        if (ItemRef.iid(IdentityVocabulary.Encryption.KEY).equals(purpose)) return encryptionPublicKey();
        return Optional.empty();
    }

    /**
     * The pre-rotation commitment for this purpose — content-hash digest over the
     * next public key's multikey-encoded bytes. Empty if this purpose has no
     * forward commitment or the vault is locked.
     */
    default Optional<ContentRef> nextKeyDigest(ItemRef purpose) {
        if (ItemRef.iid(IdentityVocabulary.Signing.KEY).equals(purpose)) return signingNextKeyDigest();
        if (ItemRef.iid(IdentityVocabulary.Encryption.KEY).equals(purpose)) return encryptionNextKeyDigest();
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

    /** The signing algorithm in use, if this vault holds signing keys. */
    Optional<Algorithm.Sign> signingAlgorithm();

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
    // Encryption track (Phase 2 — methods declared for forward extension; current
    // InMemoryVault returns empty until encryption work begins)
    // ==================================================================================

    /** The encryption algorithm in use, if this vault holds encryption keys. */
    default Optional<Algorithm.KeyMgmt> encryptionAlgorithm() {
        return Optional.empty();
    }

    /** The current encryption public key, if this vault holds encryption keys. */
    default Optional<MultiKey> encryptionPublicKey() {
        return Optional.empty();
    }

    /** The pre-rotation commitment for the encryption track. */
    default Optional<ContentRef> encryptionNextKeyDigest() {
        return Optional.empty();
    }

    /** Whether this vault can currently encrypt / do key agreement. */
    default boolean canEncrypt() {
        return encryptionAlgorithm().isPresent() && !isLocked();
    }
}
