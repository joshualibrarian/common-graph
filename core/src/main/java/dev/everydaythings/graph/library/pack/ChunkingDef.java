package dev.everydaythings.graph.library.pack;

import dev.everydaythings.graph.Canonical;

/**
 * Type-level policy for turning large blobs into chunks.
 *
 * This is a schema object, not an algorithm implementation. The algorithm key is
 * resolved by the runtime.
 */
@Canonical.Canonization(classType = Canonical.ClassCollectionType.ARRAY)
public final class ChunkingDef implements Canonical {

    public enum Algo {
        /** Fixed-size chunks (simple, but can have poor dedup on shifting content). */
        FIXED,
        /** Content-defined chunking (CDC) using FastCDC-style rolling hash. */
        FAST_CDC
    }

    @Canon(order = 0)
    private int v = 1;

    @Canon(order = 1)
    private Algo algo = Algo.FAST_CDC;

    /** Target/average chunk size in bytes (e.g., 1<<20). */
    @Canon(order = 2)
    private int targetBytes = 1 << 20;

    /** Minimum chunk size in bytes (CDC only). */
    @Canon(order = 3)
    private int minBytes = 1 << 16;

    /** Maximum chunk size in bytes (CDC only). */
    @Canon(order = 4)
    private int maxBytes = 1 << 22;

    public ChunkingDef() {}

    public ChunkingDef(int v, Algo algo, int targetBytes, int minBytes, int maxBytes) {
        this.v = v;
        this.algo = algo;
        this.targetBytes = targetBytes;
        this.minBytes = minBytes;
        this.maxBytes = maxBytes;
    }
}
