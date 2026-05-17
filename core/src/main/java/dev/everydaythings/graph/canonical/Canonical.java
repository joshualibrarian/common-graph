package dev.everydaythings.graph.canonical;

import com.upokecenter.cbor.CBOREncodeOptions;
import com.upokecenter.cbor.CBORObject;
import com.upokecenter.cbor.CBORType;
import com.upokecenter.numbers.EInteger;
import dev.everydaythings.graph.id.ItemRef;

import java.lang.reflect.*;
import java.math.BigInteger;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Canonical encoding/decoding for CG-CBOR format.
 *
 * <p>CG-CBOR is Common Graph's deterministic CBOR profile. Key features:
 * <ul>
 *   <li>BODY/RECORD scope split via {@code @Canon(isBody, isRecord)}</li>
 *   <li>ARRAY layout (ordered by name or {@code @Canon.order}) or MAP layout</li>
 *   <li>Custom tags for type discrimination (see {@link CgTag})</li>
 *   <li>No IEEE 754 floats - use Decimal or Rational</li>
 * </ul>
 *
 * <p>Types control their own encoding via {@link #toCborTree(Scope)}. Override
 * for primitives (IDs, algorithms) or use the default field-based encoding.
 *
 * <p><b>Note:</b> Canonical is ONLY for serialization. For UI presentation,
 * use {@code @Display} annotations and display methods (displayToken, emoji, etc.).
 * Keep these concerns separate: {@code @Canon} = serialize, {@code @Display} = render.
 */
public interface Canonical {

    Layout.Kind DEFAULT_LAYOUT_KIND = Layout.Kind.ARRAY;

    // ClassCollectionType, Scope, and CgTag have all moved to top-level types in
    // this package — see Layout.Kind, Scope, and CgTag respectively.

    /**
     * CG-CBOR type mappings from CBOR representations to CG ValueTypes.
     *
     * <p>This enum defines the "shorthand conventions" — how bare CBOR
     * primitives and standard CBOR tags map to CG value types. When a value
     * can be represented as one of these forms, no explicit wrapper is needed.
     *
     * <p>Use {@link #infer(CBORObject)} to determine the CG type from a CBOR node.
     */
    enum CborType {
        /** CBOR text string → cg.value:text */
        TEXT(ItemRef.fromString("cg.value:text"), CBORType.TextString, -1),

        /** CBOR integer → cg.value:integer */
        INTEGER(ItemRef.fromString("cg.value:integer"), CBORType.Integer, -1),

        /** CBOR boolean → cg.value:boolean */
        BOOLEAN(ItemRef.fromString("cg.value:boolean"), CBORType.Boolean, -1),

        /** CBOR byte string → cg.value:bytes */
        BYTES(ItemRef.fromString("cg.value:bytes"), CBORType.ByteString, -1),

        /** CBOR Tag 4 [mantissa, exponent] → cg.value:decimal */
        DECIMAL(ItemRef.fromString("cg.value:decimal"), null, 4),

        /** CBOR Tag 1 (epoch seconds/millis) → cg.value:instant */
        INSTANT(ItemRef.fromString("cg.value:instant"), null, 1),

        /** [numerator, denominator] array → cg.value:rational */
        RATIONAL(ItemRef.fromString("cg.value:rational"), CBORType.Array, -1),
        ;

        private final ItemRef valueTypeId;
        private final CBORType cborType;  // null if detected by tag
        private final int tag;            // -1 if detected by CBORType

        CborType(ItemRef valueTypeId, CBORType cborType, int tag) {
            this.valueTypeId = valueTypeId;
            this.cborType = cborType;
            this.tag = tag;
        }

        /** The ItemRef of the CG ValueType this CBOR form represents. */
        public ItemRef valueTypeId() {
            return valueTypeId;
        }

        /** The CBOR primitive type, or null if detected by tag. */
        public CBORType cborType() {
            return cborType;
        }

        /** The CBOR tag number, or -1 if detected by primitive type. */
        public int tag() {
            return tag;
        }

        /**
         * Infer the CG type from a CBOR node.
         *
         * @param node the CBOR node to inspect
         * @return the inferred CG type, or empty if explicit typing (Tag 7) is needed
         */
        public static Optional<CborType> infer(CBORObject node) {
            if (node == null || node.isNull()) return Optional.empty();

            // Check standard CBOR tags
            if (node.HasMostOuterTag(4)) return Optional.of(DECIMAL);
            if (node.HasMostOuterTag(1)) return Optional.of(INSTANT);

            // Check primitive types
            return switch (node.getType()) {
                case TextString -> Optional.of(TEXT);
                case Integer -> Optional.of(INTEGER);
                case Boolean -> Optional.of(BOOLEAN);
                case ByteString -> Optional.of(BYTES);
                case Array -> isRationalArray(node) ? Optional.of(RATIONAL) : Optional.empty();
                default -> Optional.empty();
            };
        }

        /**
         * Check if a ValueType can be encoded without explicit Tag 7 wrapper.
         *
         * @param type the ValueType to check
         * @return true if the type needs Tag 7 (CG-VALUE) wrapping
         */
        public static boolean needsExplicitType(ItemRef typeId) {
            for (CborType ct : values()) {
                if (ct.valueTypeId.equals(typeId)) return false;
            }
            return true;
        }

        /**
         * Find the CborType for a given value type ID.
         *
         * @param typeId the value type ItemRef to look up
         * @return the CborType, or empty if no shorthand exists
         */
        public static Optional<CborType> forValueType(ItemRef typeId) {
            for (CborType ct : values()) {
                if (ct.valueTypeId.equals(typeId)) return Optional.of(ct);
            }
            return Optional.empty();
        }

        /**
         * Check if a CBOR array represents a rational number [numerator, denominator].
         */
        private static boolean isRationalArray(CBORObject node) {
            if (node.size() != 2) return false;
            CBORObject num = node.get(0);
            CBORObject den = node.get(1);
            return num.getType() == CBORType.Integer && den.getType() == CBORType.Integer;
        }
    }

    // =========================================================================
    // Instance-level encoding hook
    // =========================================================================

    /**
     * Convert this object to a CBOR tree.
     *
     * <p>The default implementation reflects over {@code @Canon} fields to build
     * a CBOR array or map. Types that encode as primitives (integers, byte strings,
     * etc.) should override this method to return the appropriate CBORObject directly.
     *
     * <p>Examples:
     * <ul>
     *   <li>An ID type returns {@code CBORObject.FromByteArray(multihash.toBytes())}</li>
     *   <li>A small integer-shaped value returns {@code CBORObject.FromInt32(intValue())}</li>
     *   <li>A Decimal returns {@code CBORObject.FromCborObjectAndTag(array, 4)}</li>
     * </ul>
     *
     * @param scope BODY or RECORD scope for field filtering
     * @return the CBOR representation of this object
     */
    default CBORObject toCborTree(Scope scope) {
        return buildTreeFromFields(this, scope);
    }


    /* =============================== Hooks ============================== */

    /** Optional: customize the just-built CBOR (e.g., add 'crit' list). */
    default void beforeEncode(Scope scope, CBORObject root) { /* no-op */ }

    /** Optional: validate/normalize after fields are populated. */
    default void afterDecode(Scope scope) { /* no-op */ }

    /* ============================ Encode API ============================ */

    static List<Field> fields(Class<?> clazz, Scope scope) {
        List<Field> result = new ArrayList<>();

        for (Class<?> c = clazz; c != null; c = c.getSuperclass()) {
            for (Field f : c.getDeclaredFields()) {
                var annotation = f.getAnnotation(Order.class);
                if (annotation == null) continue;

                // Body/Record scope filtering used to live on @Order via isBody/isRecord;
                // dropped during cleanup. Body and Record are separate types now, so
                // every field marked @Order participates in both scopes by default.

                f.setAccessible(true);
                result.add(f);
            }
        }
        return result;
    }

    /**
     * Convert a Canonical object to CBOR by calling its instance method.
     * This allows types to override encoding behavior.
     */
    static CBORObject toCborTree(Canonical o, Scope scope) {
        return o.toCborTree(scope);
    }

    /**
     * Default field-based encoding: reflects over @Canon fields to build CBOR array/map.
     * Called by the default toCborTree() implementation.
     */
    static CBORObject buildTreeFromFields(Canonical o, Scope scope) {
        try {
            List<Field> fs = fields(o.getClass(), scope);

            Layout classAnnotation = o.getClass().getAnnotation(Layout.class);
            Layout.Kind type = (classAnnotation != null)
                    ? (classAnnotation.value() == Layout.Kind.MAP ? Layout.Kind.MAP : Layout.Kind.ARRAY) : DEFAULT_LAYOUT_KIND;

            if (type == Layout.Kind.MAP) {
                CBORObject map = CBORObject.NewMap();  // CTAP2 canonical options will sort keys
                for (Field f : fs) {
                    Object v = f.get(o);
                    if (isDefaultValue(v)) continue;  // omit null/default fields from MAP encoding
                    map.set(CBORObject.FromString(f.getName()), encodeValue(v, scope));
                }
                return map;
            }

            // ARRAY: order fields deterministically
            sortFieldsForEncoding(fs);
            CBORObject array = CBORObject.NewArray();
            for (Field f : fs) {
                Object v = f.get(o);
                array.Add(encodeValue(v, scope));
            }
            return array;

        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Sort fields for deterministic encoding.
     * Uses explicit @Canon.order if all fields have it, otherwise falls back to name order.
     */
    /**
     * Whether a value is a default that should be omitted from MAP encoding.
     * Null, zero primitives, false, empty collections are all default.
     */
    private static boolean isDefaultValue(Object v) {
        if (v == null) return true;
        if (v instanceof Number n && n.doubleValue() == 0.0) return true;
        if (v instanceof Boolean b && !b) return true;
        if (v instanceof Collection<?> c && c.isEmpty()) return true;
        if (v instanceof Map<?, ?> m && m.isEmpty()) return true;
        return false;
    }

    static void sortFieldsForEncoding(List<Field> fs) {
        boolean anyDefaultOrder = fs.stream().anyMatch(f -> f.getAnnotation(Order.class).value() < 0);
        if (anyDefaultOrder) {
            fs.sort(Comparator.comparing(Field::getName));
        } else {
            fs.sort(Comparator.comparingInt(f -> f.getAnnotation(Order.class).value()));
        }
    }

    /**
     * Encode arbitrary value into CBORObject with canonical rules.
     *
     * <p>For Canonical types, this delegates to {@link #toCborTree(Scope)} which
     * can be overridden by types that encode as primitives (IDs, algorithms, etc.).
     */
    static CBORObject encodeValue(Object v, Scope scope) {
        switch (v) {
            case null -> {
                return CBORObject.Null;
            }

            // @Encode-annotated leaves produce their own native wire form.
            // Check before the Canonical case so HashID-family types (which
            // are leaves but no longer implement Canonical) are caught here.
            case Object o when Leaves.isLeaf(o.getClass()) -> {
                return encodeLeaf(o);
            }

            // Canonical types control their own encoding via toCborTree().
            case Canonical c -> { return toCborTree(c, scope); }
            case byte[] b -> { return CBORObject.FromByteArray(b); }
            case CharSequence s -> { return CBORObject.FromString(s.toString()); }
            case BigInteger bi -> { return CBORObject.FromEInteger(EInteger.FromString(bi.toString())); }
            case Integer i -> { return CBORObject.FromInt32(i); }
            case Long l -> { return CBORObject.FromInt64(l); }
            case Number n -> { return CBORObject.FromInt64(n.longValue()); }
            case Boolean b -> { return b ? CBORObject.True : CBORObject.False; }
            case Enum<?> e -> { return CBORObject.FromString(e.name()); }
            case Instant ins -> { return CBORObject.FromInt64(ins.toEpochMilli()); }
            case Map<?, ?> m -> { return encodeMap(m, scope); }
            case Collection<?> col -> { return encodeCollection(col, scope); }
            default -> { }
        }

        if (v.getClass().isArray())         return encodeArray(v, scope);

        throw new IllegalArgumentException("Unsupported type for canonical encoding: " + v.getClass());
    }

    // =================== Encode Helpers ===================

    /**
     * Encode a leaf value (something carrying {@code @Encode} methods) to a
     * bare CBOR primitive. Prefers byte[] (canonical wire form) over String.
     */
    static CBORObject encodeLeaf(Object value) {
        Class<?> cls = value.getClass();
        if (Leaves.findEncode(cls, byte[].class) != null) {
            return CBORObject.FromByteArray(Leaves.encode(value, byte[].class));
        }
        if (Leaves.findEncode(cls, String.class) != null) {
            return CBORObject.FromString(Leaves.encode(value, String.class));
        }
        throw new IllegalArgumentException(
                "Class " + cls.getName() + " has no @Encode method for byte[] or String");
    }

    static CBORObject encodeArray(Object array, Scope scope) {
        int n = Array.getLength(array);
        CBORObject out = CBORObject.NewArray();
        for (int i = 0; i < n; i++) {
            out.Add(encodeValue(Array.get(array, i), scope));
        }
        return out;
    }

    static CBORObject encodeCollection(Collection<?> col, Scope scope) {
        CBORObject out = CBORObject.NewArray();
        for (Object elem : col) {
            out.Add(encodeValue(elem, scope));
        }
        return out;
    }

    static CBORObject encodeMap(Map<?, ?> m, Scope scope) {
        CBORObject map = CBORObject.NewMap();
        // CTAP2 canonical encoding will sort keys deterministically
        for (var e : m.entrySet()) {
            map.set(encodeValue(e.getKey(), scope), encodeValue(e.getValue(), scope));
        }
        return map;
    }

    /**
     * CG-CBOR encoding options: deterministic ordering without CTAP2's 4-level depth limit.
     *
     * <p>We get deterministic encoding via:
     * <ul>
     *   <li>Field ordering: @Canon(order=N) annotations sort fields</li>
     *   <li>Map key sorting: CBORObject.NewMap() with canonical options sorts keys</li>
     *   <li>No indefinite-length strings</li>
     *   <li>No duplicate keys</li>
     * </ul>
     *
     * <p>CTAP2 canonical CBOR (used for WebAuthn) has a 4-level nesting limit that's
     * too restrictive for our nested data structures.
     */
    CBOREncodeOptions CG_CBOR_OPTIONS = new CBOREncodeOptions("useIndefLengthStrings=false;allowduplicatekeys=false;float64=false");

    default byte[] encodeBinary(Scope scope) {
        CBORObject tree = toCborTree(this, scope);
        beforeEncode(scope, tree);
        return tree.EncodeToBytes(CG_CBOR_OPTIONS);
    }

    default String encodeText(Scope scope) {
        CBORObject tree = toCborTree(this, scope);
        beforeEncode(scope, tree);
        return tree.ToJSONString(); // dev/test output
    }

    /* ===================== DECODE ===================== */

    /**
     * Decode bytes to a Canonical type using RECORD scope (the default).
     */
    static <T extends Canonical> T decode(byte[] data, Class<T> type) {
        return decodeBinary(data, type, Scope.RECORD);
    }

    static <T extends Canonical> T decodeBinary(byte[] data, Class<T> type, Scope scope) {
        CBORObject node = CBORObject.DecodeFromBytes(data);
        return fromCborTree(node, type, scope);
    }

    @SuppressWarnings("unchecked")
    static <T extends Canonical> T fromCborTree(CBORObject node, Class<T> type, Scope scope) {
        if (node == null || node.isNull()) return null;

        // First, check for a custom fromCborTree(CBORObject) method
        Object result = tryFromCborTree(type, node);
        if (type.isInstance(result)) {
            return (T) result;
        }

        Layout classAnnotation = type.getAnnotation(Layout.class);
        Layout.Kind ctype = (classAnnotation != null)
                ? (classAnnotation.value() == Layout.Kind.MAP ? Layout.Kind.MAP : Layout.Kind.ARRAY) : DEFAULT_LAYOUT_KIND;

        T instance = construct(type);
        List<Field> fs = fields(type, scope);

        if (ctype == Layout.Kind.MAP) {
            decodeMapIntoInstance(node, instance, fs, scope);
        } else {
            decodeArrayIntoInstance(node, instance, fs, scope);
        }

        instance.afterDecode(scope);
        return instance;
    }

    private static <T> void decodeMapIntoInstance(CBORObject node, T instance, List<Field> fs, Scope scope) {
        if (node.getType() != CBORType.Map) {
            throw new IllegalArgumentException("Expected CBOR map for " + instance.getClass().getName());
        }
        Map<String, CBORObject> byName = new HashMap<>();
        for (CBORObject k : node.getKeys()) {
            byName.put(k.AsString(), node.get(k));
        }
        for (Field f : fs) {
            set(instance, f, decodeValueForField(f, byName.get(f.getName()), scope));
        }
    }

    private static <T> void decodeArrayIntoInstance(CBORObject node, T instance, List<Field> fs, Scope scope) {
        if (node.getType() != CBORType.Array) {
            throw new IllegalArgumentException("Expected CBOR array for " + instance.getClass().getName());
        }
        sortFieldsForEncoding(fs);  // same ordering as encode
        int provided = node.size();
        for (int i = 0; i < fs.size(); i++) {
            Field f = fs.get(i);
            CBORObject vNode = (i < provided) ? node.get(i) : CBORObject.Null;
            set(instance, f, decodeValueForField(f, vNode, scope));
        }
    }

    /**
     * Decode a CBOR node into a target class.
     *
     * <p>Dispatches to appropriate decoder based on type:
     * <ol>
     *   <li>Custom fromCborTree() for Canonical types with primitive encoding</li>
     *   <li>Legacy scalar decoders (decodeText, decodeBinary, decodeInteger)</li>
     *   <li>Sealed interface with discriminator</li>
     *   <li>Structured Canonical types</li>
     *   <li>Java primitives and common types</li>
     * </ol>
     */
    @SuppressWarnings({"unchecked"})
    static Object decodeIntoClass(Class<?> raw, CBORObject node, Scope scope) {
        // @Decode-annotated leaves: dispatch on CBOR primitive type.
        if (Leaves.isLeaf(raw) && isPrimitiveCbor(node)) {
            Object result = tryLeafDecode(raw, node);
            if (result != null) return result;
        }

        // Custom fromCborTree() for primitive-encoded Canonical types
        if (Canonical.class.isAssignableFrom(raw)) {
            Object result = tryFromCborTree(raw, node);
            if (result != null) return result;
        }

        // Legacy scalar decoding for Canonical types
        if (Canonical.class.isAssignableFrom(raw) && isPrimitiveCbor(node)) {
            Object result = tryScalarDecode(raw, node);
            if (result != null) return result;
        }

        // Sealed interface with discriminator
        if (raw.isSealed() && Canonical.class.isAssignableFrom(raw)) {
            return decodeSealedType(raw, node, scope);
        }

        // Structured Canonical type
        if (Canonical.class.isAssignableFrom(raw)) {
            return fromCborTree(node, (Class<? extends Canonical>) raw, scope);
        }

        // Java primitives and common types
        return decodePrimitive(raw, node);
    }

    /** Check if node is a primitive CBOR type (not array or map). */
    private static boolean isPrimitiveCbor(CBORObject node) {
        CBORType t = node.getType();
        return t != CBORType.Array && t != CBORType.Map;
    }

    /**
     * Try to find and invoke a @Factory-annotated decoder method.
     *
     * <p>Searches for @Factory methods taking CBORObject in the class hierarchy
     * (always) and interface hierarchy (only for primitive CBOR nodes).
     *
     * <p>The interface restriction prevents infinite recursion: interface methods
     * like Target.fromCborTree dispatch back to Canonical.fromCborTree for
     * structured types. Primitive CBOR nodes (text, bytes, int) are safe because
     * interface decoders for those don't recurse into Canonical.
     */
    static Object tryFromCborTree(Class<?> raw, CBORObject node) {
        // 1. Check class hierarchy (always safe)
        for (Class<?> c = raw; c != null && c != Object.class; c = c.getSuperclass()) {
            Method m = findFactoryDecoder(c, CBORObject.class);
            if (m != null) {
                try {
                    return m.invoke(null, node);
                } catch (InvocationTargetException e) {
                    Throwable cause = e.getCause() != null ? e.getCause() : e;
                    throw new RuntimeException("@Factory decoder failed for " + raw.getName() + ": " + cause.getMessage(), cause);
                } catch (IllegalAccessException e) {
                    throw new RuntimeException("@Factory decoder inaccessible for " + raw.getName(), e);
                }
            }
        }

        // 2. Check interfaces only for primitive CBOR (prevents recursion)
        if (isPrimitiveCbor(node)) {
            for (Class<?> iface : getInterfaceHierarchy(raw)) {
                Method m = findFactoryDecoder(iface, CBORObject.class);
                if (m != null) {
                    try {
                        return m.invoke(null, node);
                    } catch (InvocationTargetException e) {
                        Throwable cause = e.getCause() != null ? e.getCause() : e;
                        throw new RuntimeException("@Factory decoder failed for " + raw.getName() + ": " + cause.getMessage(), cause);
                    } catch (IllegalAccessException e) {
                        throw new RuntimeException("@Factory decoder inaccessible for " + raw.getName(), e);
                    }
                }
            }
        }

        return null;
    }

    /** Get all interfaces in the class hierarchy. */
    private static List<Class<?>> getInterfaceHierarchy(Class<?> clazz) {
        List<Class<?>> interfaces = new ArrayList<>();
        Set<Class<?>> seen = new HashSet<>();
        collectInterfaces(clazz, interfaces, seen);
        return interfaces;
    }

    private static void collectInterfaces(Class<?> clazz, List<Class<?>> result, Set<Class<?>> seen) {
        if (clazz == null) return;
        for (Class<?> iface : clazz.getInterfaces()) {
            if (seen.add(iface)) {
                result.add(iface);
                collectInterfaces(iface, result, seen);
            }
        }
        collectInterfaces(clazz.getSuperclass(), result, seen);
    }

    /**
     * Try {@code @Decode}-annotated leaf factory methods, dispatching on
     * CBOR primitive type (byte string → {@code byte[]}, text string →
     * {@code String}).
     */
    private static Object tryLeafDecode(Class<?> raw, CBORObject node) {
        return switch (node.getType()) {
            case ByteString -> {
                Method m = Leaves.findDecode(raw, byte[].class);
                yield m != null ? Leaves.decode(raw, node.GetByteString()) : null;
            }
            case TextString -> {
                Method m = Leaves.findDecode(raw, String.class);
                yield m != null ? Leaves.decode(raw, node.AsString()) : null;
            }
            default -> null;
        };
    }

    /** Try @Factory-annotated scalar decode methods (String, byte[], int, long). */
    private static Object tryScalarDecode(Class<?> raw, CBORObject node) {
        try {
            return switch (node.getType()) {
                case TextString -> {
                    String text = node.AsString();
                    yield tryFactoryScalar(raw, String.class, text);
                }
                case ByteString -> {
                    byte[] bytes = node.GetByteString();
                    yield tryFactoryScalar(raw, byte[].class, bytes);
                }
                case Integer -> {
                    Object result = tryFactoryScalar(raw, int.class, node.AsInt32());
                    if (result == null) {
                        result = tryFactoryScalar(raw, long.class, node.AsInt64Value());
                    }
                    yield result;
                }
                default -> null;
            };
        } catch (Exception e) {
            throw new RuntimeException("Failed to decode scalar " + raw.getName(), e);
        }
    }

    /** Find and invoke a @Factory method accepting the given scalar parameter type. */
    private static Object tryFactoryScalar(Class<?> raw, Class<?> paramType, Object arg) {
        // Search class hierarchy and interfaces for @Factory methods
        Object result = invokeFactoryScalar(raw, paramType, arg);
        if (result != null && raw.isInstance(result)) return result;

        // Fall back to constructor with same param type
        try {
            Constructor<?> ctor = raw.getConstructor(paramType);
            return ctor.newInstance(arg);
        } catch (NoSuchMethodException ignored) {
            return result; // return even if wrong type (let caller handle)
        } catch (Exception e) {
            throw new RuntimeException("Failed to construct " + raw.getName(), e);
        }
    }

    private static Object invokeFactoryScalar(Class<?> raw, Class<?> paramType, Object arg) {
        for (Class<?> c = raw; c != null && c != Object.class; c = c.getSuperclass()) {
            Method m = findFactoryDecoder(c, paramType);
            if (m != null) {
                try {
                    return m.invoke(null, arg);
                } catch (InvocationTargetException e) {
                    Throwable cause = e.getCause() != null ? e.getCause() : e;
                    throw new RuntimeException("@Factory decoder failed for " + raw.getName(), cause);
                } catch (IllegalAccessException e) {
                    throw new RuntimeException("@Factory decoder inaccessible for " + raw.getName(), e);
                }
            }
        }
        for (Class<?> iface : getInterfaceHierarchy(raw)) {
            Method m = findFactoryDecoder(iface, paramType);
            if (m != null) {
                try {
                    return m.invoke(null, arg);
                } catch (InvocationTargetException e) {
                    Throwable cause = e.getCause() != null ? e.getCause() : e;
                    throw new RuntimeException("@Factory decoder failed for " + raw.getName(), cause);
                } catch (IllegalAccessException e) {
                    throw new RuntimeException("@Factory decoder inaccessible for " + raw.getName(), e);
                }
            }
        }
        return null;
    }

    /** Decode a sealed interface by matching discriminator to permitted subclasses. */
    @SuppressWarnings("unchecked")
    private static Object decodeSealedType(Class<?> raw, CBORObject node, Scope scope) {
        String discriminator = extractDiscriminator(node);
        if (discriminator != null) {
            for (Class<?> permitted : raw.getPermittedSubclasses()) {
                // Match by class name pattern (e.g., "cid" -> ContentMemberId)
                String simpleName = permitted.getSimpleName().toLowerCase();
                if (simpleName.startsWith(discriminator) || simpleName.contains(discriminator)) {
                    return fromCborTree(node, (Class<? extends Canonical>) permitted, scope);
                }

                // Match by kind field value
                if (matchesKindField(permitted, discriminator)) {
                    return fromCborTree(node, (Class<? extends Canonical>) permitted, scope);
                }
            }
        }
        throw new IllegalArgumentException("Cannot determine concrete type for sealed interface: " + raw.getName()
                + " (discriminator=" + discriminator + ", node=" + node + ")");
    }

    /** Check if a class has a "kind" field matching the given discriminator. */
    @SuppressWarnings("unchecked")
    private static boolean matchesKindField(Class<?> clazz, String discriminator) {
        try {
            Field kindField = findKindField(clazz);
            if (kindField != null) {
                Object instance = construct((Class<? extends Canonical>) clazz);
                kindField.setAccessible(true);
                return discriminator.equals(kindField.get(instance));
            }
        } catch (Exception ignored) {}
        return false;
    }

    /** Decode Java primitives and common types. */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Object decodePrimitive(Class<?> raw, CBORObject node) {
        if (raw == String.class)                                   return node.AsString();
        if (raw == boolean.class || raw == Boolean.class)          return node.AsBoolean();
        if (raw == int.class || raw == Integer.class)              return node.AsInt32();
        if (raw == long.class || raw == Long.class)                return node.AsInt64Value();
        if (raw == short.class || raw == Short.class)              return (short) node.AsInt32();
        if (raw == byte.class || raw == Byte.class)                return (byte) node.AsInt32();
        if (raw == BigInteger.class)                               return new BigInteger(node.AsEIntegerValue().toString());
        if (raw == byte[].class)                                   return node.GetByteString();
        if (raw == Instant.class)                                  return Instant.ofEpochMilli(node.AsInt64Value());

        // CG-CBOR forbids IEEE 754 floats
        if (raw == float.class || raw == Float.class || raw == double.class || raw == Double.class) {
            throw new IllegalArgumentException("CG-CBOR forbids IEEE 754 floats. Use Decimal or Rational instead.");
        }

        if (raw.isEnum()) {
            return Enum.valueOf((Class<? extends Enum>) raw, node.AsString());
        }

        // Fallback
        return node.ToObject(raw);
    }

    // =================== Reflection Helpers ===================

    /**
     * Decode using generic type information when available.
     *
     * @param generic the Field.getGenericType() (may be ParameterizedType)
     * @param raw raw Class for the field
     * @param node the CBOR node to decode
     * @param scope the encoding scope
     * @return the decoded value
     */
    @SuppressWarnings({"rawtypes","unchecked"})
    public static Object decodeIntoType(Type generic, Class<?> raw, CBORObject node, Scope scope) {
        if (node == null || node.isNull()) return raw.isPrimitive() ? defaultPrimitive(raw) : null;

        // Check for custom fromCborTree() first - this allows Canonical types that extend
        // Collection (like EndorsementsTable) to use custom deserialization
        if (Canonical.class.isAssignableFrom(raw)) {
            Object result = tryFromCborTree(raw, node);
            if (result != null) return result;
        }

        // byte[] is encoded as ByteString, not Array
        if (raw == byte[].class) {
            if (node.getType() != CBORType.ByteString)
                throw new IllegalArgumentException("Expected CBOR byte string for byte[], got " + node.getType());
            return node.GetByteString();
        }

        // Other arrays are encoded as CBOR arrays
        if (raw.isArray()) {
            if (node.getType() != CBORType.Array)
                throw new IllegalArgumentException("Expected CBOR array for array type " + raw.getName());
            Class<?> comp = raw.getComponentType();
            int n = node.size();
            Object arr = Array.newInstance(comp, n);
            for (int i = 0; i < n; i++) Array.set(arr, i, decodeIntoClass(comp, node.get(i), scope));
            return arr;
        }

        // Collections with generic element type
        if (Collection.class.isAssignableFrom(raw)) {
            if (node.getType() != CBORType.Array)
                throw new IllegalArgumentException("Expected CBOR array for collection type " + raw.getName());

            Class<?> elemType = null;
            if (generic instanceof ParameterizedType pt) {
                Type t0 = pt.getActualTypeArguments()[0];
                if (t0 instanceof Class<?> c) elemType = c;
            }

            Collection out = Set.class.isAssignableFrom(raw) ? new LinkedHashSet<>() : new ArrayList<>();
            for (int i = 0; i < node.size(); i++) {
                CBORObject e = node.get(i);
                out.add(elemType != null ? decodeIntoClass(elemType, e, scope) : decodeUntyped(e, scope));
            }
            return out;
        }

        // Maps with generic key/value type
        if (Map.class.isAssignableFrom(raw)) {
            if (node.getType() != CBORType.Map)
                throw new IllegalArgumentException("Expected CBOR map for map type " + raw.getName());

            Class<?> keyType = null;
            Class<?> valType = null;
            if (generic instanceof ParameterizedType pt) {
                Type t0 = pt.getActualTypeArguments()[0];
                Type t1 = pt.getActualTypeArguments()[1];
                if (t0 instanceof Class<?> c) keyType = c;
                if (t1 instanceof Class<?> c) valType = c;
            }

            Map out = new LinkedHashMap<>();
            for (CBORObject k : node.getKeys()) {
                Object dk = keyType != null ? decodeIntoClass(keyType, k, scope) : decodeUntyped(k, scope);
                CBORObject v = node.get(k);
                Object dv = valType != null ? decodeIntoClass(valType, v, scope) : decodeUntyped(v, scope);
                out.put(dk, dv);
            }
            return out;
        }

        return decodeIntoClass(raw, node, scope);
    }

    // REPLACE: decodeValueForField(...) to use the helper
    static Object decodeValueForField(Field f, CBORObject node, Scope scope) {
        return decodeIntoType(f.getGenericType(), f.getType(), node, scope);
    }

    static Object decodeUntyped(CBORObject node, Scope scope) {
        CBORType t = node.getType();
        switch (t) {
            case Integer:    return node.AsInt64Value();
            case TextString: return node.AsString();
            case ByteString: return node.GetByteString();
            case Boolean:    return node.AsBoolean();
            case Array: {
                int n = node.size();
                List<Object> list = new ArrayList<>(n);
                for (int i = 0; i < n; i++) list.add(decodeUntyped(node.get(i), scope));
                return list;
            }
            case Map: {
                Map<Object, Object> m = new LinkedHashMap<>();
                for (CBORObject k : node.getKeys()) {
                    Object dk = decodeUntyped(k, scope);
                    Object dv = decodeUntyped(node.get(k), scope);
                    m.put(dk, dv);
                }
                return m;
            }
            case SimpleValue: // null etc.
                return null;
            default:
                // CG-CBOR forbids IEEE 754 floats
                if (node.isNumber()) {
                    throw new IllegalArgumentException("CG-CBOR forbids IEEE 754 floats. Use Decimal or Rational instead.");
                }
                throw new IllegalArgumentException("Unhandled CBOR type: " + t);
        }
    }

    /**
     * Extract discriminator value from CBOR node (assumes first element of array or "kind" map entry).
     */
    static String extractDiscriminator(CBORObject node) {
        if (node.getType() == CBORType.Array && node.size() > 0) {
            CBORObject first = node.get(0);
            if (first.getType() == CBORType.TextString) {
                return first.AsString();
            }
        } else if (node.getType() == CBORType.Map) {
            CBORObject kindVal = node.get(CBORObject.FromString("kind"));
            if (kindVal != null && kindVal.getType() == CBORType.TextString) {
                return kindVal.AsString();
            }
        }
        return null;
    }

    /**
     * Find a "kind" field in a class (used for polymorphic dispatch).
     */
    static Field findKindField(Class<?> clazz) {
        for (Class<?> c = clazz; c != null; c = c.getSuperclass()) {
            for (Field f : c.getDeclaredFields()) {
                if (f.getName().equals("kind") && f.getType() == String.class) {
                    return f;
                }
            }
        }
        return null;
    }

    /**
     * Get class hierarchy including interfaces (for finding static decode methods).
     */
    static List<Class<?>> getClassHierarchy(Class<?> clazz) {
        List<Class<?>> hierarchy = new ArrayList<>();
        hierarchy.add(clazz);
        // Add all interfaces
        for (Class<?> iface : clazz.getInterfaces()) {
            hierarchy.add(iface);
            hierarchy.addAll(getClassHierarchy(iface));
        }
        // Add superclass chain
        if (clazz.getSuperclass() != null && clazz.getSuperclass() != Object.class) {
            hierarchy.addAll(getClassHierarchy(clazz.getSuperclass()));
        }
        return hierarchy;
    }

    static Object defaultPrimitive(Class<?> raw) {
        if (raw == boolean.class) return false;
        if (raw == byte.class)    return (byte) 0;
        if (raw == short.class)   return (short) 0;
        if (raw == int.class)     return 0;
        if (raw == long.class)    return 0L;
        if (raw == char.class)    return (char) 0;
        throw new IllegalArgumentException("Unknown primitive " + raw);
    }

    static <T> void set(T target, Field f, Object value) {
        try { f.set(target, value); }
        catch (IllegalAccessException e) { throw new RuntimeException("Field set failed: " + f.getName(), e); }
    }

    static <T extends Canonical> T construct(Class<T> type) {
        try {
            Constructor<T> ctor = type.getDeclaredConstructor();
            if (!Modifier.isPublic(ctor.getModifiers())) ctor.setAccessible(true);
            return ctor.newInstance();
        } catch (NoSuchMethodException e) {
            throw new IllegalArgumentException("Type " + type.getName() + " must have a no-arg constructor", e);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException("Failed to instantiate " + type.getName(), e);
        }
    }


    // =========================================================================
    // DECODER CACHE
    // =========================================================================

    /**
     * Cache of @Factory-annotated decoder methods by class.
     */
    Map<Class<?>, Method> DECODER_CACHE = new ConcurrentHashMap<>();

    /**
     * Find the @Factory-annotated decoder method for a class.
     *
     * <p>Searches for static methods annotated with @Factory that accept
     * a single CBORObject parameter (for structured types), String parameter
     * (for text scalars), or byte[] parameter (for binary scalars).
     *
     * @param clazz the class to search
     * @return the decoder method, or null if none found
     */
    static Method findAnnotatedDecoder(Class<?> clazz) {
        return DECODER_CACHE.computeIfAbsent(clazz, c -> {
            // Search class hierarchy
            for (Class<?> cls = c; cls != null && cls != Object.class; cls = cls.getSuperclass()) {
                Method m = findFactoryDecoder(cls, CBORObject.class);
                if (m != null) return m;
            }

            // Search interfaces
            for (Class<?> iface : getInterfaceHierarchy(c)) {
                Method m = findFactoryDecoder(iface, CBORObject.class);
                if (m != null) return m;
            }

            return null;
        });
    }

    /**
     * Find a @Factory-annotated static method accepting the given parameter type.
     */
    private static Method findFactoryDecoder(Class<?> clazz, Class<?> paramType) {
        for (Method m : clazz.getDeclaredMethods()) {
            if (!m.isAnnotationPresent(Factory.class)) continue;
            if (!Modifier.isStatic(m.getModifiers())) continue;
            Class<?>[] params = m.getParameterTypes();
            if (params.length == 1 && params[0] == paramType) {
                m.setAccessible(true);
                return m;
            }
        }
        return null;
    }

    /**
     * Try to decode using a @Factory-annotated decoder method.
     *
     * @param clazz the target class
     * @param node the CBOR node to decode
     * @return the decoded object, or null if no annotated decoder exists
     */
    static Object tryAnnotatedDecoder(Class<?> clazz, CBORObject node) {
        Method decoder = findAnnotatedDecoder(clazz);
        if (decoder == null) return null;

        try {
            return decoder.invoke(null, node);
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            throw new RuntimeException("@Factory decoder failed for " + clazz.getName() + ": " + cause.getMessage(), cause);
        } catch (IllegalAccessException e) {
            throw new RuntimeException("@Factory decoder inaccessible for " + clazz.getName(), e);
        }
    }

}

