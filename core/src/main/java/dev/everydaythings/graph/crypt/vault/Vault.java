package dev.everydaythings.graph.crypt.vault;

import dev.everydaythings.graph.crypt.Algorithm;
import dev.everydaythings.graph.crypt.MultiKey;
import dev.everydaythings.graph.crypt.VarSig;
import dev.everydaythings.graph.item.id.ContentID;

import java.util.Optional;

/**
 * The custodial home for a Signer's private cryptographic material.
 *
 * <p>A Vault holds private keys and exposes the operations that need them
 * (signing, decryption, key agreement) while keeping the keys themselves inside.
 * It owns the entire process of generating, storing, and serving keys: a fresh
 * vault generates its own keypairs at construction; a persistent vault loads
 * them from secure storage; a hardware-backed vault never has them in software
 * memory at all.
 *
 * <p>Vaults are 1:1 with Signers — a Signer creates and owns its own vault. No
 * vault sharing across identities.
 *
 * <p>Multi-track key management: a Signer may hold keys for multiple
 * cryptographic purposes (signing, encryption, key-agreement, etc.) on independent
 * key-state chains. Per-track methods on this interface let callers query the
 * keys appropriate to each purpose. Phase 1 implementations support the signing
 * track only; encryption-track methods return empty until that work begins.
 *
 * <p>Notably absent from the interface: any way to obtain the <i>next</i> public
 * key (held privately, revealed only on rotation), or the private keys directly.
 * The digest of the next public key is the only forward commitment exposed
 * publicly (via {@link #signingNextKeyDigest()}).
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
    // Signing track
    // ==================================================================================

    /**
     * The signing algorithm in use, if this vault holds signing keys.
     */
    Optional<Algorithm.Sign> signingAlgorithm();

    /**
     * The current signing public key, if this vault holds signing keys.
     */
    Optional<MultiKey> signingPublicKey();

    /**
     * The pre-rotation commitment for the signing track — content-hash digest over
     * the next signing public key's multikey-encoded bytes. Published in
     * {@code INSTRUMENT [NEXT]} on signing-track INCEPTION / ROTATION events.
     */
    Optional<ContentID> signingNextKeyDigest();

    /**
     * Produce a self-describing {@link VarSig} over the given message bytes using
     * the current signing private key.
     *
     * @throws IllegalStateException if this vault holds no signing keys
     */
    VarSig sign(byte[] message);

    /** Convenience: whether this vault can sign. */
    default boolean canSign() {
        return signingAlgorithm().isPresent();
    }

    // ==================================================================================
    // Encryption track (Phase 2 — methods declared for forward extension; current
    // InMemoryVault returns empty until encryption work begins)
    // ==================================================================================

    /**
     * The encryption algorithm in use, if this vault holds encryption keys.
     */
    default Optional<Algorithm.KeyMgmt> encryptionAlgorithm() {
        return Optional.empty();
    }

    /**
     * The current encryption public key, if this vault holds encryption keys.
     */
    default Optional<MultiKey> encryptionPublicKey() {
        return Optional.empty();
    }

    /**
     * The pre-rotation commitment for the encryption track.
     */
    default Optional<ContentID> encryptionNextKeyDigest() {
        return Optional.empty();
    }

    /** Convenience: whether this vault can encrypt / do key agreement. */
    default boolean canEncrypt() {
        return encryptionAlgorithm().isPresent();
    }
}
