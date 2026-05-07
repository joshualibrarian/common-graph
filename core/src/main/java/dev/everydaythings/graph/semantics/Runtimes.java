package dev.everydaythings.graph.semantics;

import dev.everydaythings.graph.item.Seed;
import dev.everydaythings.graph.item.id.ItemID;

/**
 * Runtime/language sememes — used as qualifiers on AGENT bindings of IMPLEMENTS
 * frames to indicate which runtime is needed to execute an implementation.
 *
 * <p>Distinct from natural-language sememes (which use the {@code cg.lang:} prefix
 * and ISO 639-3 codes). Runtime sememes use {@code cg.runtime:} for clarity.
 *
 * <p>For now, only Java is defined. Clojure, GraalVM-hosted, WASM, native FFI, etc.
 * get added as polyglot support comes online.
 */
public final class Runtimes {

    private Runtimes() {}

    /** The Java Virtual Machine runtime. */
    @Seed(key = Java.KEY)
    public static final class Java {
        public static final String KEY = "cg.runtime:java";
        public static final ItemID IID = ItemID.fromString(KEY);
        private Java() {}
    }
}
