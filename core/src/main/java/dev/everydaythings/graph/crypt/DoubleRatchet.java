package dev.everydaythings.graph.crypt;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Signal-style Double Ratchet protocol for per-message forward secrecy.
 *
 * <p>After two parties establish a shared secret via {@link X3DH}, the Double
 * Ratchet derives unique keys for every message through two interlocking
 * ratchets:
 *
 * <ol>
 *   <li><b>DH Ratchet</b> (asymmetric, per-turn): Each turn change generates
 *       a new X25519 keypair. ECDH with the peer's public key feeds into
 *       the root KDF chain, providing <i>break-in recovery</i>.</li>
 *   <li><b>Symmetric Ratchet</b> (KDF chain, per-message): Each message
 *       derives a unique message key from the chain key. Old chain keys
 *       and message keys are deleted immediately, providing <i>forward
 *       secrecy</i>.</li>
 * </ol>
 *
 * <h3>Usage</h3>
 * <pre>{@code
 * // After X3DH handshake:
 * State alice = DoubleRatchet.initInitiator(sharedSecret, bobSPK);
 * State bob   = DoubleRatchet.initResponder(sharedSecret, bobSPKPair);
 *
 * // Alice sends:
 * Message m1 = DoubleRatchet.encrypt(alice, "hello".getBytes(), ad);
 *
 * // Bob receives:
 * byte[] plaintext = DoubleRatchet.decrypt(bob, m1, ad);
 *
 * // Bob responds (triggers DH ratchet step):
 * Message m2 = DoubleRatchet.encrypt(bob, "hi".getBytes(), ad);
 * byte[] p2 = DoubleRatchet.decrypt(alice, m2, ad);
 * }</pre>
 *
 * @see X3DH
 */
public final class DoubleRatchet {

    private static final byte[] ROOT_INFO = "CG-DR-root".getBytes(StandardCharsets.UTF_8);
    private static final byte[] CHAIN_KEY_CONSTANT = {0x02};
    private static final byte[] MSG_KEY_CONSTANT = {0x01};
    private static final int MAX_SKIP = 1000;

    private DoubleRatchet() {} // Static utility

    // ==================================================================================
    // State
    // ==================================================================================

    /**
     * Ratchet state held per sender-recipient pair. Local-only, never synced.
     *
     * <p>All byte array fields are zeroized when no longer needed. The state
     * is mutable — encrypt/decrypt operations modify it in place.
     */
    public static final class State {
        byte[] rootKey;
        byte[] sendChainKey;
        byte[] recvChainKey;
        KeyPair sendRatchetKey;
        PublicKey recvRatchetPub;
        int sendMsgNum;
        int recvMsgNum;
        int prevChainLen;
        final Map<SkippedKey, byte[]> skippedKeys = new LinkedHashMap<>();

        /** Zeroize all key material. */
        public void destroy() {
            zeroize(rootKey, sendChainKey, recvChainKey);
            rootKey = null;
            sendChainKey = null;
            recvChainKey = null;
            sendRatchetKey = null;
            recvRatchetPub = null;
            for (byte[] mk : skippedKeys.values()) zeroize(mk);
            skippedKeys.clear();
        }
    }

    /**
     * Lookup key for skipped (out-of-order) message keys.
     */
    record SkippedKey(ByteArrayWrapper ratchetPub, int messageNum) {}

    /** Wrapper for byte[] with proper equals/hashCode. */
    record ByteArrayWrapper(byte[] data) {
        @Override public boolean equals(Object o) {
            return o instanceof ByteArrayWrapper w && Arrays.equals(data, w.data);
        }
        @Override public int hashCode() { return Arrays.hashCode(data); }
    }

    // ==================================================================================
    // Header & Message
    // ==================================================================================

    /**
     * Ratchet header sent with each encrypted message.
     *
     * @param ratchetPub    Sender's current DH ratchet public key (SPKI encoded)
     * @param prevChainLen  Number of messages in the previous sending chain
     * @param messageNum    Message number within the current chain
     */
    public record Header(byte[] ratchetPub, int prevChainLen, int messageNum) {}

    /**
     * An encrypted Double Ratchet message: header + ciphertext.
     */
    public record Message(Header header, byte[] ciphertext) {}

    // ==================================================================================
    // Initialization
    // ==================================================================================

    /**
     * Initialize ratchet state for the initiator (Alice, who performed X3DH.initiate).
     *
     * <p>Alice generates a fresh DH ratchet keypair and performs the initial
     * DH ratchet step against Bob's SPK. She can send messages immediately.
     *
     * @param sharedSecret   The 32-byte shared secret from X3DH
     * @param peerRatchetPub The responder's SPK public key (from the PreKeyBundle)
     * @return Initialized state ready for sending
     */
    public static State initInitiator(byte[] sharedSecret, PublicKey peerRatchetPub) {
        Objects.requireNonNull(sharedSecret, "sharedSecret");
        Objects.requireNonNull(peerRatchetPub, "peerRatchetPub");

        State state = new State();
        state.rootKey = sharedSecret.clone();
        state.recvRatchetPub = peerRatchetPub;

        // Generate initial DH ratchet keypair and step
        state.sendRatchetKey = generateX25519();
        byte[] dhOut = ecdh(state.sendRatchetKey.getPrivate(), state.recvRatchetPub);
        byte[] kdfOut = kdfRootKey(state.rootKey, dhOut);
        zeroize(dhOut);

        state.rootKey = Arrays.copyOf(kdfOut, 32);
        state.sendChainKey = Arrays.copyOfRange(kdfOut, 32, 64);
        zeroize(kdfOut);

        return state;
    }

    /**
     * Initialize ratchet state for the responder (Bob, who will receive the
     * first message from the initiator).
     *
     * <p>Bob uses his SPK keypair as the initial ratchet key. He cannot send
     * messages until he receives Alice's first message (which triggers his
     * first DH ratchet step).
     *
     * @param sharedSecret  The 32-byte shared secret from X3DH
     * @param ourRatchetKey Bob's SPK keypair (private key in vault)
     * @return Initialized state ready for receiving
     */
    public static State initResponder(byte[] sharedSecret, KeyPair ourRatchetKey) {
        Objects.requireNonNull(sharedSecret, "sharedSecret");
        Objects.requireNonNull(ourRatchetKey, "ourRatchetKey");

        State state = new State();
        state.rootKey = sharedSecret.clone();
        state.sendRatchetKey = ourRatchetKey;
        // recvRatchetPub, sendChainKey, recvChainKey: null until first message received

        return state;
    }

    // ==================================================================================
    // Encrypt
    // ==================================================================================

    /**
     * Encrypt a plaintext message, advancing the sending chain.
     *
     * @param state     Ratchet state (modified in place)
     * @param plaintext The message bytes to encrypt
     * @param ad        Associated data for AEAD (e.g., both identity keys)
     * @return The encrypted message with ratchet header
     * @throws IllegalStateException if the sending chain is not initialized
     */
    public static Message encrypt(State state, byte[] plaintext, byte[] ad) {
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(plaintext, "plaintext");
        if (state.sendChainKey == null) {
            throw new IllegalStateException(
                    "Sending chain not initialized — responder must receive first");
        }

        // Derive message key from chain
        byte[] messageKey = hmacSha256(state.sendChainKey, MSG_KEY_CONSTANT);
        byte[] newChainKey = hmacSha256(state.sendChainKey, CHAIN_KEY_CONSTANT);
        zeroize(state.sendChainKey);
        state.sendChainKey = newChainKey;

        // Build header
        Header header = new Header(
                state.sendRatchetKey.getPublic().getEncoded(),
                state.prevChainLen,
                state.sendMsgNum);
        state.sendMsgNum++;

        // AEAD encrypt with message key
        byte[] headerAd = concatenateAd(ad, header);
        byte[] nonce = secureRandom(12);
        byte[] ciphertext = aeadEncrypt(messageKey, nonce, headerAd, plaintext);
        zeroize(messageKey);

        // Prepend nonce to ciphertext (receiver needs it)
        byte[] nonceAndCiphertext = new byte[12 + ciphertext.length];
        System.arraycopy(nonce, 0, nonceAndCiphertext, 0, 12);
        System.arraycopy(ciphertext, 0, nonceAndCiphertext, 12, ciphertext.length);

        return new Message(header, nonceAndCiphertext);
    }

    // ==================================================================================
    // Decrypt
    // ==================================================================================

    /**
     * Decrypt a received message, advancing the receiving chain (and possibly
     * stepping the DH ratchet).
     *
     * @param state   Ratchet state (modified in place)
     * @param message The received encrypted message
     * @param ad      Associated data for AEAD (must match what the sender used)
     * @return The decrypted plaintext
     * @throws SecurityException if decryption fails
     */
    public static byte[] decrypt(State state, Message message, byte[] ad) {
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(message, "message");

        Header header = message.header();

        // 1. Check skipped keys first
        SkippedKey sk = new SkippedKey(
                new ByteArrayWrapper(header.ratchetPub()), header.messageNum());
        byte[] skippedMk = state.skippedKeys.remove(sk);
        if (skippedMk != null) {
            byte[] headerAd = concatenateAd(ad, header);
            byte[] plaintext = decryptWithKey(skippedMk, message.ciphertext(), headerAd);
            zeroize(skippedMk);
            return plaintext;
        }

        // 2. DH ratchet step if new ratchet public key
        boolean newRatchet = state.recvRatchetPub == null
                || !Arrays.equals(header.ratchetPub(), state.recvRatchetPub.getEncoded());

        if (newRatchet) {
            // Skip any remaining messages in the old receiving chain
            if (state.recvChainKey != null) {
                skipMessages(state, header.prevChainLen());
            }
            dhRatchetStep(state, header);
        }

        // 3. Skip any messages in the current chain before this one
        skipMessages(state, header.messageNum());

        // 4. Derive message key
        byte[] messageKey = hmacSha256(state.recvChainKey, MSG_KEY_CONSTANT);
        byte[] newChainKey = hmacSha256(state.recvChainKey, CHAIN_KEY_CONSTANT);
        zeroize(state.recvChainKey);
        state.recvChainKey = newChainKey;
        state.recvMsgNum++;

        // 5. Decrypt
        byte[] headerAd = concatenateAd(ad, header);
        byte[] plaintext = decryptWithKey(messageKey, message.ciphertext(), headerAd);
        zeroize(messageKey);

        return plaintext;
    }

    // ==================================================================================
    // DH Ratchet Step
    // ==================================================================================

    /**
     * Perform a DH ratchet step: update root key and chain keys from a new
     * peer ratchet public key.
     */
    private static void dhRatchetStep(State state, Header header) {
        PublicKey peerPub = decodeX25519Public(header.ratchetPub());

        state.prevChainLen = state.sendMsgNum;
        state.sendMsgNum = 0;
        state.recvMsgNum = 0;
        state.recvRatchetPub = peerPub;

        // Derive receiving chain from peer's new ratchet key
        byte[] dhOut = ecdh(state.sendRatchetKey.getPrivate(), state.recvRatchetPub);
        byte[] kdfOut = kdfRootKey(state.rootKey, dhOut);
        zeroize(dhOut);
        zeroize(state.rootKey);

        state.rootKey = Arrays.copyOf(kdfOut, 32);
        state.recvChainKey = Arrays.copyOfRange(kdfOut, 32, 64);
        zeroize(kdfOut);

        // Generate new sending ratchet keypair
        state.sendRatchetKey = generateX25519();
        dhOut = ecdh(state.sendRatchetKey.getPrivate(), state.recvRatchetPub);
        kdfOut = kdfRootKey(state.rootKey, dhOut);
        zeroize(dhOut);
        zeroize(state.rootKey);
        zeroize(state.sendChainKey);

        state.rootKey = Arrays.copyOf(kdfOut, 32);
        state.sendChainKey = Arrays.copyOfRange(kdfOut, 32, 64);
        zeroize(kdfOut);
    }

    /**
     * Derive and cache message keys for skipped messages up to (but not including)
     * the target message number.
     */
    private static void skipMessages(State state, int until) {
        if (state.recvChainKey == null) return;
        if (until - state.recvMsgNum > MAX_SKIP) {
            throw new SecurityException(
                    "Too many skipped messages (" + (until - state.recvMsgNum) + " > " + MAX_SKIP + ")");
        }
        while (state.recvMsgNum < until) {
            byte[] mk = hmacSha256(state.recvChainKey, MSG_KEY_CONSTANT);
            byte[] newCk = hmacSha256(state.recvChainKey, CHAIN_KEY_CONSTANT);
            zeroize(state.recvChainKey);
            state.recvChainKey = newCk;

            SkippedKey sk = new SkippedKey(
                    new ByteArrayWrapper(state.recvRatchetPub.getEncoded()),
                    state.recvMsgNum);
            state.skippedKeys.put(sk, mk);
            state.recvMsgNum++;
        }
    }

    // ==================================================================================
    // KDF Functions
    // ==================================================================================

    /**
     * Root key KDF: HKDF(rootKey, dhOutput, info) → 64 bytes = (newRootKey, chainKey).
     */
    private static byte[] kdfRootKey(byte[] rootKey, byte[] dhOutput) {
        return EnvelopeOps.hkdfSha256(dhOutput, rootKey, ROOT_INFO, 64);
    }

    /**
     * HMAC-SHA256 for chain key derivation.
     */
    private static byte[] hmacSha256(byte[] key, byte[] data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return mac.doFinal(data);
        } catch (Exception e) {
            throw new SecurityException("HMAC-SHA256 failed", e);
        }
    }

    // ==================================================================================
    // AEAD (AES-256-GCM)
    // ==================================================================================

    private static byte[] aeadEncrypt(byte[] key, byte[] nonce, byte[] aad, byte[] plaintext) {
        try {
            javax.crypto.Cipher cipher = javax.crypto.Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(javax.crypto.Cipher.ENCRYPT_MODE,
                    new SecretKeySpec(key, "AES"),
                    new javax.crypto.spec.GCMParameterSpec(128, nonce));
            if (aad != null) cipher.updateAAD(aad);
            return cipher.doFinal(plaintext);
        } catch (Exception e) {
            throw new SecurityException("AEAD encryption failed", e);
        }
    }

    private static byte[] aeadDecrypt(byte[] key, byte[] nonce, byte[] aad, byte[] ciphertext) {
        try {
            javax.crypto.Cipher cipher = javax.crypto.Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(javax.crypto.Cipher.DECRYPT_MODE,
                    new SecretKeySpec(key, "AES"),
                    new javax.crypto.spec.GCMParameterSpec(128, nonce));
            if (aad != null) cipher.updateAAD(aad);
            return cipher.doFinal(ciphertext);
        } catch (Exception e) {
            throw new SecurityException("AEAD decryption failed (wrong key or tampered data)", e);
        }
    }

    /** Decrypt with nonce prepended to ciphertext. */
    private static byte[] decryptWithKey(byte[] messageKey, byte[] nonceAndCiphertext, byte[] aad) {
        byte[] nonce = Arrays.copyOf(nonceAndCiphertext, 12);
        byte[] ciphertext = Arrays.copyOfRange(nonceAndCiphertext, 12, nonceAndCiphertext.length);
        return aeadDecrypt(messageKey, nonce, aad, ciphertext);
    }

    // ==================================================================================
    // X25519 Helpers
    // ==================================================================================

    private static KeyPair generateX25519() {
        try {
            KeyPairGenerator kpg = KeyPairGenerator.getInstance("X25519");
            return kpg.generateKeyPair();
        } catch (NoSuchAlgorithmException e) {
            throw new SecurityException("X25519 not available", e);
        }
    }

    private static byte[] ecdh(PrivateKey myPrivate, PublicKey theirPublic) {
        try {
            javax.crypto.KeyAgreement ka = javax.crypto.KeyAgreement.getInstance("XDH");
            ka.init(myPrivate);
            ka.doPhase(theirPublic, true);
            return ka.generateSecret();
        } catch (Exception e) {
            throw new SecurityException("ECDH key agreement failed", e);
        }
    }

    private static PublicKey decodeX25519Public(byte[] spki) {
        try {
            KeyFactory kf = KeyFactory.getInstance("X25519");
            return kf.generatePublic(new java.security.spec.X509EncodedKeySpec(spki));
        } catch (Exception e) {
            throw new SecurityException("Failed to decode X25519 public key", e);
        }
    }

    // ==================================================================================
    // Utility
    // ==================================================================================

    /**
     * Concatenate AD bytes with header fields for AEAD binding.
     */
    private static byte[] concatenateAd(byte[] ad, Header header) {
        byte[] headerBytes = header.ratchetPub();
        int adLen = (ad != null ? ad.length : 0);
        // ad || ratchetPub || prevChainLen(4) || messageNum(4)
        byte[] result = new byte[adLen + headerBytes.length + 8];
        if (ad != null) System.arraycopy(ad, 0, result, 0, ad.length);
        int off = adLen;
        System.arraycopy(headerBytes, 0, result, off, headerBytes.length);
        off += headerBytes.length;
        result[off++] = (byte) (header.prevChainLen() >> 24);
        result[off++] = (byte) (header.prevChainLen() >> 16);
        result[off++] = (byte) (header.prevChainLen() >> 8);
        result[off++] = (byte) header.prevChainLen();
        result[off++] = (byte) (header.messageNum() >> 24);
        result[off++] = (byte) (header.messageNum() >> 16);
        result[off++] = (byte) (header.messageNum() >> 8);
        result[off] = (byte) header.messageNum();
        return result;
    }

    private static final SecureRandom SRNG = new SecureRandom();

    private static byte[] secureRandom(int len) {
        byte[] b = new byte[len];
        SRNG.nextBytes(b);
        return b;
    }

    private static void zeroize(byte[]... arrays) {
        for (byte[] a : arrays) {
            if (a != null) Arrays.fill(a, (byte) 0);
        }
    }
}
