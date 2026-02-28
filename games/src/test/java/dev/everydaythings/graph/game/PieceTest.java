package dev.everydaythings.graph.game;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class PieceTest {

    record TestKing() implements Piece {
        @Override public String symbol() { return "♔"; }
        @Override public String imageKey() { return "chess/pieces/w_king.svg"; }
        @Override public String modelKey() { return "chess/models/w_king.glb"; }
        @Override public String colorCategory() { return "white"; }
    }

    record TestPawn() implements Piece {
        @Override public String symbol() { return "♟"; }
        @Override public String imageKey() { return ""; }
        @Override public String modelKey() { return ""; }
        @Override public String colorCategory() { return "black"; }
    }

    @Test
    void piece_providesRenderingMetadata() {
        TestKing king = new TestKing();
        assertThat(king.symbol()).isEqualTo("♔");
        assertThat(king.imageKey()).isEqualTo("chess/pieces/w_king.svg");
        assertThat(king.modelKey()).isEqualTo("chess/models/w_king.glb");
        assertThat(king.colorCategory()).isEqualTo("white");
    }

    @Test
    void piece_optionalFieldsCanBeEmpty() {
        TestPawn pawn = new TestPawn();
        assertThat(pawn.symbol()).isEqualTo("♟");
        assertThat(pawn.imageKey()).isEmpty();
        assertThat(pawn.modelKey()).isEmpty();
        assertThat(pawn.colorCategory()).isEqualTo("black");
    }

    @Test
    void empty_singleton() {
        assertThat(Piece.EMPTY.isEmpty()).isTrue();
        assertThat(Piece.EMPTY.symbol()).isEmpty();
        assertThat(Piece.EMPTY.imageKey()).isEmpty();
        assertThat(Piece.EMPTY.modelKey()).isEmpty();
        assertThat(Piece.EMPTY.colorCategory()).isEmpty();
    }

    @Test
    void piece_isNotEmpty() {
        TestKing king = new TestKing();
        assertThat(king.isEmpty()).isFalse();
    }
}
