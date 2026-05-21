package dev.everydaythings.graph.network.noise;

import dev.everydaythings.graph.cryptography.algorithm.Aead;
import dev.everydaythings.graph.cryptography.algorithm.Hash;
import dev.everydaythings.graph.cryptography.algorithm.Kdf;
import dev.everydaythings.graph.cryptography.algorithm.KeyAgreement;
import dev.everydaythings.graph.cryptography.vault.Vault;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.PublicKey;
import java.util.Arrays;

/**
 * Noise_XX_25519_AESGCM_SHA256 handshake state machine.
 *
 * <pre>
 *   -&gt; e
 *   &lt;- e, ee, s, es
 *   -&gt; s, se
 * </pre>
 *
 * <p>Static-key DH steps (the {@code es} and {@code se} tokens that use this
 * party's static key) delegate to {@link Vault#agree}.  The static private
 * key never leaves the vault.  Ephemeral DH uses the in-memory ephemeral
 * keypair directly through {@link KeyAgreement.X25519#agree}.
 */
final class NoiseHandshake {

    enum Role { INITIATOR, RESPONDER }

    private static final String PROTOCOL = "Noise_XX_25519_AESGCM_SHA256";
    private static final int DH_LEN = 32;
    private static final int TAG_LEN = 16;
    private static final int HASH_LEN = 32;

    private final Role role;
    private final Vault vault;
    private final KeyAgreement.X25519 x25519 = KeyAgreement.X25519.builtin();
    private final Aead.AesGcm256 aead = Aead.AesGcm256.builtin();
    private final Kdf.HkdfSha256 hkdf = Kdf.HkdfSha256.builtin();

    // SymmetricState: chaining key, handshake hash, optional cipher key + nonce.
    private byte[] ck;
    private byte[] h;
    private byte[] cipherKey;       // null when no key set
    private long cipherNonce;       // resets to 0 on each MixKey

    // HandshakeState: our keys + remote keys + which message we're on.
    private final byte[] sPub;
    private KeyPair e;
    private byte[] rs;
    private PublicKey re;
    private int msgIndex;

    NoiseHandshake(Role role, Vault vault) {
        this.role = role;
        this.vault = vault;
        this.sPub = vault.keyAgreementPublicKey()
                .orElseThrow(() -> new IllegalStateException("Vault has no key-agreement key"))
                .rawKey();

        byte[] protoBytes = PROTOCOL.getBytes(StandardCharsets.UTF_8);
        this.h = new byte[HASH_LEN];
        if (protoBytes.length <= HASH_LEN) {
            System.arraycopy(protoBytes, 0, h, 0, protoBytes.length);
        } else {
            h = Hash.Sha256.digestOf(protoBytes);
        }
        this.ck = h.clone();
    }

    boolean isComplete() {
        return msgIndex >= 3;
    }

    boolean isMyTurnToWrite() {
        if (isComplete()) return false;
        return role == Role.INITIATOR ? (msgIndex == 0 || msgIndex == 2) : (msgIndex == 1);
    }

    byte[] writeMessage(byte[] payload) {
        return switch (msgIndex) {
            case 0 -> writeE(payload);                  // -> e
            case 1 -> writeEEesS(payload);              // <- e, ee, s, es
            case 2 -> writeSse(payload);                // -> s, se
            default -> throw new IllegalStateException("handshake already complete");
        };
    }

    byte[] readMessage(byte[] msg) {
        return switch (msgIndex) {
            case 0 -> readE(msg);
            case 1 -> readEEesS(msg);
            case 2 -> readSse(msg);
            default -> throw new IllegalStateException("handshake already complete");
        };
    }

    /** The peer's verified static public key (raw multikey-payload bytes). */
    byte[] remoteStaticPub() {
        if (!isComplete()) throw new IllegalStateException("handshake not complete");
        return rs.clone();
    }

    /**
     * Derive the two transport cipher keys.  For the initiator, {@code k1} is
     * the send key and {@code k2} the receive key; the responder swaps.
     */
    TransportKeys split() {
        if (!isComplete()) throw new IllegalStateException("handshake not complete");
        byte[] out = hkdf.derive(new byte[0], ck, new byte[0], 2 * HASH_LEN);
        byte[] k1 = Arrays.copyOfRange(out, 0, HASH_LEN);
        byte[] k2 = Arrays.copyOfRange(out, HASH_LEN, 2 * HASH_LEN);
        return role == Role.INITIATOR ? new TransportKeys(k1, k2) : new TransportKeys(k2, k1);
    }

    record TransportKeys(byte[] send, byte[] receive) {}

    // ==================================================================================
    // Message handlers
    // ==================================================================================

    // -> e
    private byte[] writeE(byte[] payload) {
        e = x25519.generateKeyPair();
        byte[] ePub = x25519.publicKeyToRaw(e.getPublic());
        mixHash(ePub);
        byte[] sealedPayload = encryptAndHash(payload);
        msgIndex++;
        return concat(ePub, sealedPayload);
    }

    // read <- e
    private byte[] readE(byte[] msg) {
        require(msg.length >= DH_LEN, "msg too short for e");
        byte[] rePub = Arrays.copyOfRange(msg, 0, DH_LEN);
        re = x25519.decodePublicKey(rePub);
        mixHash(rePub);
        byte[] payload = decryptAndHash(Arrays.copyOfRange(msg, DH_LEN, msg.length));
        msgIndex++;
        return payload;
    }

    // <- e, ee, s, es  (responder writes)
    private byte[] writeEEesS(byte[] payload) {
        e = x25519.generateKeyPair();
        byte[] ePub = x25519.publicKeyToRaw(e.getPublic());
        mixHash(ePub);
        mixKey(x25519.agree(e.getPrivate(), re));        // ee
        byte[] sealedS = encryptAndHash(sPub);            // s
        mixKey(vault.agree(re));                          // es: responder uses static × remote ephemeral
        byte[] sealedPayload = encryptAndHash(payload);
        msgIndex++;
        return concat(ePub, sealedS, sealedPayload);
    }

    // -> read e, ee, s, es  (initiator reads)
    private byte[] readEEesS(byte[] msg) {
        require(msg.length >= DH_LEN + DH_LEN + TAG_LEN, "msg too short for e+s");
        byte[] rePub = Arrays.copyOfRange(msg, 0, DH_LEN);
        re = x25519.decodePublicKey(rePub);
        mixHash(rePub);
        mixKey(x25519.agree(e.getPrivate(), re));        // ee
        byte[] sealedS = Arrays.copyOfRange(msg, DH_LEN, DH_LEN + DH_LEN + TAG_LEN);
        rs = decryptAndHash(sealedS);                     // s
        mixKey(x25519.agree(e.getPrivate(),               // es: initiator uses ephemeral × remote static
                            x25519.decodePublicKey(rs)));
        byte[] payload = decryptAndHash(Arrays.copyOfRange(msg, DH_LEN + DH_LEN + TAG_LEN, msg.length));
        msgIndex++;
        return payload;
    }

    // -> s, se  (initiator writes)
    private byte[] writeSse(byte[] payload) {
        byte[] sealedS = encryptAndHash(sPub);            // s
        mixKey(vault.agree(re));                          // se: initiator uses static × remote ephemeral
        byte[] sealedPayload = encryptAndHash(payload);
        msgIndex++;
        return concat(sealedS, sealedPayload);
    }

    // <- read s, se  (responder reads)
    private byte[] readSse(byte[] msg) {
        require(msg.length >= DH_LEN + TAG_LEN, "msg too short for s");
        byte[] sealedS = Arrays.copyOfRange(msg, 0, DH_LEN + TAG_LEN);
        rs = decryptAndHash(sealedS);                     // s
        mixKey(x25519.agree(e.getPrivate(),               // se: responder uses ephemeral × remote static
                            x25519.decodePublicKey(rs)));
        byte[] payload = decryptAndHash(Arrays.copyOfRange(msg, DH_LEN + TAG_LEN, msg.length));
        msgIndex++;
        return payload;
    }

    // ==================================================================================
    // SymmetricState helpers
    // ==================================================================================

    private void mixHash(byte[] data) {
        h = Hash.Sha256.digestOf(concat(h, data));
    }

    private void mixKey(byte[] ikm) {
        // Noise's HKDF(chaining_key, ikm, 2 outputs) == RFC 5869 with salt=ck, IKM=ikm.
        byte[] out = hkdf.derive(ikm, ck, new byte[0], 2 * HASH_LEN);
        ck = Arrays.copyOfRange(out, 0, HASH_LEN);
        cipherKey = Arrays.copyOfRange(out, HASH_LEN, 2 * HASH_LEN);
        cipherNonce = 0;
    }

    private byte[] encryptAndHash(byte[] plaintext) {
        if (cipherKey == null) {
            mixHash(plaintext);
            return plaintext;
        }
        byte[] ciphertext = aead.encrypt(cipherKey, nonce(cipherNonce++), h, plaintext);
        mixHash(ciphertext);
        return ciphertext;
    }

    private byte[] decryptAndHash(byte[] ciphertext) {
        if (cipherKey == null) {
            mixHash(ciphertext);
            return ciphertext;
        }
        byte[] plaintext = aead.decrypt(cipherKey, nonce(cipherNonce++), h, ciphertext);
        mixHash(ciphertext);
        return plaintext;
    }

    // Noise spec: 96-bit AES-GCM nonce = 32 zero bits || 64-bit little-endian counter.
    private static byte[] nonce(long counter) {
        ByteBuffer buf = ByteBuffer.allocate(12);
        buf.putInt(0);
        buf.putLong(Long.reverseBytes(counter));
        return buf.array();
    }

    private static byte[] concat(byte[]... parts) {
        int total = 0;
        for (byte[] p : parts) total += p.length;
        byte[] out = new byte[total];
        int off = 0;
        for (byte[] p : parts) {
            System.arraycopy(p, 0, out, off, p.length);
            off += p.length;
        }
        return out;
    }

    private static void require(boolean cond, String msg) {
        if (!cond) throw new IllegalStateException(msg);
    }
}
