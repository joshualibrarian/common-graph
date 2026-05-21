package dev.everydaythings.graph.cryptography.vault;

import dev.everydaythings.graph.cryptography.algorithm.PublicKeyAlgorithm;
import dev.everydaythings.graph.item.BindingExempt;

import java.security.PublicKey;
import java.util.Objects;

/**
 * SigningKey — a typed handle to a signing keypair held by a vault.
 *
 * <p>Carries the keypair's public material (algorithm + JCA public key) plus
 * a {@link SignFunction} that performs the actual signing operation.  The
 * private key bytes NEVER appear in the handle — the {@link SignFunction}
 * routes the operation to wherever the private material actually lives
 * (in-memory heap, encrypted file, PKCS#11 token, OS keychain).
 *
 * <p>This is the structural invariant that makes hardware-backed vaults work
 * cleanly: a {@code SigningKey} obtained from a {@code Pkcs11Vault} carries a
 * lambda that calls into the token; a {@code SigningKey} obtained from an
 * {@code InMemoryVault} carries a lambda that signs with a heap-resident
 * keypair.  User code does not (and cannot) tell the difference.
 *
 * <p>The handle is constructed by the vault during entry hydration and
 * stored on the entry's typed field (e.g.,
 * {@code Identity.currentSigning}).  User code then calls
 * {@code identity.currentSigning.sign(message)} to produce a signature.
 *
 * @see KeyAgreementKey for the parallel key-agreement-purpose handle
 */
public final class SigningKey implements BindingExempt {

    private final PublicKeyAlgorithm algorithm;
    private final PublicKey publicKey;
    private final SignFunction signFn;

    /**
     * Construct a SigningKey handle.  Package-private — handles are
     * created by vault implementations during entry hydration; user code
     * obtains them by reading entry fields.
     */
    public SigningKey(PublicKeyAlgorithm algorithm, PublicKey publicKey, SignFunction signFn) {
        this.algorithm = Objects.requireNonNull(algorithm, "algorithm");
        this.publicKey = Objects.requireNonNull(publicKey, "publicKey");
        this.signFn = Objects.requireNonNull(signFn, "signFn");
    }

    /** The algorithm this keypair uses (Ed25519, ECDSA P-256, etc.). */
    public PublicKeyAlgorithm algorithm() {
        return algorithm;
    }

    /** The JCA public key for verification. */
    public PublicKey publicKey() {
        return publicKey;
    }

    /** Raw public-key bytes (multikey payload minus codec prefix). */
    public byte[] rawPublicKey() {
        return algorithm.publicKeyToRaw(publicKey);
    }

    /**
     * Sign the given message using this keypair's private material.
     *
     * <p>The private key bytes never touch user code.  Implementation
     * details (in-memory signing, hardware-token call-out, network-attached
     * KMS, etc.) are hidden by the vault-provided {@link SignFunction}.
     */
    public byte[] sign(byte[] message) {
        Objects.requireNonNull(message, "message");
        return signFn.sign(message);
    }

    /**
     * Vault-supplied signing primitive.  Implementations route to whatever
     * backend holds the private material: a heap keypair, a JCA Signature
     * over a JWKS-decoded key, a PKCS#11 C_Sign call, etc.
     */
    @FunctionalInterface
    public interface SignFunction {
        byte[] sign(byte[] message);
    }
}
