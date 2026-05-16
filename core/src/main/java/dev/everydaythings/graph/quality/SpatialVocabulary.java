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
 * Spatial qualities and variables — sememes naming properties of position,
 * extent, and orientation.
 *
 * <p>The qualities here are used as binding-roles to predicate properties on
 * scene nodes, physical objects, and other items with spatial structure:
 *
 * <pre>
 * Body[head = ContainerNode]
 *   Width   → Quantity{Value = 100, @Meter = 1}
 *   Height  → Quantity{Value = 50,  @Meter = 1}
 *   Padding → Quantity{Value = 8,   @Pixel = 1}
 * </pre>
 *
 * <p>Each Quality's target is typically a Length-Quantity (a value-body with
 * Length dimensions). EXPECTS declarations on each quality can constrain the
 * target type when wired up.
 *
 * <p>{@link DevicePixelSize} is the one Variable in this file — a runtime-bound
 * value supplied by the resolution context (the active display).
 */
public final class SpatialVocabulary {

    private SpatialVocabulary() {}

    // ==================================================================================
    // Linear extents — width / height / depth
    // ==================================================================================

    /** The horizontal extent of something. Target is typically a Length-Quantity. */
    @Seed.Item(key = Width.KEY, head = CoreVocabulary.Quality.KEY)
    public static final class Width {
        public static final String KEY = "cg.quality:width";
        private Width() {}

        @Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss = "the horizontal extent of something";

        @Frame(predicate = LexicalVocabulary.Lexeme.KEY,
          field = @Binding(role = ThematicRole.Value.KEY,
            qualifiers = {Language.English.KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String englishNounLemma = "width";
    }

    /** The vertical extent of something. */
    @Seed.Item(key = Height.KEY, head = CoreVocabulary.Quality.KEY)
    public static final class Height {
        public static final String KEY = "cg.quality:height";
        private Height() {}

        @Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss = "the vertical extent of something";

        @Frame(predicate = LexicalVocabulary.Lexeme.KEY,
          field = @Binding(role = ThematicRole.Value.KEY,
            qualifiers = {Language.English.KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String englishNounLemma = "height";
    }

    /** The depth (front-to-back extent) of something. */
    @Seed.Item(key = Depth.KEY, head = CoreVocabulary.Quality.KEY)
    public static final class Depth {
        public static final String KEY = "cg.quality:depth";
        private Depth() {}

        @Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss = "the front-to-back extent of something";

        @Frame(predicate = LexicalVocabulary.Lexeme.KEY,
          field = @Binding(role = ThematicRole.Value.KEY,
            qualifiers = {Language.English.KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String englishNounLemma = "depth";
    }

    // ==================================================================================
    // Spacing — padding / margin / border-thickness
    // ==================================================================================

    /** The space inside a container, between its border and its contents. */
    @Seed.Item(key = Padding.KEY, head = CoreVocabulary.Quality.KEY)
    public static final class Padding {
        public static final String KEY = "cg.quality:padding";
        private Padding() {}

        @Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss = "space inside a container, between its border and its contents";

        @Frame(predicate = LexicalVocabulary.Lexeme.KEY,
          field = @Binding(role = ThematicRole.Value.KEY,
            qualifiers = {Language.English.KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String englishNounLemma = "padding";
    }

    /** The space outside a container, between its border and surrounding elements. */
    @Seed.Item(key = Margin.KEY, head = CoreVocabulary.Quality.KEY)
    public static final class Margin {
        public static final String KEY = "cg.quality:margin";
        private Margin() {}

        @Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss = "space outside a container, between its border and surrounding elements";

        @Frame(predicate = LexicalVocabulary.Lexeme.KEY,
          field = @Binding(role = ThematicRole.Value.KEY,
            qualifiers = {Language.English.KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String englishNounLemma = "margin";
    }

    // ==================================================================================
    // Transformations — position / rotation / scale
    // ==================================================================================

    /** The position of something in its containing coordinate space. */
    @Seed.Item(key = Position.KEY, head = CoreVocabulary.Quality.KEY)
    public static final class Position {
        public static final String KEY = "cg.quality:position";
        private Position() {}

        @Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss = "the position of something in its containing space; typically a Point or Vector value";

        @Frame(predicate = LexicalVocabulary.Lexeme.KEY,
          field = @Binding(role = ThematicRole.Value.KEY,
            qualifiers = {Language.English.KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String englishNounLemma = "position";
    }

    /** A rotation around an axis, typically expressed as an angular Quantity. */
    @Seed.Item(key = Rotation.KEY, head = CoreVocabulary.Quality.KEY)
    public static final class Rotation {
        public static final String KEY = "cg.quality:rotation";
        private Rotation() {}

        @Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss = "a rotation, typically an angle Quantity";

        @Frame(predicate = LexicalVocabulary.Lexeme.KEY,
          field = @Binding(role = ThematicRole.Value.KEY,
            qualifiers = {Language.English.KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String englishNounLemma = "rotation";
    }

    /** A scaling factor — dimensionless ratio applied to size or other properties. */
    @Seed.Item(key = Scale.KEY, head = CoreVocabulary.Quality.KEY)
    public static final class Scale {
        public static final String KEY = "cg.quality:scale";
        private Scale() {}

        @Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss = "a scaling factor; typically a dimensionless ratio";

        @Frame(predicate = LexicalVocabulary.Lexeme.KEY,
          field = @Binding(role = ThematicRole.Value.KEY,
            qualifiers = {Language.English.KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String englishNounLemma = "scale";
    }

    // ==================================================================================
    // Variables — resolution-context-bound spatial values
    // ==================================================================================

    /**
     * The size of a physical device pixel, expressed as a real-world length.
     * Bound by the session at layout time based on the active display.
     *
     * <p>Used in unit-conversion expressions for {@code Pixel}: a pixel-valued
     * length only has a real-world equivalent once a particular display's pixel
     * size is known. The resolver substitutes the bound value when running
     * against a concrete viewport.
     */
    @Seed.Item(key = DevicePixelSize.KEY, head = CoreVocabulary.Variable.KEY)
    public static final class DevicePixelSize {
        public static final String KEY = "cg.variable:device-pixel-size";
        private DevicePixelSize() {}

        @Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss =
                "the physical size of a device pixel as a real-world length; bound by "
                        + "the session at layout time based on the active display";

        @Frame(predicate = LexicalVocabulary.Lexeme.KEY,
          field = @Binding(role = ThematicRole.Value.KEY,
            qualifiers = {Language.English.KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String englishNounLemma = "device pixel size";
    }
}
