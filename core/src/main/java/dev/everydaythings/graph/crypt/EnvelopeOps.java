package dev.everydaythings.graph.crypt;

import dev.everydaythings.graph.Hash;
import dev.everydaythings.graph.trust.Algorithm;
import dev.everydaythings.graph.trust.EncryptionPublicKey;
import dev.everydaythings.graph.trust.GraphPublicKey;
import dev.everydaythings.graph.vault.Vault;

import javax.crypto.Cipher;
import javax.crypto.KeyAgreement;
import javax.crypto.Mac;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.*;
import java.security.spec.X509EncodedKeySpec;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Encrypt and decrypt operations for CG Tag 10 envelopes.
 *
 * <p>Supports two modes:
 * <ul>
 *   <li><b>ECDH-ES Direct</b> (single recipient) — derive CEK directly from ECDH shared secret</li>
 *   <li><b>ECDH-ES + Key Wrap</b> (multi-recipient) — random CEK, ECDH-derived key wraps the CEK per recipient</li>
 * </ul>
 *
 * <p>All private key operations go through the {@link Vault} — keys never leave the vault.
 */
public final class EnvelopeOps {

    private EnvelopeOps() {}

    // ==================================================================================
    // Encrypt
    // ==================================================================================

    /**
     * Encrypt plaintext to one or more recipients.
     *
     * @param plaintext    The bytes to encrypt
     * @param plaintextCid CID of the plaintext (for content-address recovery after decryption)
     * @param aad          Additional authenticated data (nullable)
     * @param aeadAlg      AEAD algorithm (e.g., AES_GCM_256)
     * @param recipients   Recipient encryption public keys
     * @param senderKid    Sender's signing key ID for authentication (nullable for anonymous)
     * @param senderSigFn  Function to produce sender signature (nullable for anonymous)
     * @return The encrypted envelope
     */
    public static EncryptedEnvelope encrypt(
            byte[] plaintext,
            byte[] plaintextCid,
            byte[] aad,
            Algorithm.Aead aeadAlg,
            List<EncryptionPublicKey> recipients,
            byte[] senderKid,
            java.util.function.Function<byte[], byte[]> senderSigFn) {

        if (recipients.isEmpty()) {
            throw new IllegalArgumentException("At least one recipient required");
        }

        Algorithm.KeyMgmt kemAlg = Algorithm.KeyMgmt.ECDH_ES_HKDF_256;

        // Generate random nonce
        byte[] nonce = secureRandom(aeadAlg.nonceBytes());

        // Generate random CEK
        byte[] cek = secureRandom(aeadAlg.keyBytes());

        // Build recipient entries: per-recipient ephemeral ECDH + key wrap
        List<EncryptedEnvelope.Recipient> recipientEntries = new ArrayList<>(recipients.size());
        for (EncryptionPublicKey recipientKey : recipients) {
            recipientEntries.add(buildRecipient(cek, recipientKey, kemAlg, aeadAlg));
        }

        // AEAD encrypt
        byte[] ciphertext = aeadEncrypt(aeadAlg, cek, nonce, aad, plaintext);

        // Build envelope (unsigned)
        EncryptedEnvelope envelope = new EncryptedEnvelope(
                kemAlg, aeadAlg, nonce, aad, plaintextCid,
                senderKid, null,
                recipientEntries, ciphertext);

        // Optionally sign the header
        if (senderSigFn != null && senderKid != null) {
            byte[] headerBody = envelope.headerBodyForSigning();
            byte[] sig = senderSigFn.apply(headerBody);
            return envelope.withSenderSig(sig);
        }

        return envelope;
    }

    /**
     * Convenience: encrypt with sender authentication from a Vault + Signer.
     */
    public static EncryptedEnvelope encrypt(
            byte[] plaintext,
            byte[] plaintextCid,
            byte[] aad,
            Algorithm.Aead aeadAlg,
            List<EncryptionPublicKey> recipients,
            Vault senderVault,
            GraphPublicKey senderSigningKey) {

        byte[] senderKid = senderSigningKey != null ? senderSigningKey.keyId() : null;
        java.util.function.Function<byte[], byte[]> sigFn =
                senderVault != null && senderVault.canSign()
                        ? data -> senderVault.sign(data)
                        : null;

        return encrypt(plaintext, plaintextCid, aad, aeadAlg, recipients, senderKid, sigFn);
    }

    /**
     * Convenience: encrypt anonymously (no sender identification).
     */
    public static EncryptedEnvelope encryptAnonymous(
            byte[] plaintext,
            byte[] plaintextCid,
            byte[] aad,
            Algorithm.Aead aeadAlg,
            List<EncryptionPublicKey> recipients) {

        return encrypt(plaintext, plaintextCid, aad, aeadAlg, recipients,
                (byte[]) null, (java.util.function.Function<byte[], byte[]>) null);
    }

    // ==================================================================================
    // Decrypt
    // ==================================================================================

    /**
     * Decrypt an envelope using the vault's encryption key.
     *
     * @param envelope The encrypted envelope
     * @param vault    Vault containing the recipient's private encryption key
     * @param myKeyId  The recipient's encryption key ID (to find the matching recipient entry)
     * @return The decrypted plaintext bytes
     * @throws IllegalArgumentException if no matching recipient entry found
     * @throws SecurityException        if decryption fails (wrong key, tampered data, etc.)
     */
    public static byte[] decrypt(EncryptedEnvelope envelope, Vault vault, byte[] myKeyId) {
        // Find my recipient entry
        EncryptedEnvelope.Recipient myEntry = null;
        for (EncryptedEnvelope.Recipient r : envelope.recipients()) {
            if (Arrays.equals(r.kid(), myKeyId)) {
                myEntry = r;
                break;
            }
        }
        if (myEntry == null) {
            throw new IllegalArgumentException("No recipient entry found for key ID");
        }

        // Recover CEK via ECDH key agreement
        byte[] cek = recoverCek(envelope, myEntry, vault);

        // AEAD decrypt
        byte[] plaintext = aeadDecrypt(envelope.aeadAlg(), cek, envelope.nonce(),
                envelope.aad(), envelope.ciphertext());

        // Verify plaintext CID if present
        if (envelope.plaintextCid() != null) {
            byte[] actualCid = Hash.DEFAULT.digest(plaintext);
            if (!Arrays.equals(actualCid, envelope.plaintextCid())) {
                throw new SecurityException(
                        "Plaintext CID mismatch: content integrity check failed after decryption");
            }
        }

        return plaintext;
    }

    // ==================================================================================
    // Public: key wrapping for group key distribution
    // ==================================================================================

    /**
     * Wrap a key (e.g., group key) to a recipient using ECDH + HKDF + AES-KW.
     *
     * <p>Produces a self-contained blob: [ephemeral_spki_len(2) | ephemeral_spki | wrapped_key].
     * The recipient can unwrap using their vault.
     *
     * @param keyMaterial  the key bytes to wrap (e.g., 32-byte AES-256 group key)
     * @param recipientKey the recipient's encryption public key
     * @return the wrapped key blob
     */
    public static byte[] wrapKeyForRecipient(byte[] keyMaterial, EncryptionPublicKey recipientKey) {
        Algorithm.KeyMgmt kemAlg = Algorithm.KeyMgmt.ECDH_ES_HKDF_256;
        Algorithm.Aead aeadAlg = Algorithm.Aead.AES_GCM_256;

        try {
            // Generate ephemeral X25519 keypair
            KeyPairGenerator kpg = KeyPairGenerator.getInstance(kemAlg.keyGeneratorName());
            KeyPair ephemeral = kpg.generateKeyPair();

            // ECDH: ephemeral_private × recipient_public → shared secret
            PublicKey recipientPub = recipientKey.toPublicKey();
            KeyAgreement ka = KeyAgreement.getInstance(kemAlg.agreementName());
            ka.init(ephemeral.getPrivate());
            ka.doPhase(recipientPub, true);
            byte[] sharedSecret = ka.generateSecret();

            // HKDF-SHA256: shared_secret → derived key
            byte[] derivedKey = hkdfSha256(sharedSecret, null,
                    buildKdfContext(kemAlg, aeadAlg), aeadAlg.keyBytes());

            // AES Key Wrap
            byte[] wrapped = aesKeyWrap(derivedKey, keyMaterial);

            // Pack: [epk_len(2 bytes, big-endian) | epk | wrapped]
            byte[] epk = ephemeral.getPublic().getEncoded();
            byte[] blob = new byte[2 + epk.length + wrapped.length];
            blob[0] = (byte) (epk.length >> 8);
            blob[1] = (byte) epk.length;
            System.arraycopy(epk, 0, blob, 2, epk.length);
            System.arraycopy(wrapped, 0, blob, 2 + epk.length, wrapped.length);
            return blob;

        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new RuntimeException("Failed to wrap key for recipient", e);
        }
    }

    /**
     * Unwrap a key blob produced by {@link #wrapKeyForRecipient}.
     *
     * @param blob  the wrapped key blob
     * @param vault the vault holding the recipient's private key
     * @param myKey the recipient's encryption public key (for key ID matching)
     * @return the unwrapped key bytes
     */
    public static byte[] unwrapKeyForRecipient(byte[] blob, Vault vault, EncryptionPublicKey myKey) {
        Algorithm.KeyMgmt kemAlg = Algorithm.KeyMgmt.ECDH_ES_HKDF_256;
        Algorithm.Aead aeadAlg = Algorithm.Aead.AES_GCM_256;

        try {
            // Unpack: [epk_len(2) | epk | wrapped]
            int epkLen = ((blob[0] & 0xFF) << 8) | (blob[1] & 0xFF);
            byte[] epk = new byte[epkLen];
            System.arraycopy(blob, 2, epk, 0, epkLen);
            byte[] wrapped = new byte[blob.length - 2 - epkLen];
            System.arraycopy(blob, 2 + epkLen, wrapped, 0, wrapped.length);

            // Reconstruct ephemeral public key
            KeyFactory kf = KeyFactory.getInstance(kemAlg.keyFactoryName());
            PublicKey ephemeralPub = kf.generatePublic(new X509EncodedKeySpec(epk));

            // ECDH: my_private × ephemeral_public → shared secret
            byte[] sharedSecret = vault.deriveSharedSecret(Vault.ENCRYPTION_KEY_ALIAS, ephemeralPub);

            // HKDF-SHA256
            byte[] derivedKey = hkdfSha256(sharedSecret, null,
                    buildKdfContext(kemAlg, aeadAlg), aeadAlg.keyBytes());

            // AES Key Unwrap
            return aesKeyUnwrap(derivedKey, wrapped);

        } catch (Exception e) {
            throw new SecurityException("Failed to unwrap key", e);
        }
    }

    // ==================================================================================
    // Internal: recipient entry construction
    // ==================================================================================

    private static EncryptedEnvelope.Recipient buildRecipient(
            byte[] cek, EncryptionPublicKey recipientKey,
            Algorithm.KeyMgmt kemAlg, Algorithm.Aead aeadAlg) {

        try {
            // Generate ephemeral X25519 keypair
            KeyPairGenerator kpg = KeyPairGenerator.getInstance(kemAlg.keyGeneratorName());
            KeyPair ephemeral = kpg.generateKeyPair();

            // ECDH: ephemeral_private × recipient_public → shared secret
            PublicKey recipientPub = recipientKey.toPublicKey();
            KeyAgreement ka = KeyAgreement.getInstance(kemAlg.agreementName());
            ka.init(ephemeral.getPrivate());
            ka.doPhase(recipientPub, true);
            byte[] sharedSecret = ka.generateSecret();

            // HKDF-SHA256: shared_secret → derived key (for key wrap)
            byte[] derivedKey = hkdfSha256(sharedSecret, null,
                    buildKdfContext(kemAlg, aeadAlg), aeadAlg.keyBytes());

            // AES Key Wrap: wrap the CEK with the derived key
            byte[] wrappedCek = aesKeyWrap(derivedKey, cek);

            return new EncryptedEnvelope.Recipient(
                    recipientKey.keyId(),
                    ephemeral.getPublic().getEncoded(),
                    wrappedCek);

        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new RuntimeException("Failed to build recipient entry", e);
        }
    }

    // ==================================================================================
    // Internal: CEK recovery
    // ==================================================================================

    private static byte[] recoverCek(EncryptedEnvelope envelope,
                                     EncryptedEnvelope.Recipient entry,
                                     Vault vault) {
        try {
            // Reconstruct sender's ephemeral public key from SPKI
            KeyFactory kf = KeyFactory.getInstance(envelope.kemAlg().keyFactoryName());
            PublicKey ephemeralPub = kf.generatePublic(new X509EncodedKeySpec(entry.epk()));

            // ECDH: my_private × ephemeral_public → shared secret
            byte[] sharedSecret = vault.deriveSharedSecret(Vault.ENCRYPTION_KEY_ALIAS, ephemeralPub);

            // HKDF-SHA256: shared_secret → derived key
            byte[] derivedKey = hkdfSha256(sharedSecret, null,
                    buildKdfContext(envelope.kemAlg(), envelope.aeadAlg()),
                    envelope.aeadAlg().keyBytes());

            // AES Key Unwrap: recover CEK
            return aesKeyUnwrap(derivedKey, entry.wrappedCek());

        } catch (Exception e) {
            throw new SecurityException("Failed to recover CEK", e);
        }
    }

    // ==================================================================================
    // AEAD: AES-GCM / ChaCha20-Poly1305
    // ==================================================================================

    private static byte[] aeadEncrypt(Algorithm.Aead alg, byte[] key,
                                      byte[] nonce, byte[] aad, byte[] plaintext) {
        try {
            Cipher cipher = Cipher.getInstance(alg.transformation());
            SecretKey secretKey = new SecretKeySpec(key, cipherKeyAlg(alg));
            GCMParameterSpec spec = new GCMParameterSpec(alg.tagBits(), nonce);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, spec);
            if (aad != null) cipher.updateAAD(aad);
            return cipher.doFinal(plaintext);
        } catch (Exception e) {
            throw new RuntimeException("AEAD encryption failed", e);
        }
    }

    private static byte[] aeadDecrypt(Algorithm.Aead alg, byte[] key,
                                      byte[] nonce, byte[] aad, byte[] ciphertext) {
        try {
            Cipher cipher = Cipher.getInstance(alg.transformation());
            SecretKey secretKey = new SecretKeySpec(key, cipherKeyAlg(alg));
            GCMParameterSpec spec = new GCMParameterSpec(alg.tagBits(), nonce);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, spec);
            if (aad != null) cipher.updateAAD(aad);
            return cipher.doFinal(ciphertext);
        } catch (Exception e) {
            throw new SecurityException("AEAD decryption failed (wrong key or tampered data)", e);
        }
    }

    private static String cipherKeyAlg(Algorithm.Aead alg) {
        return alg.transformation().startsWith("ChaCha") ? "ChaCha20" : "AES";
    }

    // ==================================================================================
    // HKDF-SHA256 (RFC 5869)
    // ==================================================================================

    /**
     * HKDF-SHA256: Extract-then-Expand.
     *
     * @param ikm  Input keying material (ECDH shared secret)
     * @param salt Optional salt (null = zero-filled)
     * @param info Context info for key separation
     * @param len  Desired output length in bytes
     * @return Derived key material
     */
    static byte[] hkdfSha256(byte[] ikm, byte[] salt, byte[] info, int len) {
        try {
            // Extract
            if (salt == null || salt.length == 0) {
                salt = new byte[32]; // hash length for SHA-256
            }
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(salt, "HmacSHA256"));
            byte[] prk = mac.doFinal(ikm);

            // Expand
            int hashLen = 32;
            int n = (len + hashLen - 1) / hashLen;
            byte[] okm = new byte[len];
            byte[] t = new byte[0];
            int offset = 0;
            for (int i = 1; i <= n; i++) {
                mac.init(new SecretKeySpec(prk, "HmacSHA256"));
                mac.update(t);
                if (info != null) mac.update(info);
                mac.update((byte) i);
                t = mac.doFinal();
                int toCopy = Math.min(hashLen, len - offset);
                System.arraycopy(t, 0, okm, offset, toCopy);
                offset += toCopy;
            }
            return okm;
        } catch (Exception e) {
            throw new RuntimeException("HKDF-SHA256 failed", e);
        }
    }

    // ==================================================================================
    // AES Key Wrap (RFC 3394)
    // ==================================================================================

    private static byte[] aesKeyWrap(byte[] kek, byte[] plaintext) {
        try {
            Cipher cipher = Cipher.getInstance("AESWrap");
            cipher.init(Cipher.WRAP_MODE, new SecretKeySpec(kek, "AES"));
            return cipher.wrap(new SecretKeySpec(plaintext, "AES"));
        } catch (Exception e) {
            throw new RuntimeException("AES Key Wrap failed", e);
        }
    }

    private static byte[] aesKeyUnwrap(byte[] kek, byte[] wrapped) {
        try {
            Cipher cipher = Cipher.getInstance("AESWrap");
            cipher.init(Cipher.UNWRAP_MODE, new SecretKeySpec(kek, "AES"));
            return cipher.unwrap(wrapped, "AES", Cipher.SECRET_KEY).getEncoded();
        } catch (Exception e) {
            throw new SecurityException("AES Key Unwrap failed (wrong key?)", e);
        }
    }

    // ==================================================================================
    // KDF context info
    // ==================================================================================

    /**
     * Build KDF context info for key derivation.
     * Includes algorithm identifiers for domain separation.
     */
    private static byte[] buildKdfContext(Algorithm.KeyMgmt kemAlg, Algorithm.Aead aeadAlg) {
        // Simple context: "CG-ECDH-ES" || kemCose(4 bytes) || aeadCose(4 bytes)
        byte[] prefix = "CG-ECDH-ES".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        byte[] ctx = new byte[prefix.length + 8];
        System.arraycopy(prefix, 0, ctx, 0, prefix.length);
        int off = prefix.length;
        ctx[off++] = (byte) (kemAlg.coseId() >> 24);
        ctx[off++] = (byte) (kemAlg.coseId() >> 16);
        ctx[off++] = (byte) (kemAlg.coseId() >> 8);
        ctx[off++] = (byte) (kemAlg.coseId());
        ctx[off++] = (byte) (aeadAlg.coseId() >> 24);
        ctx[off++] = (byte) (aeadAlg.coseId() >> 16);
        ctx[off++] = (byte) (aeadAlg.coseId() >> 8);
        ctx[off] = (byte) (aeadAlg.coseId());
        return ctx;
    }

    // ==================================================================================
    // Secure random
    // ==================================================================================

    private static final SecureRandom SRNG = new SecureRandom();

    private static byte[] secureRandom(int len) {
        byte[] b = new byte[len];
        SRNG.nextBytes(b);
        return b;
    }
}
