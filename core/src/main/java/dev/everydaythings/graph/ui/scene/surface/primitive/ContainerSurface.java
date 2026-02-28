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
     * Whether this container captures focus and handles its own navigation.
     *
     * <p>When true, the container is a single tab stop. Internal navigation
     * (e.g., arrow keys in a tree or list) is handled by the container itself.
     * Tab moves to the next focusable element outside this container.
     *
     * <p>Examples:
     * <ul>
     *   <li>Tree - arrow keys navigate nodes, tab exits</li>
     *   <li>List - arrow keys navigate items, tab exits</li>
     *   <li>Form - false (default), tab moves between fields</li>
     * </ul>
     */
    @Canon(order = 12)
    protected boolean capturesFocus = false;

    /**
     * Explicit width: "40ch", "200px", "20em".
     */
    @Canon(order = 13)
    protected String boxWidth;

    /**
     * Explicit height: "10ln", "100px", "5em".
     */
    @Canon(order = 14)
    protected String boxHeight;

    /**
     * Overflow behavior: "visible", "hidden", "scroll", or "auto".
     *
     * <p>"visible" (default) — content overflows, no clipping.
     * "hidden" — content clipped at bounds.
     * "scroll" — always show scrollbar.
     * "auto" — scrollbar shown only when content overflows.
     */
    @Canon(order = 15)
    protected String overflow;

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

    public <S extends ContainerSurface> S capturesFocus(boolean capturesFocus) {
        this.capturesFocus = capturesFocus;
        return self();
    }

    public Scene.Direction direction() {
        return direction;
    }

    public String gap() {
        return gap;
    }

    public boolean capturesFocus() {
        return capturesFocus;
    }

    public <S extends ContainerSurface> S width(String width) {
        this.boxWidth = width;
        return self();
    }

    public <S extends ContainerSurface> S height(String height) {
        this.boxHeight = height;
        return self();
    }

    public String boxWidth() {
        return boxWidth;
    }

    public String boxHeight() {
        return boxHeight;
    }

    public <S extends ContainerSurface> S overflow(String overflow) {
        this.overflow = overflow;
        return self();
    }

    public String overflow() {
        return overflow;
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
                || (boxWidth != null && !boxWidth.isEmpty())
                || (boxHeight != null && !boxHeight.isEmpty())
                || (padding != null && !padding.isEmpty());

        if (hasVisualProps) {
            out.beginBox(direction, style(),
                    border != null ? border : BoxBorder.NONE,
                    boxBackground != null ? boxBackground : "",
                    boxWidth != null ? boxWidth : "",
                    boxHeight != null ? boxHeight : "",
                    padding != null ? padding : "");
        } else {
            out.beginBox(direction, style());
        }

        renderChildren(out);
        out.endBox();
    }
}
