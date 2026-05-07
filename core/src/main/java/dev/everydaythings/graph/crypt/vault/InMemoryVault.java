package dev.everydaythings.graph.crypt.vault;

import dev.everydaythings.graph.crypt.Algorithm;
import dev.everydaythings.graph.crypt.MultiKey;
import dev.everydaythings.graph.crypt.VarSig;
import dev.everydaythings.graph.item.id.ContentID;

import java.math.BigInteger;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.Signature;
import java.security.interfaces.EdECPublicKey;
import java.security.spec.EdECPoint;
import java.security.spec.EdECPublicKeySpec;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.NamedParameterSpec;

/**
 * Ephemeral vault — keypairs held in process memory, lost on exit.
 *
 * <p>Generates fresh current + next keypairs at construction. Used for tests,
 * demos, and transient runs where persistent key custody isn't needed.
 *
 * <p>Phase 1 supports Ed25519 only. When other algorithms / encryption-track keys
 * become relevant, the InMemoryVault will be extended or other Vault implementations
 * will appear alongside it.
 */
public final class InMemoryVault implements Vault {

    private final KeyPair currentKeyPair;
    private final KeyPair nextKeyPair;
    private final Algorithm.Sign algorithm;

    private InMemoryVault(KeyPair currentKeyPair, KeyPair nextKeyPair, Algorithm.Sign algorithm) {
        this.currentKeyPair = currentKeyPair;
        this.nextKeyPair = nextKeyPair;
        this.algorithm = algorithm;
    }

    /**
     * Generate a fresh InMemoryVault with two newly-generated keypairs (current
     * and pre-rotation next) for the given algorithm.
     */
    public static InMemoryVault generate(Algorithm.Sign algorithm) {
        return new InMemoryVault(generateKeyPair(algorithm), generateKeyPair(algorithm), algorithm);
    }

    /** Generate using the default signing algorithm (Ed25519). */
    public static InMemoryVault generate() {
        return generate(Algorithm.Sign.ED25519);
    }

    @Override
    public java.util.Optional<Algorithm.Sign> signingAlgorithm() {
        return java.util.Optional.of(algorithm);
    }

    @Override
    public java.util.Optional<MultiKey> signingPublicKey() {
        return java.util.Optional.of(
                MultiKey.of(algorithm, rawPublicKey(currentKeyPair.getPublic(), algorithm)));
    }

    /**
     * Internal: the next signing public key, exposed within this package only
     * (rotation needs it; external callers must not see it pre-rotation).
     */
    MultiKey nextSigningPublicKey() {
        return MultiKey.of(algorithm, rawPublicKey(nextKeyPair.getPublic(), algorithm));
    }

    @Override
    public java.util.Optional<ContentID> signingNextKeyDigest() {
        return java.util.Optional.of(ContentID.of(nextSigningPublicKey().encoded()));
    }

    @Override
    public VarSig sign(byte[] message) {
        try {
            Signature sig = Signature.getInstance(algorithm.signatureName());
            sig.initSign(currentKeyPair.getPrivate());
            sig.update(message);
            return VarSig.of(algorithm, sig.sign());
        } catch (Exception e) {
            throw new RuntimeException("Signing failed", e);
        }
    }

    // ==================================================================================
    // Algorithm-specific helpers (Ed25519 only for Phase 1)
    // ==================================================================================

    private static KeyPair generateKeyPair(Algorithm.Sign algorithm) {
        try {
            KeyPairGenerator gen = KeyPairGenerator.getInstance(algorithm.keyGeneratorName());
            return gen.generateKeyPair();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("Unsupported algorithm: " + algorithm, e);
        }
    }

    private static byte[] rawPublicKey(PublicKey pub, Algorithm.Sign algorithm) {
        if (algorithm != Algorithm.Sign.ED25519) {
            throw new UnsupportedOperationException(
                    "Raw public key extraction not implemented for " + algorithm);
        }
        if (!(pub instanceof EdECPublicKey edPub)) {
            throw new IllegalStateException("Expected Ed25519 public key, got " + pub.getClass());
        }
        EdECPoint point = edPub.getPoint();
        byte[] yBE = point.getY().toByteArray();
        byte[] raw = new byte[32];
        int copyLen = Math.min(yBE.length, 32);
        for (int i = 0; i < copyLen; i++) {
            raw[i] = yBE[yBE.length - 1 - i];
        }
        if (point.isXOdd()) {
            raw[31] |= (byte) 0x80;
        }
        return raw;
    }

    /**
     * Decode an Ed25519 public key from the raw 32-byte little-endian-y-with-x-bit form.
     * Static helper used by Vault implementations and verification flows.
     */
    public static PublicKey publicKeyFromRaw(byte[] raw, Algorithm.Sign algorithm) {
        if (algorithm != Algorithm.Sign.ED25519) {
            throw new UnsupportedOperationException(
                    "Raw public key decoding not implemented for " + algorithm);
        }
        if (raw.length != 32) {
            throw new IllegalArgumentException("Ed25519 raw key must be 32 bytes, got " + raw.length);
        }
        boolean xOdd = (raw[31] & 0x80) != 0;
        byte[] yLE = raw.clone();
        yLE[31] &= 0x7F;
        byte[] yBE = new byte[32];
        for (int i = 0; i < 32; i++) {
            yBE[i] = yLE[31 - i];
        }
        BigInteger y = new BigInteger(1, yBE);
        try {
            EdECPoint point = new EdECPoint(xOdd, y);
            EdECPublicKeySpec spec = new EdECPublicKeySpec(NamedParameterSpec.ED25519, point);
            KeyFactory kf = KeyFactory.getInstance("Ed25519");
            return kf.generatePublic(spec);
        } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
            throw new RuntimeException("Failed to decode Ed25519 public key", e);
        }
    }
}
