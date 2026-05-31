package dev.everydaythings.graph.cryptography;

import dev.everydaythings.graph.value.Varint;

import java.io.ByteArrayOutputStream;
import java.util.Arrays;
import java.util.Objects;

/**
 * A self-describing signature — a varint-prefixed codec code identifying the
 * signing algorithm, followed by the raw signature bytes.
 *
 * <p>Pure data: codec code + raw bytes + encoded form.  No algorithm reference,
 * no verify operation — those are runtime concerns that operate on VarSig
 * given an algorithm resolver.  This keeps VarSig substrate-shape.
 */
public final class VarSig {

    private final int code;
    private final byte[] rawSig;
    private final byte[] encoded;

    private VarSig(int code, byte[] rawSig, byte[] encoded) {
        this.code = code;
        this.rawSig = rawSig;
        this.encoded = encoded;
    }

    /** Construct from a varsig codec code and raw signature bytes. */
    public static VarSig of(int code, byte[] rawSig) {
        Objects.requireNonNull(rawSig, "rawSig");
        return build(code, rawSig);
    }

    /** Decode a varsig-encoded byte sequence. */
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
        return "VarSig(code=" + code + ", " + rawSig.length + " bytes)";
    }
}
