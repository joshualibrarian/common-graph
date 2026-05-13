package dev.everydaythings.graph.value;

import dev.everydaythings.graph.encoding.Canonical;
import dev.everydaythings.graph.item.id.Ref;

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
 * <p>Value classes declare their type via {@code @Implements}:
 * <pre>{@code
 * @Implements("cg.value:endpoint")
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
     * Values don't inherently have a stable ref.
     * The tree wraps them with context when displayed.
     */
    default Ref ref() {
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

}
