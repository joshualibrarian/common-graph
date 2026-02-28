package dev.everydaythings.graph.ui.scene;

import dev.everydaythings.graph.Canonical;
import dev.everydaythings.graph.ui.scene.surface.SurfaceRenderer;
import dev.everydaythings.graph.ui.scene.surface.SurfaceSchema;

/**
 * Base class for all Scene patterns — the unified 2D + 3D schema.
 *
 * <p>SceneSchema extends {@link SurfaceSchema} with 3D capabilities:
 * <ul>
 *   <li>{@code scaleZ} — Z-axis scale factor (inherited from SpatialSchema concept)</li>
 *   <li>{@code depth()} / {@code hasDepth()} — aliases for elevation/elevated</li>
 *   <li>{@code render(SceneRenderer)} — unified rendering with 3D support</li>
 * </ul>
 *
 * <p>The pipeline:
 * <pre>
 * DATA + SCENE → VIEW → RENDER
 * (CBOR)  (CBOR)   (CBOR)  (Platform)
 * </pre>
 *
 * <p>All SceneSchema subclasses are CBOR-serializable via {@link Canonical}.
 * They support both {@code @Scene.*} annotations (compiled by {@link SceneCompiler})
 * and {@code @Surface.*} annotations (compiled by {@link SceneCompiler}).
 *
 * <h2>Declarative Surfaces with 3D</h2>
 * <pre>{@code
 * @Scene.Body(shape = "box", width = "44cm", depth = "44cm")
 * @Scene.Container(direction = Direction.VERTICAL)
 * public class ChessBoard extends SceneSchema<ChessState> {
 *
 *     @Scene.Repeat(bind = "value.rows")
 *     @Scene.Container(direction = Direction.HORIZONTAL)
 *     static class Row {
 *
 *         @Scene.Repeat(bind = "$item")
 *         @Scene.Container(shape = "rectangle", depth = "1cm")
 *         @Scene.State(style = "light", when = "$item.isLight")
 *         static class Square { ... }
 *     }
 * }
 * }</pre>
 *
 * @param <T> The type of value this scene displays/edits
 *
 * @see Scene
 * @see SceneRenderer
 * @see SceneCompiler
 * @see SurfaceSchema
 */
@SuppressWarnings("unchecked")
public abstract class SceneSchema<T> extends SurfaceSchema<T> {

    /**
     * Scale factor along the Z axis (default 1.0).
     *
     * <p>Inherited conceptually from SpatialSchema. In 2D rendering this is
     * ignored. In 3D rendering it applies to the element's depth.
     */
    @Canon(order = 14)
    protected double scaleZ = 1.0;

    // ===== Depth Aliases (delegate to inherited elevation/elevated) =====

    /**
     * Get the 3D depth (perpendicular displacement) in meters.
     *
     * <p>This is an alias for {@link #elevation()} — same field, clearer name
     * in the Scene context.
     *
     * @return depth in meters
     */
    public double depth() {
        return elevation;
    }

    /**
     * Whether this element has 3D depth.
     *
     * <p>Alias for {@link #isElevated()}.
     */
    public boolean hasDepth() {
        return elevated;
    }

    /**
     * Set 3D depth (perpendicular displacement from body face).
     *
     * <p>In 2D: ignored. In 3D: the element becomes a slab with this height.
     * This is the Scene-style alias for {@link #elevation(double)}.
     *
     * @param meters Depth in meters
     */
    public <S extends SceneSchema<?>> S depth(double meters) {
        this.elevation = meters;
        this.elevated = meters != 0.0;
        return self();
    }

    // ===== ScaleZ =====

    public <S extends SceneSchema<?>> S scaleZ(double scaleZ) {
        this.scaleZ = scaleZ;
        return self();
    }

    public double scaleZ() {
        return scaleZ;
    }

    /**
     * Set uniform scale (all three axes).
     *
     * <p>Overrides the parent's 2D-only scale to also set scaleZ.
     */
    @Override
    public <S extends SurfaceSchema<?>> S scale(double scale) {
        super.scale(scale);
        this.scaleZ = scale;
        return self();
    }

    // ===== Rendering =====

    /**
     * Render this scene to a SceneRenderer.
     *
     * <p>Convenience overload — delegates to {@link #render(SurfaceRenderer)}.
     *
     * @param out The scene renderer
     */
    public void render(SceneRenderer out) {
        render((SurfaceRenderer) out);
    }

    /**
     * Render this scene to any renderer.
     *
     * <p>Compiles and renders @Scene.* annotations. 3D calls are emitted only
     * when the renderer is a {@link SceneRenderer}.
     */
    @Override
    public void render(SurfaceRenderer out) {
        emitCommonProperties(out);

        Class<?> compileFrom = structureClass() != null ? structureClass() : this.getClass();

        if (SceneCompiler.canCompile(compileFrom)) {
            SceneCompiler.render(this, out);
            return;
        }

        throw new UnsupportedOperationException(
                getClass().getName() + " has no @Scene structural annotations "
                        + "and doesn't override render()");
    }

    /**
     * Emit common properties before rendering.
     *
     * <p>Depth from {@code @Scene.Container(depth = "...")} is handled by
     * {@link SceneCompiler} during node rendering, not here, to avoid
     * double-emission.
     */
    @Override
    public void emitCommonProperties(SurfaceRenderer out) {
        super.emitCommonProperties(out);
    }
}
