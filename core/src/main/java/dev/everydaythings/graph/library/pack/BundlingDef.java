package dev.everydaythings.graph.library.pack;

import dev.everydaythings.graph.Canonical;

/**
 * Type-level policy for bundling many tiny blocks into a larger container.
 *
 * Motivations:
 * - amortize per-block overhead on disk
 * - speed up fetches where many small blocks are needed
 */
@Canonical.Canonization(classType = Canonical.ClassCollectionType.ARRAY)
public final class BundlingDef implements Canonical {

    @Canon(order = 0)
    private int v = 1;

    /** Target bundle size in bytes. */
    @Canon(order = 1)
    private int targetBytes = 1 << 20;

    /** Hard cap on number of entries per bundle. */
    @Canon(order = 2)
    private int maxEntries = 4096;

    public BundlingDef() {}

    public BundlingDef(int v, int targetBytes, int maxEntries) {
        this.v = v;
        this.targetBytes = targetBytes;
        this.maxEntries = maxEntries;
    }
}
