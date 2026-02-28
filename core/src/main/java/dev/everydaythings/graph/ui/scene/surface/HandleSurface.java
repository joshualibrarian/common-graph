package dev.everydaythings.graph.ui.scene.surface;

import dev.everydaythings.graph.ui.scene.Scene;
import dev.everydaythings.graph.ui.scene.SceneCompiler;
import dev.everydaythings.graph.ui.scene.surface.primitive.ContainerSurface;
import dev.everydaythings.graph.ui.scene.surface.primitive.ImageSurface;
import lombok.Getter;

import java.util.ArrayList;
import java.util.List;

/**
 * A compact visual handle: icon + label + optional badges.
 *
 * <p>HandleSurface is a convenience composite for the common pattern of
 * representing something with an icon, a name, and optional status badges.
 * Think of it like a desktop icon or a file in a file manager.
 *
 * <p>Use cases:
 * <ul>
 *   <li>Tree node content (default for TreeSurface)</li>
 *   <li>List item content</li>
 *   <li>Chips/tags with icons</li>
 *   <li>Any compact item representation</li>
 * </ul>
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * // Simple handle
 * HandleSurface.of("📁", "Documents")
 *
 * // With badges
 * HandleSurface.of("📄", "README.md")
 *     .badge("✏️")
 *     .badge("🔒")
 *
 * // With image icon
 * HandleSurface.of(ImageSurface.of("📁").image(folderImageId), "Documents")
 *
 * // Full control
 * HandleSurface.create()
 *     .icon(ImageSurface.of("📊").image(chartImageId))
 *     .label("Sales Report")
 *     .badges(List.of(
 *         ImageSurface.of("🔴"),
 *         ImageSurface.of("📌")
 *     ))
 * }</pre>
 *
 * <h2>Declarative Structure</h2>
 * <pre>
 * ┌─────────────────────────────────────┐
 * │ [icon] label          [badges...]  │
 * └─────────────────────────────────────┘
 * </pre>
 */
@Getter
@Scene.Rule(match = ".handle-label", fontWeight = "bold")
@Scene.Rule(match = ".handle-subtitle", opacity = "dim")
@Scene.Rule(match = ".handle-badges", opacity = "dim")
@Scene.Container(direction = Scene.Direction.HORIZONTAL, style = {"handle"}, gap = "0.5em", align = "center")
public class HandleSurface extends ContainerSurface {

    // ==================== Declarative Structure ====================

    @Scene.Embed(bind = "icon")
    @Scene.If("icon != null")
    static class Icon {}

    @Scene.Text(bind = "label", style = {"handle-label"})
    @Scene.If("label != null")
    static class Label {}

    @Scene.Text(bind = "subtitle", style = {"handle-subtitle", "muted"})
    @Scene.If("subtitle != null")
    static class Subtitle {}

    @Scene.Container(direction = Scene.Direction.HORIZONTAL, style = {"handle-badges"})
    @Scene.If("hasBadges")
    static class Badges {
        @Scene.Repeat(bind = "badges")
        @Scene.Embed(bind = "$item")
        static class Badge {}
    }

    /**
     * The icon for this handle.
     */
    @Canon(order = 20)
    private ImageSurface icon;

    /**
     * The text label.
     */
    @Canon(order = 21)
    private String label;

    /**
     * Optional secondary text (count, type, status).
     */
    @Canon(order = 22)
    private String subtitle;

    /**
     * Optional badges (status indicators, tags, etc.).
     */
    @Canon(order = 23)
    private List<ImageSurface> badges;

    public HandleSurface() {
        // Handles are horizontal by default
        this.direction = Scene.Direction.HORIZONTAL;
    }

    // ==================== Factory Methods ====================

    /**
     * Create an empty handle (set icon/label separately).
     */
    public static HandleSurface create() {
        return new HandleSurface();
    }

    /**
     * Create a handle with glyph and label.
     */
    public static HandleSurface of(String glyph, String label) {
        HandleSurface h = new HandleSurface();
        h.icon = ImageSurface.of(glyph);
        h.label = label;
        return h;
    }

    /**
     * Create a handle with icon and label.
     */
    public static HandleSurface of(ImageSurface icon, String label) {
        HandleSurface h = new HandleSurface();
        h.icon = icon;
        h.label = label;
        return h;
    }

    /**
     * Create a handle from just a label (no icon).
     */
    public static HandleSurface ofLabel(String label) {
        HandleSurface h = new HandleSurface();
        h.label = label;
        return h;
    }

    /**
     * Create a handle for tree node content — icon + label, compact.
     */
    public static HandleSurface forNode(String icon, String label) {
        HandleSurface h = of(icon, label);
        h.style("node-handle");
        return h;
    }

    /**
     * Create a handle for tree node content with a custom ImageSurface icon.
     */
    public static HandleSurface forNode(ImageSurface icon, String label) {
        HandleSurface h = of(icon, label);
        h.style("node-handle");
        return h;
    }

    /**
     * Create a handle for header display — icon + label, prominent.
     */
    public static HandleSurface forHeader(String icon, String label) {
        HandleSurface h = new HandleSurface();
        h.icon = ImageSurface.of(icon).size("large").circle().backgroundColor("#3C3C4E");
        h.label = label;
        h.style("header-handle");
        return h;
    }

    /**
     * Create a handle for header display with type name subtitle.
     */
    public static HandleSurface forHeader(String icon, String label, String typeName) {
        HandleSurface h = new HandleSurface();
        h.icon = ImageSurface.of(icon).size("large").circle().backgroundColor("#3C3C4E");
        h.label = label;
        h.style("header-handle");
        if (typeName != null) h.subtitle(typeName);
        return h;
    }

    /**
     * Create a handle for prompt display — icon + label + "> ".
     */
    public static HandleSurface forPrompt(String icon, String label) {
        HandleSurface h = of(icon, label + "> ");
        h.style("prompt-handle");
        return h;
    }

    // ==================== Fluent Setters ====================

    public HandleSurface icon(ImageSurface icon) {
        this.icon = icon;
        return this;
    }

    public HandleSurface icon(String glyph) {
        this.icon = ImageSurface.of(glyph);
        return this;
    }

    public HandleSurface label(String label) {
        this.label = label;
        return this;
    }

    public HandleSurface subtitle(String subtitle) {
        this.subtitle = subtitle;
        return this;
    }

    public HandleSurface badges(List<ImageSurface> badges) {
        this.badges = new ArrayList<>(badges);
        return this;
    }

    /**
     * Add a badge by glyph. No-op if glyph is null.
     */
    public HandleSurface badge(String glyph) {
        if (glyph == null) return this;
        if (this.badges == null) {
            this.badges = new ArrayList<>();
        }
        this.badges.add(ImageSurface.of(glyph).small());
        return this;
    }

    /**
     * Add a badge.
     */
    public HandleSurface badge(ImageSurface badge) {
        if (this.badges == null) {
            this.badges = new ArrayList<>();
        }
        this.badges.add(badge);
        return this;
    }

    /**
     * Check if this handle has badges (used by @Scene.If condition).
     */
    public boolean hasBadges() {
        return badges != null && !badges.isEmpty();
    }

    // ==================== Rendering ====================

    /**
     * Render via SceneCompiler's structural compilation.
     *
     * <p>Bypasses ContainerSurface.render() (which iterates an empty children list)
     * and instead delegates to SceneCompiler, which compiles the nested class
     * annotations (Icon, Label, Badges) against this instance's fields.
     */
    @Override
    public void render(SurfaceRenderer out) {
        emitCommonProperties(out);
        SceneCompiler.render(this, out);
    }

}
