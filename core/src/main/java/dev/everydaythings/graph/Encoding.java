package dev.everydaythings.graph;

import io.ipfs.multibase.Multibase;

/**
 * Common Graph text/binary encoding options for IDs and hashes.
 * Wraps io.ipfs.multibase.Multibase and adds raw hex for debug/internal use.
 */
public enum Encoding {
    // Multibase encodings that are good for human and machine use
    BASE32_LOWER(Multibase.Base.Base32),
    BASE32_UPPER(Multibase.Base.Base32Upper),
    BASE58_BTC(Multibase.Base.Base58BTC),
    BASE64_URL(Multibase.Base.Base64Url),
    BASE64(Multibase.Base.Base64),
    BASE16_LOWER(Multibase.Base.Base16),
    BASE16_UPPER(Multibase.Base.Base16Upper),

    HEX_RAW(null);  // no Multibase support, uses raw hex methods

    private static final char[] HEX_CHARS = "0123456789abcdef".toCharArray();

    /** The corresponding Multibase enum, or null for non-multibase encodings. */
    private final Multibase.Base multibase;

    Encoding(Multibase.Base multibase) {
        this.multibase = multibase;
    }

    /** System-wide default encoding for Common Graph IDs. */
    public static final Encoding DEFAULT = BASE32_LOWER;

    /** Encode bytes to string in this encoding. */
    public String encode(byte[] data) {
        if (this == HEX_RAW) {
            return hex(data);
        }
        return Multibase.encode(multibase, data);
    }

    public static byte[] decode(String encoded) {
        return Multibase.decode(encoded);
    }

    public Multibase.Base multibase() {
        return multibase;
    }

    // ==================================================================================
    // Hex Encoding (raw, no multibase prefix)
    // ==================================================================================

    /**
     * Encode bytes to lowercase hex string (no multibase prefix).
     */
    public static String hex(byte[] bytes) {
        if (bytes == null) return null;
        char[] out = new char[bytes.length * 2];
        int i = 0;
        for (byte b : bytes) {
            out[i++] = HEX_CHARS[(b >>> 4) & 0x0f];
            out[i++] = HEX_CHARS[b & 0x0f];
        }
        return new String(out);
    }

    /**
     * Decode hex string to bytes.
     *
     * @throws IllegalArgumentException if the string is not valid hex
     */
    public static byte[] unhex(String s) {
        if (s == null) return null;
        String v = s.trim();
        if ((v.length() & 1) != 0) {
            throw new IllegalArgumentException("Odd hex length: " + v.length());
        }
        int n = v.length() / 2;
        byte[] out = new byte[n];
        for (int i = 0; i < n; i++) {
            int hi = Character.digit(v.charAt(i * 2), 16);
            int lo = Character.digit(v.charAt(i * 2 + 1), 16);
            if (hi < 0 || lo < 0) {
                throw new IllegalArgumentException("Invalid hex character at position " + (i * 2));
            }
            out[i] = (byte) ((hi << 4) | lo);
        }
        return out;
    }
}
