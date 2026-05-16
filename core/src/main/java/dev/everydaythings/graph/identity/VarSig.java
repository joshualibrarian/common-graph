package dev.everydaythings.graph.identity;

import dev.everydaythings.graph.runtime.librarian.Librarian;
import dev.everydaythings.graph.value.Varint;

import java.io.ByteArrayOutputStream;
import java.security.PublicKey;
import java.util.Arrays;
import java.util.Objects;

/**
 * Self-describing signature in varsig format.
 *
 * <p>Wire form: {@code <unsigned-varint codec> <raw-signature-bytes>}.
 *
 * <p>The codec identifies the signature algorithm. A varsig can be constructed from
 * an {@link Algorithm.Sign} (which knows the codec via
 * {@link Algorithm.Sign#varsigCode()}) or from a raw codec code plus signature bytes.
 *
 * <p>Records in the Datum architecture carry their signature as a varsig blob in their
 * signature slot, making the algorithm self-describing without requiring out-of-band
 * algorithm signaling per record.
 */
public final class VarSig {

    private final int code;
    private final byte[] rawSig;
    private final byte[] encoded;  // cached full varsig bytes
    private final AlgorithmHandle handle;  // optional — set when decoded with a librarian

    private VarSig(int code, byte[] rawSig, byte[] encoded, AlgorithmHandle handle) {
        this.code = code;
        this.rawSig = rawSig;
        this.encoded = encoded;
        this.handle = handle;
    }

    /**
     * Construct from an {@link Algorithm.Sign} and raw signature bytes. The codec
     * is taken from the algorithm. Validates length where the algorithm specifies
     * a fixed length.
     */
    public static VarSig of(Algorithm.Sign algorithm, byte[] rawSig) {
        Objects.requireNonNull(algorithm, "algorithm");
        Objects.requireNonNull(rawSig, "rawSig");
        validateLength(algorithm.sigBytes(), rawSig.length, algorithm);
        return build(algorithm.varsigCode(), rawSig);
    }

    /**
     * Construct from a raw codec code and signature bytes. Validates length where
     * a registered Sign algorithm exists for this code with a fixed expected length.
     */
    public static VarSig of(int code, byte[] rawSig) {
        Objects.requireNonNull(rawSig, "rawSig");
        try {
            Algorithm.Sign algorithm = Algorithm.Sign.byVarsigCode(code);
            validateLength(algorithm.sigBytes(), rawSig.length, algorithm);
        } catch (IllegalArgumentException unknownCode) {
            // unrecognized code; skip validation, carry bytes as-is
        }
        return build(code, rawSig);
    }

    /**
     * Decode a varsig from its full encoded form.  Does not resolve the
     * algorithm handle; the resulting {@link VarSig} carries the codec but
     * {@link #handle()} returns {@code null}.  Use
     * {@link #decode(byte[], Librarian)} when handle resolution is wanted.
     */
    public static VarSig decode(byte[] bytes) {
        Objects.requireNonNull(bytes, "bytes");
        if (bytes.length == 0) {
            throw new IllegalArgumentException("VarSig bytes cannot be empty");
        }
        Varint.Read codecRead = Varint.readUnsignedVarint(bytes, 0);
        byte[] rawSig = Arrays.copyOfRange(bytes, codecRead.next(), bytes.length);
        return of((int) codecRead.value(), rawSig);
    }

    /**
     * Decode a varsig and resolve its algorithm handle via the librarian.
     * The resulting {@link VarSig} can {@link #verify(byte[], MultiKey)}
     * with zero further lookups.
     */
    public static VarSig decode(byte[] bytes, Librarian librarian) {
        Objects.requireNonNull(bytes, "bytes");
        Objects.requireNonNull(librarian, "librarian");
        if (bytes.length == 0) {
            throw new IllegalArgumentException("VarSig bytes cannot be empty");
        }
        Varint.Read codecRead = Varint.readUnsignedVarint(bytes, 0);
        int code = (int) codecRead.value();
        byte[] rawSig = Arrays.copyOfRange(bytes, codecRead.next(), bytes.length);
        AlgorithmHandle handle = librarian.algorithmByVarsigCode(code);
        return build(code, rawSig, handle);
    }

    /**
     * Construct from an already-resolved {@link AlgorithmHandle} and raw
     * signature bytes.  The codec is taken from the handle.
     */
    public static VarSig of(AlgorithmHandle handle, byte[] rawSig) {
        Objects.requireNonNull(handle, "handle");
        Objects.requireNonNull(rawSig, "rawSig");
        return build((int) handle.varsigCode(), rawSig, handle);
    }

    private static VarSig build(int code, byte[] rawSig) {
        return build(code, rawSig, null);
    }

    private static VarSig build(int code, byte[] rawSig, AlgorithmHandle handle) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Varint.writeUnsignedVarint(out, code);
        out.writeBytes(rawSig);
        return new VarSig(code, rawSig.clone(), out.toByteArray(), handle);
    }

    private static void validateLength(int expected, int actual, Algorithm.Sign algorithm) {
        if (expected != 0 && actual != expected) {
            throw new IllegalArgumentException(
                    algorithm + " expects " + expected + " signature bytes, got " + actual);
        }
    }

    /** The varsig codec code identifying the signature algorithm. */
    public int code() {
        return code;
    }

    /**
     * The {@link Algorithm.Sign} corresponding to this codec, if recognized.
     *
     * <p>Returns {@code null} if the codec is not registered with any known signing
     * algorithm.
     */
    public Algorithm.Sign algorithm() {
        try {
            return Algorithm.Sign.byVarsigCode(code);
        } catch (IllegalArgumentException unknownCode) {
            return null;
        }
    }

    /** The raw signature bytes (defensive copy). */
    public byte[] rawSig() {
        return rawSig.clone();
    }

    /** The full varsig-encoded bytes (defensive copy). */
    public byte[] encoded() {
        return encoded.clone();
    }

    /**
     * The resolved algorithm handle, or {@code null} when this VarSig was
     * decoded without librarian context (via {@link #decode(byte[])} or
     * constructed via an enum-based factory).
     */
    public AlgorithmHandle handle() {
        return handle;
    }

    /**
     * Verify this signature against a message and a public key, using the
     * handle that was resolved at decode time.  Zero further lookups.
     *
     * @throws IllegalStateException if this VarSig has no resolved handle
     *                               (decoded without a librarian)
     */
    public boolean verify(byte[] message, MultiKey publicKey) {
        if (handle == null) {
            throw new IllegalStateException(
                    "VarSig has no resolved algorithm handle — decode with a librarian to verify");
        }
        Objects.requireNonNull(publicKey, "publicKey");
        AlgorithmHandle keyHandle = publicKey.handle();
        if (keyHandle == null) {
            throw new IllegalStateException(
                    "MultiKey has no resolved algorithm handle — decode with a librarian to verify");
        }
        PublicKey jcaKey = keyHandle.decodePublicKey(publicKey.rawKey());
        return handle.verify(message, rawSig, jcaKey);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof VarSig other)) return false;
        return Arrays.equals(encoded, other.encoded);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(encoded);
    }

    @Override
    public String toString() {
        return "VarSig[0x" + Integer.toHexString(code) + ", " + rawSig.length + " bytes]";
    }
}
