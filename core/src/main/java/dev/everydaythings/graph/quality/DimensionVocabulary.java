package dev.everydaythings.graph.quality;

import dev.everydaythings.graph.Seed;
import dev.everydaythings.graph.language.GrammaticalFeature;
import dev.everydaythings.graph.language.Language;
import dev.everydaythings.graph.language.LexicalVocabulary;
import dev.everydaythings.graph.language.PartOfSpeech;
import dev.everydaythings.graph.language.ThematicRole;

import static dev.everydaythings.graph.Seed.*;

/**
 * Physical dimensions — the SI base quantities every measurable thing decomposes
 * into.
 *
 * <p>{@link Dimension} is the meta-archetype; each named dimension (Length, Time,
 * Mass, …) is an item under it. Units cite these dimensions in their dimensional
 * formula (e.g., {@code Dimension:[Length] → 1, Dimension:[Time] → -1} on a
 * velocity unit), letting dimensional analysis prevent category errors at
 * evaluation time.
 *
 * <p>The seven SI base dimensions are:
 *
 * <ul>
 *   <li>{@link Length} (L) — meter</li>
 *   <li>{@link Time} (T) — second</li>
 *   <li>{@link Mass} (M) — kilogram</li>
 *   <li>{@link ElectricCurrent} (I) — ampere</li>
 *   <li>{@link Temperature} (Θ) — kelvin</li>
 *   <li>{@link Amount} (N) — mole</li>
 *   <li>{@link LuminousIntensity} (J) — candela</li>
 * </ul>
 *
 * <p>Compound dimensions (velocity = L/T, force = ML/T², energy = ML²/T²) emerge
 * from combinations of these on a unit's dimensional formula; they don't need
 * their own seed entries.
 *
 * <p>Non-physical dimensions (information bits, currency, percentage) can be
 * added as siblings when use cases land — the meta-archetype is general.
 */
public final class DimensionVocabulary {

    private DimensionVocabulary() {}

    // ==================================================================================
    // The meta-archetype
    // ==================================================================================

    /**
     * The archetype of physical dimensions. Each instance (Length, Time, …)
     * names an irreducible quantity-kind that participates in dimensional analysis.
     */
    @Seed.Item(key = Dimension.KEY)
    public static final class Dimension {
        public static final String KEY = "cg.archetype:dimension";
        private Dimension() {}

        @Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss =
                "an irreducible physical dimension — a kind of quantity that participates "
                        + "in dimensional analysis (length, time, mass, …)";

        @Frame(predicate = LexicalVocabulary.Lexeme.KEY,
          field = @Binding(role = ThematicRole.Value.KEY,
            qualifiers = {Language.English.KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String englishNounLemma = "dimension";
    }

    // ==================================================================================
    // The seven SI base dimensions
    // ==================================================================================

    /** Spatial extent. SI base unit: meter (m). Symbol: L. */
    @Seed.Item(key = Length.KEY, head = Dimension.KEY)
    public static final class Length {
        public static final String KEY = "cg.dimension:length";
        private Length() {}

        @Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss = "spatial extent in one direction (SI symbol: L)";

        @Frame(predicate = LexicalVocabulary.Lexeme.KEY,
          field = @Binding(role = ThematicRole.Value.KEY,
            qualifiers = {Language.English.KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String englishNounLemma = "length";
    }

    /** Duration. SI base unit: second (s). Symbol: T. */
    @Seed.Item(key = Time.KEY, head = Dimension.KEY)
    public static final class Time {
        public static final String KEY = "cg.dimension:time";
        private Time() {}

        @Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss = "duration of an event or interval (SI symbol: T)";

        @Frame(predicate = LexicalVocabulary.Lexeme.KEY,
          field = @Binding(role = ThematicRole.Value.KEY,
            qualifiers = {Language.English.KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String englishNounLemma = "time";
    }

    /** Quantity of matter. SI base unit: kilogram (kg). Symbol: M. */
    @Seed.Item(key = Mass.KEY, head = Dimension.KEY)
    public static final class Mass {
        public static final String KEY = "cg.dimension:mass";
        private Mass() {}

        @Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss = "quantity of matter in a body (SI symbol: M)";

        @Frame(predicate = LexicalVocabulary.Lexeme.KEY,
          field = @Binding(role = ThematicRole.Value.KEY,
            qualifiers = {Language.English.KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String englishNounLemma = "mass";
    }

    /** Flow of electric charge. SI base unit: ampere (A). Symbol: I. */
    @Seed.Item(key = ElectricCurrent.KEY, head = Dimension.KEY)
    public static final class ElectricCurrent {
        public static final String KEY = "cg.dimension:electric-current";
        private ElectricCurrent() {}

        @Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss = "rate of flow of electric charge (SI symbol: I)";

        @Frame(predicate = LexicalVocabulary.Lexeme.KEY,
          field = @Binding(role = ThematicRole.Value.KEY,
            qualifiers = {Language.English.KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String englishNounLemma = "electric current";
    }

    /** Thermodynamic temperature. SI base unit: kelvin (K). Symbol: Θ. */
    @Seed.Item(key = Temperature.KEY, head = Dimension.KEY)
    public static final class Temperature {
        public static final String KEY = "cg.dimension:temperature";
        private Temperature() {}

        @Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss = "thermodynamic temperature (SI symbol: Θ)";

        @Frame(predicate = LexicalVocabulary.Lexeme.KEY,
          field = @Binding(role = ThematicRole.Value.KEY,
            qualifiers = {Language.English.KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String englishNounLemma = "temperature";
    }

    /** Amount of substance. SI base unit: mole (mol). Symbol: N. */
    @Seed.Item(key = Amount.KEY, head = Dimension.KEY)
    public static final class Amount {
        public static final String KEY = "cg.dimension:amount";
        private Amount() {}

        @Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss =
                "amount of substance — count of elementary entities (SI symbol: N)";

        @Frame(predicate = LexicalVocabulary.Lexeme.KEY,
          field = @Binding(role = ThematicRole.Value.KEY,
            qualifiers = {Language.English.KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String englishNounLemma = "amount of substance";
    }

    /** Luminous intensity. SI base unit: candela (cd). Symbol: J. */
    @Seed.Item(key = LuminousIntensity.KEY, head = Dimension.KEY)
    public static final class LuminousIntensity {
        public static final String KEY = "cg.dimension:luminous-intensity";
        private LuminousIntensity() {}

        @Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss =
                "luminous power per unit solid angle (SI symbol: J)";

        @Frame(predicate = LexicalVocabulary.Lexeme.KEY,
          field = @Binding(role = ThematicRole.Value.KEY,
            qualifiers = {Language.English.KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String englishNounLemma = "luminous intensity";
    }
}
