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
 * Visual qualities — sememes naming properties of how something appears.
 *
 * <p>These are binding-role qualities used on scene nodes, items, and other
 * things with visual presentation:
 *
 * <pre>
 * Body[head = ContainerNode]
 *   Background → @cg.color:white       // a Color value or palette-slot reference
 *   Foreground → @cg.color:black
 *   Opacity    → Quantity{Value = 0.8}
 *   Visibility → @cg.visibility:hidden
 * </pre>
 *
 * <p>The Color archetype, named-color sememes (White, Black, ...), and the
 * R/G/B/A and H/S/L channel sememes all live in {@code value/Color.java}.
 * This file holds only the visual binding-role qualities that typically
 * TARGET color values, plus modulation qualities (Opacity, Visibility, Blur).
 */
public final class VisualVocabulary {

    private VisualVocabulary() {}

    // ==================================================================================
    // Color binding-role qualities (target a Color value or palette slot)
    // ==================================================================================

    /** The foreground / text color of something. */
    @Seed.Item(key = Foreground.KEY, head = CoreVocabulary.Quality.KEY)
    public static final class Foreground {
        public static final String KEY = "cg.quality:foreground";
        private Foreground() {}

        @Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss = "the foreground (typically text) color of something";

        @Frame(predicate = LexicalVocabulary.Lexeme.KEY,
          field = @Binding(role = ThematicRole.Value.KEY,
            qualifiers = {Language.English.KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String englishNounLemma = "foreground";
    }

    /** The background / fill color of something. */
    @Seed.Item(key = Background.KEY, head = CoreVocabulary.Quality.KEY)
    public static final class Background {
        public static final String KEY = "cg.quality:background";
        private Background() {}

        @Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss = "the background (fill) color of something";

        @Frame(predicate = LexicalVocabulary.Lexeme.KEY,
          field = @Binding(role = ThematicRole.Value.KEY,
            qualifiers = {Language.English.KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String englishNounLemma = "background";
    }

    /** The border color of something. */
    @Seed.Item(key = BorderColor.KEY, head = CoreVocabulary.Quality.KEY)
    public static final class BorderColor {
        public static final String KEY = "cg.quality:border-color";
        private BorderColor() {}

        @Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss = "the border color of something";
    }

    // ==================================================================================
    // Visual modulation qualities
    // ==================================================================================

    /**
     * The opacity of something — a dimensionless Quantity between 0 and 1,
     * where 0 is fully transparent and 1 is fully opaque.
     */
    @Seed.Item(key = Opacity.KEY, head = CoreVocabulary.Quality.KEY)
    public static final class Opacity {
        public static final String KEY = "cg.quality:opacity";
        private Opacity() {}

        @Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss = "opacity from 0 (transparent) to 1 (opaque)";

        @Frame(predicate = LexicalVocabulary.Lexeme.KEY,
          field = @Binding(role = ThematicRole.Value.KEY,
            qualifiers = {Language.English.KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String englishNounLemma = "opacity";
    }

    /** Whether something is visible. Target is typically a boolean or visibility sememe. */
    @Seed.Item(key = Visibility.KEY, head = CoreVocabulary.Quality.KEY)
    public static final class Visibility {
        public static final String KEY = "cg.quality:visibility";
        private Visibility() {}

        @Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss = "whether something is visible";

        @Frame(predicate = LexicalVocabulary.Lexeme.KEY,
          field = @Binding(role = ThematicRole.Value.KEY,
            qualifiers = {Language.English.KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String englishNounLemma = "visibility";
    }

    /** Blur applied to something — typically a Length-Quantity (blur radius). */
    @Seed.Item(key = Blur.KEY, head = CoreVocabulary.Quality.KEY)
    public static final class Blur {
        public static final String KEY = "cg.quality:blur";
        private Blur() {}

        @Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss = "a blur applied to something, typically a length (blur radius)";

        @Frame(predicate = LexicalVocabulary.Lexeme.KEY,
          field = @Binding(role = ThematicRole.Value.KEY,
            qualifiers = {Language.English.KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String englishNounLemma = "blur";
    }

}
