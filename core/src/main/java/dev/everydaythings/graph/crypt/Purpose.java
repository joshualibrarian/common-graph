package dev.everydaythings.graph.crypt;

import java.util.EnumSet;

/**
 * Purpose defines what a key can be used for.
 *
 * <p>Keys can have one or more purposes. Purposes are stored as a bitmask
 * in KeyLog operations to allow efficient querying and filtering.
 *
 * <p>Standard purposes:
 * <ul>
 *   <li>{@link #SIGN} - Create digital signatures (default for Signer keys)</li>
 *   <li>{@link #ENCRYPT} - Encrypt content for this key</li>
 *   <li>{@link #AUTHENTICATE} - Authenticate in protocols (e.g., TLS client certs)</li>
 * </ul>
 */
public enum Purpose {
    /**
     * Key can create digital signatures.
     * This is the primary purpose for Signer keys.
     */
    SIGN(1),

    /**
     * Key can receive encrypted content.
     * Content encrypted to this key can be decrypted by the corresponding private key.
     */
    ENCRYPT(2),

    /**
     * Key can be used for authentication protocols.
     * Examples: TLS client certificates, SSH authentication.
     */
    AUTHENTICATE(4),

    /**
     * Key is a signed pre-key (SPK) for X3DH key agreement.
     * Medium-term key, rotated periodically, signed by the identity key.
     */
    PRE_KEY(8),

    /**
     * Key is a one-time pre-key (OPK) for X3DH key agreement.
     * Single-use: consumed and tombstoned after one X3DH exchange.
     */
    ONE_TIME_PRE_KEY(16);

    private final int bit;

    Purpose(int bit) {
        this.bit = bit;
    }

    /**
     * Get the bit value for this purpose.
     */
    public int bit() {
        return bit;
    }

    /**
     * Create a bitmask from multiple purposes.
     *
     * @param purposes The purposes to combine
     * @return A bitmask with all specified purposes set
     */
    public static int mask(Purpose... purposes) {
        int m = 0;
        for (Purpose p : purposes) {
            m |= p.bit;
        }
        return m;
    }

    /**
     * Create a bitmask from an EnumSet of purposes.
     *
     * @param purposes The purposes to combine
     * @return A bitmask with all specified purposes set
     */
    public static int mask(EnumSet<Purpose> purposes) {
        int m = 0;
        for (Purpose p : purposes) {
            m |= p.bit;
        }
        return m;
    }

    /**
     * Decode a bitmask into an EnumSet of purposes.
     *
     * @param mask The bitmask to decode
     * @return EnumSet containing all purposes present in the mask
     */
    public static EnumSet<Purpose> fromMask(int mask) {
        EnumSet<Purpose> set = EnumSet.noneOf(Purpose.class);
        for (Purpose p : values()) {
            if ((mask & p.bit) != 0) {
                set.add(p);
            }
        }
        return set;
    }

    /**
     * Check if a mask contains this purpose.
     *
     * @param mask The bitmask to check
     * @return true if this purpose is present in the mask
     */
    public boolean inMask(int mask) {
        return (mask & bit) != 0;
    }
}
