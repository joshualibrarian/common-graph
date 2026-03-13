package dev.everydaythings.graph.item;

import com.upokecenter.cbor.CBOREncodeOptions;
import com.upokecenter.cbor.CBORObject;
import com.upokecenter.cbor.CBORType;
import dev.everydaythings.graph.Canonical;
import dev.everydaythings.graph.frame.BindingTarget;
import dev.everydaythings.graph.item.id.ItemID;
import dev.everydaythings.graph.library.Library;
import dev.everydaythings.graph.value.IpAddress;
import dev.everydaythings.graph.value.Quantity;
import dev.everydaythings.graph.value.Value;
import lombok.Getter;

import java.time.Instant;
import java.util.Objects;

/**
 * A relation "object literal" that is fully type-driven:
 *
 * <pre>Literal := (valueType, payloadCbor)</pre>
 *
 * <ul>
 *   <li>valueType is an ItemID referring to a ValueType item</li>
 *   <li>payloadCbor is canonical CBOR bytes for the payload, interpreted according to valueType</li>
 * </ul>
 *
 * <p>This avoids bespoke type codes / special-case unions in the relation layer.
 */
@Getter
public final class Literal implements BindingTarget {

    @Canon(order = 1)
    private final ItemID valueType;

    /** Canonical CBOR bytes of the payload value (primitive or structured), interpreted by valueType. */
    @Canon(order = 2)
    private final byte[] payload;

    /**
     * No-arg constructor for Canonical decoding.
     * Fields are populated via reflection.
     */
    private Literal() {
        this.valueType = null;
        this.payload = null;
    }

    public Literal(ItemID valueType, byte[] payload) {
        this.valueType = Objects.requireNonNull(valueType, "valueType");
        this.payload = Objects.requireNonNull(payload, "payload").clone();

        // sanity: payload must decode as CBOR (catch corruption early)
        try {
            CBORObject.DecodeFromBytes(this.payload);
        } catch (Exception e) {
            throw new IllegalArgumentException("payload is not valid CBOR", e);
        }
    }

    /* ------------------------ Well-known value type IDs ------------------------ */

    /** Well-known type for text/string values. */
    public static final ItemID TYPE_TEXT = ItemID.fromString("cg.value:text");

    /** Well-known type for boolean values. */
    public static final ItemID TYPE_BOOLEAN = ItemID.fromString("cg.value:boolean");

    /** Well-known type for integer values. */
    public static final ItemID TYPE_INTEGER = ItemID.fromString("cg.value:integer");

    // Note: CG-CBOR forbids IEEE 754 floats. Use Decimal or Rational instead.

    /** Well-known type for instant/timestamp values (epoch millis). */
    public static final ItemID TYPE_INSTANT = ItemID.fromString("cg.value:instant");

    /* ------------------------ Well-known address type IDs ------------------------ */

    /** Well-known type for Java class addresses (fully qualified class names). */
    public static final ItemID TYPE_JAVA_CLASS = ItemID.fromString("cg.address:java-class");

    /* ------------------------ Convenience factories (default types) ------------------------ */

    /** Create a text literal with default text type. */
    public static Literal ofText(String text) {
        return ofText(TYPE_TEXT, text);
    }

    /** Create a boolean literal with default boolean type. */
    public static Literal ofBoolean(boolean value) {
        return ofBoolean(TYPE_BOOLEAN, value);
    }

    /** Create an integer literal with default integer type. */
    public static Literal ofInteger(long value) {
        return ofInteger(TYPE_INTEGER, value);
    }

    // Note: No ofNumber(double) - CG-CBOR forbids IEEE 754 floats. Use Decimal.

    /** Create an instant literal with default instant type. */
    public static Literal ofInstant(Instant instant) {
        return ofInstantMillis(TYPE_INSTANT, instant);
    }

    /** Create a Java class address literal. */
    public static Literal ofJavaClass(String className) {
        return ofText(TYPE_JAVA_CLASS, className);
    }

    /** Create a Java class address literal from a Class object. */
    public static Literal ofJavaClass(Class<?> clazz) {
        return ofText(TYPE_JAVA_CLASS, clazz.getName());
    }

    /* ------------------------ Factories (payload encoders) ------------------------ */

    public static Literal ofCbor(ItemID valueType, CBORObject payloadNode) {
        Objects.requireNonNull(payloadNode, "payloadNode");
        byte[] bytes = payloadNode.EncodeToBytes(CBOREncodeOptions.DefaultCtap2Canonical);
        return new Literal(valueType, bytes);
    }

    public static Literal ofText(ItemID valueType, String text) {
        return ofCbor(valueType, CBORObject.FromString(Objects.requireNonNull(text, "text")));
    }

    public static Literal ofBoolean(ItemID valueType, boolean value) {
        return ofCbor(valueType, value ? CBORObject.True : CBORObject.False);
    }

    public static Literal ofInteger(ItemID valueType, long value) {
        return ofCbor(valueType, CBORObject.FromInt64(value));
    }

    public static Literal ofInstantMillis(ItemID valueType, Instant instant) {
        Objects.requireNonNull(instant, "instant");
        return ofCbor(valueType, CBORObject.FromInt64(instant.toEpochMilli()));
    }

    public static Literal ofIp(ItemID valueType, IpAddress ip) {
        Objects.requireNonNull(ip, "ip");
        return ofCbor(valueType, CBORObject.FromByteArray(ip.bytes()));
    }

    public static Literal ofQuantity(ItemID valueType, Quantity q) {
        Objects.requireNonNull(q, "q");
        byte[] bytes = q.encodeBinary(Canonical.Scope.BODY);
        return new Literal(valueType, bytes);
    }

    /* ------------------------ Generic Value factory ------------------------ */

    /**
     * Create a Literal from any Value that declares its type via @Value.Type.
     *
     * <p>This enables generic conversion from annotated Value instances to Literals
     * without needing type-specific factory methods like ofText(), ofInteger(), etc.
     *
     * <p>Example:
     * <pre>{@code
     * @Value.Type("cg.value:endpoint")
     * public final class Endpoint implements Value { ... }
     *
     * Endpoint ep = Endpoint.cg(host, 8080);
     * Literal lit = Literal.of(ep);  // Type discovered from annotation
     * }</pre>
     *
     * @param value The Value (must have @Value.Type annotation)
     * @return A Literal with the discovered type and encoded payload
     * @throws IllegalArgumentException if the value's class lacks @Value.Type
     */
    public static Literal of(Value value) {
        Objects.requireNonNull(value, "value");
        ItemID type = discoverValueType(value.getClass());
        byte[] payload = value.encodeBinary(Canonical.Scope.RECORD);
        return new Literal(type, payload);
    }

    /**
     * Create a Literal from a Value with an explicit type override.
     *
     * <p>Use this when you need to specify a different type than the default
     * declared via @Value.Type, or when the class lacks the annotation.
     *
     * @param valueType The value type ID to use
     * @param value The Value to encode
     * @return A Literal with the specified type and encoded payload
     */
    public static Literal of(ItemID valueType, Value value) {
        Objects.requireNonNull(valueType, "valueType");
        Objects.requireNonNull(value, "value");
        byte[] payload = value.encodeBinary(Canonical.Scope.RECORD);
        return new Literal(valueType, payload);
    }

    /**
     * Discover the value type ID for a Value class via @Value.Type annotation.
     */
    private static ItemID discoverValueType(Class<?> clazz) {
        Value.Type ann = clazz.getAnnotation(Value.Type.class);
        if (ann == null) {
            throw new IllegalArgumentException(
                    "Class " + clazz.getName() + " needs @Value.Type annotation to use Literal.of(). " +
                    "Either add the annotation or use Literal.of(ItemID, Value).");
        }
        return ItemID.fromString(ann.value());
    }

    /* ------------------------ Generic decoder ------------------------ */

    /**
     * Decode the payload as a specific Value type.
     *
     * <p>Example:
     * <pre>{@code
     * Literal lit = ...;
     * Endpoint ep = lit.as(Endpoint.class);
     * }</pre>
     *
     * @param type The Value class to decode to
     * @return The decoded value
     */
    public <T extends Value> T as(Class<T> type) {
        return Canonical.decodeBinary(payload, type, Canonical.Scope.RECORD);
    }

    /* ------------------------ Payload decoders (helpers) ------------------------ */

    public CBORObject payloadNode() {
        return CBORObject.DecodeFromBytes(payload);
    }

    public String asText() {
        CBORObject n = payloadNode();
        if (n.getType() != CBORType.TextString) throw new IllegalStateException("payload is not text");
        return n.AsString();
    }

    public boolean asBoolean() {
        CBORObject n = payloadNode();
        if (n.getType() != CBORType.Boolean) throw new IllegalStateException("payload is not boolean");
        return n.AsBoolean();
    }

    public long asInteger() {
        CBORObject n = payloadNode();
        if (n.getType() != CBORType.Integer) throw new IllegalStateException("payload is not integer");
        return n.AsInt64Value();
    }

    public Instant asInstantMillis() {
        return Instant.ofEpochMilli(asInteger());
    }

    public byte[] asBytes() {
        CBORObject n = payloadNode();
        if (n.getType() != CBORType.ByteString) throw new IllegalStateException("payload is not bytes");
        return n.GetByteString();
    }

    /**
     * Get the Java class name if this is a Java class address literal.
     *
     * @throws IllegalStateException if not a Java class literal or payload is not text
     */
    public String asJavaClassName() {
        if (!TYPE_JAVA_CLASS.equals(valueType)) {
            throw new IllegalStateException("Not a Java class literal: " + valueType);
        }
        return asText();
    }

    /**
     * Load the Java class if this is a Java class address literal.
     *
     * @return The loaded class
     * @throws IllegalStateException if not a Java class literal or class not found
     */
    public Class<?> asJavaClass() {
        String className = asJavaClassName();
        try {
            return Class.forName(className, false, Thread.currentThread().getContextClassLoader());
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException("Class not found: " + className, e);
        }
    }

    /**
     * Format this literal using the Library's type system for rich rendering.
     *
     * <p>For structured Value types (maps, arrays), attempts to decode using
     * the registered Value class for proper formatting (e.g., Endpoint → "cg://ip:port").
     *
     * @param library The library for type resolution (may be null for basic rendering)
     * @return Human-readable representation
     */
    public String format(Library library) {
        try {
            // Try to decode as the registered Value type first
            if (library != null && valueType != null) {
                // Skip well-known primitive types (no class lookup needed)
                if (!TYPE_TEXT.equals(valueType) && !TYPE_BOOLEAN.equals(valueType) &&
                    !TYPE_INTEGER.equals(valueType) && !TYPE_INSTANT.equals(valueType)) {

                    var valueClass = library.findValueImplementation(valueType);
                    if (valueClass.isPresent()) {
                        try {
                            Value decoded = Canonical.decodeBinary(payload, valueClass.get(), Canonical.Scope.RECORD);
                            return decoded.toString();
                        } catch (Exception e) {
                            // Fall through to generic rendering
                        }
                    }
                }
            }

            // Generic CBOR rendering
            return formatCbor();
        } catch (Exception e) {
            // Fallback if decoding fails
            return "Literal(" + (payload != null ? payload.length : 0) + " bytes)";
        }
    }

    /**
     * Human-readable string representation showing the decoded payload.
     *
     * <p>Uses generic CBOR rendering only. For rich type-aware formatting,
     * use {@link #format(Library)} with a library context.
     */
    @Override
    public String toString() {
        try {
            return formatCbor();
        } catch (Exception e) {
            return "Literal(" + (payload != null ? payload.length : 0) + " bytes)";
        }
    }

    /**
     * Format the payload as generic CBOR (without type system lookup).
     */
    private String formatCbor() {
        CBORObject node = payloadNode();
        return switch (node.getType()) {
            case TextString -> "\"" + node.AsString() + "\"";
            case Integer -> String.valueOf(node.AsInt64Value());
            case Boolean -> String.valueOf(node.AsBoolean());
            case ByteString -> "[" + node.GetByteString().length + " bytes]";
            case Array -> "[array:" + node.size() + "]";
            case Map -> "{map:" + node.size() + "}";
            default -> node.ToJSONString();
        };
    }
}
