package dev.everydaythings.graph.runtime.session;

import dev.everydaythings.graph.datum.Binding;
import dev.everydaythings.graph.datum.Body;
import dev.everydaythings.graph.datum.Frame;
import dev.everydaythings.graph.language.ThematicRole;
import dev.everydaythings.graph.ref.ItemRef;
import dev.everydaythings.graph.runtime.librarian.Librarian;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Slice-2 plumbing verification: an {@code ITEM_VIEW} frame submitted to the
 * librarian flows through the IMPLEMENTS-based dispatch and reaches
 * {@link Session#handleItemView}.
 *
 * <p>The handler's body is a stub at this stage (slice 3 fills it in); the
 * point of this test is to confirm the seed-processing path correctly wired
 * the @Seed.Handler annotation into a working dispatch chain (HANDLES binding
 * on the Session archetype + IMPLEMENTS binding on the Session code-item +
 * librarian's reverse-lookup-by-IMPLEMENTS-target).
 */
@DisplayName("Session.handleItemView dispatch — slice-2 plumbing")
class SessionItemViewHandlerTest {

    @BeforeEach
    void resetCounter() {
        Session.resetItemViewHandlerInvocations();
    }

    @Test
    @DisplayName("Submitting an ITEM_VIEW frame increments the handler counter")
    void itemViewFrameIsHandled() {
        Librarian lib = Librarian.inMemory();
        lib.bootstrap();

        ItemRef sessionIid = ItemRef.iid("cg.test:slice2-session");

        Body body = Body.of(
                ItemRef.iid(SessionVocabulary.ItemView.KEY),
                List.of(
                        Binding.ref(ItemRef.iid(ThematicRole.Theme.KEY), sessionIid),
                        Binding.ref(ItemRef.iid(ThematicRole.Location.KEY), sessionIid)));

        long before = Session.itemViewHandlerInvocations();
        lib.submit(Frame.of(body, List.of()));
        long after = Session.itemViewHandlerInvocations();

        assertThat(after).isGreaterThan(before);
    }

    @Test
    @DisplayName("Frames with a different predicate do NOT invoke the ITEM_VIEW handler")
    void unrelatedFramesAreNotHandled() {
        Librarian lib = Librarian.inMemory();
        lib.bootstrap();

        // A body whose predicate is something else entirely — should not
        // touch the ITEM_VIEW handler.
        Body unrelated = Body.of(
                ItemRef.iid("cg.test:some-other-predicate"),
                List.of());

        long before = Session.itemViewHandlerInvocations();
        lib.submit(Frame.of(unrelated, List.of()));
        long after = Session.itemViewHandlerInvocations();

        assertThat(after).isEqualTo(before);
    }
}
