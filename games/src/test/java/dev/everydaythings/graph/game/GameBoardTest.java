package dev.everydaythings.graph.game;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.*;

class GameBoardTest {

    @Test
    void rectangularGrid_creates64Nodes() {
        GameBoard board = GameBoard.rectangularGrid(8, 8);
        assertThat(board.nodeCount()).isEqualTo(64);
    }

    @Test
    void rectangularGrid_cornerHas3OrthogonalNeighbors() {
        GameBoard board = GameBoard.rectangularGrid(8, 8);

        // a1 is bottom-left corner
        Set<String> neighbors = board.neighbors("a1", "orthogonal");
        assertThat(neighbors).containsExactlyInAnyOrder("b1", "a2");

        // h8 is top-right corner
        neighbors = board.neighbors("h8", "orthogonal");
        assertThat(neighbors).containsExactlyInAnyOrder("g8", "h7");
    }

    @Test
    void rectangularGrid_centerHas4OrthogonalNeighbors() {
        GameBoard board = GameBoard.rectangularGrid(8, 8);

        // d4 is a center square
        Set<String> neighbors = board.neighbors("d4", "orthogonal");
        assertThat(neighbors).containsExactlyInAnyOrder("c4", "e4", "d3", "d5");
    }

    @Test
    void rectangularGrid_nodesHaveColRowAttributes() {
        GameBoard board = GameBoard.rectangularGrid(8, 8);

        GameBoard.Node a1 = board.node("a1");
        assertThat(a1).isNotNull();
        assertThat(a1.type()).isEqualTo("square");
        assertThat((int) a1.attr("col")).isEqualTo(0);
        assertThat((int) a1.attr("row")).isEqualTo(0);
        assertThat((String) a1.attr("background")).isEqualTo("dark");

        GameBoard.Node b1 = board.node("b1");
        assertThat((int) b1.attr("col")).isEqualTo(1);
        assertThat((int) b1.attr("row")).isEqualTo(0);
        assertThat((String) b1.attr("background")).isEqualTo("light");

        GameBoard.Node e4 = board.node("e4");
        assertThat((int) e4.attr("col")).isEqualTo(4);
        assertThat((int) e4.attr("row")).isEqualTo(3);
    }

    @Test
    void rectangularGrid_diagonalNeighbors() {
        GameBoard board = GameBoard.rectangularGrid(8, 8);

        // d4 has 4 diagonal neighbors
        Set<String> diag = board.neighbors("d4", "diagonal");
        assertThat(diag).containsExactlyInAnyOrder("c3", "e3", "c5", "e5");

        // a1 corner has 1 diagonal neighbor
        diag = board.neighbors("a1", "diagonal");
        assertThat(diag).containsExactlyInAnyOrder("b2");
    }

    @Test
    void customGraph_riskStyle() {
        GameBoard board = new GameBoard();
        board.addNode("alaska", "territory", Map.of("continent", "north-america"));
        board.addNode("kamchatka", "territory", Map.of("continent", "asia"));
        board.addNode("northwest", "territory", Map.of("continent", "north-america"));

        board.addBidirectional("alaska", "kamchatka", "border");
        board.addBidirectional("alaska", "northwest", "border");

        assertThat(board.nodeCount()).isEqualTo(3);
        assertThat(board.neighbors("alaska", "border"))
                .containsExactlyInAnyOrder("kamchatka", "northwest");
        assertThat(board.neighbors("kamchatka", "border"))
                .containsExactlyInAnyOrder("alaska");
        assertThat(board.neighbors("northwest", "border"))
                .containsExactlyInAnyOrder("alaska");
    }

    @Test
    void neighbors_filteredByLabel() {
        GameBoard board = GameBoard.rectangularGrid(3, 3);

        // b2 is center of 3x3 grid
        Set<String> ortho = board.neighbors("b2", "orthogonal");
        Set<String> diag = board.neighbors("b2", "diagonal");
        Set<String> all = board.neighbors("b2");

        assertThat(ortho).hasSize(4); // a2, c2, b1, b3
        assertThat(diag).hasSize(4);  // a1, c1, a3, c3
        assertThat(all).hasSize(8);   // all 8 surrounding cells
    }

    @Test
    void nodesOfType_filters() {
        GameBoard board = new GameBoard();
        board.addNode("h1", "hex");
        board.addNode("h2", "hex");
        board.addNode("v1", "vertex");
        board.addNode("e1", "edge");

        assertThat(board.nodesOfType("hex").count()).isEqualTo(2);
        assertThat(board.nodesOfType("vertex").count()).isEqualTo(1);
        assertThat(board.nodesOfType("edge").count()).isEqualTo(1);
        assertThat(board.nodesOfType("nonexistent").count()).isEqualTo(0);
    }

    @Test
    void addEdge_rejectsUnknownNodes() {
        GameBoard board = new GameBoard();
        board.addNode("a", "square");

        assertThatThrownBy(() -> board.addEdge("a", "b", "link"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown node: b");

        assertThatThrownBy(() -> board.addEdge("x", "a", "link"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown node: x");
    }

    @Test
    void gridLabel_format() {
        assertThat(GameBoard.gridLabel(0, 0)).isEqualTo("a1");
        assertThat(GameBoard.gridLabel(7, 7)).isEqualTo("h8");
        assertThat(GameBoard.gridLabel(4, 3)).isEqualTo("e4");
    }

    @Test
    void chessGrid_hasKnightEdges() {
        GameBoard board = GameBoard.chessGrid();
        assertThat(board.nodeCount()).isEqualTo(64);

        // b1 knight can reach a3, c3 (L-shaped)
        Set<String> knightMoves = board.neighbors("b1", "knight");
        assertThat(knightMoves).containsExactlyInAnyOrder("a3", "c3", "d2");

        // d4 knight can reach 8 squares
        knightMoves = board.neighbors("d4", "knight");
        assertThat(knightMoves).hasSize(8);
        assertThat(knightMoves).containsExactlyInAnyOrder(
                "b3", "b5", "c2", "c6", "e2", "e6", "f3", "f5");

        // a1 corner knight can reach only 2 squares
        knightMoves = board.neighbors("a1", "knight");
        assertThat(knightMoves).containsExactlyInAnyOrder("b3", "c2");

        // Still has orthogonal and diagonal edges too
        assertThat(board.neighbors("d4", "orthogonal")).hasSize(4);
        assertThat(board.neighbors("d4", "diagonal")).hasSize(4);
    }

    @Test
    void node_attr_withDefault() {
        GameBoard board = new GameBoard();
        board.addNode("x", "square", Map.of("col", 3));

        GameBoard.Node node = board.node("x");
        assertThat((int) node.attr("col")).isEqualTo(3);
        assertThat((String) node.attr("missing", "fallback")).isEqualTo("fallback");
    }
}
