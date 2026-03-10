package dev.everydaythings.graph.game.chess;

import dev.everydaythings.graph.game.GameMode;
import dev.everydaythings.graph.item.Item;
import dev.everydaythings.graph.item.action.ActionResult;
import dev.everydaythings.graph.item.id.ItemID;
import dev.everydaythings.graph.item.user.User;
import dev.everydaythings.graph.runtime.Librarian;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Two-player chess via Item.dispatch(caller, command, args).
 *
 * <p>Tests that caller identity flows through dispatch and
 * chess authorization logic correctly enforces turns.
 */
class ChessMultiplayerTest {

    private Librarian librarian;
    private User alice;
    private User bob;
    private Item gameItem;
    private ChessGame chess;

    @BeforeEach
    void setUp() {
        librarian = Librarian.createInMemory();

        // Create two players
        alice = User.create(librarian, "alice");
        bob = User.create(librarian, "bob");

        // Create a game item with chess component in AUTHENTICATED mode
        gameItem = Item.create(librarian);
        chess = ChessGame.create();
        chess.setMode(GameMode.AUTHENTICATED);
        gameItem.addComponent("chess", chess);
    }

    @Test
    void twoPlayers_joinAndPlay() {
        // Alice joins as white (seat 0)
        ActionResult joinResult = gameItem.dispatch(alice.iid(), "join", List.of("0"));
        assertThat(joinResult.success()).isTrue();

        // Bob joins as black (seat 1)
        joinResult = gameItem.dispatch(bob.iid(), "join", List.of("1"));
        assertThat(joinResult.success()).isTrue();

        // Verify both are seated
        assertThat(chess.seatOf(alice.iid())).isPresent().hasValue(0);
        assertThat(chess.seatOf(bob.iid())).isPresent().hasValue(1);

        // Alice moves e4 (white's first move)
        ActionResult moveResult = gameItem.dispatch(alice.iid(), "move", List.of("e2e4"));
        assertThat(moveResult.success()).isTrue();
        assertThat(chess.moveCount()).isEqualTo(1);

        // Bob tries to move out of turn (should fail)
        // Actually, Bob's move should succeed since it's now black's turn
        // Let's test Alice moving again (out of turn)
        moveResult = gameItem.dispatch(alice.iid(), "move", List.of("d2d4"));
        assertThat(moveResult.success()).isFalse();

        // Bob moves e5 (black's turn)
        moveResult = gameItem.dispatch(bob.iid(), "move", List.of("e7e5"));
        assertThat(moveResult.success()).isTrue();
        assertThat(chess.moveCount()).isEqualTo(2);
    }

    @Test
    void unauthorizedPlayer_cannotMove() {
        // Alice joins as white
        gameItem.dispatch(alice.iid(), "join", List.of("0"));

        // Bob tries to move without joining
        ActionResult moveResult = gameItem.dispatch(bob.iid(), "move", List.of("e2e4"));
        assertThat(moveResult.success()).isFalse();
    }

    @Test
    void playerNames_displayCorrectly() {
        // Alice and Bob join
        gameItem.dispatch(alice.iid(), "join", List.of("0"));
        gameItem.dispatch(bob.iid(), "join", List.of("1"));

        // Verify display names
        assertThat(chess.whiteLabel()).isEqualTo("alice");
        assertThat(chess.blackLabel()).isEqualTo("bob");
    }

    @Test
    void fullGame_scholarsMate() {
        // Both players join
        gameItem.dispatch(alice.iid(), "join", List.of("0"));
        gameItem.dispatch(bob.iid(), "join", List.of("1"));

        // Scholar's mate: 1.e4 e5 2.Bc4 Nc6 3.Qh5 Nf6 4.Qxf7#
        assertThat(gameItem.dispatch(alice.iid(), "move", List.of("e2e4")).success()).isTrue();
        assertThat(gameItem.dispatch(bob.iid(), "move", List.of("e7e5")).success()).isTrue();
        assertThat(gameItem.dispatch(alice.iid(), "move", List.of("f1c4")).success()).isTrue();
        assertThat(gameItem.dispatch(bob.iid(), "move", List.of("b8c6")).success()).isTrue();
        assertThat(gameItem.dispatch(alice.iid(), "move", List.of("d1h5")).success()).isTrue();
        assertThat(gameItem.dispatch(bob.iid(), "move", List.of("g8f6")).success()).isTrue();
        assertThat(gameItem.dispatch(alice.iid(), "move", List.of("h5f7")).success()).isTrue();

        // Game should be over - checkmate
        assertThat(chess.isGameOver()).isTrue();
        assertThat(chess.result()).isEqualTo(ChessGame.GameResult.WHITE_WINS_CHECKMATE);
    }

    @Test
    void nullCaller_skipsAuthorization() {
        // Direct dispatch without caller (no-arg overload) should still work
        // This is the backward-compatible path
        ActionResult result = gameItem.dispatch("move", List.of("e2e4"));
        assertThat(result.success()).isTrue();
    }
}
