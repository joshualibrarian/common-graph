package dev.everydaythings.graph.network.transport;

import dev.everydaythings.graph.crypt.EnvelopeOps;
import dev.everydaythings.graph.crypt.Vault;

import javax.crypto.Cipher;
import javax.crypto.KeyAgreement;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.security.*;
import java.security.spec.X509EncodedKeySpec;
import java.util.Arrays;

/**
 * Noise XX-inspired handshake and AEAD session encryption for CG transport.
 *
 * <p>Implements a 3-message handshake using X25519 (from each peer's Vault) that
 * provides mutual authentication, forward secrecy, and identity hiding:
 *
 * <pre>
 *   → e                     (initiator sends ephemeral public key)
 *   ← e, ee, s, es          (responder sends ephemeral + static, DH results)
 *   → s, se                 (initiator sends static, DH result)
 *   ↔ session keys derived  (both derive send_key/recv_key from chaining key)
 * </pre>
 *
 * <p>After handshake, all messages use AES-256-GCM with incrementing nonces.
 *
 * <p>All private key operations go through the {@link Vault} — keys never leave.
 */
public final class TransportCrypto {

    private TransportCrypto() {}

    private static final String X25519 = "X25519";
    private static final String X25519_KF = "XDH";
    private static final int KEY_LEN = 32;
    private static final int NONCE_LEN = 12;
    private static final int TAG_BITS = 128;
    private static final byte[] EMPTY = new byte[0];

    // ==================================================================================
    // Handshake State Machine
    // ==================================================================================

    /**
     * Role in the handshake.
     */
    public enum Role { INITIATOR, RESPONDER }

    /**
     * Handshake state machine. Call {@link #advance(byte[])} with each received
     * message (or null for the first initiator message) to produce the next
     * outgoing message. When {@link #isComplete()} returns true, call
     * {@link #split()} to get the session cipher pair.
     */
    public static final class HandshakeState {

        private final Role role;
        private final Vault vault;

        // Ephemeral keypair (generated locally)
        private KeyPair ephemeral;

        // Peer's ephemeral and static public keys (received during handshake)
        private PublicKey remoteEphemeral;
        private PublicKey remoteStatic;

        // Symmetric state: chaining key evolves through each DH mix
        private byte[] chainingKey;
        private byte[] handshakeHash;

        // Handshake encryption key (for encrypting static keys in messages 2 and 3)
        private byte[] encKey;

        private int step = 0;
        private boolean complete = false;

        /**
         * Create a new handshake state.
         *
         * @param role  Whether we're the initiator or responder
         * @param vault Vault containing our X25519 encryption key
         */
        public HandshakeState(Role role, Vault vault) {
            this.role = role;
            this.vault = vault;

            // Initialize symmetric state with protocol name
            byte[] protocolName = "CG_Noise_XX_25519_AESGCM".getBytes(java.nio.charset.StandardCharsets.UTF_8);
            if (protocolName.length <= 32) {
                this.handshakeHash = new byte[32];
                System.arraycopy(protocolName, 0, this.handshakeHash, 0, protocolName.length);
            } else {
                this.handshakeHash = sha256(protocolName);
            }
            this.chainingKey = Arrays.copyOf(this.handshakeHash, 32);
            this.encKey = EMPTY;
        }

        public boolean isComplete() {
            return complete;
        }

        public Role role() {
            return role;
        }

        /**
         * Get the authenticated remote static public key (available after handshake).
         */
        public PublicKey remoteStaticKey() {
            if (!complete) throw new IllegalStateException("Handshake not complete");
            return remoteStatic;
        }

        /**
         * Advance the handshake state machine.
         *
         * @param received The received message bytes (null for the initial initiator call)
         * @return The outgoing message bytes to send, or null if waiting for input
         */
        public byte[] advance(byte[] received) {
            if (complete) throw new IllegalStateException("Handshake already complete");

            return switch (role) {
                case INITIATOR -> advanceInitiator(received);
                case RESPONDER -> advanceResponder(received);
            };
        }

        // ---- Initiator: 3 steps ----
        // Step 0: send e (our ephemeral)
        // Step 1: receive (e, ee_payload, encrypted_s, es_payload) → send (encrypted_s, se_payload)
        // Step 2: complete

        private byte[] advanceInitiator(byte[] received) {
            switch (step) {
                case 0 -> {
                    // → e
                    ephemeral = generateX25519();
                    byte[] ePub = ephemeral.getPublic().getEncoded();
                    mixHash(ePub);
                    step = 1;
                    return ePub;
                }
                case 1 -> {
                    // ← e, ee, s, es (all packed in `received`)
                    ByteBuffer buf = ByteBuffer.wrap(received);

                    // Read responder's ephemeral public key (SPKI)
                    int reLen = readU16(buf);
                    byte[] reBytes = new byte[reLen];
                    buf.get(reBytes);
                    remoteEphemeral = decodeX25519Public(reBytes);
                    mixHash(reBytes);

                    // ee: ECDH(our ephemeral private, their ephemeral public)
                    byte[] eeSecret = ecdh(ephemeral.getPrivate(), remoteEphemeral);
                    mixKey(eeSecret);

                    // Read encrypted static key from responder
                    int encSLen = readU16(buf);
                    byte[] encS = new byte[encSLen];
                    buf.get(encS);
                    byte[] rsBytes = decryptAndHash(encS);
                    remoteStatic = decodeX25519Public(rsBytes);

                    // es: ECDH(our ephemeral private, their static public)
                    byte[] esSecret = ecdh(ephemeral.getPrivate(), remoteStatic);
                    mixKey(esSecret);

                    // Now send → s, se
                    // Our static key (encrypted)
                    byte[] myStatic = vault.publicKey(Vault.ENCRYPTION_KEY_ALIAS)
                            .orElseThrow(() -> new IllegalStateException("No encryption key in vault"))
                            .getEncoded();
                    byte[] encMyS = encryptAndHash(myStatic);

                    // se: ECDH(our static private, their ephemeral public)
                    byte[] seSecret = vault.deriveSharedSecret(Vault.ENCRYPTION_KEY_ALIAS, remoteEphemeral);
                    mixKey(seSecret);

                    // Build message 3
                    ByteBuffer out = ByteBuffer.allocate(2 + encMyS.length);
                    writeU16(out, encMyS.length);
                    out.put(encMyS);

                    complete = true;
                    step = 2;
                    return out.array();
                }
                default -> throw new IllegalStateException("Unexpected initiator step: " + step);
            }
        }

        // ---- Responder: 2 steps ----
        // Step 0: receive e → send (e, ee_payload, encrypted_s, es_payload)
        // Step 1: receive (encrypted_s, se_payload) → complete

        private byte[] advanceResponder(byte[] received) {
            switch (step) {
                case 0 -> {
                    // ← e (initiator's ephemeral)
                    remoteEphemeral = decodeX25519Public(received);
                    mixHash(received);

                    // Generate our ephemeral
                    ephemeral = generateX25519();
                    byte[] ePub = ephemeral.getPublic().getEncoded();
                    mixHash(ePub);

                    // ee: ECDH(our ephemeral private, their ephemeral public)
                    byte[] eeSecret = ecdh(ephemeral.getPrivate(), remoteEphemeral);
                    mixKey(eeSecret);

                    // Our static key (encrypted under handshake key)
                    byte[] myStatic = vault.publicKey(Vault.ENCRYPTION_KEY_ALIAS)
                            .orElseThrow(() -> new IllegalStateException("No encryption key in vault"))
                            .getEncoded();
                    byte[] encMyS = encryptAndHash(myStatic);

                    // es: ECDH(our static private, their ephemeral public)
                    byte[] esSecret = vault.deriveSharedSecret(Vault.ENCRYPTION_KEY_ALIAS, remoteEphemeral);
                    mixKey(esSecret);

                    // Build response: [e_len | e | encS_len | encS]
                    ByteBuffer out = ByteBuffer.allocate(2 + ePub.length + 2 + encMyS.length);
                    writeU16(out, ePub.length);
                    out.put(ePub);
                    writeU16(out, encMyS.length);
                    out.put(encMyS);

                    step = 1;
                    return out.array();
                }
                case 1 -> {
                    // ← s, se (initiator's encrypted static + DH)
                    ByteBuffer buf = ByteBuffer.wrap(received);

                    // Read encrypted static key from initiator
                    int encSLen = readU16(buf);
                    byte[] encS = new byte[encSLen];
                    buf.get(encS);
                    byte[] isBytes = decryptAndHash(encS);
                    remoteStatic = decodeX25519Public(isBytes);

                    // se: ECDH(our ephemeral private, their static public)
                    byte[] seSecret = ecdh(ephemeral.getPrivate(), remoteStatic);
                    mixKey(seSecret);

                    complete = true;
                    step = 2;
                    return null; // No outgoing message
                }
                default -> throw new IllegalStateException("Unexpected responder step: " + step);
            }
        }

        // ---- Symmetric state operations ----

        private void mixHash(byte[] data) {
            handshakeHash = sha256(concat(handshakeHash, data));
        }

        private void mixKey(byte[] inputKeyMaterial) {
            // HKDF with chaining key as salt, IKM as input
            byte[] temp = EnvelopeOps.hkdfSha256(inputKeyMaterial, chainingKey, null, 64);
            chainingKey = Arrays.copyOfRange(temp, 0, 32);
            encKey = Arrays.copyOfRange(temp, 32, 64);
        }

        private byte[] encryptAndHash(byte[] plaintext) {
            if (encKey.length == 0) {
                // No encryption key yet — send in clear (first message)
                mixHash(plaintext);
                return plaintext;
            }
            byte[] nonce = new byte[NONCE_LEN]; // zero nonce (one-shot per key)
            byte[] ciphertext = aeadEncrypt(encKey, nonce, handshakeHash, plaintext);
            mixHash(ciphertext);
            return ciphertext;
        }

        private byte[] decryptAndHash(byte[] ciphertext) {
            if (encKey.length == 0) {
                mixHash(ciphertext);
                return ciphertext;
            }
            byte[] nonce = new byte[NONCE_LEN];
            byte[] plaintext = aeadDecrypt(encKey, nonce, handshakeHash, ciphertext);
            mixHash(ciphertext);
            return plaintext;
        }

        /**
         * Split the handshake into a pair of session ciphers.
         *
         * <p>The initiator's send key is the responder's receive key and vice versa.
         *
         * @return A session cipher pair for post-handshake communication
         */
        public SessionCipherPair split() {
            if (!complete) throw new IllegalStateException("Handshake not complete");

            // Derive two 32-byte keys from the final chaining key
            byte[] keys = EnvelopeOps.hkdfSha256(EMPTY, chainingKey, null, 64);
            byte[] k1 = Arrays.copyOfRange(keys, 0, 32);
            byte[] k2 = Arrays.copyOfRange(keys, 32, 64);

            // Zeroize handshake state
            Arrays.fill(chainingKey, (byte) 0);
            Arrays.fill(encKey, (byte) 0);
            ephemeral = null;

            if (role == Role.INITIATOR) {
                return new SessionCipherPair(
                        new SessionCipher(k1), // initiator sends with k1
                        new SessionCipher(k2)  // initiator receives with k2
                );
            } else {
                return new SessionCipherPair(
                        new SessionCipher(k2), // responder sends with k2
                        new SessionCipher(k1)  // responder receives with k1
                );
            }
        }
    }

    // ==================================================================================
    // Session Cipher (post-handshake AEAD)
    // ==================================================================================

    /**
     * AEAD cipher for post-handshake session messages.
     * Uses AES-256-GCM with an incrementing nonce counter.
     */
    public static final class SessionCipher {
        private final byte[] key;
        private long nonce = 0;

        SessionCipher(byte[] key) {
            this.key = Arrays.copyOf(key, key.length);
        }

        /**
         * Encrypt a message with the session key.
         *
         * @param plaintext The message bytes
         * @return nonce (8 bytes, big-endian counter) || ciphertext
         */
        public byte[] encrypt(byte[] plaintext) {
            long n = nonce++;
            byte[] nonceBytes = nonceFromCounter(n);
            byte[] ciphertext = aeadEncrypt(key, nonceBytes, null, plaintext);

            // Prepend the 8-byte counter so the receiver knows the nonce
            byte[] result = new byte[8 + ciphertext.length];
            ByteBuffer.wrap(result).putLong(n);
            System.arraycopy(ciphertext, 0, result, 8, ciphertext.length);
            return result;
        }

        /**
         * Decrypt a message with the session key.
         *
         * @param message The message: nonce (8 bytes) || ciphertext
         * @return The plaintext bytes
         */
        public byte[] decrypt(byte[] message) {
            ByteBuffer buf = ByteBuffer.wrap(message);
            long n = buf.getLong();
            byte[] nonceBytes = nonceFromCounter(n);
            byte[] ciphertext = new byte[message.length - 8];
            buf.get(ciphertext);
            return aeadDecrypt(key, nonceBytes, null, ciphertext);
        }

        /**
         * Zeroize the key material.
         */
        public void destroy() {
            Arrays.fill(key, (byte) 0);
        }

        private static byte[] nonceFromCounter(long counter) {
            // 12-byte nonce: 4 zero bytes + 8-byte big-endian counter
            byte[] nonce = new byte[NONCE_LEN];
            ByteBuffer.wrap(nonce, 4, 8).putLong(counter);
            return nonce;
        }
    }

    /**
     * A pair of session ciphers: one for sending, one for receiving.
     */
    public record SessionCipherPair(SessionCipher send, SessionCipher recv) {
        public void destroy() {
            send.destroy();
            recv.destroy();
        }
    }

    // ==================================================================================
    // Handshake Message Types (for framing on the wire)
    // ==================================================================================

    /**
     * Message type byte prepended to handshake messages on the wire.
     */
    public static final byte MSG_HANDSHAKE = 0x01;

    /**
     * Message type byte prepended to encrypted session messages.
     */
    public static final byte MSG_TRANSPORT = 0x02;

    // ==================================================================================
    // Internal crypto primitives
    // ==================================================================================

    private static KeyPair generateX25519() {
        try {
            KeyPairGenerator kpg = KeyPairGenerator.getInstance(X25519);
            return kpg.generateKeyPair();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("X25519 not available", e);
        }
    }

    private static PublicKey decodeX25519Public(byte[] spki) {
        try {
            KeyFactory kf = KeyFactory.getInstance(X25519_KF);
            return kf.generatePublic(new X509EncodedKeySpec(spki));
        } catch (Exception e) {
            throw new SecurityException("Failed to decode X25519 public key", e);
        }
    }

    private static byte[] ecdh(PrivateKey myPrivate, PublicKey theirPublic) {
        try {
            KeyAgreement ka = KeyAgreement.getInstance(X25519_KF);
            ka.init(myPrivate);
            ka.doPhase(theirPublic, true);
            return ka.generateSecret();
        } catch (Exception e) {
            throw new SecurityException("ECDH key agreement failed", e);
        }
    }

    static byte[] aeadEncrypt(byte[] key, byte[] nonce, byte[] aad, byte[] plaintext) {
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE,
                    new SecretKeySpec(key, "AES"),
                    new GCMParameterSpec(TAG_BITS, nonce));
            if (aad != null) cipher.updateAAD(aad);
            return cipher.doFinal(plaintext);
        } catch (Exception e) {
            throw new RuntimeException("AEAD encryption failed", e);
        }
    }

    static byte[] aeadDecrypt(byte[] key, byte[] nonce, byte[] aad, byte[] ciphertext) {
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE,
                    new SecretKeySpec(key, "AES"),
                    new GCMParameterSpec(TAG_BITS, nonce));
            if (aad != null) cipher.updateAAD(aad);
            return cipher.doFinal(ciphertext);
        } catch (Exception e) {
            throw new SecurityException("AEAD decryption failed", e);
        }
    }

    private static byte[] sha256(byte[] data) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(data);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    private static byte[] concat(byte[] a, byte[] b) {
        byte[] result = new byte[a.length + b.length];
        System.arraycopy(a, 0, result, 0, a.length);
        System.arraycopy(b, 0, result, a.length, b.length);
        return result;
    }

    private static int readU16(ByteBuffer buf) {
        return ((buf.get() & 0xFF) << 8) | (buf.get() & 0xFF);
    }

    private static void writeU16(ByteBuffer buf, int value) {
        buf.put((byte) (value >> 8));
        buf.put((byte) value);
    }
}
