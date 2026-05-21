package dev.everydaythings.graph.ui;

import dev.everydaythings.graph.datum.Body;
import dev.everydaythings.graph.scene.Painter;
import dev.everydaythings.graph.scene.SceneNode;
import lombok.extern.log4j.Log4j2;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

/**
 * RenderLoop — the render-thread driver.  Owns a dedicated thread that
 * periodically reads a scene snapshot from a supplier, runs the
 * presenter, and hands the result to the painter.
 *
 * <h2>Threading discipline</h2>
 *
 * <p>The render thread must never block on work that can be done
 * elsewhere.  It does only present + paint:
 *
 * <ul>
 *   <li>The {@code Supplier<Body>} returns an already-resolved scene
 *       snapshot — the caller (Session) is responsible for keeping the
 *       snapshot fresh on its own thread.  The render loop reads the
 *       latest snapshot and never waits for a fetch.</li>
 *   <li>{@link Presenter#presentNode(Body)} is pure and CPU-bound; no
 *       I/O, no network, no librarian calls.  Variable suppliers are
 *       O(1) by contract.</li>
 *   <li>{@link Painter#paint(SceneNode)} renders to its surface.
 *       Filament's render thread takes over from here for the GPU side;
 *       TUI / Skia paint synchronously and return.</li>
 * </ul>
 *
 * <p>Anything else — handler dispatch, frame commits, network I/O,
 * Variable refreshes for expensive sources — runs on its own thread and
 * publishes results into snapshots the render loop reads.
 *
 * <h2>Cadence vs. signals</h2>
 *
 * <p>The loop blocks on a queue with a timeout equal to the configured
 * cadence.  Two ways it wakes:
 *
 * <ol>
 *   <li><b>Timeout</b> — the cadence elapsed.  Re-render (useful for
 *       continuous animation or time-driven content like a clock).</li>
 *   <li><b>{@link #requestRender()} signal</b> — external code (e.g.,
 *       Session subscription notifier) signalled that a re-render is
 *       wanted now.  The loop wakes immediately and paints.</li>
 * </ol>
 *
 * <p>Multiple signals between two ticks coalesce into a single render —
 * the queue keeps only one pending signal at a time.
 *
 * <h2>Exception isolation</h2>
 *
 * <p>An exception in the supplier, the presenter, or the painter is
 * caught and logged; the loop continues.  The render thread is
 * resilient: a transient bug in one frame doesn't kill the UI.
 */
@Log4j2
public final class RenderLoop implements AutoCloseable {

    private static final Object WAKE = new Object();

    private final Painter painter;
    private final Presenter presenter;
    private final Supplier<Body> sceneSupplier;
    private final Duration cadence;
    private final String name;

    private final LinkedBlockingQueue<Object> signals = new LinkedBlockingQueue<>(1);
    private final AtomicReference<Thread> threadRef = new AtomicReference<>();
    private volatile boolean running;

    /**
     * Construct a RenderLoop.  No thread starts until {@link #start()} is
     * called.
     *
     * @param painter        the painter to draw into
     * @param presenter      the presenter to resolve scene Variables
     * @param sceneSupplier  source of the current scene snapshot;
     *                       called from the render thread; must be O(1)
     *                       and non-blocking
     * @param cadence        time between automatic re-renders; signals
     *                       can shorten this for any individual tick
     * @param name           thread name for logging / debugging
     */
    public RenderLoop(Painter painter,
                      Presenter presenter,
                      Supplier<Body> sceneSupplier,
                      Duration cadence,
                      String name) {
        this.painter       = Objects.requireNonNull(painter,       "painter");
        this.presenter     = Objects.requireNonNull(presenter,     "presenter");
        this.sceneSupplier = Objects.requireNonNull(sceneSupplier, "sceneSupplier");
        this.cadence       = Objects.requireNonNull(cadence,       "cadence");
        this.name          = Objects.requireNonNullElse(name,      "render-loop");
        if (cadence.isNegative() || cadence.isZero()) {
            throw new IllegalArgumentException("cadence must be positive: " + cadence);
        }
    }

    /**
     * Start the render thread.  Idempotent: a second call while running
     * has no effect.
     */
    public synchronized void start() {
        if (running) return;
        running = true;
        Thread thread = new Thread(this::runLoop, name);
        thread.setDaemon(true);
        threadRef.set(thread);
        thread.start();
    }

    /**
     * Request an immediate render.  If the loop is currently rendering or
     * waiting, the next iteration starts as soon as the current one
     * finishes.  Calls coalesce — multiple signals between two paints
     * trigger one paint.  Safe to call from any thread.
     */
    public void requestRender() {
        signals.offer(WAKE);
    }

    /**
     * Stop the render thread.  Returns once the thread has exited (or
     * immediately if it wasn't running).  Idempotent.
     */
    public synchronized void stop() {
        if (!running) return;
        running = false;
        signals.offer(WAKE);
        Thread thread = threadRef.getAndSet(null);
        if (thread != null) {
            try {
                thread.join();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    /** True iff the render thread is currently running. */
    public boolean isRunning() {
        return running;
    }

    /**
     * Stop the loop and close the painter.  After this, the RenderLoop
     * is unusable.
     */
    @Override
    public void close() {
        stop();
        painter.close();
    }

    // ==================================================================================
    // Render thread
    // ==================================================================================

    private void runLoop() {
        long cadenceNanos = cadence.toNanos();
        while (running) {
            renderOnce();
            if (!running) break;
            try {
                signals.poll(cadenceNanos, TimeUnit.NANOSECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    private void renderOnce() {
        Body snapshot;
        try {
            snapshot = sceneSupplier.get();
        } catch (Exception e) {
            logger.warn("[{}] scene supplier failed; skipping frame", name, e);
            return;
        }
        if (snapshot == null) return;

        SceneNode resolved;
        try {
            resolved = presenter.presentNode(snapshot);
        } catch (Exception e) {
            logger.warn("[{}] presenter failed; skipping frame", name, e);
            return;
        }

        try {
            painter.paint(resolved);
        } catch (Exception e) {
            logger.warn("[{}] painter failed; continuing", name, e);
        }
    }
}
