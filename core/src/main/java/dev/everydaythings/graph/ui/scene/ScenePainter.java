package dev.everydaythings.graph.ui.scene;

/**
 * Paints a laid-out SceneNode tree to produce output.
 *
 * <p>Five implementations, one interface:
 * <ul>
 *   <li>{@code AnsiSurfacePainter} — ANSI terminal text</li>
 *   <li>{@code PlainTextSurfacePainter} — plain text</li>
 *   <li>{@code SkiaSurfacePainter} — Skia canvas (CPU 2D)</li>
 *   <li>{@code FilamentSurfacePainter} — Filament ortho scene (GPU 2D)</li>
 *   <li>{@code FilamentSpatialPainter} — Filament perspective scene (3D)</li>
 * </ul>
 *
 * <p>The painter receives a fully resolved, laid-out SceneNode tree
 * (post-{@link SceneResolver} and ScenePresenter) and traverses it to
 * produce output. Each node carries all representations (text, 2D, 3D);
 * the painter selects which it can handle.
 */
public interface ScenePainter {

    /**
     * Paint the given SceneNode tree.
     *
     * <p>The tree must have been resolved and laid out before painting.
     * Bounds, resolved colors, and resolved font metrics are read
     * directly from each node.
     */
    void paint(SceneNode tree);

    /** Clear any output from a previous paint call. */
    void clear();
}
