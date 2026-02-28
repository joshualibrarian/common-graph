package dev.everydaythings.graph.ui.scene.surface;

import dev.everydaythings.graph.ui.scene.Scene;
import dev.everydaythings.graph.ui.scene.SceneCompiler;
import dev.everydaythings.graph.ui.scene.surface.primitive.ContainerSurface;

/**
 * Surface-based window title bar built from an item's HandleSurface.
 *
 * <p>The title bar IS the item handle — icon, label, and badges, plus
 * a collapse toggle. This replaces OS-provided window decorations with
 * a Surface rendered through the normal pipeline.
 *
 * <pre>
 * ┌──────────────────────────────────────────────┐
 * │ [icon] label  [badges...]       [collapse]   │
 * └──────────────────────────────────────────────┘
 * </pre>
 *
 * <p>The title bar region is the drag handle for moving the window.
 */
@Scene.Container(direction = Scene.Direction.HORIZONTAL,
                 style = {"title-bar"},
                 shape = "rectangle",
                 background = "#2B2B3B",
                 padding = "0.25em 0.5em")
public class TitleBarSurface extends ContainerSurface {

    // ==================== Declarative Structure ====================

    @Scene.Embed(bind = "handle")
    static class Handle {}

    // Spacer pushes collapse button to the right
    @Scene.Container(direction = Scene.Direction.HORIZONTAL, style = {"spacer"})
    @Scene.Flex(grow = 1)
    static class Spacer {}

    @Scene.Text(bind = "collapseGlyph", style = {"title-bar-button"})
    @Scene.On(event = "click", action = "toggleCollapse")
    static class CollapseButton {}

    // ==================== State ====================

    @Canon(order = 20)
    private HandleSurface handle;

    @Canon(order = 21)
    private boolean collapsed;

    public TitleBarSurface() {
        this.direction = Scene.Direction.HORIZONTAL;
    }

    // ==================== Factory ====================

    /**
     * Create a title bar wrapping the given handle.
     */
    public static TitleBarSurface of(HandleSurface handle) {
        TitleBarSurface bar = new TitleBarSurface();
        bar.handle = handle;
        return bar;
    }

    // ==================== Accessors ====================

    public HandleSurface handle() { return handle; }

    public boolean collapsed() { return collapsed; }

    public String collapseGlyph() {
        return collapsed ? "▸" : "▾";
    }

    /**
     * Toggle collapsed state. Returns new state.
     */
    public boolean toggleCollapse() {
        collapsed = !collapsed;
        return collapsed;
    }

    // ==================== Rendering ====================

    @Override
    public void render(SurfaceRenderer out) {
        emitCommonProperties(out);
        SceneCompiler.render(this, out);
    }
}
