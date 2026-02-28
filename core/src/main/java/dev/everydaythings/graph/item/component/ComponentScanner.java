package dev.everydaythings.graph.item.component;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Scans component classes for {@link Tick} annotated methods.
 *
 * <p>Follows the same scan-once, cache-forever pattern as {@link CreationScanner}.
 * Results are stored in {@link ComponentMeta} which holds the list of
 * {@link TickSpec} instances discovered on the class.
 *
 * <p>Usage:
 * <pre>{@code
 * ComponentMeta meta = ComponentScanner.metaFor(ClockFace.class);
 * if (meta.hasTickMethods()) {
 *     for (TickSpec spec : meta.ticks()) {
 *         spec.invoke(clockInstance);
 *     }
 * }
 * }</pre>
 */
public final class ComponentScanner {

    private static final Map<Class<?>, ComponentMeta> cache = new ConcurrentHashMap<>();

    private ComponentScanner() {}

    /**
     * Get or compute the component metadata for a type.
     */
    public static ComponentMeta metaFor(Class<?> type) {
        return cache.computeIfAbsent(type, ComponentScanner::scan);
    }

    /**
     * Clear the cache (for testing).
     */
    public static void clearCache() {
        cache.clear();
    }

    private static ComponentMeta scan(Class<?> type) {
        List<TickSpec> ticks = new ArrayList<>();

        // Walk the class hierarchy (including interfaces)
        for (Method method : type.getMethods()) {
            Tick annotation = method.getAnnotation(Tick.class);
            if (annotation == null) continue;

            // Validate: must be public, non-static, no params
            if (Modifier.isStatic(method.getModifiers())) continue;
            if (method.getParameterCount() != 0) continue;

            ticks.add(new TickSpec(method, annotation.interval()));
        }

        return new ComponentMeta(Collections.unmodifiableList(ticks));
    }

    /**
     * Cached metadata about a component class's tick methods.
     */
    public static class ComponentMeta {
        private final List<TickSpec> ticks;

        ComponentMeta(List<TickSpec> ticks) {
            this.ticks = ticks;
        }

        public List<TickSpec> ticks() {
            return ticks;
        }

        public boolean hasTickMethods() {
            return !ticks.isEmpty();
        }
    }
}
