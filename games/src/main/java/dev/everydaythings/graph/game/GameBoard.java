package dev.everydaythings.graph.game;

import java.util.*;
import java.util.stream.Stream;

/**
 * A board represented as a labeled graph of spaces and adjacencies.
 *
 * <p>GameBoard generalizes the concept of a game board beyond rectangular grids.
 * Each space is a {@link Node} with an ID, type, and attributes. Spaces are
 * connected by {@link Edge}s with labeled relationships.
 *
 * <p>This works like a surface layout — it IS one — describing spatial
 * arrangement that works in text, 2D, and 3D.
 *
 * <p>Examples:
 * <ul>
 *   <li><b>Chess:</b> 64 nodes of type "square", edges labeled "orthogonal", "diagonal", "knight"</li>
 *   <li><b>Risk:</b> 42 nodes of type "territory", edges labeled "border"</li>
 *   <li><b>Catan:</b> nodes of types "hex", "vertex", "edge" with typed connections</li>
 * </ul>
 */
public class GameBoard {

    /**
     * A space on the board.
     *
     * @param id    Unique identifier (e.g., "a1", "e4", "kamchatka")
     * @param type  Node type (e.g., "square", "territory", "hex")
     * @param attrs Arbitrary attributes (col, row, background, continent, etc.)
     */
    public record Node(String id, String type, Map<String, Object> attrs) {
        public Node {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(type, "type");
            attrs = attrs == null ? Map.of() : Map.copyOf(attrs);
        }

        /**
         * Get an attribute value, or null if not present.
         */
        @SuppressWarnings("unchecked")
        public <T> T attr(String key) {
            return (T) attrs.get(key);
        }

        /**
         * Get an attribute value with a default.
         */
        @SuppressWarnings("unchecked")
        public <T> T attr(String key, T defaultValue) {
            Object v = attrs.get(key);
            return v != null ? (T) v : defaultValue;
        }
    }

    /**
     * A connection between two spaces.
     *
     * @param from  Source node ID
     * @param to    Target node ID
     * @param label Relationship type (e.g., "orthogonal", "diagonal", "border")
     */
    public record Edge(String from, String to, String label) {
        public Edge {
            Objects.requireNonNull(from, "from");
            Objects.requireNonNull(to, "to");
            Objects.requireNonNull(label, "label");
        }
    }

    // ==================================================================================
    // Core Data
    // ==================================================================================

    private final Map<String, Node> nodes = new LinkedHashMap<>();
    private final Map<String, List<Edge>> adjacency = new LinkedHashMap<>();

    // ==================================================================================
    // Builder Methods (return this for chaining)
    // ==================================================================================

    /**
     * Add a node to the board.
     *
     * @param id   Unique node identifier
     * @param type Node type
     * @param attrs Node attributes
     * @return this board for chaining
     */
    public GameBoard addNode(String id, String type, Map<String, Object> attrs) {
        Node node = new Node(id, type, attrs);
        nodes.put(id, node);
        adjacency.putIfAbsent(id, new ArrayList<>());
        return this;
    }

    /**
     * Add a node with no attributes.
     */
    public GameBoard addNode(String id, String type) {
        return addNode(id, type, Map.of());
    }

    /**
     * Add a directed edge.
     */
    public GameBoard addEdge(String from, String to, String label) {
        if (!nodes.containsKey(from)) {
            throw new IllegalArgumentException("Unknown node: " + from);
        }
        if (!nodes.containsKey(to)) {
            throw new IllegalArgumentException("Unknown node: " + to);
        }
        adjacency.computeIfAbsent(from, k -> new ArrayList<>()).add(new Edge(from, to, label));
        return this;
    }

    /**
     * Add a bidirectional edge (two directed edges).
     */
    public GameBoard addBidirectional(String from, String to, String label) {
        addEdge(from, to, label);
        addEdge(to, from, label);
        return this;
    }

    // ==================================================================================
    // Query Methods
    // ==================================================================================

    /**
     * Get a node by ID.
     *
     * @return the node, or null if not found
     */
    public Node node(String id) {
        return nodes.get(id);
    }

    /**
     * Get all neighbor IDs of a node (all edge labels).
     */
    public Set<String> neighbors(String id) {
        List<Edge> edges = adjacency.get(id);
        if (edges == null) return Set.of();
        Set<String> result = new LinkedHashSet<>();
        for (Edge e : edges) {
            result.add(e.to());
        }
        return Collections.unmodifiableSet(result);
    }

    /**
     * Get neighbor IDs filtered by edge label.
     */
    public Set<String> neighbors(String id, String edgeLabel) {
        List<Edge> edges = adjacency.get(id);
        if (edges == null) return Set.of();
        Set<String> result = new LinkedHashSet<>();
        for (Edge e : edges) {
            if (e.label().equals(edgeLabel)) {
                result.add(e.to());
            }
        }
        return Collections.unmodifiableSet(result);
    }

    /**
     * Get all edges from a node.
     */
    public List<Edge> edges(String id) {
        List<Edge> edges = adjacency.get(id);
        return edges != null ? Collections.unmodifiableList(edges) : List.of();
    }

    /**
     * Stream all nodes of a given type.
     */
    public Stream<Node> nodesOfType(String type) {
        return nodes.values().stream().filter(n -> n.type().equals(type));
    }

    /**
     * Stream all nodes.
     */
    public Stream<Node> nodes() {
        return nodes.values().stream();
    }

    /**
     * Get the number of nodes.
     */
    public int nodeCount() {
        return nodes.size();
    }

    /**
     * Check if a node exists.
     */
    public boolean hasNode(String id) {
        return nodes.containsKey(id);
    }

    // ==================================================================================
    // Factory Methods
    // ==================================================================================

    /**
     * Create a rectangular grid board (chess, checkers, go).
     *
     * <p>Nodes are labeled with chess-style notation: a1, b1, ..., h8.
     * Column letters start at 'a', row numbers start at 1.
     *
     * <p>Each node has attributes:
     * <ul>
     *   <li>{@code col} (int) — column index (0-based)</li>
     *   <li>{@code row} (int) — row index (0-based)</li>
     *   <li>{@code background} (String) — "light" or "dark" (checkerboard pattern)</li>
     * </ul>
     *
     * <p>Edges are labeled:
     * <ul>
     *   <li>{@code "orthogonal"} — horizontal/vertical adjacency</li>
     *   <li>{@code "diagonal"} — diagonal adjacency</li>
     * </ul>
     *
     * @param cols Number of columns
     * @param rows Number of rows
     * @return A new GameBoard with grid topology
     */
    public static GameBoard rectangularGrid(int cols, int rows) {
        if (cols < 1 || rows < 1) {
            throw new IllegalArgumentException("Grid must be at least 1x1");
        }

        GameBoard board = new GameBoard();

        // Create nodes
        for (int row = 0; row < rows; row++) {
            for (int col = 0; col < cols; col++) {
                String id = gridLabel(col, row);
                String background = (col + row) % 2 == 0 ? "dark" : "light";
                Map<String, Object> attrs = Map.of(
                        "col", col,
                        "row", row,
                        "background", background
                );
                board.addNode(id, "square", attrs);
            }
        }

        // Create edges
        for (int row = 0; row < rows; row++) {
            for (int col = 0; col < cols; col++) {
                String from = gridLabel(col, row);

                // Orthogonal neighbors
                if (col + 1 < cols) board.addBidirectional(from, gridLabel(col + 1, row), "orthogonal");
                if (row + 1 < rows) board.addBidirectional(from, gridLabel(col, row + 1), "orthogonal");

                // Diagonal neighbors
                if (col + 1 < cols && row + 1 < rows) board.addBidirectional(from, gridLabel(col + 1, row + 1), "diagonal");
                if (col - 1 >= 0 && row + 1 < rows) board.addBidirectional(from, gridLabel(col - 1, row + 1), "diagonal");
            }
        }

        return board;
    }

    /**
     * Create a standard chess board (8x8 grid with knight edges).
     *
     * <p>Includes all edge types relevant to chess:
     * <ul>
     *   <li>{@code "orthogonal"} — rook movement directions</li>
     *   <li>{@code "diagonal"} — bishop movement directions</li>
     *   <li>{@code "knight"} — knight movement (L-shaped jumps)</li>
     * </ul>
     *
     * @return A new GameBoard with full chess topology
     */
    public static GameBoard chessGrid() {
        GameBoard board = rectangularGrid(8, 8);

        int[][] knightOffsets = {
                {-2, -1}, {-2, 1}, {-1, -2}, {-1, 2},
                {1, -2}, {1, 2}, {2, -1}, {2, 1}
        };

        for (int row = 0; row < 8; row++) {
            for (int col = 0; col < 8; col++) {
                String from = gridLabel(col, row);
                for (int[] d : knightOffsets) {
                    int nc = col + d[0], nr = row + d[1];
                    if (nc >= 0 && nc < 8 && nr >= 0 && nr < 8) {
                        board.addEdge(from, gridLabel(nc, nr), "knight");
                    }
                }
            }
        }

        return board;
    }

    /**
     * Convert column/row to chess-style label (a1, b2, etc.).
     */
    public static String gridLabel(int col, int row) {
        return String.valueOf((char) ('a' + col)) + (row + 1);
    }

    /**
     * Parse a chess-style label (a1, b2, etc.) back to {col, row}.
     *
     * @return int array of {col, row}
     * @throws IllegalArgumentException if the label is malformed
     */
    public static int[] parseGridLabel(String label) {
        if (label == null || label.length() < 2) {
            throw new IllegalArgumentException("Invalid grid label: " + label);
        }
        int col = label.charAt(0) - 'a';
        int row = Integer.parseInt(label.substring(1)) - 1;
        if (col < 0) {
            throw new IllegalArgumentException("Invalid grid label: " + label);
        }
        return new int[]{col, row};
    }

    @Override
    public String toString() {
        return "GameBoard[" + nodeCount() + " nodes]";
    }
}
