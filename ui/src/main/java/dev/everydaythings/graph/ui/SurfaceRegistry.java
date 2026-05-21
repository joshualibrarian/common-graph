package dev.everydaythings.graph.ui;

import dev.everydaythings.graph.runtime.session.Session;
import lombok.extern.log4j.Log4j2;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.ServiceLoader;

/**
 * SurfaceRegistry — ServiceLoader-driven dispatch from a uiMode string
 * (e.g. {@code "tui"}, {@code "skia"}, {@code "filament"}, {@code "web"})
 * to a fresh {@link Surface} produced by the registered
 * {@link SurfaceProvider} for that mode.
 *
 * <p>Mirrors {@code TransportRegistry} in {@code :core}: a thin static
 * facade over {@link ServiceLoader}, with helpful errors when nothing
 * matches.  Each painter library ships its own provider and a
 * {@code META-INF/services} registration; the registry sees them via the
 * classpath.
 *
 * <p>Usage:
 * <pre>{@code
 * Surface surface = SurfaceRegistry.require(uiMode, session);
 * surface.open();
 * }</pre>
 */
@Log4j2
public final class SurfaceRegistry {

    private SurfaceRegistry() {}

    /**
     * Look up a provider for the given uiMode and build a {@link Surface}
     * bound to the given session.  Returns empty if no provider is
     * registered for that mode.
     *
     * <p>uiMode matching is case-insensitive; null or blank returns empty.
     */
    public static Optional<Surface> find(String uiMode, Session session) {
        Objects.requireNonNull(session, "session");
        if (uiMode == null || uiMode.isBlank()) return Optional.empty();
        String key = uiMode.toLowerCase(Locale.ROOT);
        for (SurfaceProvider provider : providers()) {
            if (key.equals(provider.uiMode().toLowerCase(Locale.ROOT))) {
                return Optional.of(provider.create(session));
            }
        }
        return Optional.empty();
    }

    /**
     * Look up a surface for the given uiMode or throw a descriptive error.
     * The error message lists every uiMode currently registered so the
     * caller knows what's available on this classpath.
     */
    public static Surface require(String uiMode, Session session) {
        Objects.requireNonNull(uiMode, "uiMode");
        Objects.requireNonNull(session, "session");
        return find(uiMode, session).orElseThrow(() -> new IllegalStateException(
                "No SurfaceProvider registered for uiMode '" + uiMode + "'. "
                        + "Available: " + available()
                        + " — check the :ui:* modules on the classpath."));
    }

    /**
     * The set of uiMode strings registered on the current classpath, in
     * provider order.  Useful for {@code --help} text and error messages.
     */
    public static List<String> available() {
        List<String> modes = new ArrayList<>();
        for (SurfaceProvider provider : providers()) {
            modes.add(provider.uiMode());
        }
        return modes;
    }

    private static List<SurfaceProvider> providers() {
        List<SurfaceProvider> list = new ArrayList<>();
        ServiceLoader.load(SurfaceProvider.class).forEach(list::add);
        return list;
    }
}
