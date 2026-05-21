package dev.everydaythings.graph.quality;

import dev.everydaythings.graph.CoreVocabulary;
import dev.everydaythings.graph.Seed;
import dev.everydaythings.graph.language.GrammaticalFeature;
import dev.everydaythings.graph.language.Language;
import dev.everydaythings.graph.language.LexicalVocabulary;
import dev.everydaythings.graph.language.PartOfSpeech;
import dev.everydaythings.graph.language.ThematicRole;

import static dev.everydaythings.graph.Seed.*;

/**
 * Typography qualities and variables — sememes naming properties of text
 * rendering.
 *
 * <p>The qualities here are used as binding-roles on text-bearing scene nodes
 * and items:
 *
 * <pre>
 * Body[head = TextNode]
 *   FontSize   → Quantity{Value = 16, @Pixel = 1}
 *   FontWeight → @cg.font-weight:bold
 *   LineHeight → Quantity{Value = 1.5}
 * </pre>
 *
 * <p>{@link BaseFontSize} is the one Variable in this file — a runtime-bound
 * value supplied by the typography scope (the surrounding text container's
 * current font size).
 */
public final class TypographyVocabulary {

    private TypographyVocabulary() {}

    // ==================================================================================
    // Font properties
    // ==================================================================================

    /** The size of text — typically a Length-Quantity (px, em, pt). */
    @Seed.Item(key = FontSize.KEY, head = CoreVocabulary.Quality.KEY)
    public static final class FontSize {
        public static final String KEY = "cg.quality:font-size";
        private FontSize() {}

        @Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss = "the size of text, typically a length quantity";

        @Frame(predicate = LexicalVocabulary.Lexeme.KEY,
          field = @Binding(role = ThematicRole.Value.KEY,
            qualifiers = {Language.English.KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String englishNounLemma = "font size";
    }

    /** The weight (boldness) of text. Target is typically a weight sememe (Regular, Bold, Light, etc.) or a numeric weight. */
    @Seed.Item(key = FontWeight.KEY, head = CoreVocabulary.Quality.KEY)
    public static final class FontWeight {
        public static final String KEY = "cg.quality:font-weight";
        private FontWeight() {}

        @Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss = "the weight (boldness) of text";

        @Frame(predicate = LexicalVocabulary.Lexeme.KEY,
          field = @Binding(role = ThematicRole.Value.KEY,
            qualifiers = {Language.English.KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String englishNounLemma = "font weight";
    }

    /** The style of text (italic, oblique, normal). */
    @Seed.Item(key = FontStyle.KEY, head = CoreVocabulary.Quality.KEY)
    public static final class FontStyle {
        public static final String KEY = "cg.quality:font-style";
        private FontStyle() {}

        @Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss = "the style of text (italic, oblique, normal)";

        @Frame(predicate = LexicalVocabulary.Lexeme.KEY,
          field = @Binding(role = ThematicRole.Value.KEY,
            qualifiers = {Language.English.KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String englishNounLemma = "font style";
    }

    /** The font family — typically a name or font-item reference. */
    @Seed.Item(key = FontFamily.KEY, head = CoreVocabulary.Quality.KEY)
    public static final class FontFamily {
        public static final String KEY = "cg.quality:font-family";
        private FontFamily() {}

        @Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss = "the font family";

        @Frame(predicate = LexicalVocabulary.Lexeme.KEY,
          field = @Binding(role = ThematicRole.Value.KEY,
            qualifiers = {Language.English.KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String englishNounLemma = "font family";
    }

    // ==================================================================================
    // Line and inter-character spacing
    // ==================================================================================

    /** The height of each line of text — a dimensionless ratio or a length. */
    @Seed.Item(key = LineHeight.KEY, head = CoreVocabulary.Quality.KEY)
    public static final class LineHeight {
        public static final String KEY = "cg.quality:line-height";
        private LineHeight() {}

        @Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss = "the line height — either a ratio or an explicit length";
    }

    /** Additional space between characters. Typically a length-quantity. */
    @Seed.Item(key = LetterSpacing.KEY, head = CoreVocabulary.Quality.KEY)
    public static final class LetterSpacing {
        public static final String KEY = "cg.quality:letter-spacing";
        private LetterSpacing() {}

        @Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss = "extra space between characters";
    }

    /** Horizontal alignment of text within its container. */
    @Seed.Item(key = TextAlign.KEY, head = CoreVocabulary.Quality.KEY)
    public static final class TextAlign {
        public static final String KEY = "cg.quality:text-align";
        private TextAlign() {}

        @Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss = "horizontal alignment of text (left, center, right, justify)";
    }

    // ==================================================================================
    // Text decoration — underline, strike-through, overline.
    // ==================================================================================

    /**
     * TextDecoration — line decorations on text.  Target is one of
     * {@link Underline} / {@link LineThrough} / {@link Overline}, or a
     * list of them for combinations.
     */
    @Seed.Item(key = TextDecoration.KEY, head = CoreVocabulary.Quality.KEY)
    public static final class TextDecoration {
        public static final String KEY = "cg.quality:text-decoration";
        private TextDecoration() {}

        @Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss = "line decorations applied to text";

        @Frame(predicate = LexicalVocabulary.Lexeme.KEY,
          field = @Binding(role = ThematicRole.Value.KEY,
            qualifiers = {Language.English.KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String englishNounLemma = "text decoration";
    }

    /** Underline decoration — a line beneath the text. */
    @Seed.Item(key = Underline.KEY)
    public static final class Underline {
        public static final String KEY = "cg.text-decoration:underline";
        private Underline() {}

        @Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss = "a line beneath the text";

        @Frame(predicate = LexicalVocabulary.Lexeme.KEY,
          field = @Binding(role = ThematicRole.Value.KEY,
            qualifiers = {Language.English.KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String englishNounLemma = "underline";
    }

    /** LineThrough decoration — a line drawn through the middle of the text. */
    @Seed.Item(key = LineThrough.KEY)
    public static final class LineThrough {
        public static final String KEY = "cg.text-decoration:line-through";
        private LineThrough() {}

        @Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss = "a line drawn through the middle of the text";
    }

    /** Overline decoration — a line above the text. */
    @Seed.Item(key = Overline.KEY)
    public static final class Overline {
        public static final String KEY = "cg.text-decoration:overline";
        private Overline() {}

        @Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss = "a line above the text";

        @Frame(predicate = LexicalVocabulary.Lexeme.KEY,
          field = @Binding(role = ThematicRole.Value.KEY,
            qualifiers = {Language.English.KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String englishNounLemma = "overline";
    }

    // ==================================================================================
    // Text overflow and whitespace handling.
    // ==================================================================================

    /**
     * TextOverflow — how text exceeding its container's bounds is handled.
     * Target is one of {@link Ellipsis} / {@link Clip}.
     */
    @Seed.Item(key = TextOverflow.KEY, head = CoreVocabulary.Quality.KEY)
    public static final class TextOverflow {
        public static final String KEY = "cg.quality:text-overflow";
        private TextOverflow() {}

        @Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss = "how text exceeding its container's bounds is handled";

        @Frame(predicate = LexicalVocabulary.Lexeme.KEY,
          field = @Binding(role = ThematicRole.Value.KEY,
            qualifiers = {Language.English.KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String englishNounLemma = "text overflow";
    }

    /** Truncate overflowing text with an ellipsis (…). */
    @Seed.Item(key = Ellipsis.KEY)
    public static final class Ellipsis {
        public static final String KEY = "cg.text-overflow:ellipsis";
        private Ellipsis() {}

        @Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss = "truncate overflowing text with an ellipsis";

        @Frame(predicate = LexicalVocabulary.Lexeme.KEY,
          field = @Binding(role = ThematicRole.Value.KEY,
            qualifiers = {Language.English.KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String englishNounLemma = "ellipsis";
    }

    /** Clip overflowing text at the container's edge with no marker. */
    @Seed.Item(key = Clip.KEY)
    public static final class Clip {
        public static final String KEY = "cg.text-overflow:clip";
        private Clip() {}

        @Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss = "clip overflowing text at the container's edge with no marker";

        @Frame(predicate = LexicalVocabulary.Lexeme.KEY,
          field = @Binding(role = ThematicRole.Value.KEY,
            qualifiers = {Language.English.KEY, PartOfSpeech.Verb.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String englishVerbLemma = "clip";
    }

    /**
     * WhiteSpace — how whitespace and newlines in source text are
     * preserved on render.  Target is one of
     * {@link NormalWhitespace} / {@link NoWrap} / {@link Pre}
     * / {@link PreWrap}.
     */
    @Seed.Item(key = WhiteSpace.KEY, head = CoreVocabulary.Quality.KEY)
    public static final class WhiteSpace {
        public static final String KEY = "cg.quality:white-space";
        private WhiteSpace() {}

        @Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss =
                "how whitespace and newlines in source text are preserved on render";

        @Frame(predicate = LexicalVocabulary.Lexeme.KEY,
          field = @Binding(role = ThematicRole.Value.KEY,
            qualifiers = {Language.English.KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String englishNounLemma = "white space";
    }

    /** Collapse runs of whitespace; wrap at word boundaries. */
    @Seed.Item(key = NormalWhitespace.KEY)
    public static final class NormalWhitespace {
        public static final String KEY = "cg.white-space:normal";
        private NormalWhitespace() {}

        @Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss = "collapse runs of whitespace; wrap at word boundaries";
    }

    /** Collapse whitespace, but do not wrap — text stays on one line. */
    @Seed.Item(key = NoWrap.KEY)
    public static final class NoWrap {
        public static final String KEY = "cg.white-space:nowrap";
        private NoWrap() {}

        @Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss = "collapse whitespace, but do not wrap";
    }

    /** Preserve whitespace and newlines exactly; no wrapping. */
    @Seed.Item(key = Pre.KEY)
    public static final class Pre {
        public static final String KEY = "cg.white-space:pre";
        private Pre() {}

        @Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss = "preserve whitespace and newlines exactly; no wrapping";
    }

    /** Preserve whitespace and newlines exactly, but wrap at word boundaries. */
    @Seed.Item(key = PreWrap.KEY)
    public static final class PreWrap {
        public static final String KEY = "cg.white-space:pre-wrap";
        private PreWrap() {}

        @Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss =
                "preserve whitespace and newlines exactly, but wrap at word boundaries";
    }

    // ==================================================================================
    // Variables — resolution-context-bound typography values
    // ==================================================================================

    /**
     * The current font size, bound by the resolution context. Used as the
     * equivalent-in-base for the {@code em} unit: one em equals one
     * BaseFontSize.
     *
     * <p>Different scopes bind different BaseFontSize values — a heading
     * scope might bind 24px while body text binds 16px. {@code 1em} means
     * different lengths in different scopes even within the same scene.
     */
    @Seed.Item(key = BaseFontSize.KEY, head = CoreVocabulary.Variable.KEY)
    public static final class BaseFontSize {
        public static final String KEY = "cg.variable:base-font-size";
        private BaseFontSize() {}

        @Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss =
                "the font size of the current typography scope, bound by the resolution "
                        + "context at render time";

        @Frame(predicate = LexicalVocabulary.Lexeme.KEY,
          field = @Binding(role = ThematicRole.Value.KEY,
            qualifiers = {Language.English.KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String englishNounLemma = "base font size";
    }

    /**
     * The font size of the <i>root</i> typography scope — the outermost
     * scene's font size.  Used as the equivalent-in-base for the
     * {@code rem} unit: one rem equals one RootFontSize, unaffected by
     * nested scope changes that might rebind BaseFontSize.
     */
    @Seed.Item(key = RootFontSize.KEY, head = CoreVocabulary.Variable.KEY)
    public static final class RootFontSize {
        public static final String KEY = "cg.variable:root-font-size";
        private RootFontSize() {}

        @Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss =
                "the font size of the root typography scope (the outermost scene); bound "
                        + "by the resolution context at render time and unaffected by "
                        + "nested-scope BaseFontSize changes";

        @Frame(predicate = LexicalVocabulary.Lexeme.KEY,
          field = @Binding(role = ThematicRole.Value.KEY,
            qualifiers = {Language.English.KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String englishNounLemma = "root font size";
    }
}
