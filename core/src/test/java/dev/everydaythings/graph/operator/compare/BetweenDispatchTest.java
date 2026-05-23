package dev.everydaythings.graph.operator.compare;

import dev.everydaythings.graph.datum.Body;
import dev.everydaythings.graph.datum.Frame;
import dev.everydaythings.graph.ref.ItemRef;
import dev.everydaythings.graph.item.Item;
import dev.everydaythings.graph.language.ThematicRole;
import dev.everydaythings.graph.runtime.SubmitResult;
import dev.everydaythings.graph.runtime.librarian.Librarian;
import dev.everydaythings.graph.runtime.stage.ItemStage;
import dev.everydaythings.graph.value.Bool;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

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
 *   <li>Operator.receive wraps the Boolean primitive into a value-body
 *       Frame ({@code Body{ head=Bool, atomicContent=true|false }}) so
 *       dispatch can propagate it as a response.</li>
 * </ol>
 *
 * <p>This proves the full IMPLEMENTS-based dispatch path, the universal
 * {@code Item.receive(Frame)} contract, the value-body return convention,
 * and the Stage delivery primitive work together.
 */
class BetweenDispatchTest {

    private static final ItemRef BOOL_ARCHETYPE = ItemRef.iid(Bool.KEY);

    @Test
    @DisplayName("Between.receive wraps true in a Bool value-body when THEME lies in [SOURCE, GOAL]")
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

        Body valueBody = singleValueBody(librarian.stage().deliver(between, frame));
        assertThat(valueBody.headRef()).isEqualTo(BOOL_ARCHETYPE);
        assertThat(valueBody.atomicContent()).contains(true);
    }

    @Test
    @DisplayName("Between.receive wraps false in a Bool value-body when THEME is outside [SOURCE, GOAL]")
    void receiveOutOfRange() {
        Librarian librarian = Librarian.ephemeral(ItemStage.javaOnly());
        librarian.bootstrap();

        Item between = librarian.fetchItem(ItemRef.iid(Between.KEY)).orElseThrow();

        Frame frame = Frame.compose(ItemRef.iid(Between.KEY))
                .with(ItemRef.iid(ThematicRole.Source.KEY), 0L)
                .with(ItemRef.iid(ThematicRole.Goal.KEY), 100L)
                .with(ItemRef.iid(ThematicRole.Theme.KEY), 200L)
                .build();

        Body valueBody = singleValueBody(librarian.stage().deliver(between, frame));
        assertThat(valueBody.headRef()).isEqualTo(BOOL_ARCHETYPE);
        assertThat(valueBody.atomicContent()).contains(false);
    }

    @Test
    @DisplayName("Librarian.submit dispatches a BETWEEN frame and surfaces the value-body response")
    void submitDispatchesViaImplements() {
        Librarian librarian = Librarian.ephemeral(ItemStage.javaOnly());
        librarian.bootstrap();

        Frame frame = Frame.compose(ItemRef.iid(Between.KEY))
                .with(ItemRef.iid(ThematicRole.Source.KEY), 0L)
                .with(ItemRef.iid(ThematicRole.Goal.KEY), 255L)
                .with(ItemRef.iid(ThematicRole.Theme.KEY), 128L)
                .build();

        SubmitResult result = librarian.submit(frame);
        assertThat(result.submitted()).isSameAs(frame);
        assertThat(result.responses())
                .as("Submit should surface the Bool value-body response from Between")
                .hasSize(1);
        Body valueBody = result.responses().get(0).body();
        assertThat(valueBody.headRef()).isEqualTo(BOOL_ARCHETYPE);
        assertThat(valueBody.atomicContent()).contains(true);
    }

    @Test
    @DisplayName("Between.receive returns an empty response list when an operand binding is absent (partial application)")
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
        assertThat(result)
                .as("Partial-application returns an empty list (nothing to substitute)")
                .isEqualTo(List.of());
    }

    /** Extract the single value-body Frame from a List<Frame> return; fail clearly otherwise. */
    @SuppressWarnings("unchecked")
    private static Body singleValueBody(Object deliveryResult) {
        assertThat(deliveryResult).isInstanceOf(List.class);
        List<Frame> frames = (List<Frame>) deliveryResult;
        assertThat(frames).hasSize(1);
        return frames.get(0).body();
    }
}
