package dev.everydaythings.graph.game.chess;

import com.github.bhlangonijr.chesslib.Board;
import com.github.bhlangonijr.chesslib.MoveBackup;
import com.github.bhlangonijr.chesslib.Side;
import com.github.bhlangonijr.chesslib.Square;
import com.github.bhlangonijr.chesslib.move.Move;
import dev.everydaythings.graph.dispatch.ActionContext;
import dev.everydaythings.graph.game.BoardState;
import dev.everydaythings.graph.game.GameBoard;
import dev.everydaythings.graph.game.GameVocabulary;
import dev.everydaythings.graph.game.Piece;
import dev.everydaythings.graph.item.Implements;
import dev.everydaythings.graph.item.Item;
import dev.everydaythings.graph.item.Literal;
import dev.everydaythings.graph.item.Param;
import dev.everydaythings.graph.item.Verb;
import dev.everydaythings.graph.item.id.ItemID;
import dev.everydaythings.graph.language.CoreVocabulary;
import dev.everydaythings.graph.language.GrammaticalFeature;
import dev.everydaythings.graph.language.PartOfSpeech;
import dev.everydaythings.graph.language.Sememe;
import dev.everydaythings.graph.runtime.Librarian;
import dev.everydaythings.graph.ui.scene.Scene;
import dev.everydaythings.graph.ui.scene.Scene.Direction;
import dev.everydaythings.graph.ui.scene.View;
import dev.everydaythings.graph.ui.scene.surface.HandleSurface;
import dev.everydaythings.graph.ui.scene.surface.SurfaceSchema;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.experimental.Accessors;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Chess as a proper Item — moves become frames via {@code relate()}.
 *
 * <p>Extends Item directly (no GameComponent hierarchy). Uses chesslib
 * for legal move generation and game-end detection. Each move is persisted
 * as a relation frame on this item.
 */
@Implements(ChessItem.Chess.KEY)
@Scene.Body(shape = "box", fontSize = "2.2cm", color = 0x8B4513)
@Scene.Container(id = "chess-root", direction = Direction.VERTICAL, width = "100%", height = "100%",
        padding = "0.6em", gap = "0.5em", style = "fill")
public class ChessItem extends Item {

    public static class Chess {
        public static final String KEY = "cg.sememe:chess";
        @Item.Seed public static final Sememe SEED = new Sememe(KEY)
                .gloss("en", "the game of chess")
                .word(PartOfSpeech.NOUN, GrammaticalFeature.Lemma.SEED, "en", "chess");
    }

    // ==================================================================================
    // Scene Structure (declarative 2D layout)
    // ==================================================================================

    private static final String SIDE_PANEL_WIDTH = "25%";
    private static final String HANDLE_ROW_HEIGHT = "5.1em";
    private static final String HANDLE_ROW_PADDING = "0 25% 0 0";
    private static final String CAPTURE_ROW_HEIGHT = "3.1em";

    @Scene.Container(id = "black-row", direction = Direction.HORIZONTAL,
            width = "100%", height = HANDLE_ROW_HEIGHT)
    static class BlackRow {

        @Scene.Container(id = "black-handle-lane", direction = Direction.HORIZONTAL,
                style = {"fill", "justify-center"}, align = "center", padding = HANDLE_ROW_PADDING)
        static class HandleLane {
            @Scene.Embed(bind = "value.blackHandle")
            static class Handle {}
        }
    }

    @Scene.Container(id = "table-row", direction = Direction.HORIZONTAL,
            width = "100%", style = {"fill"}, gap = "0.5em")
    static class TableRow {

        @Scene.Container(id = "board-lane", direction = Direction.HORIZONTAL,
                style = {"fill", "justify-center"}, align = "center", height = "100%")
        static class BoardLane {
            @Scene.Embed(bind = "value.chessBoard")
            static class Board {}
        }

        @Scene.Container(id = "side-panel", direction = Direction.VERTICAL,
                gap = "0.5em", padding = "0.45em",
                width = SIDE_PANEL_WIDTH, height = "100%")
        static class SidePanel {

            @Scene.Container(direction = Direction.HORIZONTAL, style = "captured-pieces",
                    gap = "0.125em", padding = "0.25em",
                    width = "100%", height = CAPTURE_ROW_HEIGHT)
            static class BlackCaptured {
                @Scene.Repeat(bind = "value.capturedPieces.byBlack")
                @Scene.Image(bind = "$item", size = "1em")
                static class Piece {}
            }

            @Scene.Container(direction = Direction.VERTICAL, style = {"fill", "justify-center"}, align = "center")
            static class MiddleLane {
                @Scene.If("value.clock")
                @Scene.Container(direction = Direction.HORIZONTAL, width = "100%", height = "100%",
                        style = {"fill", "justify-center"}, align = "center", id = "clock-area")
                static class ClockArea {
                    @Scene.Embed(bind = "value.clock")
                    @Scene.Container(width = "100%", style = {"fill"})
                    static class Clock {}
                }

                @Scene.If("!value.clock")
                @Scene.Container(direction = Direction.HORIZONTAL, width = "100%",
                        style = {"justify-center"}, align = "center")
                static class ClockOffRow {
                    @Scene.Text(content = "Clock Off", style = {"muted"})
                    static class ClockOffLabel {}
                }

                @Scene.If("value.materialAdvantageLabel")
                @Scene.Container(direction = Direction.HORIZONTAL, width = "100%",
                        style = {"justify-center"}, align = "center")
                static class AdvantageRow {
                    @Scene.Text(bind = "value.materialAdvantageLabel", style = {"muted"})
                    static class AdvantageLabel {}
                }
            }

            @Scene.Container(direction = Direction.HORIZONTAL, style = "captured-pieces",
                    gap = "0.125em", padding = "0.25em",
                    width = "100%", height = CAPTURE_ROW_HEIGHT)
            static class WhiteCaptured {
                @Scene.Repeat(bind = "value.capturedPieces.byWhite")
                @Scene.Image(bind = "$item", size = "1em")
                static class Piece {}
            }
        }
    }

    @Scene.Container(id = "white-row", direction = Direction.HORIZONTAL,
            width = "100%", height = HANDLE_ROW_HEIGHT)
    static class WhiteRow {

        @Scene.Container(id = "white-handle-lane", direction = Direction.HORIZONTAL,
                style = {"fill", "justify-center"}, align = "center", padding = HANDLE_ROW_PADDING)
        static class HandleLane {
            @Scene.Embed(bind = "value.whiteHandle")
            static class Handle {}
        }
    }

    // ==================================================================================
    // Game Result
    // ==================================================================================

    public enum GameResult {
        IN_PROGRESS,
        WHITE_WINS_CHECKMATE,
        BLACK_WINS_CHECKMATE,
        WHITE_WINS_RESIGNATION,
        BLACK_WINS_RESIGNATION,
        DRAW_AGREEMENT,
        DRAW_STALEMATE,
        DRAW_INSUFFICIENT_MATERIAL,
        DRAW_THREEFOLD_REPETITION,
        DRAW_FIFTY_MOVE_RULE
    }

    // ==================================================================================
    // Chess engine state
    // ==================================================================================

    private static final GameBoard CHESS_GRID = GameBoard.chessGrid();

    private transient Board board = new Board();
    private transient final List<String> moveHistory = new ArrayList<>();
    private transient GameResult result = GameResult.IN_PROGRESS;

    // Player seats: 0=white, 1=black
    private transient final List<ItemID> players = new ArrayList<>(Arrays.asList(null, null));
    private transient final Map<Integer, String> playerNames = new LinkedHashMap<>();

    // Clock (null = casual play)
    private transient ChessClock clock;

    // Selection state (interactive board)
    private transient String selectedSquare;
    private transient Set<String> legalTargets = Set.of();

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
        return move(san);
    }

    public String move(String san) {
        if (isGameOver()) return "Game is already over";

        String normalized = san.replaceAll("\\s+", "");

        try {
            Move m = new Move(normalized, board.getSideToMove());
            if (!board.legalMoves().contains(m)) {
                return "Illegal move: " + san;
            }

            board.doMove(m);
            moveHistory.add(normalized);

            // Clock integration
            if (clock != null) {
                clock.switchSide();
                if (moveHistory.size() == 1) clock.start();
            }

            // Persist as a relation frame
            relate(GameVocabulary.Move.SEED.iid(), Literal.ofText(normalized));

            updateResult();
            clearSelection();

            return null;
        } catch (Exception e) {
            return "Invalid move: " + san;
        }
    }

    @Verb(value = GameVocabulary.Resign.KEY, doc = "Resign the game")
    public String resign(ActionContext ctx) {
        if (isGameOver()) return "Game is already over";

        Side resigning = board.getSideToMove();
        result = resigning == Side.WHITE
                ? GameResult.BLACK_WINS_RESIGNATION
                : GameResult.WHITE_WINS_RESIGNATION;

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

        for (int i = 0; i < 2; i++) {
            if (players.get(i) == null) {
                players.set(i, caller);
                return "Joined as " + (i == 0 ? "white" : "black");
            }
        }
        return "Game is full";
    }

    @Verb(value = CoreVocabulary.ListVerb.KEY, doc = "List legal moves")
    public List<String> legalMoves() {
        List<Move> moves = board.legalMoves();
        List<String> result = new ArrayList<>(moves.size());
        for (Move m : moves) {
            result.add(m.toString());
        }
        return result;
    }

    @Verb(value = CoreVocabulary.Put.KEY, doc = "Set time control (e.g., '5+3')")
    public String setClock(@Param(value = "spec", doc = "Time spec like '5+3' or 'off'") String spec) {
        if ("off".equalsIgnoreCase(spec)) {
            clock = null;
            return "Clock disabled";
        }
        String[] parts = spec.split("\\+");
        int minutes = Integer.parseInt(parts[0].trim());
        int increment = parts.length > 1 ? Integer.parseInt(parts[1].trim()) : 0;
        clock = ChessClock.create(minutes, increment);
        return "Clock set: " + minutes + " min + " + increment + "s increment";
    }

    // ==================================================================================
    // Selection (interactive piece movement)
    // ==================================================================================

    @Verb(value = GameVocabulary.Select.KEY, doc = "Select a piece to move")
    public String select(@Param(value = "square", doc = "Square to select") String squareId) {
        if (isGameOver()) return "Game is over";

        if (selectedSquare != null && legalTargets.contains(squareId)) {
            String result = move(selectedSquare + squareId);
            clearSelection();
            return result;
        }

        if (squareId.equals(selectedSquare)) {
            clearSelection();
            return null;
        }

        BoardState<ChessPiece> boardState = state();
        var piece = boardState.pieceAt(squareId);
        if (piece.isPresent()) {
            String pieceColor = piece.get().colorCategory();
            String sideColor = sideToMove() == Side.WHITE ? "white" : "black";
            if (pieceColor.equals(sideColor)) {
                selectedSquare = squareId;
                legalTargets = computeLegalTargets(squareId);
                return null;
            }
        }

        clearSelection();
        return null;
    }

    @Verb(value = GameVocabulary.Place.KEY, doc = "Place selected piece on target")
    public String place(@Param(value = "square", doc = "Target square") String squareId) {
        if (selectedSquare == null) return "No piece selected";
        if (!legalTargets.contains(squareId)) return "Not a legal target";

        String result = move(selectedSquare + squareId);
        clearSelection();
        return result;
    }

    public String selectedSquare() { return selectedSquare; }
    public Set<String> legalTargets() { return legalTargets; }

    private void clearSelection() {
        selectedSquare = null;
        legalTargets = Set.of();
    }

    private Set<String> computeLegalTargets(String fromSquare) {
        return board.legalMoves().stream()
                .filter(m -> m.getFrom().toString().equalsIgnoreCase(fromSquare))
                .map(m -> m.getTo().toString().toLowerCase())
                .collect(Collectors.toUnmodifiableSet());
    }

    // ==================================================================================
    // Queries
    // ==================================================================================

    public boolean isGameOver() { return result != GameResult.IN_PROGRESS; }
    public String fen() { return board.getFen(); }
    public int moveCount() { return moveHistory.size(); }
    public List<String> moves() { return List.copyOf(moveHistory); }
    public GameResult result() { return result; }
    public Side sideToMove() { return board.getSideToMove(); }
    public boolean isCheck() { return board.isKingAttacked(); }
    public int fullMoveNumber() { return board.getMoveCounter(); }
    public ChessClock clock() { return clock; }

    public Optional<ItemID> playerAt(int seat) {
        if (seat < 0 || seat >= players.size()) return Optional.empty();
        return Optional.ofNullable(players.get(seat));
    }

    public Optional<String> playerName(int seat) {
        return Optional.ofNullable(playerNames.get(seat));
    }

    // ==================================================================================
    // Board State
    // ==================================================================================

    public BoardState<ChessPiece> state() {
        BoardState<ChessPiece> boardState = new BoardState<>(CHESS_GRID);
        for (int rank = 0; rank < 8; rank++) {
            for (int file = 0; file < 8; file++) {
                Square sq = Square.squareAt(file + rank * 8);
                com.github.bhlangonijr.chesslib.Piece piece = board.getPiece(sq);
                if (piece != com.github.bhlangonijr.chesslib.Piece.NONE) {
                    ChessPiece cp = ChessPiece.from(piece);
                    if (cp != null) {
                        boardState.place(GameBoard.gridLabel(file, rank), cp);
                    }
                }
            }
        }
        return boardState;
    }

    // ==================================================================================
    // View Model (consumed by scene annotations)
    // ==================================================================================

    @Getter @Accessors(fluent = true) @EqualsAndHashCode
    public static class SquareView {
        private final String id;
        private final boolean light;
        private final Piece piece;
        private final boolean selected;
        private final boolean legalTarget;

        public SquareView(String id, boolean light, Piece piece, boolean selected, boolean legalTarget) {
            this.id = id;
            this.light = light;
            this.piece = piece;
            this.selected = selected;
            this.legalTarget = legalTarget;
        }
    }

    @Getter @Accessors(fluent = true) @EqualsAndHashCode
    public static class RankView {
        private final String label;
        private final List<SquareView> squares;

        public RankView(String label, List<SquareView> squares) {
            this.label = label;
            this.squares = squares;
        }
    }

    public List<RankView> ranks() {
        BoardState<ChessPiece> boardState = state();
        List<RankView> result = new ArrayList<>();
        for (int rank = 7; rank >= 0; rank--) {
            List<SquareView> squares = new ArrayList<>();
            for (int file = 0; file < 8; file++) {
                String id = GameBoard.gridLabel(file, rank);
                boolean isLight = (file + rank) % 2 != 0;
                Piece piece = boardState.pieceAt(id).orElse(null);
                boolean isSelected = id.equals(selectedSquare);
                boolean isLegalTarget = legalTargets.contains(id);
                squares.add(new SquareView(id, isLight, piece, isSelected, isLegalTarget));
            }
            result.add(new RankView(String.valueOf(rank + 1), squares));
        }
        return result;
    }

    public List<String> fileLabels() {
        return List.of("a", "b", "c", "d", "e", "f", "g", "h");
    }

    // ==================================================================================
    // Captured Pieces
    // ==================================================================================

    @Getter @Accessors(fluent = true) @EqualsAndHashCode
    public static class CapturedPieces {
        private final List<ChessPiece> byWhite;
        private final List<ChessPiece> byBlack;

        public CapturedPieces(List<ChessPiece> byWhite, List<ChessPiece> byBlack) {
            this.byWhite = byWhite;
            this.byBlack = byBlack;
        }

        public int materialAdvantage() {
            int whiteValue = byWhite.stream().mapToInt(ChessItem::pieceValue).sum();
            int blackValue = byBlack.stream().mapToInt(ChessItem::pieceValue).sum();
            return whiteValue - blackValue;
        }
    }

    public CapturedPieces capturedPieces() {
        List<ChessPiece> byWhite = new ArrayList<>();
        List<ChessPiece> byBlack = new ArrayList<>();
        for (MoveBackup backup : board.getBackup()) {
            com.github.bhlangonijr.chesslib.Piece captured = backup.getCapturedPiece();
            if (captured != com.github.bhlangonijr.chesslib.Piece.NONE) {
                ChessPiece cp = ChessPiece.from(captured);
                if (cp != null) {
                    if ("black".equals(cp.colorCategory())) {
                        byWhite.add(cp);
                    } else {
                        byBlack.add(cp);
                    }
                }
            }
        }
        Comparator<ChessPiece> byValue = Comparator.comparingInt(ChessItem::pieceValue).reversed();
        byWhite.sort(byValue);
        byBlack.sort(byValue);
        return new CapturedPieces(byWhite, byBlack);
    }

    static int pieceValue(ChessPiece p) {
        return switch (p) {
            case WHITE_QUEEN, BLACK_QUEEN -> 9;
            case WHITE_ROOK, BLACK_ROOK -> 5;
            case WHITE_BISHOP, BLACK_BISHOP -> 3;
            case WHITE_KNIGHT, BLACK_KNIGHT -> 3;
            case WHITE_PAWN, BLACK_PAWN -> 1;
            case WHITE_KING, BLACK_KING -> 0;
        };
    }

    public String materialAdvantageLabel() {
        int adv = capturedPieces().materialAdvantage();
        if (adv == 0) return null;
        return adv > 0 ? "White +" + adv : "Black +" + (-adv);
    }

    // ==================================================================================
    // Display Bindings (for scene annotations)
    // ==================================================================================

    public String whiteLabel() { return playerName(0).orElse("White"); }
    public String blackLabel() { return playerName(1).orElse("Black"); }

    private String playerIcon(int seat) {
        return playerAt(seat).isPresent() ? "🧑" : "👤";
    }

    private String currentTurnSubtitle(Side side) {
        if (isGameOver()) return "Final";
        return "Move " + fullMoveNumber();
    }

    public HandleSurface whiteHandle() {
        String subtitle = currentTurnSubtitle(Side.WHITE);
        HandleSurface handle = subtitle != null
                ? HandleSurface.forHeader(playerIcon(0), whiteLabel(), subtitle)
                : HandleSurface.forHeader(playerIcon(0), whiteLabel());
        handle.badge(!isGameOver() && sideToMove() == Side.WHITE ? "▶" : "▷");
        return handle;
    }

    public HandleSurface blackHandle() {
        String subtitle = currentTurnSubtitle(Side.BLACK);
        HandleSurface handle = subtitle != null
                ? HandleSurface.forHeader(playerIcon(1), blackLabel(), subtitle)
                : HandleSurface.forHeader(playerIcon(1), blackLabel());
        handle.badge(!isGameOver() && sideToMove() == Side.BLACK ? "▶" : "▷");
        return handle;
    }

    public ChessBoard chessBoard() {
        return new ChessBoard(this);
    }

    public String sideToMoveLabel() {
        String name = sideToMove() == Side.WHITE ? whiteLabel() : blackLabel();
        return name + " to move";
    }

    public String statusLabel() {
        if (isGameOver()) return formatResult(result);
        String name = sideToMove() == Side.WHITE ? whiteLabel() : blackLabel();
        if (isCheck()) return name + " to move (check)";
        return name + " to move";
    }

    private String formatResult(GameResult result) {
        String w = whiteLabel();
        String b = blackLabel();
        return switch (result) {
            case IN_PROGRESS -> "In progress";
            case WHITE_WINS_CHECKMATE -> w + " wins (checkmate)";
            case BLACK_WINS_CHECKMATE -> b + " wins (checkmate)";
            case WHITE_WINS_RESIGNATION -> w + " wins (resignation)";
            case BLACK_WINS_RESIGNATION -> b + " wins (resignation)";
            case DRAW_AGREEMENT -> "Draw (agreement)";
            case DRAW_STALEMATE -> "Draw (stalemate)";
            case DRAW_INSUFFICIENT_MATERIAL -> "Draw (insufficient material)";
            case DRAW_THREEFOLD_REPETITION -> "Draw (threefold repetition)";
            case DRAW_FIFTY_MOVE_RULE -> "Draw (fifty-move rule)";
        };
    }

    // ==================================================================================
    // View Generation
    // ==================================================================================

    public View view() { return viewBoard(); }

    public View viewBoard() {
        SurfaceSchema<ChessItem> schema = new SurfaceSchema<>() {};
        schema.value(this);
        schema.structureClass(ChessItem.class);
        return View.of(schema);
    }

    // ==================================================================================
    // Board rendering (text fallback)
    // ==================================================================================

    public String renderBoard() {
        BoardState<ChessPiece> boardState = state();
        StringBuilder sb = new StringBuilder();
        sb.append("  a b c d e f g h\n");

        for (int rank = 7; rank >= 0; rank--) {
            sb.append(rank + 1).append(" ");
            for (int file = 0; file < 8; file++) {
                String id = GameBoard.gridLabel(file, rank);
                var piece = boardState.pieceAt(id);
                if (piece.isPresent()) {
                    sb.append(piece.get().symbol());
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

    @Override
    public String displayToken() {
        return statusLabel();
    }

    // ==================================================================================
    // Internal
    // ==================================================================================

    private void updateResult() {
        if (board.isMated()) {
            result = board.getSideToMove() == Side.WHITE
                    ? GameResult.BLACK_WINS_CHECKMATE
                    : GameResult.WHITE_WINS_CHECKMATE;
        } else if (board.isStaleMate()) {
            result = GameResult.DRAW_STALEMATE;
        } else if (board.isInsufficientMaterial()) {
            result = GameResult.DRAW_INSUFFICIENT_MATERIAL;
        } else if (board.isRepetition()) {
            result = GameResult.DRAW_THREEFOLD_REPETITION;
        } else if (board.getHalfMoveCounter() >= 100) {
            result = GameResult.DRAW_FIFTY_MOVE_RULE;
        }
    }
}
