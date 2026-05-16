package dev.everydaythings.graph.identity;

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
    private final AlgorithmHandle handle;  // optional — set when decoded with a librarian

    private MultiKey(int code, byte[] rawKey, byte[] encoded, AlgorithmHandle handle) {
        this.code = code;
        this.rawKey = rawKey;
        this.encoded = encoded;
        this.handle = handle;
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
     * Decode a multikey from its full encoded form.  Does not resolve the
     * algorithm handle; the resulting {@link MultiKey} carries the codec but
     * {@link #handle()} returns {@code null}.  Use
     * {@link #decode(byte[], Librarian)} when handle resolution is wanted.
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
     * Decode a multikey and resolve its algorithm handle via the librarian.
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
        AlgorithmHandle handle = librarian.algorithmByMultikeyCode(code);
        return build(code, rawKey, handle);
    }

    /**
     * Construct from an already-resolved {@link AlgorithmHandle} and raw
     * key bytes.  The codec is taken from the handle.
     */
    public static MultiKey of(AlgorithmHandle handle, byte[] rawKey) {
        Objects.requireNonNull(handle, "handle");
        Objects.requireNonNull(rawKey, "rawKey");
        return build((int) handle.multikeyCode(), rawKey, handle);
    }

    private static MultiKey build(int code, byte[] rawKey) {
        return build(code, rawKey, null);
    }

    private static MultiKey build(int code, byte[] rawKey, AlgorithmHandle handle) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Varint.writeUnsignedVarint(out, code);
        out.writeBytes(rawKey);
        return new MultiKey(code, rawKey.clone(), out.toByteArray(), handle);
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

    /**
     * The resolved algorithm handle, or {@code null} when this MultiKey was
     * decoded without librarian context.
     */
    public AlgorithmHandle handle() {
        return handle;
    }

    /**
     * Decode the raw key bytes into a JCA {@link java.security.PublicKey}
     * using the resolved handle.
     *
     * @throws IllegalStateException if this MultiKey has no resolved handle
     */
    public java.security.PublicKey publicKey() {
        if (handle == null) {
            throw new IllegalStateException(
                    "MultiKey has no resolved algorithm handle — decode with a librarian to materialize");
        }
        return handle.decodePublicKey(rawKey);
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
