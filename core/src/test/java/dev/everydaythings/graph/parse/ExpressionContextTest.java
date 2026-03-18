package dev.everydaythings.graph.parse;

import dev.everydaythings.graph.parse.ExpressionToken.RefToken;
import dev.everydaythings.graph.item.Item;
import dev.everydaythings.graph.item.id.ItemID;
import dev.everydaythings.graph.language.*;
import dev.everydaythings.graph.runtime.Librarian;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link ExpressionContext} — partial-frame analysis and
 * completion narrowing.
 *
 * <p>Uses a real Librarian with seed vocabulary to get properly hydrated
 * CoreVocabulary, LexicalVocabulary, and PrepositionVocabulary items.
 */
@Tag("slow")
class ExpressionContextTest {

    private Librarian lib;
    private Function<ItemID, Optional<Item>> resolver;

    @BeforeEach
    void setUp(@TempDir Path testDir) {
        lib = Librarian.open(testDir);
        resolver = iid -> lib.get(iid);
    }

    @AfterEach
    void tearDown() {
        if (lib != null) lib.close();
    }

    // ==================================================================================
    // Analyze
    // ==================================================================================

    @Nested
    class Analyze {

        @Test
        void emptyTokensReturnsEmpty() {
            ExpressionContext ctx = ExpressionContext.analyze(List.of(), resolver);
            assertThat(ctx.verb()).isNull();
            assertThat(ctx.filledRoles()).isEmpty();
            assertThat(ctx.lastTokenIsPreposition()).isFalse();
        }

        @Test
        void verbTokenSetsVerb() {
            List<ExpressionToken> tokens = List.of(
                    RefToken.of(CoreVocabulary.Create.IID, "create")
            );

            ExpressionContext ctx = ExpressionContext.analyze(tokens, resolver);

            assertThat(ctx.verb()).isNotNull();
            assertThat(ctx.verb().iid()).isEqualTo(CoreVocabulary.Create.IID);
            assertThat(ctx.filledRoles()).isEmpty();
        }

        @Test
        void verbPlusNounFillsThemeRole() {
            // Use TITLE (a CoreVocabulary seed) as the argument item
            List<ExpressionToken> tokens = List.of(
                    RefToken.of(CoreVocabulary.Create.IID, "create"),
                    RefToken.of(CoreVocabulary.Title.IID, "title")
            );

            ExpressionContext ctx = ExpressionContext.analyze(tokens, resolver);

            assertThat(ctx.verb()).isNotNull();
            assertThat(ctx.filledRoles()).containsExactly(ThematicRole.Theme.IID);
            assertThat(ctx.unfilledRoles()).hasSize(4); // TARGET, NAME, COMITATIVE, SOURCE
            assertThat(ctx.unfilledRoles().get(0)).isEqualTo(ThematicRole.Goal.IID);
        }

        @Test
        void verbPlusPrepositionPlusNounFillsRole() {
            List<ExpressionToken> tokens = List.of(
                    RefToken.of(CoreVocabulary.Create.IID, "create"),
                    RefToken.of(PrepositionVocabulary.On.IID, "on"),
                    RefToken.of(CoreVocabulary.Title.IID, "title")
            );

            ExpressionContext ctx = ExpressionContext.analyze(tokens, resolver);

            assertThat(ctx.verb()).isNotNull();
            assertThat(ctx.filledRoles()).contains(ThematicRole.Goal.IID);
            assertThat(ctx.lastTokenIsPreposition()).isFalse();
        }

        @Test
        void trailingPrepositionDetected() {
            List<ExpressionToken> tokens = List.of(
                    RefToken.of(CoreVocabulary.Create.IID, "create"),
                    RefToken.of(PrepositionVocabulary.On.IID, "on")
            );

            ExpressionContext ctx = ExpressionContext.analyze(tokens, resolver);

            assertThat(ctx.verb()).isNotNull();
            assertThat(ctx.lastTokenIsPreposition()).isTrue();
        }

        @Test
        void verbWithAllRolesFilledShowsNoUnfilled() {
            List<ExpressionToken> tokens = List.of(
                    RefToken.of(CoreVocabulary.Create.IID, "create"),
                    RefToken.of(CoreVocabulary.Title.IID, "title"),
                    RefToken.of(PrepositionVocabulary.On.IID, "on"),
                    RefToken.of(CoreVocabulary.Author.IID, "author")
            );

            ExpressionContext ctx = ExpressionContext.analyze(tokens, resolver);

            assertThat(ctx.verb()).isNotNull();
            assertThat(ctx.filledRoles()).contains(ThematicRole.Theme.IID, ThematicRole.Goal.IID);
            assertThat(ctx.unfilledRoles()).hasSize(3); // NAME, COMITATIVE, SOURCE
        }

        @Test
        void nonRefTokensIgnored() {
            List<ExpressionToken> tokens = List.of(
                    RefToken.of(CoreVocabulary.Create.IID, "create"),
                    ExpressionToken.LiteralToken.ofString("hello")
            );

            ExpressionContext ctx = ExpressionContext.analyze(tokens, resolver);

            assertThat(ctx.verb()).isNotNull();
            // Literal doesn't fill a role (it's not a resolvable Item)
            assertThat(ctx.filledRoles()).isEmpty();
        }

        @Test
        void noVerbReturnsEmpty() {
            List<ExpressionToken> tokens = List.of(
                    RefToken.of(CoreVocabulary.Title.IID, "title")
            );

            ExpressionContext ctx = ExpressionContext.analyze(tokens, resolver);

            assertThat(ctx.verb()).isNull();
            assertThat(ctx).isEqualTo(ExpressionContext.EMPTY);
        }
    }

    // ==================================================================================
    // Filter
    // ==================================================================================

    @Nested
    class Filter {

        @Test
        void noVerbShowsEverything() {
            ExpressionContext ctx = ExpressionContext.EMPTY;

            List<Posting> postings = List.of(
                    Posting.universal("create", CoreVocabulary.Create.IID),
                    Posting.universal("on", PrepositionVocabulary.On.IID),
                    Posting.universal("title", CoreVocabulary.Title.IID)
            );

            List<Posting> filtered = ctx.filter(postings, resolver);
            assertThat(filtered).hasSize(3);
        }

        @Test
        void verbPresentExcludesOtherVerbs() {
            List<ExpressionToken> tokens = List.of(
                    RefToken.of(CoreVocabulary.Create.IID, "create")
            );
            ExpressionContext ctx = ExpressionContext.analyze(tokens, resolver);

            List<Posting> postings = List.of(
                    Posting.universal("get", CoreVocabulary.Get.IID),
                    Posting.universal("on", PrepositionVocabulary.On.IID),
                    Posting.universal("title", CoreVocabulary.Title.IID)
            );

            List<Posting> filtered = ctx.filter(postings, resolver);

            assertThat(filtered).hasSize(2);
            assertThat(filtered).noneMatch(p -> p.token().equals("get"));
        }

        @Test
        void openPrepositionExcludesVerbsAndPrepositions() {
            List<ExpressionToken> tokens = List.of(
                    RefToken.of(CoreVocabulary.Create.IID, "create"),
                    RefToken.of(PrepositionVocabulary.On.IID, "on")
            );
            ExpressionContext ctx = ExpressionContext.analyze(tokens, resolver);
            assertThat(ctx.lastTokenIsPreposition()).isTrue();

            List<Posting> postings = List.of(
                    Posting.universal("get", CoreVocabulary.Get.IID),
                    Posting.universal("on", PrepositionVocabulary.On.IID),
                    Posting.universal("title", CoreVocabulary.Title.IID)
            );

            List<Posting> filtered = ctx.filter(postings, resolver);

            // Only "title" should survive — verbs and prepositions excluded
            assertThat(filtered).hasSize(1);
            assertThat(filtered.get(0).token()).isEqualTo("title");
        }

        @Test
        void unresolvablePostingsKept() {
            List<ExpressionToken> tokens = List.of(
                    RefToken.of(CoreVocabulary.Create.IID, "create")
            );
            ExpressionContext ctx = ExpressionContext.analyze(tokens, resolver);

            ItemID unknown = ItemID.random();
            List<Posting> postings = List.of(
                    Posting.universal("unknown", unknown)
            );

            List<Posting> filtered = ctx.filter(postings, resolver);
            assertThat(filtered).hasSize(1);
        }

        @Test
        void nounSememesAlwaysPass() {
            List<ExpressionToken> tokens = List.of(
                    RefToken.of(CoreVocabulary.Create.IID, "create")
            );
            ExpressionContext ctx = ExpressionContext.analyze(tokens, resolver);

            List<Posting> postings = List.of(
                    Posting.universal("title", CoreVocabulary.Title.IID),
                    Posting.universal("author", CoreVocabulary.Author.IID)
            );

            List<Posting> filtered = ctx.filter(postings, resolver);
            assertThat(filtered).hasSize(2);
        }
    }
}
