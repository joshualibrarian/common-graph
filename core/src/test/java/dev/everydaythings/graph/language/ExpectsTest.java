package dev.everydaythings.graph.language;

import dev.everydaythings.graph.item.id.ItemID;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for the EXPECTS mechanism — type-level frame schema declarations.
 *
 * <p>EXPECTS frames on a Sememe describe what frames its instances should
 * carry. Works in both directions: forward (creation guidance) and backward
 * (duck typing — structural type recognition).
 */
class ExpectsTest {

    @Test
    void expectsSeedExists() {
        assertThat(CoreVocabulary.Expects.SEED).isNotNull();
        assertThat(CoreVocabulary.Expects.SEED.iid())
                .isEqualTo(ItemID.fromString(CoreVocabulary.Expects.KEY));
    }

    @Test
    void fluentExpects_simplePredicate() {
        Sememe type = new Sememe("test:type/document")
                .expects(CoreVocabulary.Author.KEY)
                .expects(CoreVocabulary.Title.KEY);

        assertThat(type.expectations()).hasSize(2);

        assertThat(type.expectations().get(0).predicate())
                .isEqualTo(ItemID.fromString(CoreVocabulary.Author.KEY));
        assertThat(type.expectations().get(0).roleBindings()).isEmpty();

        assertThat(type.expectations().get(1).predicate())
                .isEqualTo(ItemID.fromString(CoreVocabulary.Title.KEY));
        assertThat(type.expectations().get(1).roleBindings()).isEmpty();
    }

    @Test
    void fluentExpects_withRoleBinding() {
        // Chess expects: Player(THEME=White), Player(THEME=Black), Move, Resign
        Sememe chess = new Sememe("cg:type/chess")
                .gloss("en", "the game of chess")
                .expects("cg.game:player", ThematicRole.Theme.KEY, ColorVocabulary.White.KEY)
                .expects("cg.game:player", ThematicRole.Theme.KEY, ColorVocabulary.Black.KEY)
                .expects("cg.verb:move")
                .expects("cg.verb:resign");

        assertThat(chess.expectations()).hasSize(4);

        // White player expectation
        var whitePlayer = chess.expectations().get(0);
        assertThat(whitePlayer.predicate())
                .isEqualTo(ItemID.fromString("cg.game:player"));
        assertThat(whitePlayer.roleBindings())
                .containsEntry(
                        ItemID.fromString(ThematicRole.Theme.KEY),
                        ItemID.fromString(ColorVocabulary.White.KEY));

        // Black player expectation
        var blackPlayer = chess.expectations().get(1);
        assertThat(blackPlayer.predicate())
                .isEqualTo(ItemID.fromString("cg.game:player"));
        assertThat(blackPlayer.roleBindings())
                .containsEntry(
                        ItemID.fromString(ThematicRole.Theme.KEY),
                        ItemID.fromString(ColorVocabulary.Black.KEY));

        // Move — simple, no bindings
        assertThat(chess.expectations().get(2).predicate())
                .isEqualTo(ItemID.fromString("cg.verb:move"));
        assertThat(chess.expectations().get(2).roleBindings()).isEmpty();

        // Resign — simple, no bindings
        assertThat(chess.expectations().get(3).predicate())
                .isEqualTo(ItemID.fromString("cg.verb:resign"));
    }

    @Test
    void expectations_nullWhenNoneSet() {
        Sememe simple = new Sememe("test:type/simple");
        assertThat(simple.expectations()).isNull();
    }

    @Test
    void passiveType_structuralExpectations() {
        // A Book type — no behavior code, just structural expectations
        Sememe book = new Sememe("cg:type/book")
                .gloss("en", "a written work")
                .expects(CoreVocabulary.Author.KEY)
                .expects(CoreVocabulary.Title.KEY)
                .expects(CoreVocabulary.Description.KEY);

        // Duck typing: if an item has author + title + description,
        // it structurally matches Book
        assertThat(book.expectations()).hasSize(3);
        assertThat(book.expectations().stream().map(Sememe.Expectation::predicate))
                .containsExactly(
                        ItemID.fromString(CoreVocabulary.Author.KEY),
                        ItemID.fromString(CoreVocabulary.Title.KEY),
                        ItemID.fromString(CoreVocabulary.Description.KEY));
    }
}
