package dev.everydaythings.graph.ui.skia;

import dev.everydaythings.graph.runtime.session.Session;
import dev.everydaythings.graph.ui.Surface;
import dev.everydaythings.graph.ui.SurfaceProvider;

/**
 * ServiceLoader registration for {@link SkiaSurface}.  Registered via
 * {@code META-INF/services/dev.everydaythings.graph.ui.SurfaceProvider}.
 *
 * <p>Responds to {@code --ui skia}.  Constructs a {@link SkiaSurface}
 * bound to the given session; the surface owns its own {@link SkiaPainter}
 * (and the GLFW window) internally.
 */
public final class SkiaSurfaceProvider implements SurfaceProvider {

    @Override
    public String uiMode() {
        return "skia";
    }

    @Override
    public Surface create(Session session) {
        return new SkiaSurface(session);
    }
}
