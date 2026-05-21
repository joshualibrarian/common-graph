package dev.everydaythings.graph.ui.tui;

import dev.everydaythings.graph.runtime.session.Session;
import dev.everydaythings.graph.scene.Painter;
import dev.everydaythings.graph.ui.AbstractSurface;

import java.time.Duration;

/**
 * TuiSurface — terminal-backed {@link dev.everydaythings.graph.ui.Surface Surface}.
 *
 * <p>All the surface machinery (window list, lifecycle, render loop,
 * composition) lives in {@link AbstractSurface}.  This class just supplies
 * the TUI-specific bits: it builds a {@link TuiPainter} and ticks at a
 * conservative 250ms cadence to avoid terminal flicker.
 */
public final class TuiSurface extends AbstractSurface {

    private static final Duration TUI_CADENCE = Duration.ofMillis(250);

    public TuiSurface(Session session) {
        super(session);
    }

    @Override
    protected Painter createPainter() {
        return new TuiPainter();
    }

    @Override
    protected Duration cadence() {
        return TUI_CADENCE;
    }

    @Override
    protected String renderThreadName() {
        return "tui-surface";
    }
}
