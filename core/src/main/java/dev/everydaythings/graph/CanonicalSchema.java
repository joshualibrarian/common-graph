package dev.everydaythings.graph;

import dev.everydaythings.graph.Canonical.Canon;
import dev.everydaythings.graph.Canonical.ClassCollectionType;
import dev.everydaythings.graph.Canonical.Canonization;
import dev.everydaythings.graph.item.component.Factory;
import lombok.Value;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Cached encoding schema for a Canonical class.
 *
 * <p>Extracted ONCE per class via {@link #of(Class)}, then cached.
 * Contains everything needed for encoding/decoding a Canonical object.
 *
 * <p>This is focused purely on serialization. For display/presentation,
 * see {@link DisplaySchema} which is completely independent.
 */
@Value
public class CanonicalSchema {

    private static final Map<Class<?>, CanonicalSchema> CACHE = new ConcurrentHashMap<>();

    /** The class this schema describes. */
    Class<?> clazz;

    /** ARRAY or MAP encoding layout. */
    ClassCollectionType layout;

    /** Decoder method annotated with @Factory (taking CBORObject), or null. */
    Method decoder;

    /** Fields annotated with @Canon, sorted by order. */
    List<FieldSchema> fields;

    // ==================================================================================
    // Factory
    // ==================================================================================

    /**
     * Get or compute the schema for a Canonical class.
     */
    public static CanonicalSchema of(Class<?> clazz) {
        return CACHE.computeIfAbsent(clazz, CanonicalSchema::scan);
    }

    /**
     * Clear the cache (for testing or hot-reload).
     */
    public static void clearCache() {
        CACHE.clear();
    }

    // ==================================================================================
    // Scanning
    // ==================================================================================

    private static CanonicalSchema scan(Class<?> clazz) {
        // Class-level @Canonization
        Canonization canonization = clazz.getAnnotation(Canonization.class);
        ClassCollectionType layout = canonization != null
                ? canonization.classType()
                : Canonical.DEFAULT_CLASS_COLLECTION_TYPE;

        // Find decoder method
        Method decoder = findDecoder(clazz);

        // Scan @Canon fields
        List<FieldSchema> fields = scanFields(clazz);

        return new CanonicalSchema(clazz, layout, decoder, fields);
    }

    private static Method findDecoder(Class<?> clazz) {
        // Check class hierarchy for @Factory methods taking CBORObject
        for (Class<?> c = clazz; c != null && c != Object.class; c = c.getSuperclass()) {
            Method m = findFactoryDecoder(c);
            if (m != null) return m;
        }

        // Check interfaces
        for (Class<?> iface : getAllInterfaces(clazz)) {
            Method m = findFactoryDecoder(iface);
            if (m != null) return m;
        }

        return null;
    }

    private static Method findFactoryDecoder(Class<?> clazz) {
        for (Method m : clazz.getDeclaredMethods()) {
            if (!m.isAnnotationPresent(Factory.class)) continue;
            if (!Modifier.isStatic(m.getModifiers())) continue;
            Class<?>[] params = m.getParameterTypes();
            if (params.length == 1 && params[0] == com.upokecenter.cbor.CBORObject.class) {
                m.setAccessible(true);
                return m;
            }
        }
        return null;
    }

    private static List<FieldSchema> scanFields(Class<?> clazz) {
        List<FieldSchema> fields = new ArrayList<>();

        for (Class<?> c = clazz; c != null && c != Object.class; c = c.getSuperclass()) {
            for (Field f : c.getDeclaredFields()) {
                if (Modifier.isStatic(f.getModifiers())) continue;
                if (Modifier.isTransient(f.getModifiers())) continue;

                Canon canon = f.getAnnotation(Canon.class);
                if (canon == null) continue;

                f.setAccessible(true);

                fields.add(new FieldSchema(
                        f,
                        f.getName(),
                        f.getType(),
                        f.getGenericType(),
                        toDisplayName(f.getName()),
                        canon.order(),
                        canon.isBody(),
                        canon.isRecord(),
                        canon.setting()
                ));
            }
        }

        // Sort by order
        fields.sort(Comparator.comparingInt(FieldSchema::order));
        return List.copyOf(fields);
    }

    private static List<Class<?>> getAllInterfaces(Class<?> clazz) {
        List<Class<?>> result = new ArrayList<>();
        collectInterfaces(clazz, result);
        return result;
    }

    private static void collectInterfaces(Class<?> clazz, List<Class<?>> result) {
        if (clazz == null) return;
        for (Class<?> iface : clazz.getInterfaces()) {
            if (!result.contains(iface)) {
                result.add(iface);
                collectInterfaces(iface, result);
            }
        }
        collectInterfaces(clazz.getSuperclass(), result);
    }

    // ==================================================================================
    // Convenience methods
    // ==================================================================================

    /**
     * Get fields for a specific encoding scope.
     */
    public List<FieldSchema> fieldsForScope(Canonical.Scope scope) {
        return fields.stream()
                .filter(f -> scope == Canonical.Scope.BODY ? f.isBody() : f.isRecord())
                .toList();
    }

    /**
     * Check if this class has a custom decoder.
     */
    public boolean hasDecoder() {
        return decoder != null;
    }

    // ==================================================================================
    // FieldSchema - encoding metadata for a single field
    // ==================================================================================

    /**
     * Schema for a single @Canon annotated field.
     */
    @Value
    public static class FieldSchema {
        Field field;
        String name;
        Class<?> type;
        Type genericType;
        String displayName;
        int order;
        boolean isBody;
        boolean isRecord;
        boolean isSetting;

        /**
         * Is this field a Canonical type?
         */
        public boolean isCanonical() {
            return Canonical.class.isAssignableFrom(type);
        }

        /**
         * Is this field a collection?
         */
        public boolean isCollection() {
            return Iterable.class.isAssignableFrom(type) || type.isArray();
        }

        /**
         * Is this field a boolean?
         */
        public boolean isBoolean() {
            return type == boolean.class || type == Boolean.class;
        }

        /**
         * Is this field a String?
         */
        public boolean isString() {
            return type == String.class;
        }

        /**
         * Is this field a numeric primitive or wrapper?
         */
        public boolean isNumeric() {
            return type == int.class || type == Integer.class
                    || type == long.class || type == Long.class
                    || type == short.class || type == Short.class
                    || type == byte.class || type == Byte.class
                    || type == double.class || type == Double.class
                    || type == float.class || type == Float.class
                    || Number.class.isAssignableFrom(type);
        }

        /**
         * Is this field an enum?
         */
        public boolean isEnum() {
            return type.isEnum();
        }

        /**
         * Get the enum constants if this field is an enum type.
         *
         * @return array of enum constants, or null if not an enum
         */
        public Object[] enumConstants() {
            return type.getEnumConstants();
        }

        /**
         * Extract the element type from a parameterized collection (e.g., List&lt;String&gt; → String).
         *
         * @return the element type, or Object.class if not parameterized
         */
        public Class<?> elementType() {
            if (genericType instanceof ParameterizedType pt) {
                Type[] args = pt.getActualTypeArguments();
                if (args.length > 0 && args[0] instanceof Class<?> c) {
                    return c;
                }
            }
            return Object.class;
        }

        /**
         * Get field value from an object.
         */
        public Object getValue(Object obj) {
            try {
                return field.get(obj);
            } catch (IllegalAccessException e) {
                throw new RuntimeException("Cannot access field: " + name, e);
            }
        }

        /**
         * Set field value on an object.
         */
        public void setValue(Object obj, Object value) {
            try {
                field.set(obj, value);
            } catch (IllegalAccessException e) {
                throw new RuntimeException("Cannot set field: " + name, e);
            }
        }
    }

    // ==================================================================================
    // Display name helper
    // ==================================================================================

    /**
     * Convert a camelCase field name to a display name.
     *
     * <p>Examples:
     * <ul>
     *   <li>"baseColor" → "Base Color"</li>
     *   <li>"darkMode" → "Dark Mode"</li>
     *   <li>"x" → "X"</li>
     *   <li>"fontSize" → "Font Size"</li>
     * </ul>
     */
    public static String toDisplayName(String fieldName) {
        if (fieldName == null || fieldName.isEmpty()) return "";

        StringBuilder sb = new StringBuilder();
        sb.append(Character.toUpperCase(fieldName.charAt(0)));

        for (int i = 1; i < fieldName.length(); i++) {
            char c = fieldName.charAt(i);
            if (Character.isUpperCase(c)) {
                sb.append(' ');
            }
            sb.append(c);
        }
        return sb.toString();
    }
}
