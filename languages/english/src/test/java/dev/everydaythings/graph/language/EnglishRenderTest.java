package dev.everydaythings.graph.language;

import dev.everydaythings.graph.id.ItemRef;
import dev.everydaythings.graph.item.Item;
import dev.everydaythings.graph.operator.math.Add;
import dev.everydaythings.graph.runtime.librarian.Librarian;
import dev.everydaythings.graph.text.FrameMap;
import dev.everydaythings.graph.text.FrameMap.BindingMap;
import dev.everydaythings.graph.text.FrameMap.Part;
import dev.everydaythings.graph.text.ParseParams;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Smoke test for {@link English}'s transitive-verb render path: ADD{THEME=5,
 * GOAL=3} → "add 5 to 3".
 *
 * <p>Exercises the lexicon-driven render machinery end-to-end — verb lemma
 * lookup, role-to-preposition resolution via the role item's own English
 * Preposition Lexeme frame, and target rendering for plain integer literals.
 */
class EnglishRenderTest {

    private Librarian librarian;
    private English english;

    @BeforeEach
    void setUp() {
        librarian = Librarian.inMemory();
        librarian.bootstrap();
        Optional<Item> englishItem = librarian.fetchItem(ItemRef.iid(Language.English.KEY));
        assertThat(englishItem).as("English seeded into bootstrap").isPresent();
        assertThat(englishItem.get()).isInstanceOf(English.class);
        english = (English) englishItem.get();
    }

    @Test
    @DisplayName("ADD{THEME=5, GOAL=3} → \"<add|sum> 5 to 3\"")
    void rendersTransitiveAddWithGoalPreposition() {
        FrameMap input = new FrameMap(
                null,
                new Part<>(ItemRef.of(ItemRef.iid(Add.KEY)),
                        new BigDecimal("1.0"), List.of()),
                List.of(
                        binding(ThematicRole.Theme.KEY, 5L),
                        binding(ThematicRole.Goal.KEY, 3L)),
                List.of());

        FrameMap rendered = english.render(input, ParseParams.defaults());

        // Add carries two English verb-lemmas ("add", "sum"); the frame-iteration
        // order is canonical-hash, not source-order, so either lemma may surface
        // until English gains primary/register-aware lemma selection. The smoke
        // test asserts the grammar shape rather than the specific verb choice.
        assertThat(rendered.text()).matches("(add|sum) 5 to 3");
    }

    private static BindingMap binding(String roleKey, Object target) {
        return new BindingMap(
                new Part<>(ItemRef.of(ItemRef.iid(roleKey)),
                        new BigDecimal("1.0"), List.of()),
                List.of(),
                new Part<>(target, new BigDecimal("1.0"), List.of()));
    }
}
