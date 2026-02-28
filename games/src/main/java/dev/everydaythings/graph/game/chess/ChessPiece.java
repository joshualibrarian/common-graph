package dev.everydaythings.graph.game.chess;

import dev.everydaythings.graph.game.Piece;

import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * All chess piece types with multi-fidelity rendering metadata.
 *
 * <p>Each enum constant holds a reference to the corresponding chesslib
 * {@link com.github.bhlangonijr.chesslib.Piece}, enabling O(1) lookup
 * in both directions.
 *
 * @see Piece
 * @see Chess
 */
public enum ChessPiece implements Piece {
    // Real Staunton heights (mm): King=78, Queen=68, Bishop=59, Knight=59, Rook=49, Pawn=43
    WHITE_KING  (com.github.bhlangonijr.chesslib.Piece.WHITE_KING,   "♔", "chess/pieces/w_king.svg",   "chess/models/w_king.glb",   "white", 78),
    WHITE_QUEEN (com.github.bhlangonijr.chesslib.Piece.WHITE_QUEEN,  "♕", "chess/pieces/w_queen.svg",  "chess/models/w_queen.glb",  "white", 68),
    WHITE_ROOK  (com.github.bhlangonijr.chesslib.Piece.WHITE_ROOK,   "♖", "chess/pieces/w_rook.svg",   "chess/models/w_rook.glb",   "white", 49),
    WHITE_BISHOP(com.github.bhlangonijr.chesslib.Piece.WHITE_BISHOP, "♗", "chess/pieces/w_bishop.svg", "chess/models/w_bishop.glb", "white", 59),
    WHITE_KNIGHT(com.github.bhlangonijr.chesslib.Piece.WHITE_KNIGHT, "♘", "chess/pieces/w_knight.svg", "chess/models/w_knight.glb", "white", 59),
    WHITE_PAWN  (com.github.bhlangonijr.chesslib.Piece.WHITE_PAWN,   "♙", "chess/pieces/w_pawn.svg",   "chess/models/w_pawn.glb",   "white", 43),

    BLACK_KING  (com.github.bhlangonijr.chesslib.Piece.BLACK_KING,   "♚", "chess/pieces/b_king.svg",   "chess/models/b_king.glb",   "black", 78),
    BLACK_QUEEN (com.github.bhlangonijr.chesslib.Piece.BLACK_QUEEN,  "♛", "chess/pieces/b_queen.svg",  "chess/models/b_queen.glb",  "black", 68),
    BLACK_ROOK  (com.github.bhlangonijr.chesslib.Piece.BLACK_ROOK,   "♜", "chess/pieces/b_rook.svg",   "chess/models/b_rook.glb",   "black", 49),
    BLACK_BISHOP(com.github.bhlangonijr.chesslib.Piece.BLACK_BISHOP, "♝", "chess/pieces/b_bishop.svg", "chess/models/b_bishop.glb", "black", 59),
    BLACK_KNIGHT(com.github.bhlangonijr.chesslib.Piece.BLACK_KNIGHT, "♞", "chess/pieces/b_knight.svg", "chess/models/b_knight.glb", "black", 59),
    BLACK_PAWN  (com.github.bhlangonijr.chesslib.Piece.BLACK_PAWN,   "♟", "chess/pieces/b_pawn.svg",   "chess/models/b_pawn.glb",   "black", 43);

    private static final Map<com.github.bhlangonijr.chesslib.Piece, ChessPiece> BY_LIB_PIECE =
            Stream.of(values()).collect(Collectors.toUnmodifiableMap(cp -> cp.libPiece, Function.identity()));

    private static final double KING_HEIGHT_MM = 78.0;

    private final com.github.bhlangonijr.chesslib.Piece libPiece;
    private final String symbol;
    private final String imageKey;
    private final String modelKey;
    private final String colorCategory;
    private final double stauntonHeightMm;

    ChessPiece(com.github.bhlangonijr.chesslib.Piece libPiece,
               String symbol, String imageKey, String modelKey, String colorCategory,
               double stauntonHeightMm) {
        this.libPiece = libPiece;
        this.symbol = symbol;
        this.imageKey = imageKey;
        this.modelKey = modelKey;
        this.colorCategory = colorCategory;
        this.stauntonHeightMm = stauntonHeightMm;
    }

    @Override public String symbol() { return symbol; }
    @Override public String imageKey() { return imageKey; }
    @Override public String modelKey() { return modelKey; }
    @Override public String colorCategory() { return colorCategory; }
    @Override public int modelColor() { return "white".equals(colorCategory) ? 0xFAF0E6 : 0x3B3B3B; }

    /**
     * Scale factor relative to the king's height, based on real Staunton dimensions.
     * King=1.0, Pawn≈0.55. Applied uniformly to all axes.
     */
    public double heightScale() { return stauntonHeightMm / KING_HEIGHT_MM; }

    /**
     * The underlying chesslib piece constant.
     */
    public com.github.bhlangonijr.chesslib.Piece libPiece() { return libPiece; }

    /**
     * Convert from chesslib Piece to ChessPiece.
     *
     * @return the matching ChessPiece, or null for NONE
     */
    public static ChessPiece from(com.github.bhlangonijr.chesslib.Piece piece) {
        return BY_LIB_PIECE.get(piece);
    }
}
