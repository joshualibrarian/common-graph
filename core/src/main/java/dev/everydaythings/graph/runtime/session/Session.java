package dev.everydaythings.graph.runtime.session;

import dev.everydaythings.graph.Seed;
import dev.everydaythings.graph.item.Item;
import dev.everydaythings.graph.id.ItemRef;
import dev.everydaythings.graph.runtime.librarian.Librarian;
import lombok.extern.log4j.Log4j2;

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
 * <p>A Session is an {@link Item} but <b>not</b> a {@link dev.everydaythings.graph.identity.Signer}.
 * It carries no vault; it never signs anything itself. State changes to a Session
 * are signed by whichever participating principal made the change. Its INCEPTION
 * is signed by the principal who minted it.
 *
 * <h2>Runtime embodiments</h2>
 * The {@link Session} class is the canonical server-side embodiment (used by
 * the {@link Librarian} process to track the Session's state). Client-side
 * processes use one of two subclasses to represent the same Session item:
 * <ul>
 *   <li>{@link LocalSession} — the in-VM client view. Holds a direct
 *       {@link Librarian} reference; method calls dispatch directly.</li>
 *   <li>{@link RemoteSession} — the remote client view. Owns a
 *       {@link dev.everydaythings.graph.network.parley.Parley} and a
 *       {@link dev.everydaythings.graph.network.parley.RemoteConnection}; method
 *       calls become frames over the wire. Carries its own ephemeral keypair
 *       (Ed25519 → Noise X25519) delegated by the user's vault at session
 *       start. Typically the entry point of the frontend.</li>
 * </ul>
 *
 * <p>All three classes share the same IID for the same Session item; only the
 * runtime class differs based on where you are relative to the librarian that
 * owns the Session.
 *
 * <p>The {@code @Seed.Embodies} below points at the {@code Session} class
 * itself — the server-side embodiment. {@link LocalSession} and
 * {@link RemoteSession} are <i>not</i> separate embodiments; they're subclasses
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
    public static final ItemRef IID = ItemRef.fromString(KEY);

    /** Canonical key for the CodeItem representing the server-side Java embodiment. */
    public static final String CODE_KEY = "cg.code:session-java-default";

    /** IID of the CodeItem for the server-side Java embodiment. */
    public static final ItemRef CODE_IID = ItemRef.fromString(CODE_KEY);

    @Override
    public ItemRef archetype() {
        return IID;
    }

    /** Runtime constructor — bound to a librarian. */
    public Session(ItemRef iid, Librarian librarian) {
        super(iid, librarian);
    }

    // TODO: workspace state (focus, subscriptions, view state)
    // TODO: device/principal/librarian/host bindings
    // TODO: lifecycle (mint, attach, detach, dissolve)
}
