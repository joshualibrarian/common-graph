package dev.everydaythings.graph.ui.scene.spatial;

import dev.everydaythings.graph.ui.scene.RenderContext;
import dev.everydaythings.graph.ui.scene.Scene;

import static dev.everydaythings.graph.ui.scene.spatial.CompiledBody.dim;

/**
 * Compiled snapshot of {@link Scene.Environment}, {@link Scene.Camera}, and
 * {@link Scene.Light} annotations for a SpatialSchema class.
 *
 * <p>Annotations are read once by {@link SpatialCompiler} and cached — renderers
 * consume this record instead of calling {@code getAnnotation()} per frame.
 *
 * <p>Dimension fields are stored as strings (e.g., "5m", "1.5m") and
 * resolved to meters at emit time via {@link RenderContext}.
 *
 * @see SpatialCompiler
 * @see Scene.Environment
 * @see Scene.Camera
 * @see Scene.Light
 */
public record CompiledSpace(
        // @Space.Environment
        boolean hasEnvironment,
        int background, int ambient,
        String fogNear, String fogFar, int fogColor,

        // @Space.Camera
        boolean hasCamera,
        String projection, double fov, String near, String far,
        String camX, String camY, String camZ,
        String targetX, String targetY, String targetZ,

        // @Space.Light
        boolean hasLight,
        String lightType, int lightColor, double lightIntensity,
        String lightX, String lightY, String lightZ,
        double lightDirX, double lightDirY, double lightDirZ
) {

    /** Empty compiled space — no annotations present. Z-up: y=forward, z=up. */
    public static final CompiledSpace EMPTY = new CompiledSpace(
            false, 0, 0, "", "", 0,
            false, "perspective", 60, "0.1m", "1000m", "0", "5m", "1.5m", "0", "0", "0",
            false, "directional", 0xFFFFFF, 1.0, "0", "0", "5m", 0, 0, -1
    );

    /**
     * Compile all @Scene.Environment/Camera/Light annotations from a class.
     */
    static CompiledSpace from(Class<?> schemaClass) {
        Scene.Environment sceneEnv = schemaClass.getAnnotation(Scene.Environment.class);
        Scene.Camera sceneCam = schemaClass.getAnnotation(Scene.Camera.class);
        Scene.Light sceneLight = schemaClass.getAnnotation(Scene.Light.class);

        if (sceneEnv == null && sceneCam == null && sceneLight == null) {
            return EMPTY;
        }

        return new CompiledSpace(
                // Environment
                sceneEnv != null,
                sceneEnv != null ? sceneEnv.background() : 0,
                sceneEnv != null ? sceneEnv.ambient() : 0,
                sceneEnv != null ? sceneEnv.fogNear() : "",
                sceneEnv != null ? sceneEnv.fogFar() : "",
                sceneEnv != null ? sceneEnv.fogColor() : 0,

                // Camera
                sceneCam != null,
                sceneCam != null ? sceneCam.projection() : "perspective",
                sceneCam != null ? sceneCam.fov() : 60,
                sceneCam != null ? sceneCam.near() : "0.1m",
                sceneCam != null ? sceneCam.far() : "1000m",
                sceneCam != null ? sceneCam.x() : "0",
                sceneCam != null ? sceneCam.y() : "1.5m",
                sceneCam != null ? sceneCam.z() : "5m",
                sceneCam != null ? sceneCam.targetX() : "0",
                sceneCam != null ? sceneCam.targetY() : "0",
                sceneCam != null ? sceneCam.targetZ() : "0",

                // Light
                sceneLight != null,
                sceneLight != null ? sceneLight.type() : "directional",
                sceneLight != null ? sceneLight.color() : 0xFFFFFF,
                sceneLight != null ? sceneLight.intensity() : 1.0,
                sceneLight != null ? sceneLight.x() : "0",
                sceneLight != null ? sceneLight.y() : "5m",
                sceneLight != null ? sceneLight.z() : "0",
                sceneLight != null ? sceneLight.dirX() : 0,
                sceneLight != null ? sceneLight.dirY() : -1,
                sceneLight != null ? sceneLight.dirZ() : 0
        );
    }

    /**
     * Resolve fog distance, returning -1 for empty (no fog).
     */
    private static double fogDim(String spec, RenderContext ctx) {
        if (spec == null || spec.isEmpty()) return -1;
        return dim(spec, ctx);
    }

    /**
     * Emit environment to a renderer.
     */
    public void emitEnvironment(SpatialRenderer out, RenderContext ctx) {
        if (hasEnvironment) {
            out.environment(background, ambient, fogDim(fogNear, ctx), fogDim(fogFar, ctx), fogColor);
        }
    }

    /**
     * Emit camera to a renderer.
     */
    public void emitCamera(SpatialRenderer out, RenderContext ctx) {
        if (hasCamera) {
            out.camera(projection, fov, dim(near, ctx), dim(far, ctx),
                    dim(camX, ctx), dim(camY, ctx), dim(camZ, ctx),
                    dim(targetX, ctx), dim(targetY, ctx), dim(targetZ, ctx));
        }
    }

    /**
     * Emit light to a renderer.
     */
    public void emitLight(SpatialRenderer out, RenderContext ctx) {
        if (hasLight) {
            out.light(lightType, lightColor, lightIntensity,
                    dim(lightX, ctx), dim(lightY, ctx), dim(lightZ, ctx),
                    lightDirX, lightDirY, lightDirZ);
        }
    }

    /**
     * Emit all space properties (environment, light, camera) to a renderer.
     */
    public void emit(SpatialRenderer out) {
        RenderContext ctx = out.renderContext();
        emitEnvironment(out, ctx);
        emitLight(out, ctx);
        emitCamera(out, ctx);
    }
}
