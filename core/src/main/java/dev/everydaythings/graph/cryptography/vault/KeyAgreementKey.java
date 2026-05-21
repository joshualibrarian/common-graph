package dev.everydaythings.graph.cryptography.vault;

import dev.everydaythings.graph.cryptography.algorithm.PublicKeyAlgorithm;
import dev.everydaythings.graph.item.BindingExempt;

import java.security.PublicKey;
import java.util.Objects;

/**
 * KeyAgreementKey — a typed handle to a key-agreement keypair held by a vault.
 *
 * <p>Parallel to {@link SigningKey} but for key-agreement primitives (X25519,
 * ECDH, RSA-OAEP).  Carries the keypair's public material plus an
 * {@link AgreeFunction} that performs the agreement operation.  The private
 * scalar bytes never appear in the handle.
 *
 * <p>Used for the long-term key-agreement track of an Identity, for
 * signed pre-keys and one-time pre-keys, and for protocol-bound keys like
 * GPG encryption sub-keys on Authentication entries.
 *
 * @see SigningKey for the parallel signing-purpose handle
 */
public final class KeyAgreementKey implements BindingExempt {

    private final PublicKeyAlgorithm algorithm;
    private final PublicKey publicKey;
    private final AgreeFunction agreeFn;

    /**
     * Construct a KeyAgreementKey handle.  Created by vault implementations
     * during entry hydration; user code obtains these by reading entry
     * fields.
     */
    public KeyAgreementKey(PublicKeyAlgorithm algorithm, PublicKey publicKey, AgreeFunction agreeFn) {
        this.algorithm = Objects.requireNonNull(algorithm, "algorithm");
        this.publicKey = Objects.requireNonNull(publicKey, "publicKey");
        this.agreeFn = Objects.requireNonNull(agreeFn, "agreeFn");
    }

    /** The algorithm this keypair uses (X25519, ECDH-P-256, etc.). */
    public PublicKeyAlgorithm algorithm() {
        return algorithm;
    }

    /** The JCA public key (peers use this to derive a shared secret with us). */
    public PublicKey publicKey() {
        return publicKey;
    }

    /** Raw public-key bytes (multikey payload minus codec prefix). */
    public byte[] rawPublicKey() {
        return algorithm.publicKeyToRaw(publicKey);
    }

    /**
     * Derive a shared secret by agreeing this keypair's private material
     * with a peer's public key.
     *
     * <p>The private scalar never touches user code.  Implementation details
     * (software ECDH, token-side DH, etc.) are hidden by the vault-provided
     * {@link AgreeFunction}.
     */
    public byte[] agree(PublicKey peerPublic) {
        Objects.requireNonNull(peerPublic, "peerPublic");
        return agreeFn.agree(peerPublic);
    }

    /**
     * Vault-supplied key-agreement primitive.  Implementations route to
     * whatever backend holds the private scalar.
     */
    @FunctionalInterface
    public interface AgreeFunction {
        byte[] agree(PublicKey peerPublic);
    }
}
