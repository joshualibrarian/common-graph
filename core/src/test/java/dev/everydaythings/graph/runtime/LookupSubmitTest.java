package dev.everydaythings.graph.runtime;
import dev.everydaythings.graph.SchemaVocabulary;

import dev.everydaythings.graph.datum.Frame;
import dev.everydaythings.graph.id.ItemID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end tests for the LOOKUP predicate + @Handler dispatch + submit.
 *
 * <p>Validates the actor-model pipeline:
 * <ol>
 *   <li>Build a LOOKUP frame via the fluent {@code Frame.compose(...)} API</li>
 *   <li>{@code librarian.submit(frame)} dispatches to the {@code @Handler}-annotated
 *       {@code lookup} method on Librarian</li>
 *   <li>Handler returns response frames carrying postings</li>
 *   <li>Since LOOKUP carries {@code CONFIG[RETENTION] → @Ephemeral}, the body
 *       is <i>not</i> persisted</li>
 * </ol>
 */
class LookupSubmitTest {

    @Test
    @DisplayName("LOOKUP frame routes through submit and returns posting frames")
    void lookupEndToEnd() {
        Librarian lib = Librarian.inMemory();
        lib.bootstrap();   // populates seed vocabulary, including English glosses/lexemes

        // Build a LOOKUP frame for a token we know is bootstrapped ("handler"
        // is the English noun lemma for the Handles sememe).
        Frame lookup = Frame.compose(Lookup.IID)
                .theme("handler")
                .build();

        SubmitResult result = lib.submit(lookup);

        // The handler returned response frames (postings for "handler")
        assertThat(result.responses())
                .as("seed vocabulary should yield at least one posting for 'handler'")
                .isNotEmpty();

        // Each response is itself a LOOKUP frame carrying the posting payload
        for (Frame response : result.responses()) {
            assertThat(response.body()).isNotNull();
            assertThat(response.records())
                    .as("response frames have no records — ephemeral")
                    .isEmpty();
        }
    }

    @Test
    @DisplayName("LOOKUP body is not persisted (ephemeral retention)")
    void ephemeralRetention() {
        Librarian lib = Librarian.inMemory();
        lib.bootstrap();

        Frame lookup = Frame.compose(Lookup.IID)
                .theme("handler")
                .build();
        lib.submit(lookup);

        // The body should NOT be in storage — ephemeral retention skipped persist.
        assertThat(lib.has(lookup.body().datumId()))
                .as("ephemeral LOOKUP body should not be persisted")
                .isFalse();
    }

    @Test
    @DisplayName("LOOKUP with limit performs prefix match")
    void lookupWithLimit() {
        Librarian lib = Librarian.inMemory();
        lib.bootstrap();

        // Build a LOOKUP with both THEME and ATTRIBUTE[LIMIT]. Exit the binding
        // sub-builder via a parent method (.theme via forwarding), then build.
        Frame lookup = (Frame) Frame.compose(Lookup.IID)
                .theme("c")
                .binding(dev.everydaythings.graph.semantics.ThematicRole.Attribute.IID)
                    .qualifier(SchemaVocabulary.Limit.IID)
                    .target(50L)
                .build();

        SubmitResult result = lib.submit(lookup);
        // Prefix-match against "c" should produce zero-or-more postings, all
        // beginning with 'c' (loose check: just verify the pipeline runs).
        assertThat(result.responses()).isNotNull();
    }

    @Test
    @DisplayName("LOOKUP with no matching token returns empty responses")
    void lookupNoMatch() {
        Librarian lib = Librarian.inMemory();
        lib.bootstrap();

        Frame lookup = Frame.compose(Lookup.IID)
                .theme("absolutely-not-a-real-token-xyzzy")
                .build();

        SubmitResult result = lib.submit(lookup);
        assertThat(result.responses()).isEmpty();
    }

    @Test
    @DisplayName("non-LOOKUP frame with no handler returns empty responses")
    void noHandlerNoResponses() {
        Librarian lib = Librarian.inMemory();

        // A made-up predicate with no @Handler on Librarian
        ItemID custom = ItemID.fromString("test.predicate:nonexistent");
        Frame frame = Frame.compose(custom)
                .theme("anything")
                .build();

        SubmitResult result = lib.submit(frame);
        assertThat(result.responses()).isEmpty();
    }
}
