package dev.everydaythings.graph.item.component;

import dev.everydaythings.graph.item.component.FactorySchema.FactoryOption;
import dev.everydaythings.graph.item.component.FactorySchema.ParamSchema;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Scans classes for {@link Factory} annotated methods to build creation schemas.
 *
 * <p>Used by the UI to discover how to create instances of a type.
 *
 * <p>Example usage:
 * <pre>{@code
 * FactorySchema schema = CreationScanner.schemaFor(Library.class);
 * if (schema.hasFactories()) {
 *     FactoryOption primary = schema.primary();
 *     // Show factory selection UI
 * }
 * }</pre>
 */
public final class CreationScanner {

    /** Cache of scanned schemas by class. */
    private static final Map<Class<?>, FactorySchema> cache = new ConcurrentHashMap<>();

    private CreationScanner() {} // Static utility class

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

    /**
     * Scan a class for @Factory methods.
     */
    private static FactorySchema scan(Class<?> type) {
        List<FactoryOption> factories = new ArrayList<>();

        // Scan all methods including inherited
        for (Method method : type.getMethods()) {
            Factory annotation = method.getAnnotation(Factory.class);
            if (annotation == null) continue;

            // Must be static
            if (!Modifier.isStatic(method.getModifiers())) continue;

            // Must return the type (or subtype)
            if (!type.isAssignableFrom(method.getReturnType())) continue;

            // Build factory option
            FactoryOption option = buildFactoryOption(method, annotation);
            factories.add(option);
        }

        // Sort by order
        factories.sort(Comparator.naturalOrder());

        return new FactorySchema(type, List.copyOf(factories));
    }

    /**
     * Build a FactoryOption from a method and its annotation.
     */
    private static FactoryOption buildFactoryOption(Method method, Factory annotation) {
        // Derive label from method name if not specified
        String label = annotation.label().isEmpty()
            ? humanize(method.getName())
            : annotation.label();

        String doc = annotation.doc();
        String glyph = annotation.glyph();
        boolean primary = annotation.primary();
        int order = annotation.order();

        // Build parameter schemas
        List<ParamSchema> params = new ArrayList<>();
        for (Parameter param : method.getParameters()) {
            params.add(ParamSchema.fromParameter(param));
        }

        return new FactoryOption(label, doc, glyph, primary, order, method, List.copyOf(params));
    }

    /**
     * Convert camelCase method name to human-readable label.
     *
     * <p>Examples:
     * <ul>
     *   <li>"memory" → "Memory"</li>
     *   <li>"createInMemory" → "Create In Memory"</li>
     *   <li>"file" → "File"</li>
     * </ul>
     */
    private static String humanize(String methodName) {
        if (methodName.isEmpty()) return methodName;

        StringBuilder result = new StringBuilder();
        result.append(Character.toUpperCase(methodName.charAt(0)));

        for (int i = 1; i < methodName.length(); i++) {
            char c = methodName.charAt(i);
            if (Character.isUpperCase(c)) {
                result.append(' ');
            }
            result.append(c);
        }

        return result.toString();
    }
}
