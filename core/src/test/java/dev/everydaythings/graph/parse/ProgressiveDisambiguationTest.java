package dev.everydaythings.graph.parse;

import dev.everydaythings.graph.parse.ExpressionToken.*;
import dev.everydaythings.graph.item.Item;
import dev.everydaythings.graph.item.id.ItemID;
import dev.everydaythings.graph.language.PartOfSpeech;
import dev.everydaythings.graph.language.Posting;
import dev.everydaythings.graph.language.Sememe;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for the progressive disambiguation engine.
 *
 * <p>Verifies that {@link ExpressionContext#pruneForPosition} applies
 * constraint rules correctly, and that {@link InputController}'s iterative
 * pruning converges to correct resolutions.
 */
class ProgressiveDisambiguationTest {

    // ==================================================================================
    // Test IDs
    // ==================================================================================

    private static final ItemID CREATE_IID = ItemID.fromString("cg.verb:create");
    private static final ItemID SET_GAME_IID = ItemID.random();
    private static final ItemID SET_VERB_IID = ItemID.random();
    private static final ItemID SET_NOUN_IID = ItemID.random();
    private static final ItemID CHESS_IID = ItemID.random();
    private static final ItemID ON_PREP_IID = ItemID.random();
    private static final ItemID NOUN_A_IID = ItemID.random();
    private static final ItemID NOUN_B_IID = ItemID.random();

    // ==================================================================================
    // ExpressionContext.pruneForPosition Tests
    // ==================================================================================

    @Nested
    class PruneForPosition {

        @Test
        void rule1_verbExclusionRemovesVerbCandidates() {
            // Verb already known → verb candidates should be removed
            ExpressionContext ctx = new ExpressionContext(
                    mockVerb(CREATE_IID), Set.of(), List.of(), false);

            List<Posting> candidates = List.of(
                    Posting.universal("set", SET_VERB_IID),
                    Posting.universal("set", SET_NOUN_IID)
            );

            Function<ItemID, Optional<Item>> resolver = iid -> {
                if (iid.equals(SET_VERB_IID)) return Optional.of(mockVerb(SET_VERB_IID));
                if (iid.equals(SET_NOUN_IID)) return Optional.of(mockNoun(SET_NOUN_IID));
                return Optional.empty();
            };

            List<Posting> surviving = ctx.pruneForPosition(
                    0, candidates, List.of(), resolver);

            assertThat(surviving).hasSize(1);
            assertThat(surviving.get(0).target()).isEqualTo(SET_NOUN_IID);
        }

        @Test
        void rule2_prepositionExclusionAfterDanglingPrep() {
            // Last token is a preposition → preposition candidates excluded
            ExpressionContext ctx = new ExpressionContext(
                    mockVerb(CREATE_IID), Set.of(), List.of(), true);

            List<Posting> candidates = List.of(
                    Posting.universal("on", ON_PREP_IID),
                    Posting.universal("on", NOUN_A_IID)
            );

            Function<ItemID, Optional<Item>> resolver = iid -> {
                if (iid.equals(ON_PREP_IID)) return Optional.of(mockPreposition(ON_PREP_IID));
                if (iid.equals(NOUN_A_IID)) return Optional.of(mockNoun(NOUN_A_IID));
                return Optional.empty();
            };

            List<Posting> surviving = ctx.pruneForPosition(
                    0, candidates, List.of(), resolver);

            assertThat(surviving).hasSize(1);
            assertThat(surviving.get(0).target()).isEqualTo(NOUN_A_IID);
        }

        @Test
        void rule5_prepObjectExcludesPrepositions() {
            // Previous token is a resolved preposition → preposition candidates excluded
            ExpressionContext ctx = new ExpressionContext(
                    mockVerb(CREATE_IID), Set.of(), List.of(), false);

            List<ExpressionToken> allTokens = List.of(
                    RefToken.of(ON_PREP_IID, "on"),
                    new CandidateToken("for", List.of(
                            Posting.universal("for", ItemID.random()),
                            Posting.universal("for", NOUN_A_IID)
                    ))
            );

            List<Posting> candidates = List.of(
                    Posting.universal("for", ItemID.random()),
                    Posting.universal("for", NOUN_A_IID)
            );

            ItemID prepCandidateIid = candidates.get(0).target();

            Function<ItemID, Optional<Item>> resolver = iid -> {
                if (iid.equals(ON_PREP_IID)) return Optional.of(mockPreposition(ON_PREP_IID));
                if (iid.equals(prepCandidateIid)) return Optional.of(mockPreposition(prepCandidateIid));
                if (iid.equals(NOUN_A_IID)) return Optional.of(mockNoun(NOUN_A_IID));
                return Optional.empty();
            };

            List<Posting> surviving = ctx.pruneForPosition(
                    1, candidates, allTokens, resolver);

            assertThat(surviving).hasSize(1);
            assertThat(surviving.get(0).target()).isEqualTo(NOUN_A_IID);
        }

        @Test
        void rule6_posExclusionWhenAllRolesFilled() {
            // All roles filled → noun candidates excluded
            ExpressionContext ctx = new ExpressionContext(
                    mockVerb(CREATE_IID),
                    Set.of(ItemID.fromString("cg.role:theme")),
                    List.of(), // no unfilled roles
                    false);

            List<Posting> candidates = List.of(
                    Posting.universal("extra", NOUN_A_IID),
                    Posting.universal("extra", NOUN_B_IID)
            );

            Function<ItemID, Optional<Item>> resolver = iid -> {
                if (iid.equals(NOUN_A_IID)) return Optional.of(mockNoun(NOUN_A_IID));
                if (iid.equals(NOUN_B_IID)) return Optional.of(mockNoun(NOUN_B_IID));
                return Optional.empty();
            };

            List<Posting> surviving = ctx.pruneForPosition(
                    0, candidates, List.of(), resolver);

            // Both are nouns, all roles filled → both excluded
            assertThat(surviving).isEmpty();
        }

        @Test
        void noVerbMeansNoFiltering() {
            ExpressionContext ctx = ExpressionContext.EMPTY;

            List<Posting> candidates = List.of(
                    Posting.universal("set", SET_VERB_IID),
                    Posting.universal("set", SET_NOUN_IID)
            );

            Function<ItemID, Optional<Item>> resolver = iid -> Optional.empty();

            List<Posting> surviving = ctx.pruneForPosition(
                    0, candidates, List.of(), resolver);

            // No verb → all pass through (rules don't fire)
            assertThat(surviving).hasSize(2);
        }

        @Test
        void unresolvableCandidatesKept() {
            ExpressionContext ctx = new ExpressionContext(
                    mockVerb(CREATE_IID), Set.of(), List.of(), false);

            List<Posting> candidates = List.of(
                    Posting.universal("unknown", ItemID.random())
            );

            // Resolver can't resolve
            Function<ItemID, Optional<Item>> resolver = iid -> Optional.empty();

            List<Posting> surviving = ctx.pruneForPosition(
                    0, candidates, List.of(), resolver);

            assertThat(surviving).hasSize(1);
        }
    }

    // ==================================================================================
    // Iterative Convergence Tests (via InputController)
    // ==================================================================================

    @Nested
    class IterativeConvergence {

        @Test
        void singlePassResolvesUnambiguous() {
            // One candidate → should resolve immediately
            InputController input = InputController.builder()
                    .lookup(text -> {
                        if ("create".startsWith(text.toLowerCase())) {
                            return List.of(Posting.universal("create", CREATE_IID));
                        }
                        return List.of();
                    })
                    .prompt("> ")
                    .minLookupLength(1)
                    .build();

            input.type("create");
            input.tokenBoundary();

            assertThat(input.tokens()).hasSize(1);
            assertThat(input.tokens().get(0)).isInstanceOf(RefToken.class);
        }

        @Test
        void ambiguousTokenStaysAsCandidateWithoutContext() {
            // Two candidates, no context to disambiguate → stays as CandidateToken
            InputController input = InputController.builder()
                    .lookup(text -> {
                        if ("set".startsWith(text.toLowerCase())) {
                            return List.of(
                                    Posting.universal("set", SET_VERB_IID),
                                    Posting.universal("set", SET_NOUN_IID)
                            );
                        }
                        return List.of();
                    })
                    .prompt("> ")
                    .minLookupLength(1)
                    .build();

            input.type("set");
            input.tokenBoundary();

            assertThat(input.tokens()).hasSize(1);
            assertThat(input.tokens().get(0)).isInstanceOf(CandidateToken.class);
            assertThat(input.hasUnresolvedCandidates()).isTrue();
        }

        @Test
        void candidateTokenDisplayText() {
            CandidateToken candidate = new CandidateToken("set", List.of(
                    Posting.universal("set", SET_VERB_IID),
                    Posting.universal("set", SET_NOUN_IID)
            ));

            assertThat(candidate.displayText()).isEqualTo("set");
            assertThat(candidate.text()).isEqualTo("set");
            assertThat(candidate.candidates()).hasSize(2);
        }
    }

    // ==================================================================================
    // InputSnapshot Integration
    // ==================================================================================

    @Nested
    class SnapshotIntegration {

        @Test
        void snapshotReportsUnresolvedCandidates() {
            InputSnapshot snap = new InputSnapshot(
                    List.of(new CandidateToken("set", List.of(
                            Posting.universal("set", SET_VERB_IID),
                            Posting.universal("set", SET_NOUN_IID)
                    ))),
                    "", 0, List.of(), List.of(), -1, false,
                    "> ", "", null, -1
            );

            assertThat(snap.hasUnresolvedCandidates()).isTrue();
        }

        @Test
        void snapshotWithOnlyRefTokensHasNoCandidates() {
            InputSnapshot snap = new InputSnapshot(
                    List.of(RefToken.of(CREATE_IID, "create")),
                    "", 0, List.of(), List.of(), -1, false,
                    "> ", "", null, -1
            );

            assertThat(snap.hasUnresolvedCandidates()).isFalse();
        }
    }

    // ==================================================================================
    // Mock helpers
    // ==================================================================================

    private static Sememe mockVerb(ItemID iid) {
        return new Sememe("test:verb/" + iid.toString().substring(0, 8), PartOfSpeech.VERB);
    }

    private static Sememe mockNoun(ItemID iid) {
        return new Sememe("test:noun/" + iid.toString().substring(0, 8), PartOfSpeech.NOUN);
    }

    private static Sememe mockPreposition(ItemID iid) {
        return new Sememe("test:prep/" + iid.toString().substring(0, 8), PartOfSpeech.PREPOSITION);
    }
}
