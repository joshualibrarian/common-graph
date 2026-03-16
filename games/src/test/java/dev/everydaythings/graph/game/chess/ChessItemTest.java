package dev.everydaythings.graph.game.chess;

import dev.everydaythings.graph.dispatch.ActionContext;
import dev.everydaythings.graph.item.id.ItemID;
import dev.everydaythings.graph.runtime.Librarian;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link ChessItem} — chess as a proper Item.
 */
class ChessItemTest {

    private Librarian librarian;
    private ChessItem chess;

    @BeforeEach
    void setUp() {
        librarian = Librarian.createInMemory();
        chess = new ChessItem(librarian);
    }

    @Test
    void freshGame_isNotOver() {
        assertThat(chess.isGameOver()).isFalse();
        assertThat(chess.moveCount()).isEqualTo(0);
        assertThat(chess.result()).isEqualTo(ChessGame.GameResult.IN_PROGRESS);
    }

    @Test
    void move_appliesAndTracksHistory() {
        ActionContext ctx = ActionContext.of(ItemID.fromString("test:player/white"), null, chess, librarian);

        String result = chess.move(ctx, "e2e4");
        assertThat(result).isNull(); // null = success
        assertThat(chess.moveCount()).isEqualTo(1);
        assertThat(chess.moves()).containsExactly("e2e4");
    }

    @Test
    void move_rejectsIllegal() {
        ActionContext ctx = ActionContext.of(ItemID.fromString("test:player/white"), null, chess, librarian);

        String result = chess.move(ctx, "e5e6");
        assertThat(result).contains("Illegal move");
        assertThat(chess.moveCount()).isEqualTo(0);
    }

    @Test
    void show_rendersBoard() {
        String board = chess.show();
        assertThat(board).contains("a b c d e f g h");
        assertThat(board).contains("White to move");
    }

    @Test
    void scholarsMate_detectsCheckmate() {
        ActionContext ctx = ActionContext.of(ItemID.fromString("test:player/white"), null, chess, librarian);

        // Scholar's mate: 1. e4 e5 2. Bc4 Nc6 3. Qh5 Nf6 4. Qxf7#
        assertThat(chess.move(ctx, "e2e4")).isNull();
        assertThat(chess.move(ctx, "e7e5")).isNull();
        assertThat(chess.move(ctx, "f1c4")).isNull();
        assertThat(chess.move(ctx, "b8c6")).isNull();
        assertThat(chess.move(ctx, "d1h5")).isNull();
        assertThat(chess.move(ctx, "g8f6")).isNull();
        assertThat(chess.move(ctx, "h5f7")).isNull();

        assertThat(chess.isGameOver()).isTrue();
        assertThat(chess.result()).isEqualTo(ChessGame.GameResult.WHITE_WINS_CHECKMATE);
    }

    @Test
    void resign_endsGame() {
        ActionContext ctx = ActionContext.of(ItemID.fromString("test:player/white"), null, chess, librarian);

        String msg = chess.resign(ctx);
        assertThat(msg).contains("White resigns");
        assertThat(chess.isGameOver()).isTrue();
        assertThat(chess.result()).isEqualTo(ChessGame.GameResult.BLACK_WINS_RESIGNATION);
    }

    @Test
    void join_assignsSeats() {
        ItemID white = ItemID.fromString("test:player/white");
        ItemID black = ItemID.fromString("test:player/black");

        ActionContext ctxW = ActionContext.of(white, null, chess, librarian);
        ActionContext ctxB = ActionContext.of(black, null, chess, librarian);

        assertThat(chess.join(ctxW, null)).isEqualTo("Joined as white");
        assertThat(chess.join(ctxB, null)).isEqualTo("Joined as black");
    }

    @Test
    void fen_returnsCurrentPosition() {
        assertThat(chess.fen()).startsWith("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR");
    }
}
