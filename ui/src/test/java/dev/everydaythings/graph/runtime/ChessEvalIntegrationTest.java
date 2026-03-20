package dev.everydaythings.graph.runtime;

import dev.everydaythings.graph.game.chess.ChessItem;
import dev.everydaythings.graph.item.Item;
import dev.everydaythings.graph.item.id.ItemID;
import dev.everydaythings.graph.language.Sememe;
import dev.everydaythings.graph.language.CoreVocabulary;
import dev.everydaythings.graph.game.GameVocabulary;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test: full chess flow through Eval.
 *
 * <p>Tests the end-to-end path:
 * create chess → move e4 → verify
 */
class ChessEvalIntegrationTest {

    private Librarian librarian;

    @BeforeEach
    void setUp() {
        librarian = Librarian.createInMemory();
    }

    @Test
    void nounSememe_resolvesToChessItem() {
        ItemID chessTypeId = ItemID.fromString("cg.sememe:chess");
        Optional<Item> chessType = librarian.get(chessTypeId);

        assertThat(chessType).isPresent();
        assertThat(chessType.get()).isInstanceOf(Sememe.class);

        Sememe ns = (Sememe) chessType.get();
        Optional<Class<?>> resolved = ns.resolveImplementingClass();
        assertThat(resolved).isPresent();
        assertThat(resolved.get()).isEqualTo(ChessItem.class);
    }

    @Test
    void eval_createChess_returnsChessItem() {
        Eval eval = Eval.builder()
                .librarian(librarian)
                .context(librarian)
                .interactive(false)
                .build();

        Eval.EvalResult result = eval.evaluateCommand(List.of("create", "chess"));

        assertThat(result).isInstanceOf(Eval.EvalResult.Created.class);
        Item created = ((Eval.EvalResult.Created) result).item();
        assertThat(created).isInstanceOf(ChessItem.class);
        assertThat(created.iid()).isNotNull();

        // ChessItem has move verb
        ItemID moveId = ItemID.fromString(GameVocabulary.Move.KEY);
        assertThat(created.vocabulary().lookup(moveId)).isPresent();
    }

    @Test
    void eval_createItem_returnsItem() {
        Eval eval = Eval.builder()
                .librarian(librarian)
                .context(librarian)
                .interactive(false)
                .build();

        Eval.EvalResult result = eval.evaluateCommand(List.of("create", "item"));

        assertThat(result).isInstanceOf(Eval.EvalResult.Created.class);
        Item created = ((Eval.EvalResult.Created) result).item();
        assertThat(created).isNotNull();
        assertThat(created.iid()).isNotNull();
    }

    @Test
    void eval_nounAlone_navigatesToNoun() {
        Eval eval = Eval.builder()
                .librarian(librarian)
                .context(librarian)
                .interactive(false)
                .build();

        Eval.EvalResult result = eval.evaluateCommand(List.of("chess"));

        assertThat(result).isInstanceOf(Eval.EvalResult.ItemResult.class);
        Item item = ((Eval.EvalResult.ItemResult) result).item();
        assertThat(item).isInstanceOf(Sememe.class);
    }

    @Test
    void fullFlow_createChess_move_show() {
        Eval eval = Eval.builder()
                .librarian(librarian)
                .context(librarian)
                .interactive(false)
                .build();

        // Step 1: "create chess" → ChessItem
        Eval.EvalResult createResult = eval.evaluateCommand(List.of("create", "chess"));
        assertThat(createResult).isInstanceOf(Eval.EvalResult.Created.class);
        Item item = ((Eval.EvalResult.Created) createResult).item();
        assertThat(item).isInstanceOf(ChessItem.class);
        ChessItem chess = (ChessItem) item;

        // Step 2: "show" in context of the chess item
        Eval evalOnItem = Eval.builder()
                .librarian(librarian)
                .context(item)
                .interactive(false)
                .build();

        Eval.EvalResult showResult = evalOnItem.evaluateCommand(List.of("show"));
        assertThat(showResult.isSuccess()).isTrue();




        // Step 3: "move e2e4"
        Eval.EvalResult moveResult = evalOnItem.evaluateCommand(List.of("move", "e2e4"));
        assertThat(moveResult.isSuccess()).isTrue();

        // Verify the move was made
        assertThat(chess.moveCount()).isEqualTo(1);

        // Step 4: Show again — board should reflect the move
        Eval.EvalResult showResult2 = evalOnItem.evaluateCommand(List.of("show"));
        assertThat(showResult2.isSuccess()).isTrue();
    }
}
