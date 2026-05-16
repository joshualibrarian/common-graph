package dev.everydaythings.graph.quality;

import dev.everydaythings.graph.CoreVocabulary;
import dev.everydaythings.graph.Seed;
import dev.everydaythings.graph.id.ItemRef;
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
}
