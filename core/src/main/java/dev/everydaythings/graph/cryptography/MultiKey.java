package dev.everydaythings.graph.cryptography;

import dev.everydaythings.graph.cryptography.algorithm.PublicKeyAlgorithm;
import dev.everydaythings.graph.cryptography.algorithm.Signing;
import dev.everydaythings.graph.runtime.librarian.Librarian;
import dev.everydaythings.graph.value.Varint;

import java.io.ByteArrayOutputStream;
import java.util.Arrays;
import java.util.Objects;

/**
 * Self-describing public key in multikey format.
 *
 * <p>Wire form: {@code <unsigned-varint codec> <raw-key-bytes>}.
 *
 * <p>The codec identifies the key TYPE (not its purpose).  A multikey can be
 * constructed from any {@link PublicKeyAlgorithm} (which knows its codec) — a
 * {@link Signing} for signing keys, a
 * {@link dev.everydaythings.graph.cryptography.algorithm.KeyAgreement KeyAgreement}
 * for ECDH-shaped keys — or from a raw codec code plus key bytes.
 *
 * <p>Note that the same multikey code may correspond to multiple algorithms — for
 * example, RSA keys (multikey 0x1205) are used by both {@code PS256} signing and
 * {@code RSA_OAEP_256} key management.  The wire form does not disambiguate
 * algorithm purpose; that comes from context (the surrounding {@code VarSig}, or
 * the binding role).
 */
public final class MultiKey {

    private final int code;
    private final byte[] rawKey;
    private final byte[] encoded;  // cached full multikey bytes
    private final PublicKeyAlgorithm algorithm;  // optional — set when resolved

    private MultiKey(int code, byte[] rawKey, byte[] encoded, PublicKeyAlgorithm algorithm) {
        this.code = code;
        this.rawKey = rawKey;
        this.encoded = encoded;
        this.algorithm = algorithm;
    }

    /**
     * Construct from a raw codec code and key bytes.  No validation against a
     * registered algorithm is performed here — pass through a librarian (via
     * {@link #decode(byte[], Librarian)}) to resolve an algorithm and enable
     * length-aware decoding.
     */
    public static MultiKey of(int code, byte[] rawKey) {
        Objects.requireNonNull(rawKey, "rawKey");
        return build(code, rawKey);
    }

    /**
     * Decode a multikey from its full encoded form.  Does not resolve the
     * algorithm; the resulting {@link MultiKey} carries the codec but
     * {@link #algorithm()} returns {@code null}.  Use
     * {@link #decode(byte[], Librarian)} when algorithm resolution is wanted.
     */
    public static MultiKey decode(byte[] bytes) {
        Objects.requireNonNull(bytes, "bytes");
        if (bytes.length == 0) {
            throw new IllegalArgumentException("MultiKey bytes cannot be empty");
        }
        Varint.Read codecRead = Varint.readUnsignedVarint(bytes, 0);
        byte[] rawKey = Arrays.copyOfRange(bytes, codecRead.next(), bytes.length);
        return of((int) codecRead.value(), rawKey);
    }

    /**
     * Decode a multikey and resolve its algorithm via the librarian.
     * The resulting {@link MultiKey} can produce a JCA {@link java.security.PublicKey}
     * directly via {@link #publicKey()} with zero further lookups.
     */
    public static MultiKey decode(byte[] bytes, Librarian librarian) {
        Objects.requireNonNull(bytes, "bytes");
        Objects.requireNonNull(librarian, "librarian");
        if (bytes.length == 0) {
            throw new IllegalArgumentException("MultiKey bytes cannot be empty");
        }
        Varint.Read codecRead = Varint.readUnsignedVarint(bytes, 0);
        int code = (int) codecRead.value();
        byte[] rawKey = Arrays.copyOfRange(bytes, codecRead.next(), bytes.length);
        Signing algorithm = librarian.algorithmByMultikeyCode(code);
        return build(code, rawKey, algorithm);
    }

    /**
     * Construct from an already-resolved {@link PublicKeyAlgorithm} and raw
     * key bytes.  The codec is taken from the algorithm.  Accepts any
     * key-bearing algorithm — {@link Signing}, KeyAgreement, etc.
     */
    public static MultiKey of(PublicKeyAlgorithm algorithm, byte[] rawKey) {
        Objects.requireNonNull(algorithm, "algorithm");
        Objects.requireNonNull(rawKey, "rawKey");
        return build((int) algorithm.multikeyCode(), rawKey, algorithm);
    }


    private static MultiKey build(int code, byte[] rawKey) {
        return build(code, rawKey, null);
    }

    private static MultiKey build(int code, byte[] rawKey, PublicKeyAlgorithm algorithm) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Varint.writeUnsignedVarint(out, code);
        out.writeBytes(rawKey);
        return new MultiKey(code, rawKey.clone(), out.toByteArray(), algorithm);
    }

    /** The multikey codec code identifying the key type. */
    public int code() {
        return code;
    }

    /** The raw key bytes (defensive copy). */
    public byte[] rawKey() {
        return rawKey.clone();
    }

    /** The full multikey-encoded bytes (defensive copy). */
    public byte[] encoded() {
        return encoded.clone();
    }

    /**
     * The resolved algorithm, or {@code null} when this MultiKey was decoded
     * without librarian context (or its codec maps to no known algorithm).
     * Typed as {@link PublicKeyAlgorithm} — callers that need a specific
     * sub-archetype (signing vs key-agreement) downcast.
     */
    public PublicKeyAlgorithm algorithm() {
        return algorithm;
    }

    /**
     * The resolved algorithm as a {@link Signing}, or {@code null} if the
     * algorithm is unresolved or is not a signing algorithm.  Convenience
     * accessor for verifier code paths that don't want the instanceof check.
     */
    public Signing signingAlgorithm() {
        return algorithm instanceof Signing s ? s : null;
    }

    /**
     * Decode the raw key bytes into a JCA {@link java.security.PublicKey}
     * using the resolved algorithm.
     *
     * @throws IllegalStateException if this MultiKey has no resolved algorithm
     */
    public java.security.PublicKey publicKey() {
        if (algorithm == null) {
            throw new IllegalStateException(
                    "MultiKey has no resolved algorithm — decode with a librarian to materialize");
        }
        return algorithm.decodePublicKey(rawKey);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof MultiKey other)) return false;
        return Arrays.equals(encoded, other.encoded);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(encoded);
    }

    @Override
    public String toString() {
        return "MultiKey[0x" + Integer.toHexString(code) + ", " + rawKey.length + " bytes]";
    }
}
