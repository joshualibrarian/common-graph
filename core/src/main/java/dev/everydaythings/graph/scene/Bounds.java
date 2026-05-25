package dev.everydaythings.graph.scene;

/**
 * Bounds — the placement of a {@link SceneNode} in its painter's native
 * unit space.  Top-left origin: {@code (x, y)} is the upper-left corner,
 * {@code width} and {@code height} extend rightward and downward.
 *
 * <p>Units are painter-specific: character cells for TUI painters, logical
 * pixels for graphical painters (same convention as {@link Viewport} and
 * {@link FontMetrics}).  The presenter solves layout in the painter's
 * native units; bounds carried by the positioned tree are already in the
 * units the painter consumes.
 *
 * <p>Immutable value.  Layout assigns a fresh Bounds to each node; if a
 * subsequent stage needs a different placement, it builds a new one
 * rather than mutating.
 */
public final class Bounds {

    /** All-zero bounds — convenient default before layout runs. */
    public static final Bounds ZERO = new Bounds(0, 0, 0, 0);

    private final float x;
    private final float y;
    private final float width;
    private final float height;

    public Bounds(float x, float y, float width, float height) {
        if (width < 0 || height < 0) {
            throw new IllegalArgumentException(
                    "Bounds requires non-negative width and height; got "
                            + width + "x" + height);
        }
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
    }

    public float x()      { return x; }
    public float y()      { return y; }
    public float width()  { return width; }
    public float height() { return height; }

    /** Right edge: {@code x + width}.  Convenience for layout math. */
    public float right()  { return x + width; }

    /** Bottom edge: {@code y + height}.  Convenience for layout math. */
    public float bottom() { return y + height; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Bounds other)) return false;
        return Float.compare(other.x, x) == 0
                && Float.compare(other.y, y) == 0
                && Float.compare(other.width, width) == 0
                && Float.compare(other.height, height) == 0;
    }

    @Override
    public int hashCode() {
        int result = Float.hashCode(x);
        result = 31 * result + Float.hashCode(y);
        result = 31 * result + Float.hashCode(width);
        result = 31 * result + Float.hashCode(height);
        return result;
    }

    @Override
    public String toString() {
        return "Bounds[" + x + "," + y + " " + width + "x" + height + "]";
    }
}
