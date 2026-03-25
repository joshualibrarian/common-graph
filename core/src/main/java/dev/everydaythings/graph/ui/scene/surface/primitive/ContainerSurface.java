package dev.everydaythings.graph.ui.scene.surface.primitive;

import dev.everydaythings.graph.ui.scene.BoxBorder;
import dev.everydaythings.graph.ui.scene.Scene;
import dev.everydaythings.graph.ui.scene.surface.*;

import java.util.ArrayList;
import java.util.List;

/**
 * Container surface that holds child surfaces.
 *
 * <p>ContainerSurface is the fundamental building block for composing surfaces.
 * It arranges children horizontally or vertically, like HTML's flexbox.
 *
 * <p>Leaf surfaces (TextSurface, ImageSurface) extend SurfaceSchema directly.
 * Containers with additional semantics extend this class:
 * <ul>
 *   <li>{@link ButtonSurface} - clickable action trigger</li>
 *   <li>{@link ListSurface} - selectable items</li>
 *   <li>etc.</li>
 * </ul>
 *
 * <h2>Hierarchy</h2>
 * <pre>
 * SurfaceSchema (base)
 * ├── TextSurface (leaf)
 * ├── ImageSurface (leaf)
 * └── ContainerSurface (has children + direction)
 *     ├── ButtonSurface (adds action semantics)
 *     ├── ListSurface (adds selection)
 *     └── etc.
 * </pre>
 */
public class ContainerSurface extends SurfaceSchema<Void> {

    /**
     * Layout direction for children.
     */
    @Canon(order = 10)
    protected Scene.Direction direction = Scene.Direction.VERTICAL;

    /**
     * Gap between children.
     */
    @Canon(order = 11)
    protected String gap;

    /**
     * Child surfaces contained by this surface.
     */
    @Canon(order = 100)
    protected List<SurfaceSchema<?>> children = new ArrayList<>();

    public ContainerSurface() {}

    public ContainerSurface(Scene.Direction direction) {
        this.direction = direction;
    }

    // ==================== Factory Methods ====================

    /**
     * Create a vertical container.
     */
    public static ContainerSurface vertical() {
        return new ContainerSurface(Scene.Direction.VERTICAL);
    }

    /**
     * Create a horizontal container.
     */
    public static ContainerSurface horizontal() {
        return new ContainerSurface(Scene.Direction.HORIZONTAL);
    }

    // ==================== Layout Properties ====================

    public <S extends ContainerSurface> S direction(Scene.Direction direction) {
        this.direction = direction;
        return self();
    }

    public <S extends ContainerSurface> S gap(String gap) {
        this.gap = gap;
        return self();
    }

    @SuppressWarnings("unchecked")
    @Override
    public ContainerSurface width(String width) {
        this.width = width;
        return this;
    }

    @SuppressWarnings("unchecked")
    @Override
    public ContainerSurface height(String height) {
        this.height = height;
        return this;
    }

    @SuppressWarnings("unchecked")
    @Override
    public ContainerSurface overflow(String overflow) {
        this.overflow = overflow;
        return this;
    }

    @SuppressWarnings("unchecked")
    @Override
    public ContainerSurface capturesFocus(boolean capturesFocus) {
        this.capturesFocus = capturesFocus;
        return this;
    }

    public Scene.Direction direction() {
        return direction;
    }

    public String gap() {
        return gap;
    }

    /**
     * Bridge getter for callers that use the old boxWidth name.
     * Delegates to the base class {@code width} field.
     */
    public String boxWidth() {
        return width;
    }

    /**
     * Bridge getter for callers that use the old boxHeight name.
     * Delegates to the base class {@code height} field.
     */
    public String boxHeight() {
        return height;
    }

    // ==================== Children ====================

    /**
     * Add a child surface.
     */
    public <S extends ContainerSurface> S add(SurfaceSchema<?> child) {
        children.add(child);
        return self();
    }

    /**
     * Add multiple child surfaces.
     */
    public <S extends ContainerSurface> S addAll(List<? extends SurfaceSchema<?>> surfaces) {
        children.addAll(surfaces);
        return self();
    }

    /**
     * Add a text child (convenience).
     */
    public <S extends ContainerSurface> S add(String text) {
        children.add(TextSurface.of(text));
        return self();
    }

    /**
     * Set the children, replacing any existing.
     */
    public <S extends ContainerSurface> S children(List<? extends SurfaceSchema<?>> children) {
        this.children = new ArrayList<>(children);
        return self();
    }

    /**
     * Get the children.
     */
    public List<SurfaceSchema<?>> children() {
        return children;
    }

    /**
     * Check if this container has children.
     */
    public boolean hasChildren() {
        return !children.isEmpty();
    }

    // ==================== Rendering ====================

    /**
     * Render all children to the output.
     */
    protected void renderChildren(SurfaceRenderer out) {
        for (SurfaceSchema<?> child : children) {
            child.render(out);
        }
    }

    @Override
    public void render(SurfaceRenderer out) {
        emitCommonProperties(out);

        if (gap != null && !gap.isEmpty()) {
            out.gap(gap);
        }
        if (overflow != null && !"visible".equals(overflow)) {
            out.overflow(overflow);
        }

        BoxBorder border = boxBorder();
        boolean hasVisualProps = (border != null && border.isVisible())
                || (boxBackground != null && !boxBackground.isEmpty())
                || (width != null && !width.isEmpty())
                || (height != null && !height.isEmpty())
                || (padding != null && !padding.isEmpty());

        if (hasVisualProps) {
            out.beginBox(direction, style(),
                    border != null ? border : BoxBorder.NONE,
                    boxBackground != null ? boxBackground : "",
                    width != null ? width : "",
                    height != null ? height : "",
                    padding != null ? padding : "");
        } else {
            out.beginBox(direction, style());
        }

        renderChildren(out);
        out.endBox();
    }
}
