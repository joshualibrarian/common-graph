package dev.everydaythings.graph.value;

import java.io.ByteArrayOutputStream;

/**
 * Unsigned varint (LEB128) encoding utilities.
 *
 * <p>Varints encode non-negative integers as a sequence of bytes where each byte
 * holds 7 bits of data and a continuation flag in the high bit. Used by multiformats
 * specs (multihash, multibase, multikey, varsig) for self-describing length and code
 * prefixes.
 */
public final class Varint {

    private Varint() {}

    /** Result of reading a varint: the decoded value and the new read position. */
    public record Read(long value, int next) {}

    /**
     * Write an unsigned varint to a stream.
     *
     * @throws IllegalArgumentException if {@code value} is negative.
     */
    public static void writeUnsignedVarint(ByteArrayOutputStream out, long value) {
        if (value < 0) {
            throw new IllegalArgumentException("negative varint: " + value);
        }
        do {
            int b = (int) (value & 0x7F);
            value >>>= 7;
            if (value != 0) b |= 0x80;
            out.write(b);
        } while (value != 0);
    }

    /**
     * Encode an unsigned varint to a fresh byte array.
     */
    public static byte[] encodeUnsignedVarint(long value) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        writeUnsignedVarint(out, value);
        return out.toByteArray();
    }

    /**
     * Read an unsigned varint from a byte buffer starting at {@code offset}.
     *
     * @return the decoded value and the position immediately after the varint
     * @throws IllegalArgumentException if the buffer ends mid-varint
     */
    public static Read readUnsignedVarint(byte[] bytes, int offset) {
        if (bytes == null) {
            throw new IllegalArgumentException("buffer is null");
        }
        long value = 0;
        int shift = 0;
        int pos = offset;
        while (pos < bytes.length) {
            int b = bytes[pos++] & 0xFF;
            value |= ((long) (b & 0x7F)) << shift;
            if ((b & 0x80) == 0) {
                return new Read(value, pos);
            }
            shift += 7;
            if (shift >= 64) {
                throw new IllegalArgumentException("varint too long");
            }
        }
        throw new IllegalArgumentException("buffer ended mid-varint");
    }
}
