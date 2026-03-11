package dev.everydaythings.graph.crypt;

import dev.everydaythings.graph.Canonical;
import dev.everydaythings.graph.trust.EncryptionPublicKey;
import dev.everydaythings.graph.vault.Vault;
import lombok.Getter;
import lombok.experimental.Accessors;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.security.SecureRandom;
import java.util.*;

/**
 * Sender Key protocol for group chat encryption.
 *
 * <p>For group streams (3+ participants), per-pair Double Ratchet is O(n²)
 * in state. Sender Keys reduce this to O(n): each member maintains ONE
 * sending chain (symmetric ratchet), and distributes the key material to
 * all other members via 1:1 ratcheted channels.
 *
 * <h3>Protocol</h3>
 * <ol>
 *   <li>Member creates a {@link SenderState} (random chain key + key ID)</li>
 *   <li>Member creates a {@link DistributionMessage} and sends it to each
 *       group member via their 1:1 ratcheted channel (Double Ratchet)</li>
 *   <li>Recipients process the distribution into a {@link ReceiverState}</li>
 *   <li>Sender encrypts group messages with their chain key (symmetric ratchet)</li>
 *   <li>Recipients decrypt using the stored sender key</li>
 * </ol>
 *
 * <h3>Key Rotation</h3>
 * <p>When a member is removed from the group, ALL remaining members must
 * rotate their Sender Keys and redistribute — the removed member had
 * everyone's old keys. When a member joins, existing members distribute
 * their current Sender Keys to the new member.
 *
 * <h3>Properties</h3>
 * <ul>
 *   <li><b>O(n) state</b>: each member stores n-1 receiver states</li>
 *   <li><b>Forward secrecy per sender</b>: each chain ratchets independently</li>
 *   <li><b>No break-in recovery</b>: unlike Double Ratchet, there's no DH
 *       ratchet step — compromise of a chain key reveals all future messages
 *       until rotation. This is the tradeoff for O(n) vs O(n²).</li>
 * </ul>
 *
 * @see DoubleRatchet
 */
public final class SenderKey {

    private static final byte[] CHAIN_KEY_CONSTANT = {0x02};
    private static final byte[] MSG_KEY_CONSTANT = {0x01};
    private static final int MAX_SKIP = 2000;
    private static final int KEY_ID_LEN = 16;

    private SenderKey() {} // Static utility

    // ==================================================================================
    // Sender State
    // ==================================================================================

    /**
     * Sender's own state for their sending chain. Local-only.
     *
     * <p>Each group member has exactly one SenderState for each group they
     * participate in. The chain key advances with each message sent.
     */
    public static final class SenderState {
        /** Unique identifier for this sender key (random, 16 bytes). */
        @Getter @Accessors(fluent = true)
        private final byte[] keyId;

        /** Current chain key (32 bytes). Advances per message. */
        byte[] chainKey;

        /** Number of messages sent with this key. */
        int iteration;

        SenderState(byte[] keyId, byte[] chainKey) {
            this.keyId = keyId;
            this.chainKey = chainKey;
        }

        /** Zeroize key material. */
        public void destroy() {
            zeroize(chainKey);
            chainKey = null;
        }

        public int iteration() { return iteration; }
    }

    /**
     * Create a new sender key with a random chain key.
     *
     * @return Fresh sender state ready for encrypting
     */
    public static SenderState createSenderKey() {
        byte[] keyId = secureRandom(KEY_ID_LEN);
        byte[] chainKey = secureRandom(32);
        return new SenderState(keyId, chainKey);
    }

    // ==================================================================================
    // Distribution
    // ==================================================================================

    /**
     * Message distributed to group members containing the sender's key material.
     *
     * <p>This message is sent via 1:1 ratcheted channels (Double Ratchet) to
     * each group member. It contains everything the recipient needs to decrypt
     * future messages from this sender.
     *
     * @param keyId     Unique identifier for this sender key
     * @param chainKey  Current chain key value
     * @param iteration Current iteration (so receiver can sync)
     */
    @Canonical.Canonization(classType = Canonical.ClassCollectionType.ARRAY)
    public record DistributionMessage(
            @Canonical.Canon(order = 0) byte[] keyId,
            @Canonical.Canon(order = 1) byte[] chainKey,
            @Canonical.Canon(order = 2) int iteration
    ) {}

    /**
     * Create a distribution message from the sender's current state.
     *
     * <p>The distribution message captures a snapshot of the sender's chain.
     * It should be encrypted (via Double Ratchet or EnvelopeOps) before
     * sending to each group member.
     *
     * @param state The sender's current state
     * @return Distribution message to send to each group member
     */
    public static DistributionMessage distribute(SenderState state) {
        Objects.requireNonNull(state, "state");
        return new DistributionMessage(
                state.keyId.clone(),
                state.chainKey.clone(),
                state.iteration);
    }

    /**
     * Wrap a distribution message for a specific recipient using their
     * encryption public key.
     *
     * <p>Uses ECDH + HKDF + AES Key Wrap via {@link EnvelopeOps}. The
     * result is a self-contained blob that only the recipient can unwrap.
     *
     * @param dist         The distribution message
     * @param recipientKey The recipient's encryption public key
     * @return Wrapped blob to send to the recipient
     */
    public static byte[] wrapForRecipient(DistributionMessage dist, EncryptionPublicKey recipientKey) {
        // Serialize: keyId(16) || chainKey(32) || iteration(8, big-endian long)
        // Total = 56 bytes, must be multiple of 8 for AES Key Wrap (RFC 3394)
        byte[] material = new byte[KEY_ID_LEN + 32 + 8];
        System.arraycopy(dist.keyId(), 0, material, 0, KEY_ID_LEN);
        System.arraycopy(dist.chainKey(), 0, material, KEY_ID_LEN, 32);
        long iter = dist.iteration();
        int off = KEY_ID_LEN + 32;
        material[off]     = (byte) (iter >> 56);
        material[off + 1] = (byte) (iter >> 48);
        material[off + 2] = (byte) (iter >> 40);
        material[off + 3] = (byte) (iter >> 32);
        material[off + 4] = (byte) (iter >> 24);
        material[off + 5] = (byte) (iter >> 16);
        material[off + 6] = (byte) (iter >> 8);
        material[off + 7] = (byte) iter;

        return EnvelopeOps.wrapKeyForRecipient(material, recipientKey);
    }

    /**
     * Unwrap a distribution message received from a group member.
     *
     * @param blob  The wrapped blob from {@link #wrapForRecipient}
     * @param vault The recipient's vault
     * @param myKey The recipient's encryption public key
     * @return The unwrapped distribution message
     */
    public static DistributionMessage unwrapDistribution(byte[] blob, Vault vault, EncryptionPublicKey myKey) {
        byte[] material = EnvelopeOps.unwrapKeyForRecipient(blob, vault, myKey);
        byte[] keyId = Arrays.copyOf(material, KEY_ID_LEN);
        byte[] chainKey = Arrays.copyOfRange(material, KEY_ID_LEN, KEY_ID_LEN + 32);
        int off = KEY_ID_LEN + 32;
        int iteration = (int) (
                ((long)(material[off]     & 0xFF) << 56)
              | ((long)(material[off + 1] & 0xFF) << 48)
              | ((long)(material[off + 2] & 0xFF) << 40)
              | ((long)(material[off + 3] & 0xFF) << 32)
              | ((long)(material[off + 4] & 0xFF) << 24)
              | ((long)(material[off + 5] & 0xFF) << 16)
              | ((long)(material[off + 6] & 0xFF) << 8)
              | ((long)(material[off + 7] & 0xFF)));
        zeroize(material);
        return new DistributionMessage(keyId, chainKey, iteration);
    }

    // ==================================================================================
    // Receiver State
    // ==================================================================================

    /**
     * Receiver's record of a sender's key. One per group member per sender.
     *
     * <p>When a group message arrives, the receiver looks up the ReceiverState
     * by the message's keyId to find the right chain key for decryption.
     */
    public static final class ReceiverState {
        @Getter @Accessors(fluent = true)
        private final byte[] keyId;

        byte[] chainKey;
        int iteration;

        ReceiverState(byte[] keyId, byte[] chainKey, int iteration) {
            this.keyId = keyId;
            this.chainKey = chainKey;
            this.iteration = iteration;
        }

        /** Zeroize key material. */
        public void destroy() {
            zeroize(chainKey);
            chainKey = null;
        }

        public int iteration() { return iteration; }
    }

    /**
     * Process a received distribution message into a receiver state.
     *
     * @param dist The distribution message from a group member
     * @return Receiver state for decrypting that member's messages
     */
    public static ReceiverState receive(DistributionMessage dist) {
        Objects.requireNonNull(dist, "dist");
        return new ReceiverState(
                dist.keyId().clone(),
                dist.chainKey().clone(),
                dist.iteration());
    }

    // ==================================================================================
    // Group Message
    // ==================================================================================

    /**
     * An encrypted group message.
     *
     * @param keyId      Identifies which sender key was used
     * @param iteration  Message number in the sender's chain
     * @param ciphertext Nonce (12 bytes) + AEAD ciphertext
     */
    public record GroupMessage(byte[] keyId, int iteration, byte[] ciphertext) {}

    // ==================================================================================
    // Encrypt
    // ==================================================================================

    /**
     * Encrypt a message for the group using the sender's chain key.
     *
     * <p>Advances the sender's chain: derives a message key from the current
     * chain key, encrypts, then ratchets the chain key forward.
     *
     * @param state     Sender's state (modified in place)
     * @param plaintext The message bytes
     * @param ad        Associated data for AEAD (e.g., group item ID)
     * @return Encrypted group message
     */
    public static GroupMessage encrypt(SenderState state, byte[] plaintext, byte[] ad) {
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(plaintext, "plaintext");
        if (state.chainKey == null) {
            throw new IllegalStateException("Sender key has been destroyed");
        }

        // Derive message key and advance chain
        byte[] messageKey = hmacSha256(state.chainKey, MSG_KEY_CONSTANT);
        byte[] newChainKey = hmacSha256(state.chainKey, CHAIN_KEY_CONSTANT);
        zeroize(state.chainKey);
        state.chainKey = newChainKey;

        int msgIteration = state.iteration;
        state.iteration++;

        // AEAD encrypt
        byte[] nonce = secureRandom(12);
        byte[] ciphertext = aeadEncrypt(messageKey, nonce, ad, plaintext);
        zeroize(messageKey);

        // Pack nonce + ciphertext
        byte[] packed = new byte[12 + ciphertext.length];
        System.arraycopy(nonce, 0, packed, 0, 12);
        System.arraycopy(ciphertext, 0, packed, 12, ciphertext.length);

        return new GroupMessage(state.keyId.clone(), msgIteration, packed);
    }

    // ==================================================================================
    // Decrypt
    // ==================================================================================

    /**
     * Decrypt a group message using the receiver's stored sender key.
     *
     * <p>If the message iteration is ahead of the receiver's current state,
     * the chain is advanced (skipping intermediate keys). If the iteration
     * is behind, it must have been a previously skipped message — not
     * recoverable (Sender Keys don't cache skipped keys like Double Ratchet).
     *
     * @param state   Receiver's state for this sender (modified in place)
     * @param message The encrypted group message
     * @param ad      Associated data (must match sender)
     * @return Decrypted plaintext
     * @throws SecurityException if decryption fails or message is too far ahead
     */
    public static byte[] decrypt(ReceiverState state, GroupMessage message, byte[] ad) {
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(message, "message");
        if (state.chainKey == null) {
            throw new IllegalStateException("Receiver key has been destroyed");
        }
        if (!Arrays.equals(state.keyId, message.keyId())) {
            throw new SecurityException("Key ID mismatch: message uses a different sender key");
        }

        int target = message.iteration();
        if (target < state.iteration) {
            throw new SecurityException(
                    "Cannot decrypt past message (iteration " + target
                            + " < current " + state.iteration + ")");
        }

        int skip = target - state.iteration;
        if (skip > MAX_SKIP) {
            throw new SecurityException(
                    "Too many skipped messages (" + skip + " > " + MAX_SKIP + ")");
        }

        // Advance chain to the target iteration
        byte[] chainKey = state.chainKey;
        for (int i = 0; i < skip; i++) {
            byte[] next = hmacSha256(chainKey, CHAIN_KEY_CONSTANT);
            if (chainKey != state.chainKey) zeroize(chainKey);
            chainKey = next;
        }

        // Derive message key at the target iteration
        byte[] messageKey = hmacSha256(chainKey, MSG_KEY_CONSTANT);
        byte[] newChainKey = hmacSha256(chainKey, CHAIN_KEY_CONSTANT);
        if (chainKey != state.chainKey) zeroize(chainKey);
        zeroize(state.chainKey);
        state.chainKey = newChainKey;
        state.iteration = target + 1;

        // Decrypt
        byte[] packed = message.ciphertext();
        byte[] nonce = Arrays.copyOf(packed, 12);
        byte[] ciphertext = Arrays.copyOfRange(packed, 12, packed.length);
        byte[] plaintext = aeadDecrypt(messageKey, nonce, ad, ciphertext);
        zeroize(messageKey);

        return plaintext;
    }

    // ==================================================================================
    // Key Registry (manages receiver states for a group)
    // ==================================================================================

    /**
     * Registry of receiver states for a group. Maps sender key IDs to their
     * receiver state. One registry per group per member.
     */
    public static final class KeyRegistry {
        private final Map<ByteArrayKey, ReceiverState> states = new LinkedHashMap<>();

        /** Register a receiver state from a distribution message. */
        public void register(ReceiverState state) {
            states.put(new ByteArrayKey(state.keyId), state);
        }

        /** Look up a receiver state by key ID. */
        public ReceiverState lookup(byte[] keyId) {
            return states.get(new ByteArrayKey(keyId));
        }

        /** Remove a sender's key (e.g., when they leave the group). */
        public void remove(byte[] keyId) {
            ReceiverState removed = states.remove(new ByteArrayKey(keyId));
            if (removed != null) removed.destroy();
        }

        /** Number of registered sender keys. */
        public int size() { return states.size(); }

        /** Zeroize all stored keys. */
        public void destroy() {
            for (ReceiverState s : states.values()) s.destroy();
            states.clear();
        }

        private record ByteArrayKey(byte[] data) {
            @Override public boolean equals(Object o) {
                return o instanceof ByteArrayKey k && Arrays.equals(data, k.data);
            }
            @Override public int hashCode() { return Arrays.hashCode(data); }
        }
    }

    // ==================================================================================
    // Crypto Helpers
    // ==================================================================================

    private static byte[] hmacSha256(byte[] key, byte[] data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return mac.doFinal(data);
        } catch (Exception e) {
            throw new SecurityException("HMAC-SHA256 failed", e);
        }
    }

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
