package dev.everydaythings.graph.canonical;

import io.ipfs.multihash.Multihash;
import org.bouncycastle.jce.provider.BouncyCastleProvider;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.Security;

/**
 * Substrate-shape cryptographic hash dispatch.
 *
 * <p>Pure-static facade over JCA's {@link MessageDigest}, with on-demand
 * BouncyCastle registration for non-JDK-native algorithms (BLAKE family).
 * Substrate-shape callers (content-addressing in {@link
 * dev.everydaythings.graph.ref.ContentRef ContentRef} / {@link
 * dev.everydaythings.graph.ref.DatumRef DatumRef}, structural hashing in
 * {@link HashTree}) reach this directly without depending on the Item-shaped
 * algorithm hierarchy in {@code cryptography.algorithm}.
 *
 * <p>The runtime {@code Hash} class (Item-extending, graph-integrated) is a
 * peer at the graph layer; both compute the same digests via the same JCA
 * mechanism.  Substrate uses this helper; graph-integrated code uses Hash.
 */
public final class Hashing {

    private Hashing() {}

    /** The protocol-pinned default hash for content addressing. */
    public static final Multihash.Type SHA256_TYPE = Multihash.Type.sha2_256;

    /**
     * Compute the SHA-256 digest of {@code input} — the content-addressing
     * default.  Convenience for {@link #compute(Multihash.Type, byte[])}
     * with {@link #SHA256_TYPE}.
     */
    public static byte[] sha256(byte[] input) {
        return compute(SHA256_TYPE, input);
    }

    /**
     * Compute the digest of {@code input} under the given multihash type.
     * Translates to the JCA algorithm name internally and dispatches to
     * {@link MessageDigest}.  Auto-installs BouncyCastle on first miss to
     * cover algorithms the JDK doesn't carry natively (BLAKE3, BLAKE2*).
     */
    public static byte[] compute(Multihash.Type type, byte[] input) {
        return compute(jcaNameFor(type), input);
    }

    /**
     * Invoke JCA to compute a digest by its JCA algorithm name.  If no
     * registered provider supplies the algorithm on the first attempt,
     * install BouncyCastle and retry once.  BC is added at lowest priority
     * so JDK-native algorithms keep being preferred.
     */
    public static byte[] compute(String jcaName, byte[] input) {
        if (input == null) throw new IllegalArgumentException("input must not be null");
        try {
            return MessageDigest.getInstance(jcaName).digest(input);
        } catch (NoSuchAlgorithmException firstAttempt) {
            installBouncyCastle();
            try {
                return MessageDigest.getInstance(jcaName).digest(input);
            } catch (NoSuchAlgorithmException retry) {
                throw new IllegalArgumentException(
                        "Hash algorithm not available: " + jcaName, retry);
            }
        }
    }

    /**
     * Register the BouncyCastle JCA provider so non-JDK-native digest names
     * (BLAKE3-256, BLAKE2B-256, BLAKE2S-256, etc.) resolve via
     * {@code MessageDigest.getInstance}.  Idempotent; added at lowest
     * priority so JDK-native algorithm names continue to resolve to the JDK.
     */
    public static synchronized void installBouncyCastle() {
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
    }

    /**
     * Translate a multihash type to its JCA algorithm name.  Covers the
     * algorithms substrate uses; for the full multihash → JCA mapping see
     * {@code cryptography.algorithm.Hash}.
     */
    private static String jcaNameFor(Multihash.Type type) {
        return switch (type) {
            case sha2_256    -> "SHA-256";
            case sha2_512    -> "SHA-512";
            case sha3_256    -> "SHA3-256";
            case blake3      -> "BLAKE3-256";
            case blake2b_256 -> "BLAKE2B-256";
            default -> throw new IllegalArgumentException(
                    "Unsupported multihash type: " + type);
        };
    }
}
