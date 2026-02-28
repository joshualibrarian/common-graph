package dev.everydaythings.graph.ui.filament;

import dev.everydaythings.graph.ui.skia.LayoutEngine;
import dev.everydaythings.graph.ui.skia.LayoutNode;

/**
 * Paints a laid-out {@link LayoutNode} tree as Filament geometry.
 *
 * <p>Two implementations:
 * <ul>
 *   <li>{@link FilamentSurfacePainter} — native MSDF text + colored quads</li>
 *   <li>{@link SkiaSurfacePainter} — Skia rasterization uploaded as a texture</li>
 * </ul>
 *
 * <p>Both are used by {@link FilamentPane} to paint 2D UI content in an
 * orthographic or perspective Filament view.
 */
public interface SurfacePainter {

    /**
     * Configure the coordinate mapping from pixel layout to Filament world units.
     *
     * @param widthPx    layout width in pixels
     * @param heightPx   layout height in pixels
     * @param worldWidth desired width in world units (e.g. 2.0 for ortho)
     * @param centerY    world Y position for the vertical center of the layout
     */
    void configureForLayout(float widthPx, float heightPx,
                            float worldWidth, float centerY);

    /**
     * Set an element ID whose subtree should be skipped during painting.
     * Used to avoid rendering 2D content in regions covered by 3D.
     *
     * @param id the element id to skip, or null to paint everything
     */
    void skipId(String id);

    /**
     * Paint the full layout tree as Filament geometry.
     *
     * @param root the root BoxNode (already laid out by LayoutEngine)
     * @param z    base Z depth in world space
     */
    void paint(LayoutNode.BoxNode root, float z);

    /**
     * Remove all entities created by the previous paint pass.
     * Call before each repaint to avoid accumulation.
     */
    void clear();

    /**
     * Destroy all resources including shared materials and caches.
     * Call when the painter is no longer needed.
     */
    void destroy();

    /**
     * Return the text measurer used by this painter's rendering backend.
     * Needed so the layout engine uses matching text metrics.
     */
    LayoutEngine.TextMeasurer textMeasurer();
}
