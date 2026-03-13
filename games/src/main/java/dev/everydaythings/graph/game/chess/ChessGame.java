package dev.everydaythings.graph.game.chess;

import com.github.bhlangonijr.chesslib.*;
import com.github.bhlangonijr.chesslib.move.Move;
import com.github.bhlangonijr.chesslib.MoveBackup;
import com.upokecenter.cbor.CBORObject;
import dev.everydaythings.graph.game.BoardState;
import dev.everydaythings.graph.game.GameBoard;
import dev.everydaythings.graph.game.GameComponent;
import dev.everydaythings.graph.game.GameMode;
import dev.everydaythings.graph.game.Piece;
import dev.everydaythings.graph.game.Spatial;
import dev.everydaythings.graph.item.action.ActionContext;
import dev.everydaythings.graph.item.component.InspectEntry;
import dev.everydaythings.graph.item.component.Inspectable;
import dev.everydaythings.graph.item.component.Param;
import dev.everydaythings.graph.item.component.Type;
import dev.everydaythings.graph.item.component.Verb;
import dev.everydaythings.graph.item.id.ItemID;
import dev.everydaythings.graph.language.VerbSememe;
import dev.everydaythings.graph.game.GameVocabulary;
import dev.everydaythings.graph.trust.Signing;
import dev.everydaythings.graph.ui.scene.Scene;
import dev.everydaythings.graph.ui.scene.Scene.Direction;
import dev.everydaythings.graph.ui.scene.surface.HandleSurface;
import dev.everydaythings.graph.ui.scene.surface.SurfaceSchema;
import dev.everydaythings.graph.ui.scene.surface.primitive.ContainerSurface;
import dev.everydaythings.graph.ui.scene.surface.primitive.TextSurface;
import dev.everydaythings.graph.ui.scene.View;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.experimental.Accessors;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Chess game as a streaming log component.
 *
 * <p>Wraps the chesslib library for legal move generation, validation,
 * and game state detection. The game history is stored as a Dag (stream
 * of operations), making games:
 * <ul>
 *   <li>Syncable between players</li>
 *   <li>Replayable (step through history)</li>
 *   <li>Verifiable (all moves signed)</li>
 *   <li>Forkable (analysis lines)</li>
 * </ul>
 *
 * @see <a href="https://github.com/bhlangonijr/chesslib">chesslib</a>
 */
@Type(value = ChessGame.KEY, glyph = "♟️")
@Scene.Body(shape = "box", fontSize = "2.2cm", color = 0x8B4513)
@Scene.Container(id = "chess-root", direction = Direction.VERTICAL, width = "100%", height = "100%",
        padding = "0.6em", gap = "0.5em", style = "fill")
public class ChessGame extends GameComponent<ChessGame.Op> implements Spatial<ChessPiece>, Inspectable {

    public static final String KEY = "cg:type/chess";
    private static final String SIDE_PANEL_WIDTH = "25%";
    private static final String HANDLE_ROW_HEIGHT = "5.1em";
    private static final String HANDLE_ROW_PADDING = "0 25% 0 0";
    private static final String CAPTURE_ROW_HEIGHT = "3.1em";


    // ==================================================================================
    // Scene Structure (declarative 2D layout)
    // ==================================================================================

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

        // Right column: captured pieces + clock lane
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
    // Operations
    // ==================================================================================

    public sealed interface Op permits MoveOp, ResignOp, DrawOfferOp, DrawAcceptOp, DrawDeclineOp {}

    /**
     * A chess move in SAN (Standard Algebraic Notation).
     */
    @Getter @Accessors(fluent = true) @EqualsAndHashCode
    public static final class MoveOp implements Op {
        private final String san;
        public MoveOp(String san) { this.san = san; }
        public static MoveOp from(Move move, Board board) {
            return new MoveOp(move.toString());
        }
    }

    @Getter @Accessors(fluent = true) @EqualsAndHashCode
    public static final class ResignOp implements Op {
        private final Side side;
        public ResignOp(Side side) { this.side = side; }
    }

    @Getter @Accessors(fluent = true) @EqualsAndHashCode
    public static final class DrawOfferOp implements Op {
        private final Side side;
        public DrawOfferOp(Side side) { this.side = side; }
    }

    @Getter @Accessors(fluent = true) @EqualsAndHashCode
    public static final class DrawAcceptOp implements Op {
        private final Side side;
        public DrawAcceptOp(Side side) { this.side = side; }
    }

    @Getter @Accessors(fluent = true) @EqualsAndHashCode
    public static final class DrawDeclineOp implements Op {
        private final Side side;
        public DrawDeclineOp(Side side) { this.side = side; }
    }

    // ==================================================================================
    // Game State (materialized from moves via chesslib)
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

    // Cached board topology (same for all chess games)
    private static final GameBoard CHESS_BOARD = GameBoard.chessGrid();

    // The chesslib board - handles all chess logic
    // Named 'chessBoard' to avoid conflict with the Board interface from game framework
    private final com.github.bhlangonijr.chesslib.Board chessBoard = new com.github.bhlangonijr.chesslib.Board();

    // Move history (SAN notation)
    private final List<String> moveHistory = new ArrayList<>();

    // Game result
    private GameResult result = GameResult.IN_PROGRESS;

    // Draw offer pending
    private Side drawOfferFrom = null;

    // Sequence counter for Dag events
    private long sequence = 0;

    // Chess clock (null = casual play, no time control)
    private transient ChessClock clock;

    // Selection state (transient — not persisted)
    private transient String selectedSquare;
    private transient Set<String> legalTargets = Set.of();

    // ==================================================================================
    // Factory
    // ==================================================================================

    /**
     * Create a new chess game from the starting position.
     *
     * <p>By default, uses a demo signer for interactive play.
     * For production use, call withSigner() to set a real identity.
     */
    public static ChessGame create() {
        return new ChessGame().withDemoSigner();
    }

    /**
     * Create a new chess game with parameters from verb dispatch.
     *
     * <p>Supports:
     * <ul>
     *   <li>{@code players} — list of player Items or strings (max 2: white, black)</li>
     *   <li>{@code source} — FEN string or PGN movetext to load from</li>
     *   <li>{@code mode} — GameMode or string ("archive", "analysis", "authenticated")</li>
     *   <li>{@code name} — game title (handled by caller, not here)</li>
     * </ul>
     *
     * <p>When loading from a PGN source, the mode defaults to ARCHIVE (the game
     * is a historical record). Players can be Person items, Signer items, or
     * plain strings (names only, no identity).
     */
    @SuppressWarnings("unchecked")
    public static ChessGame create(Map<String, Object> params) {
        ChessGame game = new ChessGame().withDemoSigner();

        // Set mode (explicit or inferred)
        Object modeParam = params.get("mode");
        if (modeParam instanceof GameMode gm) {
            game.mode = gm;
        } else if (modeParam instanceof String s) {
            game.mode = GameMode.valueOf(s.toUpperCase());
        }

        List<?> players = (List<?>) params.get("players");
        if (players != null) {
            for (int i = 0; i < Math.min(players.size(), 2); i++) {
                Object player = players.get(i);
                if (player instanceof dev.everydaythings.graph.item.Item item) {
                    game.joinAs(i, item.iid(), item.displayToken());
                } else if (player instanceof ItemID id) {
                    game.joinAs(i, id, null);
                } else if (player instanceof String name) {
                    // Name-only player — no ItemID required (Person, historical, etc.)
                    while (game.playerSeats.size() <= i) game.playerSeats.add(null);
                    game.playerNames.put(i, name);
                }
            }
        }

        String source = (String) params.get("source");
        if (source != null) {
            game.loadFromSource(source);
            // Default to ARCHIVE when loading from source (unless explicitly overridden)
            if (modeParam == null) {
                game.mode = GameMode.ARCHIVE;
            }
        }

        return game;
    }

    /**
     * Create a new chess game from a FEN position.
     *
     * <p>By default, uses a demo signer for interactive play.
     */
    public static ChessGame fromFen(String fen) {
        ChessGame chess = new ChessGame().withDemoSigner();
        chess.chessBoard.loadFromFen(fen);
        return chess;
    }

    /**
     * Load game state from a source string.
     *
     * <p>Interprets the source as FEN if it matches FEN structure
     * (contains '/' and has 4-6 space-separated parts), otherwise
     * treats it as PGN movetext (e.g., "1. e4 e5 2. Nf3 Nc6").
     */
    public void loadFromSource(String source) {
        if (source == null || source.isBlank()) return;

        String trimmed = source.trim();
        if (looksLikeFen(trimmed)) {
            chessBoard.loadFromFen(trimmed);
        } else {
            loadPgnMoves(trimmed);
        }
    }

    private static boolean looksLikeFen(String s) {
        // FEN has slashes for rank separators and 4-6 space-separated fields
        return s.contains("/") && s.split("\\s+").length >= 4;
    }

    private void loadPgnMoves(String pgn) {
        // Strip move numbers and parse SAN moves
        String[] tokens = pgn.replaceAll("\\d+\\.", "").trim().split("\\s+");
        for (String token : tokens) {
            if (token.isBlank()) continue;
            // Skip result markers
            if (token.equals("1-0") || token.equals("0-1") || token.equals("1/2-1/2") || token.equals("*")) continue;
            if (!chessBoard.doMove(token)) break;
            moveHistory.add(token);
        }
    }

    // ==================================================================================
    // Encode/Decode
    // ==================================================================================

    @Override
    protected CBORObject encodeOp(Op op) {
        CBORObject m = CBORObject.NewMap();
        switch (op) {
            case MoveOp move -> {
                m.set(num(0), num(1));  // type = 1 (move)
                m.set(num(1), CBORObject.FromString(move.san()));
            }
            case ResignOp r -> {
                m.set(num(0), num(2));  // type = 2
                m.set(num(1), CBORObject.FromString(r.side().name()));
            }
            case DrawOfferOp o -> {
                m.set(num(0), num(3));
                m.set(num(1), CBORObject.FromString(o.side().name()));
            }
            case DrawAcceptOp a -> {
                m.set(num(0), num(4));
                m.set(num(1), CBORObject.FromString(a.side().name()));
            }
            case DrawDeclineOp d -> {
                m.set(num(0), num(5));
                m.set(num(1), CBORObject.FromString(d.side().name()));
            }
        }
        return m;
    }

    @Override
    protected Op decodeOp(CBORObject c) {
        int type = c.get(num(0)).AsInt32();
        return switch (type) {
            case 1 -> new MoveOp(c.get(num(1)).AsString());
            case 2 -> new ResignOp(Side.valueOf(c.get(num(1)).AsString()));
            case 3 -> new DrawOfferOp(Side.valueOf(c.get(num(1)).AsString()));
            case 4 -> new DrawAcceptOp(Side.valueOf(c.get(num(1)).AsString()));
            case 5 -> new DrawDeclineOp(Side.valueOf(c.get(num(1)).AsString()));
            default -> throw new IllegalArgumentException("Unknown chess op type: " + type);
        };
    }

    private static CBORObject num(int i) {
        return CBORObject.FromInt32(i);
    }

    // ==================================================================================
    // Fold (apply operations to state)
    // ==================================================================================

    @Override
    protected void fold(Op op, Event ev) {
        switch (op) {
            case MoveOp move -> {
                try {
                    Move m = new Move(move.san(), chessBoard.getSideToMove());
                    if (chessBoard.doMove(m)) {
                        moveHistory.add(move.san());
                        drawOfferFrom = null;  // Move declines pending draw offer

                        // Check game end conditions
                        if (chessBoard.isMated()) {
                            result = chessBoard.getSideToMove() == Side.WHITE
                                    ? GameResult.BLACK_WINS_CHECKMATE
                                    : GameResult.WHITE_WINS_CHECKMATE;
                        } else if (chessBoard.isStaleMate()) {
                            result = GameResult.DRAW_STALEMATE;
                        } else if (chessBoard.isInsufficientMaterial()) {
                            result = GameResult.DRAW_INSUFFICIENT_MATERIAL;
                        } else if (chessBoard.isRepetition()) {
                            result = GameResult.DRAW_THREEFOLD_REPETITION;
                        } else if (chessBoard.getHalfMoveCounter() >= 100) {
                            result = GameResult.DRAW_FIFTY_MOVE_RULE;
                        }
                    }
                } catch (Exception e) {
                    // Invalid move - log but don't crash
                    // In production, this would reject the event
                }
            }
            case ResignOp r -> {
                result = r.side() == Side.WHITE
                        ? GameResult.BLACK_WINS_RESIGNATION
                        : GameResult.WHITE_WINS_RESIGNATION;
            }
            case DrawOfferOp o -> {
                drawOfferFrom = o.side();
            }
            case DrawAcceptOp a -> {
                if (drawOfferFrom != null) {
                    result = GameResult.DRAW_AGREEMENT;
                }
            }
            case DrawDeclineOp d -> {
                drawOfferFrom = null;
            }
        }
    }

    // ==================================================================================
    // Signer/Hasher Setup (for interactive play)
    // ==================================================================================

    /**
     * Set the signer and hasher for making moves.
     *
     * <p>Required before calling makeMove(). For demo purposes, use withDemoSigner().
     */
    public ChessGame withSigner(Signing.Signer signer, Signing.Hasher hasher) {
        this.signer = signer;
        this.hasher = hasher;
        return this;
    }


    // ==================================================================================
    // Inspect Entries
    // ==================================================================================

    @Override
    public List<InspectEntry> inspectEntries() {
        List<InspectEntry> entries = new ArrayList<>();
        for (int i = 0; i < moveHistory.size(); i++) {
            String san = moveHistory.get(i);
            int moveNum = (i / 2) + 1;
            String label = moveNum + (i % 2 == 0 ? ". " : "... ") + san;
            entries.add(new InspectEntry(String.valueOf(i), label, "♟️", san));
        }
        return entries;
    }

    // ==================================================================================
    // Game Actions
    // ==================================================================================

    /**
     * Check if a move is legal (without making it).
     *
     * @param san Standard Algebraic Notation or UCI (e.g., "e4", "e2e4")
     * @return true if the move is legal
     */
    public boolean isLegalMove(String san) {
        if (isGameOver()) return false;

        try {
            Move m = new Move(san, chessBoard.getSideToMove());
            return chessBoard.legalMoves().contains(m);
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Make a move via verb dispatch.
     *
     * <p>In AUTHENTICATED mode, the caller must be seated and it must be their turn.
     * In ANALYSIS mode, anyone can move freely. In ARCHIVE mode, no moves allowed.
     *
     * @param ctx ActionContext with caller identity
     * @param san Standard Algebraic Notation or UCI (e.g., "e4", "e2e4")
     * @return null on success, error message on failure
     */
    @Verb(value = GameVocabulary.Move.KEY, doc = "Make a chess move in SAN notation")
    public String move(ActionContext ctx,
                       @Param(value = "san", doc = "Move in algebraic notation") String san) {
        if (mode == GameMode.ARCHIVE) return "Game is in archive mode — fork to analyze";
        authorizedSeat(ctx); // no-op in ANALYSIS mode, enforced in AUTHENTICATED
        return doMove(san, resolveSigner(ctx), resolveHasher());
    }

    /**
     * Make a move — convenience for direct calls and tests.
     *
     * @param san Standard Algebraic Notation or UCI (e.g., "e4", "e2e4")
     * @return null on success, error message on failure
     */
    public String move(String san) {
        if (mode == GameMode.ARCHIVE) return "Game is in archive mode — fork to analyze";
        return doMove(san, signer, hasher);
    }

    private String doMove(String san, Signing.Signer s, Signing.Hasher h) {
        if (isGameOver()) return "Game is already over";
        if (s == null || h == null) {
            throw new IllegalStateException("No signer configured - call withSigner() or withDemoSigner() first");
        }

        // Normalize: strip whitespace so "d7 d6" → "d7d6" (UCI format)
        String normalized = san.replaceAll("\\s+", "");

        try {
            Move m = new Move(normalized, chessBoard.getSideToMove());
            if (chessBoard.legalMoves().contains(m)) {
                // Move is legal - append to Dag
                MoveOp op = new MoveOp(normalized);
                append(op, ++sequence, s, h);

                // Clock integration: switch sides, start on first move
                if (clock != null) {
                    clock.switchSide();
                    if (moveHistory.size() == 1) clock.start();
                }

                return null;  // success, no message needed
            }
            return "Illegal move: " + san;
        } catch (Exception e) {
            return "Invalid move: " + san;
        }
    }

    /**
     * Resign via verb dispatch.
     *
     * <p>In AUTHENTICATED mode, side is derived from the caller's seat.
     * In ANALYSIS mode, side must be specified (or defaults to side-to-move).
     */
    @Verb(value = GameVocabulary.Resign.KEY, doc = "Resign the game")
    public void resign(ActionContext ctx,
                       @Param(value = "side", doc = "Side resigning (WHITE or BLACK)", required = false) Side side) {
        if (mode == GameMode.ARCHIVE) return;
        int seat = requireSeat(ctx);
        if (seat >= 0) {
            side = seat == 0 ? Side.WHITE : Side.BLACK;
        } else if (side == null) {
            side = sideToMove(); // default to current side in analysis mode
        }
        doResign(side, resolveSigner(ctx), resolveHasher());
    }

    /** Convenience for direct calls and tests. */
    public void resign(Side side) {
        doResign(side, signer, hasher);
    }

    private void doResign(Side side, Signing.Signer s, Signing.Hasher h) {
        if (isGameOver()) return;
        if (s == null || h == null) {
            throw new IllegalStateException("No signer configured");
        }
        append(new ResignOp(side), ++sequence, s, h);
    }

    /**
     * Offer a draw via verb dispatch.
     */
    @Verb(value = GameVocabulary.Offer.KEY, doc = "Offer a draw")
    public void offerDraw(ActionContext ctx,
                          @Param(value = "side", doc = "Side offering", required = false) Side side) {
        if (mode == GameMode.ARCHIVE) return;
        int seat = requireSeat(ctx);
        if (seat >= 0) {
            side = seat == 0 ? Side.WHITE : Side.BLACK;
        } else if (side == null) {
            side = sideToMove();
        }
        doOfferDraw(side, resolveSigner(ctx), resolveHasher());
    }

    /** Convenience for direct calls and tests. */
    public void offerDraw(Side side) {
        doOfferDraw(side, signer, hasher);
    }

    private void doOfferDraw(Side side, Signing.Signer s, Signing.Hasher h) {
        if (isGameOver()) return;
        if (s == null || h == null) {
            throw new IllegalStateException("No signer configured");
        }
        append(new DrawOfferOp(side), ++sequence, s, h);
    }

    /**
     * Accept a draw offer via verb dispatch.
     */
    @Verb(value = GameVocabulary.Accept.KEY, doc = "Accept a draw offer")
    public void acceptDraw(ActionContext ctx,
                           @Param(value = "side", doc = "Side accepting", required = false) Side side) {
        if (mode == GameMode.ARCHIVE) return;
        int seat = requireSeat(ctx);
        if (seat >= 0) {
            side = seat == 0 ? Side.WHITE : Side.BLACK;
        } else if (side == null) {
            side = sideToMove();
        }
        doAcceptDraw(side, resolveSigner(ctx), resolveHasher());
    }

    /** Convenience for direct calls and tests. */
    public void acceptDraw(Side side) {
        doAcceptDraw(side, signer, hasher);
    }

    private void doAcceptDraw(Side side, Signing.Signer s, Signing.Hasher h) {
        if (isGameOver() || drawOfferFrom == null) return;
        if (s == null || h == null) {
            throw new IllegalStateException("No signer configured");
        }
        append(new DrawAcceptOp(side), ++sequence, s, h);
    }

    /**
     * Decline a draw offer via verb dispatch.
     */
    @Verb(value = GameVocabulary.Decline.KEY, doc = "Decline a draw offer")
    public void declineDraw(ActionContext ctx,
                            @Param(value = "side", doc = "Side declining", required = false) Side side) {
        if (mode == GameMode.ARCHIVE) return;
        int seat = requireSeat(ctx);
        if (seat >= 0) {
            side = seat == 0 ? Side.WHITE : Side.BLACK;
        } else if (side == null) {
            side = sideToMove();
        }
        doDeclineDraw(side, resolveSigner(ctx), resolveHasher());
    }

    /** Convenience for direct calls and tests. */
    public void declineDraw(Side side) {
        doDeclineDraw(side, signer, hasher);
    }

    private void doDeclineDraw(Side side, Signing.Signer s, Signing.Hasher h) {
        if (drawOfferFrom == null) return;
        if (s == null || h == null) {
            throw new IllegalStateException("No signer configured");
        }
        append(new DrawDeclineOp(side), ++sequence, s, h);
    }

    /**
     * Get all legal moves in the current position.
     */
    @Verb(value = VerbSememe.ListVerb.KEY, doc = "List legal moves")
    public List<String> legalMoves() {
        List<Move> moves = chessBoard.legalMoves();
        List<String> result = new ArrayList<>(moves.size());
        for (Move m : moves) {
            result.add(m.toString());
        }
        return result;
    }

    // ==================================================================================
    // Selection (interactive piece movement)
    // ==================================================================================

    /**
     * Select a piece or place a selected piece.
     *
     * <p>Unified click handler:
     * <ul>
     *   <li>If a piece is selected and the target is a legal move → execute the move</li>
     *   <li>If the target has a piece of the current side → select it</li>
     *   <li>If the same piece is clicked → deselect</li>
     *   <li>Otherwise → clear selection</li>
     * </ul>
     *
     * @param squareId The square clicked (e.g., "e2")
     * @return status message or null on success
     */
    @Verb(value = GameVocabulary.Select.KEY, doc = "Select a piece to move")
    public String select(@Param(value = "square", doc = "Square to select") String squareId) {
        if (isGameOver()) return "Game is over";

        // If a piece is selected and clicked square is a legal target → move
        if (selectedSquare != null && legalTargets.contains(squareId)) {
            String result = move(selectedSquare + squareId);
            clearSelection();
            return result;
        }

        // If clicking the already-selected square → deselect
        if (squareId.equals(selectedSquare)) {
            clearSelection();
            return null;
        }

        // Try to select a piece on this square
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

        // Clicked empty square or opponent piece without selection → clear
        clearSelection();
        return null;
    }

    /**
     * Place the selected piece on a target square.
     *
     * <p>Only valid when a piece is selected and the target is a legal move.
     *
     * @param squareId The target square
     * @return status message or null on success
     */
    @Verb(value = GameVocabulary.Place.KEY, doc = "Place selected piece on target")
    public String place(@Param(value = "square", doc = "Target square") String squareId) {
        if (selectedSquare == null) return "No piece selected";
        if (!legalTargets.contains(squareId)) return "Not a legal target";

        String result = move(selectedSquare + squareId);
        clearSelection();
        return result;
    }

    /**
     * Get the currently selected square, or null if nothing selected.
     */
    public String selectedSquare() {
        return selectedSquare;
    }

    /**
     * Get the set of legal target squares for the selected piece.
     */
    public Set<String> legalTargets() {
        return legalTargets;
    }

    private void clearSelection() {
        selectedSquare = null;
        legalTargets = Set.of();
    }

    private Set<String> computeLegalTargets(String fromSquare) {
        List<Move> moves = chessBoard.legalMoves();
        return moves.stream()
                .filter(m -> m.getFrom().toString().equalsIgnoreCase(fromSquare))
                .map(m -> m.getTo().toString().toLowerCase())
                .collect(Collectors.toUnmodifiableSet());
    }

    // ==================================================================================
    // View Model (consumed by all renderers)
    // ==================================================================================

    /**
     * View data for a single square, carrying selection state and piece info.
     */
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

    /**
     * View data for a rank (row of squares).
     */
    @Getter @Accessors(fluent = true) @EqualsAndHashCode
    public static class RankView {
        private final String label;
        private final List<SquareView> squares;

        public RankView(String label, List<SquareView> squares) {
            this.label = label;
            this.squares = squares;
        }
    }

    /**
     * Build structured view data for the entire board.
     *
     * <p>Includes selection state (selected square, legal targets) for
     * interactive rendering. Used by all renderers (2D, 3D, text).
     *
     * @return ranks from top (8) to bottom (1), each containing 8 squares
     */
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

    /**
     * File labels for the board columns.
     */
    public List<String> fileLabels() {
        return List.of("a", "b", "c", "d", "e", "f", "g", "h");
    }

    // ==================================================================================
    // Queries
    // ==================================================================================

    /**
     * Get the current position as FEN.
     */
    public String fen() {
        return chessBoard.getFen();
    }

    /**
     * Get the move history in SAN notation.
     */
    public List<String> moves() {
        return List.copyOf(moveHistory);
    }

    /**
     * Get the move history as PGN movetext.
     */
    public String pgn() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < moveHistory.size(); i++) {
            if (i % 2 == 0) {
                sb.append((i / 2) + 1).append(". ");
            }
            sb.append(moveHistory.get(i)).append(" ");
        }
        return sb.toString().trim();
    }

    public GameResult result() {
        return result;
    }

    public Side sideToMove() {
        return chessBoard.getSideToMove();
    }

    public int moveCount() {
        return moveHistory.size();
    }

    public int fullMoveNumber() {
        return chessBoard.getMoveCounter();
    }

    public boolean isGameOver() {
        return result != GameResult.IN_PROGRESS;
    }

    /**
     * Describe the current game status as a single string.
     */
    @Verb(value = VerbSememe.Describe.KEY, doc = "Describe game status")
    public String describeStatus() {
        return sideToMove() + " to move | Move " + fullMoveNumber()
                + " | " + result() + (isCheck() ? " | CHECK" : "");
    }

    // ==================================================================================
    // Clock
    // ==================================================================================

    /**
     * Get the chess clock, or null if no time control is set.
     */
    public ChessClock clock() { return clock; }

    /**
     * Set time control for this game.
     *
     * <p>Parses specs like "5+3" (5 min, 3s increment), "10+0" (10 min, no increment),
     * or "off" to disable the clock.
     *
     * @param spec Time control specification
     * @return status message
     */
    @Verb(value = VerbSememe.Put.KEY, doc = "Set time control (e.g., '5+3')")
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
    // Display-friendly Getters (for surface template bindings)
    // ==================================================================================

    /**
     * Display name for the white player (seat 0).
     * Returns the player's handle if joined, otherwise "White".
     */
    public String whiteLabel() {
        return playerName(0).orElse("White");
    }

    /**
     * Display name for the black player (seat 1).
     * Returns the player's handle if joined, otherwise "Black".
     */
    public String blackLabel() {
        return playerName(1).orElse("Black");
    }

    /**
     * Avatar for the white player — king glyph as default.
     */
    public String whiteAvatar() { return "♔"; }

    /**
     * Avatar for the black player — king glyph as default.
     */
    public String blackAvatar() { return "♚"; }

    /**
     * HandleSurface for the white player (embedded above/below the board).
     */
    public HandleSurface whiteHandle() {
        String subtitle = currentTurnSubtitle(Side.WHITE);
        HandleSurface handle = subtitle != null
                ? HandleSurface.forHeader(playerIcon(0), whiteLabel(), subtitle)
                : HandleSurface.forHeader(playerIcon(0), whiteLabel());
        handle.badge(!isGameOver() && sideToMove() == Side.WHITE ? "▶" : "▷");
        return handle;
    }

    /**
     * HandleSurface for the black player (embedded above/below the board).
     */
    public HandleSurface blackHandle() {
        String subtitle = currentTurnSubtitle(Side.BLACK);
        HandleSurface handle = subtitle != null
                ? HandleSurface.forHeader(playerIcon(1), blackLabel(), subtitle)
                : HandleSurface.forHeader(playerIcon(1), blackLabel());
        handle.badge(!isGameOver() && sideToMove() == Side.BLACK ? "▶" : "▷");
        return handle;
    }

    private String currentTurnSubtitle(Side side) {
        if (isGameOver()) return "Final";
        // Keep both handle blocks the same visual width; turn state is conveyed by badge.
        return "Move " + fullMoveNumber();
    }

    private String playerIcon(int seat) {
        return playerAt(seat).isPresent() ? "🧑" : "👤";
    }

    /**
     * Unified chess board — one class with @Scene.Body + @Scene.Container.
     * Embedded via @Scene.Embed in the BoardColumn.
     */
    public ChessBoard chessBoard() {
        return new ChessBoard(this);
    }

    /**
     * Human-readable side-to-move label (e.g., "alice to move" or "WHITE to move").
     */
    public String sideToMoveLabel() {
        String name = sideToMove() == Side.WHITE ? whiteLabel() : blackLabel();
        return name + " to move";
    }

    /**
     * Human-readable status line including check and game-over state.
     */
    public String statusLabel() {
        if (isGameOver()) return formatResult(result);
        String name = sideToMove() == Side.WHITE ? whiteLabel() : blackLabel();
        if (isCheck()) return name + " to move (check)";
        return name + " to move";
    }

    /**
     * Human-readable move counter (e.g., "Move 15").
     */
    public String moveLabel() {
        return "Move " + fullMoveNumber();
    }

    /**
     * Human-readable half-move clock.
     */
    public String halfMoveLabel() {
        return "Half move clock: " + chessBoard.getHalfMoveCounter();
    }

    /**
     * Declarative status lines for the side panel.
     */
    public List<String> statusLines() {
        List<String> lines = new ArrayList<>();
        lines.add("Side to move: " + sideToMove().name());
        lines.add("Full move: " + fullMoveNumber());
        lines.add("Half move clock: " + chessBoard.getHalfMoveCounter());
        if (isCheck()) {
            lines.add("Check: Yes");
        }
        if (isDrawOfferPending()) {
            lines.add("Draw offer from: " + drawOfferFrom().name());
        }
        if (isGameOver()) {
            lines.add("Result: " + formatResult(result));
        }
        return lines;
    }

    public boolean hasMoveHistory() {
        return !moveHistory.isEmpty();
    }

    public List<String> moveHistoryRows() {
        List<String> rows = new ArrayList<>();
        for (int i = 0; i < moveHistory.size(); i += 2) {
            int moveNum = (i / 2) + 1;
            String whiteMove = moveHistory.get(i);
            String blackMove = (i + 1 < moveHistory.size()) ? moveHistory.get(i + 1) : "";
            rows.add(moveNum + ". " + whiteMove + (blackMove.isEmpty() ? "" : " " + blackMove));
        }
        return rows;
    }

    // ==================================================================================
    // GameComponent Abstract Methods
    // ==================================================================================

    @Override
    public int minPlayers() {
        return 2;
    }

    @Override
    public int maxPlayers() {
        return 2;
    }

    @Override
    public Set<Integer> activePlayers() {
        if (isGameOver()) return Set.of();
        return Set.of(sideToMove() == Side.WHITE ? 0 : 1);
    }

    @Override
    public Optional<Integer> winner() {
        return switch (result) {
            case WHITE_WINS_CHECKMATE, WHITE_WINS_RESIGNATION -> Optional.of(0);
            case BLACK_WINS_CHECKMATE, BLACK_WINS_RESIGNATION -> Optional.of(1);
            default -> Optional.empty();
        };
    }

    @Override
    public GameBoard board() {
        return CHESS_BOARD;
    }

    @Override
    public BoardState<ChessPiece> state() {
        BoardState<ChessPiece> boardState = new BoardState<>(CHESS_BOARD);
        for (int rank = 0; rank < 8; rank++) {
            for (int file = 0; file < 8; file++) {
                Square sq = Square.squareAt(file + rank * 8);
                com.github.bhlangonijr.chesslib.Piece piece = chessBoard.getPiece(sq);
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

    public boolean isCheck() {
        return chessBoard.isKingAttacked();
    }

    /**
     * Get a map of square ID → display character for every square on the board.
     *
     * <p>Occupied squares show the piece symbol; empty squares show a dot or
     * space based on square color. Used by ChessSurface binding expressions
     * to ensure every square has a character (prevents layout collapse in text renderers).
     */
    public Map<String, String> squareDisplay() {
        BoardState<ChessPiece> boardState = state();
        Map<String, String> display = new LinkedHashMap<>();
        for (int rank = 0; rank < 8; rank++) {
            for (int file = 0; file < 8; file++) {
                String id = GameBoard.gridLabel(file, rank);
                var piece = boardState.pieceAt(id);
                if (piece.isPresent()) {
                    display.put(id, piece.get().symbol());
                } else {
                    display.put(id, (rank + file) % 2 == 0 ? "·" : " ");
                }
            }
        }
        return display;
    }

    /**
     * Get a map of square ID → Piece for every occupied square.
     *
     * <p>Used by ChessSurface binding expressions for multi-fidelity rendering.
     * GUI renderers use {@link Piece#imageKey()}, text renderers use
     * {@link Piece#symbol()}. Empty squares are not included.
     */
    public Map<String, Piece> squarePieces() {
        BoardState<ChessPiece> boardState = state();
        Map<String, Piece> pieces = new LinkedHashMap<>();
        for (int rank = 0; rank < 8; rank++) {
            for (int file = 0; file < 8; file++) {
                String id = GameBoard.gridLabel(file, rank);
                boardState.pieceAt(id).ifPresent(p -> pieces.put(id, p));
            }
        }
        return pieces;
    }

    // ==================================================================================
    // Captured Pieces
    // ==================================================================================

    @Getter @Accessors(fluent = true) @EqualsAndHashCode
    public static class CapturedPieces {
        private final List<ChessPiece> byWhite;  // Black pieces White captured
        private final List<ChessPiece> byBlack;  // White pieces Black captured

        public CapturedPieces(List<ChessPiece> byWhite, List<ChessPiece> byBlack) {
            this.byWhite = byWhite;
            this.byBlack = byBlack;
        }

        public int materialAdvantage() {
            int whiteValue = byWhite.stream().mapToInt(ChessGame::pieceValue).sum();
            int blackValue = byBlack.stream().mapToInt(ChessGame::pieceValue).sum();
            return whiteValue - blackValue;
        }
    }

    public CapturedPieces capturedPieces() {
        List<ChessPiece> byWhite = new ArrayList<>();
        List<ChessPiece> byBlack = new ArrayList<>();
        for (MoveBackup backup : chessBoard.getBackup()) {
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
        Comparator<ChessPiece> byValue = Comparator.comparingInt(ChessGame::pieceValue).reversed();
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

    /**
     * Human-readable material advantage label (e.g., "White +3") or null if even.
     */
    public String materialAdvantageLabel() {
        int adv = capturedPieces().materialAdvantage();
        if (adv == 0) return null;
        return adv > 0 ? "White +" + adv : "Black +" + (-adv);
    }

    public boolean isDrawOfferPending() {
        return drawOfferFrom != null;
    }

    public Side drawOfferFrom() {
        return drawOfferFrom;
    }

    // ==================================================================================
    // Board Rendering
    // ==================================================================================

    /**
     * Render the board as Unicode text (for CLI and toString).
     */
    @Verb(value = VerbSememe.Show.KEY, doc = "Show the chess board")
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

    // ==================================================================================
    // View Generation (for text and GUI rendering)
    // ==================================================================================

    public View view() {
        return viewBoard();
    }

    /**
     * Generate a View for rendering the current board position.
     *
     * <p>Uses the inline {@code @Scene} annotations on {@link ChessGame} for
     * the board representation. Works across CLI (unicode), TUI, GUI, and 3D
     * renderers — the {@code @Scene.Body} annotation on ChessGame provides the 3D slab,
     * and {@code composeSurfaceOnBody} elevates squares and places GLB meshes.
     */
    @Override
    public View viewBoard() {
        SurfaceSchema<ChessGame> schema = new SurfaceSchema<>() {};
        schema.value(this);
        schema.structureClass(ChessGame.class);
        return View.of(schema);
    }

    /**
     * Generate a View of legal moves.
     */
    public View viewLegalMoves() {
        List<String> moves = legalMoves();
        if (moves.isEmpty()) {
            return View.of(TextSurface.of("No legal moves"));
        }

        ContainerSurface box = ContainerSurface.vertical();
        box.add(TextSurface.of("Legal Moves (" + moves.size() + ")").style("heading"));

        StringBuilder sb = new StringBuilder();
        for (String moveStr : moves) {
            sb.append(moveStr).append(" ");
        }
        box.add(TextSurface.of(sb.toString().trim()));

        return View.of(box);
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

    @Override
    public String toString() {
        return renderBoard();
    }
}
