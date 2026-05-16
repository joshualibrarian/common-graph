package dev.everydaythings.graph.value;

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
 * Geometric value archetypes — structured values describing positions, extents,
 * directions, and ranges.
 *
 * <p>Each is a Value archetype (head = {@link Value}). Instances
 * carry their components as typed bindings:
 *
 * <pre>
 * Point   → Body[head = Point,  X = …, Y = …, Z = …]
 * Vector  → Body[head = Vector, X = …, Y = …, Z = …]    (direction + magnitude)
 * Range   → Body[head = Range,  Min = …, Max = …]
 * Box     → Body[head = Box,    Left = …, Top = …, Right = …, Bottom = …]
 * </pre>
 *
 * <p>The component values are typically Quantities (for spatial cases) but
 * can be anything appropriate (Instants for time Ranges, integers for index
 * Ranges, etc.).
 *
 * <p>X / Y / Z component sememes are declared here since they're shared across
 * Point and Vector. Min / Max for Range. Left / Top / Right / Bottom for Box.
 * Each is a plain sememe used as a binding-role.
 */
public final class GeometryVocabulary {

    private GeometryVocabulary() {}

    // ==================================================================================
    // Value archetypes
    // ==================================================================================

    /**
     * A position in 2D or 3D space. Carries X, Y, and optionally Z bindings.
     * Component targets are typically Length-Quantities, but can be any value
     * type appropriate for the coordinate system in use.
     */
    @Seed.Item(key = Point.KEY, head = Value.KEY)
    public static final class Point {
        public static final String KEY = "cg.value:point";
        private Point() {}

        @Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss =
                "a position in 2D or 3D space, with X / Y / Z component bindings";

        @Frame(predicate = LexicalVocabulary.Lexeme.KEY,
          field = @Binding(role = ThematicRole.Value.KEY,
            qualifiers = {Language.English.KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String englishNounLemma = "point";
    }

    /**
     * A direction-and-magnitude in 2D or 3D space. Same shape as Point at the
     * binding level (X / Y / Z bindings); distinguished from Point by intent
     * and by operator semantics — Vector arithmetic differs from Point
     * arithmetic.
     */
    @Seed.Item(key = Vector.KEY, head = Value.KEY)
    public static final class Vector {
        public static final String KEY = "cg.value:vector";
        private Vector() {}

        @Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss =
                "a direction with magnitude in 2D or 3D space, with X / Y / Z component bindings";

        @Frame(predicate = LexicalVocabulary.Lexeme.KEY,
          field = @Binding(role = ThematicRole.Value.KEY,
            qualifiers = {Language.English.KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String englishNounLemma = "vector";
    }

    /**
     * A continuous span between two endpoints. Carries Min and Max bindings.
     * Components are typically Quantities of the same kind (two lengths, two
     * instants, two integers), but the archetype itself doesn't constrain the
     * component type.
     */
    @Seed.Item(key = Range.KEY, head = Value.KEY)
    public static final class Range {
        public static final String KEY = "cg.value:range";
        private Range() {}

        @Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss =
                "a continuous span between two endpoints, with Min and Max bindings";

        @Frame(predicate = LexicalVocabulary.Lexeme.KEY,
          field = @Binding(role = ThematicRole.Value.KEY,
            qualifiers = {Language.English.KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String englishNounLemma = "range";
    }

    /**
     * An axis-aligned rectangular region. Carries Left, Top, Right, Bottom
     * bindings (or, optionally, X/Y/Width/Height in some conventions). Origins
     * convention is application-dependent.
     */
    @Seed.Item(key = Box.KEY, head = Value.KEY)
    public static final class Box {
        public static final String KEY = "cg.value:box";
        private Box() {}

        @Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss =
                "an axis-aligned rectangular region, with Left/Top/Right/Bottom or "
                        + "X/Y/Width/Height component bindings";

        @Frame(predicate = LexicalVocabulary.Lexeme.KEY,
          field = @Binding(role = ThematicRole.Value.KEY,
            qualifiers = {Language.English.KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String englishNounLemma = "box";
    }

    // ==================================================================================
    // Component sememes — binding-roles used inside geometric value bodies
    // ==================================================================================

    /** The X (horizontal) component of a Point or Vector. */
    @Seed.Item(key = X.KEY, head = CoreVocabulary.Quality.KEY)
    public static final class X {
        public static final String KEY = "cg.geometry:x";
        private X() {}

        @Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss = "the X (horizontal) component";
    }

    /** The Y (vertical) component of a Point or Vector. */
    @Seed.Item(key = Y.KEY, head = CoreVocabulary.Quality.KEY)
    public static final class Y {
        public static final String KEY = "cg.geometry:y";
        private Y() {}

        @Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss = "the Y (vertical) component";
    }

    /** The Z (depth) component of a Point or Vector. */
    @Seed.Item(key = Z.KEY, head = CoreVocabulary.Quality.KEY)
    public static final class Z {
        public static final String KEY = "cg.geometry:z";
        private Z() {}

        @Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss = "the Z (depth) component";
    }

    /** The minimum endpoint of a Range. */
    @Seed.Item(key = Min.KEY, head = CoreVocabulary.Quality.KEY)
    public static final class Min {
        public static final String KEY = "cg.geometry:min";
        private Min() {}

        @Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss = "the minimum endpoint of a range";
    }

    /** The maximum endpoint of a Range. */
    @Seed.Item(key = Max.KEY, head = CoreVocabulary.Quality.KEY)
    public static final class Max {
        public static final String KEY = "cg.geometry:max";
        private Max() {}

        @Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss = "the maximum endpoint of a range";
    }

    /** The left edge of a Box (typically a horizontal coordinate or distance). */
    @Seed.Item(key = Left.KEY, head = CoreVocabulary.Quality.KEY)
    public static final class Left {
        public static final String KEY = "cg.geometry:left";
        private Left() {}

        @Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss = "the left edge of a region";
    }

    /** The top edge of a Box. */
    @Seed.Item(key = Top.KEY, head = CoreVocabulary.Quality.KEY)
    public static final class Top {
        public static final String KEY = "cg.geometry:top";
        private Top() {}

        @Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss = "the top edge of a region";
    }

    /** The right edge of a Box. */
    @Seed.Item(key = Right.KEY, head = CoreVocabulary.Quality.KEY)
    public static final class Right {
        public static final String KEY = "cg.geometry:right";
        private Right() {}

        @Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss = "the right edge of a region";
    }

    /** The bottom edge of a Box. */
    @Seed.Item(key = Bottom.KEY, head = CoreVocabulary.Quality.KEY)
    public static final class Bottom {
        public static final String KEY = "cg.geometry:bottom";
        private Bottom() {}

        @Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss = "the bottom edge of a region";
    }
}
