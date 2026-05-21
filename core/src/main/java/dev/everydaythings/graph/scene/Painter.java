package dev.everydaythings.graph.scene;

/**
 * Painter — the final leg of the render pipeline.  Takes a fully-positioned
 * scene tree and emits whatever its target medium understands: terminal
 * glyphs, 2D pixels, or GPU draw calls in 3D space.
 *
 * <p>The Painter contract is intentionally minimal:
 *
 * <ul>
 *   <li>{@link #paint(SceneNode)} — draw a positioned tree.  Every node has
 *       fully resolved properties (pixel-level dimensions, integer colors,
 *       no unresolved expressions) at this point.  Painters don't
 *       interpret strings or evaluate bindings; they just consume the
 *       three primitives ({@code Container} / {@code Text} / {@code Body})
 *       and produce output at their {@link #fidelity()}.</li>
 *
 *   <li>{@link #viewport()} — the dimensions the {@code Presenter} resolves
 *       layout against.  Cell-count for terminal painters, pixel-count for
 *       graphical painters.</li>
 *
 *   <li>{@link #fontMetrics()} — text measurement for the layout pass.  The
 *       presenter measures text width and height before it can solve the
 *       layout, and the answer is painter-specific (a TUI cell is fixed
 *       width; a graphical painter has glyph metrics that vary per font).</li>
 *
 *   <li>{@link #fidelity()} — what the painter draws to.  The presenter
 *       uses this to pick the right body representation when a {@code
 *       SceneBody} node carries multiple (a {@code Model} for spatial, a
 *       {@code Glyph} for text, etc.).</li>
 * </ul>
 *
 * <p>Input events (keys, mouse, focus, resize) are not part of this initial
 * contract.  They land in a follow-up pass once the rendering loop is
 * proven end-to-end.
 *
 * <p>One painter per window / surface / terminal.  Painters are
 * {@link AutoCloseable}; closing tears down the surface and stops the
 * render loop.
 *
 * <p>See {@code docs/scene.md} (the pipeline section) for the broader
 * picture: declared → resolver → resolved → presenter → positioned →
 * painter.
 */
public interface Painter extends AutoCloseable {

    /**
     * Draw the given positioned tree to this painter's surface.  Every
     * binding on every node carries fully resolved values; no string
     * parsing, no unit conversion, no expression evaluation is done here.
     *
     * <p>Called once per frame.  Painters that are vsync-driven may
     * actually call this from their own render loop in response to a
     * scene-update notification.
     */
    void paint(SceneNode positionedTree);

    /**
     * Current surface dimensions.  Units are painter-specific: character
     * cells for TUI painters, logical pixels for graphical painters, world
     * units for spatial painters.  The presenter resolves dimensional
     * units against this value.
     */
    Viewport viewport();

    /**
     * Painter-specific text metrics.  The presenter calls this during
     * layout to measure text nodes before solving placement.  Implementations
     * answer in the painter's native units (cells for TUI, pixels for
     * graphical).
     */
    FontMetrics fontMetrics();

    /**
     * What this painter draws to.  The presenter consults this to pick
     * the appropriate body representation when a {@link SceneBody} node
     * carries representations at multiple fidelities.
     */
    Fidelity fidelity();

    @Override
    void close();

    /**
     * Fidelity coarse-categorization — what kind of surface the painter
     * paints onto.  Finer-grained capabilities (color depth, anti-aliasing,
     * shader support) belong on more specific painter sub-types when needed.
     */
    enum Fidelity {
        /** Terminal cells.  TUI painter.  Body picks glyph or alt text. */
        TEXT,
        /** 2D pixel surface.  Skia / Filament Surface.  Body picks image or shape. */
        RASTER_2D,
        /** 3D world space.  Filament Spatial.  Body picks model, falls back to image. */
        SPATIAL_3D
    }
}
