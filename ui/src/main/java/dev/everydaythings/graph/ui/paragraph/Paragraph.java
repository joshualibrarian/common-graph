package dev.everydaythings.graph.ui.paragraph;

import java.util.List;

/**
 * A laid-out paragraph of styled text.
 *
 * <p>Paragraph is the core text layout abstraction, implemented by both
 * the Skia backend (wrapping Skia's Paragraph API) and the MSDF backend
 * (custom layout using ICU4J line breaking and MSDF glyph metrics).
 *
 * <p>Usage:
 * <pre>{@code
 * Paragraph para = factory.fromText("Hello world", List.of(), maxWidth);
 * // para is already laid out by fromText
 * para.paint(canvas, x, y);
 * para.close();
 * }</pre>
 *
 * <p>Implementations must be closed after use to release native resources.
 *
 * @see ParagraphBuilder
 * @see ParagraphFactory
 */
public interface Paragraph extends AutoCloseable {

    /** Layout the paragraph within maxWidth. Must call before other methods. */
    void layout(float maxWidth);

    /** Total width after layout. */
    float width();

    /** Total height after layout. */
    float height();

    /** Maximum intrinsic width (longest line without wrapping). */
    float maxIntrinsicWidth();

    /** Per-line metrics after layout. */
    List<LineMetrics> lines();

    record LineMetrics(int startIndex, int endIndex, float x, float y,
                       float width, float ascent, float descent) {}

    /** Hit test: pixel coordinate → character index. */
    int glyphPositionAt(float x, float y);

    /** Selection: character range → visual rectangles. */
    List<Rect> rectsForRange(int start, int end);

    record Rect(float x, float y, float width, float height) {}

    /** Paint to the active backend at (x, y). */
    void paint(Object canvas, float x, float y);

    /** Release resources. */
    @Override
    void close();
}
