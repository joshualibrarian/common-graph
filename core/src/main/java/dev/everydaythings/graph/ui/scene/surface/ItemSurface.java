package dev.everydaythings.graph.ui.scene.surface;

import dev.everydaythings.graph.item.Item;
import dev.everydaythings.graph.ui.scene.Scene;
import dev.everydaythings.graph.ui.scene.SceneMode;
import dev.everydaythings.graph.ui.scene.SceneSchema;
import dev.everydaythings.graph.ui.scene.surface.primitive.ImageSurface;
import dev.everydaythings.graph.ui.scene.surface.primitive.TextSurface;

import java.util.ArrayList;
import java.util.List;

/**
 * Surface for presenting an Item at various scales.
 *
 * <p>ItemSurface extracts presentation data from an Item and builds
 * a CBOR-serializable surface tree. It handles the complexity of
 * Item presentation so that @Scene annotations on Item itself
 * don't get cluttered.
 *
 * <p>Usage:
 * <pre>{@code
 * // Build from an Item
 * ItemSurface surface = ItemSurface.from(item, SceneMode.FULL);
 *
 * // Render to any platform
 * surface.render(skiaOutput); // Skia
 * surface.render(cliOutput);  // CLI
 * }</pre>
 *
 * <p>Scales:
 * <ul>
 *   <li><b>CHIP</b> - icon + token (inline reference)</li>
 *   <li><b>COMPACT</b> - icon + token + subtitle (list item)</li>
 *   <li><b>PREVIEW</b> - header + key properties</li>
 *   <li><b>FULL</b> - header + all content (expandable tree)</li>
 * </ul>
 *
 * <h2>Declarative Structure</h2>
 * <pre>
 * CHIP:    [icon] name
 * COMPACT: [icon] name
 * PREVIEW: ┌─────────────────┐    FULL: ┌─────────────────┐
 *          │ [icon] name     │          │ [icon] name     │
 *          │ subtitle        │          │                 │
 *          │ content...      │          │ content...      │
 *          └─────────────────┘          └─────────────────┘
 * </pre>
 */
@Scene.Container(direction = Scene.Direction.VERTICAL)
public class ItemSurface extends SceneSchema<Item> {

    // ==================== Declarative Structure ====================
    // Mode-specific rendering requires procedural logic for now

    // CHIP mode structure
    @Scene.Container(direction = Scene.Direction.HORIZONTAL, style = {"chip"})
    @Scene.If("mode == CHIP")
    static class ChipMode {
        @Scene.Image(bind = "icon")
        @Scene.If("icon != null")
        static class Icon {}

        @Scene.Text(bind = "name", style = {"chip-label"})
        static class Name {}
    }

    // COMPACT mode structure
    @Scene.Container(direction = Scene.Direction.HORIZONTAL, style = {"compact"})
    @Scene.If("mode == COMPACT")
    static class CompactMode {
        @Scene.Image(bind = "icon")
        @Scene.If("icon != null")
        static class Icon {}

        @Scene.Text(bind = "name", style = {"name"})
        static class Name {}
    }

    // PREVIEW/FULL mode - header + content
    @Scene.Container(direction = Scene.Direction.VERTICAL)
    @Scene.If("mode == PREVIEW || mode == FULL")
    static class FullMode {
        @Scene.Container(direction = Scene.Direction.HORIZONTAL, style = {"header"})
        static class Header {
            @Scene.Image(bind = "icon")
            @Scene.If("icon != null")
            static class Icon {}

            @Scene.Text(bind = "name", style = {"name", "heading"})
            static class Name {}

            @Scene.Container(direction = Scene.Direction.HORIZONTAL, style = {"badge", "type-badge", "muted"})
            @Scene.If("typeName != null && !typeName.isEmpty()")
            static class TypeBadge {
                @Scene.Text(bind = "typeName")
                static class Type {}
            }
        }

        @Scene.Text(bind = "subtitle", style = {"subtitle", "muted"})
        @Scene.If("mode != FULL && subtitle != null && !subtitle.isEmpty()")
        static class Subtitle {}

        // TODO: @Scene.Repeat(bind = "content") for children
    }

    // ==================================================================================
    // Presentation Data (CBOR-serializable)
    // ==================================================================================

    @Canon(order = 10)
    private ImageSurface icon;

    @Canon(order = 11)
    private String name;

    @Canon(order = 12)
    private String typeName;

    @Canon(order = 13)
    private String subtitle;

    @Canon(order = 14)
    private SceneMode mode = SceneMode.FULL;

    @Canon(order = 15)
    private List<SurfaceSchema> content = new ArrayList<>();

    // ==================================================================================
    // Construction
    // ==================================================================================

    public ItemSurface() {}

    /**
     * Build an ItemSurface from an Item at the given mode.
     *
     * <p>This extracts presentation data from the Item and builds
     * appropriate content based on the mode.
     */
    public static ItemSurface from(Item item, SceneMode mode) {
        ItemSurface surface = new ItemSurface();
        String emoji = item.emoji();
        surface.icon = emoji != null ? ImageSurface.of(emoji) : null;
        surface.name = item.displayToken();
        surface.typeName = item.colorCategory();
        surface.subtitle = item.displaySubtitle();
        surface.mode = mode;

        // Build content based on mode
        switch (mode) {
            case CHIP -> surface.buildChipContent(item);
            case COMPACT -> surface.buildCompactContent(item);
            case PREVIEW -> surface.buildPreviewContent(item);
            case FULL -> surface.buildFullContent(item);
        }

        // Items are clickable - navigate on click (except FULL mode which is already the main view)
        if (mode != SceneMode.FULL && item.iid() != null) {
            surface.onClick("navigate", "iid:" + item.iid().encodeText());
        }

        return surface;
    }

    // ==================================================================================
    // Content Builders
    // ==================================================================================

    /**
     * CHIP mode - minimal inline: just icon + token.
     */
    private void buildChipContent(Item item) {
        // No extra content for chips - just header info
    }

    /**
     * COMPACT mode - list item: icon + token + subtitle.
     */
    private void buildCompactContent(Item item) {
        // Subtitle is already captured in the header
        // Could add badges or status indicators here
    }

    /**
     * PREVIEW mode - card: header + key properties.
     */
    private void buildPreviewContent(Item item) {
        // Get a few key properties
        String detail = item.displayDetail();
        if (detail != null && !detail.isEmpty()) {
            content.add(TextSurface.of(detail).style("detail", "muted"));
        }
    }

    /**
     * FULL mode - complete view with all content.
     */
    private void buildFullContent(Item item) {
        // Children and components are the real content for Items
        buildItemChildren(item);
    }

    /**
     * Build child surfaces for an Item's components.
     *
     * <p>An Item's children are its tables (content, mounts, relations).
     */
    private void buildItemChildren(Item item) {
        ListSurface childList = new ListSurface();

        // Add tables if non-empty (render them at COMPACT mode)
        if (item.content().isExpandable()) {
            childList.add(createTableSurface(item.content().emoji(), item.content().displayToken(),
                    item.content().displaySubtitle(), item.content().colorCategory()));
        }

        if (!childList.items().isEmpty()) {
            content.add(childList);
        }
    }

    /**
     * Create a simple surface for a table (content, actions, etc.).
     */
    private static SurfaceSchema createTableSurface(String emoji, String name, String subtitle, String typeName) {
        ItemSurface surface = new ItemSurface();
        surface.icon = emoji != null ? ImageSurface.of(emoji) : null;
        surface.name = name;
        surface.typeName = typeName;
        surface.subtitle = subtitle;
        surface.mode = SceneMode.COMPACT;
        return surface;
    }

    // ==================================================================================
    // Accessors
    // ==================================================================================

    public ImageSurface icon() { return icon; }
    public String name() { return name; }
    public String typeName() { return typeName; }
    public String subtitle() { return subtitle; }
    public SceneMode mode() { return mode; }
    public List<SurfaceSchema> content() { return content; }

    // ==================================================================================
    // Rendering
    // ==================================================================================
    // TODO: Remove procedural render() after @Scene.Repeat and mode-switching work

    @Override
    public void render(SurfaceRenderer out) {
        emitCommonProperties(out);

        switch (mode) {
            case CHIP -> renderChip(out);
            case COMPACT -> renderCompact(out);
            case PREVIEW -> renderPreview(out);
            case FULL -> renderFull(out);
        }
    }

    private void renderChip(SurfaceRenderer out) {
        // Chip: just icon + name in a tight inline format
        List<String> chipStyles = new ArrayList<>(style());
        chipStyles.add("chip");

        out.beginBox(Scene.Direction.HORIZONTAL, chipStyles);
        if (icon != null) {
            icon.render(out);
        }
        if (name != null) {
            out.text(name, List.of("chip-label"));
        }
        out.endBox();
    }

    private void renderCompact(SurfaceRenderer out) {
        // Compact: single line with icon + name
        List<String> compactStyles = new ArrayList<>(style());
        compactStyles.add("compact");

        out.beginBox(Scene.Direction.HORIZONTAL, compactStyles);
        if (icon != null) {
            icon.render(out);
        }
        if (name != null) {
            out.text(name, List.of("name"));
        }
        out.endBox();
    }

    private void renderPreview(SurfaceRenderer out) {
        // Preview: vertical box with header + content
        out.beginBox(Scene.Direction.VERTICAL, style());

        // Header row
        renderHeader(out);

        // Content
        for (SurfaceSchema child : content) {
            child.render(out);
        }

        out.endBox();
    }

    private void renderFull(SurfaceRenderer out) {
        // Full: complete vertical layout
        out.beginBox(Scene.Direction.VERTICAL, style());

        // Header row
        renderHeader(out);

        // All content
        for (SurfaceSchema child : content) {
            child.render(out);
        }

        out.endBox();
    }

    private void renderHeader(SurfaceRenderer out) {
        out.beginBox(Scene.Direction.HORIZONTAL, List.of("header"));

        // Icon
        if (icon != null) {
            icon.render(out);
        }

        // Name
        out.text(name != null ? name : "", List.of("name", "heading"));

        // Type badge - but only if different from the name
        if (typeName != null && !typeName.isEmpty() && !typeName.equalsIgnoreCase(name)) {
            out.beginBox(Scene.Direction.HORIZONTAL, List.of("badge", "type-badge", "muted"));
            out.text(typeName, List.of());
            out.endBox();
        }

        out.endBox();

        // Subtitle on its own line - but only for non-FULL modes
        if (mode != SceneMode.FULL && subtitle != null && !subtitle.isEmpty()) {
            out.text(subtitle, List.of("subtitle", "muted"));
        }
    }
}
