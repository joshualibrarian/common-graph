package dev.everydaythings.graph.parse;

import dev.everydaythings.graph.frame.Binding;
import dev.everydaythings.graph.frame.FrameBodyOld;
import dev.everydaythings.graph.item.Literal;
import dev.everydaythings.graph.item.id.CompoundKey;
import dev.everydaythings.graph.item.id.ItemID;
import dev.everydaythings.graph.language.CoreVocabulary;
import dev.everydaythings.graph.language.Posting;
import dev.everydaythings.graph.language.ThematicRole;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;

class TokenLatticeTest {

    // Test IDs
    private static final ItemID CREATE_IID = ItemID.fromString("cg.verb:create");
    private static final ItemID CHESS_IID = ItemID.fromString("cg.game:chess");
    private static final ItemID ADD_IID = ItemID.fromString("cg.op:add");
    private static final ItemID BOB_MARLEY_IID = ItemID.fromString("cg.person:bob-marley");
    private static final ItemID BOB_IID = ItemID.fromString("cg.person:bob");
    private static final ItemID ENGLISH = ItemID.fromString("cg:language/eng");

    // ==================================================================================
    // Basic tokenization
    // ==================================================================================

    @Test
    void emptyInput_producesEmptyPath() {
        var lattice = TokenLattice.build("", token -> List.of());
        assertThat(lattice.bestPath()).isEmpty();
    }

    @Test
    void singleWord_resolves() {
        Function<String, List<Posting>> lookup = token ->
                "create".equals(token) ? List.of(posting("create", CREATE_IID)) : List.of();

        var lattice = TokenLattice.build("create", lookup);
        var path = lattice.bestPath();

        assertThat(path).hasSize(1);
        assertThat(path.getFirst().text()).isEqualTo("create");
        assertThat(path.getFirst().isResolved()).isTrue();
        assertThat(path.getFirst().bestPosting().target()).isEqualTo(CREATE_IID);
    }

    @Test
    void twoWords_bothResolve() {
        Function<String, List<Posting>> lookup = token -> switch (token) {
            case "create" -> List.of(posting("create", CREATE_IID));
            case "chess" -> List.of(posting("chess", CHESS_IID));
            default -> List.of();
        };

        var lattice = TokenLattice.build("create chess", lookup);
        var path = lattice.bestPath();

        assertThat(path).hasSize(2);
        assertThat(path.get(0).text()).isEqualTo("create");
        assertThat(path.get(1).text()).isEqualTo("chess");
    }

    @Test
    void unresolvedWord_producesUnresolvedSpan() {
        var lattice = TokenLattice.build("xyzzy", token -> List.of());
        var path = lattice.bestPath();

        assertThat(path).hasSize(1);
        assertThat(path.getFirst().text()).isEqualTo("xyzzy");
        assertThat(path.getFirst().type()).isEqualTo(TokenLattice.SpanType.UNRESOLVED);
        assertThat(path.getFirst().isResolved()).isFalse();
    }

    // ==================================================================================
    // Character class boundary splitting
    // ==================================================================================

    @Test
    void operatorBetweenNumbers_splitsByCharClass() {
        Function<String, List<Posting>> lookup = token ->
                "+".equals(token) ? List.of(posting("+", ADD_IID)) : List.of();

        var lattice = TokenLattice.build("3+4", lookup);
        var path = lattice.bestPath();

        assertThat(path).hasSize(3);
        assertThat(path.get(0).text()).isEqualTo("3");
        assertThat(path.get(0).type()).isEqualTo(TokenLattice.SpanType.LITERAL);
        assertThat(path.get(1).text()).isEqualTo("+");
        assertThat(path.get(1).isResolved()).isTrue();
        assertThat(path.get(2).text()).isEqualTo("4");
        assertThat(path.get(2).type()).isEqualTo(TokenLattice.SpanType.LITERAL);
    }

    @Test
    void decimalNumber_staysTogether() {
        var lattice = TokenLattice.build("3.14", token -> List.of());
        var path = lattice.bestPath();

        assertThat(path).hasSize(1);
        assertThat(path.getFirst().text()).isEqualTo("3.14");
        assertThat(path.getFirst().type()).isEqualTo(TokenLattice.SpanType.LITERAL);
    }

    @Test
    void alphanumeric_staysTogether() {
        // "x2" should stay as one token (digit after letter)
        var lattice = TokenLattice.build("x2", token -> List.of());
        var path = lattice.bestPath();

        assertThat(path).hasSize(1);
        assertThat(path.getFirst().text()).isEqualTo("x2");
    }

    // ==================================================================================
    // Multi-word windows
    // ==================================================================================

    @Test
    void multiWordSpan_preferredOverIndividualWords() {
        Function<String, List<Posting>> lookup = token -> switch (token) {
            case "bob" -> List.of(posting("bob", BOB_IID, 0.5f));
            case "bob marley" -> List.of(posting("bob marley", BOB_MARLEY_IID, 1.0f));
            default -> List.of();
        };

        var lattice = TokenLattice.build("Bob Marley", lookup);
        var path = lattice.bestPath();

        // Should prefer the 2-word span "Bob Marley" over individual "Bob" + unresolved "Marley"
        assertThat(path).hasSize(1);
        assertThat(path.getFirst().text()).isEqualTo("Bob Marley");
        assertThat(path.getFirst().bestPosting().target()).isEqualTo(BOB_MARLEY_IID);
    }

    @Test
    void multiWordSpan_lowestScore_prefersIndividuals() {
        Function<String, List<Posting>> lookup = token -> switch (token) {
            case "bob" -> List.of(posting("bob", BOB_IID, 0.9f));
            case "marley" -> List.of(posting("marley", ItemID.fromString("cg.person:marley"), 0.9f));
            case "bob marley" -> List.of(posting("bob marley", BOB_MARLEY_IID, 0.3f));
            default -> List.of();
        };

        var lattice = TokenLattice.build("Bob Marley", lookup);
        var path = lattice.bestPath();

        // Individual words (0.9 + 0.9 = 1.8) should beat weak multi-word (0.3 + 0.1 boost = 0.4)
        assertThat(path).hasSize(2);
        assertThat(path.get(0).text()).isEqualTo("Bob");
        assertThat(path.get(1).text()).isEqualTo("Marley");
    }

    // ==================================================================================
    // Structural symbols (resolved through dictionary like any other sememe)
    // ==================================================================================

    @Test
    void parens_alwaysIsolated() {
        // Parens resolve through the dictionary as sememes, not hardcoded structural tokens
        Function<String, List<Posting>> lookup = token -> switch (token) {
            case "sqrt" -> List.of(posting("sqrt", ItemID.fromString("cg.fn:sqrt")));
            case "(" -> List.of(posting("(", ItemID.fromString("cg.syntax:open-group")));
            case ")" -> List.of(posting(")", ItemID.fromString("cg.syntax:close-group")));
            default -> List.of();
        };

        var lattice = TokenLattice.build("sqrt(9)", lookup);
        var path = lattice.bestPath();

        assertThat(path).hasSize(4);
        assertThat(path.get(0).text()).isEqualTo("sqrt");
        assertThat(path.get(0).isResolved()).isTrue();
        assertThat(path.get(1).text()).isEqualTo("(");
        assertThat(path.get(1).type()).isEqualTo(TokenLattice.SpanType.WORD);
        assertThat(path.get(1).bestPosting().target()).isEqualTo(ItemID.fromString("cg.syntax:open-group"));
        assertThat(path.get(2).text()).isEqualTo("9");
        assertThat(path.get(2).type()).isEqualTo(TokenLattice.SpanType.LITERAL);
        assertThat(path.get(3).text()).isEqualTo(")");
        assertThat(path.get(3).type()).isEqualTo(TokenLattice.SpanType.WORD);
        assertThat(path.get(3).bestPosting().target()).isEqualTo(ItemID.fromString("cg.syntax:close-group"));
    }

    // ==================================================================================
    // Literal detection
    // ==================================================================================

    @Test
    void numericLiteral_detected() {
        var lattice = TokenLattice.build("42", token -> List.of());
        var path = lattice.bestPath();

        assertThat(path).hasSize(1);
        assertThat(path.getFirst().type()).isEqualTo(TokenLattice.SpanType.LITERAL);
    }

    @Test
    void booleanLiteral_detected() {
        var lattice = TokenLattice.build("true", token -> List.of());
        var path = lattice.bestPath();

        assertThat(path).hasSize(1);
        assertThat(path.getFirst().type()).isEqualTo(TokenLattice.SpanType.LITERAL);
    }

    // ==================================================================================
    // Language inference
    // ==================================================================================

    @Test
    void inferLanguage_fromPostingScopes() {
        Function<String, List<Posting>> lookup = token -> switch (token) {
            case "create" -> List.of(scopedPosting("create", CREATE_IID, ENGLISH));
            case "chess" -> List.of(scopedPosting("chess", CHESS_IID, ENGLISH));
            default -> List.of();
        };

        var lattice = TokenLattice.build("create chess", lookup);
        assertThat(lattice.inferLanguage()).isEqualTo(ENGLISH);
    }

    @Test
    void languageAt_returnsCorrectScope() {
        Function<String, List<Posting>> lookup = token -> switch (token) {
            case "create" -> List.of(scopedPosting("create", CREATE_IID, ENGLISH));
            case "chess" -> List.of(scopedPosting("chess", CHESS_IID, ENGLISH));
            default -> List.of();
        };

        var lattice = TokenLattice.build("create chess", lookup);
        assertThat(lattice.languageAt(0)).isEqualTo(ENGLISH);  // "c" in "create"
        assertThat(lattice.languageAt(7)).isEqualTo(ENGLISH);  // "c" in "chess"
    }

    // ==================================================================================
    // Ambiguity detection
    // ==================================================================================

    @Test
    void ambiguousSpan_detected() {
        Function<String, List<Posting>> lookup = token ->
                "play".equals(token) ? List.of(
                        posting("play", ItemID.fromString("cg.verb:play"), 0.9f),
                        posting("play", ItemID.fromString("cg.noun:play"), 0.85f)
                ) : List.of();

        var lattice = TokenLattice.build("play", lookup);
        assertThat(lattice.hasAmbiguity()).isTrue();
        assertThat(lattice.ambiguousSpans()).hasSize(1);
    }

    @Test
    void clearResolution_notAmbiguous() {
        Function<String, List<Posting>> lookup = token ->
                "create".equals(token) ? List.of(
                        posting("create", CREATE_IID, 1.0f),
                        posting("create", ItemID.fromString("cg.noun:creation"), 0.3f)
                ) : List.of();

        var lattice = TokenLattice.build("create", lookup);
        assertThat(lattice.hasAmbiguity()).isFalse();
    }

    // ==================================================================================
    // Segment splitting (tested via lattice span output)
    // ==================================================================================

    @Test
    void charClassBoundaries_splitCorrectly() {
        // "5+2" should produce 3 spans: number, operator, number
        var lattice = TokenLattice.build("5+2", token -> List.of());
        var path = lattice.bestPath();
        assertThat(path).extracting(TokenLattice.Span::text).containsExactly("5", "+", "2");
    }

    @Test
    void structuralIsolation_parensAndCommas() {
        // "f(x,y)" should isolate parens and commas — they split at character-class boundaries
        Function<String, List<Posting>> lookup = token -> switch (token) {
            case "(" -> List.of(posting("(", ItemID.fromString("cg.syntax:open-group")));
            case ")" -> List.of(posting(")", ItemID.fromString("cg.syntax:close-group")));
            case "," -> List.of(posting(",", ItemID.fromString("cg.syntax:separator")));
            default -> List.of();
        };

        var lattice = TokenLattice.build("f(x,y)", lookup);
        var path = lattice.bestPath();
        assertThat(path).extracting(TokenLattice.Span::text).containsExactly("f", "(", "x", ",", "y", ")");
    }

    @Test
    void decimalNumber_preserved() {
        var lattice = TokenLattice.build("3.14", token -> List.of());
        var path = lattice.bestPath();
        assertThat(path).hasSize(1);
        assertThat(path.getFirst().text()).isEqualTo("3.14");
    }

    @Test
    void alphanumeric_preserved() {
        // "x2" should stay together (digit after letter)
        var lattice = TokenLattice.build("x2", token -> List.of());
        var path = lattice.bestPath();
        assertThat(path).hasSize(1);
        assertThat(path.getFirst().text()).isEqualTo("x2");
    }

    // ==================================================================================
    // Helpers
    // ==================================================================================

    private static Posting posting(String token, ItemID target) {
        return posting(token, target, 1.0f);
    }

    private static Posting posting(String token, ItemID target, float weight) {
        FrameBodyOld body = new FrameBodyOld(CoreVocabulary.Lexeme.IID, List.of(
                FrameBodyOld.homeBinding(target),
                new Binding(ThematicRole.Value.IID, List.of(), Literal.ofText(token))
        ));
        return Posting.fromFrame(body, 1, weight);
    }

    private static Posting scopedPosting(String token, ItemID target, ItemID scope) {
        FrameBodyOld body = new FrameBodyOld(CoreVocabulary.Lexeme.IID, List.of(
                FrameBodyOld.homeBinding(target),
                new Binding(ThematicRole.Value.IID,
                        List.of(new CompoundKey.Sememe(scope)),
                        Literal.ofText(token))
        ));
        return Posting.fromFrame(body, 1, 1.0f);
    }
}
