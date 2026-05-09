package dev.everydaythings.graph;

import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares that a class implements a concept identified by its canonical key.
 *
 * <p>The value is the concept's canonical key string (e.g., "cg.sememe:chess").
 * This links the Java class to its concept definition (a Sememe with that key).
 *
 * <p>Display metadata (glyph, color) lives on the Sememe seed via
 * {@code .glyph()} and {@code .color()}.
 *
 * <pre>{@code
 * @Implements(ChessItem.Chess.KEY)
 * public class ChessItem extends Item {
 *     public static class Chess {
 *         public static final String KEY = "cg.sememe:chess";
 *         @Seed public static final Sememe SEED = new Sememe(KEY)
 *                 .glyph("♟️").color(0x8B4513)
 *                 .gloss("en", "the game of chess");
 *     }
 * }
 * }</pre>
 */
@Inherited
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface Implements {
    /** The canonical key for the concept this class implements. */
    String value();
}
