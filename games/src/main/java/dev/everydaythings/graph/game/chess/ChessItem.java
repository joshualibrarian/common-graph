package dev.everydaythings.graph.game.chess;

import com.github.bhlangonijr.chesslib.Board;
import com.github.bhlangonijr.chesslib.Piece;
import com.github.bhlangonijr.chesslib.Side;
import com.github.bhlangonijr.chesslib.Square;
import com.github.bhlangonijr.chesslib.move.Move;
import dev.everydaythings.graph.dispatch.ActionContext;
import dev.everydaythings.graph.game.GameVocabulary;
import dev.everydaythings.graph.item.Implements;
import dev.everydaythings.graph.item.Item;
import dev.everydaythings.graph.item.Literal;
import dev.everydaythings.graph.item.Param;
import dev.everydaythings.graph.item.Type;
import dev.everydaythings.graph.item.Verb;
import dev.everydaythings.graph.item.id.ItemID;
import dev.everydaythings.graph.language.CoreVocabulary;
import dev.everydaythings.graph.language.GrammaticalFeature;
import dev.everydaythings.graph.language.PartOfSpeech;
import dev.everydaythings.graph.language.Sememe;
import dev.everydaythings.graph.runtime.Librarian;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Chess as a proper Item — moves become frames via {@code relate()}.
 *
 * <p>Extends Item directly (no GameComponent hierarchy). Uses chesslib
 * for legal move generation and game-end detection. Each move is persisted
 * as a relation frame on this item.
 */
@Implements(ChessItem.Chess.KEY)
@Type(glyph = "♟️")
public class ChessItem extends Item {

    public static class Chess {
        public static final String KEY = "cg.sememe:chess";
        @Item.Seed public static final Sememe SEED = new Sememe(KEY)
                .gloss("en", "the game of chess")
                .word(PartOfSpeech.NOUN, GrammaticalFeature.Lemma.SEED, "en", "chess");
    }

    // ==================================================================================
    // Chess engine state (materialized from moves via chesslib)
    // ==================================================================================

    private transient Board board = new Board();
    private transient final List<String> moveHistory = new ArrayList<>();
    private transient ChessGame.GameResult result = ChessGame.GameResult.IN_PROGRESS;

    // Player seats: 0=white, 1=black (simple for v1)
    private transient final List<ItemID> players = new ArrayList<>(Arrays.asList(null, null));

    // ==================================================================================
    // Construction
    // ==================================================================================

    /** Fresh game — starting position. */
    public ChessItem(Librarian librarian) {
        super(librarian);
    }

    // ==================================================================================
    // Verbs
    // ==================================================================================

    @Verb(value = GameVocabulary.Move.KEY, doc = "Make a chess move in SAN or UCI notation")
    public String move(ActionContext ctx,
                       @Param(value = "notation", doc = "Move in algebraic notation (e.g., e4, Nf3, e2e4)") String san) {
        if (isGameOver()) return "Game is already over";

        // Normalize: strip whitespace so "d7 d6" → "d7d6" (UCI format)
        String normalized = san.replaceAll("\\s+", "");

        try {
            Move m = new Move(normalized, board.getSideToMove());
            if (!board.legalMoves().contains(m)) {
                return "Illegal move: " + san;
            }

            // Apply to engine
            board.doMove(m);
            moveHistory.add(normalized);

            // Persist as a relation frame on this item
            relate(GameVocabulary.Move.SEED.iid(), Literal.ofText(normalized));

            // Check game-end conditions
            updateResult();

            return null; // success
        } catch (Exception e) {
            return "Invalid move: " + san;
        }
    }

    @Verb(value = GameVocabulary.Resign.KEY, doc = "Resign the game")
    public String resign(ActionContext ctx) {
        if (isGameOver()) return "Game is already over";

        Side resigning = board.getSideToMove();
        result = resigning == Side.WHITE
                ? ChessGame.GameResult.BLACK_WINS_RESIGNATION
                : ChessGame.GameResult.WHITE_WINS_RESIGNATION;

        return (resigning == Side.WHITE ? "White" : "Black") + " resigns";
    }

    @Verb(value = GameVocabulary.Join.KEY, doc = "Join the game")
    public String join(ActionContext ctx,
                       @Param(value = "seat", doc = "Seat number (0=white, 1=black)", required = false) Integer seat) {
        ItemID caller = ctx.caller();
        if (caller == null) return "No caller identity";

        if (seat != null) {
            if (seat < 0 || seat > 1) return "Seat must be 0 (white) or 1 (black)";
            if (players.get(seat) != null) return "Seat " + seat + " is already taken";
            players.set(seat, caller);
            return "Joined as " + (seat == 0 ? "white" : "black");
        }

        // Auto-assign first available
        for (int i = 0; i < 2; i++) {
            if (players.get(i) == null) {
                players.set(i, caller);
                return "Joined as " + (i == 0 ? "white" : "black");
            }
        }
        return "Game is full";
    }

    @Verb(value = CoreVocabulary.Show.KEY, doc = "Show the chess board")
    public String show() {
        return renderBoard();
    }

    // ==================================================================================
    // Queries
    // ==================================================================================

    public boolean isGameOver() {
        return result != ChessGame.GameResult.IN_PROGRESS;
    }

    public String fen() {
        return board.getFen();
    }

    public int moveCount() {
        return moveHistory.size();
    }

    public List<String> moves() {
        return List.copyOf(moveHistory);
    }

    public ChessGame.GameResult result() {
        return result;
    }

    public Side sideToMove() {
        return board.getSideToMove();
    }

    public boolean isCheck() {
        return board.isKingAttacked();
    }

    // ==================================================================================
    // Board rendering
    // ==================================================================================

    /**
     * Render the board as Unicode text.
     */
    public String renderBoard() {
        StringBuilder sb = new StringBuilder();
        sb.append("  a b c d e f g h\n");

        for (int rank = 7; rank >= 0; rank--) {
            sb.append(rank + 1).append(" ");
            for (int file = 0; file < 8; file++) {
                Square sq = Square.squareAt(rank * 8 + file);
                Piece piece = board.getPiece(sq);
                if (piece != Piece.NONE) {
                    sb.append(ChessPiece.from(piece).symbol());
                } else {
                    sb.append((rank + file) % 2 == 0 ? "·" : " ");
                }
                sb.append(" ");
            }
            sb.append(rank + 1).append("\n");
        }

        sb.append("  a b c d e f g h\n");
        sb.append(sideToMove() == Side.WHITE ? "White" : "Black").append(" to move");
        if (isCheck()) sb.append(" (check)");
        sb.append("\n");

        return sb.toString();
    }

    // ==================================================================================
    // Internal
    // ==================================================================================

    private void updateResult() {
        if (board.isMated()) {
            result = board.getSideToMove() == Side.WHITE
                    ? ChessGame.GameResult.BLACK_WINS_CHECKMATE
                    : ChessGame.GameResult.WHITE_WINS_CHECKMATE;
        } else if (board.isStaleMate()) {
            result = ChessGame.GameResult.DRAW_STALEMATE;
        } else if (board.isInsufficientMaterial()) {
            result = ChessGame.GameResult.DRAW_INSUFFICIENT_MATERIAL;
        } else if (board.isRepetition()) {
            result = ChessGame.GameResult.DRAW_THREEFOLD_REPETITION;
        } else if (board.getHalfMoveCounter() >= 100) {
            result = ChessGame.GameResult.DRAW_FIFTY_MOVE_RULE;
        }
    }
}
