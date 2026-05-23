package dev.everydaythings.graph.ui;

import dev.everydaythings.graph.datum.Binding;
import dev.everydaythings.graph.datum.Body;
import dev.everydaythings.graph.language.ThematicRole;
import dev.everydaythings.graph.ref.CompoundKey;
import dev.everydaythings.graph.ref.ItemRef;
import dev.everydaythings.graph.runtime.librarian.Librarian;
import dev.everydaythings.graph.runtime.session.Session;
import dev.everydaythings.graph.runtime.session.SessionVocabulary;
import dev.everydaythings.graph.scene.ContextChain;
import dev.everydaythings.graph.scene.SceneCascade;
import dev.everydaythings.graph.scene.SceneResolver;
import lombok.extern.log4j.Log4j2;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
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
    private Runnable viewStateListener;

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
            // Subscribe to view-state changes so subsequent ITEM_VIEW frames
            // (added/closed/repositioned) reconcile the surface's window list
            // automatically.  stopUi removes this listener.
            viewStateListener = this::reconcileWindows;
            addViewStateListener(viewStateListener);
            logger.info("{} UI started ({} surface)", getClass().getSimpleName(), uiMode);
        }
    }

    /**
     * Re-walk the librarian's ITEM_VIEW frames and reconcile the surface's
     * window list against them.  Called by the view-state listener when
     * {@link Session#handleItemView} fires.  Clears the surface's current
     * windows, then re-enumerates from the index — the Library is the source
     * of truth, so we don't track diffs; we just rebuild the projection.
     *
     * <p>O(N) in the number of ITEM_VIEW frames per reconciliation.  For
     * small workspaces this is cheap; if it becomes a bottleneck, a diff-based
     * reconciler can replace it without changing the listener contract.
     */
    private void reconcileWindows() {
        Surface s;
        synchronized (surfaceLock) { s = surface; }
        if (s == null || !s.isOpen()) return;
        for (Window existing : s.windows()) {
            s.removeWindow(existing);
        }
        enumerateItemViewWindows(s);
    }

    /**
     * Walk the librarian for {@code ITEM_VIEW} frames addressed to this
     * session, build a {@link Window} from each, and attach it to
     * {@code surface}.  Called once at {@link #startUi} after the surface
     * opens, and again from {@link #reconcileWindows} on each view-state
     * notification.
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
        ItemRef closeHead = ItemRef.iid(SessionVocabulary.Close.KEY);

        // The Library is the source of truth; we derive "open views" =
        // ITEM_VIEW frames addressed to this session, minus any whose Theme
        // has been CLOSEd.  One reverse-lookup pulls both predicates
        // (everything with Location → sessionIid); we partition by head.
        Set<ItemRef> closedThemes = new HashSet<>();
        java.util.List<Body> itemViews = new java.util.ArrayList<>();
        for (Body body : lib.bodiesByReferenceBinding(locationRole, sessionIid)) {
            ItemRef head = body.headRef();
            if (itemViewHead.equals(head)) {
                itemViews.add(body);
            } else if (closeHead.equals(head)) {
                readReferenceTarget(body, themeRole).ifPresent(closedThemes::add);
            }
        }

        int attached = 0;
        for (Body body : itemViews) {
            ItemRef theme = readReferenceTarget(body, themeRole).orElse(null);
            if (theme == null) continue;
            if (closedThemes.contains(theme)) continue;
            Supplier<Body> sceneSupplier = () -> {
                Body declared = SceneCascade.sceneFor(theme, lib);
                ContextChain chain = contextChainFor(theme);
                return SceneResolver.resolve(declared, chain);
            };
            intoSurface.addWindow(Window.fromBody(body, sceneSupplier));
            attached++;
        }
        if (attached > 0) {
            logger.info("Attached {} window(s) from ITEM_VIEW frames addressed to {}",
                    attached, sessionIid.encodeText());
        }
    }

    /**
     * Build the context chain a {@link SceneResolver} consults when resolving
     * a Window's scene for the item at {@code contextItemIid}.
     *
     * <p>Chain order is most-specific first: the context item, then this
     * session, then the librarian.  User-level variables (when actor/user
     * machinery lands) slot between context-item and session.  Missing
     * entries (e.g., the context item isn't locally materialized) drop out
     * of the chain via {@link ContextChain}'s null-filtering.
     */
    private ContextChain contextChainFor(ItemRef contextItemIid) {
        Librarian lib = librarian();
        dev.everydaythings.graph.item.Item contextItem = lib != null
                ? lib.fetchItem(contextItemIid).orElse(null)
                : null;
        return new ContextChain(java.util.Arrays.asList(
                contextItem,    // most-specific
                this,           // session
                lib));          // librarian
    }

    /**
     * Read a binding's reference target as an {@link ItemRef} from
     * {@code body}'s simple-key (no qualifiers) binding for {@code role}.
     * Empty when missing or the target is not a reference.
     */
    private static java.util.Optional<ItemRef> readReferenceTarget(Body body, ItemRef role) {
        return body.binding(CompoundKey.of(role))
                .map(Binding::target)
                .filter(t -> t instanceof ItemRef)
                .map(t -> (ItemRef) t);
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
     * Stop the UI: unsubscribe from session view-state notifications, close
     * the surface, release its OS resource.  Idempotent.  Safe to call before
     * {@link #startUi} too (no-op).
     */
    public final void stopUi() {
        Surface toClose;
        Runnable listenerToRemove;
        synchronized (surfaceLock) {
            toClose = surface;
            listenerToRemove = viewStateListener;
            surface = null;
            viewStateListener = null;
        }
        if (listenerToRemove != null) {
            removeViewStateListener(listenerToRemove);
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
