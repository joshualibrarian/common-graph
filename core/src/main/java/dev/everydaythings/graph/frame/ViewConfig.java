package dev.everydaythings.graph.frame;

import dev.everydaythings.graph.Canonical;
import dev.everydaythings.graph.Canonical.Canon;
import lombok.Builder;
import lombok.Getter;

/**
 * Configuration payload for ITEM_VIEW frames.
 *
 * <p>Stores view-mode state (presentation vs inspect), inspector sub-mode,
 * window position/size, and collapsed state. Stored in the CONFIG binding
 * of an ITEM_VIEW frame on a Session.
 *
 * @see dev.everydaythings.graph.language.ViewVocabulary.ItemView
 */
@Getter
@Builder
@Canonical.Canonization
public class ViewConfig implements Canonical {

    /**
     * Primary view mode — what the view content shows.
     */
    public enum ViewMode {
        /** Author's scene — the item's presentation surface. */
        PRESENTATION,
        /** Developer view — frame inspector. */
        INSPECT
    }

    /**
     * Inspector sub-mode — what the inspect pane shows.
     * Only relevant when {@link #mode} is {@link ViewMode#INSPECT}.
     */
    public enum InspectMode {
        /** Frame listing — predicates, bindings, config editors. */
        FRAMES,
        /** Version history — VID DAG, commit/checkout. */
        VERSIONS
    }

    /**
     * Rendering backend for this view's window.
     */
    public enum RendererType {
        /** Skia 2D rendering (OpenGL). */
        SKIA,
        /** Filament 3D rendering (Metal/Vulkan). */
        FILAMENT
    }

    @Canon(order = 0) private ViewMode mode;
    @Canon(order = 1) private InspectMode inspectMode;
    @Canon(order = 2) private int x;
    @Canon(order = 3) private int y;
    @Canon(order = 4) private int width;
    @Canon(order = 5) private int height;
    @Canon(order = 6) private boolean collapsed;
    @Canon(order = 7) private RendererType renderer;

    /** No-arg constructor for Canonical decode. */
    @SuppressWarnings("unused")
    private ViewConfig() {}

    private ViewConfig(ViewMode mode, InspectMode inspectMode,
                       int x, int y, int width, int height, boolean collapsed,
                       RendererType renderer) {
        this.mode = mode;
        this.inspectMode = inspectMode;
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        this.collapsed = collapsed;
        this.renderer = renderer;
    }

    /**
     * Default config — presentation mode, frames inspect sub-mode.
     */
    public static ViewConfig defaults() {
        return ViewConfig.builder()
                .mode(ViewMode.PRESENTATION)
                .inspectMode(InspectMode.FRAMES)
                .renderer(RendererType.FILAMENT)
                .build();
    }

    /**
     * Return a copy with the given view mode.
     */
    public ViewConfig withMode(ViewMode mode) {
        return ViewConfig.builder()
                .mode(mode)
                .inspectMode(this.inspectMode)
                .x(this.x).y(this.y)
                .width(this.width).height(this.height)
                .collapsed(this.collapsed)
                .renderer(this.renderer)
                .build();
    }

    /**
     * Return a copy with the given inspect mode.
     */
    public ViewConfig withInspectMode(InspectMode inspectMode) {
        return ViewConfig.builder()
                .mode(this.mode)
                .inspectMode(inspectMode)
                .x(this.x).y(this.y)
                .width(this.width).height(this.height)
                .collapsed(this.collapsed)
                .renderer(this.renderer)
                .build();
    }

    /**
     * Return a copy with the given renderer type.
     */
    public ViewConfig withRenderer(RendererType renderer) {
        return ViewConfig.builder()
                .mode(this.mode)
                .inspectMode(this.inspectMode)
                .x(this.x).y(this.y)
                .width(this.width).height(this.height)
                .collapsed(this.collapsed)
                .renderer(renderer)
                .build();
    }
}
