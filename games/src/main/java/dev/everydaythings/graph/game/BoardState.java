package dev.everydaythings.graph.game;

import java.util.*;
import java.util.stream.Stream;

/** work. My chan
 * Tracks which pieces are on which spaces of a {@link GameBoard}.
 *
 * <p>BoardState is mutable — game logic modifies it as moves are made.
 * Combined with GameBoard (topology), this gives you the full board
 * at any point in time.
 *
 * @param <P> The piece type
 */
public class BoardState<P extends Piece> {

    private final GameBoard board;
    private final Map<String, P> pieces = new LinkedHashMap<>();

    /**
     * Create an empty board state for the given board topology.
     */
    public BoardState(GameBoard board) {
        this.board = Objects.requireNonNull(board, "board");
    }

    /**
     * Get the underlying board topology.
     */
    public GameBoard board() {
        return board;
    }

    // ==================================================================================
    // Queries
    // ==================================================================================

    /**
     * Get the piece at a node.
     *
     * @param nodeId The board node ID
     * @return The piece, or empty if no piece is there
     */
    public Optional<P> pieceAt(String nodeId) {
        return Optional.ofNullable(pieces.get(nodeId));
    }

    /**
     * Check if a node has no piece on it.
     */
    public boolean isEmpty(String nodeId) {
        return !pieces.containsKey(nodeId);
    }

    /**
     * Stream all occupied nodes with their pieces.
     */
    public Stream<Map.Entry<String, P>> occupiedNodes() {
        return pieces.entrySet().stream();
    }

    /**
     * Get an unmodifiable view of the pieces map (node ID → piece).
     *
     * <p>Exposed for binding path traversal — allows expressions like
     * {@code "state.pieces.a1"} to resolve via Map key access.
     */
    public Map<String, P> pieces() {
        return Collections.unmodifiableMap(pieces);
    }

    /**
     * Get the number of pieces on the board.
     */
    public int pieceCount() {
        return pieces.size();
    }

    // ==================================================================================
    // Mutations
    // ==================================================================================

    /**
     * Place a piece on a node.
     *
     * @param nodeId The board node ID
     * @param piece  The piece to place
     * @throws IllegalArgumentException if the node doesn't exist on the board
     */
    public void place(String nodeId, P piece) {
        if (!board.hasNode(nodeId)) {
            throw new IllegalArgumentException("Unknown node: " + nodeId);
        }
        Objects.requireNonNull(piece, "piece");
        pieces.put(nodeId, piece);
    }

    /**
     * Remove a piece from a node.
     *
     * @param nodeId The board node ID
     * @return The removed piece, or empty if the node was empty
     */
    public Optional<P> remove(String nodeId) {
        return Optional.ofNullable(pieces.remove(nodeId));
    }

    /**
     * Move a piece from one node to another.
     *
     * <p>If the destination already has a piece, it is replaced (captured).
     *
     * @param from Source node ID
     * @param to   Destination node ID
     * @throws IllegalStateException if there is no piece at the source
     * @throws IllegalArgumentException if either node doesn't exist
     */
    public void move(String from, String to) {
        if (!board.hasNode(from)) {
            throw new IllegalArgumentException("Unknown node: " + from);
        }
        if (!board.hasNode(to)) {
            throw new IllegalArgumentException("Unknown node: " + to);
        }
        P piece = pieces.remove(from);
        if (piece == null) {
            throw new IllegalStateException("No piece at " + from);
        }
        pieces.put(to, piece);
    }

    /**
     * Remove all pieces from the board.
     */
    public void clear() {
        pieces.clear();
    }

    @Override
    public String toString() {
        return "BoardState[" + pieceCount() + " pieces on " + board + "]";
    }
}
