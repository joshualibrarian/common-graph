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

    /** The minimum horizontal extent — lower bound on width. */
    @Seed.Item(key = MinWidth.KEY, head = CoreVocabulary.Quality.KEY)
    public static final class MinWidth {
        public static final String KEY = "cg.quality:min-width";
        private MinWidth() {}

        @Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss = "the minimum horizontal extent — lower bound on width";
    }

    /** The maximum horizontal extent — upper bound on width. */
    @Seed.Item(key = MaxWidth.KEY, head = CoreVocabulary.Quality.KEY)
    public static final class MaxWidth {
        public static final String KEY = "cg.quality:max-width";
        private MaxWidth() {}

        @Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss = "the maximum horizontal extent — upper bound on width";
    }

    /** The minimum vertical extent — lower bound on height. */
    @Seed.Item(key = MinHeight.KEY, head = CoreVocabulary.Quality.KEY)
    public static final class MinHeight {
        public static final String KEY = "cg.quality:min-height";
        private MinHeight() {}

        @Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss = "the minimum vertical extent — lower bound on height";
    }

    /** The maximum vertical extent — upper bound on height. */
    @Seed.Item(key = MaxHeight.KEY, head = CoreVocabulary.Quality.KEY)
    public static final class MaxHeight {
        public static final String KEY = "cg.quality:max-height";
        private MaxHeight() {}

        @Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss = "the maximum vertical extent — upper bound on height";
    }

    /** The minimum front-to-back extent — lower bound on depth. */
    @Seed.Item(key = MinDepth.KEY, head = CoreVocabulary.Quality.KEY)
    public static final class MinDepth {
        public static final String KEY = "cg.quality:min-depth";
        private MinDepth() {}

        @Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss = "the minimum front-to-back extent — lower bound on depth";
    }

    /** The maximum front-to-back extent — upper bound on depth. */
    @Seed.Item(key = MaxDepth.KEY, head = CoreVocabulary.Quality.KEY)
    public static final class MaxDepth {
        public static final String KEY = "cg.quality:max-depth";
        private MaxDepth() {}

        @Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss = "the maximum front-to-back extent — upper bound on depth";
    }

    /**
     * Elevation — how far a node rises above (positive) or sinks below
     * (negative) its parent surface.  In 3D, literal Z displacement.  In
     * 2D, drives drop shadows for positive values and inner shadows for
     * negative values.  In text, contributes to depth hints.
     */
    @Seed.Item(key = Elevation.KEY, head = CoreVocabulary.Quality.KEY)
    public static final class Elevation {
        public static final String KEY = "cg.quality:elevation";
        private Elevation() {}

        @Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss =
                "how far a node rises above or sinks below its parent surface; positive raises, "
                        + "negative recesses, zero is flush";

        @Frame(predicate = LexicalVocabulary.Lexeme.KEY,
          field = @Binding(role = ThematicRole.Value.KEY,
            qualifiers = {Language.English.KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String englishNounLemma = "elevation";
    }

    /**
     * Transform origin — the point around which rotation and scale apply.
     * Defaults to center.  Target is typically a Position value (or accepts
     * keyword positions like "top left", "50% 0%").
     */
    @Seed.Item(key = TransformOrigin.KEY, head = CoreVocabulary.Quality.KEY)
    public static final class TransformOrigin {
        public static final String KEY = "cg.quality:transform-origin";
        private TransformOrigin() {}

        @Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss =
                "the point around which rotation and scale apply; defaults to center";

        @Frame(predicate = LexicalVocabulary.Lexeme.KEY,
          field = @Binding(role = ThematicRole.Value.KEY,
            qualifiers = {Language.English.KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String englishNounLemma = "transform origin";
    }

    /**
     * Corner — qualifier identifying one of the four corners of a box.
     * Used as a compound qualifier on per-corner properties (typically
     * border radius / corner-rounding) to address one corner at a time.
     */
    @Seed.Item(key = Corner.KEY, head = CoreVocabulary.Quality.KEY)
    public static final class Corner {
        public static final String KEY = "cg.quality:corner";
        private Corner() {}

        @Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss =
                "one of the four corners of a box — used as a compound qualifier on per-corner "
                        + "properties (typically border radius)";

        @Frame(predicate = LexicalVocabulary.Lexeme.KEY,
          field = @Binding(role = ThematicRole.Value.KEY,
            qualifiers = {Language.English.KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String englishNounLemma = "corner";
    }

    /** The top-left corner. */
    @Seed.Item(key = TopLeft.KEY)
    public static final class TopLeft {
        public static final String KEY = "cg.corner:top-left";
        private TopLeft() {}

        @Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss = "the top-left corner of a box";
    }

    /** The top-right corner. */
    @Seed.Item(key = TopRight.KEY)
    public static final class TopRight {
        public static final String KEY = "cg.corner:top-right";
        private TopRight() {}

        @Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss = "the top-right corner of a box";
    }

    /** The bottom-left corner. */
    @Seed.Item(key = BottomLeft.KEY)
    public static final class BottomLeft {
        public static final String KEY = "cg.corner:bottom-left";
        private BottomLeft() {}

        @Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss = "the bottom-left corner of a box";
    }

    /** The bottom-right corner. */
    @Seed.Item(key = BottomRight.KEY)
    public static final class BottomRight {
        public static final String KEY = "cg.corner:bottom-right";
        private BottomRight() {}

        @Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss = "the bottom-right corner of a box";
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
