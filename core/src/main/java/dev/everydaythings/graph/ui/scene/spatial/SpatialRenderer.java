package dev.everydaythings.graph.ui.scene.spatial;

import dev.everydaythings.graph.ui.scene.RenderContext;
import dev.everydaythings.graph.ui.scene.surface.SurfaceRenderer;

import java.util.List;

/**
 * Platform-agnostic rendering interface for 3D scenes.
 *
 * <p>SpatialRenderer is the 3D counterpart to {@link SurfaceRenderer}.
 * Scenes describe themselves by emitting render instructions to this interface.
 * Platform-specific renderers (Filament, WebGL, text fallback) implement it.
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * // Scene describes itself
 * public void render(SpatialRenderer out) {
 *     out.environment(0x1A1A2E, 0x404040, -1, -1, 0);
 *     out.light("directional", 0xFFFFFF, 0.8, 0, 10, 0, 0, -1, 0);
 *     out.camera("perspective", 60, 0.1, 1000, 0, 2, 5, 0, 0, 0);
 *
 *     out.pushTransform(1, 0, 0, 0, 0, 0, 1, 1, 1, 1);
 *     out.body("box", 1, 0.5, 1, 0x8B4513, 1.0, "lit", List.of());
 *     out.popTransform();
 * }
 * }</pre>
 *
 * @see SpatialSchema
 * @see SurfaceRenderer
 */
public interface SpatialRenderer {

    // ==================== Bodies ====================

    /**
     * Emit a line segment in 3D space.
     *
     * <p>Rendered as an unlit colored quad oriented perpendicular to the
     * line direction. Affected by the current transform stack.
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
    void body(String shape, double w, double h, double d,
              int color, double opacity, String shading, List<String> styles);

    /**
     * Emit a mesh-based 3D body.
     *
     * @param meshRef ContentID reference to mesh asset
     * @param color   Material color as hex int
     * @param opacity Material opacity (0.0-1.0)
     * @param shading Shading mode: "lit", "unlit", "wireframe"
     * @param styles  Style classes
     */
    void meshBody(String meshRef, int color, double opacity, String shading, List<String> styles);

    // ==================== Transform Stack ====================

    /**
     * Push a transform onto the stack. Applied to all subsequent bodies/panels
     * until {@link #popTransform()}.
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
    void pushTransform(double x, double y, double z,
                       double qx, double qy, double qz, double qw,
                       double sx, double sy, double sz);

    /**
     * Pop the most recent transform from the stack.
     */
    void popTransform();

    // ==================== Embedded 2D Panels ====================

    /**
     * Begin an embedded 2D surface panel in 3D space.
     *
     * <p>Between {@code beginPanel()} and {@code endPanel()}, use
     * {@link #panelRenderer()} to get a {@link SurfaceRenderer} for
     * emitting 2D content onto the panel.
     *
     * @param width  Panel width in meters
     * @param height Panel height in meters
     * @param ppm    Pixels per meter (resolution)
     */
    void beginPanel(double width, double height, double ppm);

    /**
     * Get the SurfaceRenderer for the current panel.
     *
     * <p>Only valid between {@link #beginPanel} and {@link #endPanel}.
     *
     * @return A SurfaceRenderer for emitting 2D content onto the panel
     */
    SurfaceRenderer panelRenderer();

    /**
     * End the current 2D panel.
     */
    void endPanel();

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
    void light(String type, int color, double intensity,
               double x, double y, double z,
               double dirX, double dirY, double dirZ);

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
    void camera(String projection, double fov, double near, double far,
                double x, double y, double z,
                double tx, double ty, double tz);

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
    void environment(int background, int ambient,
                     double fogNear, double fogFar, int fogColor);

    // ==================== Audio ====================

    /**
     * Emit a 3D audio source.
     *
     * <p>Audio sources are positioned in 3D space and use distance-based
     * attenuation. The listener position tracks the camera.
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
    default void audio(String src, double x, double y, double z,
                       double volume, double pitch, boolean loop,
                       boolean spatial, double refDistance, double maxDistance,
                       boolean autoplay) {}

    // ==================== Context ====================

    /**
     * Get the render context for unit resolution.
     *
     * <p>Used by {@link CompiledBody} and {@link CompiledSpace} to resolve
     * string-based dimension annotations (e.g., "2m", "50cm") to meters
     * at emit time.
     *
     * @return the render context, or null if unavailable (tests)
     */
    default RenderContext renderContext() { return null; }

    // ==================== Metadata ====================

    /**
     * Set the ID for the next scene element.
     */
    void id(String id);
}
