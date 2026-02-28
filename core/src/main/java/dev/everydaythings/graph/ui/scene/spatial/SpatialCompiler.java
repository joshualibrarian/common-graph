package dev.everydaythings.graph.ui.scene.spatial;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Compiles {@code @Scene.*} spatial annotations into cached data objects.
 *
 * <p>SpatialCompiler reads {@link dev.everydaythings.graph.ui.scene.Scene.Body Scene.Body},
 * {@link dev.everydaythings.graph.ui.scene.Scene.Transform Scene.Transform},
 * {@link dev.everydaythings.graph.ui.scene.Scene.Light Scene.Light},
 * {@link dev.everydaythings.graph.ui.scene.Scene.Audio Scene.Audio},
 * {@link dev.everydaythings.graph.ui.scene.Scene.Environment Scene.Environment},
 * {@link dev.everydaythings.graph.ui.scene.Scene.Camera Scene.Camera}, and
 * {@link dev.everydaythings.graph.ui.scene.Scene.Face Scene.Face} annotations
 * once per class and caches the results as {@link CompiledSpace} and
 * {@link CompiledBody} records.
 *
 * <p>Renderers consume these compiled records instead of calling
 * {@code getAnnotation()} at every render frame. This establishes the
 * data-driven rendering pattern where annotations are a developer convenience
 * for setting defaults — not the runtime source of truth.
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * // In a SpatialSchema.render() method:
 * CompiledSpace space = SpatialCompiler.compileSpace(getClass());
 * space.emit(out);  // environment + light + camera
 *
 * // In ItemSpace for component bodies:
 * CompiledBody body = SpatialCompiler.compileBody(component.getClass());
 * if (body.isCompound()) { ... }
 * else if (body.hasGeometry()) { body.emitGeometry(out); }
 * }</pre>
 *
 * @see CompiledSpace
 * @see CompiledBody
 */
public final class SpatialCompiler {

    private SpatialCompiler() {} // Static utility

    /** Per-class cache for @Space.* annotation data. */
    private static final Map<Class<?>, CompiledSpace> SPACE_CACHE = new ConcurrentHashMap<>();

    /** Per-class cache for @Body.* annotation data. */
    private static final Map<Class<?>, CompiledBody> BODY_CACHE = new ConcurrentHashMap<>();

    /**
     * Compile @Scene.Environment/Camera/Light annotations for a SpatialSchema class.
     *
     * <p>Returns a cached result if available, otherwise reads annotations
     * from the class and caches the compiled data.
     *
     * @param schemaClass The SpatialSchema class to compile
     * @return Compiled space data (never null)
     */
    public static CompiledSpace compileSpace(Class<?> schemaClass) {
        return SPACE_CACHE.computeIfAbsent(schemaClass, CompiledSpace::from);
    }

    /**
     * Compile @Scene.Body/Transform/Face/Light/Audio annotations for a component class.
     *
     * <p>Returns a cached result if available, otherwise reads annotations
     * from the class and caches the compiled data.
     *
     * @param componentClass The component class to compile
     * @return Compiled body data (never null)
     */
    public static CompiledBody compileBody(Class<?> componentClass) {
        return BODY_CACHE.computeIfAbsent(componentClass, CompiledBody::from);
    }

    /**
     * Clear both caches. Use only for testing or hot-reload scenarios.
     */
    public static void clearCache() {
        SPACE_CACHE.clear();
        BODY_CACHE.clear();
    }

    /**
     * Number of cached space compilations.
     */
    public static int spaceCacheSize() {
        return SPACE_CACHE.size();
    }

    /**
     * Number of cached body compilations.
     */
    public static int bodyCacheSize() {
        return BODY_CACHE.size();
    }
}
