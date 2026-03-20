package dev.everydaythings.graph.game.chess;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for chess notation recognition across all supported variants.
 */
class ChessNotationTest {

    // ==================================================================================
    // Standard Algebraic Notation (SAN)
    // ==================================================================================

    @ParameterizedTest
    @ValueSource(strings = {
            "e4", "d5", "a3", "h6",           // pawn moves
            "Nf3", "Bb5", "Qd1", "Ke2",       // piece moves
            "Rg1", "Ba4",                       // more pieces
    })
    void san_basicMoves(String move) {
        assertThat(ChessNotation.isChessMove(move)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "exd5", "Bxe5", "Nxf7", "Qxd7",   // captures
            "dxe5", "fxg4",                      // pawn captures
    })
    void san_captures(String move) {
        assertThat(ChessNotation.isChessMove(move)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "e8=Q", "a1=R", "h8=N",             // with = sign
    })
    void san_promotions(String move) {
        assertThat(ChessNotation.isChessMove(move)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "Rae1", "Rfe1",                      // file disambiguation
            "R1e1", "N3d2",                      // rank disambiguation
            "Raxe1",                              // file disambiguation + capture
    })
    void san_disambiguation(String move) {
        assertThat(ChessNotation.isChessMove(move)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "Nf3+", "Qxd7+",                    // check
            "Qh7#",                               // checkmate
            "e4+",                                // pawn move with check
    })
    void san_checkAndMate(String move) {
        assertThat(ChessNotation.isChessMove(move)).isTrue();
    }

    // ==================================================================================
    // Castling
    // ==================================================================================

    @ParameterizedTest
    @ValueSource(strings = {
            "O-O", "O-O-O",                     // standard
            "0-0", "0-0-0",                      // with zeros
            "o-o", "o-o-o",                      // lowercase
            "O-O+", "O-O-O#",                   // with check/mate
    })
    void castling(String move) {
        assertThat(ChessNotation.isChessMove(move)).isTrue();
    }

    // ==================================================================================
    // Long Algebraic / UCI
    // ==================================================================================

    @ParameterizedTest
    @ValueSource(strings = {
            "e2e4", "d7d5", "g1f3",             // UCI
            "e7e8q",                              // UCI promotion (lowercase)
            "Ng1f3", "Bb5c6",                    // long algebraic with piece
    })
    void longAlgebraicAndUci(String move) {
        assertThat(ChessNotation.isChessMove(move)).isTrue();
    }

    // ==================================================================================
    // Coordinate (with hyphen)
    // ==================================================================================

    @ParameterizedTest
    @ValueSource(strings = {
            "e2-e4", "g1-f3", "d7-d5",
            "e7-e8Q",                             // with promotion
    })
    void coordinate(String move) {
        assertThat(ChessNotation.isChessMove(move)).isTrue();
    }

    // ==================================================================================
    // ICCF Numeric
    // ==================================================================================

    @ParameterizedTest
    @ValueSource(strings = {
            "5254",                               // e2-e4
            "7163",                               // g1-f3
            "4745",                               // d7-d5
    })
    void iccfNumeric(String move) {
        assertThat(ChessNotation.isChessMove(move)).isTrue();
    }

    // ==================================================================================
    // Descriptive Notation (English)
    // ==================================================================================

    @ParameterizedTest
    @ValueSource(strings = {
            "P-K4",                               // pawn to king 4
            "N-KB3",                              // knight to king's bishop 3
            "B-N5",                               // bishop to knight 5
            "PxP",                                // pawn takes pawn
            "QxBP",                               // queen takes bishop's pawn
    })
    void descriptive(String move) {
        assertThat(ChessNotation.isChessMove(move)).isTrue();
    }

    // ==================================================================================
    // Figurine Algebraic
    // ==================================================================================

    @ParameterizedTest
    @ValueSource(strings = {
            "♞f3",                                // knight to f3
            "♗b5",                                // bishop to b5
            "♕xd7",                               // queen captures d7
            "♖e1",                                // rook to e1
    })
    void figurineAlgebraic(String move) {
        assertThat(ChessNotation.isChessMove(move)).isTrue();
    }

    // ==================================================================================
    // Annotations (stripped, not rejected)
    // ==================================================================================

    @ParameterizedTest
    @ValueSource(strings = {
            "e4!", "Nf3?", "Bb5!!", "d5??",
            "e4!?", "Nf3?!",
    })
    void annotatedMoves(String move) {
        assertThat(ChessNotation.isChessMove(move)).isTrue();
    }

    // ==================================================================================
    // Non-moves (should NOT match)
    // ==================================================================================

    @ParameterizedTest
    @ValueSource(strings = {
            "hello", "create", "chess",           // English words
            "42", "3.14",                          // numbers
            "+", "=",                              // operators
            "j4", "a9", "i1",                     // invalid squares
            "", " ",                               // empty
    })
    void nonMoves(String text) {
        assertThat(ChessNotation.isChessMove(text)).isFalse();
    }

    // ==================================================================================
    // Move numbers (should be skipped, not treated as moves)
    // ==================================================================================

    @Test
    void moveNumbers_areNotMoves() {
        assertThat(ChessNotation.isChessMove("1.")).isFalse();
        assertThat(ChessNotation.isChessMove("23.")).isFalse();
        assertThat(ChessNotation.isChessMove("1...")).isFalse();
    }

    // ==================================================================================
    // ChessMove predicate
    // ==================================================================================

    @Test
    void chessMove_contributesDelegatesToChessNotation() {
        var chessMove = new ChessMove();
        var contribution = chessMove.contribute(null);
        assertThat(contribution.subLanguage()).isNotNull();
        assertThat(contribution.subLanguage()).isInstanceOf(ChessNotation.class);
    }
}
