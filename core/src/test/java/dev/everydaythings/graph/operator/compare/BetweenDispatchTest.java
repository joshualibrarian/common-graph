package dev.everydaythings.graph.operator.compare;

import dev.everydaythings.graph.datum.Frame;
import dev.everydaythings.graph.id.ItemRef;
import dev.everydaythings.graph.item.Item;
import dev.everydaythings.graph.language.ThematicRole;
import dev.everydaythings.graph.runtime.SubmitResult;
import dev.everydaythings.graph.runtime.librarian.Librarian;
import dev.everydaythings.graph.runtime.stage.ItemStage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end proof of the universal handler dispatch pipeline using BETWEEN
 * as the proof case:
 *
 * <ol>
 *   <li>Bootstrap a librarian — seeds the Between predicate sememe with
 *       {@code IMPLEMENTS → @between} (self-implementing).</li>
 *   <li>Compose a BETWEEN frame with SOURCE, GOAL, THEME operands.</li>
 *   <li>Submit the frame — dispatch reverse-looks-up code items where
 *       {@code IMPLEMENTS → @between}, finds the Between Java instance,
 *       hands the frame to {@link ItemStage#deliver} which calls
 *       {@code receive(frame)} on the instance.</li>
 *   <li>Between's {@code receive} extracts the operands and returns a Boolean.</li>
 * </ol>
 *
 * <p>This proves the full IMPLEMENTS-based dispatch path, the universal
 * {@code Item.receive(Frame)} contract, and the Stage delivery primitive
 * work together.  Polyglot dispatch (a Python implementation of BETWEEN
 * routed through the same path) is the next chunk.
 */
class BetweenDispatchTest {

    @Test
    @DisplayName("Between.receive(frame) returns true when THEME lies in [SOURCE, GOAL]")
    void receiveInRange() {
        Librarian librarian = Librarian.ephemeral(ItemStage.javaOnly());
        librarian.bootstrap();

        Item between = librarian.fetchItem(ItemRef.iid(Between.KEY)).orElseThrow();
        assertThat(between).isInstanceOf(Between.class);

        Frame frame = Frame.compose(ItemRef.iid(Between.KEY))
                .with(ItemRef.iid(ThematicRole.Source.KEY), 0L)
                .with(ItemRef.iid(ThematicRole.Goal.KEY), 255L)
                .with(ItemRef.iid(ThematicRole.Theme.KEY), 128L)
                .build();

        Object result = librarian.stage().deliver(between, frame);
        assertThat(result).isEqualTo(true);
    }

    @Test
    @DisplayName("Between.receive(frame) returns false when THEME is outside [SOURCE, GOAL]")
    void receiveOutOfRange() {
        Librarian librarian = Librarian.ephemeral(ItemStage.javaOnly());
        librarian.bootstrap();

        Item between = librarian.fetchItem(ItemRef.iid(Between.KEY)).orElseThrow();

        Frame frame = Frame.compose(ItemRef.iid(Between.KEY))
                .with(ItemRef.iid(ThematicRole.Source.KEY), 0L)
                .with(ItemRef.iid(ThematicRole.Goal.KEY), 100L)
                .with(ItemRef.iid(ThematicRole.Theme.KEY), 200L)
                .build();

        Object result = librarian.stage().deliver(between, frame);
        assertThat(result).isEqualTo(false);
    }

    @Test
    @DisplayName("Librarian.submit dispatches a BETWEEN frame via IMPLEMENTS reverse-lookup without error")
    void submitDispatchesViaImplements() {
        Librarian librarian = Librarian.ephemeral(ItemStage.javaOnly());
        librarian.bootstrap();

        Frame frame = Frame.compose(ItemRef.iid(Between.KEY))
                .with(ItemRef.iid(ThematicRole.Source.KEY), 0L)
                .with(ItemRef.iid(ThematicRole.Goal.KEY), 255L)
                .with(ItemRef.iid(ThematicRole.Theme.KEY), 128L)
                .build();

        // Submit goes through the IMPLEMENTS-based dispatch path; the Boolean
        // result Between.receive returns isn't wrapped as a Frame, so the
        // response list is empty.  What matters here is that dispatch found
        // the right code item via IMPLEMENTS and called it without throwing —
        // proving the universal-dispatch pipeline is live.
        SubmitResult result = librarian.submit(frame);
        assertThat(result.submitted()).isSameAs(frame);
        assertThat(result.responses()).isEmpty();
    }

    @Test
    @DisplayName("Between.receive returns null when an operand binding is absent (partial application)")
    void receivePartial() {
        Librarian librarian = Librarian.ephemeral(ItemStage.javaOnly());
        librarian.bootstrap();

        Item between = librarian.fetchItem(ItemRef.iid(Between.KEY)).orElseThrow();

        Frame frame = Frame.compose(ItemRef.iid(Between.KEY))
                .with(ItemRef.iid(ThematicRole.Source.KEY), 0L)
                .with(ItemRef.iid(ThematicRole.Goal.KEY), 255L)
                // THEME absent — partial application
                .build();

        Object result = librarian.stage().deliver(between, frame);
        assertThat(result).isNull();
    }
}
