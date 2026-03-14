package dev.everydaythings.graph.item;

import dev.everydaythings.graph.item.FactorySchema.FactoryOption;
import dev.everydaythings.graph.item.FactorySchema.ParamSchema;
import lombok.extern.log4j.Log4j2;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Parameter;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Scans classes for {@link Factory} annotated methods and provides instantiation utilities.
 *
 * <p>This is the single place for all component instantiation logic:
 * <ul>
 *   <li>{@link #schemaFor} — discover @Factory methods on a class</li>
 *   <li>{@link #createDefault} — create via no-arg @Factory</li>
 *   <li>{@link #openPathBased} — create via Path-taking @Factory</li>
 *   <li>{@link #instantiate} — create via static create() or no-arg constructor</li>
 *   <li>{@link #instantiateWithParams} — create via static create(Map) or fallback</li>
 * </ul>
 */
@Log4j2
public final class CreationScanner {

    /** Cache of scanned schemas by class. */
    private static final java.util.Map<Class<?>, FactorySchema> cache = new ConcurrentHashMap<>();

    private CreationScanner() {} // Static utility class

    // ==================================================================================
    // Schema Discovery
    // ==================================================================================

    /**
     * Get or compute the factory schema for a type.
     *
     * <p>Results are cached for performance.
     *
     * @param type The class to scan
     * @return The factory schema (may have empty factories list)
     */
    public static FactorySchema schemaFor(Class<?> type) {
        return cache.computeIfAbsent(type, CreationScanner::scan);
    }

    /**
     * Clear the cache (for testing).
     */
    public static void clearCache() {
        cache.clear();
    }

    // ==================================================================================
    // Instantiation
    // ==================================================================================

    /**
     * Instantiate a component from its class.
     *
     * <p>Tries in order:
     * <ol>
     *   <li>Static {@code create()} factory method</li>
     *   <li>No-arg constructor</li>
     * </ol>
     */
    public static Object instantiate(Class<?> compClass) {
        // Try static create() factory first
        try {
            Method createMethod = compClass.getDeclaredMethod("create");
            if (Modifier.isStatic(createMethod.getModifiers())) {
                createMethod.setAccessible(true);
                return createMethod.invoke(null);
            }
        } catch (NoSuchMethodException ignored) {
            // No create() method, try constructor
        } catch (Exception e) {
            logger.debug("create() factory failed for {}, trying constructor: {}",
                    compClass.getSimpleName(), e.getMessage());
        }

        // Fall back to no-arg constructor
        try {
            var ctor = compClass.getDeclaredConstructor();
            ctor.setAccessible(true);
            return ctor.newInstance();
        } catch (Exception e) {
            throw new IllegalStateException(
                    "Cannot instantiate " + compClass.getSimpleName()
                            + ": no create() factory or no-arg constructor", e);
        }
    }

    /**
     * Instantiate a component with parameters.
     *
     * <p>Tries in order:
     * <ol>
     *   <li>Static {@code create(Map)} factory method</li>
     *   <li>Falls back to {@link #instantiate(Class)}</li>
     * </ol>
     */
    public static Object instantiateWithParams(Class<?> compClass, Map<String, Object> params) {
        try {
            Method createMethod = compClass.getDeclaredMethod("create", Map.class);
            if (Modifier.isStatic(createMethod.getModifiers())) {
                createMethod.setAccessible(true);
                return createMethod.invoke(null, params);
            }
        } catch (NoSuchMethodException ignored) {
        } catch (Exception e) {
            logger.debug("create(Map) factory failed for {}, falling back: {}",
                    compClass.getSimpleName(), e.getMessage());
        }
        return instantiate(compClass);
    }

    /**
     * Create a default instance using @Factory annotated methods.
     *
     * <p>Looks for a no-arg @Factory method, preferring the primary one.
     */
    @SuppressWarnings("unchecked")
    public static <T> Optional<T> createDefault(Class<T> type) {
        FactorySchema schema = schemaFor(type);

        if (!schema.hasFactories()) {
            return Optional.empty();
        }

        // First, try to find a no-arg factory (prefer primary)
        FactoryOption primary = schema.primary();
        if (primary != null && primary.params().isEmpty()) {
            try {
                return Optional.of((T) primary.invoke());
            } catch (ReflectiveOperationException e) {
                throw new RuntimeException(
                        "Failed to invoke @Factory " + primary.label() + " on " + type.getName(), e);
            }
        }

        // Try any no-arg factory
        for (FactoryOption factory : schema.factories()) {
            if (factory.params().isEmpty()) {
                try {
                    return Optional.of((T) factory.invoke());
                } catch (ReflectiveOperationException e) {
                    throw new RuntimeException(
                            "Failed to invoke @Factory " + factory.label() + " on " + type.getName(), e);
                }
            }
        }

        // Try primary factory if all params have defaults
        if (primary != null && allParamsHaveDefaults(primary)) {
            try {
                Object[] args = buildDefaultArgs(primary);
                return Optional.of((T) primary.invoke(args));
            } catch (ReflectiveOperationException e) {
                throw new RuntimeException(
                        "Failed to invoke @Factory " + primary.label() + " on " + type.getName(), e);
            }
        }

        return Optional.empty();
    }

    /**
     * Open a path-based component using its @Factory methods.
     *
     * <p>Uses the factory schema to find a factory that accepts a {@link Path}.
     */
    @SuppressWarnings("unchecked")
    public static <T> T openPathBased(Class<T> type, Path path) {
        FactorySchema schema = schemaFor(type);

        FactoryOption primary = schema.primary();
        if (primary != null && matchesPathSignature(primary)) {
            try {
                return (T) primary.invoke(path);
            } catch (ReflectiveOperationException e) {
                throw new RuntimeException("Failed to invoke factory on " + type.getName(), e);
            }
        }

        for (FactoryOption factory : schema.factories()) {
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

    // ==================================================================================
    // Schema Scanning (private)
    // ==================================================================================

    private static FactorySchema scan(Class<?> type) {
        List<FactoryOption> factories = new ArrayList<>();

        for (Method method : type.getMethods()) {
            Factory annotation = method.getAnnotation(Factory.class);
            if (annotation == null) continue;
            if (!Modifier.isStatic(method.getModifiers())) continue;
            if (!type.isAssignableFrom(method.getReturnType())) continue;

            factories.add(buildFactoryOption(method, annotation));
        }

        factories.sort(Comparator.naturalOrder());
        return new FactorySchema(type, List.copyOf(factories));
    }

    private static FactoryOption buildFactoryOption(Method method, Factory annotation) {
        String label = annotation.label().isEmpty()
            ? humanize(method.getName())
            : annotation.label();

        List<ParamSchema> params = new ArrayList<>();
        for (Parameter param : method.getParameters()) {
            params.add(ParamSchema.fromParameter(param));
        }

        return new FactoryOption(label, annotation.doc(), annotation.glyph(),
                annotation.primary(), annotation.order(), method, List.copyOf(params));
    }

    private static String humanize(String methodName) {
        if (methodName.isEmpty()) return methodName;
        StringBuilder result = new StringBuilder();
        result.append(Character.toUpperCase(methodName.charAt(0)));
        for (int i = 1; i < methodName.length(); i++) {
            char c = methodName.charAt(i);
            if (Character.isUpperCase(c)) result.append(' ');
            result.append(c);
        }
        return result.toString();
    }

    private static boolean matchesPathSignature(FactoryOption factory) {
        var params = factory.params();
        return params.size() == 1 && Path.class.isAssignableFrom(params.getFirst().type());
    }

    private static boolean allParamsHaveDefaults(FactoryOption factory) {
        for (ParamSchema param : factory.params()) {
            if (param.required() && param.defaultValue().isEmpty()) return false;
        }
        return true;
    }

    private static Object[] buildDefaultArgs(FactoryOption factory) {
        Object[] args = new Object[factory.params().size()];
        for (int i = 0; i < factory.params().size(); i++) {
            ParamSchema param = factory.params().get(i);
            args[i] = parseDefaultValue(param.defaultValue(), param.type());
        }
        return args;
    }

    private static Object parseDefaultValue(String value, Class<?> type) {
        if (value == null || value.isEmpty()) return null;
        if (type == String.class) return value;
        if (type == int.class || type == Integer.class) return Integer.parseInt(value);
        if (type == long.class || type == Long.class) return Long.parseLong(value);
        if (type == boolean.class || type == Boolean.class) return Boolean.parseBoolean(value);
        if (type == double.class || type == Double.class) return Double.parseDouble(value);
        if (type == float.class || type == Float.class) return Float.parseFloat(value);
        if (type == Path.class) return Path.of(value);
        return null;
    }
}
