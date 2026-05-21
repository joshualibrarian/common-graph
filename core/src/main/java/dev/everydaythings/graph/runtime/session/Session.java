package dev.everydaythings.graph.runtime.session;

import dev.everydaythings.graph.Seed;
import dev.everydaythings.graph.datum.Frame;
import dev.everydaythings.graph.item.Item;
import dev.everydaythings.graph.ref.ItemRef;
import dev.everydaythings.graph.runtime.librarian.Librarian;
import dev.everydaythings.graph.scene.VariableResolver;
import lombok.extern.log4j.Log4j2;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;
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
     * Counter for {@code ITEM_VIEW} frames the dispatch routed to this
     * code-item's handler.  Slice-2 plumbing observation point — lets tests
     * verify that the {@link #handleItemView} handler is actually invoked
     * end-to-end through the Librarian's IMPLEMENTS-based dispatch.  Slice 3
     * replaces this with real per-session state mutation + a listener
     * mechanism that {@code UiSession} subscribes to.
     */
    private static final AtomicLong itemViewHandlerInvocations = new AtomicLong();

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
    // ITEM_VIEW handler — slice-2 plumbing stub
    // ==================================================================================

    /**
     * Handler for incoming {@code ITEM_VIEW} frames.  Slice-2 plumbing only:
     * logs the frame arrival, increments the invocation counter, returns nothing.
     *
     * <p>Steady-state behavior (slice 3+):
     * <ul>
     *   <li>Extract the target session IID from the frame's {@code Location}
     *       binding; if it isn't this librarian's known session, ignore.</li>
     *   <li>Read the frame's bindings (Theme = item, Location[device]:Point,
     *       Attribute[Expanded]:Bool, Attribute[Size]:Size) into Window-shaped
     *       intent.</li>
     *   <li>Mutate the session's runtime view-state — add a new Window,
     *       update an existing one (supersedence via FOLLOWS), or remove one.</li>
     *   <li>Notify any attached {@code UiSession} to reconcile its Surface's
     *       Window list against the new state.</li>
     * </ul>
     *
     * <p>Today it does none of those — see {@link #itemViewHandlerInvocations}
     * for the test observation point.
     */
    @Seed.Handler(predicate = SessionVocabulary.ItemView.KEY)
    public List<Frame> handleItemView(Frame request) {
        long n = itemViewHandlerInvocations.incrementAndGet();
        logger.info("[Session handler] ITEM_VIEW frame received (invocation #{}) — head={}",
                n, request.body().headRef());
        return List.of();
    }

    /**
     * How many {@code ITEM_VIEW} frames have been routed to
     * {@link #handleItemView} across all dispatches in this JVM.  Slice-2
     * observation point for tests.  Resettable for test isolation.
     */
    public static long itemViewHandlerInvocations() {
        return itemViewHandlerInvocations.get();
    }

    /** Reset the {@code ITEM_VIEW} handler invocation counter — for test isolation. */
    public static void resetItemViewHandlerInvocations() {
        itemViewHandlerInvocations.set(0);
    }

    // TODO: workspace state (focus, subscriptions, view state)
    // TODO: device/principal/librarian/host bindings
    // TODO: lifecycle (mint, attach, detach, dissolve)
}
