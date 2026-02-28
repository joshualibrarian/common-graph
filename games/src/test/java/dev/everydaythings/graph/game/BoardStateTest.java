package dev.everydaythings.graph.game;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class BoardStateTest {

    record King() implements Piece {
        @Override public String symbol() { return "♔"; }
        @Override public String imageKey() { return ""; }
        @Override public String modelKey() { return ""; }
        @Override public String colorCategory() { return "white"; }
    }

    record Pawn() implements Piece {
        @Override public String symbol() { return "♟"; }
        @Override public String imageKey() { return ""; }
        @Override public String modelKey() { return ""; }
        @Override public String colorCategory() { return "black"; }
    }

    private GameBoard board;
    private BoardState<Piece> state;

    @BeforeEach
    void setUp() {
        board = GameBoard.rectangularGrid(8, 8);
        state = new BoardState<>(board);
    }

    @Test
    void place_and_query() {
        King king = new King();
        state.place("e1", king);

        assertThat(state.pieceAt("e1")).contains(king);
        assertThat(state.isEmpty("e1")).isFalse();
        assertThat(state.pieceCount()).isEqualTo(1);
    }

    @Test
    void isEmpty_forEmptyNode() {
        assertThat(state.isEmpty("a1")).isTrue();
        assertThat(state.pieceAt("a1")).isEmpty();
    }

    @Test
    void move_piece() {
        King king = new King();
        state.place("e1", king);

        state.move("e1", "e2");

        assertThat(state.isEmpty("e1")).isTrue();
        assertThat(state.pieceAt("e2")).contains(king);
        assertThat(state.pieceCount()).isEqualTo(1);
    }

    @Test
    void move_captures() {
        King king = new King();
        Pawn pawn = new Pawn();
        state.place("e1", king);
        state.place("e2", pawn);

        state.move("e1", "e2");

        assertThat(state.isEmpty("e1")).isTrue();
        assertThat(state.pieceAt("e2")).contains(king);
        assertThat(state.pieceCount()).isEqualTo(1);
    }

    @Test
    void remove_piece() {
        King king = new King();
        state.place("e1", king);

        var removed = state.remove("e1");

        assertThat(removed).contains(king);
        assertThat(state.isEmpty("e1")).isTrue();
        assertThat(state.pieceCount()).isEqualTo(0);
    }

    @Test
    void remove_empty_returnsEmpty() {
        assertThat(state.remove("a1")).isEmpty();
    }

    @Test
    void occupiedNodes_stream() {
        state.place("e1", new King());
        state.place("a7", new Pawn());
        state.place("b7", new Pawn());

        var occupied = state.occupiedNodes().toList();
        assertThat(occupied).hasSize(3);
        assertThat(occupied.stream().map(e -> e.getKey()).toList())
                .containsExactlyInAnyOrder("e1", "a7", "b7");
    }

    @Test
    void clear_removesAll() {
        state.place("e1", new King());
        state.place("a7", new Pawn());

        state.clear();

        assertThat(state.pieceCount()).isEqualTo(0);
        assertThat(state.isEmpty("e1")).isTrue();
    }

    @Test
    void place_rejectsUnknownNode() {
        assertThatThrownBy(() -> state.place("z9", new King()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown node");
    }

    @Test
    void move_throwsWhenNoPieceAtSource() {
        assertThatThrownBy(() -> state.move("e1", "e2"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("No piece at e1");
    }
}
