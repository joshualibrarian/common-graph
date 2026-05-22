package dev.everydaythings.graph.ui;

import dev.everydaythings.graph.runtime.session.Session;
import dev.everydaythings.graph.scene.Painter;

import java.util.List;

/**
 * Surface — the host-side canvas a session paints on.
 *
 * <p>A Surface is the runtime body of "the UI for this session from this
 * host's vantage point."  It owns one OS-level resource (the terminal in
 * TUI; a single overlay window in desktop GUI; a browser tab on the web)
 * and runs a single render loop that paints all of the session's
 * {@link Window Windows} into that resource.
 *
 * <h2>Ownership</h2>
 * <ul>
 *   <li><b>One {@link Painter}</b> — internal, picks the medium (TUI cells,
 *       Skia pixels, Filament GPU calls, ...).  The Painter SPI is still
 *       the pixel-emitter contract, but it's no longer the user-facing
 *       extension point; Surface is.</li>
 *   <li><b>One render loop</b> — drives the painter at a configurable
 *       cadence.  Dirty tracking lets clean windows skip work cheaply.</li>
 *   <li><b>A collection of {@link Window Windows}</b> — runtime mirrors of
 *       the session's {@code ITEM_VIEW} frames.  Each Window contributes
 *       its scene to the surface's composite on each render.</li>
 *   <li><b>A background drawing region</b> — the space between/around
 *       Windows (swarms, ambient content).  Not yet wired; placeholder.</li>
 * </ul>
 *
 * <h2>Lifecycle</h2>
 * <ol>
 *   <li>{@link #open()} — acquires the OS resource, starts the render loop.</li>
 *   <li>{@link #addWindow(Window)} / {@link #removeWindow(Window)} — windows
 *       added/removed as the session's {@code ITEM_VIEW} frames evolve.</li>
 *   <li>{@link #requestRender()} — coalesced ask for a re-paint at the next
 *       tick (safe from any thread).</li>
 *   <li>{@link #close()} — stops the loop, releases the OS resource.
 *       Idempotent.</li>
 * </ol>
 *
 * <p>Surfaces are constructed via {@link SurfaceProvider} (ServiceLoader
 * discovery) bound to a {@link Session}.  Each painter library
 * ({@code :ui:tui}, {@code :ui:skia}, {@code :ui:filament}, ...) ships one
 * Surface implementation that owns a matching Painter internally.
 */
public interface Surface extends AutoCloseable {

    /**
     * Bring the surface up: acquire the OS resource (terminal, window),
     * start the render loop, paint any windows already added.
     *
     * <p>Idempotent in the sense that a second call while open is a no-op;
     * calling after {@link #close()} is an error.
     */
    void open();

    /**
     * Add a window to be rendered on this surface.  Triggers a re-render.
     * Safe from any thread.
     */
    void addWindow(Window window);

    /**
     * Remove a window from this surface.  Triggers a re-render.  No-op if
     * the window was never added.  Safe from any thread.
     */
    void removeWindow(Window window);

    /**
     * Coalesced request for an immediate re-paint at the next loop tick.
     * Safe from any thread; multiple signals between two paints collapse
     * into one.
     */
    void requestRender();

    /** True iff the surface is currently open and rendering. */
    boolean isOpen();

    /**
     * Snapshot of the windows currently attached to this surface.  Returned
     * list is an immutable copy at the call site; subsequent
     * {@link #addWindow}/{@link #removeWindow} calls don't affect it.
     */
    List<Window> windows();

    /**
     * Stop the loop, release the OS resource.  Idempotent.  After close,
     * the surface is unusable; construct a new one to render again.
     */
    @Override
    void close();
}
