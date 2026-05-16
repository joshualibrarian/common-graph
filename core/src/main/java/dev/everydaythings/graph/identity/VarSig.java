package dev.everydaythings.graph.identity;

import dev.everydaythings.graph.value.Varint;

import java.io.ByteArrayOutputStream;
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

    private VarSig(int code, byte[] rawSig, byte[] encoded) {
        this.code = code;
        this.rawSig = rawSig;
        this.encoded = encoded;
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
     * Decode a varsig from its full encoded form.
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

    private static VarSig build(int code, byte[] rawSig) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Varint.writeUnsignedVarint(out, code);
        out.writeBytes(rawSig);
        return new VarSig(code, rawSig.clone(), out.toByteArray());
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
