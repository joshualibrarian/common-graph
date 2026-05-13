package dev.everydaythings.graph.item;

import dev.everydaythings.graph.encoding.Canonical;

import com.upokecenter.cbor.CBOREncodeOptions;
import com.upokecenter.cbor.CBORObject;
import com.upokecenter.cbor.CBORType;
import dev.everydaythings.graph.identity.MultiKey;
import dev.everydaythings.graph.datum.BindingTarget;
import dev.everydaythings.graph.item.id.ItemID;
import dev.everydaythings.graph.library_old.LibraryOld;
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
    /**
     * Encode this Literal to CBOR. Only primitive-typed Literals are encodable
     * after the cleanup; semantic types live as binding qualifiers, not as
     * Literal valueTypes.
     *
     * <ul>
     *   <li>{@link #TYPE_TEXT}    → bare CBOR TextString</li>
     *   <li>{@link #TYPE_INTEGER} → bare CBOR Integer</li>
     *   <li>{@link #TYPE_BOOLEAN} → bare CBOR Boolean</li>
     *   <li>{@link #TYPE_BYTES}   → bare CBOR ByteString</li>
     *   <li>{@link #TYPE_INSTANT} → CBOR Tag 1 wrapping epoch-millis integer
     *       (Tag 1 is CBOR's standard tag for epoch time; it isn't a CG-specific
     *       wrapper, so it stays in the encoding.)</li>
     * </ul>
     */
    @Override
    public CBORObject toCborTree(Canonical.Scope scope) {
        CBORObject payloadCbor = payloadNode();
        if (TYPE_TEXT.equals(valueType)) return payloadCbor;
        if (TYPE_INTEGER.equals(valueType)) return payloadCbor;
        if (TYPE_BOOLEAN.equals(valueType)) return payloadCbor;
        if (TYPE_BYTES.equals(valueType)) return payloadCbor;
        if (TYPE_INSTANT.equals(valueType)) {
            return CBORObject.FromObjectAndTag(payloadCbor, 1);
        }
        throw new IllegalStateException(
                "Literal valueType " + valueType + " is not a primitive encoding type; "
                        + "semantic types must move to binding qualifiers");
    }

    /**
     * Decode a Literal from CBOR. Recognized shapes:
     *
     * <ul>
     *   <li>Bare TextString → TYPE_TEXT</li>
     *   <li>Bare Integer    → TYPE_INTEGER</li>
     *   <li>Bare Boolean    → TYPE_BOOLEAN</li>
     *   <li>Bare ByteString → TYPE_BYTES</li>
     *   <li>Tag(1, millis)  → TYPE_INSTANT</li>
     * </ul>
     */
    @dev.everydaythings.graph.item.Factory
    public static Literal fromCborTree(CBORObject node) {
        if (node == null || node.isNull()) return null;
        if (node.HasMostOuterTag(1)) {
            return ofCbor(TYPE_INSTANT, node.UntagOne());
        }
        return switch (node.getType()) {
            case TextString -> ofCbor(TYPE_TEXT, node);
            case Integer    -> ofCbor(TYPE_INTEGER, node);
            case Boolean    -> ofCbor(TYPE_BOOLEAN, node);
            case ByteString -> ofCbor(TYPE_BYTES, node);
            default -> throw new IllegalArgumentException(
                    "Cannot decode Literal from CBOR: " + node.getType());
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

    /** Well-known type for raw byte strings (encoded as bare CBOR ByteString). */
    public static final ItemID TYPE_BYTES = ItemID.fromString("cg.value:bytes");

    // Removed: TYPE_CBOR. Opaque structured-CBOR payloads now encode as plain
    // byte strings (TYPE_BYTES, bare CBOR ByteString shorthand). Consumers that
    // need to re-decode the bytes as CBOR can do so explicitly via
    // CBORObject.DecodeFromBytes(lit.asBytes()).

    /* ------------------------ Removed semantic types ------------------------ */
    // Semantic-narrowing types (JAVA_CLASS, MULTIKEY) used to live here as
    // Literal.TYPE_* constants and conflated encoding with semantic meaning. They
    // now live as binding-qualifier sememes — see CoreVocabulary.JavaClass and
    // CoreVocabulary.Multikey. The target literal is just the underlying
    // encoding-primitive (text or bytes), and the qualifier carries the meaning.

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

    /**
     * Create a literal carrying a fully-qualified Java class name as plain text.
     *
     * <p>Producing the FQCN as text is the encoding; the "this text is a Java class
     * name" semantic claim belongs on the surrounding binding's qualifiers (see
     * {@code CoreVocabulary.JavaClass}). The factory is kept as a convenience for
     * code that's building such bindings; the legacy {@code TYPE_JAVA_CLASS}
     * valueType is no longer used.
     */
    public static Literal ofJavaClass(String className) {
        return ofText(className);
    }

    /** Create a literal carrying a Java class's fully-qualified name as plain text. */
    public static Literal ofJavaClass(Class<?> clazz) {
        return ofText(clazz.getName());
    }

    /**
     * Create a literal carrying a multikey-encoded public key as plain bytes.
     *
     * <p>Produces a bytes-shaped Literal — the encoding is just bytes; "these
     * bytes are a multikey" is the semantic claim on the surrounding binding's
     * qualifiers (see {@code CoreVocabulary.Multikey}). The factory stays as a
     * convenience.
     */
    public static Literal ofMultiKey(MultiKey key) {
        Objects.requireNonNull(key, "key");
        return ofBytes(key.encoded());
    }

    /**
     * Create a literal carrying raw bytes — encoded as bare CBOR ByteString.
     * Use a binding qualifier (e.g. {@code CoreVocabulary.Multikey}) to declare
     * what the bytes mean.
     */
    public static Literal ofBytes(byte[] raw) {
        Objects.requireNonNull(raw, "raw");
        return ofCbor(TYPE_BYTES, CBORObject.FromByteArray(raw));
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

    // Removed: ofIp(IpAddress), ofQuantity(Quantity), of(ItemID, Value).
    // Semantic Value types (IpAddress, Quantity, Endpoint, etc.) no longer
    // carry their type in the Literal's valueType; the binding's qualifiers
    // carry the semantic narrowing, and the target encodes as a plain CBOR
    // primitive (bytes/integer/text/etc.).

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
     * Get the text payload as a fully-qualified Java class name.
     *
     * <p>Callers should already know — via the binding's qualifiers — that this
     * literal carries a class name; this method just exposes the text. Throws if
     * the payload isn't a text literal.
     */
    public String asJavaClassName() {
        return asText();
    }

    /**
     * Load the Java class named by this literal's text payload.
     *
     * @return The loaded class
     * @throws IllegalStateException if the class isn't on the classpath, or the
     *         payload isn't text
     */
    public Class<?> asJavaClass() {
        String className = asText();
        try {
            return Class.forName(className, false, Thread.currentThread().getContextClassLoader());
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException("Class not found: " + className, e);
        }
    }

    /**
     * Decode the bytes payload as a {@link MultiKey}. Callers should already know
     * — via the binding's qualifiers ({@code CoreVocabulary.Multikey}) — that
     * this literal carries a multikey; this method just decodes the bytes.
     */
    public MultiKey asMultiKey() {
        return MultiKey.decode(asBytes());
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
    public String format(LibraryOld library) {
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
     * use {@link #format(LibraryOld)} with a library context.
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
