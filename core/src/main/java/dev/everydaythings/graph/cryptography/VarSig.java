package dev.everydaythings.graph.cryptography;

import dev.everydaythings.graph.cryptography.algorithm.Signing;
import dev.everydaythings.graph.runtime.librarian.LibrarianHandle;
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
 * <p>The codec identifies the signature algorithm.  A varsig can be constructed
 * from an {@link Signing} (which knows its codec) or from a raw codec
 * code plus signature bytes.
 *
 * <p>Records in the Datum architecture carry their signature as a varsig blob in their
 * signature slot, making the algorithm self-describing without requiring out-of-band
 * algorithm signaling per record.
 */
public final class VarSig {

    private final int code;
    private final byte[] rawSig;
    private final byte[] encoded;  // cached full varsig bytes
    private final Signing algorithm;  // optional — set when decoded with a librarian

    private VarSig(int code, byte[] rawSig, byte[] encoded, Signing algorithm) {
        this.code = code;
        this.rawSig = rawSig;
        this.encoded = encoded;
        this.algorithm = algorithm;
    }

    /**
     * Construct from a raw codec code and signature bytes.  No validation
     * against a registered algorithm is performed here — pass through a
     * librarian (via {@link #decode(byte[], LibrarianHandle)}) to resolve an
     * algorithm and enable verification.
     */
    public static VarSig of(int code, byte[] rawSig) {
        Objects.requireNonNull(rawSig, "rawSig");
        return build(code, rawSig);
    }

    /**
     * Decode a varsig from its full encoded form.  Does not resolve the
     * algorithm; the resulting {@link VarSig} carries the codec but
     * {@link #algorithm()} returns {@code null}.  Use
     * {@link #decode(byte[], LibrarianHandle)} when algorithm resolution is wanted.
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
     * Decode a varsig and resolve its algorithm via the librarian.
     * The resulting {@link VarSig} can {@link #verify(byte[], MultiKey)}
     * with zero further lookups.
     */
    public static VarSig decode(byte[] bytes, LibrarianHandle librarian) {
        Objects.requireNonNull(bytes, "bytes");
        Objects.requireNonNull(librarian, "librarian");
        if (bytes.length == 0) {
            throw new IllegalArgumentException("VarSig bytes cannot be empty");
        }
        Varint.Read codecRead = Varint.readUnsignedVarint(bytes, 0);
        int code = (int) codecRead.value();
        byte[] rawSig = Arrays.copyOfRange(bytes, codecRead.next(), bytes.length);
        Signing algorithm = librarian.algorithmByVarsigCode(code);
        return build(code, rawSig, algorithm);
    }

    /**
     * Construct from an already-resolved {@link Signing} and raw
     * signature bytes.  The codec is taken from the algorithm.
     */
    public static VarSig of(Signing algorithm, byte[] rawSig) {
        Objects.requireNonNull(algorithm, "algorithm");
        Objects.requireNonNull(rawSig, "rawSig");
        return build((int) algorithm.varsigCode(), rawSig, algorithm);
    }

    private static VarSig build(int code, byte[] rawSig) {
        return build(code, rawSig, null);
    }

    private static VarSig build(int code, byte[] rawSig, Signing algorithm) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Varint.writeUnsignedVarint(out, code);
        out.writeBytes(rawSig);
        return new VarSig(code, rawSig.clone(), out.toByteArray(), algorithm);
    }

    /** The varsig codec code identifying the signature algorithm. */
    public int code() {
        return code;
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
     * The resolved signing algorithm, or {@code null} when this VarSig was
     * decoded without librarian context (via {@link #decode(byte[])} or
     * constructed via the bare {@link #of(int, byte[])} factory).
     */
    public Signing algorithm() {
        return algorithm;
    }

    /**
     * Verify this signature against a message and a public key, using the
     * algorithm that was resolved at decode time.  Zero further lookups.
     *
     * @throws IllegalStateException if this VarSig has no resolved algorithm
     *                               (decoded without a librarian)
     */
    public boolean verify(byte[] message, MultiKey publicKey) {
        if (algorithm == null) {
            throw new IllegalStateException(
                    "VarSig has no resolved algorithm — decode with a librarian to verify");
        }
        Objects.requireNonNull(publicKey, "publicKey");
        Signing keyAlgorithm = publicKey.signingAlgorithm();
        if (keyAlgorithm == null) {
            throw new IllegalStateException(
                    "MultiKey has no resolved algorithm — decode with a librarian to verify");
        }
        PublicKey jcaKey = keyAlgorithm.decodePublicKey(publicKey.rawKey());
        return algorithm.verify(message, rawSig, jcaKey);
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
