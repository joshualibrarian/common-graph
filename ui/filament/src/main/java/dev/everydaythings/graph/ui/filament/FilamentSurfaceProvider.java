package dev.everydaythings.graph.ui.filament;

import dev.everydaythings.graph.runtime.session.Session;
import dev.everydaythings.graph.ui.Surface;
import dev.everydaythings.graph.ui.SurfaceProvider;

/**
 * ServiceLoader registration for {@link FilamentSurface}.  Registered via
 * {@code META-INF/services/dev.everydaythings.graph.ui.SurfaceProvider}.
 *
 * <p>Responds to {@code --ui filament}.  Constructs a {@link FilamentSurface}
 * bound to the given session; the surface owns its own {@link FilamentPainter}
 * (and the GLFW window + Filament engine) internally.
 */
public final class FilamentSurfaceProvider implements SurfaceProvider {

    @Override
    public String uiMode() {
        return "filament";
    }

    @Override
    public Surface create(Session session) {
        return new FilamentSurface(session);
    }
}
