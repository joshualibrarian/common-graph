package dev.everydaythings.graph.quality;

import dev.everydaythings.graph.CoreVocabulary;
import dev.everydaythings.graph.Seed;
import dev.everydaythings.graph.GrammaticalFeature;
import dev.everydaythings.graph.language.Language;
import dev.everydaythings.graph.language.LexicalVocabulary;
import dev.everydaythings.graph.PartOfSpeech;
import dev.everydaythings.graph.ThematicRole;

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

    // ==================================================================================
    // Border — per-side width and style.  Compound with the Side qualifier
    // ({@link dev.everydaythings.graph.quality.LayoutVocabulary.Side}) to
    // address one edge: {@code BorderWidth[Top]}, {@code BorderStyle[Left]}, etc.
    // {@link BorderColor} (above) takes the same compound treatment.
    // ==================================================================================

    /** The width of a border on a side of a box.  Compound with a Side qualifier. */
    @Seed.Item(key = BorderWidth.KEY, head = CoreVocabulary.Quality.KEY)
    public static final class BorderWidth {
        public static final String KEY = "cg.quality:border-width";
        private BorderWidth() {}

        @Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss = "the width of a border on a side of a box";
    }

    /**
     * The style of a border on a side of a box.  Compound with a Side
     * qualifier.  Target is one of {@link Solid} / {@link Dashed} /
     * {@link Dotted} / {@link NoBorder}.
     */
    @Seed.Item(key = BorderStyle.KEY, head = CoreVocabulary.Quality.KEY)
    public static final class BorderStyle {
        public static final String KEY = "cg.quality:border-style";
        private BorderStyle() {}

        @Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss = "the style of a border on a side of a box";
    }

    /** Solid border style — continuous unbroken line. */
    @Seed.Item(key = Solid.KEY)
    public static final class Solid {
        public static final String KEY = "cg.border-style:solid";
        private Solid() {}

        @Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss = "continuous unbroken border line";

        @Frame(predicate = LexicalVocabulary.Lexeme.KEY,
          field = @Binding(role = ThematicRole.Value.KEY,
            qualifiers = {Language.English.KEY, PartOfSpeech.Adjective.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String englishAdjectiveLemma = "solid";
    }

    /** Dashed border style — short line segments with gaps. */
    @Seed.Item(key = Dashed.KEY)
    public static final class Dashed {
        public static final String KEY = "cg.border-style:dashed";
        private Dashed() {}

        @Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss = "short line segments with gaps";

        @Frame(predicate = LexicalVocabulary.Lexeme.KEY,
          field = @Binding(role = ThematicRole.Value.KEY,
            qualifiers = {Language.English.KEY, PartOfSpeech.Adjective.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String englishAdjectiveLemma = "dashed";
    }

    /** Dotted border style — round dots with gaps. */
    @Seed.Item(key = Dotted.KEY)
    public static final class Dotted {
        public static final String KEY = "cg.border-style:dotted";
        private Dotted() {}

        @Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss = "round dots with gaps";

        @Frame(predicate = LexicalVocabulary.Lexeme.KEY,
          field = @Binding(role = ThematicRole.Value.KEY,
            qualifiers = {Language.English.KEY, PartOfSpeech.Adjective.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String englishAdjectiveLemma = "dotted";
    }

    /** No border — the side has no rendered border. */
    @Seed.Item(key = NoBorder.KEY)
    public static final class NoBorder {
        public static final String KEY = "cg.border-style:none";
        private NoBorder() {}

        @Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss = "no border — the side has no rendered border";
    }

    // ==================================================================================
    // Background image and gradient.
    // ==================================================================================

    /**
     * BackgroundImage — image painted behind content.  Target is typically
     * a reference to an image resource (SVG, PNG, JPEG, etc.) or a binding
     * expression that resolves to one.
     */
    @Seed.Item(key = BackgroundImage.KEY, head = CoreVocabulary.Quality.KEY)
    public static final class BackgroundImage {
        public static final String KEY = "cg.quality:background-image";
        private BackgroundImage() {}

        @Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss = "an image painted behind content";
    }

    /**
     * BackgroundSize — how a background image is sized within the
     * element's bounds.  Target is one of {@link FillSize} /
     * {@link CoverSize} / {@link ContainSize} / {@link NaturalSize}, or a
     * length-quantity for explicit sizing.
     */
    @Seed.Item(key = BackgroundSize.KEY, head = CoreVocabulary.Quality.KEY)
    public static final class BackgroundSize {
        public static final String KEY = "cg.quality:background-size";
        private BackgroundSize() {}

        @Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss = "how a background image is sized within the element's bounds";
    }

    /** Stretch the background image to fill the bounds, ignoring aspect. */
    @Seed.Item(key = FillSize.KEY)
    public static final class FillSize {
        public static final String KEY = "cg.background-size:fill";
        private FillSize() {}

        @Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss =
                "stretch the background image to fill the bounds, ignoring aspect ratio";
    }

    /** Scale uniformly to cover the bounds; aspect preserved, may crop. */
    @Seed.Item(key = CoverSize.KEY)
    public static final class CoverSize {
        public static final String KEY = "cg.background-size:cover";
        private CoverSize() {}

        @Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss =
                "scale uniformly to cover the bounds; aspect preserved, may crop";
    }

    /** Scale uniformly to fit within the bounds; aspect preserved, may letterbox. */
    @Seed.Item(key = ContainSize.KEY)
    public static final class ContainSize {
        public static final String KEY = "cg.background-size:contain";
        private ContainSize() {}

        @Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss =
                "scale uniformly to fit within the bounds; aspect preserved, may letterbox";
    }

    /** Use the image's natural pixel size; no scaling. */
    @Seed.Item(key = NaturalSize.KEY)
    public static final class NaturalSize {
        public static final String KEY = "cg.background-size:natural";
        private NaturalSize() {}

        @Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss = "use the image's natural pixel size; no scaling";
    }

    /**
     * BackgroundGradient — a gradient painted behind content (over the
     * background color, under the background image).  Target is a
     * {@code Gradient} value (LinearGradient or RadialGradient).
     */
    @Seed.Item(key = BackgroundGradient.KEY, head = CoreVocabulary.Quality.KEY)
    public static final class BackgroundGradient {
        public static final String KEY = "cg.quality:background-gradient";
        private BackgroundGradient() {}

        @Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss = "a gradient painted behind content";
    }
}
