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

    private final ItemID valueType;

    /** Canonical CBOR bytes of the payload value (primitive or structured), interpreted by valueType. */
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

    /* ------------------------ CBOR Encoding (shorthand + long form) ------------------------ */

    /**
     * Encode this Literal to CBOR using shorthand for common types.
     *
     * <ul>
     *   <li>TEXT → bare CBOR TextString</li>
     *   <li>INTEGER → bare CBOR Integer</li>
     *   <li>BOOLEAN → bare CBOR Boolean</li>
     *   <li>INSTANT → Tag(1, epoch_millis)</li>
     *   <li>Everything else → Tag(7, [typeIID_bytes, payload_cbor])</li>
     * </ul>
     */
    @Override
    public CBORObject toCborTree(Canonical.Scope scope) {
        CBORObject payloadCbor = payloadNode();

        // Shorthand: bare CBOR primitives
        if (TYPE_TEXT.equals(valueType)) return payloadCbor;
        if (TYPE_INTEGER.equals(valueType)) return payloadCbor;
        if (TYPE_BOOLEAN.equals(valueType)) return payloadCbor;

        // Shorthand: standard CBOR Tag 1 for instants
        if (TYPE_INSTANT.equals(valueType)) {
            return CBORObject.FromObjectAndTag(payloadCbor, 1);
        }

        // Long form: Tag 7 [typeIID, payload]
        CBORObject arr = CBORObject.NewArray();
        arr.Add(CBORObject.FromByteArray(valueType.encodeBinary()));
        arr.Add(payloadCbor);
        return CBORObject.FromObjectAndTag(arr, Canonical.CgTag.VALUE);
    }

    /**
     * Decode a Literal from CBOR, accepting both shorthand and long form.
     *
     * <ul>
     *   <li>Bare TextString → TYPE_TEXT</li>
     *   <li>Bare Integer → TYPE_INTEGER</li>
     *   <li>Bare Boolean → TYPE_BOOLEAN</li>
     *   <li>Tag(1, millis) → TYPE_INSTANT</li>
     *   <li>Tag(7, [typeIID, payload]) → custom type</li>
     *   <li>Array [typeIID_bytes, payload_bytes] → legacy long form (no tag)</li>
     * </ul>
     */
    @dev.everydaythings.graph.item.Factory
    public static Literal fromCborTree(CBORObject node) {
        if (node == null || node.isNull()) return null;

        // Tag 1: Instant (epoch millis)
        if (node.HasMostOuterTag(1)) {
            CBORObject inner = node.UntagOne();
            return ofCbor(TYPE_INSTANT, inner);
        }

        // Tag 7: Explicit typed value [typeIID, payload]
        if (node.HasMostOuterTag(Canonical.CgTag.VALUE)) {
            CBORObject inner = node.UntagOne();
            if (inner.getType() == CBORType.Array && inner.size() == 2) {
                ItemID typeId = new ItemID(inner.get(0).GetByteString());
                CBORObject payloadCbor = inner.get(1);
                return ofCbor(typeId, payloadCbor);
            }
        }

        // Bare primitives (shorthand)
        return switch (node.getType()) {
            case TextString -> ofCbor(TYPE_TEXT, node);
            case Integer -> ofCbor(TYPE_INTEGER, node);
            case Boolean -> ofCbor(TYPE_BOOLEAN, node);
            // Legacy: bare array [typeIID_bytes, payload_bytes]
            case Array -> {
                if (node.size() == 2 && node.get(0).getType() == CBORType.ByteString) {
                    ItemID typeId = new ItemID(node.get(0).GetByteString());
                    CBORObject payloadCbor;
                    if (node.get(1).getType() == CBORType.ByteString) {
                        payloadCbor = CBORObject.DecodeFromBytes(node.get(1).GetByteString());
                    } else {
                        payloadCbor = node.get(1);
                    }
                    yield ofCbor(typeId, payloadCbor);
                }
                // Fall back to default Canonical decoding
                yield Canonical.fromCborTree(node, Literal.class, Canonical.Scope.RECORD);
            }
            default -> throw new IllegalArgumentException("Cannot decode Literal from CBOR: " + node.getType());
        };
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

    /** Well-known type for opaque CBOR payloads (structured config, policy, etc.). */
    public static final ItemID TYPE_CBOR = ItemID.fromString("cg.value:cbor");

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
     * Create a Literal from any Value that declares its type via @Implements.
     *
     * <p>This enables generic conversion from annotated Value instances to Literals
     * without needing type-specific factory methods like ofText(), ofInteger(), etc.
     *
     * <p>Example:
     * <pre>{@code
     * @Implements("cg.value:endpoint")
     * public final class Endpoint implements Value { ... }
     *
     * Endpoint ep = Endpoint.cg(host, 8080);
     * Literal lit = Literal.of(ep);  // Type discovered from annotation
     * }</pre>
     *
     * @param value The Value (must have @Implements annotation)
     * @return A Literal with the discovered type and encoded payload
     * @throws IllegalArgumentException if the value's class lacks @Implements
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
     * declared via @Implements, or when the class lacks the annotation.
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
     * Discover the value type ID for a Value class via @Implements annotation.
     */
    private static ItemID discoverValueType(Class<?> clazz) {
        Implements impl = clazz.getAnnotation(Implements.class);
        if (impl != null) {
            return ItemID.fromString(impl.value());
        }
        throw new IllegalArgumentException(
                "Class " + clazz.getName() + " needs @Implements annotation to use Literal.of().");
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
        // Instant — format as human-readable date/time
        if (TYPE_INSTANT.equals(valueType)) {
            try {
                Instant instant = asInstantMillis();
                return java.time.ZonedDateTime.ofInstant(instant, java.time.ZoneId.systemDefault())
                        .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd h:mm:ss a"));
            } catch (Exception e) {
                // fall through to generic
            }
        }

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
