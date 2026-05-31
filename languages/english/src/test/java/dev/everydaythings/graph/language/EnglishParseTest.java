package dev.everydaythings.graph.language;

import dev.everydaythings.graph.ref.ItemRef;
import dev.everydaythings.graph.item.Item;
import dev.everydaythings.graph.operator.math.Add;
import dev.everydaythings.graph.operator.math.Subtract;
import dev.everydaythings.graph.runtime.librarian.Librarian;
import dev.everydaythings.graph.text.FrameMap;
import dev.everydaythings.graph.text.FrameMap.BindingMap;
import dev.everydaythings.graph.text.ParseParams;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Smoke test for {@link English}'s transitive-verb parse path: "add 5 to 3"
 * → ADD{THEME=5, GOAL=3}.
 *
 * <p>Exercises the full pipeline through {@link Item#parse(String, ParseParams)}
 * — tokenization, dictionary lookup, English as a Language-stack participant in
 * the consensus round, and the merge into the orchestrator's draft.
 */
class EnglishParseTest {

    private Librarian librarian;
    private Item orchestrator;
    private ParseParams englishParams;

    @BeforeEach
    void setUp() {
        librarian = Librarian.inMemory();
        librarian.bootstrap();
        Optional<Item> englishItem = librarian.fetchItem(ItemRef.iid(Language.English.KEY));
        assertThat(englishItem).as("English seeded into bootstrap").isPresent();

        orchestrator = new Item(ItemRef.fromString("test.english-parse-orchestrator"),
                librarian);

        englishParams = ParseParams.defaults().toBuilder()
                .languageStack(List.of(ItemRef.iid(Language.English.KEY)))
                .build();
    }

    @Test
    @DisplayName("\"add 5 to 3\" → ADD{THEME=5, GOAL=3}")
    void parsesTransitiveVerbWithGoalPreposition() {
        FrameMap result = orchestrator.parse("add 5 to 3", englishParams);

        assertThat(result.predicate()).as("predicate present").isNotNull();
        assertThat(result.predicate().value().iid())
                .as("predicate is ADD")
                .isEqualTo(ItemRef.iid(Add.KEY));

        BindingMap theme = bindingForRole(result, dev.everydaythings.graph.ThematicRole.Theme.KEY);
        assertThat(theme).as("THEME binding present").isNotNull();
        assertThat(theme.target().value())
                .as("THEME = 5").isEqualTo(5L);

        BindingMap goal = bindingForRole(result, dev.everydaythings.graph.ThematicRole.Goal.KEY);
        assertThat(goal).as("GOAL binding present").isNotNull();
        assertThat(goal.target().value())
                .as("GOAL = 3").isEqualTo(3L);
    }

    @Test
    @DisplayName("\"subtract 3 from 10\" → SUBTRACT{THEME=3, SOURCE=10}")
    void parsesTransitiveVerbWithSourcePreposition() {
        // Exercises a non-GOAL role marker: "from" → SOURCE.
        FrameMap result = orchestrator.parse("subtract 3 from 10", englishParams);

        assertThat(result.predicate()).as("predicate present").isNotNull();
        assertThat(result.predicate().value().iid())
                .as("predicate is SUBTRACT")
                .isEqualTo(ItemRef.iid(Subtract.KEY));

        BindingMap theme = bindingForRole(result, dev.everydaythings.graph.ThematicRole.Theme.KEY);
        assertThat(theme).as("THEME binding present").isNotNull();
        assertThat(theme.target().value())
                .as("THEME = 3 (the quantity being removed)")
                .isEqualTo(3L);

        BindingMap source = bindingForRole(result, dev.everydaythings.graph.ThematicRole.Source.KEY);
        assertThat(source).as("SOURCE binding present").isNotNull();
        assertThat(source.target().value())
                .as("SOURCE = 10 (the quantity removed from)")
                .isEqualTo(10L);
    }

    @Test
    @DisplayName("\"5 + 3\" with English in stack → OperatorNotation wins (ADD{THEME=5, GOAL=3})")
    void englishYieldsToOperatorNotationOnSymbolicInput() {
        // English is in the language stack but its parse contributes empty for
        // input that holds no English verb-lemma. OperatorNotation (anchored
        // automatically via the "+" symbol resolving to Add) wins consensus.
        FrameMap result = orchestrator.parse("5 + 3", englishParams);

        assertThat(result.predicate()).as("predicate present").isNotNull();
        assertThat(result.predicate().value().iid())
                .as("OperatorNotation wins — predicate is ADD")
                .isEqualTo(ItemRef.iid(Add.KEY));

        BindingMap theme = bindingForRole(result, dev.everydaythings.graph.ThematicRole.Theme.KEY);
        assertThat(theme).isNotNull();
        assertThat(theme.target().value()).isEqualTo(5L);

        BindingMap goal = bindingForRole(result, dev.everydaythings.graph.ThematicRole.Goal.KEY);
        assertThat(goal).isNotNull();
        assertThat(goal.target().value()).isEqualTo(3L);
    }

    private static BindingMap bindingForRole(FrameMap fm, String roleKey) {
        ItemRef role = ItemRef.iid(roleKey);
        for (BindingMap b : fm.bindings()) {
            if (b.role() == null || b.role().value() == null) continue;
            if (role.equals(b.role().value().iid())) return b;
        }
        return null;
    }
}
