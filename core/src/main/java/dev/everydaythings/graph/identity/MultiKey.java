package dev.everydaythings.graph.identity;

import dev.everydaythings.graph.item.id.Varint;

import java.io.ByteArrayOutputStream;
import java.util.Arrays;
import java.util.Objects;

/**
 * Self-describing public key in multikey format.
 *
 * <p>Wire form: {@code <unsigned-varint codec> <raw-key-bytes>}.
 *
 * <p>The codec identifies the key TYPE (not its purpose). A multikey can be
 * constructed from an {@link Algorithm.Asymmetric} (which knows the codec for its
 * key type via {@link Algorithm.Asymmetric#multikeyCode()}) or from a raw codec
 * code plus key bytes.
 *
 * <p>Note that the same multikey code may correspond to multiple algorithms — for
 * example, RSA keys (multikey 0x1205) are used by both {@code PS256} signing and
 * {@code RSA_OAEP_256} key management. The wire form does not disambiguate
 * algorithm purpose; that comes from context (the surrounding {@code VarSig}, or
 * the binding role).
 */
public final class MultiKey {

    private final int code;
    private final byte[] rawKey;
    private final byte[] encoded;  // cached full multikey bytes

    private MultiKey(int code, byte[] rawKey, byte[] encoded) {
        this.code = code;
        this.rawKey = rawKey;
        this.encoded = encoded;
    }

    /**
     * Construct from an {@link Algorithm.Asymmetric} and raw key bytes. The codec
     * is taken from the algorithm. Validates length where the algorithm specifies
     * a fixed length.
     */
    public static MultiKey of(Algorithm.Asymmetric algorithm, byte[] rawKey) {
        Objects.requireNonNull(algorithm, "algorithm");
        Objects.requireNonNull(rawKey, "rawKey");
        validateLength(algorithm.rawKeyBytes(), rawKey.length, algorithm);
        return build(algorithm.multikeyCode(), rawKey);
    }

    /**
     * Construct from a raw codec code and key bytes. Validates length where a
     * registered algorithm exists for this code with a fixed expected length.
     */
    public static MultiKey of(int code, byte[] rawKey) {
        Objects.requireNonNull(rawKey, "rawKey");
        // If we recognize the code, validate length. If not, accept whatever bytes
        // were provided (forward-compat: unknown codecs may be carried through).
        try {
            Algorithm.Asymmetric algorithm = Algorithm.Asymmetric.byMultikeyCode(code);
            validateLength(algorithm.rawKeyBytes(), rawKey.length, algorithm);
        } catch (IllegalArgumentException unknownCode) {
            // unrecognized code; skip validation, carry bytes as-is
        }
        return build(code, rawKey);
    }

    /**
     * Decode a multikey from its full encoded form.
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

    private static MultiKey build(int code, byte[] rawKey) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Varint.writeUnsignedVarint(out, code);
        out.writeBytes(rawKey);
        return new MultiKey(code, rawKey.clone(), out.toByteArray());
    }

    private static void validateLength(int expected, int actual, Algorithm.Asymmetric algorithm) {
        if (expected != 0 && actual != expected) {
            throw new IllegalArgumentException(
                    algorithm + " expects " + expected + " key bytes, got " + actual);
        }
    }

    /** The multikey codec code identifying the key type. */
    public int code() {
        return code;
    }

    /**
     * The {@link Algorithm.Asymmetric} corresponding to this codec, if recognized.
     *
     * <p>Returns {@code null} if the codec is not registered with any known algorithm.
     * Note that for codes shared by multiple algorithms (e.g., RSA), this returns
     * the first registered match.
     */
    public Algorithm.Asymmetric algorithm() {
        try {
            return Algorithm.Asymmetric.byMultikeyCode(code);
        } catch (IllegalArgumentException unknownCode) {
            return null;
        }
    }

    /** The raw key bytes (defensive copy). */
    public byte[] rawKey() {
        return rawKey.clone();
    }

    /** The full multikey-encoded bytes (defensive copy). */
    public byte[] encoded() {
        return encoded.clone();
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
