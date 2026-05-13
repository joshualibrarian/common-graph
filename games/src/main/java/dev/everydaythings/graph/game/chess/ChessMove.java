package dev.everydaythings.graph.game.chess;

import dev.everydaythings.graph.datum.Binding;
import dev.everydaythings.graph.frame.FrameBodyOld;
import dev.everydaythings.graph.frame.eval.ParseContext;
import dev.everydaythings.graph.frame.eval.ParseContribution;
import dev.everydaythings.graph.game.GameVocabulary;
import dev.everydaythings.graph.Implements;
import dev.everydaythings.graph.item.ItemSeed;
import dev.everydaythings.graph.item.id.ItemID;
import dev.everydaythings.graph.language.CoreVocabulary;
import dev.everydaythings.graph.language.Sememe;
import dev.everydaythings.graph.language.ThematicRole;

import java.util.List;

/**
 * The CHESS_MOVE predicate — a specialized move sememe for chess.
 *
 * <p>CHESS_MOVE is a hyponym of MOVE (more specific). Its {@link #contribute}
 * method delegates parsing to {@link ChessNotation}, enabling chess algebraic
 * notation to be typed directly into a chess game's prompt.
 *
 * <p>When a chess game is focused, tokens like "e4" or "Nf3" resolve through
 * the vocabulary. The evaluator infers chess notation is active (from posting
 * scopes). CHESS_MOVE's contribute() says "delegate to ChessNotation." The
 * notation parser produces MOVE frames. The chess game executes them.
 *
 * <p>This is the showcase of domain-specific notation as a Language:
 * <pre>
 *   ┌──────────────────────────────────────────────────────────┐
 *   │ e4 Nf3 d5                                            ♞  │
 *   └──────────────────────────────────────────────────────────┘
 * </pre>
 */
@Implements(ChessMove.KEY)
@ItemSeed(key = ChessMove.KEY)
public class ChessMove extends Sememe {

    public static final String KEY = "cg.game:chess-move";
    public static final ItemID IID = ItemID.fromString(KEY);

    ChessMove() {
        super(KEY);
        gloss("en", "a chess move in algebraic notation");
        endorseFrame(new FrameBodyOld(CoreVocabulary.Expects.IID,
                List.of(Binding.ref(ThematicRole.Topic.IID,
                        ItemID.fromString(GameVocabulary.Move.KEY)))));
    }

    /**
     * Delegate parsing to ChessNotation — the chess notation language.
     *
     * <p>When the parser encounters this predicate, it hands the remaining
     * tokens to ChessNotation.parse(), which recognizes SAN, long algebraic,
     * UCI, descriptive, ICCF numeric, figurine, and coordinate notation.
     */
    @Override
    public ParseContribution contribute(ParseContext context) {
        return ParseContribution.delegateTo(new ChessNotation(ChessNotation.IID));
    }
}
