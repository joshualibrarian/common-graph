package dev.everydaythings.graph.runtime.session;

import dev.everydaythings.graph.datum.Binding;
import dev.everydaythings.graph.datum.Body;
import dev.everydaythings.graph.datum.Frame;
import dev.everydaythings.graph.language.ThematicRole;
import dev.everydaythings.graph.ref.ItemRef;
import dev.everydaythings.graph.runtime.librarian.Librarian;
import dev.everydaythings.graph.runtime.stage.ItemStage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies {@link Session#handleItemView} dispatch wiring:
 *
 * <ol>
 *   <li>The seed-processing path correctly wires the {@code @Seed.Handler}
 *       annotation into the dispatch chain (HANDLES binding on the Session
 *       archetype + IMPLEMENTS binding on the Session code-item).</li>
 *   <li>With {@code role = Location.KEY} declared, the dispatcher routes the
 *       frame to the live {@link Session} instance whose iid matches the
 *       frame's Location binding target.</li>
 *   <li>The handler fires registered view-state listeners.</li>
 * </ol>
 */
@DisplayName("Session.handleItemView dispatch + listener notification")
class SessionItemViewHandlerTest {

    @Test
    @DisplayName("Submitting an ITEM_VIEW frame fires the targeted session's listeners")
    void itemViewFrameNotifiesTargetedSessionListeners() {
        Librarian lib = Librarian.ephemeral(ItemStage.javaOnly());
        lib.bootstrap();

        ItemRef sessionIid = ItemRef.iid("cg.test:slice3-session");
        Session session = new Session(sessionIid, lib);
        lib.register(session);

        AtomicInteger fired = new AtomicInteger();
        session.addViewStateListener(fired::incrementAndGet);

        Body body = Body.of(
                ItemRef.iid(SessionVocabulary.ItemView.KEY),
                List.of(
                        Binding.ref(ItemRef.iid(ThematicRole.Theme.KEY), sessionIid),
                        Binding.ref(ItemRef.iid(ThematicRole.Location.KEY), sessionIid)));
        lib.submit(Frame.of(body, List.of()));

        assertThat(fired.get())
                .as("Session's view-state listener should fire exactly once per matching frame")
                .isEqualTo(1);
    }

    @Test
    @DisplayName("Frames with a different predicate do NOT fire the ITEM_VIEW listener")
    void unrelatedFramesAreNotHandled() {
        Librarian lib = Librarian.ephemeral(ItemStage.javaOnly());
        lib.bootstrap();

        ItemRef sessionIid = ItemRef.iid("cg.test:unrelated-predicate-session");
        Session session = new Session(sessionIid, lib);
        lib.register(session);

        AtomicInteger fired = new AtomicInteger();
        session.addViewStateListener(fired::incrementAndGet);

        Body unrelated = Body.of(
                ItemRef.iid("cg.test:some-other-predicate"),
                List.of());
        lib.submit(Frame.of(unrelated, List.of()));

        assertThat(fired.get())
                .as("Non-ITEM_VIEW frames should not reach handleItemView")
                .isZero();
    }

    @Test
    @DisplayName("ITEM_VIEW frames addressed to a different session do NOT fire this session's listener")
    void framesForOtherSessionsDontFire() {
        Librarian lib = Librarian.ephemeral(ItemStage.javaOnly());
        lib.bootstrap();

        // Register two sessions; the frame targets the second one.  The first
        // session's listener should not fire — dispatch routes to the
        // Location-named instance, not just any registered Session.
        ItemRef sessionAIid = ItemRef.iid("cg.test:session-a");
        ItemRef sessionBIid = ItemRef.iid("cg.test:session-b");
        Session sessionA = new Session(sessionAIid, lib);
        Session sessionB = new Session(sessionBIid, lib);
        lib.register(sessionA);
        lib.register(sessionB);

        AtomicInteger firedA = new AtomicInteger();
        AtomicInteger firedB = new AtomicInteger();
        sessionA.addViewStateListener(firedA::incrementAndGet);
        sessionB.addViewStateListener(firedB::incrementAndGet);

        Body bodyForB = Body.of(
                ItemRef.iid(SessionVocabulary.ItemView.KEY),
                List.of(
                        Binding.ref(ItemRef.iid(ThematicRole.Theme.KEY), sessionBIid),
                        Binding.ref(ItemRef.iid(ThematicRole.Location.KEY), sessionBIid)));
        lib.submit(Frame.of(bodyForB, List.of()));

        assertThat(firedB.get()).as("Session B's listener fires for the frame addressed to it").isEqualTo(1);
        assertThat(firedA.get()).as("Session A's listener is not touched").isZero();
    }
}
