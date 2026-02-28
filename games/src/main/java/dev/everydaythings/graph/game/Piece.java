package dev.everydaythings.graph.game;

/**
 * A game piece that can be placed on a {@link GameBoard} node.
 *
 * <p>Provides multi-fidelity rendering info:
 * <ul>
 *   <li>{@link #symbol()} — Unicode for text rendering</li>
 *   <li>{@link #imageKey()} — resource path for 2D rendering</li>
 *   <li>{@link #modelKey()} — resource path for 3D rendering</li>
 *   <li>{@link #colorCategory()} — color/side (for theming)</li>
 * </ul>
 *
 * <p>Implement directly — enums with constructor fields, or records.
 * The interface IS the contract; no annotation needed.
 *
 * @see BoardState
 */
public interface Piece {

    /**
     * Singleton empty piece (no piece on a space).
     */
    Piece EMPTY = new Piece() {
        @Override public String symbol() { return ""; }
        @Override public String imageKey() { return ""; }
        @Override public String modelKey() { return ""; }
        @Override public String colorCategory() { return ""; }
        @Override public int modelColor() { return -1; }
        @Override public boolean isEmpty() { return true; }
        @Override public String toString() { return "Piece.EMPTY"; }
    };

    /**
     * Unicode symbol for text rendering.
     */
    String symbol();

    /**
     * Resource path for 2D image/SVG.
     */
    String imageKey();

    /**
     * Resource path for 3D model/GLTF.
     */
    String modelKey();

    /**
     * Color/side category (e.g., "white", "black").
     */
    String colorCategory();

    /**
     * ARGB tint for 3D model rendering. Returns -1 for model default.
     */
    default int modelColor() { return -1; }

    /**
     * Whether this is the empty piece (no piece present).
     */
    default boolean isEmpty() {
        return false;
    }
}
