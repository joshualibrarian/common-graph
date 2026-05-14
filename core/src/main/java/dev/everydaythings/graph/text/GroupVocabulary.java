package dev.everydaythings.graph.text;

import dev.everydaythings.graph.Seed;
import dev.everydaythings.graph.id.ItemRef;
import dev.everydaythings.graph.language.Language;
import dev.everydaythings.graph.language.ThematicRole;
import dev.everydaythings.graph.language.LexicalVocabulary;

/**
 * Sememes for textual grouping markers — {@code (} and {@code )}.
 *
 * <p><b>Text-layer, not frame-layer.</b> Parens appear in the input string to
 * disambiguate operator precedence at the surface form, but they don't surface
 * <i>in</i> the resulting frame. The frame {@code MULTIPLY{ADD{5,3}, 2}} can be
 * entered as {@code (5+3)*2} (explicit parens necessary) but not as {@code 5+3*2}
 * (which is a different frame); conversely {@code 5+3*2} and {@code 5+(3*2)} parse
 * to the same frame — the redundant parens are notational, not semantic.
 *
 * <p>Accordingly, {@link OpenGroup} and {@link CloseGroup} are <i>thin</i> sememes:
 * they exist so {@code (} and {@code )} resolve to known IIDs in the token
 * dictionary, and that's it. They have no {@code parse(ctx)} override and no
 * runtime behavior of their own — they're recognition markers, not active
 * participants.
 *
 * <p>The actual rollup logic — "see {@code )} adjacent to me, walk back to {@code (},
 * recursively parse the bracketed text, use the result as a {@link FrameMapTarget}"
 * — lives in {@code Operator.parse}'s neighbor resolution. That helper consults
 * these sememes' IIDs to detect group boundaries; the recognition data is
 * vocabulary-driven, the iteration code is not.
 *
 * <p>The end result of explicit grouping is the same artifact as implicit
 * precedence-based grouping: a {@link FrameMapTarget} in the right binding slot.
 * Different input mechanisms, same frame-layer outcome.
 */
public final class GroupVocabulary {

    private GroupVocabulary() {}

    /**
     * The open-group sememe — the {@code (} marker. Opens a parenthesized
     * sub-expression in surface text. Has no role in the resulting frame.
     */
    @Seed.Item(key = OpenGroup.KEY)
    public static final class OpenGroup {
        public static final String KEY = "cg.notation:open-group";
        public static final ItemRef IID = ItemRef.fromString(KEY);
        private OpenGroup() {}

        @Seed.Frame(predicate = LexicalVocabulary.Lexeme.KEY)
        static final String symbol = "(";

        @Seed.Frame(predicate = LexicalVocabulary.Gloss.KEY,
                field = @Seed.Binding(role = ThematicRole.Value.KEY,
                        qualifiers = {Language.English.KEY}))
        static final String englishGloss =
                "the open-group marker — opens a parenthesized sub-expression in surface text";
    }

    /**
     * The close-group sememe — the {@code )} marker. Closes a parenthesized
     * sub-expression in surface text. Has no role in the resulting frame.
     */
    @Seed.Item(key = CloseGroup.KEY)
    public static final class CloseGroup {
        public static final String KEY = "cg.notation:close-group";
        public static final ItemRef IID = ItemRef.fromString(KEY);
        private CloseGroup() {}

        @Seed.Frame(predicate = LexicalVocabulary.Lexeme.KEY)
        static final String symbol = ")";

        @Seed.Frame(predicate = LexicalVocabulary.Gloss.KEY,
                field = @Seed.Binding(role = ThematicRole.Value.KEY,
                        qualifiers = {Language.English.KEY}))
        static final String englishGloss =
                "the close-group marker — closes a parenthesized sub-expression in surface text";
    }
}
