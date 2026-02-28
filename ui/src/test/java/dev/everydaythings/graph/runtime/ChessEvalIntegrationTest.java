package dev.everydaythings.graph.runtime;

import dev.everydaythings.graph.game.chess.ChessGame;
import dev.everydaythings.graph.game.minesweeper.Minesweeper;
import dev.everydaythings.graph.item.Item;
import dev.everydaythings.graph.item.component.ComponentType;
import dev.everydaythings.graph.item.id.ItemID;
import dev.everydaythings.graph.language.Posting;
import dev.everydaythings.graph.language.Sememe;
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
 * create item → create chess → show board → move e4 → show updated board
 */
class ChessEvalIntegrationTest {

    private Librarian librarian;

    @BeforeEach
    void setUp() {
        librarian = Librarian.createInMemory();
    }

    @Test
    void componentType_createVerb_instantiatesChess() {
        // Find the Chess ComponentType via its type ID
        ItemID chessTypeId = ItemID.fromString("cg:type/chess");
        Optional<Item> chessType = librarian.get(chessTypeId);

        assertThat(chessType).isPresent();
        assertThat(chessType.get()).isInstanceOf(ComponentType.class);

        ComponentType ct = (ComponentType) chessType.get();

        // The ComponentType should be able to resolve to Chess class
        Optional<Class<?>> resolved = ct.resolveClass();
        assertThat(resolved).isPresent();
        assertThat(resolved.get()).isEqualTo(ChessGame.class);
    }

    @Test
    void componentType_instantiateComponent_createsChess() {
        Object chess = ComponentType.instantiateComponent(ChessGame.class);

        assertThat(chess).isInstanceOf(ChessGame.class);
        ChessGame game = (ChessGame) chess;
        assertThat(game.isGameOver()).isFalse();
        assertThat(game.moveCount()).isEqualTo(0);
    }

    @Test
    void eval_createChess_returnsChessComponent() {
        // "create chess" should dispatch CREATE on Chess ComponentType
        Eval eval = Eval.builder()
                .librarian(librarian)
                .context(librarian)
                .interactive(false)
                .build();

        Eval.EvalResult result = eval.evaluateCommand(List.of("create", "chess"));

        // Should return a Value containing a Chess ContentComponent
        assertThat(result).isInstanceOf(Eval.EvalResult.Value.class);
        Object value = ((Eval.EvalResult.Value) result).value();
        assertThat(value).isInstanceOf(ChessGame.class);
    }

    @Test
    void eval_createMinesweeper_prefersComponentType_whenTokenCollides() {
        // Inject a higher-weight ambiguous posting for "minesweeper" that points to the librarian.
        librarian.tokenIndex().runInWriteTransaction(tx ->
                librarian.tokenIndex().index(Posting.universal("minesweeper", librarian.iid(), 2.0f), tx));

        Eval eval = Eval.builder()
                .librarian(librarian)
                .context(librarian)
                .interactive(false)
                .build();

        Eval.EvalResult result = eval.evaluateCommand(List.of("create", "minesweeper"));

        assertThat(result).isInstanceOf(Eval.EvalResult.Value.class);
        Object value = ((Eval.EvalResult.Value) result).value();
        assertThat(value).isInstanceOf(Minesweeper.class);
    }

    @Test
    void eval_createItem_returnsItem() {
        // "create item" should dispatch CREATE on the Item type → new Item
        Eval eval = Eval.builder()
                .librarian(librarian)
                .context(librarian)
                .interactive(false)
                .build();

        Eval.EvalResult result = eval.evaluateCommand(List.of("create", "item"));

        assertThat(result).isInstanceOf(Eval.EvalResult.ItemResult.class);
        Item created = ((Eval.EvalResult.ItemResult) result).item();
        assertThat(created).isNotNull();
        assertThat(created.iid()).isNotNull();
    }

    @Test
    void eval_verbAlone_dispatchesOnContextIfAvailable() {
        // "create" alone — Librarian has actionNew (CREATE verb), so should dispatch
        Eval eval = Eval.builder()
                .librarian(librarian)
                .context(librarian)
                .interactive(false)
                .build();

        Eval.EvalResult result = eval.evaluateCommand(List.of("create"));

        // Librarian has CREATE verb → dispatches on context → creates Item
        assertThat(result.isSuccess()).isTrue();
    }

    @Test
    void eval_nounAlone_navigatesToNoun() {
        // "chess" alone should navigate to the Chess ComponentType
        Eval eval = Eval.builder()
                .librarian(librarian)
                .context(librarian)
                .interactive(false)
                .build();

        Eval.EvalResult result = eval.evaluateCommand(List.of("chess"));

        assertThat(result).isInstanceOf(Eval.EvalResult.ItemResult.class);
        Item item = ((Eval.EvalResult.ItemResult) result).item();
        assertThat(item).isInstanceOf(ComponentType.class);
    }

    @Test
    void item_addChessComponent_enablesChessVerbs() {
        // Create an item and add a chess component
        Item item = Item.create(librarian);
        ChessGame chess = ChessGame.create();
        item.addComponent("chess", chess);

        // Item should now have chess verbs (MOVE, SHOW, etc.)
        ItemID moveId = ItemID.fromString(GameVocabulary.MOVE);
        assertThat(item.vocabulary().lookup(moveId)).isPresent();

        ItemID showId = ItemID.fromString(Sememe.SHOW);
        assertThat(item.vocabulary().lookup(showId)).isPresent();
    }

    @Test
    void fullFlow_createItem_addChess_move_show() {
        // Step 1: Create an Item via Eval
        Eval eval = Eval.builder()
                .librarian(librarian)
                .context(librarian)
                .interactive(false)
                .build();

        Eval.EvalResult createResult = eval.evaluateCommand(List.of("create", "item"));
        assertThat(createResult).isInstanceOf(Eval.EvalResult.ItemResult.class);
        Item item = ((Eval.EvalResult.ItemResult) createResult).item();

        // Step 2: Create a Chess component via Eval (context = item)
        Eval evalOnItem = Eval.builder()
                .librarian(librarian)
                .context(item)
                .interactive(false)
                .build();

        Eval.EvalResult chessResult = evalOnItem.evaluateCommand(List.of("create", "chess"));
        assertThat(chessResult).isInstanceOf(Eval.EvalResult.Value.class);
        Object chessValue = ((Eval.EvalResult.Value) chessResult).value();
        assertThat(chessValue).isInstanceOf(ChessGame.class);

        // Step 3: Add the chess component to the item (Session would do this)
        ChessGame chess = (ChessGame) chessValue;
        item.addComponent("chess", chess);

        // Step 4: Join the game (Eval dispatch passes real caller, so must be seated)
        // The librarian's principal is the caller — seat them as both players for testing
        ItemID principalId = librarian.principal().map(s -> s.iid()).orElse(null);
        assertThat(principalId).isNotNull();
        chess.joinAs(0, principalId);
        chess.joinAs(1, ItemID.fromString("test:player/opponent"));

        // Step 5: "show" in context of the item → should show the chess board
        Eval evalWithChess = Eval.builder()
                .librarian(librarian)
                .context(item)
                .interactive(false)
                .build();

        Eval.EvalResult showResult = evalWithChess.evaluateCommand(List.of("show"));
        assertThat(showResult.isSuccess()).isTrue();

        // Step 6: "move e2e4" → should make a move (principal is seated at 0 = white)
        Eval.EvalResult moveResult = evalWithChess.evaluateCommand(List.of("move", "e2e4"));
        assertThat(moveResult.isSuccess()).isTrue();

        // Verify the move was made
        assertThat(chess.moveCount()).isEqualTo(1);

        // Step 7: Show again — board should reflect the move
        Eval.EvalResult showResult2 = evalWithChess.evaluateCommand(List.of("show"));
        assertThat(showResult2.isSuccess()).isTrue();
    }
}
