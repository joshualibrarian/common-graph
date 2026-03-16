package dev.everydaythings.graph.item;

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
 * <p>The canonical key is defined ONCE, in the inner seed class's {@code KEY}
 * constant. This annotation references it:
 * <pre>{@code
 * @Implements(ChessGame.Chess.KEY)
 * @Type(glyph = "♟️", color = 0x8B4513)
 * public class ChessGame extends Item {
 *     public static class Chess {
 *         public static final String KEY = "cg.sememe:chess";
 *         @Seed public static final Sememe SEED = new Sememe(KEY)
 *                 .gloss("en", "the game of chess");
 *     }
 * }
 * }</pre>
 *
 * @see Type for display metadata (glyph, color, shape)
 */
@Inherited
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface Implements {
    /** The canonical key for the concept this class implements. */
    String value();
}
