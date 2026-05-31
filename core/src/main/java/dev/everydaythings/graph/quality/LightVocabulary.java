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
 * Light vocabulary — sememes naming the kinds of lights a scene can carry
 * and the properties they declare.
 *
 * <p>A scene's light contributes meaningfully across every fidelity level:
 * in 3D it's a real Filament light source casting physical shadows; in 2D
 * it's a direction and color reference used to derive drop shadows from
 * elevation; in text it contributes to depth hints (border weight, ANSI
 * emphasis).
 *
 * <p>The {@code value/Light.java} class (added separately) is the typed
 * Value mirror of a Light body — head=Light, with bindings using the
 * qualities below.
 */
public final class LightVocabulary {

    private LightVocabulary() {}

    // ==================================================================================
    // LightType — how the light radiates.
    // ==================================================================================

    /**
     * LightType — how a light source radiates.  Target is one of
     * {@link Directional} / {@link PointLight} / {@link Spot}.
     */
    @Seed.Item(key = LightType.KEY, head = CoreVocabulary.Quality.KEY)
    public static final class LightType {
        public static final String KEY = "cg.quality:light-type";
        private LightType() {}

        @Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss = "how a light source radiates";

        @Frame(predicate = LexicalVocabulary.Lexeme.KEY,
          field = @Binding(role = ThematicRole.Value.KEY,
            qualifiers = {Language.English.KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String englishNounLemma = "light type";
    }

    /**
     * Directional — parallel rays from an infinitely-distant source.
     * Like sunlight.  Position becomes direction; no falloff with distance.
     */
    @Seed.Item(key = Directional.KEY)
    public static final class Directional {
        public static final String KEY = "cg.light-type:directional";
        private Directional() {}

        @Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss =
                "parallel rays from an infinitely-distant source — like sunlight; "
                        + "no falloff with distance";

        @Frame(predicate = LexicalVocabulary.Lexeme.KEY,
          field = @Binding(role = ThematicRole.Value.KEY,
            qualifiers = {Language.English.KEY, PartOfSpeech.Adjective.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String englishAdjectiveLemma = "directional";
    }

    /**
     * Point — light radiating outward in all directions from a position.
     * Like a bare bulb.  Falloff with distance.
     *
     * <p>Java name is {@code PointLight} to avoid colliding with
     * geometric Point types.
     */
    @Seed.Item(key = PointLight.KEY)
    public static final class PointLight {
        public static final String KEY = "cg.light-type:point";
        private PointLight() {}

        @Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss =
                "light radiating outward in all directions from a position — like a bare bulb; "
                        + "falloff with distance";

        @Frame(predicate = LexicalVocabulary.Lexeme.KEY,
          field = @Binding(role = ThematicRole.Value.KEY,
            qualifiers = {Language.English.KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String englishNounLemma = "point light";
    }

    /**
     * Spot — light cast in a cone from a position toward a direction.
     * Like a spotlight or flashlight.  Falloff with distance plus angular
     * cutoff.
     */
    @Seed.Item(key = Spot.KEY)
    public static final class Spot {
        public static final String KEY = "cg.light-type:spot";
        private Spot() {}

        @Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss =
                "light cast in a cone from a position toward a direction — like a spotlight; "
                        + "falloff with distance plus angular cutoff";

        @Frame(predicate = LexicalVocabulary.Lexeme.KEY,
          field = @Binding(role = ThematicRole.Value.KEY,
            qualifiers = {Language.English.KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String englishNounLemma = "spot light";
    }

    // ==================================================================================
    // Light properties — qualities used on Light bodies.
    // ==================================================================================

    /**
     * Ambient — the ambient (fill) light color.  This is what's lighting
     * shadowed areas — sky light, bounced light, the color shadows
     * actually have.  In 2D, this is the color drop shadows are painted in.
     */
    @Seed.Item(key = Ambient.KEY, head = CoreVocabulary.Quality.KEY)
    public static final class Ambient {
        public static final String KEY = "cg.quality:ambient";
        private Ambient() {}

        @Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss =
                "the ambient (fill) light color — what lights shadowed areas; in 2D, "
                        + "the color drop shadows are painted in";

        @Frame(predicate = LexicalVocabulary.Lexeme.KEY,
          field = @Binding(role = ThematicRole.Value.KEY,
            qualifiers = {Language.English.KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String englishNounLemma = "ambient";
    }

    /**
     * Intensity — strength multiplier on a light source.  Numeric target.
     */
    @Seed.Item(key = Intensity.KEY, head = CoreVocabulary.Quality.KEY)
    public static final class Intensity {
        public static final String KEY = "cg.quality:intensity";
        private Intensity() {}

        @Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss = "strength multiplier on a light source";

        @Frame(predicate = LexicalVocabulary.Lexeme.KEY,
          field = @Binding(role = ThematicRole.Value.KEY,
            qualifiers = {Language.English.KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String englishNounLemma = "intensity";
    }
}
