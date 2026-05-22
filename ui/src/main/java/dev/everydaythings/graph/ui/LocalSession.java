package dev.everydaythings.graph.ui;

import dev.everydaythings.graph.datum.Binding;
import dev.everydaythings.graph.datum.Body;
import dev.everydaythings.graph.language.ThematicRole;
import dev.everydaythings.graph.ref.ItemRef;
import dev.everydaythings.graph.runtime.librarian.Librarian;
import dev.everydaythings.graph.runtime.session.Session;
import dev.everydaythings.graph.runtime.session.SessionVocabulary;
import lombok.extern.log4j.Log4j2;

import java.util.List;

/**
 * The in-VM client embodiment of a {@link Session}.
 *
 * <p>A LocalSession holds a direct reference to the {@link Librarian} that owns
 * the Session item (inherited as {@code Item#librarian}). Method calls dispatch
 * directly — no Parley, no tunnel, no serialization. Same JVM, same heap.
 *
 * <p>Typically instantiated in two scenarios:
 * <ul>
 *   <li><b>Combined-VM mode</b> — a single process runs both a Librarian and
 *       the UI for one of its sessions. The UI side holds the LocalSession;
 *       the Librarian holds the canonical {@link Session}. They reference the
 *       same Session item by IID.</li>
 *   <li><b>Local development / testing</b> — convenient for spinning up a
 *       full stack without networking.</li>
 * </ul>
 *
 * <p>By default a LocalSession skips authentication ceremony — it's in the
 * same VM as the librarian, no attack surface to defend against. Paranoid
 * configurations (regulated environments, multi-tenant same-VM) can opt in to
 * the full auth dance (DELEGATION from user vault to ephemeral session keys,
 * INCEPTION publication, etc.) just like {@link RemoteSession}.
 *
 * <p>UI lifecycle (startUi / requestRender / stopUi) lives on the
 * {@link UiSession} base class shared with {@link RemoteSession}; this
 * subclass only differs in its <i>librarian-comm</i> path (direct ref vs.
 * Parley over a tunnel).
 *
 * <p>STUB — structure only.
 */
@Log4j2
public class LocalSession extends UiSession {

    /** Construct a LocalSession bound to an already-existing Session item. */
    public LocalSession(ItemRef iid, Librarian librarian) {
        super(iid, librarian);
    }

    /**
     * Mint a fresh in-VM session against {@code librarian}: allocate a random
     * IID, construct a {@link LocalSession}, register it with the librarian's
     * cache (so dispatch's {@code liveInstanceOf} walk finds it), and publish
     * a minimal {@code ITEM_VIEW(self)} bootstrap frame so the UI bring-up has
     * at least one view to enumerate.
     *
     * <p>The bootstrap frame carries only {@code Theme → sessionIid} and
     * {@code Location → sessionIid} — the session viewing itself.  Device-
     * qualified Location, Size, and Expanded bindings are deliberately
     * absent; the renderer and window factory provide defaults when those
     * bindings are missing.  Concrete device/size assignment happens later
     * (on {@code startUi} resolving a surface, or on user interaction).
     *
     * <p>The frame is signed by the librarian — sessions don't sign (no vault,
     * no keys), and the librarian is the minting party.  Future remote-session
     * flows may shift signing authority to a delegated session keypair; that's
     * out of scope here.
     *
     * <p>Full session lifecycle (publishing a Session manifest, attaching to a
     * Host, enumerating devices) remains TBD — see the lifecycle TODO on
     * {@link Session}.
     */
    public static LocalSession mint(Librarian librarian) {
        ItemRef sessionIid = ItemRef.random();
        LocalSession session = new LocalSession(sessionIid, librarian);
        librarian.register(session);

        Body bootstrapView = Body.of(
                ItemRef.iid(SessionVocabulary.ItemView.KEY),
                List.of(
                        Binding.ref(ItemRef.iid(ThematicRole.Theme.KEY), sessionIid),
                        Binding.ref(ItemRef.iid(ThematicRole.Location.KEY), sessionIid)));
        librarian.assembleFrame(bootstrapView, librarian);

        return session;
    }

    // TODO: device binding (which local display/keyboard belong to this session)
    // TODO: input forwarding (keystrokes/clicks → frame submissions to librarian)
    // TODO: scene-graph receive path (subscribe to librarian's rendering frames)
    // TODO: optional auth dance for paranoid mode
}
