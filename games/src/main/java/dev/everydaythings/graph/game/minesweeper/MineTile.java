package dev.everydaythings.graph.game.minesweeper;

import dev.everydaythings.graph.game.Piece;

/**
 * The visual state of a single minesweeper cell.
 *
 * <p>Each constant represents what the player sees: hidden, flagged,
 * exploded mine, or a revealed count (0–8). The glyph IS the rendering —
 * no SVGs or 3D models needed; emoji and digits work across all tiers
 * (CLI, 2D, 3D text surfaces).
 *
 * <p>Boolean accessors ({@link #hidden()}, {@link #flagged()}, etc.)
 * support direct binding in {@code @Scene.State} expressions.
 *
 * @see Minesweeper
 */
public enum MineTile implements Piece {

    HIDDEN  ("■",  "hidden"),
    FLAGGED ("🚩", "flagged"),
    MINE    ("💣", "mine"),
    EMPTY_0 ("·",  "revealed"),
    EMPTY_1 ("1",  "revealed"),
    EMPTY_2 ("2",  "revealed"),
    EMPTY_3 ("3",  "revealed"),
    EMPTY_4 ("4",  "revealed"),
    EMPTY_5 ("5",  "revealed"),
    EMPTY_6 ("6",  "revealed"),
    EMPTY_7 ("7",  "revealed"),
    EMPTY_8 ("8",  "revealed");

    private static final MineTile[] BY_COUNT = {
            EMPTY_0, EMPTY_1, EMPTY_2, EMPTY_3,
            EMPTY_4, EMPTY_5, EMPTY_6, EMPTY_7, EMPTY_8
    };

    private final String symbol;
    private final String colorCategory;

    MineTile(String symbol, String colorCategory) {
        this.symbol = symbol;
        this.colorCategory = colorCategory;
    }

    /** Tile for a given adjacent mine count (0–8). */
    public static MineTile forCount(int adjacentMines) {
        if (adjacentMines < 0 || adjacentMines > 8) {
            throw new IllegalArgumentException("Adjacent mines must be 0-8, got: " + adjacentMines);
        }
        return BY_COUNT[adjacentMines];
    }

    /** Adjacent mine count for revealed number tiles, or -1 for non-count tiles. */
    public int adjacentCount() {
        return switch (this) {
            case EMPTY_0 -> 0; case EMPTY_1 -> 1; case EMPTY_2 -> 2;
            case EMPTY_3 -> 3; case EMPTY_4 -> 4; case EMPTY_5 -> 5;
            case EMPTY_6 -> 6; case EMPTY_7 -> 7; case EMPTY_8 -> 8;
            default -> -1;
        };
    }

    // --- State booleans (for @Scene.State binding) ---

    public boolean hidden()   { return this == HIDDEN; }
    public boolean flagged()  { return this == FLAGGED; }
    public boolean mine()     { return this == MINE; }
    public boolean revealed() { return adjacentCount() >= 0; }

    /** Display text — always the glyph. Every tile is visible in all renderers. */
    public String display() {
        return symbol;
    }

    // --- Piece interface ---

    @Override public String symbol()        { return symbol; }
    @Override public String imageKey()      { return ""; }
    @Override public String modelKey()      { return ""; }
    @Override public String colorCategory() { return colorCategory; }
}
