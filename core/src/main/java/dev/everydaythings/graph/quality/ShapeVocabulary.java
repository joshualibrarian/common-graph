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
 * Shape vocabulary — sememes naming the kinds of geometric primitives a
 * Body-type scene node can represent, plus the qualities that color and
 * outline them.
 *
 * <p>The {@link Shape} quality on a body-shaped scene node names which
 * geometric primitive the body is.  Seven primitives cover the 2D / 3D
 * spectrum:
 *
 * <pre>
 * 2D:  Line, Circle, Rectangle
 * 3D:  Sphere, Box, Cylinder, Cone
 * </pre>
 *
 * <p>The shape qualities ({@link Fill}, {@link StrokeColor},
 * {@link StrokeWidth}, {@link Radius}, {@link Material}, {@link Surfaces})
 * are companion bindings on the same body, supplying color, outline, size
 * parameters, and (for 3D) PBR material references.
 */
public final class ShapeVocabulary {

    private ShapeVocabulary() {}

    // ==================================================================================
    // Shape quality — names the geometric primitive a body represents.
    // ==================================================================================

    /**
     * Shape — the geometric primitive a body-shaped scene node represents.
     * Target is one of {@link Line} / {@link Circle} / {@link Rectangle}
     * (2D) or {@link Sphere} / {@link Box} / {@link Cylinder} / {@link Cone}
     * (3D).
     */
    @Seed.Item(key = Shape.KEY, head = CoreVocabulary.Quality.KEY)
    public static final class Shape {
        public static final String KEY = "cg.quality:shape";
        private Shape() {}

        @Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss =
                "the geometric primitive a body-shaped scene node represents";

        @Frame(predicate = LexicalVocabulary.Lexeme.KEY,
          field = @Binding(role = ThematicRole.Value.KEY,
            qualifiers = {Language.English.KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String englishNounLemma = "shape";
    }

    // ==================================================================================
    // 2D shape instances.
    // ==================================================================================

    /** A straight line segment. */
    @Seed.Item(key = Line.KEY)
    public static final class Line {
        public static final String KEY = "cg.shape:line";
        private Line() {}

        @Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss = "a straight line segment";

        @Frame(predicate = LexicalVocabulary.Lexeme.KEY,
          field = @Binding(role = ThematicRole.Value.KEY,
            qualifiers = {Language.English.KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String englishNounLemma = "line";
    }

    /** A 2D circle defined by its radius. */
    @Seed.Item(key = Circle.KEY)
    public static final class Circle {
        public static final String KEY = "cg.shape:circle";
        private Circle() {}

        @Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss = "a 2D circle defined by its radius";

        @Frame(predicate = LexicalVocabulary.Lexeme.KEY,
          field = @Binding(role = ThematicRole.Value.KEY,
            qualifiers = {Language.English.KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String englishNounLemma = "circle";
    }

    /** A 2D rectangle defined by width and height (and optional corner radii). */
    @Seed.Item(key = Rectangle.KEY)
    public static final class Rectangle {
        public static final String KEY = "cg.shape:rectangle";
        private Rectangle() {}

        @Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss =
                "a 2D rectangle defined by width and height (and optional corner radii)";

        @Frame(predicate = LexicalVocabulary.Lexeme.KEY,
          field = @Binding(role = ThematicRole.Value.KEY,
            qualifiers = {Language.English.KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String englishNounLemma = "rectangle";
    }

    // ==================================================================================
    // 3D shape instances.
    // ==================================================================================

    /** A 3D sphere defined by its radius. */
    @Seed.Item(key = Sphere.KEY)
    public static final class Sphere {
        public static final String KEY = "cg.shape:sphere";
        private Sphere() {}

        @Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss = "a 3D sphere defined by its radius";

        @Frame(predicate = LexicalVocabulary.Lexeme.KEY,
          field = @Binding(role = ThematicRole.Value.KEY,
            qualifiers = {Language.English.KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String englishNounLemma = "sphere";
    }

    /** A 3D rectangular box defined by width, height, and depth. */
    @Seed.Item(key = Box.KEY)
    public static final class Box {
        public static final String KEY = "cg.shape:box";
        private Box() {}

        @Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss = "a 3D rectangular box defined by width, height, and depth";

        @Frame(predicate = LexicalVocabulary.Lexeme.KEY,
          field = @Binding(role = ThematicRole.Value.KEY,
            qualifiers = {Language.English.KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String englishNounLemma = "box";
    }

    /** A 3D cylinder defined by radius and height. */
    @Seed.Item(key = Cylinder.KEY)
    public static final class Cylinder {
        public static final String KEY = "cg.shape:cylinder";
        private Cylinder() {}

        @Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss = "a 3D cylinder defined by radius and height";

        @Frame(predicate = LexicalVocabulary.Lexeme.KEY,
          field = @Binding(role = ThematicRole.Value.KEY,
            qualifiers = {Language.English.KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String englishNounLemma = "cylinder";
    }

    /** A 3D cone defined by base radius and height. */
    @Seed.Item(key = Cone.KEY)
    public static final class Cone {
        public static final String KEY = "cg.shape:cone";
        private Cone() {}

        @Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss = "a 3D cone defined by base radius and height";

        @Frame(predicate = LexicalVocabulary.Lexeme.KEY,
          field = @Binding(role = ThematicRole.Value.KEY,
            qualifiers = {Language.English.KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String englishNounLemma = "cone";
    }

    // ==================================================================================
    // Shape-specific qualities — color, outline, dimensional parameters, material.
    // ==================================================================================

    /** Fill — the interior color of a shape.  Target is a Color value (or palette slot). */
    @Seed.Item(key = Fill.KEY, head = CoreVocabulary.Quality.KEY)
    public static final class Fill {
        public static final String KEY = "cg.quality:fill";
        private Fill() {}

        @Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss = "the interior color of a shape";

        @Frame(predicate = LexicalVocabulary.Lexeme.KEY,
          field = @Binding(role = ThematicRole.Value.KEY,
            qualifiers = {Language.English.KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String englishNounLemma = "fill";
    }

    /** StrokeColor — the outline color of a shape. */
    @Seed.Item(key = StrokeColor.KEY, head = CoreVocabulary.Quality.KEY)
    public static final class StrokeColor {
        public static final String KEY = "cg.quality:stroke-color";
        private StrokeColor() {}

        @Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss = "the outline color of a shape";
    }

    /** StrokeWidth — the thickness of a shape's outline.  Length-quantity. */
    @Seed.Item(key = StrokeWidth.KEY, head = CoreVocabulary.Quality.KEY)
    public static final class StrokeWidth {
        public static final String KEY = "cg.quality:stroke-width";
        private StrokeWidth() {}

        @Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss = "the thickness of a shape's outline";
    }

    /**
     * Radius — the radius parameter of a radial shape (Circle, Sphere,
     * Cylinder, Cone).  Length-quantity.
     */
    @Seed.Item(key = Radius.KEY, head = CoreVocabulary.Quality.KEY)
    public static final class Radius {
        public static final String KEY = "cg.quality:radius";
        private Radius() {}

        @Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss =
                "the radius parameter of a radial shape (circle, sphere, cylinder, cone)";

        @Frame(predicate = LexicalVocabulary.Lexeme.KEY,
          field = @Binding(role = ThematicRole.Value.KEY,
            qualifiers = {Language.English.KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String englishNounLemma = "radius";
    }

    /**
     * Material — a reference to a PBR material describing how light
     * interacts with a 3D body's surface.  Target is an item reference
     * (Material instance).
     */
    @Seed.Item(key = Material.KEY, head = CoreVocabulary.Quality.KEY)
    public static final class Material {
        public static final String KEY = "cg.quality:material";
        private Material() {}

        @Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss =
                "a reference to a PBR material describing how light interacts with a 3D "
                        + "body's surface";

        @Frame(predicate = LexicalVocabulary.Lexeme.KEY,
          field = @Binding(role = ThematicRole.Value.KEY,
            qualifiers = {Language.English.KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String englishNounLemma = "material";
    }

    /**
     * Surfaces — named regions of a 3D body's geometry that can be bound
     * to nested container scenes.  Target is a map of name → container
     * reference, letting an inner container render onto a face or other
     * named surface of the parent geometry.
     */
    @Seed.Item(key = Surfaces.KEY, head = CoreVocabulary.Quality.KEY)
    public static final class Surfaces {
        public static final String KEY = "cg.quality:surfaces";
        private Surfaces() {}

        @Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss =
                "named regions of a 3D body's geometry that can be bound to nested "
                        + "container scenes";

        @Frame(predicate = LexicalVocabulary.Lexeme.KEY,
          field = @Binding(role = ThematicRole.Value.KEY,
            qualifiers = {Language.English.KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String englishNounLemma = "surfaces";
    }
}
