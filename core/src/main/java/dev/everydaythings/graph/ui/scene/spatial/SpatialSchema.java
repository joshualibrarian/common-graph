package dev.everydaythings.graph.ui.scene.spatial;

import dev.everydaythings.graph.Canonical;

import java.util.List;

/**
 * Base class for all 3D Scene patterns.
 *
 * <p>SpatialSchema is the 3D counterpart to {@link SurfaceSchema}. Where
 * SurfaceSchema defines how data is presented in 2D (layout, text, containers),
 * SpatialSchema defines how data is presented in 3D (bodies, transforms, lights).
 *
 * <p>The pipeline:
 * <pre>
 * DATA + SCENE → SCENE-TREE → RENDER
 * (CBOR)  (CBOR)    (CBOR)      (Platform)
 * </pre>
 *
 * <p>All SpatialSchema subclasses are CBOR-serializable via {@link Canonical}.
 * They can be:
 * <ul>
 *   <li>Derived from {@link Body} and {@link Space} annotations at compile time</li>
 *   <li>Stored as components on Items (runtime override)</li>
 *   <li>Sent over the wire to remote renderers</li>
 * </ul>
 *
 * @param <T> The type of value this scene displays. Use {@code Void}
 *            for scenes that don't bind to a specific value type.
 *
 * @see Body
 * @see Space
 * @see SpatialRenderer
 * @see SurfaceSchema
 */
@Canonical.Canonization
public abstract class SpatialSchema<T> implements Canonical {

    /**
     * The value being displayed by this scene.
     */
    @Canon(order = -1)
    protected T value;

    /**
     * Optional ID for referencing this scene element.
     */
    @Canon(order = 0)
    protected String id;

    /**
     * Style classes applied to this scene element.
     */
    @Canon(order = 1)
    protected List<String> style;

    /**
     * Whether this scene element is visible.
     */
    @Canon(order = 2)
    protected boolean visible = true;

    // ===== 3D Transform =====

    /**
     * Scale factor along the X axis (default 1.0).
     */
    @Canon(order = 10)
    protected double scaleX = 1.0;

    /**
     * Scale factor along the Y axis (default 1.0).
     */
    @Canon(order = 11)
    protected double scaleY = 1.0;

    /**
     * Scale factor along the Z axis (default 1.0).
     */
    @Canon(order = 12)
    protected double scaleZ = 1.0;

    // ===== Fluent Setters =====

    @SuppressWarnings("unchecked")
    protected <S extends SpatialSchema<?>> S self() {
        return (S) this;
    }

    public <S extends SpatialSchema<?>> S id(String id) {
        this.id = id;
        return self();
    }

    public <S extends SpatialSchema<?>> S style(String... styles) {
        this.style = List.of(styles);
        return self();
    }

    public <S extends SpatialSchema<?>> S style(List<String> styles) {
        this.style = styles;
        return self();
    }

    public <S extends SpatialSchema<?>> S visible(boolean visible) {
        this.visible = visible;
        return self();
    }

    public <S extends SpatialSchema<?>> S scaleX(double scaleX) {
        this.scaleX = scaleX;
        return self();
    }

    public <S extends SpatialSchema<?>> S scaleY(double scaleY) {
        this.scaleY = scaleY;
        return self();
    }

    public <S extends SpatialSchema<?>> S scaleZ(double scaleZ) {
        this.scaleZ = scaleZ;
        return self();
    }

    /**
     * Set uniform scale (all three axes).
     */
    public <S extends SpatialSchema<?>> S scale(double scale) {
        this.scaleX = scale;
        this.scaleY = scale;
        this.scaleZ = scale;
        return self();
    }

    // ===== Value Accessors =====

    public T value() {
        return value;
    }

    @SuppressWarnings("unchecked")
    public <S extends SpatialSchema<T>> S value(T value) {
        this.value = value;
        return (S) this;
    }

    // ===== Getters =====

    public String id() {
        return id;
    }

    public List<String> style() {
        return style != null ? style : List.of();
    }

    public boolean visible() {
        return visible;
    }

    public double scaleX() {
        return scaleX;
    }

    public double scaleY() {
        return scaleY;
    }

    public double scaleZ() {
        return scaleZ;
    }

    // ===== Rendering =====

    /**
     * Render this scene to the given output.
     *
     * <p>Each scene describes itself by emitting render instructions.
     * Platform-specific renderers implement {@link SpatialRenderer} and build
     * their native output from these instructions.
     *
     * @param out The scene renderer to emit instructions to
     */
    public abstract void render(SpatialRenderer out);

    /**
     * Emit common properties (id, visibility) before rendering.
     */
    protected void emitCommonProperties(SpatialRenderer out) {
        if (id != null && !id.isEmpty()) {
            out.id(id);
        }
    }
}
