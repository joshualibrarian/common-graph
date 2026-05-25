package dev.everydaythings.graph.ui.filament;

import dev.everydaythings.graph.runtime.session.Session;
import dev.everydaythings.graph.scene.Painter;
import dev.everydaythings.graph.ui.AbstractSurface;

/**
 * FilamentSurface — RASTER_2D (and eventually SPATIAL_3D) {@link
 * dev.everydaythings.graph.ui.Surface Surface} backed by Filament on a
 * GLFW-managed X11 window.
 *
 * <p>All the surface machinery (window list, render loop, lifecycle,
 * composition) lives in {@link AbstractSurface}.  This class supplies a
 * {@link FilamentPainter} which opens the OS window during its
 * constructor and lets the base's 60Hz cadence drive paints.
 *
 * <p>Sibling to {@link dev.everydaythings.graph.ui.skia.SkiaSurface}.
 * Same shape; different painter.
 */
public final class FilamentSurface extends AbstractSurface {

    public FilamentSurface(Session session) {
        super(session);
    }

    @Override
    protected Painter createPainter() {
        return new FilamentPainter();
    }

    @Override
    protected String renderThreadName() {
        return "filament-surface";
    }
}
