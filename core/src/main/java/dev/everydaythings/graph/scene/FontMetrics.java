package dev.everydaythings.graph.scene;

/**
 * FontMetrics — painter-specific text measurement.  The presenter calls
 * this during layout to size {@link SceneText} nodes before solving
 * placement.
 *
 * <p>Answers are in the painter's native units (character cells for TUI,
 * logical pixels for graphical painters).
 *
 * <p>Minimal first-cut surface.  Real implementations need font family,
 * size, style, weight, letter-spacing, line-height — those land alongside
 * actual painter implementations as they prove what shape the measurement
 * call really wants.
 */
public interface FontMetrics {

    /**
     * Width of a single line of text in the painter's native units, at the
     * given font size.  No wrapping; returns the unwrapped width.
     */
    float measureWidth(String text, float fontSize);

    /**
     * Height of a line at the given font size, in the painter's native
     * units.  Implementations may return the font's natural line height,
     * or a multiple thereof if the painter applies leading.
     */
    float lineHeight(float fontSize);
}
