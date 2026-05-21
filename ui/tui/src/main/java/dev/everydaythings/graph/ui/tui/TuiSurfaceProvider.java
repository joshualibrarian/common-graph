package dev.everydaythings.graph.ui.tui;

import dev.everydaythings.graph.runtime.session.Session;
import dev.everydaythings.graph.ui.Surface;
import dev.everydaythings.graph.ui.SurfaceProvider;

/**
 * ServiceLoader registration for {@link TuiSurface}.  Registered via
 * {@code META-INF/services/dev.everydaythings.graph.ui.SurfaceProvider}.
 *
 * <p>Responds to {@code --ui tui}.  Constructs a {@link TuiSurface} bound
 * to the given session; the surface owns its own {@link TuiPainter}
 * internally.
 */
public final class TuiSurfaceProvider implements SurfaceProvider {

    @Override
    public String uiMode() {
        return "tui";
    }

    @Override
    public Surface create(Session session) {
        return new TuiSurface(session);
    }
}
