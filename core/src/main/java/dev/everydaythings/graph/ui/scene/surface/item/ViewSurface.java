package dev.everydaythings.graph.ui.scene.surface.item;

import dev.everydaythings.graph.frame.ViewConfig;
import dev.everydaythings.graph.item.Item;
import dev.everydaythings.graph.ui.scene.surface.ButtonSurface;
import dev.everydaythings.graph.ui.scene.surface.HandleSurface;
import dev.everydaythings.graph.ui.scene.surface.SurfaceRenderer;
import dev.everydaythings.graph.ui.scene.surface.SurfaceSchema;
import dev.everydaythings.graph.ui.scene.surface.primitive.ContainerSurface;
import dev.everydaythings.graph.ui.scene.surface.primitive.TextSurface;

/**
 * Minimal chrome wrapper for a viewed item.
 *
 * <p>ViewSurface wraps content in a standard chrome frame:
 * <pre>
 * ┌─────────────────────────────────────────┐
 * │ [icon+name]        [pres|inspect] [×]   │  ← title bar (handle)
 * ├─────────────────────────────────────────┤
 * │                                         │
 * │           inner content                 │  ← plugged in
 * │                                         │
 * ├─────────────────────────────────────────┤
 * │ [prompt]                                │  ← always accessible
 * └─────────────────────────────────────────┘
 * </pre>
 *
 * <p>This is NOT an ItemSurface — it's an independent wrapper that provides
 * consistent chrome around any content. The content and prompt are supplied
 * by the caller (typically ItemModel via Session).
 */
public class ViewSurface extends SurfaceSchema<Void> {

    private HandleSurface handle;
    private SurfaceSchema<?> content;
    private SurfaceSchema<?> prompt;
    private ViewConfig.ViewMode mode;
    private boolean dirty;

    public ViewSurface() {}

    /**
     * Create a ViewSurface wrapping item content.
     *
     * @param item    the viewed item (for handle display)
     * @param content the inner content surface (ItemSurface or InspectSurface)
     * @param prompt  the prompt surface (always accessible)
     * @param config  the current view configuration
     */
    public static ViewSurface of(Item item, SurfaceSchema<?> content,
                                  SurfaceSchema<?> prompt, ViewConfig config) {
        ViewSurface view = new ViewSurface();
        String emoji = item.emoji();
        String name = item.displayToken();
        view.handle = HandleSurface.of(emoji != null ? emoji : "", name);
        view.content = content;
        view.prompt = prompt;
        view.mode = config != null ? config.mode() : ViewConfig.ViewMode.PRESENTATION;
        view.dirty = item.dirty();
        return view;
    }

    // ==================== Accessors ====================

    public HandleSurface handle() { return handle; }
    public SurfaceSchema<?> content() { return content; }
    public SurfaceSchema<?> prompt() { return prompt; }
    public ViewConfig.ViewMode mode() { return mode; }
    public boolean dirty() { return dirty; }

    // ==================== Rendering ====================

    @Override
    public void render(SurfaceRenderer out) {
        emitCommonProperties(out);
        buildTree().render(out);
    }

    /**
     * Build the surface tree for this view.
     *
     * <p>Constructs proper ContainerSurfaces with gap and children so that
     * renderers (TUI, Skia, Filament) handle spacing correctly.
     */
    private ContainerSurface buildTree() {
        ContainerSurface root = ContainerSurface.vertical().style("view-surface");

        // Title bar
        root.add(buildTitleBar());

        // Content area
        if (content != null) {
            root.add(content);
        }

        // Prompt
        if (prompt != null) {
            root.add(prompt);
        }

        return root;
    }

    private ContainerSurface buildTitleBar() {
        ContainerSurface titleBar = ContainerSurface.horizontal()
                .gap("0.5em")
                .style("view-title-bar");

        // Handle (icon + name)
        if (handle != null) {
            titleBar.add(handle);
        }

        // Mode toggle
        boolean isPresentation = mode == ViewConfig.ViewMode.PRESENTATION;
        String modeLabel = isPresentation ? "presentation" : "inspect";
        ButtonSurface modeToggle = ButtonSurface.of(modeLabel, "viewMode:toggle")
                .ghost()
                .style("view-mode-toggle");
        titleBar.add(modeToggle);

        // Dirty indicator
        if (dirty) {
            titleBar.add(TextSurface.of("*").style("dirty-indicator"));
        }

        // Close button
        ButtonSurface closeBtn = ButtonSurface.of("×", "viewClose")
                .ghost()
                .style("view-close");
        titleBar.add(closeBtn);

        return titleBar;
    }
}
