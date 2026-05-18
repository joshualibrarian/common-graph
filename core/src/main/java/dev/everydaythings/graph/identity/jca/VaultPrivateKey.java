package dev.everydaythings.graph.identity.jca;

import dev.everydaythings.graph.id.ItemRef;
import dev.everydaythings.graph.identity.IdentityVocabulary;
import dev.everydaythings.graph.identity.vault.Vault;

import java.security.PrivateKey;
import java.util.Objects;

/**
 * Opaque {@link PrivateKey} handle backed by a {@link Vault}.  Carries no
 * key material itself — just a reference to the Vault plus the purpose
 * (which key track) and the algorithm name.  Signing operations route
 * through the Vault via {@link VaultSignatureSpi}; the cleartext private
 * key never enters the JVM heap.
 *
 * <p>The {@code getFormat()} / {@code getEncoded()} accessors return
 * {@code null} per the standard JCA convention for opaque-handle keys
 * (PKCS#11, HSM, smartcard).  Java security APIs that respect this
 * convention will not attempt to inspect the bytes.
 */
public final class VaultPrivateKey implements PrivateKey {

    private final Vault vault;
    private final ItemRef purpose;
    private final String algorithm;

    /**
     * Construct a handle for the Vault's signing-purpose key with algorithm
     * {@code "Ed25519"} — the most common form.
     */
    public static VaultPrivateKey signing(Vault vault) {
        return new VaultPrivateKey(vault, ItemRef.iid(IdentityVocabulary.Signing.KEY), "Ed25519");
    }

    /**
     * Construct a handle for an arbitrary Vault purpose with the given
     * algorithm name.  The algorithm string is the JCA name (e.g.,
     * {@code "Ed25519"}) — it has to match the SPI class registered in
     * {@link VaultProvider}.
     */
    public VaultPrivateKey(Vault vault, ItemRef purpose, String algorithm) {
        this.vault = Objects.requireNonNull(vault, "vault");
        this.purpose = Objects.requireNonNull(purpose, "purpose");
        this.algorithm = Objects.requireNonNull(algorithm, "algorithm");
    }

    /** The Vault that owns the actual private key material. */
    public Vault vault() {
        return vault;
    }

    /** The purpose IID (signing / encryption / key-agreement). */
    public ItemRef purpose() {
        return purpose;
    }

    @Override
    public String getAlgorithm() {
        return algorithm;
    }

    /**
     * Returns {@code null} — opaque handle, no encoded form.  Standard
     * convention for HSM- and smartcard-style PrivateKey implementations.
     */
    @Override
    public String getFormat() {
        return null;
    }

    /**
     * Returns {@code null} — opaque handle, no exported bytes.  The whole
     * point: cleartext private keys never leave the Vault.
     */
    @Override
    public byte[] getEncoded() {
        return null;
    }

    @Override
    public String toString() {
        return "VaultPrivateKey[algorithm=" + algorithm + ", purpose=" + purpose + "]";
    }
}
