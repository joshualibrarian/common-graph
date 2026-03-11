package dev.everydaythings.graph.crypt;

import dev.everydaythings.graph.Canonical;
import dev.everydaythings.graph.Hash;
import dev.everydaythings.graph.trust.EncryptionPublicKey;
import dev.everydaythings.graph.trust.SigningPublicKey;
import dev.everydaythings.graph.vault.Vault;
import lombok.Getter;
import lombok.experimental.Accessors;

import javax.crypto.KeyAgreement;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.Arrays;
import java.util.Objects;

/**
 * X3DH (Extended Triple Diffie-Hellman) key agreement protocol.
 *
 * <p>Allows two parties to establish a shared secret even when one party
 * is offline, using pre-published key material. Based on the Signal Protocol's
 * X3DH specification, adapted for Common Graph's peer-to-peer architecture.
 *
 * <h3>CG Adaptation</h3>
 * <p>In Signal, a central server stores pre-key bundles. In CG, the Signer
 * item IS the "server" — pre-keys are an endorsed component on the Signer,
 * replicated to peers via the normal sync mechanism. Any peer that has
 * your Signer can initiate X3DH without you being online.
 *
 * <h3>Protocol</h3>
 * <p>Bob publishes: identity key (IK_B), signed pre-key (SPK_B), one-time
 * pre-keys (OPK_B). Alice initiates:
 * <ol>
 *   <li>Verify SPK_B signature (Ed25519 by Bob's signing key)</li>
 *   <li>Generate ephemeral key EK_A</li>
 *   <li>DH1 = DH(IK_A, SPK_B) — identity x signed pre-key</li>
 *   <li>DH2 = DH(EK_A, IK_B) — ephemeral x identity</li>
 *   <li>DH3 = DH(EK_A, SPK_B) — ephemeral x signed pre-key</li>
 *   <li>DH4 = DH(EK_A, OPK_B) — ephemeral x one-time pre-key (optional)</li>
 *   <li>SK = HKDF(DH1 || DH2 || DH3 [|| DH4], info="CG-X3DH-v1")</li>
 * </ol>
 *
 * <h3>Encryption Layers</h3>
 * <p>X3DH establishes the initial shared secret for 1:1 encrypted sessions
 * (Double Ratchet, Phase 8). It composes with transport encryption (Noise XX)
 * and at-rest encryption (AES-256-GCM) independently.
 *
 * @see PreKeyBundle
 */
public final class X3DH {

    private static final byte[] INFO = "CG-X3DH-v1".getBytes(StandardCharsets.UTF_8);
    private static final int SECRET_LEN = 32;

    private X3DH() {} // Static utility

    // ==================================================================================
    // Result Types
    // ==================================================================================

    /**
     * Result of an X3DH key agreement.
     *
     * @param sharedSecret   32-byte shared secret for session key derivation
     * @param associatedData Both identity keys concatenated (for AEAD binding)
     */
    public record Result(byte[] sharedSecret, byte[] associatedData) {
        /** Zeroize the shared secret. */
        public void destroy() {
            Arrays.fill(sharedSecret, (byte) 0);
        }
    }

    /**
     * Initiator's result: shared secret + the init message to send to responder.
     */
    public record InitiatorResult(Result result, InitMessage message) {}

    // ==================================================================================
    // Init Message
    // ==================================================================================

    /**
     * Initial message sent by the initiator to the responder.
     *
     * <p>Contains the initiator's identity and ephemeral public keys, plus
     * references to which of the responder's pre-keys were used. The responder
     * uses this to perform the matching DH operations and derive the same
     * shared secret.
     */
    @Canonical.Canonization(classType = Canonical.ClassCollectionType.ARRAY)
    @Getter
    @Accessors(fluent = true)
    public static class InitMessage implements Canonical {

        /** Initiator's X25519 identity public key. */
        @Canon(order = 0)
        private EncryptionPublicKey identityKey;

        /** Initiator's ephemeral X25519 public key (fresh per session). */
        @Canon(order = 1)
        private EncryptionPublicKey ephemeralKey;

        /** Key ID (SHA-256 of SPKI) of the responder's SPK that was used. */
        @Canon(order = 2)
        private byte[] signedPreKeyId;

        /** Key ID of the responder's OPK that was used (null if none available). */
        @Canon(order = 3)
        private byte[] oneTimePreKeyId;

        /** No-arg for Canonical decoder. */
        @SuppressWarnings("unused")
        InitMessage() {}

        public InitMessage(EncryptionPublicKey identityKey, EncryptionPublicKey ephemeralKey,
                           byte[] signedPreKeyId, byte[] oneTimePreKeyId) {
            this.identityKey = Objects.requireNonNull(identityKey, "identityKey");
            this.ephemeralKey = Objects.requireNonNull(ephemeralKey, "ephemeralKey");
            this.signedPreKeyId = Objects.requireNonNull(signedPreKeyId, "signedPreKeyId");
            this.oneTimePreKeyId = oneTimePreKeyId; // nullable
        }
    }

    // ==================================================================================
    // Protocol: Initiator
    // ==================================================================================

    /**
     * Initiate an X3DH key agreement.
     *
     * <p>The initiator uses their own vault (identity key) and the responder's
     * published pre-key bundle to derive a shared secret. If the bundle has
     * one-time pre-keys available, the first one is used (providing extra
     * forward secrecy).
     *
     * @param ourVault         Our vault (contains X25519 identity key)
     * @param ourIdentityKey   Our X25519 public key
     * @param theirBundle      Their published pre-key bundle
     * @param theirIdentityKey Their X25519 public key
     * @param theirSigningKey  Their Ed25519 signing key (for SPK verification)
     * @return Shared secret, associated data, and init message to send
     * @throws SecurityException if SPK signature verification fails
     */
    public static InitiatorResult initiate(
            Vault ourVault,
            EncryptionPublicKey ourIdentityKey,
            PreKeyBundle theirBundle,
            EncryptionPublicKey theirIdentityKey,
            SigningPublicKey theirSigningKey) {

        Objects.requireNonNull(ourVault, "ourVault");
        Objects.requireNonNull(ourIdentityKey, "ourIdentityKey");
        Objects.requireNonNull(theirBundle, "theirBundle");
        Objects.requireNonNull(theirIdentityKey, "theirIdentityKey");
        Objects.requireNonNull(theirSigningKey, "theirSigningKey");

        // 1. Verify SPK signature
        if (!theirBundle.verifySignedPreKey(theirSigningKey)) {
            throw new SecurityException("SPK signature verification failed");
        }

        // 2. Generate ephemeral key pair
        KeyPair ephemeral = generateX25519();
        EncryptionPublicKey ephemeralPub = EncryptionPublicKey.builder()
                .jcaPublicKey(ephemeral.getPublic())
                .build();

        // 3. DH operations
        PublicKey spkPub = theirBundle.signedPreKey().toPublicKey();

        byte[] dh1 = ourVault.deriveSharedSecret(Vault.ENCRYPTION_KEY_ALIAS, spkPub);
        byte[] dh2 = ecdh(ephemeral.getPrivate(), theirIdentityKey.toPublicKey());
        byte[] dh3 = ecdh(ephemeral.getPrivate(), spkPub);

        byte[] dh4 = null;
        byte[] opkKeyId = null;
        if (theirBundle.hasOneTimePreKeys()) {
            EncryptionPublicKey opk = theirBundle.oneTimePreKeys().getFirst();
            opkKeyId = opk.keyId();
            dh4 = ecdh(ephemeral.getPrivate(), opk.toPublicKey());
        }

        // 4. Derive shared secret
        byte[] ikm = concatenate(dh1, dh2, dh3, dh4);
        byte[] sharedSecret = EnvelopeOps.hkdfSha256(ikm, null, INFO, SECRET_LEN);

        // 5. Associated data: both identity keys (for AEAD channel binding)
        byte[] ad = concatenate(
                ourIdentityKey.encodeBinary(Canonical.Scope.BODY),
                theirIdentityKey.encodeBinary(Canonical.Scope.BODY));

        // 6. Zeroize intermediate key material
        zeroize(dh1, dh2, dh3, dh4, ikm);

        // 7. Build init message
        InitMessage initMsg = new InitMessage(
                ourIdentityKey, ephemeralPub,
                theirBundle.signedPreKey().keyId(),
                opkKeyId);

        return new InitiatorResult(new Result(sharedSecret, ad), initMsg);
    }

    // ==================================================================================
    // Protocol: Responder
    // ==================================================================================

    /**
     * Respond to an X3DH key agreement.
     *
     * <p>The responder uses their own vault (identity + pre-keys) and the
     * initiator's init message to derive the same shared secret. If an OPK
     * was used, it is deleted from the vault (single-use).
     *
     * @param ourVault       Our vault (contains identity, SPK, and OPK keys)
     * @param ourIdentityKey Our X25519 public key
     * @param initMsg        The initiator's init message
     * @return The shared secret and associated data (matching the initiator's)
     * @throws SecurityException if the referenced OPK is not found in vault
     */
    public static Result respond(
            Vault ourVault,
            EncryptionPublicKey ourIdentityKey,
            InitMessage initMsg) {

        Objects.requireNonNull(ourVault, "ourVault");
        Objects.requireNonNull(ourIdentityKey, "ourIdentityKey");
        Objects.requireNonNull(initMsg, "initMsg");

        PublicKey theirIdentity = initMsg.identityKey().toPublicKey();
        PublicKey theirEphemeral = initMsg.ephemeralKey().toPublicKey();

        // DH operations (mirror of initiator, using our private keys)
        byte[] dh1 = ourVault.deriveSharedSecret(PreKeyBundle.SPK_ALIAS, theirIdentity);
        byte[] dh2 = ourVault.deriveSharedSecret(Vault.ENCRYPTION_KEY_ALIAS, theirEphemeral);
        byte[] dh3 = ourVault.deriveSharedSecret(PreKeyBundle.SPK_ALIAS, theirEphemeral);

        byte[] dh4 = null;
        if (initMsg.oneTimePreKeyId() != null) {
            String opkAlias = findOpkAlias(ourVault, initMsg.oneTimePreKeyId());
            if (opkAlias == null) {
                throw new SecurityException("OPK not found for the given keyId");
            }
            dh4 = ourVault.deriveSharedSecret(opkAlias, theirEphemeral);
            ourVault.deleteKey(opkAlias); // Consume the OPK (single-use)
        }

        // Derive shared secret (same HKDF as initiator)
        byte[] ikm = concatenate(dh1, dh2, dh3, dh4);
        byte[] sharedSecret = EnvelopeOps.hkdfSha256(ikm, null, INFO, SECRET_LEN);

        // Associated data: initiator IK || our IK (same order as initiator)
        byte[] ad = concatenate(
                initMsg.identityKey().encodeBinary(Canonical.Scope.BODY),
                ourIdentityKey.encodeBinary(Canonical.Scope.BODY));

        // Zeroize intermediate key material
        zeroize(dh1, dh2, dh3, dh4, ikm);

        return new Result(sharedSecret, ad);
    }

    // ==================================================================================
    // Helpers
    // ==================================================================================

    /**
     * Find the vault alias for an OPK by its key ID.
     *
     * <p>Iterates all vault aliases starting with the OPK prefix and
     * compares the SHA-256(SPKI) of each key against the target.
     */
    static String findOpkAlias(Vault vault, byte[] targetKeyId) {
        for (String alias : vault.aliases()) {
            if (!alias.startsWith(PreKeyBundle.OPK_PREFIX)) continue;
            PublicKey pub = vault.publicKey(alias).orElse(null);
            if (pub == null) continue;
            byte[] keyId = Hash.DEFAULT.digest(pub.getEncoded());
            if (Arrays.equals(keyId, targetKeyId)) {
                return alias;
            }
        }
        return null;
    }

    /** X25519 ECDH key agreement (for ephemeral keys not stored in vault). */
    private static byte[] ecdh(PrivateKey myPrivate, PublicKey theirPublic) {
        try {
            KeyAgreement ka = KeyAgreement.getInstance("XDH");
            ka.init(myPrivate);
            ka.doPhase(theirPublic, true);
            return ka.generateSecret();
        } catch (Exception e) {
            throw new SecurityException("ECDH key agreement failed", e);
        }
    }

    /** Generate an ephemeral X25519 key pair. */
    private static KeyPair generateX25519() {
        try {
            KeyPairGenerator kpg = KeyPairGenerator.getInstance("X25519");
            return kpg.generateKeyPair();
        } catch (NoSuchAlgorithmException e) {
            throw new SecurityException("X25519 not available", e);
        }
    }

    /** Concatenate byte arrays (nulls are skipped). */
    private static byte[] concatenate(byte[]... arrays) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (byte[] a : arrays) {
            if (a != null) {
                out.write(a, 0, a.length);
            }
        }
        return out.toByteArray();
    }

    /** Zeroize multiple byte arrays (nulls are skipped). */
    private static void zeroize(byte[]... arrays) {
        for (byte[] a : arrays) {
            if (a != null) Arrays.fill(a, (byte) 0);
        }
    }
}
