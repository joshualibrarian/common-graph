package dev.everydaythings.graph.ui;

import dev.everydaythings.graph.datum.Body;
import dev.everydaythings.graph.runtime.session.Session;
import dev.everydaythings.graph.scene.Painter;
import lombok.extern.log4j.Log4j2;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * AbstractSurface — Template-method base for {@link Surface} implementations.
 *
 * <p>Carries the machinery that every Surface needs regardless of medium:
 *
 * <ul>
 *   <li>Synchronized {@link Window} list (add/remove/snapshot).</li>
 *   <li>Open/close lifecycle with idempotency and post-close rejection.</li>
 *   <li>Construction and ownership of {@link Painter} + {@link Presenter} +
 *       {@link RenderLoop} on open; teardown on close.</li>
 *   <li>{@link #composeScene()} that produces the per-tick composite Body
 *       the render loop feeds to the painter.</li>
 *   <li>{@link #requestRender()} delegating to the loop.</li>
 * </ul>
 *
 * <h2>What subclasses provide</h2>
 * <ul>
 *   <li>{@link #createPainter()} — the only painter-specific step; the
 *       subclass instantiates its medium's Painter.</li>
 *   <li>{@link #cadence()} (optional override) — default 60Hz; TUI overrides
 *       to a slower value to avoid terminal flicker.</li>
 *   <li>{@link #renderThreadName()} (optional override) — defaults to the
 *       subclass simple name; subclasses can pick something more specific.</li>
 *   <li>{@link #onOpen()} / {@link #onClose()} (optional override) — hooks
 *       for additional OS-level setup/teardown (GLFW window, input callbacks,
 *       terminal raw mode, etc.).</li>
 * </ul>
 *
 * <h2>What subclasses MAY override later (currently uniform)</h2>
 * {@link #composeScene()} is currently a slice-1 single-window pass-through.
 * When slice 2 introduces multi-window composition, the default impl will
 * grow to lay out windows according to surface conventions (TUI: bordered
 * panels; GUI: free-form positioning by Window position/size).  Subclasses
 * with truly bespoke composition (or a per-medium layout hook) can override
 * at that point.
 */
@Log4j2
public abstract class AbstractSurface implements Surface {

    private static final Duration DEFAULT_CADENCE = Duration.ofMillis(16); // 60Hz

    protected final Session session;
    private final Object lock = new Object();
    private final List<Window> windows = new ArrayList<>();

    private Painter painter;
    private Presenter presenter;
    private RenderLoop loop;
    private boolean open;
    private boolean closed;

    protected AbstractSurface(Session session) {
        this.session = Objects.requireNonNull(session, "session");
    }

    // ==================================================================================
    // Subclass extension points
    // ==================================================================================

    /**
     * Construct this surface's painter.  Called once at {@link #open()};
     * the result is owned by this surface and closed on {@link #close()}.
     */
    protected abstract Painter createPainter();

    /** Render-loop cadence.  Default 60Hz; override for slower mediums. */
    protected Duration cadence() {
        return DEFAULT_CADENCE;
    }

    /** Render-thread name.  Defaults to this class's simple name. */
    protected String renderThreadName() {
        return getClass().getSimpleName();
    }

    /**
     * Post-painter-construction hook for subclass setup (GLFW init,
     * terminal raw mode, input callbacks, etc.).  Called inside the
     * open-state lock so subclasses see a consistent surface state.  Default
     * no-op.
     */
    protected void onOpen() {}

    /**
     * Pre-loop-close hook for subclass teardown.  Called inside the
     * close-state lock.  Default no-op.
     */
    protected void onClose() {}

    // ==================================================================================
    // Surface SPI — final template implementations
    // ==================================================================================

    @Override
    public final void open() {
        synchronized (lock) {
            if (closed) {
                throw new IllegalStateException(
                        getClass().getSimpleName() + " is closed; construct a new one");
            }
            if (open) return;

            painter   = Objects.requireNonNull(createPainter(), "createPainter() returned null");
            presenter = new Presenter(painter.viewport(), session.variableResolver());
            loop      = new RenderLoop(painter, presenter, this::composeScene,
                                       cadence(), renderThreadName());
            onOpen();
            loop.start();
            open = true;
            logger.info("{} opened (cadence: {})", getClass().getSimpleName(), cadence());
        }
    }

    @Override
    public final void addWindow(Window window) {
        Objects.requireNonNull(window, "window");
        synchronized (lock) {
            windows.add(window);
        }
        if (loop != null) loop.requestRender();
    }

    @Override
    public final void removeWindow(Window window) {
        if (window == null) return;
        synchronized (lock) {
            windows.remove(window);
        }
        if (loop != null) loop.requestRender();
    }

    @Override
    public final void requestRender() {
        if (loop != null) loop.requestRender();
    }

    @Override
    public final boolean isOpen() {
        synchronized (lock) { return open && !closed; }
    }

    @Override
    public final void close() {
        RenderLoop toClose;
        synchronized (lock) {
            if (closed) return;
            closed = true;
            open = false;
            toClose = loop;
            loop = null;
            try {
                onClose();
            } catch (RuntimeException e) {
                logger.warn("{}.onClose() threw", getClass().getSimpleName(), e);
            }
        }
        if (toClose != null) {
            toClose.close();
            logger.info("{} closed.", getClass().getSimpleName());
        }
    }

    // ==================================================================================
    // Scene composition
    // ==================================================================================

    /**
     * Snapshot the current windows and produce the composite Body the
     * painter will render this tick.
     *
     * <p>Slice 1: returns the first window's scene directly (single-window
     * pass-through).  Slice 2: composes multiple windows with surface-
     * specific layout (panels for TUI, free-form positioning for GUI).
     *
     * <p>Subclasses can override if they need a fundamentally different
     * composition shape; the default is uniform across surfaces.
     */
    protected Body composeScene() {
        Window first;
        synchronized (lock) {
            if (windows.isEmpty()) return null;
            first = windows.get(0);
        }
        return first.sceneSupplier().get();
    }

    // ==================================================================================
    // Accessors for subclasses and tests
    // ==================================================================================

    /** Snapshot the current window list. */
    public final List<Window> windows() {
        synchronized (lock) { return List.copyOf(windows); }
    }

    /** The painter, after {@link #open()}; {@code null} before. */
    protected final Painter painter() { return painter; }

    /** The presenter, after {@link #open()}; {@code null} before. */
    protected final Presenter presenter() { return presenter; }

    /** True iff the render thread is running.  Visible-for-testing. */
    final boolean isLoopRunning() {
        synchronized (lock) {
            return loop != null && loop.isRunning();
        }
    }
}
