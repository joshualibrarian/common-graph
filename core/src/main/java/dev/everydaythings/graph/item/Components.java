package dev.everydaythings.graph.item;

import dev.everydaythings.graph.item.id.ItemID;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.file.Path;
import java.util.Optional;

/**
 * Static utility methods for working with components.
 *
 * <p>Provides type resolution, encoding/decoding, factory invocation,
 * and lifecycle management for component instances.
 */
public final class Components {

    private Components() {} // Utility class

    /**
     * Get the type key from a component or item class.
     */
    public static String typeKey(Class<?> type) {
        Type annotation = type.getAnnotation(Type.class);
        if (annotation == null) {
            throw new IllegalArgumentException(
                    "Class " + type.getName() + " is missing @Type annotation");
        }
        return annotation.value();
    }

    /**
     * Get the type ID from a component or item class.
     */
    public static ItemID typeId(Class<?> type) {
        return ItemID.fromString(typeKey(type));
    }

    /**
     * Encode a component by invoking its static encode method or instance encode().
     *
     * <p>Looks for:
     * <ol>
     *   <li>Static encode(T) method on the class</li>
     *   <li>Instance encode() method returning byte[]</li>
     * </ol>
     *
     * @param value The component value to encode
     * @return Encoded bytes
     * @throws IllegalStateException if no encoding method found
     */
    public static byte[] encode(Object value) {
        Class<?> type = value.getClass();

        // Try static encode(T) method
        try {
            Method encodeMethod = type.getMethod("encode", type);
            if (Modifier.isStatic(encodeMethod.getModifiers())) {
                return (byte[]) encodeMethod.invoke(null, value);
            }
        } catch (NoSuchMethodException ignored) {
            // Fall through to next option
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException("Failed to invoke encode() on " + type.getName(), e);
        }

        // Try instance encode() method
        try {
            Method encodeMethod = type.getMethod("encode");
            if (!Modifier.isStatic(encodeMethod.getModifiers())
                    && byte[].class.equals(encodeMethod.getReturnType())) {
                return (byte[]) encodeMethod.invoke(value);
            }
        } catch (NoSuchMethodException ignored) {
            // Fall through
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException("Failed to invoke encode() on " + type.getName(), e);
        }

        throw new IllegalStateException(
                "Class " + type.getName() + " has no encode method");
    }

    /**
     * Decode bytes to a component by invoking its static decode(byte[]) method.
     *
     * @param type The component class (must have static decode(byte[]) method)
     * @param bytes The bytes to decode
     * @return The decoded component instance
     * @throws IllegalStateException if the class doesn't have a decode method
     */
    @SuppressWarnings("unchecked")
    public static <T> T decode(Class<T> type, byte[] bytes) {
        try {
            Method decodeMethod = type.getMethod("decode", byte[].class);
            return (T) decodeMethod.invoke(null, (Object) bytes);
        } catch (NoSuchMethodException e) {
            throw new IllegalStateException(
                    "Class " + type.getName() + " is missing static decode(byte[]) method");
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException("Failed to invoke decode() on " + type.getName(), e);
        }
    }

    /**
     * Open a path-based component using its @Factory methods.
     *
     * <p>Uses {@link CreationScanner} to find a factory that accepts a {@link Path}.
     *
     * @param type The component class (must have a @Factory method taking Path)
     * @param path The path to open at
     * @return The opened component instance
     * @throws IllegalStateException if no suitable factory method found
     */
    @SuppressWarnings("unchecked")
    public static <T> T openPathBased(Class<T> type, Path path) {
        FactorySchema schema = CreationScanner.schemaFor(type);

        // First try: primary factory if it takes a single Path
        FactorySchema.FactoryOption primary = schema.primary();
        if (primary != null && matchesPathSignature(primary)) {
            try {
                return (T) primary.invoke(path);
            } catch (ReflectiveOperationException e) {
                throw new RuntimeException("Failed to invoke factory on " + type.getName(), e);
            }
        }

        // Second try: any factory that takes a single Path
        for (FactorySchema.FactoryOption factory : schema.factories()) {
            if (matchesPathSignature(factory)) {
                try {
                    return (T) factory.invoke(path);
                } catch (ReflectiveOperationException e) {
                    throw new RuntimeException("Failed to invoke factory on " + type.getName(), e);
                }
            }
        }

        throw new IllegalStateException(
                "Class " + type.getName() + " has no @Factory method that takes a Path parameter");
    }

    private static boolean matchesPathSignature(FactorySchema.FactoryOption factory) {
        var params = factory.params();
        return params.size() == 1 && Path.class.isAssignableFrom(params.getFirst().type());
    }

    /**
     * Create a default instance using @Factory annotated methods.
     *
     * <p>Looks for a no-arg @Factory method, preferring the primary one.
     *
     * @param type The component class
     * @return The created component, or empty if no suitable factory
     */
    @SuppressWarnings("unchecked")
    public static <T> Optional<T> createDefault(Class<T> type) {
        FactorySchema schema = CreationScanner.schemaFor(type);

        if (!schema.hasFactories()) {
            return Optional.empty();
        }

        // First, try to find a no-arg factory (prefer primary)
        FactorySchema.FactoryOption primary = schema.primary();
        if (primary != null && primary.params().isEmpty()) {
            try {
                return Optional.of((T) primary.invoke());
            } catch (ReflectiveOperationException e) {
                throw new RuntimeException("Failed to invoke @Factory " + primary.label() + " on " + type.getName(), e);
            }
        }

        // Try any no-arg factory
        for (FactorySchema.FactoryOption factory : schema.factories()) {
            if (factory.params().isEmpty()) {
                try {
                    return Optional.of((T) factory.invoke());
                } catch (ReflectiveOperationException e) {
                    throw new RuntimeException("Failed to invoke @Factory " + factory.label() + " on " + type.getName(), e);
                }
            }
        }

        // Try primary factory if all params have defaults
        if (primary != null && allParamsHaveDefaults(primary)) {
            try {
                Object[] args = buildDefaultArgs(primary);
                return Optional.of((T) primary.invoke(args));
            } catch (ReflectiveOperationException e) {
                throw new RuntimeException("Failed to invoke @Factory " + primary.label() + " on " + type.getName(), e);
            }
        }

        return Optional.empty();
    }

    private static boolean allParamsHaveDefaults(FactorySchema.FactoryOption factory) {
        for (FactorySchema.ParamSchema param : factory.params()) {
            if (param.required() && param.defaultValue().isEmpty()) {
                return false;
            }
        }
        return true;
    }

    private static Object[] buildDefaultArgs(FactorySchema.FactoryOption factory) {
        Object[] args = new Object[factory.params().size()];
        for (int i = 0; i < factory.params().size(); i++) {
            FactorySchema.ParamSchema param = factory.params().get(i);
            args[i] = parseDefaultValue(param.defaultValue(), param.type());
        }
        return args;
    }

    private static Object parseDefaultValue(String value, Class<?> type) {
        if (value == null || value.isEmpty()) {
            return null;
        }
        if (type == String.class) return value;
        if (type == int.class || type == Integer.class) return Integer.parseInt(value);
        if (type == long.class || type == Long.class) return Long.parseLong(value);
        if (type == boolean.class || type == Boolean.class) return Boolean.parseBoolean(value);
        if (type == double.class || type == Double.class) return Double.parseDouble(value);
        if (type == float.class || type == Float.class) return Float.parseFloat(value);
        if (type == Path.class) return Path.of(value);
        return null;
    }

    /**
     * Close a component if it implements AutoCloseable.
     *
     * @param value The component to close
     */
    public static void close(Object value) {
        if (value instanceof AutoCloseable ac) {
            try {
                ac.close();
            } catch (Exception e) {
                System.err.println("Failed to close component: " + e.getMessage());
            }
        }
    }
}
