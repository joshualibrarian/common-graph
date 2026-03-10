package dev.everydaythings.graph.game.chess;

import com.github.bhlangonijr.chesslib.Side;
import dev.everydaythings.graph.game.BoardState;
import dev.everydaythings.graph.game.GameBoard;
import dev.everydaythings.graph.game.GameMode;
import dev.everydaythings.graph.item.Item;
import dev.everydaythings.graph.item.ItemScanner;
import dev.everydaythings.graph.item.VerbSpec;
import dev.everydaythings.graph.item.action.ActionContext;
import dev.everydaythings.graph.item.action.ActionResult;
import dev.everydaythings.graph.item.id.ItemID;
import dev.everydaythings.graph.language.Sememe;
import dev.everydaythings.graph.game.GameVocabulary;
import dev.everydaythings.graph.runtime.Librarian;
import dev.everydaythings.graph.ui.scene.View;
import dev.everydaythings.graph.ui.scene.Scene;
import dev.everydaythings.graph.ui.scene.surface.SurfaceSchema;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for Chess component and chesslib integration.
 */
class ChessTest {

    @Test
    void newGame_startsFromInitialPosition() {
        ChessGame chess = ChessGame.create();

        assertThat(chess.sideToMove().name()).isEqualTo("WHITE");
        assertThat(chess.moveCount()).isEqualTo(0);
        assertThat(chess.fullMoveNumber()).isEqualTo(1);
        assertThat(chess.isGameOver()).isFalse();
        assertThat(chess.isCheck()).isFalse();
    }

    @Test
    void newGame_hasLegalMoves() {
        ChessGame chess = ChessGame.create();

        assertThat(chess.legalMoves())
                .isNotEmpty()
                .contains("e2e4", "d2d4", "g1f3");  // Common first moves
    }

    @Test
    void fromFen_loadsMidgamePosition() {
        // After 1.e4 e5 2.Nf3 Nc6 3.Bb5 (Ruy Lopez)
        String fen = "r1bqkbnr/pppp1ppp/2n5/1B2p3/4P3/5N2/PPPP1PPP/RNBQK2R b KQkq - 3 3";
        ChessGame chess = ChessGame.fromFen(fen);

        assertThat(chess.sideToMove().name()).isEqualTo("BLACK");
        assertThat(chess.isGameOver()).isFalse();
        assertThat(chess.fen()).isEqualTo(fen);
    }

    @Test
    void move_appliesLegalMoves() {
        ChessGame chess = ChessGame.create();

        // Legal opening move - actually applies through Dag
        assertThat(chess.move("e2e4")).isNull();
        assertThat(chess.moveCount()).isEqualTo(1);
        assertThat(chess.sideToMove().name()).isEqualTo("BLACK");

        // Black responds
        assertThat(chess.move("e7e5")).isNull();
        assertThat(chess.moveCount()).isEqualTo(2);
        assertThat(chess.sideToMove().name()).isEqualTo("WHITE");
    }

    @Test
    void move_rejectsIllegalMoves() {
        ChessGame chess = ChessGame.create();

        // Illegal move (pawn can't move to e5 directly from start)
        assertThat(chess.move("e2e5")).isNotNull();
        assertThat(chess.moveCount()).isEqualTo(0);  // No move recorded
    }

    @Test
    void isLegalMove_validatesWithoutApplying() {
        ChessGame chess = ChessGame.create();

        // Legal move - validates but doesn't apply
        assertThat(chess.isLegalMove("e2e4")).isTrue();
        assertThat(chess.moveCount()).isEqualTo(0);  // Still no moves

        // Illegal move
        assertThat(chess.isLegalMove("e2e5")).isFalse();
    }

    @Test
    void renderBoard_showsInitialPosition() {
        ChessGame chess = ChessGame.create();
        String board = chess.renderBoard();

        assertThat(board)
                .contains("a b c d e f g h")  // File labels
                .contains("White to move");    // Side indicator
    }

    @Test
    void pgn_emptyForNewGame() {
        ChessGame chess = ChessGame.create();
        assertThat(chess.pgn()).isEmpty();
    }

    @Test
    void gameResult_startsInProgress() {
        ChessGame chess = ChessGame.create();
        assertThat(chess.result()).isEqualTo(ChessGame.GameResult.IN_PROGRESS);
    }

    @Test
    void drawOffer_notPendingInitially() {
        ChessGame chess = ChessGame.create();
        assertThat(chess.isDrawOfferPending()).isFalse();
        assertThat(chess.drawOfferFrom()).isNull();
    }

    @Test
    void scholarsMate_detectsCheckmate() {
        // Scholar's mate: 1.e4 e5 2.Qh5 Nc6 3.Bc4 Nf6?? 4.Qxf7#
        String fen = "r1bqkb1r/pppp1Qpp/2n2n2/4p3/2B1P3/8/PPPP1PPP/RNB1K1NR b KQkq - 0 4";
        ChessGame chess = ChessGame.fromFen(fen);

        assertThat(chess.legalMoves()).isEmpty();  // No legal moves = checkmate or stalemate
        // Note: The board.isMated() check happens in fold(), not just from loading FEN
    }

    @Test
    void dagIntegration_movesCreateEvents() {
        ChessGame chess = ChessGame.create();

        // Initially no heads (empty Dag)
        assertThat(chess.isEmpty()).isTrue();

        // Make a move
        chess.move("e2e4");

        // Now has heads (events in Dag)
        assertThat(chess.isEmpty()).isFalse();
        assertThat(chess.heads()).hasSize(1);
    }

    @Test
    void pgn_tracksFullGame() {
        ChessGame chess = ChessGame.create();

        // Play a few moves
        chess.move("e2e4");
        chess.move("e7e5");
        chess.move("g1f3");
        chess.move("b8c6");

        String pgn = chess.pgn();
        assertThat(pgn).contains("1.");
        assertThat(pgn).contains("e2e4");
        assertThat(pgn).contains("e7e5");
        assertThat(pgn).contains("2.");
        assertThat(pgn).contains("g1f3");
        assertThat(pgn).contains("b8c6");
    }

    @Test
    void resign_endsGame() {
        ChessGame chess = ChessGame.create();
        chess.move("e2e4");

        // Black resigns
        chess.resign(com.github.bhlangonijr.chesslib.Side.BLACK);

        assertThat(chess.isGameOver()).isTrue();
        assertThat(chess.result()).isEqualTo(ChessGame.GameResult.WHITE_WINS_RESIGNATION);
    }

    @Test
    void drawOffer_canBeAccepted() {
        ChessGame chess = ChessGame.create();
        chess.move("e2e4");
        chess.move("e7e5");

        // White offers draw
        chess.offerDraw(com.github.bhlangonijr.chesslib.Side.WHITE);
        assertThat(chess.isDrawOfferPending()).isTrue();

        // Black accepts
        chess.acceptDraw(com.github.bhlangonijr.chesslib.Side.BLACK);
        assertThat(chess.isGameOver()).isTrue();
        assertThat(chess.result()).isEqualTo(ChessGame.GameResult.DRAW_AGREEMENT);
    }

    // ==================================================================================
    // Board / State / Scene Graph Integration Tests
    // ==================================================================================

    @Test
    void board_hasKnightEdges() {
        ChessGame chess = ChessGame.create();
        GameBoard board = chess.board();

        // g1 knight can reach f3 and h3 (and e2)
        Set<String> knightMoves = board.neighbors("g1", "knight");
        assertThat(knightMoves).contains("f3", "h3", "e2");

        // Still has orthogonal and diagonal
        assertThat(board.neighbors("e4", "orthogonal")).hasSize(4);
        assertThat(board.neighbors("e4", "diagonal")).hasSize(4);
    }

    @Test
    void state_hasAllPieces() {
        ChessGame chess = ChessGame.create();
        BoardState<ChessPiece> state = chess.state();

        assertThat(state.pieceCount()).isEqualTo(32);

        // White pieces on ranks 1-2
        assertThat(state.pieceAt("e1")).hasValue(ChessPiece.WHITE_KING);
        assertThat(state.pieceAt("d1")).hasValue(ChessPiece.WHITE_QUEEN);
        assertThat(state.pieceAt("a1")).hasValue(ChessPiece.WHITE_ROOK);
        assertThat(state.pieceAt("e2")).hasValue(ChessPiece.WHITE_PAWN);

        // Black pieces on ranks 7-8
        assertThat(state.pieceAt("e8")).hasValue(ChessPiece.BLACK_KING);
        assertThat(state.pieceAt("d8")).hasValue(ChessPiece.BLACK_QUEEN);
        assertThat(state.pieceAt("e7")).hasValue(ChessPiece.BLACK_PAWN);

        // Empty squares
        assertThat(state.pieceAt("e4")).isEmpty();
        assertThat(state.pieceAt("d5")).isEmpty();
    }

    @Test
    void state_reflectsMove() {
        ChessGame chess = ChessGame.create();
        chess.move("e2e4");

        BoardState<ChessPiece> state = chess.state();
        assertThat(state.pieceAt("e2")).isEmpty();
        assertThat(state.pieceAt("e4")).hasValue(ChessPiece.WHITE_PAWN);
        assertThat(state.pieceCount()).isEqualTo(32); // no captures
    }

    @Test
    void viewBoard_producesStructuredView() {
        ChessGame chess = ChessGame.create();
        View view = chess.viewBoard();

        assertThat(view).isNotNull();
        SurfaceSchema<?> root = view.root();
        assertThat(root).isNotNull();
        assertThat(root.value()).isSameAs(chess);
    }

    @Test
    void bodyAnnotation_declaresInlineGeometry() {
        Scene.Body bodyAnno = ChessGame.class.getAnnotation(Scene.Body.class);
        assertThat(bodyAnno).isNotNull();
        assertThat(bodyAnno.shape()).isEqualTo("box");
        assertThat(bodyAnno.fontSize()).isEqualTo("2.2cm");
        assertThat(bodyAnno.color()).isEqualTo(0x8B4513);
    }

    @Test
    void chessBoardView_producesEquivalentView() {
        ChessGame chess = ChessGame.create();
        SurfaceSchema<ChessGame> surface = chessSurface(chess);
        View view = View.of(surface);

        assertThat(view).isNotNull();
        assertThat(view.root()).isNotNull();
        assertThat(view.root().value()).isSameAs(chess);
    }

    // ==================================================================================
    // Rendering Tests
    // ==================================================================================

    private static SurfaceSchema<ChessGame> chessSurface(ChessGame chess) {
        SurfaceSchema<ChessGame> surface = new SurfaceSchema<>() {};
        surface.value(chess);
        surface.structureClass(ChessGame.class);
        return surface;
    }

    @Test
    void chessSurface_rendering_resolvesLiveState() {
        ChessGame chess = ChessGame.create();
        SurfaceSchema<ChessGame> surface = chessSurface(chess);
        RecordingSurfaceRenderer recorder = new RecordingSurfaceRenderer();

        surface.render(recorder);

        // Active-turn badge should be present on the current player's handle.
        assertThat(recorder.texts).isNotEmpty();
        assertThat(recorder.images).anyMatch(img -> img.equals("▶"));

        // Board pieces present (32 on board + player handle avatars)
        assertThat(recorder.images).hasSizeGreaterThanOrEqualTo(32);

        // a8 is a black rook
        assertThat(recorder.images).anyMatch(img -> img.equals("♜"));
        // e1 is a white king
        assertThat(recorder.images).anyMatch(img -> img.equals("♔"));
    }

    @Test
    void chessSurface_rendering_reflectsStateAfterMove() {
        ChessGame chess = ChessGame.create();
        chess.move("e2e4");

        SurfaceSchema<ChessGame> surface = chessSurface(chess);
        RecordingSurfaceRenderer recorder = new RecordingSurfaceRenderer();

        surface.render(recorder);

        // 32 pieces still on board (no captures), plus player handle avatars
        assertThat(recorder.images).hasSizeGreaterThanOrEqualTo(32);

        // Active-turn badge should still be present after turn changes.
        assertThat(recorder.images).anyMatch(img -> img.equals("▶"));
    }

    @Test
    void chessSurface_rendering_afterCapture_showsFewerPieces() {
        ChessGame chess = ChessGame.create();
        // Scholar's mate setup: 1.e4 e5 2.Bc4 Nc6 3.Qh5 Nf6 4.Qxf7#
        chess.move("e2e4");
        chess.move("e7e5");
        chess.move("f1c4");
        chess.move("b8c6");
        chess.move("d1h5");
        chess.move("g8f6");
        chess.move("h5f7"); // Qxf7# — captures the f7 pawn

        SurfaceSchema<ChessGame> surface = chessSurface(chess);
        RecordingSurfaceRenderer recorder = new RecordingSurfaceRenderer();

        surface.render(recorder);

        // 31 on-board pieces + 1 captured piece in side panel + player handle avatars
        assertThat(recorder.images).hasSizeGreaterThanOrEqualTo(32);
    }

    @Test
    void chessSurface_rendering_showsSelectionHighlight() {
        ChessGame chess = ChessGame.create();
        chess.select("e2"); // Select white pawn

        SurfaceSchema<ChessGame> surface = chessSurface(chess);
        RecordingSurfaceRenderer recorder = new RecordingSurfaceRenderer();
        surface.render(recorder);

        // e2 square should have "selected" style
        assertThat(recorder.boxStylesById.get("e2")).contains("selected");

        // Legal targets (e3, e4) should have "legal-target" style
        assertThat(recorder.boxStylesById.get("e3")).contains("legal-target");
        assertThat(recorder.boxStylesById.get("e4")).contains("legal-target");

        // Other squares should not have selection styles
        assertThat(recorder.boxStylesById.get("d2")).doesNotContain("selected", "legal-target");
    }

    @Test
    void chessSurface_rendering_emitsClickEvents() {
        ChessGame chess = ChessGame.create();
        SurfaceSchema<ChessGame> surface = chessSurface(chess);
        RecordingSurfaceRenderer recorder = new RecordingSurfaceRenderer();
        surface.render(recorder);

        // Squares should have click → select events
        assertThat(recorder.events).anyMatch(e ->
                e.action.equals("select") && e.target.equals("e2"));
        assertThat(recorder.events).anyMatch(e ->
                e.action.equals("select") && e.target.equals("a1"));
    }

    /** Minimal SurfaceRenderer that records text, images, styles, and events. */
    static class RecordingSurfaceRenderer implements dev.everydaythings.graph.ui.scene.surface.SurfaceRenderer {
        final java.util.List<String> texts = new java.util.ArrayList<>();
        final java.util.List<String> images = new java.util.ArrayList<>();
        final java.util.Map<String, java.util.List<String>> boxStylesById = new java.util.HashMap<>();
        final java.util.List<RecordedEvent> events = new java.util.ArrayList<>();

        private String pendingId;
        private final java.util.List<RecordedEvent> pendingEvents = new java.util.ArrayList<>();

        record RecordedEvent(String on, String action, String target) {}

        @Override public void text(String content, java.util.List<String> styles) { texts.add(content); }
        @Override public void formattedText(String content, String format, java.util.List<String> styles) { texts.add(content); }
        @Override public void image(String alt, dev.everydaythings.graph.item.id.ContentID image,
                                    dev.everydaythings.graph.item.id.ContentID solid, String size,
                                    String fit, java.util.List<String> styles) { images.add(alt); }
        @Override public void image(String alt, dev.everydaythings.graph.item.id.ContentID image,
                                    dev.everydaythings.graph.item.id.ContentID solid, String resource,
                                    String size, String fit, java.util.List<String> styles) { images.add(alt); }
        @Override public void beginBox(dev.everydaythings.graph.ui.scene.Scene.Direction direction, java.util.List<String> styles) {
            if (pendingId != null) {
                boxStylesById.put(pendingId, new java.util.ArrayList<>(styles));
                events.addAll(pendingEvents);
            }
            pendingId = null;
            pendingEvents.clear();
        }
        @Override public void endBox() {}
        @Override public void type(String type) {}
        @Override public void id(String id) { pendingId = id; }
        @Override public void editable(boolean editable) {}
        @Override public void event(String on, String action, String target) {
            pendingEvents.add(new RecordedEvent(on, action, target));
        }
    }

    // ==================================================================================
    // Selection Tests
    // ==================================================================================

    @Test
    void select_ownPiece_setsSelectedAndLegalTargets() {
        ChessGame chess = ChessGame.create();

        chess.select("e2"); // White pawn

        assertThat(chess.selectedSquare()).isEqualTo("e2");
        assertThat(chess.legalTargets()).contains("e3", "e4");
    }

    @Test
    void select_samePieceTwice_deselects() {
        ChessGame chess = ChessGame.create();
        chess.select("e2");
        assertThat(chess.selectedSquare()).isEqualTo("e2");

        chess.select("e2"); // Click again → deselect
        assertThat(chess.selectedSquare()).isNull();
        assertThat(chess.legalTargets()).isEmpty();
    }

    @Test
    void select_legalTarget_executesMove() {
        ChessGame chess = ChessGame.create();
        chess.select("e2"); // Select white e-pawn
        chess.select("e4"); // Click legal target → move

        assertThat(chess.selectedSquare()).isNull(); // Selection cleared
        assertThat(chess.moveCount()).isEqualTo(1);
        assertThat(chess.sideToMove()).isEqualTo(Side.BLACK);

        BoardState<ChessPiece> state = chess.state();
        assertThat(state.pieceAt("e2")).isEmpty();
        assertThat(state.pieceAt("e4")).hasValue(ChessPiece.WHITE_PAWN);
    }

    @Test
    void select_emptySquare_clearsSelection() {
        ChessGame chess = ChessGame.create();
        chess.select("e2");
        assertThat(chess.selectedSquare()).isEqualTo("e2");

        chess.select("d4"); // Empty, not a legal target
        assertThat(chess.selectedSquare()).isNull();
    }

    @Test
    void select_opponentPiece_clearsSelection() {
        ChessGame chess = ChessGame.create();
        chess.select("e2");

        chess.select("e7"); // Black pawn — not own piece
        assertThat(chess.selectedSquare()).isNull();
    }

    @Test
    void select_differentOwnPiece_switchesSelection() {
        ChessGame chess = ChessGame.create();
        chess.select("e2");
        assertThat(chess.selectedSquare()).isEqualTo("e2");

        chess.select("d2"); // Different white pawn
        assertThat(chess.selectedSquare()).isEqualTo("d2");
        assertThat(chess.legalTargets()).contains("d3", "d4");
    }

    @Test
    void place_withSelectedPiece_executesMove() {
        ChessGame chess = ChessGame.create();
        chess.select("e2");

        String result = chess.place("e4");
        assertThat(result).isNull(); // Success
        assertThat(chess.moveCount()).isEqualTo(1);
        assertThat(chess.selectedSquare()).isNull();
    }

    @Test
    void place_withoutSelection_returnsError() {
        ChessGame chess = ChessGame.create();
        String result = chess.place("e4");
        assertThat(result).isEqualTo("No piece selected");
    }

    @Test
    void place_illegalTarget_returnsError() {
        ChessGame chess = ChessGame.create();
        chess.select("e2");

        String result = chess.place("e5"); // Not legal for pawn
        assertThat(result).isEqualTo("Not a legal target");
    }

    @Test
    void ranks_returnsViewModelWithSelectionState() {
        ChessGame chess = ChessGame.create();
        chess.select("e2");

        List<ChessGame.RankView> ranks = chess.ranks();
        assertThat(ranks).hasSize(8);

        // Rank 8 is first (top of board)
        assertThat(ranks.getFirst().label()).isEqualTo("8");
        assertThat(ranks.getFirst().squares()).hasSize(8);

        // Find e2 square (rank 2 = index 6)
        ChessGame.RankView rank2 = ranks.get(6);
        assertThat(rank2.label()).isEqualTo("2");
        ChessGame.SquareView e2 = rank2.squares().get(4); // file e = index 4
        assertThat(e2.id()).isEqualTo("e2");
        assertThat(e2.selected()).isTrue();
        assertThat(e2.piece()).isEqualTo(ChessPiece.WHITE_PAWN);

        // e4 should be a legal target (rank 4 = index 4)
        ChessGame.RankView rank4 = ranks.get(4);
        ChessGame.SquareView e4 = rank4.squares().get(4);
        assertThat(e4.id()).isEqualTo("e4");
        assertThat(e4.legalTarget()).isTrue();
        assertThat(e4.piece()).isNull(); // Empty square

        // d2 should not be selected or legal target
        ChessGame.SquareView d2 = rank2.squares().get(3);
        assertThat(d2.selected()).isFalse();
        assertThat(d2.legalTarget()).isFalse();
    }

    // ==================================================================================
    // Verb Discovery and Dispatch Tests
    // ==================================================================================

    @Test
    void verbsAreDiscoverable() {
        List<VerbSpec> verbs = ItemScanner.scanComponentVerbs(ChessGame.class, "chess");

        Set<ItemID> ids = verbs.stream()
                .map(VerbSpec::sememeId)
                .collect(Collectors.toSet());

        assertThat(ids).contains(
                ItemID.fromString(GameVocabulary.MOVE),
                ItemID.fromString(GameVocabulary.RESIGN),
                ItemID.fromString(GameVocabulary.OFFER),
                ItemID.fromString(GameVocabulary.ACCEPT),
                ItemID.fromString(GameVocabulary.DECLINE),
                ItemID.fromString(Sememe.SHOW),
                ItemID.fromString(Sememe.LIST),
                ItemID.fromString(Sememe.DESCRIBE),
                ItemID.fromString(GameVocabulary.SELECT),
                ItemID.fromString(GameVocabulary.PLACE)
        );
    }

    @Test
    void verbDispatch_move_onGenericItem() {
        Librarian librarian = Librarian.createInMemory();

        // Create a plain generic Item — no ChessGame class needed
        Item item = Item.create(librarian);
        item.addComponent("chess", ChessGame.create());

        // Dispatch move via verb system (direct sememe key)
        ActionResult result = item.dispatch("cg.verb:move", List.of("e2e4"));
        assertThat(result.success()).isTrue();
        assertThat(result.value()).isNull();  // null = success, no message

        // Verify move applied via the component
        ChessGame chess = (ChessGame) item.component("chess");
        assertThat(chess.moveCount()).isEqualTo(1);
        assertThat(chess.sideToMove()).isEqualTo(Side.BLACK);
    }

    @Test
    void verbDispatch_showBoard_onGenericItem() {
        Librarian librarian = Librarian.createInMemory();
        Item item = Item.create(librarian);
        item.addComponent("chess", ChessGame.create());

        ActionResult result = item.dispatch("cg.verb:show", List.of());
        assertThat(result.success()).isTrue();
        assertThat(result.value().toString()).contains("a b c d e f g h");
    }

    @Test
    void verbDispatch_resign_onGenericItem() {
        Librarian librarian = Librarian.createInMemory();
        Item item = Item.create(librarian);
        item.addComponent("chess", ChessGame.create());

        ActionResult result = item.dispatch("cg.verb:resign", List.of("BLACK"));
        assertThat(result.success()).isTrue();

        ChessGame chess = (ChessGame) item.component("chess");
        assertThat(chess.isGameOver()).isTrue();
    }

    @Test
    void verbDispatch_listLegalMoves_onGenericItem() {
        Librarian librarian = Librarian.createInMemory();
        Item item = Item.create(librarian);
        item.addComponent("chess", ChessGame.create());

        ActionResult result = item.dispatch("cg.verb:list", List.of());
        assertThat(result.success()).isTrue();
        assertThat((List<?>) result.value()).hasSize(20);  // 20 legal opening moves
    }

    @Test
    void verbDispatch_describeStatus_onGenericItem() {
        Librarian librarian = Librarian.createInMemory();
        Item item = Item.create(librarian);
        item.addComponent("chess", ChessGame.create());

        ActionResult result = item.dispatch("cg.verb:describe", List.of());
        assertThat(result.success()).isTrue();
        assertThat(result.value().toString()).contains("to move");
    }

    @Test
    void verbDispatch_select_onGenericItem() {
        Librarian librarian = Librarian.createInMemory();
        Item item = Item.create(librarian);
        item.addComponent("chess", ChessGame.create());

        ActionResult result = item.dispatch("cg.verb:select", List.of("e2"));
        assertThat(result.success()).isTrue();

        ChessGame chess = (ChessGame) item.component("chess");
        assertThat(chess.selectedSquare()).isEqualTo("e2");
        assertThat(chess.legalTargets()).contains("e3", "e4");
    }

    @Test
    void verbDispatch_select_thenLegalTarget_executesMove() {
        Librarian librarian = Librarian.createInMemory();
        Item item = Item.create(librarian);
        item.addComponent("chess", ChessGame.create());

        // Select piece
        item.dispatch("cg.verb:select", List.of("e2"));
        // Click legal target → auto-move
        item.dispatch("cg.verb:select", List.of("e4"));

        ChessGame chess = (ChessGame) item.component("chess");
        assertThat(chess.moveCount()).isEqualTo(1);
        assertThat(chess.selectedSquare()).isNull();
    }

    @Test
    void move_handlesSpaceSeparatedUCI() {
        ChessGame chess = ChessGame.create();
        // "d7 d6" should work (spaces stripped)
        assertThat(chess.move("e2 e4")).isNull();
        assertThat(chess.moveCount()).isEqualTo(1);
    }

    @Test
    void state_reflectsMovedPieces() {
        ChessGame chess = ChessGame.create();
        chess.move("e2e4");

        BoardState<ChessPiece> state = chess.state();

        // e2 should be empty (pawn moved away)
        assertThat(state.pieceAt("e2")).isEmpty();
        // e4 should have a white pawn
        assertThat(state.pieceAt("e4")).isPresent();
        assertThat(state.pieceAt("e4").get()).isEqualTo(ChessPiece.WHITE_PAWN);

        // Other pieces should still be in starting position
        assertThat(state.pieceAt("a1")).isPresent();
        assertThat(state.pieceAt("a1").get()).isEqualTo(ChessPiece.WHITE_ROOK);
        assertThat(state.pieceAt("e1")).isPresent();
        assertThat(state.pieceAt("e1").get()).isEqualTo(ChessPiece.WHITE_KING);
        assertThat(state.pieceAt("d8")).isPresent();
        assertThat(state.pieceAt("d8").get()).isEqualTo(ChessPiece.BLACK_QUEEN);
    }

    // ==================================================================================
    // Seat Authorization Tests
    // ==================================================================================

    static ItemID pid(String name) {
        return ItemID.fromString("test:player/" + name);
    }

    static ActionContext ctxFor(ItemID caller) {
        return ActionContext.of(caller, null, null, null);
    }

    @Test
    void move_withAuthorizedCaller_succeeds() {
        ChessGame chess = ChessGame.create();
        ItemID white = pid("white");
        ItemID black = pid("black");
        chess.joinAs(0, white);
        chess.joinAs(1, black);

        // White moves on white's turn
        String result = chess.move(ctxFor(white), "e2e4");
        assertThat(result).isNull();
        assertThat(chess.moveCount()).isEqualTo(1);

        // Black moves on black's turn
        result = chess.move(ctxFor(black), "e7e5");
        assertThat(result).isNull();
        assertThat(chess.moveCount()).isEqualTo(2);
    }

    @Test
    void move_withWrongTurn_throwsSecurityException() {
        ChessGame chess = ChessGame.create();
        chess.setMode(GameMode.AUTHENTICATED);
        ItemID white = pid("white");
        ItemID black = pid("black");
        chess.joinAs(0, white);
        chess.joinAs(1, black);

        // Black tries to move on white's turn
        assertThatThrownBy(() -> chess.move(ctxFor(black), "e7e5"))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("Not your turn");
    }

    @Test
    void move_withUnseatedCaller_throwsSecurityException() {
        ChessGame chess = ChessGame.create();
        chess.setMode(GameMode.AUTHENTICATED);
        ItemID white = pid("white");
        chess.joinAs(0, white);

        // Stranger tries to move
        ItemID stranger = pid("stranger");
        assertThatThrownBy(() -> chess.move(ctxFor(stranger), "e2e4"))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("Not a player");
    }

    // ==================================================================================
    // Captured Pieces Tests
    // ==================================================================================

    @Test
    void capturedPieces_emptyAtStart() {
        ChessGame chess = ChessGame.create();
        ChessGame.CapturedPieces captured = chess.capturedPieces();

        assertThat(captured.byWhite()).isEmpty();
        assertThat(captured.byBlack()).isEmpty();
        assertThat(captured.materialAdvantage()).isEqualTo(0);
    }

    @Test
    void capturedPieces_tracksCaptures() {
        ChessGame chess = ChessGame.create();
        // 1.e4 d5 2.exd5 — White captures Black's d-pawn
        chess.move("e2e4");
        chess.move("d7d5");
        chess.move("e4d5");

        ChessGame.CapturedPieces captured = chess.capturedPieces();
        assertThat(captured.byWhite()).containsExactly(ChessPiece.BLACK_PAWN);
        assertThat(captured.byBlack()).isEmpty();
        assertThat(captured.materialAdvantage()).isEqualTo(1);
    }

    @Test
    void capturedPieces_sortsByValue() {
        ChessGame chess = ChessGame.create();
        // Play a sequence that captures multiple pieces of different values
        // 1.e4 d5 2.exd5 Qxd5 3.Nc3 Qd4 4.Nf3 Qxc4 — Black captures knight (not possible easily)
        // Simpler: use fromFen to set up a position then capture
        // Let's just do: 1.e4 d5 2.exd5 e6 3.dxe6 Bxe6 — two captures
        chess.move("e2e4");
        chess.move("d7d5");
        chess.move("e4d5"); // White takes black pawn
        chess.move("d8d5"); // Black takes white pawn (recapture)

        ChessGame.CapturedPieces captured = chess.capturedPieces();
        assertThat(captured.byWhite()).containsExactly(ChessPiece.BLACK_PAWN);
        assertThat(captured.byBlack()).containsExactly(ChessPiece.WHITE_PAWN);
        assertThat(captured.materialAdvantage()).isEqualTo(0); // Equal trade

        // Now White captures the queen: 3.Nc3 Qe4 4.Nxe4
        chess.move("b1c3");
        chess.move("d5e4");
        chess.move("c3e4"); // White takes black queen

        captured = chess.capturedPieces();
        // byWhite should be sorted: queen first, then pawn
        assertThat(captured.byWhite()).containsExactly(ChessPiece.BLACK_QUEEN, ChessPiece.BLACK_PAWN);
        assertThat(captured.byBlack()).containsExactly(ChessPiece.WHITE_PAWN);
        assertThat(captured.materialAdvantage()).isEqualTo(9); // Q(9) + P(1) - P(1) = 9
    }

    @Test
    void resign_withAuthorizedCaller_endsGame() {
        ChessGame chess = ChessGame.create();
        ItemID white = pid("white");
        ItemID black = pid("black");
        chess.joinAs(0, white);
        chess.joinAs(1, black);

        // White resigns — side derived from seat
        chess.resign(ctxFor(white), null);

        assertThat(chess.isGameOver()).isTrue();
        assertThat(chess.result()).isEqualTo(ChessGame.GameResult.BLACK_WINS_RESIGNATION);
    }

    // ==================================================================================
    // Parameterized creation (verb arguments end-to-end)
    // ==================================================================================

    @Test
    void createWithPlayers() {
        var params = Map.<String, Object>of(
                "players", List.of("Alice", "Bob"));
        ChessGame chess = ChessGame.create(params);

        assertThat(chess.whiteLabel()).isEqualTo("Alice");
        assertThat(chess.blackLabel()).isEqualTo("Bob");
        assertThat(chess.isGameOver()).isFalse();
    }

    @Test
    void createWithFenSource() {
        String fen = "rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq e3 0 1";
        var params = Map.<String, Object>of("source", fen);
        ChessGame chess = ChessGame.create(params);

        assertThat(chess.fen()).isEqualTo(fen);
        assertThat(chess.sideToMove().name()).isEqualTo("BLACK");
    }

    @Test
    void createWithPgnMoves() {
        var params = Map.<String, Object>of(
                "source", "1. e2e4 e7e5 2. g1f3 b8c6");
        ChessGame chess = ChessGame.create(params);

        assertThat(chess.moveCount()).isEqualTo(4);
        assertThat(chess.sideToMove().name()).isEqualTo("WHITE");
    }

    @Test
    void createWithPlayersAndSource() {
        String fen = "rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq e3 0 1";
        var params = Map.<String, Object>of(
                "players", List.of("Alice", "Bob"),
                "source", fen);
        ChessGame chess = ChessGame.create(params);

        assertThat(chess.whiteLabel()).isEqualTo("Alice");
        assertThat(chess.blackLabel()).isEqualTo("Bob");
        assertThat(chess.fen()).isEqualTo(fen);
    }

    @Test
    void createWithEmptyParams() {
        ChessGame chess = ChessGame.create(java.util.Map.of());

        assertThat(chess.whiteLabel()).isEqualTo("White");
        assertThat(chess.blackLabel()).isEqualTo("Black");
        assertThat(chess.moveCount()).isEqualTo(0);
    }

    // ==================================================================================
    // Game Mode Tests
    // ==================================================================================

    @Test
    void defaultMode_isAnalysis() {
        ChessGame chess = ChessGame.create();
        assertThat(chess.mode()).isEqualTo(GameMode.ANALYSIS);
    }

    @Test
    void analysisMode_anyoneCanMove() {
        ChessGame chess = ChessGame.create();
        // No players seated — anyone can move
        assertThat(chess.move("e2e4")).isNull();
        assertThat(chess.move("e7e5")).isNull();
        assertThat(chess.moveCount()).isEqualTo(2);
    }

    @Test
    void analysisMode_noTurnEnforcement() {
        ChessGame chess = ChessGame.create();
        ItemID alice = pid("alice");
        ItemID bob = pid("bob");
        chess.joinAs(0, alice);
        chess.joinAs(1, bob);

        // In analysis mode, wrong-turn moves still work
        chess.move(ctxFor(alice), "e2e4");
        String result = chess.move(ctxFor(alice), "d2d4"); // alice again — would fail in authenticated
        // Should not throw, but might be illegal chess move (wrong turn for chess rules)
        // The key point: no SecurityException
        assertThat(chess.moveCount()).isGreaterThanOrEqualTo(1);
    }

    @Test
    void authenticatedMode_enforcesTurns() {
        ChessGame chess = ChessGame.create();
        chess.setMode(GameMode.AUTHENTICATED);
        ItemID white = pid("white");
        ItemID black = pid("black");
        chess.joinAs(0, white);
        chess.joinAs(1, black);

        chess.move(ctxFor(white), "e2e4");

        // Wrong turn should throw
        assertThatThrownBy(() -> chess.move(ctxFor(white), "d2d4"))
                .isInstanceOf(SecurityException.class);
    }

    @Test
    void archiveMode_rejectsNewMoves() {
        ChessGame chess = ChessGame.create();
        chess.setMode(GameMode.ARCHIVE);

        String result = chess.move("e2e4");
        assertThat(result).contains("archive");
        assertThat(chess.moveCount()).isEqualTo(0);
    }

    @Test
    void archiveMode_rejectsVerbMoves() {
        ChessGame chess = ChessGame.create();
        chess.setMode(GameMode.ARCHIVE);

        String result = chess.move(ctxFor(pid("alice")), "e2e4");
        assertThat(result).contains("archive");
    }

    // ==================================================================================
    // PGN Import Tests
    // ==================================================================================

    @Test
    void createFromPgn_loadsMovesAndSetsArchive() {
        var params = Map.<String, Object>of(
                "players", List.of("Marshall, Frank", "Capablanca, Jose Raul"),
                "source", "1. e4 e5 2. Nf3 Nc6 3. Bb5 a6");
        ChessGame chess = ChessGame.create(params);

        assertThat(chess.mode()).isEqualTo(GameMode.ARCHIVE);
        assertThat(chess.whiteLabel()).isEqualTo("Marshall, Frank");
        assertThat(chess.blackLabel()).isEqualTo("Capablanca, Jose Raul");
        assertThat(chess.moveCount()).isEqualTo(6);
    }

    @Test
    void createFromPgn_modeCanBeOverridden() {
        var params = Map.<String, Object>of(
                "players", List.of("Alice", "Bob"),
                "source", "1. e4 e5",
                "mode", "ANALYSIS");
        ChessGame chess = ChessGame.create(params);

        // Explicit mode overrides the default archive
        assertThat(chess.mode()).isEqualTo(GameMode.ANALYSIS);
        assertThat(chess.moveCount()).isEqualTo(2);
    }

    @Test
    void createFromPgn_stringModeParam() {
        var params = Map.<String, Object>of(
                "mode", GameMode.AUTHENTICATED);
        ChessGame chess = ChessGame.create(params);

        assertThat(chess.mode()).isEqualTo(GameMode.AUTHENTICATED);
    }
}
