package dev.everydaythings.graph.ui;

import dev.everydaythings.graph.datum.Binding;
import dev.everydaythings.graph.datum.Body;
import dev.everydaythings.graph.language.ThematicRole;
import dev.everydaythings.graph.ref.CompoundKey;
import dev.everydaythings.graph.ref.ItemRef;
import dev.everydaythings.graph.runtime.librarian.Librarian;
import dev.everydaythings.graph.runtime.session.Session;
import dev.everydaythings.graph.runtime.session.SessionVocabulary;
import dev.everydaythings.graph.scene.SceneCascade;
import lombok.extern.log4j.Log4j2;

import java.util.Objects;
import java.util.function.Supplier;

/**
 * UiSession — intermediate {@link Session} subclass that holds the UI
 * lifecycle.  Lives in {@code :ui} so it can directly wire a {@link Surface}
 * (which internally owns the painter + render loop) without crossing an
 * SPI back into {@code :core}.
 *
 * <p>Both client-side embodiments extend this:
 * <ul>
 *   <li>{@link LocalSession} — in-VM Librarian reference; direct dispatch.</li>
 *   <li>{@link RemoteSession} — Parley + Noise to a remote Librarian.</li>
 * </ul>
 * Each subclass differs only in its <i>librarian-comm</i> path; the UI
 * bring-up is identical and lives entirely here.
 *
 * <h2>Lifecycle</h2>
 * <ol>
 *   <li>{@link #startUi(String)} — resolve a {@link Surface} via
 *       {@link SurfaceRegistry} for the requested uiMode, open it.  The
 *       Surface internally constructs its Painter, Presenter, and
 *       RenderLoop.</li>
 *   <li>{@link #addWindow(Window)} / {@link #removeWindow(Window)} —
 *       manage the windows the session is currently showing.</li>
 *   <li>{@link #requestRender()} — coalesce a re-render at the next loop
 *       tick.  Safe from any thread.</li>
 *   <li>{@link #stopUi()} — stop the surface and release its OS resource.
 *       Safe to call before {@code startUi} (no-op) or twice (idempotent).</li>
 * </ol>
 *
 * <p>Exactly one Surface is allowed per session at a time; calling
 * {@code startUi} twice without an intervening {@code stopUi} throws.
 */
@Log4j2
public abstract class UiSession extends Session {

    private final Object surfaceLock = new Object();
    private Surface surface;

    protected UiSession(ItemRef iid, Librarian librarian) {
        super(iid, librarian);
    }

    /**
     * Bring up the UI for this session.
     *
     * <p>Resolves a {@link Surface} from {@link SurfaceRegistry} by uiMode
     * (e.g. {@code "tui"}), opens it, and returns once the surface is up.
     *
     * @param uiMode which surface to use ({@code "tui"} / {@code "skia"} / {@code "filament"})
     * @throws IllegalStateException if the UI is already running
     * @throws IllegalStateException if no surface is registered for {@code uiMode}
     */
    public final void startUi(String uiMode) {
        Objects.requireNonNull(uiMode, "uiMode");

        synchronized (surfaceLock) {
            if (surface != null && surface.isOpen()) {
                throw new IllegalStateException(
                        "UI already running on " + getClass().getSimpleName()
                                + " (call stopUi first to restart)");
            }
            surface = SurfaceRegistry.require(uiMode, this);
            surface.open();
            enumerateItemViewWindows(surface);
            logger.info("{} UI started ({} surface)", getClass().getSimpleName(), uiMode);
        }
    }

    /**
     * Walk the librarian for {@code ITEM_VIEW} frames addressed to this
     * session, build a {@link Window} from each, and attach it to
     * {@code surface}.  Called once at {@link #startUi} after the surface
     * opens; lives outside the dispatch path so initial-render is driven by
     * the declarative ITEM_VIEW state, not by manual {@code addWindow}
     * calls.
     *
     * <p>Each Window's scene supplier captures the Theme iid and calls
     * {@link SceneCascade#sceneFor} on the live librarian, so re-renders
     * pick up any change to the viewed item's declared scene (or its
     * archetype's, on up the chain) without further wiring.
     */
    private void enumerateItemViewWindows(Surface intoSurface) {
        Librarian lib = librarian();
        ItemRef sessionIid = iid();
        if (lib == null || sessionIid == null) return;

        ItemRef locationRole = ItemRef.iid(ThematicRole.Location.KEY);
        ItemRef themeRole = ItemRef.iid(ThematicRole.Theme.KEY);
        ItemRef itemViewHead = ItemRef.iid(SessionVocabulary.ItemView.KEY);

        int attached = 0;
        for (Body body : lib.bodiesByReferenceBinding(locationRole, sessionIid)) {
            if (!itemViewHead.equals(body.headRef())) continue;
            ItemRef theme = body.binding(CompoundKey.of(themeRole))
                    .map(Binding::target)
                    .filter(t -> t instanceof ItemRef)
                    .map(t -> (ItemRef) t)
                    .orElse(null);
            if (theme == null) continue;
            Supplier<Body> sceneSupplier = () -> SceneCascade.sceneFor(theme, lib);
            intoSurface.addWindow(Window.fromBody(body, sceneSupplier));
            attached++;
        }
        if (attached > 0) {
            logger.info("Attached {} window(s) from ITEM_VIEW frames addressed to {}",
                    attached, sessionIid.encodeText());
        }
    }

    /**
     * Add a window to the current surface.  Throws if the UI isn't running.
     */
    public final void addWindow(Window window) {
        Objects.requireNonNull(window, "window");
        Surface s;
        synchronized (surfaceLock) {
            if (surface == null || !surface.isOpen()) {
                throw new IllegalStateException("UI is not running");
            }
            s = surface;
        }
        s.addWindow(window);
    }

    /**
     * Remove a window from the current surface.  No-op if the UI isn't
     * running or the window wasn't added.
     */
    public final void removeWindow(Window window) {
        if (window == null) return;
        Surface s;
        synchronized (surfaceLock) {
            if (surface == null || !surface.isOpen()) return;
            s = surface;
        }
        s.removeWindow(window);
    }

    /**
     * Request an immediate re-render at the next loop tick.  Safe from any
     * thread.  No-op if the UI is not currently running.
     */
    public final void requestRender() {
        Surface s;
        synchronized (surfaceLock) { s = surface; }
        if (s != null && s.isOpen()) s.requestRender();
    }

    /**
     * Stop the UI: close the surface, release its OS resource.
     * Idempotent.  Safe to call before {@link #startUi} too (no-op).
     */
    public final void stopUi() {
        Surface toClose;
        synchronized (surfaceLock) {
            toClose = surface;
            surface = null;
        }
        if (toClose != null) {
            toClose.close();
            logger.info("{} UI stopped.", getClass().getSimpleName());
        }
    }

    /** True iff the surface is currently open. */
    public final boolean isUiRunning() {
        synchronized (surfaceLock) {
            return surface != null && surface.isOpen();
        }
    }

    /**
     * Snapshot of the windows currently attached to this session's surface.
     * Empty list if the UI isn't running.  Returned list is a copy at the
     * call site; subsequent surface mutations don't affect it.
     */
    public final java.util.List<Window> attachedWindows() {
        Surface s;
        synchronized (surfaceLock) { s = surface; }
        if (s == null || !s.isOpen()) return java.util.List.of();
        return s.windows();
    }
}
