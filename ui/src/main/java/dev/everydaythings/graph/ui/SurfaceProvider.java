package dev.everydaythings.graph.ui;

import dev.everydaythings.graph.runtime.session.Session;

/**
 * SurfaceProvider — the ServiceLoader contract that lets a painter module
 * (e.g. {@code :ui:tui}) register itself for discovery by uiMode string.
 *
 * <p>Each painter library ships exactly one provider implementation and a
 * {@code META-INF/services/dev.everydaythings.graph.ui.SurfaceProvider}
 * file naming the implementation.  At runtime the {@link SurfaceRegistry}
 * walks the ServiceLoader to find the provider whose {@link #uiMode()}
 * matches the caller's request, then calls {@link #create(Session)} to
 * build a fresh {@link Surface} for the given session.
 *
 * <p>Providers are stateless and zero-arg-constructable (ServiceLoader
 * requires it).  All configuration goes through {@code create()} — for
 * the first cut the surface auto-detects its OS resource (terminal size,
 * default overlay dimensions, etc.); richer construction-time hints can
 * come later via additional methods on this interface or via an options
 * bag.
 */
public interface SurfaceProvider {

    /**
     * The {@code --ui} mode string this provider responds to.  Conventional
     * values: {@code "tui"}, {@code "skia"}, {@code "filament"}, {@code "web"}.
     * Matching is case-insensitive in the registry but providers should
     * return their canonical lowercase form.
     */
    String uiMode();

    /**
     * Construct a fresh {@link Surface} bound to the given session.  The
     * surface is not yet open; the caller invokes {@link Surface#open()}
     * after construction.
     *
     * <p>The session reference lets the surface read the session's
     * {@code variableResolver}, query its {@code ITEM_VIEW} frames, and
     * deliver input events back to the librarian.
     */
    Surface create(Session session);
}
