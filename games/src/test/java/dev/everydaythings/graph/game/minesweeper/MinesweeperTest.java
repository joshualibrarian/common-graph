package dev.everydaythings.graph.game.minesweeper;

import dev.everydaythings.graph.game.BoardState;
import dev.everydaythings.graph.game.GameBoard;
import dev.everydaythings.graph.ui.scene.SceneCompiler;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.*;

class MinesweeperTest {

    // ==================================================================================
    // Creation and Difficulty
    // ==================================================================================

    @Test
    void create_beginner_hasCorrectDimensions() {
        Minesweeper game = Minesweeper.create(Minesweeper.Difficulty.BEGINNER);
        assertThat(game.cols()).isEqualTo(9);
        assertThat(game.rows()).isEqualTo(9);
        assertThat(game.mineCount()).isEqualTo(10);
    }

    @Test
    void create_intermediate_hasCorrectDimensions() {
        Minesweeper game = Minesweeper.create(Minesweeper.Difficulty.INTERMEDIATE);
        assertThat(game.cols()).isEqualTo(16);
        assertThat(game.rows()).isEqualTo(16);
        assertThat(game.mineCount()).isEqualTo(40);
    }

    @Test
    void create_expert_hasCorrectDimensions() {
        Minesweeper game = Minesweeper.create(Minesweeper.Difficulty.EXPERT);
        assertThat(game.cols()).isEqualTo(30);
        assertThat(game.rows()).isEqualTo(16);
        assertThat(game.mineCount()).isEqualTo(99);
    }

    @Test
    void create_allTilesHidden() {
        Minesweeper game = Minesweeper.create();
        for (int x = 0; x < game.cols(); x++) {
            for (int y = 0; y < game.rows(); y++) {
                assertThat(game.tileAt(x, y)).isEqualTo(MineTile.HIDDEN);
            }
        }
    }

    @Test
    void create_minesNotPlacedYet() {
        Minesweeper game = Minesweeper.create();
        assertThat(game.minesPlaced()).isFalse();
    }

    // ==================================================================================
    // GameComponent Properties
    // ==================================================================================

    @Test
    void singlePlayer() {
        Minesweeper game = Minesweeper.create();
        assertThat(game.minPlayers()).isEqualTo(1);
        assertThat(game.maxPlayers()).isEqualTo(1);
    }

    @Test
    void activePlayers_singlePlayerActive() {
        Minesweeper game = Minesweeper.create();
        assertThat(game.activePlayers()).isEqualTo(Set.of(0));
    }

    @Test
    void notGameOverAtStart() {
        Minesweeper game = Minesweeper.create();
        assertThat(game.isGameOver()).isFalse();
        assertThat(game.isWon()).isFalse();
        assertThat(game.isLost()).isFalse();
    }

    // ==================================================================================
    // First Reveal — Mine Safety
    // ==================================================================================

    @Test
    void firstReveal_neverAMine() {
        // Run many times with different positions to ensure first click is safe
        for (int x = 0; x < 9; x++) {
            for (int y = 0; y < 9; y++) {
                Minesweeper game = Minesweeper.create();
                game.reveal(x, y);
                assertThat(game.isLost())
                        .as("First reveal at (%d,%d) should never hit a mine", x, y)
                        .isFalse();
                assertThat(game.minesPlaced()).isTrue();
                assertThat(game.hasMineAt(x, y)).isFalse();
            }
        }
    }

    @Test
    void firstReveal_placesMines() {
        Minesweeper game = Minesweeper.create();
        assertThat(game.minesPlaced()).isFalse();
        game.reveal(4, 4);
        assertThat(game.minesPlaced()).isTrue();
    }

    // ==================================================================================
    // Reveal Mechanics
    // ==================================================================================

    @Test
    void reveal_showsNumberTile() {
        Minesweeper game = Minesweeper.create();
        game.reveal(4, 4);

        // At least the clicked tile should be revealed
        MineTile tile = game.tileAt(4, 4);
        assertThat(tile.revealed()).isTrue();
    }

    @Test
    void reveal_floodFill_revealsConnectedZeros() {
        Minesweeper game = Minesweeper.create();
        game.reveal(4, 4);

        // Count revealed tiles — flood fill from a zero should reveal multiple
        int revealed = game.revealedCount();
        assertThat(revealed).isGreaterThanOrEqualTo(1);

        // All revealed tiles should be EMPTY_0 through EMPTY_8
        for (int x = 0; x < game.cols(); x++) {
            for (int y = 0; y < game.rows(); y++) {
                MineTile tile = game.tileAt(x, y);
                if (tile.revealed()) {
                    assertThat(tile.adjacentCount()).isBetween(0, 8);
                }
            }
        }
    }

    @Test
    void reveal_alreadyRevealed_noOp() {
        Minesweeper game = Minesweeper.create();
        game.reveal(4, 4);
        int countBefore = game.revealedCount();

        // Revealing same tile again should not change anything
        boolean result = game.reveal(4, 4);
        assertThat(result).isFalse();
        assertThat(game.revealedCount()).isEqualTo(countBefore);
    }

    @Test
    void reveal_outOfBounds_rejected() {
        Minesweeper game = Minesweeper.create();
        assertThat(game.reveal(-1, 0)).isFalse();
        assertThat(game.reveal(0, -1)).isFalse();
        assertThat(game.reveal(9, 0)).isFalse();
        assertThat(game.reveal(0, 9)).isFalse();
    }

    @Test
    void reveal_flaggedTile_rejected() {
        Minesweeper game = Minesweeper.create();
        game.flag(3, 3);
        assertThat(game.reveal(3, 3)).isFalse();
    }

    // ==================================================================================
    // Flag Mechanics
    // ==================================================================================

    @Test
    void flag_togglesHiddenToFlagged() {
        Minesweeper game = Minesweeper.create();
        game.flag(0, 0);
        assertThat(game.tileAt(0, 0)).isEqualTo(MineTile.FLAGGED);
        assertThat(game.flagCount()).isEqualTo(1);
    }

    @Test
    void flag_togglesFlaggedToHidden() {
        Minesweeper game = Minesweeper.create();
        game.flag(0, 0);
        game.flag(0, 0);
        assertThat(game.tileAt(0, 0)).isEqualTo(MineTile.HIDDEN);
        assertThat(game.flagCount()).isEqualTo(0);
    }

    @Test
    void flag_revealedTile_rejected() {
        Minesweeper game = Minesweeper.create();
        game.reveal(4, 4);
        // Try to flag a revealed tile
        if (game.tileAt(4, 4).revealed()) {
            assertThat(game.flag(4, 4)).isFalse();
        }
    }

    @Test
    void remainingMines_tracksFlags() {
        Minesweeper game = Minesweeper.create();
        assertThat(game.remainingMines()).isEqualTo(10);
        game.flag(0, 0);
        assertThat(game.remainingMines()).isEqualTo(9);
        game.flag(1, 0);
        assertThat(game.remainingMines()).isEqualTo(8);
        game.flag(0, 0); // unflag
        assertThat(game.remainingMines()).isEqualTo(9);
    }

    // ==================================================================================
    // Chord Mechanics
    // ==================================================================================

    @Test
    void chord_onHiddenTile_rejected() {
        Minesweeper game = Minesweeper.create();
        assertThat(game.chord(0, 0)).isFalse();
    }

    @Test
    void chord_onZeroTile_rejected() {
        Minesweeper game = Minesweeper.create();
        game.reveal(4, 4);

        // Find a zero tile
        for (int x = 0; x < game.cols(); x++) {
            for (int y = 0; y < game.rows(); y++) {
                if (game.tileAt(x, y) == MineTile.EMPTY_0) {
                    assertThat(game.chord(x, y)).isFalse();
                    return;
                }
            }
        }
    }

    // ==================================================================================
    // Win Condition
    // ==================================================================================

    @Test
    void win_allNonMinesRevealed() {
        // Use a beginner board and systematically reveal all non-mine tiles
        Minesweeper game = Minesweeper.create();
        game.reveal(4, 4); // Place mines, start revealing

        // Reveal every hidden non-mine tile
        boolean progress = true;
        while (progress && !game.isGameOver()) {
            progress = false;
            for (int x = 0; x < game.cols(); x++) {
                for (int y = 0; y < game.rows(); y++) {
                    if (game.tileAt(x, y) == MineTile.HIDDEN && !game.hasMineAt(x, y)) {
                        game.reveal(x, y);
                        progress = true;
                    }
                }
            }
        }

        assertThat(game.isWon()).isTrue();
        assertThat(game.isGameOver()).isTrue();
        assertThat(game.winner()).hasValue(0);
        assertThat(game.activePlayers()).isEmpty();
    }

    // ==================================================================================
    // Loss Condition
    // ==================================================================================

    @Test
    void loss_revealingMine() {
        Minesweeper game = Minesweeper.create();
        game.reveal(4, 4); // Place mines first

        // Find and reveal a mine
        for (int x = 0; x < game.cols(); x++) {
            for (int y = 0; y < game.rows(); y++) {
                if (game.hasMineAt(x, y) && game.tileAt(x, y) == MineTile.HIDDEN) {
                    game.reveal(x, y);

                    assertThat(game.isLost()).isTrue();
                    assertThat(game.isGameOver()).isTrue();
                    assertThat(game.winner()).isEmpty();
                    assertThat(game.activePlayers()).isEmpty();

                    // All mines should be revealed
                    for (int mx = 0; mx < game.cols(); mx++) {
                        for (int my = 0; my < game.rows(); my++) {
                            if (game.hasMineAt(mx, my)) {
                                MineTile tile = game.tileAt(mx, my);
                                assertThat(tile == MineTile.MINE || tile == MineTile.FLAGGED)
                                        .as("Mine at (%d,%d) should be revealed or flagged", mx, my)
                                        .isTrue();
                            }
                        }
                    }
                    return;
                }
            }
        }
        fail("No mine found to test loss condition");
    }

    @Test
    void afterGameOver_actionsRejected() {
        Minesweeper game = Minesweeper.create();
        game.reveal(4, 4);

        // Lose by hitting a mine
        for (int x = 0; x < game.cols(); x++) {
            for (int y = 0; y < game.rows(); y++) {
                if (game.hasMineAt(x, y) && game.tileAt(x, y) == MineTile.HIDDEN) {
                    game.reveal(x, y);
                    break;
                }
            }
            if (game.isGameOver()) break;
        }

        assertThat(game.isGameOver()).isTrue();
        assertThat(game.reveal(0, 0)).isFalse();
        assertThat(game.flag(0, 0)).isFalse();
        assertThat(game.chord(0, 0)).isFalse();
    }

    // ==================================================================================
    // Board Topology (Spatial)
    // ==================================================================================

    @Test
    void board_hasCorrectSize() {
        Minesweeper game = Minesweeper.create();
        GameBoard board = game.board();
        // 9x9 = 81 nodes
        assertThat(board.nodeCount()).isEqualTo(81);
    }

    @Test
    void board_cornerNeighbors() {
        Minesweeper game = Minesweeper.create();
        GameBoard board = game.board();
        // Corner (a1) should have 3 neighbors in a rectangular grid
        assertThat(board.neighbors("a1")).hasSize(3);
    }

    @Test
    void board_edgeNeighbors() {
        Minesweeper game = Minesweeper.create();
        GameBoard board = game.board();
        // Edge (b1) should have 5 neighbors
        assertThat(board.neighbors("b1")).hasSize(5);
    }

    @Test
    void board_centerNeighbors() {
        Minesweeper game = Minesweeper.create();
        GameBoard board = game.board();
        // Center should have 8 neighbors
        assertThat(board.neighbors("e5")).hasSize(8);
    }

    @Test
    void state_reflectsTiles() {
        Minesweeper game = Minesweeper.create();
        game.reveal(4, 4);

        BoardState<MineTile> state = game.state();
        // Every cell should have a tile
        for (int x = 0; x < game.cols(); x++) {
            for (int y = 0; y < game.rows(); y++) {
                String nodeId = GameBoard.gridLabel(x, y);
                assertThat(state.pieceAt(nodeId)).isPresent();
                assertThat(state.pieceAt(nodeId).get()).isEqualTo(game.tileAt(x, y));
            }
        }
    }

    // ==================================================================================
    // CBOR Round-Trip
    // ==================================================================================

    @Test
    void cborRoundTrip_revealOp() {
        Minesweeper game = Minesweeper.create();
        var op = new Minesweeper.RevealOp(3, 7);
        var encoded = game.encodeOp(op);
        var decoded = game.decodeOp(encoded);
        assertThat(decoded).isEqualTo(op);
    }

    @Test
    void cborRoundTrip_flagOp() {
        Minesweeper game = Minesweeper.create();
        var op = new Minesweeper.FlagOp(5, 2);
        var encoded = game.encodeOp(op);
        var decoded = game.decodeOp(encoded);
        assertThat(decoded).isEqualTo(op);
    }

    @Test
    void cborRoundTrip_chordOp() {
        Minesweeper game = Minesweeper.create();
        var op = new Minesweeper.ChordOp(1, 8);
        var encoded = game.encodeOp(op);
        var decoded = game.decodeOp(encoded);
        assertThat(decoded).isEqualTo(op);
    }

    // ==================================================================================
    // Determinism
    // ==================================================================================

    @Test
    void deterministic_sameSeed_sameLayout() {
        // Two games with the same reveal should produce the same mine layout
        // (because GameRandom.fromEvent uses event CID as seed)
        Minesweeper game1 = Minesweeper.create();
        Minesweeper game2 = Minesweeper.create();

        game1.reveal(4, 4);
        game2.reveal(4, 4);

        for (int x = 0; x < 9; x++) {
            for (int y = 0; y < 9; y++) {
                assertThat(game1.hasMineAt(x, y))
                        .as("Mine at (%d,%d)", x, y)
                        .isEqualTo(game2.hasMineAt(x, y));
            }
        }
    }

    // ==================================================================================
    // MineTile
    // ==================================================================================

    @Test
    void mineTile_forCount_allValid() {
        for (int i = 0; i <= 8; i++) {
            MineTile tile = MineTile.forCount(i);
            assertThat(tile.adjacentCount()).isEqualTo(i);
            assertThat(tile.revealed()).isTrue();
        }
    }

    @Test
    void mineTile_forCount_outOfRange() {
        assertThatIllegalArgumentException().isThrownBy(() -> MineTile.forCount(-1));
        assertThatIllegalArgumentException().isThrownBy(() -> MineTile.forCount(9));
    }

    @Test
    void mineTile_nonRevealedTypes() {
        assertThat(MineTile.HIDDEN.revealed()).isFalse();
        assertThat(MineTile.FLAGGED.revealed()).isFalse();
        assertThat(MineTile.MINE.revealed()).isFalse();
    }

    @Test
    void mineTile_symbols() {
        assertThat(MineTile.HIDDEN.symbol()).isEqualTo("■");
        assertThat(MineTile.FLAGGED.symbol()).isEqualTo("🚩");
        assertThat(MineTile.MINE.symbol()).isEqualTo("💣");
        assertThat(MineTile.EMPTY_0.symbol()).isEqualTo("·");
        assertThat(MineTile.EMPTY_3.symbol()).isEqualTo("3");
    }

    // ==================================================================================
    // Text Rendering
    // ==================================================================================

    @Test
    void renderBoard_showsGrid() {
        Minesweeper game = Minesweeper.create();
        String board = game.renderBoard();
        assertThat(board).contains("■"); // hidden tiles
        assertThat(board).contains("Mines:"); // status line
    }

    @Test
    void statusText_inProgress() {
        Minesweeper game = Minesweeper.create();
        assertThat(game.statusText()).contains("remaining");
    }

    @Test
    void statusText_won() {
        Minesweeper game = Minesweeper.create();
        game.reveal(4, 4);
        for (int x = 0; x < game.cols(); x++) {
            for (int y = 0; y < game.rows(); y++) {
                if (game.tileAt(x, y) == MineTile.HIDDEN && !game.hasMineAt(x, y)) {
                    game.reveal(x, y);
                }
            }
        }
        if (game.isWon()) {
            assertThat(game.statusText()).contains("win");
        }
    }

    // ==================================================================================
    // Surface Compilation
    // ==================================================================================

    @Test
    void surfaceCompiler_canCompile() {
        assertThat(SceneCompiler.canCompile(Minesweeper.class)).isTrue();
    }

    @Test
    void surface_rendersGridRows() {
        Minesweeper game = Minesweeper.create();
        dev.everydaythings.graph.ui.scene.View view = SceneCompiler.compile(game);
        assertThat(view).isNotNull();
        assertThat(view.root()).isNotNull();

        // Render through a tracing renderer to verify @Scene.Repeat expansion
        var texts = new java.util.ArrayList<String>();
        var boxCount = new int[]{0};
        dev.everydaythings.graph.ui.scene.surface.SurfaceRenderer tracer =
                new dev.everydaythings.graph.ui.scene.surface.SurfaceRenderer() {
                    @Override public void beginBox(dev.everydaythings.graph.ui.scene.Scene.Direction d,
                                                    java.util.List<String> s) { boxCount[0]++; }
                    @Override public void endBox() {}
                    @Override public void text(String c, java.util.List<String> s) { if (c != null) texts.add(c); }
                    @Override public void formattedText(String c, String f, java.util.List<String> s) {}
                    @Override public void image(String a, dev.everydaythings.graph.item.id.ContentID i,
                                                 dev.everydaythings.graph.item.id.ContentID so,
                                                 String sz, String ft, java.util.List<String> st) {}
                    @Override public void type(String t) {}
                    @Override public void id(String i) {}
                    @Override public void editable(boolean e) {}
                    @Override public void event(String o, String a, String t) {}
                };

        view.root().render(tracer);

        // 1 root + 9 rows + 81 cells = 91 boxes
        assertThat(boxCount[0]).isEqualTo(1 + 9 + 81);
        // 1 status text + 81 cell glyphs
        assertThat(texts).hasSize(1 + 81);
        assertThat(texts.get(0)).contains("remaining");
        // All hidden tiles show ■
        assertThat(texts.subList(1, texts.size())).allMatch("■"::equals);
    }

    // ==================================================================================
    // View Model (CellView / RowView)
    // ==================================================================================

    @Test
    void gridRows_allHidden_atStart() {
        Minesweeper game = Minesweeper.create();
        List<Minesweeper.RowView> rows = game.gridRows();
        assertThat(rows).hasSize(9);
        for (Minesweeper.RowView row : rows) {
            assertThat(row.cells()).hasSize(9);
            for (Minesweeper.CellView cell : row.cells()) {
                assertThat(cell.hidden()).isTrue();
                assertThat(cell.flagged()).isFalse();
                assertThat(cell.mine()).isFalse();
                assertThat(cell.revealed()).isFalse();
                assertThat(cell.display()).isEqualTo("■");
            }
        }
    }

    @Test
    void gridRows_flaggedCell_hasCorrectProperties() {
        Minesweeper game = Minesweeper.create();
        game.flag(0, 0);
        List<Minesweeper.RowView> rows = game.gridRows();
        // (0,0) is the last row (y=0), last in reverse order
        Minesweeper.RowView lastRow = rows.get(rows.size() - 1);
        Minesweeper.CellView flaggedCell = lastRow.cells().get(0);
        assertThat(flaggedCell.flagged()).isTrue();
        assertThat(flaggedCell.hidden()).isFalse();
        assertThat(flaggedCell.display()).isEqualTo("🚩");
    }

    @Test
    void gridRows_revealedCell_hasCorrectProperties() {
        Minesweeper game = Minesweeper.create();
        game.reveal(4, 4);
        List<Minesweeper.RowView> rows = game.gridRows();
        // Find the cell at (4,4) — it's at row index (8-4)=4, col index 4
        Minesweeper.CellView cell = rows.get(rows.size() - 1 - 4).cells().get(4);
        assertThat(cell.revealed()).isTrue();
        assertThat(cell.hidden()).isFalse();
        assertThat(cell.id()).isEqualTo("e5");
    }

    @Test
    void gridRows_cellIds_matchBoardLabels() {
        Minesweeper game = Minesweeper.create();
        List<Minesweeper.RowView> rows = game.gridRows();
        // First row is y=8, first cell is x=0 → "a9"
        assertThat(rows.get(0).cells().get(0).id()).isEqualTo("a9");
        // Last row is y=0, last cell is x=8 → "i1"
        assertThat(rows.get(8).cells().get(8).id()).isEqualTo("i1");
    }

    // ==================================================================================
    // allCells() Flat List
    // ==================================================================================

    @Test
    void allCells_beginner_returns81Items() {
        Minesweeper game = Minesweeper.create();
        List<Minesweeper.CellView> cells = game.allCells();
        assertThat(cells).hasSize(81);
    }

    @Test
    void allCells_firstCellIsTopLeftCorner() {
        Minesweeper game = Minesweeper.create();
        List<Minesweeper.CellView> cells = game.allCells();
        // First cell is top-left: x=0, y=8 → "a9"
        assertThat(cells.get(0).id()).isEqualTo("a9");
    }

    @Test
    void allCells_lastCellIsBottomRightCorner() {
        Minesweeper game = Minesweeper.create();
        List<Minesweeper.CellView> cells = game.allCells();
        // Last cell is bottom-right: x=8, y=0 → "i1"
        assertThat(cells.get(80).id()).isEqualTo("i1");
    }

    // ==================================================================================
    // MineTile.display()
    // ==================================================================================

    @Test
    void mineTile_display() {
        assertThat(MineTile.HIDDEN.display()).isEqualTo("■");
        assertThat(MineTile.EMPTY_0.display()).isEqualTo("·");
        assertThat(MineTile.FLAGGED.display()).isEqualTo("🚩");
        assertThat(MineTile.MINE.display()).isEqualTo("\uD83D\uDCA3");
        assertThat(MineTile.EMPTY_1.display()).isEqualTo("1");
        assertThat(MineTile.EMPTY_5.display()).isEqualTo("5");
        assertThat(MineTile.EMPTY_8.display()).isEqualTo("8");
    }

}
