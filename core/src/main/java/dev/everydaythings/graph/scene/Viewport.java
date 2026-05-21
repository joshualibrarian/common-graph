package dev.everydaythings.graph.scene;

/**
 * Viewport — the dimensions of a {@link Painter}'s surface.  Carries the
 * width and height in the painter's native units (character cells for TUI,
 * logical pixels for graphical painters), plus a device-pixel-ratio for
 * graphical painters that drive high-DPI displays.
 *
 * <p>The presenter resolves dimensional units (percentages, font-relative
 * em, viewport-relative vw/vh) against this value when laying out the
 * positioned tree.
 *
 * <p>Immutable.  A surface resize produces a new Viewport; the painter
 * exposes the current one via {@link Painter#viewport()}.
 */
public final class Viewport {

    private final float width;
    private final float height;
    private final float devicePixelRatio;

    /**
     * Construct a viewport.  Width and height are in the painter's native
     * units.  Device-pixel-ratio is 1.0 for terminals and 1x displays; 2.0
     * for retina; 1.5 / 1.25 etc. for fractional-scale displays.
     */
    public Viewport(float width, float height, float devicePixelRatio) {
        if (width < 0 || height < 0 || devicePixelRatio <= 0) {
            throw new IllegalArgumentException(
                    "Viewport requires non-negative dimensions and positive DPR; got "
                            + width + "x" + height + " @" + devicePixelRatio);
        }
        this.width = width;
        this.height = height;
        this.devicePixelRatio = devicePixelRatio;
    }

    /** Construct a viewport at DPR 1.0 (terminals, standard-density displays). */
    public Viewport(float width, float height) {
        this(width, height, 1.0f);
    }

    public float width()            { return width; }
    public float height()           { return height; }
    public float devicePixelRatio() { return devicePixelRatio; }

    @Override
    public String toString() {
        return "Viewport[" + width + "x" + height
                + (devicePixelRatio == 1.0f ? "" : " @" + devicePixelRatio + "x") + "]";
    }
}
