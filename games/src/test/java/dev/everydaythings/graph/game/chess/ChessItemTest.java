package dev.everydaythings.graph.game.chess;

import com.upokecenter.cbor.CBORObject;
import dev.everydaythings.graph.item.id.ItemID;
import dev.everydaythings.graph.network.session.RenderInstructionRecorder;
import dev.everydaythings.graph.runtime.Librarian;
import dev.everydaythings.graph.ui.scene.SceneCompiler;
import dev.everydaythings.graph.ui.scene.View;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
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
        assertThat(chess.result()).isEqualTo(ChessItem.GameResult.IN_PROGRESS);
    }

    @Test
    void move_appliesAndTracksHistory() {
        String result = chess.move("e2e4");
        assertThat(result).isNull(); // null = success
        assertThat(chess.moveCount()).isEqualTo(1);
        assertThat(chess.moves()).containsExactly("e2e4");
    }

    @Test
    void move_rejectsIllegal() {
        String result = chess.move("e5e6");
        assertThat(result).contains("Illegal move");
        assertThat(chess.moveCount()).isEqualTo(0);
    }

    @Test
    void show_rendersBoard() {
        String board = chess.renderBoard();
        assertThat(board).contains("a b c d e f g h");
        assertThat(board).contains("White to move");
    }

    @Test
    void scholarsMate_detectsCheckmate() {
        // Scholar's mate: 1. e4 e5 2. Bc4 Nc6 3. Qh5 Nf6 4. Qxf7#
        assertThat(chess.move("e2e4")).isNull();
        assertThat(chess.move("e7e5")).isNull();
        assertThat(chess.move("f1c4")).isNull();
        assertThat(chess.move("b8c6")).isNull();
        assertThat(chess.move("d1h5")).isNull();
        assertThat(chess.move("g8f6")).isNull();
        assertThat(chess.move("h5f7")).isNull();

        assertThat(chess.isGameOver()).isTrue();
        assertThat(chess.result()).isEqualTo(ChessItem.GameResult.WHITE_WINS_CHECKMATE);
    }

    @Test
    void resign_endsGame() {
        String msg = chess.resign();
        assertThat(msg).contains("White resigns");
        assertThat(chess.isGameOver()).isTrue();
        assertThat(chess.result()).isEqualTo(ChessItem.GameResult.BLACK_WINS_RESIGNATION);
    }

    @Test
    void join_assignsSeats() {
        ItemID white = ItemID.fromString("test:player/white");
        ItemID black = ItemID.fromString("test:player/black");

        assertThat(chess.join(white, null)).isEqualTo("Joined as white");
        assertThat(chess.join(black, null)).isEqualTo("Joined as black");
    }

    @Test
    void fen_returnsCurrentPosition() {
        assertThat(chess.fen()).startsWith("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR");
    }

    // =========================================================================
    // Scene compilation (display pipeline)
    // =========================================================================

    @Nested
    @DisplayName("Display Pipeline")
    class DisplayPipeline {

        @Test
        @DisplayName("viewBoard() produces a non-empty View")
        void viewBoard_producesView() {
            View view = chess.viewBoard();
            assertThat(view).isNotNull();
            assertThat(view.root()).isNotNull();
        }

        @Test
        @DisplayName("viewBoard() compiles to render instructions")
        void viewBoard_compilesToInstructions() {
            View view = chess.viewBoard();

            RenderInstructionRecorder recorder = new RenderInstructionRecorder();
            view.root().render(recorder);
            CBORObject instructions = recorder.toCbor();

            System.out.println("Total instructions: " + instructions.size());
            for (int i = 0; i < Math.min(20, instructions.size()); i++) {
                System.out.println("  [" + i + "]: " + instructions.get(i).ToJSONString());
            }
            if (instructions.size() > 20) {
                System.out.println("  ... (" + (instructions.size() - 20) + " more)");
            }

            // Should have substantial content: board grid, pieces, handles, etc.
            assertThat(instructions.size()).isGreaterThan(10);
        }

        @Test
        @DisplayName("SceneCompiler.compile() works on ChessItem")
        void sceneCompiler_compilesChessItem() {
            View view = SceneCompiler.compile(chess);

            assertThat(view).isNotNull();
            assertThat(view.root()).isNotNull();

            RenderInstructionRecorder recorder = new RenderInstructionRecorder();
            view.root().render(recorder);
            CBORObject instructions = recorder.toCbor();

            System.out.println("SceneCompiler.compile() instructions: " + instructions.size());
            for (int i = 0; i < Math.min(10, instructions.size()); i++) {
                System.out.println("  [" + i + "]: " + instructions.get(i).ToJSONString());
            }

            assertThat(instructions.size()).isGreaterThan(2);
        }

        @Test
        @DisplayName("board state produces 64 squares across 8 ranks")
        void ranks_produces8RanksOf8Squares() {
            var ranks = chess.ranks();
            assertThat(ranks).hasSize(8);
            for (var rank : ranks) {
                assertThat(rank.squares()).hasSize(8);
            }
        }

        @Test
        @DisplayName("starting position has correct pieces")
        void startingPosition_hasCorrectPieces() {
            var ranks = chess.ranks();
            // Rank 8 (index 0) should have black pieces
            var rank8 = ranks.get(0);
            assertThat(rank8.label()).isEqualTo("8");
            assertThat(rank8.squares().get(0).piece()).isEqualTo(ChessPiece.BLACK_ROOK);
            assertThat(rank8.squares().get(4).piece()).isEqualTo(ChessPiece.BLACK_KING);

            // Rank 1 (index 7) should have white pieces
            var rank1 = ranks.get(7);
            assertThat(rank1.label()).isEqualTo("1");
            assertThat(rank1.squares().get(0).piece()).isEqualTo(ChessPiece.WHITE_ROOK);
            assertThat(rank1.squares().get(4).piece()).isEqualTo(ChessPiece.WHITE_KING);

            // Rank 4 (index 4) should be empty
            var rank4 = ranks.get(4);
            for (var sq : rank4.squares()) {
                assertThat(sq.piece()).isNull();
            }
        }
    }
}
