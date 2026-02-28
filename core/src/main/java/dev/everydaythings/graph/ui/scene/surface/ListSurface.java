package dev.everydaythings.graph.ui.scene.surface;

import dev.everydaythings.graph.ui.scene.Scene;
import dev.everydaythings.graph.ui.scene.SceneMode;

import java.util.ArrayList;
import java.util.List;

/**
 * Surface for displaying a scrollable list of items.
 *
 * <p>ListSurface is the default surface for List fields.
 * Each item is rendered using its own Surface (with the specified mode).
 *
 * <h2>Declarative Structure</h2>
 * <pre>
 * ┌─────────────────────────┐
 * │  item 1                 │
 * │  item 2  ← selected     │
 * │  item 3                 │
 * │  ...                    │
 * └─────────────────────────┘
 * </pre>
 */
@Scene.Container(direction = Scene.Direction.VERTICAL, style = {"list"})
@Scene.State(when = "selectable", style = {"selectable"})
public class ListSurface extends SurfaceSchema {

    // ==================== Declarative Structure ====================

    @Scene.Repeat(bind = "items", indexVar = "i")
    @Scene.Container(direction = Scene.Direction.HORIZONTAL, style = {"list-item"})
    @Scene.State(when = "$index == selectedIndex", style = {"selected"})
    @Scene.On(event = "click", action = "select", target = "$index")
    static class ItemTemplate {
        @Scene.Embed(bind = "$item")
        static class Content {}
    }

    /**
     * The items to display.
     */
    @Canon(order = 10)
    private List<SurfaceSchema> items = new ArrayList<>();

    /**
     * Rendering mode for each item.
     */
    @Canon(order = 11)
    private SceneMode itemMode = SceneMode.COMPACT;

    /**
     * Whether selection is enabled.
     */
    @Canon(order = 12)
    private boolean selectable = false;

    /**
     * Index of the selected item (-1 for none).
     */
    @Canon(order = 13)
    private int selectedIndex = -1;

    public ListSurface() {}

    public static ListSurface of(List<? extends SurfaceSchema> items) {
        ListSurface surface = new ListSurface();
        surface.items.addAll(items);
        return surface;
    }

    public ListSurface add(SurfaceSchema item) {
        items.add(item);
        return this;
    }

    public ListSurface items(List<? extends SurfaceSchema> items) {
        this.items = new ArrayList<>(items);
        return this;
    }

    public ListSurface itemMode(SceneMode mode) {
        this.itemMode = mode;
        return this;
    }

    public ListSurface selectable(boolean selectable) {
        this.selectable = selectable;
        return this;
    }

    public ListSurface selectedIndex(int index) {
        this.selectedIndex = index;
        return this;
    }

    public List<SurfaceSchema> items() {
        return items;
    }

    public SceneMode itemMode() {
        return itemMode;
    }

    public boolean selectable() {
        return selectable;
    }

    public int selectedIndex() {
        return selectedIndex;
    }

    // Render is now handled declaratively via @Scene.Container + @Scene.Repeat + @Scene.Embed
}
