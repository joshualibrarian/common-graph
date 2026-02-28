package dev.everydaythings.graph.game.chess;

import dev.everydaythings.graph.ui.scene.SceneCompiler;
import dev.everydaythings.graph.ui.scene.spatial.CompiledBody;
import dev.everydaythings.graph.ui.scene.spatial.SpatialCompiler;
import dev.everydaythings.graph.ui.scene.spatial.SpatialSchema;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for the declarative chess rendering pipeline.
 *
 * <p>Verifies that ChessGame's {@code @Body} and {@code @Surface} annotations
 * compile correctly, and that the surface pipeline resolves piece models
 * from bound ChessPiece values.
 */
class ChessBodyTest {

    @Test
    void chessGame_compilesInlineBody() {
        CompiledBody body = SpatialCompiler.compileBody(ChessGame.class);

        assertThat(body.hasBody()).isTrue();
        assertThat(body.isCompound()).isFalse();
        assertThat(body.hasGeometry()).isTrue();
        assertThat(body.shape()).isEqualTo("box");
        assertThat(body.fontSize()).isEqualTo("2.2cm");
        assertThat(body.isDerivedSize()).isTrue();
        // width/depth revert to defaults since dimensions are derived from fontSize
        assertThat(body.width()).isEqualTo("1m");
        assertThat(body.color()).isEqualTo(0x8B4513);
    }

    @Test
    void chessGame_bodyIsNotCompound() {
        CompiledBody body = SpatialCompiler.compileBody(ChessGame.class);
        assertThat(body.as()).isEqualTo(SpatialSchema.class);
        assertThat(body.isCompound()).isFalse();
    }

    @Test
    void chessBoard_canCompileSurface() {
        assertThat(SceneCompiler.canCompile(ChessBoard.class)).isTrue();
    }

    @Test
    void chessGame_canCompileSurface() {
        assertThat(SceneCompiler.canCompile(ChessGame.class)).isTrue();
    }

    @Test
    void chessPiece_providesModelKey() {
        assertThat(ChessPiece.WHITE_KING.modelKey()).isEqualTo("chess/models/w_king.glb");
        assertThat(ChessPiece.BLACK_QUEEN.modelKey()).isEqualTo("chess/models/b_queen.glb");
        assertThat(ChessPiece.WHITE_PAWN.modelKey()).endsWith(".glb");
    }

    @Test
    void chessPiece_providesModelColor() {
        assertThat(ChessPiece.WHITE_KING.modelColor()).isEqualTo(0xFAF0E6);
        assertThat(ChessPiece.BLACK_KING.modelColor()).isEqualTo(0x3B3B3B);
    }

    @Test
    void afterMove_boardStateReflectsNewPosition() {
        ChessGame chess = ChessGame.create();
        chess.move("e2e4");

        var state = chess.state();
        // e4 should have the white pawn, e2 should be empty
        assertThat(state.pieceAt("e4")).hasValue(ChessPiece.WHITE_PAWN);
        assertThat(state.pieceAt("e2")).isEmpty();
    }

    @Test
    void afterCapture_capturedPiecesUpdated() {
        ChessGame chess = ChessGame.create();
        chess.move("e2e4");
        chess.move("d7d5");
        chess.move("e4d5"); // pawn captures

        var captured = chess.capturedPieces();
        assertThat(captured.byWhite()).hasSize(1);
        assertThat(captured.byWhite().get(0)).isEqualTo(ChessPiece.BLACK_PAWN);
    }

    @Test
    void ranks_producesCorrectStructure() {
        ChessGame chess = ChessGame.create();
        var ranks = chess.ranks();

        // 8 ranks, each with 8 squares
        assertThat(ranks).hasSize(8);
        assertThat(ranks.get(0).squares()).hasSize(8);

        // First rank shown is rank 8 (top of board), last is rank 1
        assertThat(ranks.get(0).label()).isEqualTo("8");
        assertThat(ranks.get(7).label()).isEqualTo("1");
    }
}
