package dev.everydaythings.graph.ui.scene;

import dev.everydaythings.graph.ui.scene.surface.SurfaceRenderer;

import java.util.List;

/**
 * Unified rendering interface for 2D surfaces and 3D spatial scenes.
 *
 * <p>SceneRenderer extends {@link SurfaceRenderer} with 3D scene methods.
 * All 3D methods have default no-op implementations, so existing renderers
 * (Skia, CLI, TUI) can trivially implement SceneRenderer by just changing
 * their {@code implements} clause — they only need what they already implement.
 *
 * <p>3D-capable renderers (Filament, WebGL) override the 3D methods to
 * emit geometry, transforms, lights, and cameras.
 *
 * <h2>Capability Probing</h2>
 * <p>Use {@link #supportsDepth()} to check if the renderer handles 3D.
 * This replaces the {@code @Scene.Query("depth")} mechanism — the compiler
 * can ask the renderer at compile time whether to emit 3D or 2D fallback.
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * public void render(SceneRenderer out) {
 *     // 2D primitives (inherited from SurfaceRenderer)
 *     out.beginBox(Scene.Direction.VERTICAL, List.of("card"));
 *     out.text("Hello", List.of("heading"));
 *     out.endBox();
 *
 *     // 3D scene elements (no-op if renderer doesn't support them)
 *     out.environment(0x1A1A2E, 0x404040, -1, -1, 0);
 *     out.light("directional", 0xFFFFFF, 0.8, 0, 10, 0, 0, -1, 0);
 *     out.pushTransform(1, 0, 0, 0, 0, 0, 1, 1, 1, 1);
 *     out.body("box", 1, 0.5, 1, 0x8B4513, 1.0, "lit", List.of());
 *     out.popTransform();
 * }
 * }</pre>
 *
 * @see SurfaceRenderer
 * @see SceneSchema
 * @see SceneCompiler
 */
public interface SceneRenderer extends SurfaceRenderer {

    // ==================== Depth (replaces elevation) ====================

    /**
     * Set 3D depth for the next container or shape.
     *
     * <p>When a 3D renderer encounters a depth-annotated element, it creates
     * a solid slab with the specified height. Children render on top of the slab.
     * 2D renderers inherit the no-op default.
     *
     * <p>This replaces {@link SurfaceRenderer#elevation(double, boolean)} with
     * clearer naming. Both methods are supported — depth() delegates to
     * elevation() by default for backward compatibility.
     *
     * @param meters Depth in meters
     * @param solid  Whether to render as a solid 3D slab or just Z offset
     */
    default void depth(double meters, boolean solid) {
        elevation(meters, solid);
    }

    // ==================== 3D Geometry ====================

    /**
     * Emit a primitive 3D body (geometry + material).
     *
     * @param shape   Primitive shape: "box", "sphere", "cylinder", "plane", "none"
     * @param w       Width (meters)
     * @param h       Height (meters)
     * @param d       Depth (meters)
     * @param color   Material color as hex int
     * @param opacity Material opacity (0.0-1.0)
     * @param shading Shading mode: "lit", "unlit", "wireframe"
     * @param styles  Style classes for semantic meaning
     */
    default void body(String shape, double w, double h, double d,
                      int color, double opacity, String shading, List<String> styles) {}

    /**
     * Emit a mesh-based 3D body.
     *
     * @param meshRef ContentID or classpath reference to mesh asset
     * @param color   Material color as hex int
     * @param opacity Material opacity (0.0-1.0)
     * @param shading Shading mode: "lit", "unlit", "wireframe"
     * @param styles  Style classes
     */
    default void meshBody(String meshRef, int color, double opacity,
                          String shading, List<String> styles) {}

    /**
     * Emit a line segment in 3D space.
     *
     * @param x1    Start X (meters)
     * @param y1    Start Y
     * @param z1    Start Z
     * @param x2    End X (meters)
     * @param y2    End Y
     * @param z2    End Z
     * @param color Line color as hex int (0xRRGGBB)
     * @param width Line width (meters)
     */
    default void line(double x1, double y1, double z1,
                      double x2, double y2, double z2,
                      int color, double width) {}

    // ==================== Transform Stack ====================

    /**
     * Push a transform onto the stack.
     *
     * <p>Applied to all subsequent bodies/faces until {@link #popTransform()}.
     *
     * @param x  Position X (meters)
     * @param y  Position Y (meters)
     * @param z  Position Z (meters)
     * @param qx Rotation quaternion X
     * @param qy Rotation quaternion Y
     * @param qz Rotation quaternion Z
     * @param qw Rotation quaternion W
     * @param sx Scale X
     * @param sy Scale Y
     * @param sz Scale Z
     */
    default void pushTransform(double x, double y, double z,
                               double qx, double qy, double qz, double qw,
                               double sx, double sy, double sz) {}

    /**
     * Pop the most recent transform from the stack.
     */
    default void popTransform() {}

    // ==================== Face Rendering (surface-on-body) ====================

    /**
     * Begin rendering a 2D surface on a body face.
     *
     * <p>Between {@code beginFace()} and {@code endFace()}, 2D render
     * instructions (text, image, beginBox/endBox) are directed to the
     * specified face of the current body.
     *
     * @param face Face name: "top", "front", "back", "bottom", "left", "right"
     * @param ppm  Resolution: pixels per meter
     */
    default void beginFace(String face, int ppm) {}

    /**
     * End the current face rendering context.
     */
    default void endFace() {}

    // ==================== Lighting ====================

    /**
     * Emit a light source.
     *
     * @param type      Light type: "directional", "point", "spot"
     * @param color     Light color as hex int
     * @param intensity Light intensity (1.0 = standard)
     * @param x         Position X (for point/spot lights)
     * @param y         Position Y
     * @param z         Position Z
     * @param dirX      Direction X (for directional/spot lights)
     * @param dirY      Direction Y
     * @param dirZ      Direction Z
     */
    default void light(String type, int color, double intensity,
                       double x, double y, double z,
                       double dirX, double dirY, double dirZ) {}

    // ==================== Camera ====================

    /**
     * Set the camera for this scene.
     *
     * @param projection Projection type: "perspective", "orthographic"
     * @param fov        Field of view in degrees (perspective only)
     * @param near       Near clipping plane distance
     * @param far        Far clipping plane distance
     * @param x          Camera position X
     * @param y          Camera position Y
     * @param z          Camera position Z
     * @param tx         Look-at target X
     * @param ty         Look-at target Y
     * @param tz         Look-at target Z
     */
    default void camera(String projection, double fov, double near, double far,
                        double x, double y, double z,
                        double tx, double ty, double tz) {}

    // ==================== Environment ====================

    /**
     * Set the scene environment (background, ambient, fog).
     *
     * @param background Background color as hex int
     * @param ambient    Ambient light color as hex int
     * @param fogNear    Fog start distance (-1 = no fog)
     * @param fogFar     Fog end distance (-1 = no fog)
     * @param fogColor   Fog color as hex int
     */
    default void environment(int background, int ambient,
                             double fogNear, double fogFar, int fogColor) {}

    // ==================== 3D Audio ====================

    /**
     * Emit a 3D audio source.
     *
     * @param src         Content reference to audio asset
     * @param x           Position X (meters)
     * @param y           Position Y
     * @param z           Position Z
     * @param volume      Volume (0.0-1.0)
     * @param pitch       Playback speed multiplier
     * @param loop        Whether to loop
     * @param spatial     Whether to apply 3D spatialization
     * @param refDistance Reference distance for attenuation
     * @param maxDistance Maximum audible distance
     * @param autoplay    Whether to start playing immediately
     */
    default void audio3d(String src, double x, double y, double z,
                         double volume, double pitch, boolean loop,
                         boolean spatial, double refDistance, double maxDistance,
                         boolean autoplay) {}

    // ==================== Capability Probes ====================

    /**
     * Whether this renderer supports 3D depth/geometry.
     *
     * <p>Used by the compiler to decide whether to emit 3D elements
     * or fall back to 2D-only rendering.
     *
     * @return true if the renderer handles 3D scene elements
     */
    default boolean supportsDepth() { return false; }

    // ==================== Context ====================

    /**
     * Get the render context for unit resolution.
     *
     * <p>Used by the compiler to resolve string-based dimension annotations
     * (e.g., "2m", "50cm") to meters at emit time.
     *
     * @return the render context, or null if unavailable (tests)
     */
    default RenderContext renderContext() { return null; }
}
