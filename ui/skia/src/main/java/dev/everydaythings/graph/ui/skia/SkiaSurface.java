package dev.everydaythings.graph.ui.skia;

import dev.everydaythings.graph.runtime.session.Session;
import dev.everydaythings.graph.scene.Painter;
import dev.everydaythings.graph.ui.AbstractSurface;

/**
 * SkiaSurface — RASTER_2D {@link dev.everydaythings.graph.ui.Surface
 * Surface} backed by a Skia-on-GLFW window.
 *
 * <p>All the surface machinery (window list, render loop, lifecycle,
 * composition) lives in {@link AbstractSurface}.  This class just
 * supplies the Skia-specific bits: it builds a {@link SkiaPainter}
 * (which opens the OS window during its constructor) and lets the
 * base's 60Hz cadence drive paints.
 */
public final class SkiaSurface extends AbstractSurface {

    public SkiaSurface(Session session) {
        super(session);
    }

    @Override
    protected Painter createPainter() {
        return new SkiaPainter();
    }

    @Override
    protected String renderThreadName() {
        return "skia-surface";
    }
}
