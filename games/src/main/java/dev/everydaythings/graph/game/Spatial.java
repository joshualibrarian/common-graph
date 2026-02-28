package dev.everydaythings.graph.game;

import dev.everydaythings.graph.ui.scene.View;

/**
 * Capability: the game has a board topology with pieces on it.
 *
 * <p>Covers any game where tokens/pieces occupy named spaces:
 * chess grids, Risk territories, Catan hex maps, Scrabble tile grids,
 * growing tile-placement boards.
 *
 * <p>The board is a labeled graph (not grid-specific) that works across
 * text, 2D, and 3D renderers.
 *
 * @param <P> the piece type occupying board spaces
 * @see GameBoard
 * @see BoardState
 */
public interface Spatial<P extends Piece> {

    /**
     * The board topology (graph of spaces and edges).
     * Typically constant for a given game type.
     */
    GameBoard board();

    /**
     * Current piece positions on the board.
     * Live, mutable state reflecting the current game position.
     */
    BoardState<P> state();

    /**
     * Renderer-agnostic view of the current board position.
     * Works across CLI (unicode), TUI, and GUI.
     */
    View viewBoard();
}
