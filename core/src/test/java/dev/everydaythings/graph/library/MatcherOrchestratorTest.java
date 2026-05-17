package dev.everydaythings.graph.library;

import dev.everydaythings.graph.CoreVocabulary;
import dev.everydaythings.graph.datum.Binding;
import dev.everydaythings.graph.datum.Body;
import dev.everydaythings.graph.datum.Frame;
import dev.everydaythings.graph.id.ItemRef;
import dev.everydaythings.graph.id.TypeRef;
import dev.everydaythings.graph.language.ThematicRole;
import dev.everydaythings.graph.operator.Operator;
import dev.everydaythings.graph.operator.compare.Between;
import dev.everydaythings.graph.operator.compare.Equal;
import dev.everydaythings.graph.operator.math.Add;
import dev.everydaythings.graph.runtime.SubmitResult;
import dev.everydaythings.graph.runtime.librarian.Librarian;
import dev.everydaythings.graph.runtime.stage.ItemStage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Matcher orchestrator — finds items matching a query frame.
 *
 * <p>First-cut tests exercise the foundational shape: TypeRef head matches
 * items by archetype; concrete-target binding constraints match by exact
 * value.  The seeded Operator family (Add, Between, Equal, …) gives us a
 * ready-made population to query against — they all have {@code head = @operator}
 * by virtue of {@code @Seed.Item(head = Operator.KEY)}.
 */
class MatcherOrchestratorTest {

    @Nested
    @DisplayName("TypeRef head")
    class TypeRefHead {

        @Test
        @DisplayName("?operator finds every operator sememe seeded at bootstrap")
        void findsAllOperators() {
            Librarian librarian = Librarian.ephemeral(ItemStage.javaOnly());
            librarian.bootstrap();

            Body query = Body.of(TypeRef.iid(Operator.KEY), List.of());
            SubmitResult result = librarian.submit(Frame.of(query, List.of()));

            Set<ItemRef> matchedIids = extractMatchedIids(result.responses());

            // Operators we know are seeded (some among many).
            assertThat(matchedIids).contains(
                    ItemRef.iid(Add.KEY),
                    ItemRef.iid(Between.KEY),
                    ItemRef.iid(Equal.KEY));
        }

        @Test
        @DisplayName("each result is a RESULT frame with @theme → matched IID")
        void resultsAreResultFrames() {
            Librarian librarian = Librarian.ephemeral(ItemStage.javaOnly());
            librarian.bootstrap();

            Body query = Body.of(TypeRef.iid(Operator.KEY), List.of());
            SubmitResult result = librarian.submit(Frame.of(query, List.of()));

            assertThat(result.responses()).isNotEmpty();
            for (Frame f : result.responses()) {
                assertThat(f.body().head()).isEqualTo(ItemRef.iid(CoreVocabulary.Result.KEY));
                // Each carries a THEME binding pointing at the matched item's IID.
                assertThat(f.body().bindingsByRole(ItemRef.iid(ThematicRole.Theme.KEY)))
                        .isNotEmpty();
            }
        }
    }

    @Nested
    @DisplayName("Binding constraints")
    class BindingConstraints {

        @Test
        @DisplayName("?operator with arity=3 constraint narrows to ternary operators only")
        void filtersByConcreteBindingTarget() {
            Librarian librarian = Librarian.ephemeral(ItemStage.javaOnly());
            librarian.bootstrap();

            // Between has Arity=3 (declared via @Seed.Item.bindings on Between.java);
            // many other operators have Arity=2 (Add, Equal, …).  Filtering by Arity=3
            // should pick out Between and not Add or Equal.
            Body query = Body.of(TypeRef.iid(Operator.KEY), List.of(
                    Binding.literal(ItemRef.iid(Operator.Arity.KEY), 3L)));
            SubmitResult result = librarian.submit(Frame.of(query, List.of()));

            Set<ItemRef> matched = extractMatchedIids(result.responses());
            assertThat(matched).contains(ItemRef.iid(Between.KEY));
            assertThat(matched).doesNotContain(
                    ItemRef.iid(Add.KEY),
                    ItemRef.iid(Equal.KEY));
        }
    }

    @Nested
    @DisplayName("Non-matching query")
    class NonMatching {

        @Test
        @DisplayName("query with no matches returns an empty response list")
        void noMatches() {
            Librarian librarian = Librarian.ephemeral(ItemStage.javaOnly());
            librarian.bootstrap();

            // No operator has Arity=999.
            Body query = Body.of(TypeRef.iid(Operator.KEY), List.of(
                    Binding.literal(ItemRef.iid(Operator.Arity.KEY), 999L)));
            SubmitResult result = librarian.submit(Frame.of(query, List.of()));

            assertThat(result.responses()).isEmpty();
        }
    }

    private static Set<ItemRef> extractMatchedIids(List<Frame> results) {
        return results.stream()
                .flatMap(f -> f.body()
                        .bindingsByRole(ItemRef.iid(ThematicRole.Theme.KEY))
                        .stream())
                .map(Binding::target)
                .filter(t -> t instanceof ItemRef)
                .map(t -> (ItemRef) t)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }
}
