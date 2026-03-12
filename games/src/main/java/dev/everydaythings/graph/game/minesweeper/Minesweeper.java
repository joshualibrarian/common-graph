package dev.everydaythings.graph.game.minesweeper;

import com.upokecenter.cbor.CBORObject;
import dev.everydaythings.graph.game.*;
import dev.everydaythings.graph.item.Item;
import dev.everydaythings.graph.item.component.FrameEntry;
import dev.everydaythings.graph.item.component.Param;
import dev.everydaythings.graph.item.component.Type;
import dev.everydaythings.graph.item.component.Verb;
import dev.everydaythings.graph.language.VerbSememe;
import dev.everydaythings.graph.game.GameVocabulary;
import dev.everydaythings.graph.trust.Signing;
import dev.everydaythings.graph.ui.scene.Scene;
import dev.everydaythings.graph.ui.scene.Scene.Direction;
import dev.everydaythings.graph.ui.scene.View;
import dev.everydaythings.graph.ui.scene.surface.SurfaceSchema;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.experimental.Accessors;

import java.util.*;

/**
 * Minesweeper as a streaming log component.
 *
 * <p>Single-player puzzle on a rectangular grid. Tiles are hidden until
 * revealed; each revealed tile shows the count of adjacent mines.
 * Revealing a mine loses the game. Revealing all non-mine tiles wins.
 *
 * <p>Mines are placed on the first reveal using verifiable randomness
 * from the Dag event, ensuring the first click is always safe and
 * the layout is deterministically replayable.
 *
 * @see MineTile
 */
@Scene.Rule(match = ".tile.hidden", background = "#585B70")
@Scene.Rule(match = ".tile.flagged", background = "#FAB387")
@Scene.Rule(match = ".tile.mine", background = "#F38BA8")
@Scene.Rule(match = ".tile.revealed", background = "#45475A")
@Type(value = Minesweeper.KEY, glyph = "\uD83D\uDCA3")
@Scene.Container(direction = Direction.VERTICAL, padding = "0.5em", gap = "0.25em")
public class Minesweeper extends GameComponent<Minesweeper.Op>
        implements Spatial<MineTile>, Randomized {

    public static final String KEY = "cg:type/minesweeper";
    private static final String CONFIG_SCOPE_ROOT = "/";
    private static final String CONFIG_DIFFICULTY = "difficulty";
    private static final String CONFIG_COLS = "cols";
    private static final String CONFIG_ROWS = "rows";
    private static final String CONFIG_MINES = "mines";


    // ==================================================================================
    // Inline Surface Structure
    // ==================================================================================

    @Scene.Text(bind = "value.statusText", style = "heading")
    static class Status {}

    @Scene.Repeat(bind = "value.gridRows")
    @Scene.Container(direction = Direction.HORIZONTAL, style = "fill", gap = "1px")
    static class Row {

        @Scene.Repeat(bind = "$item.cells")
        @Scene.Container(id = "bind:$item.id",
                direction = Direction.VERTICAL,
                style = {"tile", "fill"}, aspectRatio = "1",
                padding = "2px", align = "center",
                depth = "3mm")
        @Scene.State(when = "$item.hidden", style = "hidden")
        @Scene.State(when = "$item.flagged", style = "flagged")
        @Scene.State(when = "$item.mine", style = "mine")
        @Scene.State(when = "$item.revealed", style = "revealed")
        @Scene.On(event = "click", action = "reveal", target = "$item.id")
        @Scene.On(event = "rightclick", action = "flag", target = "$item.id")
        static class Cell {

            @Scene.Text(bind = "$item.display", fontSize = "80%")
            static class Content {}
        }
    }

    // ==================================================================================
    // Difficulty
    // ==================================================================================

    public enum Difficulty {
        BEGINNER(9, 9, 10),
        INTERMEDIATE(16, 16, 40),
        EXPERT(30, 16, 99);

        private final int cols;
        private final int rows;
        private final int mines;

        Difficulty(int cols, int rows, int mines) {
            this.cols = cols;
            this.rows = rows;
            this.mines = mines;
        }

        public int cols() { return cols; }
        public int rows() { return rows; }
        public int mines() { return mines; }
    }

    // ==================================================================================
    // Operations
    // ==================================================================================

    public sealed interface Op permits RevealOp, FlagOp, ChordOp {}

    /** Reveal a hidden tile. */
    @Getter @Accessors(fluent = true) @EqualsAndHashCode
    public static final class RevealOp implements Op {
        private final int x;
        private final int y;
        public RevealOp(int x, int y) { this.x = x; this.y = y; }
    }

    /** Toggle flag on a hidden tile. */
    @Getter @Accessors(fluent = true) @EqualsAndHashCode
    public static final class FlagOp implements Op {
        private final int x;
        private final int y;
        public FlagOp(int x, int y) { this.x = x; this.y = y; }
    }

    /**
     * Chord: auto-reveal all hidden neighbors of a numbered tile when the
     * adjacent flag count matches the tile's number. Standard minesweeper
     * speedrunning technique (middle-click or both-button click).
     */
    @Getter @Accessors(fluent = true) @EqualsAndHashCode
    public static final class ChordOp implements Op {
        private final int x;
        private final int y;
        public ChordOp(int x, int y) { this.x = x; this.y = y; }
    }

    // ==================================================================================
    // State
    // ==================================================================================

    private enum GameResult { IN_PROGRESS, WON, LOST }

    private static final Map<Difficulty, GameBoard> BOARDS = new EnumMap<>(Difficulty.class);

    private final Difficulty difficulty;
    private final int cols;
    private final int rows;
    private final int mineCount;
    private final int totalNonMines;

    private MineTile[][] visible;
    private boolean[][] mines;
    private int[][] adjacentCounts;
    private boolean minesPlaced = false;
    private GameResult result = GameResult.IN_PROGRESS;
    private int revealedCount = 0;
    private int flagCount = 0;

    private long sequence = 0;

    // ==================================================================================
    // Factory
    // ==================================================================================

    public static Minesweeper create() {
        return create(Difficulty.BEGINNER);
    }

    public static Minesweeper create(Difficulty difficulty) {
        return new Minesweeper(difficulty).withDemoSigner();
    }

    public Minesweeper(Difficulty difficulty) {
        this.difficulty = difficulty;
        this.cols = difficulty.cols;
        this.rows = difficulty.rows;
        this.mineCount = difficulty.mines;
        this.totalNonMines = cols * rows - mineCount;
        this.visible = new MineTile[cols][rows];
        for (MineTile[] row : visible) Arrays.fill(row, MineTile.HIDDEN);
    }

    // ==================================================================================
    // Signer Setup
    // ==================================================================================

    public Minesweeper withSigner(Signing.Signer signer, Signing.Hasher hasher) {
        this.signer = signer;
        this.hasher = hasher;
        return this;
    }

    @Override
    public void initComponent(Item owningItem) {
        publishConfigSettings(owningItem);
    }

    private void publishConfigSettings(Item owningItem) {
        owningItem.componentEntry(this).ifPresent(entry -> {
            entry.putSetting(FrameEntry.ScopedSetting.builder()
                    .scopePath(CONFIG_SCOPE_ROOT)
                    .key(CONFIG_DIFFICULTY)
                    .value(difficulty.name().toLowerCase(Locale.ROOT))
                    .valueType("enum")
                    .build());
            entry.putSetting(FrameEntry.ScopedSetting.builder()
                    .scopePath(CONFIG_SCOPE_ROOT)
                    .key(CONFIG_COLS)
                    .value(Integer.toString(cols))
                    .valueType("int")
                    .build());
            entry.putSetting(FrameEntry.ScopedSetting.builder()
                    .scopePath(CONFIG_SCOPE_ROOT)
                    .key(CONFIG_ROWS)
                    .value(Integer.toString(rows))
                    .valueType("int")
                    .build());
            entry.putSetting(FrameEntry.ScopedSetting.builder()
                    .scopePath(CONFIG_SCOPE_ROOT)
                    .key(CONFIG_MINES)
                    .value(Integer.toString(mineCount))
                    .valueType("int")
                    .build());
        });
    }

    // ==================================================================================
    // Encode/Decode
    // ==================================================================================

    @Override
    protected CBORObject encodeOp(Op op) {
        CBORObject m = CBORObject.NewMap();
        switch (op) {
            case RevealOp r -> {
                m.set(num(0), num(1));
                m.set(num(1), num(r.x()));
                m.set(num(2), num(r.y()));
            }
            case FlagOp f -> {
                m.set(num(0), num(2));
                m.set(num(1), num(f.x()));
                m.set(num(2), num(f.y()));
            }
            case ChordOp c -> {
                m.set(num(0), num(3));
                m.set(num(1), num(c.x()));
                m.set(num(2), num(c.y()));
            }
        }
        return m;
    }

    @Override
    protected Op decodeOp(CBORObject c) {
        int type = c.get(num(0)).AsInt32();
        int x = c.get(num(1)).AsInt32();
        int y = c.get(num(2)).AsInt32();
        return switch (type) {
            case 1 -> new RevealOp(x, y);
            case 2 -> new FlagOp(x, y);
            case 3 -> new ChordOp(x, y);
            default -> throw new IllegalArgumentException("Unknown minesweeper op type: " + type);
        };
    }

    private static CBORObject num(int i) {
        return CBORObject.FromInt32(i);
    }

    // ==================================================================================
    // Fold
    // ==================================================================================

    @Override
    protected void fold(Op op, Event ev) {
        switch (op) {
            case RevealOp reveal -> foldReveal(reveal.x(), reveal.y(), ev);
            case FlagOp flag -> foldFlag(flag.x(), flag.y());
            case ChordOp chord -> foldChord(chord.x(), chord.y());
        }
    }

    private void foldReveal(int x, int y, Event ev) {
        if (!inBounds(x, y) || result != GameResult.IN_PROGRESS) return;
        if (visible[x][y] != MineTile.HIDDEN) return;

        if (!minesPlaced) {
            placeMines(x, y, GameRandom.fromEvent(ev));
        }

        if (mines[x][y]) {
            visible[x][y] = MineTile.MINE;
            result = GameResult.LOST;
            revealAllMines();
        } else {
            floodFill(x, y);
            checkWin();
        }
    }

    private void foldFlag(int x, int y) {
        if (!inBounds(x, y) || result != GameResult.IN_PROGRESS) return;

        if (visible[x][y] == MineTile.HIDDEN) {
            visible[x][y] = MineTile.FLAGGED;
            flagCount++;
        } else if (visible[x][y] == MineTile.FLAGGED) {
            visible[x][y] = MineTile.HIDDEN;
            flagCount--;
        }
    }

    private void foldChord(int x, int y) {
        if (!inBounds(x, y) || result != GameResult.IN_PROGRESS) return;

        MineTile tile = visible[x][y];
        int expected = tile.adjacentCount();
        if (expected <= 0) return;

        if (countAdjacentFlagged(x, y) == expected) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    if (dx == 0 && dy == 0) continue;
                    int nx = x + dx, ny = y + dy;
                    if (inBounds(nx, ny) && visible[nx][ny] == MineTile.HIDDEN) {
                        if (mines[nx][ny]) {
                            visible[nx][ny] = MineTile.MINE;
                            result = GameResult.LOST;
                            revealAllMines();
                            return;
                        }
                        floodFill(nx, ny);
                    }
                }
            }
            checkWin();
        }
    }

    // ==================================================================================
    // Mine Placement
    // ==================================================================================

    private void placeMines(int safeX, int safeY, GameRandom rng) {
        mines = new boolean[cols][rows];
        adjacentCounts = new int[cols][rows];

        List<int[]> candidates = new ArrayList<>();
        for (int x = 0; x < cols; x++) {
            for (int y = 0; y < rows; y++) {
                if (x == safeX && y == safeY) continue;
                candidates.add(new int[]{x, y});
            }
        }

        rng.shuffle(candidates);
        for (int i = 0; i < mineCount; i++) {
            int[] pos = candidates.get(i);
            mines[pos[0]][pos[1]] = true;
        }

        for (int x = 0; x < cols; x++) {
            for (int y = 0; y < rows; y++) {
                if (!mines[x][y]) {
                    adjacentCounts[x][y] = countAdjacentMines(x, y);
                }
            }
        }
        minesPlaced = true;
    }

    // ==================================================================================
    // Flood Fill
    // ==================================================================================

    private void floodFill(int startX, int startY) {
        Deque<int[]> stack = new ArrayDeque<>();
        stack.push(new int[]{startX, startY});

        while (!stack.isEmpty()) {
            int[] pos = stack.pop();
            int x = pos[0], y = pos[1];

            if (!inBounds(x, y)) continue;
            if (visible[x][y] != MineTile.HIDDEN) continue;

            int adj = adjacentCounts[x][y];
            visible[x][y] = MineTile.forCount(adj);
            revealedCount++;

            if (adj == 0) {
                for (int dx = -1; dx <= 1; dx++) {
                    for (int dy = -1; dy <= 1; dy++) {
                        if (dx == 0 && dy == 0) continue;
                        stack.push(new int[]{x + dx, y + dy});
                    }
                }
            }
        }
    }

    // ==================================================================================
    // Helpers
    // ==================================================================================

    private boolean inBounds(int x, int y) {
        return x >= 0 && x < cols && y >= 0 && y < rows;
    }

    private int countAdjacentMines(int x, int y) {
        int count = 0;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                if (dx == 0 && dy == 0) continue;
                int nx = x + dx, ny = y + dy;
                if (inBounds(nx, ny) && mines[nx][ny]) count++;
            }
        }
        return count;
    }

    private int countAdjacentFlagged(int x, int y) {
        int count = 0;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                if (dx == 0 && dy == 0) continue;
                int nx = x + dx, ny = y + dy;
                if (inBounds(nx, ny) && visible[nx][ny] == MineTile.FLAGGED) count++;
            }
        }
        return count;
    }

    private void revealAllMines() {
        for (int x = 0; x < cols; x++) {
            for (int y = 0; y < rows; y++) {
                if (mines[x][y] && visible[x][y] != MineTile.FLAGGED) {
                    visible[x][y] = MineTile.MINE;
                }
            }
        }
    }

    private void checkWin() {
        if (revealedCount == totalNonMines) {
            result = GameResult.WON;
        }
    }

    // ==================================================================================
    // Game Actions (Verbs)
    // ==================================================================================

    @Verb(value = GameVocabulary.Reveal.KEY, doc = "Reveal a tile")
    public boolean reveal(@Param(value = "cell", doc = "Cell label (e.g., a1)") String label) {
        int[] pos = GameBoard.parseGridLabel(label);
        return reveal(pos[0], pos[1]);
    }

    public boolean reveal(int x, int y) {
        if (result != GameResult.IN_PROGRESS) return false;
        if (!inBounds(x, y)) return false;
        if (visible[x][y] != MineTile.HIDDEN) {
            // Clicking a revealed numbered tile → chord (standard minesweeper UX)
            if (visible[x][y].adjacentCount() > 0) {
                return chord(x, y);
            }
            return false;
        }
        requireSigner();
        append(new RevealOp(x, y), ++sequence, signer, hasher);
        return true;
    }

    @Verb(value = GameVocabulary.Flag.KEY, doc = "Toggle flag on a tile")
    public boolean flag(@Param(value = "cell", doc = "Cell label (e.g., a1)") String label) {
        int[] pos = GameBoard.parseGridLabel(label);
        return flag(pos[0], pos[1]);
    }

    public boolean flag(int x, int y) {
        if (result != GameResult.IN_PROGRESS) return false;
        if (!inBounds(x, y)) return false;
        MineTile tile = visible[x][y];
        if (tile != MineTile.HIDDEN && tile != MineTile.FLAGGED) return false;
        requireSigner();
        append(new FlagOp(x, y), ++sequence, signer, hasher);
        return true;
    }

    @Verb(value = GameVocabulary.Chord.KEY, doc = "Auto-reveal neighbors when flags match the number")
    public boolean chord(@Param(value = "cell", doc = "Cell label (e.g., a1)") String label) {
        int[] pos = GameBoard.parseGridLabel(label);
        return chord(pos[0], pos[1]);
    }

    public boolean chord(int x, int y) {
        if (result != GameResult.IN_PROGRESS) return false;
        if (!inBounds(x, y)) return false;
        int expected = visible[x][y].adjacentCount();
        if (expected <= 0) return false;
        if (countAdjacentFlagged(x, y) != expected) return false;
        requireSigner();
        append(new ChordOp(x, y), ++sequence, signer, hasher);
        return true;
    }

    private void requireSigner() {
        if (signer == null || hasher == null) {
            throw new IllegalStateException("No signer configured — call withDemoSigner() first");
        }
    }

    // ==================================================================================
    // View Model
    // ==================================================================================

    /** A positioned tile — delegates state booleans to {@link MineTile}. */
    public static class CellView {
        private final String id;
        private final MineTile tile;

        CellView(String id, MineTile tile) { this.id = id; this.tile = tile; }

        public String id()        { return id; }
        public MineTile tile()    { return tile; }
        public boolean hidden()   { return tile.hidden(); }
        public boolean flagged()  { return tile.flagged(); }
        public boolean mine()     { return tile.mine(); }
        public boolean revealed() { return tile.revealed(); }
        public String display()   { return tile.display(); }
    }

    /** A row of cells for {@code @Scene.Repeat}. */
    public static class RowView {
        private final List<CellView> cells;

        public RowView(List<CellView> cells) {
            this.cells = cells;
        }

        public List<CellView> cells() { return cells; }
    }

    /** Flat list of all cells, row by row top-to-bottom then left-to-right. For grid repeat. */
    public List<CellView> allCells() {
        List<CellView> cells = new ArrayList<>();
        for (int y = this.rows - 1; y >= 0; y--) {
            for (int x = 0; x < cols; x++) {
                cells.add(new CellView(GameBoard.gridLabel(x, y), visible[x][y]));
            }
        }
        return cells;
    }

    /** Grid rows top-to-bottom, each with cells left-to-right. For {@code @Scene.Repeat}. */
    public List<RowView> gridRows() {
        List<RowView> result = new ArrayList<>();
        for (int y = this.rows - 1; y >= 0; y--) {
            List<CellView> cells = new ArrayList<>();
            for (int x = 0; x < cols; x++) {
                cells.add(new CellView(GameBoard.gridLabel(x, y), visible[x][y]));
            }
            result.add(new RowView(cells));
        }
        return result;
    }

    @Verb(value = VerbSememe.Describe.KEY, doc = "Describe game status")
    public String statusText() {
        if (isWon()) return "You win! All mines cleared.";
        if (isLost()) return "Game over! Hit a mine.";
        return "Mines: " + remainingMines() + " remaining | " + revealedCount + "/" + totalNonMines + " cleared";
    }

    // ==================================================================================
    // Queries
    // ==================================================================================

    public Difficulty difficulty() { return difficulty; }
    public int cols() { return cols; }
    public int rows() { return rows; }
    public int mineCount() { return mineCount; }
    public int flagCount() { return flagCount; }
    public int remainingMines() { return mineCount - flagCount; }
    public int revealedCount() { return revealedCount; }
    public boolean isWon() { return result == GameResult.WON; }
    public boolean isLost() { return result == GameResult.LOST; }

    public MineTile tileAt(int x, int y) {
        if (!inBounds(x, y)) throw new IndexOutOfBoundsException("(" + x + "," + y + ")");
        return visible[x][y];
    }

    /** For testing: check if a mine is at (x,y). Only valid after mines are placed. */
    boolean hasMineAt(int x, int y) {
        return minesPlaced && inBounds(x, y) && mines[x][y];
    }

    /** For testing: check if mines have been placed yet. */
    boolean minesPlaced() {
        return minesPlaced;
    }

    // ==================================================================================
    // GameComponent Abstract Methods
    // ==================================================================================

    @Override public int minPlayers() { return 1; }
    @Override public int maxPlayers() { return 1; }

    @Override
    public Set<Integer> activePlayers() {
        return isGameOver() ? Set.of() : Set.of(0);
    }

    @Override
    public boolean isGameOver() {
        return result != GameResult.IN_PROGRESS;
    }

    @Override
    public Optional<Integer> winner() {
        return result == GameResult.WON ? Optional.of(0) : Optional.empty();
    }

    // ==================================================================================
    // Spatial<MineTile>
    // ==================================================================================

    @Override
    public GameBoard board() {
        return BOARDS.computeIfAbsent(difficulty,
                d -> GameBoard.rectangularGrid(d.cols(), d.rows()));
    }

    @Override
    public BoardState<MineTile> state() {
        BoardState<MineTile> bs = new BoardState<>(board());
        for (int x = 0; x < cols; x++) {
            for (int y = 0; y < rows; y++) {
                bs.place(GameBoard.gridLabel(x, y), visible[x][y]);
            }
        }
        return bs;
    }

    @Override
    public View viewBoard() {
        SurfaceSchema<Minesweeper> schema = new SurfaceSchema<>() {};
        schema.value(this);
        schema.structureClass(Minesweeper.class);
        return View.of(schema);
    }

    // ==================================================================================
    // Text Rendering
    // ==================================================================================

    public String renderBoard() {
        StringBuilder sb = new StringBuilder();

        // Column headers
        sb.append("   ");
        for (int x = 0; x < cols; x++) {
            sb.append(String.format("%2d", x));
        }
        sb.append("\n");

        // Grid rows (top to bottom, y descending for natural orientation)
        for (int y = rows - 1; y >= 0; y--) {
            sb.append(String.format("%2d ", y));
            for (int x = 0; x < cols; x++) {
                sb.append(visible[x][y].symbol());
                if (x < cols - 1) sb.append(' ');
            }
            sb.append("\n");
        }

        // Status line
        if (isWon()) {
            sb.append("Victory! All mines cleared.\n");
        } else if (isLost()) {
            sb.append("BOOM! Game over.\n");
        } else {
            sb.append("Mines: ").append(remainingMines()).append(" remaining\n");
        }

        return sb.toString();
    }

    @Override
    public String toString() {
        return renderBoard();
    }
}
