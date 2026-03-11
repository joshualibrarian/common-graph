package dev.everydaythings.graph.crypt;

import dev.everydaythings.graph.Canonical;
import dev.everydaythings.graph.item.id.ItemID;
import dev.everydaythings.graph.trust.Algorithm;
import dev.everydaythings.graph.trust.EncryptionPublicKey;
import dev.everydaythings.graph.trust.KeyLog;
import dev.everydaythings.graph.trust.SigningPublicKey;
import dev.everydaythings.graph.vault.Vault;
import lombok.Getter;
import lombok.experimental.Accessors;

import java.security.PublicKey;
import java.security.Signature;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * Snapshot of pre-key material for X3DH key agreement.
 *
 * <p>This is a protocol-level data class, not a component. Pre-keys are
 * managed by the Signer's {@link dev.everydaythings.graph.trust.KeyLog}
 * (lifecycle: add, set-current, tombstone) and
 * {@link dev.everydaythings.graph.trust.CertLog} (SPK certification).
 * PreKeyBundle is the transport format used during X3DH exchanges.
 *
 * <p>Contains the signed pre-key (SPK) and one-time pre-keys (OPKs) that
 * allow peers to initiate encrypted sessions even when this Signer is offline.
 *
 * <p>The SPK is signed by the Signer's Ed25519 identity key, binding it
 * to the Signer's cryptographic identity. OPKs are single-use: consumed
 * and deleted from the vault after one X3DH exchange.
 *
 * @see X3DH
 */
@Canonical.Canonization(classType = Canonical.ClassCollectionType.ARRAY)
@Getter
@Accessors(fluent = true)
public class PreKeyBundle implements Canonical {

    /** Vault alias for the signed pre-key. */
    public static final String SPK_ALIAS = "spk";

    /** Vault alias prefix for one-time pre-keys. */
    public static final String OPK_PREFIX = "opk-";

    /** Default number of one-time pre-keys to generate. */
    public static final int DEFAULT_OPK_COUNT = 100;

    /** Current signed pre-key (X25519). */
    @Canon(order = 0)
    private EncryptionPublicKey signedPreKey;

    /** Ed25519 signature over the SPK's BODY encoding. */
    @Canon(order = 1)
    private byte[] signedPreKeySignature;

    /** When the SPK was generated (epoch millis). */
    @Canon(order = 2)
    private long signedPreKeyTimestamp;

    /** Available one-time pre-keys (X25519). Consumed and removed after use. */
    @Canon(order = 3)
    private List<EncryptionPublicKey> oneTimePreKeys;

    /** No-arg constructor for Canonical decoding. */
    @SuppressWarnings("unused")
    PreKeyBundle() {
        this.oneTimePreKeys = new ArrayList<>();
    }

    public PreKeyBundle(EncryptionPublicKey signedPreKey, byte[] signedPreKeySignature,
                        long signedPreKeyTimestamp, List<EncryptionPublicKey> oneTimePreKeys) {
        this.signedPreKey = Objects.requireNonNull(signedPreKey, "signedPreKey");
        this.signedPreKeySignature = Objects.requireNonNull(signedPreKeySignature, "signedPreKeySignature");
        this.signedPreKeyTimestamp = signedPreKeyTimestamp;
        this.oneTimePreKeys = new ArrayList<>(oneTimePreKeys);
    }

    // ==================================================================================
    // Generation
    // ==================================================================================

    /**
     * Generate a fresh PreKeyBundle from a vault.
     *
     * <p>Creates one signed pre-key (X25519, signed by the vault's Ed25519
     * signing key) and {@code opkCount} one-time pre-keys. All private keys
     * remain in the vault; only public keys are stored in the bundle.
     *
     * @param vault    Vault with signing + encryption keys
     * @param owner    Owner item ID (for public key metadata, nullable)
     * @param opkCount Number of one-time pre-keys to generate
     * @return A new PreKeyBundle with fresh keys
     */
    public static PreKeyBundle generate(Vault vault, ItemID owner, int opkCount) {
        Objects.requireNonNull(vault, "vault");

        // Generate signed pre-key
        if (vault.containsKey(SPK_ALIAS)) {
            vault.deleteKey(SPK_ALIAS);
        }
        vault.generateKey(SPK_ALIAS, Algorithm.KeyMgmt.ECDH_ES_HKDF_256);
        PublicKey spkPub = vault.publicKey(SPK_ALIAS)
                .orElseThrow(() -> new IllegalStateException("Failed to generate SPK"));
        EncryptionPublicKey spk = EncryptionPublicKey.builder()
                .jcaPublicKey(spkPub)
                .owner(owner)
                .build();

        // Sign SPK with Ed25519 signing key
        byte[] spkBody = spk.encodeBinary(Canonical.Scope.BODY);
        byte[] spkSig = vault.sign(spkBody);

        // Generate one-time pre-keys
        List<EncryptionPublicKey> opks = new ArrayList<>(opkCount);
        for (int i = 0; i < opkCount; i++) {
            String alias = OPK_PREFIX + i;
            if (vault.containsKey(alias)) {
                vault.deleteKey(alias);
            }
            vault.generateKey(alias, Algorithm.KeyMgmt.ECDH_ES_HKDF_256);
            PublicKey opkPub = vault.publicKey(alias).orElseThrow();
            opks.add(EncryptionPublicKey.builder()
                    .jcaPublicKey(opkPub)
                    .owner(owner)
                    .build());
        }

        return new PreKeyBundle(spk, spkSig, System.currentTimeMillis(), opks);
    }

    /**
     * Generate with default OPK count.
     */
    public static PreKeyBundle generate(Vault vault, ItemID owner) {
        return generate(vault, owner, DEFAULT_OPK_COUNT);
    }

    /**
     * Extract a PreKeyBundle snapshot from a KeyLog's materialized state.
     *
     * <p>Reads the current SPK (Purpose.PRE_KEY) and available OPKs
     * (Purpose.ONE_TIME_PRE_KEY) from the KeyLog. The SPK signature
     * must be provided separately (from CertLog or vault).
     *
     * @param keyLog       The KeyLog containing pre-key entries
     * @param spkSignature Ed25519 signature over the SPK's BODY encoding
     * @param spkTimestamp When the SPK was generated (epoch millis)
     * @return A PreKeyBundle snapshot, or null if no SPK is set
     */
    public static PreKeyBundle fromKeyLog(KeyLog keyLog, byte[] spkSignature, long spkTimestamp) {
        Objects.requireNonNull(keyLog, "keyLog");
        EncryptionPublicKey spk = keyLog.currentPreKey().orElse(null);
        if (spk == null) return null;
        List<EncryptionPublicKey> opks = keyLog.availableOneTimePreKeys();
        return new PreKeyBundle(spk, spkSignature, spkTimestamp, opks);
    }

    // ==================================================================================
    // Verification
    // ==================================================================================

    /**
     * Verify the SPK signature using the given signing public key.
     *
     * <p>The SPK must have been signed by the Signer's Ed25519 identity key.
     * This binds the pre-key to the Signer's identity, preventing substitution.
     *
     * @param signingKey The signer's Ed25519 public key
     * @return true if the signature is valid
     */
    public boolean verifySignedPreKey(SigningPublicKey signingKey) {
        try {
            byte[] spkBody = signedPreKey.encodeBinary(Canonical.Scope.BODY);
            Signature sig = Signature.getInstance(signingKey.algorithm().signatureName());
            sig.initVerify(signingKey.toPublicKey());
            sig.update(spkBody);
            return sig.verify(signedPreKeySignature);
        } catch (Exception e) {
            return false;
        }
    }

    // ==================================================================================
    // OPK Management
    // ==================================================================================

    /**
     * Remove a consumed one-time pre-key by its key ID.
     *
     * @param keyId The SHA-256(SPKI) key ID of the consumed OPK
     * @return true if an OPK was removed
     */
    public boolean removeOneTimePreKey(byte[] keyId) {
        return oneTimePreKeys.removeIf(opk -> Arrays.equals(opk.keyId(), keyId));
    }

    /**
     * Check if any one-time pre-keys are available.
     */
    public boolean hasOneTimePreKeys() {
        return !oneTimePreKeys.isEmpty();
    }

    /**
     * Get the number of remaining one-time pre-keys.
     */
    public int oneTimePreKeyCount() {
        return oneTimePreKeys.size();
    }
}
