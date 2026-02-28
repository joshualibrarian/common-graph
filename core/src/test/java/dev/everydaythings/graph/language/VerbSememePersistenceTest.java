package dev.everydaythings.graph.language;

import dev.everydaythings.graph.runtime.Librarian;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test verifying that VerbSememe argument slots survive
 * the full lifecycle: seed creation → commit → store → hydrate.
 */
class VerbSememePersistenceTest {

    @Test
    void createVerbArgumentsSurviveHydration(@TempDir Path testDir) {
        try (Librarian lib = Librarian.open(testDir)) {
            Optional<VerbSememe> createOpt = lib.get(Sememe.create.iid(), VerbSememe.class);

            assertThat(createOpt).as("CREATE verb should be retrievable").isPresent();

            VerbSememe create = createOpt.get();
            assertThat(create.arguments())
                    .as("CREATE should have argument slots after hydration")
                    .isNotEmpty()
                    .hasSize(5);

            // Verify first slot: THEME (optional, "what to create")
            ArgumentSlot themeSlot = create.arguments().get(0);
            assertThat(themeSlot.role()).isEqualTo(ThematicRole.THEME);
            assertThat(themeSlot.required()).isFalse();
            assertThat(themeSlot.typeConstraint()).isNull();
            assertThat(themeSlot.descriptions()).containsEntry("en", "what to create");

            // Verify second slot: TARGET (optional, "where to place the result")
            ArgumentSlot targetSlot = create.arguments().get(1);
            assertThat(targetSlot.role()).isEqualTo(ThematicRole.TARGET);
            assertThat(targetSlot.required()).isFalse();
            assertThat(targetSlot.descriptions()).containsEntry("en", "where to place the result");

            // Verify third slot: NAME (optional)
            assertThat(create.arguments().get(2).role()).isEqualTo(ThematicRole.NAME);

            // Verify fourth slot: COMITATIVE (optional)
            assertThat(create.arguments().get(3).role()).isEqualTo(ThematicRole.COMITATIVE);

            // Verify fifth slot: SOURCE (optional)
            assertThat(create.arguments().get(4).role()).isEqualTo(ThematicRole.SOURCE);
        }
    }

    @Test
    void getVerbArgumentsSurviveHydration(@TempDir Path testDir) {
        try (Librarian lib = Librarian.open(testDir)) {
            Optional<VerbSememe> getOpt = lib.get(Sememe.get.iid(), VerbSememe.class);

            assertThat(getOpt).as("GET verb should be retrievable").isPresent();

            VerbSememe get = getOpt.get();
            assertThat(get.arguments())
                    .as("GET should have argument slots after hydration")
                    .isNotEmpty()
                    .hasSize(1);

            // GET has a required THEME slot
            ArgumentSlot themeSlot = get.arguments().get(0);
            assertThat(themeSlot.role()).isEqualTo(ThematicRole.THEME);
            assertThat(themeSlot.required()).isTrue();
            assertThat(themeSlot.descriptions()).containsEntry("en", "what to retrieve");
        }
    }

    @Test
    void relationVerbHasNoArguments(@TempDir Path testDir) {
        try (Librarian lib = Librarian.open(testDir)) {
            // HYPERNYM is a relation predicate, not a user-facing action — no arguments
            Optional<VerbSememe> hyperOpt = lib.get(Sememe.HYPERNYM.iid(), VerbSememe.class);

            assertThat(hyperOpt).as("HYPERNYM should be retrievable").isPresent();
            assertThat(hyperOpt.get().arguments())
                    .as("Relation verbs should have no argument slots")
                    .isEmpty();
        }
    }
}
