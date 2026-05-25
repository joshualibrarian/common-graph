package dev.everydaythings.graph.runtime.session;

import dev.everydaythings.graph.Seed;
import dev.everydaythings.graph.datum.Binding;
import dev.everydaythings.graph.datum.Body;
import dev.everydaythings.graph.datum.Frame;
import dev.everydaythings.graph.item.Item;
import dev.everydaythings.graph.language.ThematicRole;
import dev.everydaythings.graph.ref.ItemRef;
import dev.everydaythings.graph.runtime.librarian.Librarian;
import dev.everydaythings.graph.scene.VariableResolver;
import lombok.extern.log4j.Log4j2;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Supplier;

/**
 * Session — the other side of the Librarian coin. The persistent, in-graph
 * shared context Item where users, devices, librarians, and hosts meet.
 *
 * <p>A Session is multi-* by design:
 * <ul>
 *   <li><b>Multi-principal</b> — several users may participate in the same
 *       Session (work / family / hobby contexts).</li>
 *   <li><b>Multi-librarian</b> — multiple librarians may serve a Session
 *       (federated across servers).</li>
 *   <li><b>Multi-host</b> — a Session may span hosts.</li>
 *   <li><b>Multi-device</b> — phones, watches, laptops can all bind to the
 *       same Session.</li>
 * </ul>
 *
 * <p>A Session is an {@link Item} but <b>not</b> a {@link dev.everydaythings.graph.cryptography.Signer}.
 * It carries no vault; it never signs anything itself. State changes to a Session
 * are signed by whichever participating principal made the change. Its INCEPTION
 * is signed by the principal who minted it.
 *
 * <h2>Runtime embodiments</h2>
 * The {@link Session} class is the canonical server-side embodiment (used by
 * the {@link Librarian} process to track the Session's state). Client-side
 * processes use one of two subclasses (both in {@code :ui}) to represent the
 * same Session item:
 * <ul>
 *   <li>{@code LocalSession} — the in-VM client view. Holds a direct
 *       {@link Librarian} reference; method calls dispatch directly.</li>
 *   <li>{@code RemoteSession} — the remote client view. Owns a Parley
 *       instance and a RemoteConnection; method calls become frames over
 *       the wire. Carries its own ephemeral keypair (Ed25519 → Noise X25519)
 *       delegated by the user's vault at session start. Typically the entry
 *       point of the frontend.</li>
 * </ul>
 *
 * <p>All three classes share the same IID for the same Session item; only the
 * runtime class differs based on where you are relative to the librarian that
 * owns the Session.
 *
 * <p>Both client-side embodiments live in {@code :ui} so they can wire
 * {@code Painter} + {@code Presenter} + {@code RenderLoop} directly without
 * crossing back through an SPI; {@code :core} just sees this base class.
 *
 * <p>The {@code @Seed.Embodies} below points at the {@code Session} class
 * itself — the server-side embodiment. {@code LocalSession} and
 * {@code RemoteSession} are <i>not</i> separate embodiments; they're subclasses
 * the appropriate client-side runtime explicitly instantiates at startup.
 *
 * <p>STUB — structure only; workspace state, device/principal/librarian/host
 * bindings, and lifecycle wiring TBD.
 */
@Seed.Item(key = Session.KEY)
@Seed.Embodies(key = Session.CODE_KEY, archetype = Session.KEY)
@Log4j2
public class Session extends Item {

    /** Canonical key for Session-the-archetype. */
    public static final String KEY = "cg.archetype:session";

    /** The archetype IID for Session instances. */

    /** Canonical key for the CodeItem representing the server-side Java embodiment. */
    public static final String CODE_KEY = "cg.code:session-java-default";

    /** IID of the CodeItem for the server-side Java embodiment. */
    public static final ItemRef CODE_IID = ItemRef.fromString(CODE_KEY);

    @Override
    public ItemRef archetype() {
        return ItemRef.iid(KEY);
    }

    /**
     * Runtime Variable bindings — the local-to-the-runtime suppliers for
     * Variable sememes the presenter consults during scene resolution.
     *
     * <p>Held on the base class because both {@code LocalSession} and
     * {@code RemoteSession} need it.  Window-side Variables (Viewport,
     * CursorPosition, FocusedNode, CurrentTime, ...) bind here; the
     * presenter calls {@link #variableResolver()} once per render to
     * obtain a {@link VariableResolver} that reads through to these
     * suppliers.
     *
     * <p>Concurrent-safe: variables may be (re)bound from one thread
     * while another thread is rendering.
     */
    private final ConcurrentMap<ItemRef, Supplier<Object>> variables = new ConcurrentHashMap<>();

    /**
     * Listeners notified when this session's view-state changes — i.e., when
     * an {@code ITEM_VIEW} frame addressed to this session arrives at
     * {@link #handleItemView}.  The session itself doesn't cache view-state;
     * the Library is the source of truth (the index has all ITEM_VIEW frames
     * addressed to this session via their Location binding).  Listeners
     * typically re-walk the index to reconcile their local picture
     * (e.g., {@code UiSession} updates its Surface's window list).
     *
     * <p>{@link CopyOnWriteArrayList} for safe concurrent iteration: dispatch
     * may be firing a notification while a UI thread (de)registers a listener.
     */
    private final List<Runnable> viewStateListeners = new CopyOnWriteArrayList<>();

    /** Runtime constructor — bound to a librarian. */
    public Session(ItemRef iid, Librarian librarian) {
        super(iid, librarian);
    }

    // ==================================================================================
    // Variable binding — the contract the Presenter consults during render.
    // ==================================================================================

    /**
     * Bind a runtime supplier for a Variable.  The supplier is called by
     * the presenter when it encounters a reference to {@code variable} in
     * a scene-tree binding target.  Suppliers should be cheap; for
     * snapshot semantics within a render pass, capture the value once at
     * tick start and have the supplier return the captured value.
     *
     * <p>Calling {@code bindVariable} again with the same key replaces
     * the previous supplier.
     */
    public void bindVariable(ItemRef variable, Supplier<Object> supplier) {
        Objects.requireNonNull(variable, "variable");
        Objects.requireNonNull(supplier, "supplier");
        variables.put(variable, supplier);
    }

    /** Remove a Variable binding.  No-op if {@code variable} wasn't bound. */
    public void unbindVariable(ItemRef variable) {
        if (variable == null) return;
        variables.remove(variable);
    }

    /** True iff a supplier is currently bound for the given Variable. */
    public boolean isVariableBound(ItemRef variable) {
        return variable != null && variables.containsKey(variable);
    }

    /**
     * Build a {@link VariableResolver} that reads this session's currently
     * bound Variables.  Resolvers are cheap — call once per render pass to
     * get a fresh handle that reads through to live suppliers.  Each
     * {@code resolve} call invokes the underlying supplier; if a supplier
     * captures a snapshot at tick start, all reads in a single render see
     * the same value.
     */
    public VariableResolver variableResolver() {
        return ref -> {
            if (ref == null) return Optional.empty();
            Supplier<Object> supplier = variables.get(ref);
            if (supplier == null) return Optional.empty();
            return Optional.ofNullable(supplier.get());
        };
    }

    // ==================================================================================
    // View-state mutation — open / close views
    // ==================================================================================

    /**
     * Open a view of {@code itemIid} on this session.  Publishes a minimal
     * {@code ITEM_VIEW} frame ({@code Theme = itemIid, Location = this.iid()})
     * via the librarian.  The frame is signed by the librarian, persisted,
     * indexed, and routed back to {@link #handleItemView} — listeners fire,
     * any attached {@code UiSession} reconciles its surface.
     *
     * <p>For richer views (specific display, expanded/collapsed state,
     * explicit size), publish the ITEM_VIEW frame directly with the
     * additional bindings.  This helper is the minimal-shape convenience.
     *
     * @throws IllegalStateException if this session has no librarian
     */
    public void openView(ItemRef itemIid) {
        Objects.requireNonNull(itemIid, "itemIid");
        if (librarian == null) {
            throw new IllegalStateException("Session has no librarian; cannot openView");
        }
        Body body = Body.of(
                ItemRef.iid(SessionVocabulary.ItemView.KEY),
                java.util.List.of(
                        Binding.ref(ItemRef.iid(ThematicRole.Theme.KEY), itemIid),
                        Binding.ref(ItemRef.iid(ThematicRole.Location.KEY), iid())));
        librarian.assembleFrame(body, librarian);
    }

    /**
     * Close any open views of {@code itemIid} on this session.  Publishes a
     * minimal {@code CLOSE} frame ({@code Theme = itemIid, Location = this.iid()}).
     * Reconciliation filters out ITEM_VIEW frames whose Theme matches a
     * CLOSE frame's Theme, so the corresponding windows disappear.
     *
     * @throws IllegalStateException if this session has no librarian
     */
    public void closeView(ItemRef itemIid) {
        Objects.requireNonNull(itemIid, "itemIid");
        if (librarian == null) {
            throw new IllegalStateException("Session has no librarian; cannot closeView");
        }
        Body body = Body.of(
                ItemRef.iid(SessionVocabulary.Close.KEY),
                java.util.List.of(
                        Binding.ref(ItemRef.iid(ThematicRole.Theme.KEY), itemIid),
                        Binding.ref(ItemRef.iid(ThematicRole.Location.KEY), iid())));
        librarian.assembleFrame(body, librarian);
    }

    // ==================================================================================
    // View-state listeners — observers of ITEM_VIEW arrivals
    // ==================================================================================

    /**
     * Register a listener to be notified whenever an {@code ITEM_VIEW} frame
     * is delivered to this session.  Listeners typically re-walk the
     * librarian's index to reconcile their local picture of the session's
     * windows; the Library is the source of truth, so the listener doesn't
     * receive a diff — just a "something changed, re-check" signal.
     *
     * <p>Listeners run on the dispatch thread (synchronously inside
     * {@link #handleItemView}).  Exceptions thrown by listeners are caught
     * and logged; one bad listener doesn't break the chain.
     */
    public void addViewStateListener(Runnable listener) {
        Objects.requireNonNull(listener, "listener");
        viewStateListeners.add(listener);
    }

    /** Remove a previously-registered view-state listener.  No-op if not registered. */
    public void removeViewStateListener(Runnable listener) {
        if (listener == null) return;
        viewStateListeners.remove(listener);
    }

    // ==================================================================================
    // ITEM_VIEW handler
    // ==================================================================================

    /**
     * Handler for incoming {@code ITEM_VIEW} frames addressed to this session.
     * Dispatch routes here based on the frame's {@code Location} binding
     * (declared via {@code role = ThematicRole.Location.KEY} below): the
     * dispatcher's {@code liveInstanceOf(Session.KEY, location-iid)} walk
     * finds this specific session instance.
     *
     * <p>This handler doesn't itself cache view-state — the Library's
     * {@code FRAME_BY_TARGET}-index on {@code Location → sessionIid} already
     * holds every ITEM_VIEW frame addressed to us, indexed and queryable.
     * The handler's job is to <i>notify</i> registered listeners that
     * something changed; observers like {@code UiSession} react by re-walking
     * the index to update their projection.
     *
     * <p>The frame body itself is persisted ahead of dispatch by the librarian's
     * submit path; nothing in this handler needs to write it again.
     *
     * <p>Supersedence (an ITEM_VIEW frame {@code FOLLOWS}-ing a prior one),
     * CLOSE handling, and frame-binding interpretation are slice-4+ work.
     */
    @Seed.Handler(predicate = SessionVocabulary.ItemView.KEY, role = ThematicRole.Location.KEY)
    public List<Frame> handleItemView(Frame request) {
        logger.debug("[Session {}] ITEM_VIEW frame received — body head={}",
                iid(), request.body().headRef());
        notifyViewStateListeners();
        return List.of();
    }

    /**
     * Handler for {@code CLOSE} frames addressed to this session.  Dispatch
     * routes here via {@code role = ThematicRole.Location.KEY} the same way
     * {@link #handleItemView} works.
     *
     * <p>The CLOSE frame's {@code Theme} names the item whose view should be
     * closed.  Like ITEM_VIEW, this handler doesn't itself mutate a cache —
     * the Library indexes the CLOSE frame, and observers (typically a
     * {@code UiSession}) derive the open-view set by walking ITEM_VIEW frames
     * for this session minus any whose Theme has been CLOSEd.
     *
     * <p>Per-device close (multi-monitor mirroring close one mirror but not
     * another) lands when device-qualified Location on ITEM_VIEW lands.
     */
    @Seed.Handler(predicate = SessionVocabulary.Close.KEY, role = ThematicRole.Location.KEY)
    public List<Frame> handleClose(Frame request) {
        logger.debug("[Session {}] CLOSE frame received — body head={}",
                iid(), request.body().headRef());
        notifyViewStateListeners();
        return List.of();
    }

    /**
     * Fire each registered listener.  Exceptions are caught + logged so one
     * misbehaving observer doesn't prevent the others from running.
     */
    private void notifyViewStateListeners() {
        for (Runnable listener : viewStateListeners) {
            try {
                listener.run();
            } catch (RuntimeException e) {
                logger.warn("[Session {}] view-state listener threw: {}", iid(), e.toString(), e);
            }
        }
    }

    // TODO: workspace state (focus, subscriptions, view state)
    // TODO: device/principal/librarian/host bindings
    // TODO: lifecycle (mint, attach, detach, dissolve)
}
