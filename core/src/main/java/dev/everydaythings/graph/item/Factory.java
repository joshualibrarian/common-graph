package dev.everydaythings.graph.item;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a static factory method for UI-driven creation discovery.
 *
 * <p>When the UI needs to create an instance of a type, it scans for
 * methods annotated with @Factory and presents them as options to the user.
 *
 * <p>Usage:
 * <pre>{@code
 * @Factory(label = "In-Memory", glyph = "⚡",
 *          doc = "Fast, zero dependencies, no persistence")
 * public static Library memory() { ... }
 *
 * @Factory(label = "Persistent (RocksDB)", glyph = "💾",
 *          primary = true, doc = "Production-grade persistent storage")
 * public static Library file(@Param(label = "Directory") Path path) { ... }
 * }</pre>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Factory {
    /** Human-readable label. If empty, derives from method name. */
    String label() default "";

    /** Description/tooltip text. */
    String doc() default "";

    /** Glyph (emoji/icon) for this factory option. */
    String glyph() default "";

    /** Whether this is the primary/default factory option. */
    boolean primary() default false;

    /** Order in the factory selection UI (lower = earlier). */
    int order() default 50;
}
