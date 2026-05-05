package dev.everydaythings.graph.game.chess;

import dev.everydaythings.graph.game.GameVocabulary;
import dev.everydaythings.graph.item.Implements;
import dev.everydaythings.graph.item.ItemSeed;
import dev.everydaythings.graph.item.ItemOld;
import dev.everydaythings.graph.item.ManifestOld;
import dev.everydaythings.graph.item.id.ItemID;
import dev.everydaythings.graph.language.Language;
import dev.everydaythings.graph.language.Sememe;
import dev.everydaythings.graph.language.SemanticFrame;
import dev.everydaythings.graph.language.ThematicRole;
import dev.everydaythings.graph.runtime.Eval;
import dev.everydaythings.graph.runtime.LibrarianOld;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.ToIntFunction;
import java.util.regex.Pattern;

/**
 * Chess algebraic notation as a Language.
 *
 * <p>Recognizes all standard chess notation variants and produces MOVE frames.
 * This is the showcase implementation of domain-specific notation as a Language:
 * when a chess game is focused and the user types "e4", the token resolves via
 * chess-notation scope, the language inference detects chess notation is active,
 * and this parser produces a MOVE frame.
 *
 * <p>Supported notation variants:
 * <ul>
 *   <li><b>SAN</b> (Standard Algebraic): e4, Nf3, Bxe5+, Qd1#, e8=Q</li>
 *   <li><b>Long Algebraic</b>: e2e4, Ng1f3, Bb5xc6</li>
 *   <li><b>UCI</b>: e2e4, g1f3, e7e8q (promotion lowercase)</li>
 *   <li><b>Castling</b>: O-O, O-O-O, 0-0, 0-0-0</li>
 *   <li><b>Captures</b>: exd5, Bxe5, Nxf7</li>
 *   <li><b>Promotions</b>: e8=Q, e8Q, e7e8q</li>
 *   <li><b>Disambiguation</b>: Rae1, R1e1, Raxe1</li>
 *   <li><b>Check/checkmate</b>: +, #, ++</li>
 *   <li><b>Move numbers</b>: 1. e4 e5 2. Nf3 (numbers stripped)</li>
 *   <li><b>Annotations</b>: !, ?, !!, ??, !?, ?! (stripped)</li>
 *   <li><b>Descriptive (English)</b>: P-K4, N-KB3, PxP, B-N5, QxBP, O-O</li>
 *   <li><b>ICCF numeric</b>: 5254, 7163 (column+row pairs, 4 digits)</li>
 *   <li><b>Figurine algebraic</b>: ♔, ♕, ♖, ♗, ♘ used in place of K, Q, R, B, N</li>
 *   <li><b>Coordinate</b>: e2-e4 (with hyphen separator)</li>
 * </ul>
 *
 * <p>ChessNotation does NOT validate move legality — that is the job of
 * {@link ChessItem#move(String)}. This language only recognizes the syntax
 * and produces frames.
 */
@Implements(ChessNotation.KEY)
@ItemSeed(key = ChessNotation.KEY)
public class ChessNotation extends Language {

    public static final String KEY = "cg:language/chess-notation";
    public static final ItemID IID = ItemID.fromString(KEY);

    // ==================================================================================
    // Notation patterns
    // ==================================================================================

    /** Files: a-h */
    private static final String FILE = "[a-h]";

    /** Ranks: 1-8 */
    private static final String RANK = "[1-8]";

    /** Piece letters (no pawn — pawn moves start with file or are just a square) */
    private static final String PIECE = "[KQRBN]";

    /** Square: file + rank */
    private static final String SQUARE = FILE + RANK;

    /** Promotion piece */
    private static final String PROMO = "[QRBNqrbn]";

    // SAN: Piece disambiguation? capture? square promotion? check?
    // Examples: e4, Nf3, Bxe5, Rae1, R1e1, exd5, e8=Q, Nf3+, Qxd7#
    private static final Pattern SAN_MOVE = Pattern.compile(
            "^(" + PIECE + ")?(" + FILE + ")?(" + RANK + ")?(x)?(" + SQUARE + ")(=" + PROMO + "|" + PROMO + ")?([+#]{1,2})?$"
    );

    // Long algebraic / UCI: source-square destination-square promotion?
    // Examples: e2e4, g1f3, e7e8q, Ng1f3 (with optional piece prefix)
    private static final Pattern LONG_ALG = Pattern.compile(
            "^(" + PIECE + ")?(" + SQUARE + ")" + "x?(" + SQUARE + ")(" + PROMO + ")?([+#]{1,2})?$"
    );

    // Castling: O-O, O-O-O, 0-0, 0-0-0 (with optional check)
    private static final Pattern CASTLING = Pattern.compile(
            "^([Oo0]-[Oo0](-[Oo0])?)([+#]{1,2})?$"
    );

    // Coordinate with hyphen: e2-e4, Nf3-e5
    private static final Pattern COORDINATE = Pattern.compile(
            "^(" + PIECE + ")?(" + SQUARE + ")-(" + SQUARE + ")(" + PROMO + ")?([+#]{1,2})?$"
    );

    // ICCF numeric: 4 digits — column+row pairs (1=a, 2=b, ... 8=h; 1-8 for rank)
    // Examples: 5254 (e2-e4), 7163 (g1-f3)
    private static final Pattern ICCF_NUMERIC = Pattern.compile(
            "^[1-8]{4}$"
    );

    // English Descriptive Notation: P-K4, N-KB3, PxP, B-N5, QxBP, R(1)-K1
    // Pieces: K, Q, R, B, N, P
    // Squares: K1-K8, Q1-Q8, KB1-KB8, QN1-QN8, KR1-KR8, etc.
    private static final String DESC_PIECE = "[KQRBNP]";
    private static final String DESC_SQUARE = "(?:K?[QKRBN]?[1-8])";
    // Descriptive: Piece[-x]Square or Piece[-x]Piece (PxP, QxBP, NxN)
    private static final String DESC_TARGET = "(?:" + DESC_SQUARE + "|" + DESC_PIECE + "{1,2})";
    private static final Pattern DESCRIPTIVE = Pattern.compile(
            "^" + DESC_PIECE + "(?:\\([KQ12]\\))?[-x]" + DESC_TARGET + "(?:/" + DESC_PIECE + ")?([+#]{1,2})?$"
    );

    // Figurine algebraic: Unicode chess pieces used in place of letters
    // ♔♕♖♗♘ (white) or ♚♛♜♝♞ (black) followed by standard SAN
    private static final String FIGURINE = "[♔♕♖♗♘♚♛♜♝♞]";
    private static final Pattern FIGURINE_SAN = Pattern.compile(
            "^" + FIGURINE + "(" + FILE + ")?(" + RANK + ")?(x)?(" + SQUARE + ")(=" + PROMO + ")?([+#]{1,2})?$"
    );

    // Move number prefix: "1." or "1..." or "23." — stripped before parsing
    private static final Pattern MOVE_NUMBER = Pattern.compile(
            "^\\d+\\.{1,3}$"
    );

    // Annotation suffix: !, ?, !!, ??, !?, ?!
    private static final Pattern ANNOTATION = Pattern.compile(
            "^[!?]{1,2}$"
    );

    // ==================================================================================
    // Constructors
    // ==================================================================================

    /** Seed/type constructor. */
    @SuppressWarnings("unused")
    public ChessNotation(ItemID typeId) {
        super(typeId);
    }

    /** Runtime constructor. */
    public ChessNotation(LibrarianOld librarian) {
        super(librarian, Locale.ROOT);
    }

    /** Hydration constructor. */
    @SuppressWarnings("unused")
    private ChessNotation(LibrarianOld librarian, ManifestOld manifest) {
        super(librarian, manifest);
    }

    // ==================================================================================
    // Parsing
    // ==================================================================================

    /**
     * Parse resolved tokens as chess notation.
     *
     * <p>Each token that matches a chess move pattern becomes a MOVE frame.
     * Move numbers and annotations are silently consumed. Unrecognized
     * tokens are left for the caller to handle.
     */
    @Override
    public ParseResult parse(
            List<Eval.ResolvedToken> tokens,
            String rawText,
            Function<ItemID, Optional<ItemOld>> resolver,
            ToIntFunction<Sememe> headVerbScorer) {

        List<SemanticFrame> frames = new ArrayList<>();
        List<Eval.ResolvedToken> unbound = new ArrayList<>();

        for (Eval.ResolvedToken token : tokens) {
            String text = tokenText(token);
            if (text == null || text.isBlank()) continue;

            // Skip move numbers (1. or 1...)
            if (MOVE_NUMBER.matcher(text).matches()) continue;

            // Skip annotations (!, ?, !!, etc.)
            if (ANNOTATION.matcher(text).matches()) continue;

            // Try to recognize as a chess move
            if (isChessMove(text)) {
                frames.add(moveFrame(text));
            } else {
                unbound.add(token);
            }
        }

        return new ParseResult(frames, unbound);
    }

    /**
     * Test whether a string looks like chess notation.
     *
     * <p>This is a syntax check only — does not validate legality.
     * Used both by parse() and by external callers that need to
     * detect chess notation in mixed input.
     */
    public static boolean isChessMove(String text) {
        if (text == null || text.isEmpty()) return false;

        // Strip trailing annotations for matching
        String clean = text.replaceAll("[!?]+$", "");
        if (clean.isEmpty()) return false;

        return CASTLING.matcher(clean).matches()
                || SAN_MOVE.matcher(clean).matches()
                || LONG_ALG.matcher(clean).matches()
                || COORDINATE.matcher(clean).matches()
                || ICCF_NUMERIC.matcher(clean).matches()
                || DESCRIPTIVE.matcher(clean).matches()
                || FIGURINE_SAN.matcher(clean).matches();
    }

    /**
     * Build a MOVE SemanticFrame from a notation string.
     *
     * <p>The SAN string goes into the NAME role — ChessGame.move(String san)
     * reads it from there and delegates to chesslib for validation.
     */
    private static SemanticFrame moveFrame(String notation) {
        Map<ItemID, Object> bindings = new LinkedHashMap<>();
        bindings.put(ThematicRole.Value.IID, notation);
        return new SemanticFrame(
                new Sememe(GameVocabulary.Move.KEY),
                bindings,
                Map.of(),
                List.of(),
                List.of());
    }

    private static String tokenText(Eval.ResolvedToken token) {
        return switch (token) {
            case Eval.ResolvedToken.Link link -> link.originalToken();
            case Eval.ResolvedToken.Literal lit -> String.valueOf(lit.value());
            case Eval.ResolvedToken.Unresolved u -> u.token();
        };
    }
}
