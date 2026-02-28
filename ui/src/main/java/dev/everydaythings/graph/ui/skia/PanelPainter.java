package dev.everydaythings.graph.ui.skia;

import dev.everydaythings.graph.ui.scene.RenderContext;

/**
 * Paints a laid-out {@link LayoutNode} tree as a 2D panel in 3D space.
 *
 * <p>Two implementations:
 * <ul>
 *   <li>{@code FilamentPanelPainter} — direct MSDF geometry via {@code FilamentSurfacePainter}</li>
 *   <li>{@code SkiaPanelPainter} — Skia raster to texture on a quad</li>
 * </ul>
 *
 * <p>The pipeline is identical to 2D rendering: Surface → {@link SkiaSurfaceRenderer} →
 * LayoutNode tree → {@link LayoutEngine} → this painter. The only difference is the
 * output target (3D plane instead of screen).
 */
public interface PanelPainter {

    /**
     * Build a RenderContext with font metrics matching this painter's backend.
     *
     * @param pixelWidth  panel width in pixels
     * @param pixelHeight panel height in pixels
     */
    RenderContext buildContext(float pixelWidth, float pixelHeight);

    /**
     * Return the text measurer matching this painter's font backend.
     */
    LayoutEngine.TextMeasurer textMeasurer();

    /**
     * Paint a laid-out tree as a 3D panel at the given world transform.
     *
     * @param tree           root of the laid-out LayoutNode tree
     * @param pixelW         panel width in pixels
     * @param pixelH         panel height in pixels
     * @param panelWidthM    panel width in meters (world units)
     * @param panelHeightM   panel height in meters (world units)
     * @param worldTransform 4x4 column-major matrix from the spatial renderer's transform stack
     */
    void paintPanel(LayoutNode.BoxNode tree,
                    float pixelW, float pixelH,
                    float panelWidthM, float panelHeightM,
                    float[] worldTransform);

    /**
     * Remove all entities/resources created by the previous paintPanel call.
     */
    void clear();

    /**
     * Destroy all resources including shared materials.
     */
    void destroy();
}
