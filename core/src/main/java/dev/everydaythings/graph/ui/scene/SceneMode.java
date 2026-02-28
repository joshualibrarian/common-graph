package dev.everydaythings.graph.ui.scene;

/**
 * Rendering mode for Surfaces.
 *
 * <p>Controls how much detail is shown when rendering an item's Surface.
 * Used when embedding items within other surfaces (e.g., a list of members
 * in a project surface).
 */
public enum SceneMode {

    /**
     * Complete surface - show all regions and fields.
     */
    FULL,

    /**
     * Compact mode - icon + title, typically one line.
     */
    COMPACT,

    /**
     * Chip mode - minimal inline token, just enough to identify.
     */
    CHIP,

    /**
     * Preview mode - hover/popup with more detail than chip but less than full.
     */
    PREVIEW
}
