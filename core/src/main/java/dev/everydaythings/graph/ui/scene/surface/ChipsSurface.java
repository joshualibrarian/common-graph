package dev.everydaythings.graph.ui.scene.surface;

import dev.everydaythings.graph.Canonical;
import dev.everydaythings.graph.ui.scene.Scene;
import dev.everydaythings.graph.ui.scene.surface.primitive.ImageSurface;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * Surface for displaying compact inline tokens (chips/tags).
 *
 * <p>ChipsSurface renders items as small, compact tokens that
 * flow inline. Useful for tags, labels, categories.
 *
 * <h2>Declarative Structure</h2>
 * <pre>
 * ┌───────────────────────────────────────┐
 * │ [🏷️ tag1] [🏷️ tag2] [🏷️ tag3] ...  │
 * └───────────────────────────────────────┘
 * </pre>
 */
@Scene.Container(direction = Scene.Direction.HORIZONTAL, style = {"chips"}, gap = "0.5em")
public class ChipsSurface extends SurfaceSchema {

    // ==================== Declarative Structure ====================

    @Scene.Repeat(bind = "chips")
    @Scene.Container(direction = Scene.Direction.HORIZONTAL, style = {"chip"})
    @Scene.On(event = "click", action = "$item.action")
    static class ChipTemplate {
        @Scene.Image(bind = "$item.icon")
        @Scene.If("$item.icon != null")
        static class Icon {}

        @Scene.Text(bind = "$item.label", style = {"chip-label"})
        static class Label {}
    }

    /**
     * The chips to display.
     */
    @Canon(order = 10)
    private List<Chip> chips = new ArrayList<>();

    /**
     * Whether chips can be removed/dismissed.
     */
    @Canon(order = 11)
    private boolean removable = false;

    /**
     * Whether clicking a chip triggers an action.
     */
    @Canon(order = 12)
    private boolean clickable = true;

    public ChipsSurface() {}

    public static ChipsSurface of(List<Chip> chips) {
        ChipsSurface surface = new ChipsSurface();
        surface.chips.addAll(chips);
        return surface;
    }

    public ChipsSurface add(Chip chip) {
        chips.add(chip);
        return this;
    }

    public ChipsSurface add(String label) {
        chips.add(new Chip(label));
        return this;
    }

    public ChipsSurface add(String label, String icon) {
        chips.add(new Chip(label, icon));
        return this;
    }

    public ChipsSurface chips(List<Chip> chips) {
        this.chips = new ArrayList<>(chips);
        return this;
    }

    public ChipsSurface removable(boolean removable) {
        this.removable = removable;
        return this;
    }

    public ChipsSurface clickable(boolean clickable) {
        this.clickable = clickable;
        return this;
    }

    public List<Chip> chips() {
        return chips;
    }

    public boolean removable() {
        return removable;
    }

    public boolean clickable() {
        return clickable;
    }

    /**
     * A single chip/tag.
     */
    @NoArgsConstructor
    public static class Chip implements Canonical {
        @Canon(order = 0) @Getter private String id;
        @Canon(order = 1) @Getter private String label;
        @Canon(order = 2) @Getter private ImageSurface icon;
        @Canon(order = 3) private List<String> style;
        @Canon(order = 4) @Getter private String action;

        public Chip(String label) { this.label = label; }
        public Chip(String label, String iconGlyph) {
            this.label = label;
            this.icon = iconGlyph != null ? ImageSurface.of(iconGlyph) : null;
        }

        public static Chip of(String label) { return new Chip(label); }
        public static Chip of(String label, String iconGlyph) { return new Chip(label, iconGlyph); }

        public Chip id(String id) { this.id = id; return this; }
        public Chip icon(ImageSurface icon) { this.icon = icon; return this; }
        public Chip icon(String glyph) { this.icon = ImageSurface.of(glyph); return this; }
        public Chip style(String... styles) { this.style = List.of(styles); return this; }
        public Chip action(String action) { this.action = action; return this; }

        public List<String> style() { return style != null ? style : List.of(); }
    }

    // Render is now handled declaratively via @Scene.Container + @Scene.Repeat
}
