package dev.everydaythings.graph.library.bytestore;

/**
 * Schema definition for a column/table in a byte store.
 *
 * <p>A ColumnSchema defines:
 * <ul>
 *   <li>A unique name for the column/table</li>
 *   <li>Optional prefix length for optimized prefix scans</li>
 *   <li>Key composition - the sequence of encoders that make up a key</li>
 * </ul>
 *
 * <p>Implementations are typically enums, allowing type-safe column references:
 * <pre>{@code
 * public enum MyCF implements ColumnSchema {
 *     USERS("users", 32, KeyEncoder.ID),
 *     POSTS("posts", 32, KeyEncoder.ID, KeyEncoder.U64);
 *
 *     private final String schemaName;
 *     private final Integer prefixLen;
 *     private final KeyEncoder[] keyComposition;
 *
 *     MyCF(String schemaName, Integer prefixLen, KeyEncoder... keys) {
 *         this.schemaName = schemaName;
 *         this.prefixLen = prefixLen;
 *         this.keyComposition = keys;
 *     }
 *
 *     @Override public String schemaName() { return schemaName; }
 *     @Override public Integer prefixLen() { return prefixLen; }
 *     @Override public KeyEncoder[] keyComposition() { return keyComposition; }
 * }
 * }</pre>
 *
 * <p>Note: We use {@code schemaName()} instead of {@code name()} because Enum.name() is final.
 */
public interface ColumnSchema {

    /**
     * The unique name of this column/table.
     *
     * <p>Named {@code schemaName()} to avoid conflict with {@link Enum#name()}.
     */
    String schemaName();

    /**
     * Optional prefix length for prefix-optimized iteration.
     * Return null if no prefix optimization is needed.
     */
    Integer prefixLen();

    /**
     * The key composition - sequence of encoders that make up a complete key.
     */
    KeyEncoder[] keyComposition();

    /**
     * Optional bloom filter bits per key.
     * Used by backends that support bloom filters (e.g., RocksDB).
     * Return null if no bloom filter is needed.
     *
     * <p>Default returns null - backends that don't support bloom filters
     * can safely ignore this.
     */
    default Integer bloomBits() {
        return null;
    }

    /**
     * Build a complete key from the given parts.
     *
     * @param args values to encode, must match keyComposition() length
     * @return the concatenated key bytes
     * @throws IllegalArgumentException if args count doesn't match keyComposition
     */
    default byte[] key(Object... args) {
        KeyEncoder[] composition = keyComposition();
        if (args.length != composition.length) {
            throw new IllegalArgumentException(
                    schemaName() + " expects " + composition.length + " key parts, got " + args.length);
        }
        byte[][] segments = new byte[composition.length][];
        for (int i = 0; i < composition.length; i++) {
            segments[i] = composition[i].bytes(args[i]);
        }
        return KeyEncoder.cat(segments);
    }

    /**
     * Build a key prefix from the given parts.
     *
     * @param prefixArgs values to encode for prefix (may be fewer than keyComposition length)
     * @return the concatenated prefix bytes
     * @throws IllegalArgumentException if prefixArgs count exceeds keyComposition length
     */
    default byte[] keyPrefix(Object... prefixArgs) {
        if (prefixArgs == null) prefixArgs = new Object[0];
        KeyEncoder[] composition = keyComposition();
        if (prefixArgs.length > composition.length) {
            throw new IllegalArgumentException(
                    schemaName() + " prefix max " + composition.length + " parts, got " + prefixArgs.length);
        }
        byte[][] segs = new byte[prefixArgs.length][];
        for (int i = 0; i < prefixArgs.length; i++) {
            segs[i] = composition[i].bytes(prefixArgs[i]);
        }
        return KeyEncoder.cat(segs);
    }
}
