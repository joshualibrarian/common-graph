package dev.everydaythings.graph.value;

import dev.everydaythings.graph.Canonical;
import dev.everydaythings.graph.item.Link;
import dev.everydaythings.graph.item.Literal;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marker interface for values that can be relation literals.
 *
 * <p>A Value is a typed piece of data that can appear as the object
 * of a relation. Values have three representations:
 * <ul>
 *   <li><b>Binary CBOR</b> - canonical bytes for hashing, storage, wire protocol</li>
 *   <li><b>Text CBOR</b> - JSON-like for debugging/logs</li>
 *   <li><b>Token</b> - human-friendly string for UI/CLI/config</li>
 * </ul>
 *
 * <p>Value classes declare their type via {@code @Value.Type}:
 * <pre>{@code
 * @Type("cg.value:endpoint")
 * public final class Endpoint implements Value {
 *     // ...
 *     @Override
 *     public String token() {
 *         return protocol + "://" + host.token() + ":" + port;
 *     }
 * }
 * }</pre>
 *
 * <p>The type ID references a {@link ValueType} seed item that defines
 * the value's semantics, validation rules, and behavior.
 *
 * @see ValueType
 * @see Numeric
 */
public interface Value extends Canonical {

    // ==================================================================================
    // Display Methods
    // ==================================================================================

    /**
     * Values don't inherently have a stable link.
     * The tree wraps them with context when displayed.
     */
    default Link link() {
        return null;
    }

    default String displayToken() {
        return token();
    }

    default boolean isExpandable() {
        return false; // Values are typically leaves
    }

    default String colorCategory() {
        return "value";
    }

    /**
     * Emoji/icon for display.
     */
    default String emoji() {
        return "💎"; // Default value glyph
    }

    /**
     * Human-friendly token representation.
     *
     * <p>Override this to provide a domain-specific format.
     * Examples:
     * <ul>
     *   <li>Endpoint: {@code cg://192.168.1.1:8080}</li>
     *   <li>Quantity: {@code 5.2 m}</li>
     *   <li>Rational: {@code 3/4}</li>
     *   <li>IpAddress: {@code 192.168.1.1} or {@code ::1}</li>
     * </ul>
     *
     * <p>Default falls back to JSON-like text CBOR representation.
     *
     * @return human-readable token string
     */
    default String token() {
        return encodeText(Scope.RECORD);
    }

    /**
     * Declares the value type ID for a Value class.
     *
     * <p>The type ID should correspond to a {@link ValueType} seed item
     * (e.g., "cg.value:endpoint", "cg.value:quantity").
     *
     * <p>This enables generic conversion from any annotated Value to a
     * {@link Literal} via
     * {@code Literal.of(Value)}.
     */
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.TYPE)
    @interface Type {
        /** The value type ID (e.g., "cg.value:endpoint") */
        String value();

        /** The default glyph (emoji/icon) for this value type. Defaults to 💎 for values. */
        String glyph() default "💎";

        /** RGB color as hex int. Defaults to value rose/magenta. */
        int color() default 0xB4648C;

        /** Shape kind: "sphere" (items), "cube" (components), "disc" (values). */
        String shape() default "disc";
    }
}
