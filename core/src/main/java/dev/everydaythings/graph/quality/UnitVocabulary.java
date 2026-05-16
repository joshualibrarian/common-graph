package dev.everydaythings.graph.quality;

import dev.everydaythings.graph.Seed;
import dev.everydaythings.graph.datum.Body;
import dev.everydaythings.graph.id.ItemRef;
import dev.everydaythings.graph.language.GrammaticalFeature;
import dev.everydaythings.graph.language.Language;
import dev.everydaythings.graph.language.LexicalVocabulary;
import dev.everydaythings.graph.language.PartOfSpeech;
import dev.everydaythings.graph.language.ThematicRole;
import dev.everydaythings.graph.operator.math.Multiply;
import dev.everydaythings.graph.value.Quantity;

import static dev.everydaythings.graph.Seed.*;

/**
 * Units of measurement and the binding-role sememes they use to describe
 * themselves.
 *
 * <p>Every unit is an instance of {@link Unit}. Each unit's manifest carries
 * direct property bindings declaring:
 *
 * <ul>
 *   <li>{@link Symbol} — the display symbol ({@code "m"}, {@code "kg"})</li>
 *   <li>{@code Dimension:[D] → N} — dimensional formula entry per
 *       {@link DimensionVocabulary} dimension, with the exponent as target</li>
 *   <li>{@link ScaleNumerator}, {@link ScaleDenominator} — rational factor for
 *       converting to the SI base unit of the same dimension(s)</li>
 * </ul>
 *
 * <p>This file holds units whose conversion is a fixed rational scale —
 * derivable purely from constants. Units whose conversion involves runtime
 * variables (Pixel needing DevicePixelSize, Em needing BaseFontSize) are
 * declared elsewhere via named-expression seeds; they reference {@link Unit}
 * the same way.
 */
public final class UnitVocabulary {

    private UnitVocabulary() {}

    // ==================================================================================
    // Meta-archetype + property predicates
    // ==================================================================================

    /**
     * The archetype of units of measurement. Each instance carries a dimensional
     * formula, a display symbol, and a rational scale to the SI base unit of
     * its dimensions.
     */
    @Seed.Item(key = Unit.KEY)
    public static final class Unit {
        public static final String KEY = "cg.archetype:unit";
        private Unit() {}

        @Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss =
                "a unit of measurement — a named scale on one or more physical "
                        + "dimensions";

        @Frame(predicate = LexicalVocabulary.Lexeme.KEY,
          field = @Binding(role = ThematicRole.Value.KEY,
            qualifiers = {Language.English.KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String englishNounLemma = "unit";
    }

    /**
     * The display symbol of a unit ({@code "m"}, {@code "kg"}, {@code "px"}).
     * Appears as a binding-role key on a unit's manifest:
     * {@code Symbol → "m"}.
     */
    @Seed.Item(key = Symbol.KEY)
    public static final class Symbol {
        public static final String KEY = "cg.unit:symbol";
        private Symbol() {}

        @Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss =
                "the canonical short display form of a unit (e.g., \"m\", \"kg\", \"px\")";

        @Frame(predicate = LexicalVocabulary.Lexeme.KEY,
          field = @Binding(role = ThematicRole.Value.KEY,
            qualifiers = {Language.English.KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String englishNounLemma = "symbol";
    }

    /**
     * Numerator of the rational scale converting from this unit to the SI base
     * unit of its dimensions. Used as a binding-role key:
     * {@code ScaleNumerator → 1}.
     */
    @Seed.Item(key = ScaleNumerator.KEY)
    public static final class ScaleNumerator {
        public static final String KEY = "cg.unit:scale-numerator";
        private ScaleNumerator() {}

        @Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss =
                "numerator of the rational factor that converts a value in this unit "
                        + "to a value in the SI base unit";
    }

    /**
     * Denominator of the rational scale converting from this unit to the SI base
     * unit. Used as a binding-role key: {@code ScaleDenominator → 100}.
     */
    @Seed.Item(key = ScaleDenominator.KEY)
    public static final class ScaleDenominator {
        public static final String KEY = "cg.unit:scale-denominator";
        private ScaleDenominator() {}

        @Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss =
                "denominator of the rational factor that converts a value in this unit "
                        + "to a value in the SI base unit";
    }

    // Quantity (the Value archetype paired with units) lives at value.Quantity.

    /**
     * Binding-role for a unit's equivalent value in the SI base unit of its
     * dimensions. Used when the conversion isn't a fixed rational scale — when
     * the unit depends on runtime variables (Pixel needs DevicePixelSize, Em
     * needs BaseFontSize, etc.).
     *
     * <p>The target is typically a {@link Quantity} body or an evaluable
     * expression whose result is a Quantity. Fixed-scale units use
     * {@link ScaleNumerator} / {@link ScaleDenominator} instead.
     */
    @Seed.Item(key = EquivalentInBase.KEY)
    public static final class EquivalentInBase {
        public static final String KEY = "cg.unit:equivalent-in-base";
        private EquivalentInBase() {}

        @Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss =
                "the value of one of this unit expressed in the SI base unit of its "
                        + "dimensions — used by variable-bearing units whose conversion "
                        + "depends on runtime context";
    }

    // ==================================================================================
    // Length units
    // ==================================================================================

    /** SI base unit of length. 1 meter = 1 meter. */
    @Seed.Item(key = Meter.KEY, head = Unit.KEY)
    public static final class Meter {
        public static final String KEY = "cg.unit:meter";
        private Meter() {}

        @Seed.Property(role = Symbol.KEY)
        static final String symbol = "m";

        @Seed.Property(role = DimensionVocabulary.Dimension.KEY,
                       qualifiers = {DimensionVocabulary.Length.KEY})
        static final long lengthExponent = 1;

        @Seed.Property(role = ScaleNumerator.KEY)
        static final long scaleNumerator = 1;

        @Seed.Property(role = ScaleDenominator.KEY)
        static final long scaleDenominator = 1;

        @Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss = "the SI base unit of length";

        @Frame(predicate = LexicalVocabulary.Lexeme.KEY,
          field = @Binding(role = ThematicRole.Value.KEY,
            qualifiers = {Language.English.KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String englishNounLemma = "meter";
    }

    /** 1 cm = 1/100 m. */
    @Seed.Item(key = Centimeter.KEY, head = Unit.KEY)
    public static final class Centimeter {
        public static final String KEY = "cg.unit:centimeter";
        private Centimeter() {}

        @Seed.Property(role = Symbol.KEY)
        static final String symbol = "cm";

        @Seed.Property(role = DimensionVocabulary.Dimension.KEY,
                       qualifiers = {DimensionVocabulary.Length.KEY})
        static final long lengthExponent = 1;

        @Seed.Property(role = ScaleNumerator.KEY)
        static final long scaleNumerator = 1;

        @Seed.Property(role = ScaleDenominator.KEY)
        static final long scaleDenominator = 100;

        @Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss = "one hundredth of a meter";

        @Frame(predicate = LexicalVocabulary.Lexeme.KEY,
          field = @Binding(role = ThematicRole.Value.KEY,
            qualifiers = {Language.English.KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String englishNounLemma = "centimeter";
    }

    /** 1 mm = 1/1000 m. */
    @Seed.Item(key = Millimeter.KEY, head = Unit.KEY)
    public static final class Millimeter {
        public static final String KEY = "cg.unit:millimeter";
        private Millimeter() {}

        @Seed.Property(role = Symbol.KEY)
        static final String symbol = "mm";

        @Seed.Property(role = DimensionVocabulary.Dimension.KEY,
                       qualifiers = {DimensionVocabulary.Length.KEY})
        static final long lengthExponent = 1;

        @Seed.Property(role = ScaleNumerator.KEY)
        static final long scaleNumerator = 1;

        @Seed.Property(role = ScaleDenominator.KEY)
        static final long scaleDenominator = 1000;

        @Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss = "one thousandth of a meter";

        @Frame(predicate = LexicalVocabulary.Lexeme.KEY,
          field = @Binding(role = ThematicRole.Value.KEY,
            qualifiers = {Language.English.KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String englishNounLemma = "millimeter";
    }

    // ==================================================================================
    // Time units
    // ==================================================================================

    /** SI base unit of time. */
    @Seed.Item(key = Second.KEY, head = Unit.KEY)
    public static final class Second {
        public static final String KEY = "cg.unit:second";
        private Second() {}

        @Seed.Property(role = Symbol.KEY)
        static final String symbol = "s";

        @Seed.Property(role = DimensionVocabulary.Dimension.KEY,
                       qualifiers = {DimensionVocabulary.Time.KEY})
        static final long timeExponent = 1;

        @Seed.Property(role = ScaleNumerator.KEY)
        static final long scaleNumerator = 1;

        @Seed.Property(role = ScaleDenominator.KEY)
        static final long scaleDenominator = 1;

        @Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss = "the SI base unit of time";

        @Frame(predicate = LexicalVocabulary.Lexeme.KEY,
          field = @Binding(role = ThematicRole.Value.KEY,
            qualifiers = {Language.English.KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String englishNounLemma = "second";
    }

    // ==================================================================================
    // Mass units
    // ==================================================================================

    /** SI base unit of mass. */
    @Seed.Item(key = Kilogram.KEY, head = Unit.KEY)
    public static final class Kilogram {
        public static final String KEY = "cg.unit:kilogram";
        private Kilogram() {}

        @Seed.Property(role = Symbol.KEY)
        static final String symbol = "kg";

        @Seed.Property(role = DimensionVocabulary.Dimension.KEY,
                       qualifiers = {DimensionVocabulary.Mass.KEY})
        static final long massExponent = 1;

        @Seed.Property(role = ScaleNumerator.KEY)
        static final long scaleNumerator = 1;

        @Seed.Property(role = ScaleDenominator.KEY)
        static final long scaleDenominator = 1;

        @Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss = "the SI base unit of mass";

        @Frame(predicate = LexicalVocabulary.Lexeme.KEY,
          field = @Binding(role = ThematicRole.Value.KEY,
            qualifiers = {Language.English.KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String englishNounLemma = "kilogram";
    }

    // ==================================================================================
    // Electric current
    // ==================================================================================

    /** SI base unit of electric current. */
    @Seed.Item(key = Ampere.KEY, head = Unit.KEY)
    public static final class Ampere {
        public static final String KEY = "cg.unit:ampere";
        private Ampere() {}

        @Seed.Property(role = Symbol.KEY)
        static final String symbol = "A";

        @Seed.Property(role = DimensionVocabulary.Dimension.KEY,
                       qualifiers = {DimensionVocabulary.ElectricCurrent.KEY})
        static final long currentExponent = 1;

        @Seed.Property(role = ScaleNumerator.KEY)
        static final long scaleNumerator = 1;

        @Seed.Property(role = ScaleDenominator.KEY)
        static final long scaleDenominator = 1;

        @Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss = "the SI base unit of electric current";

        @Frame(predicate = LexicalVocabulary.Lexeme.KEY,
          field = @Binding(role = ThematicRole.Value.KEY,
            qualifiers = {Language.English.KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String englishNounLemma = "ampere";
    }

    // ==================================================================================
    // Temperature
    // ==================================================================================

    /** SI base unit of thermodynamic temperature. */
    @Seed.Item(key = Kelvin.KEY, head = Unit.KEY)
    public static final class Kelvin {
        public static final String KEY = "cg.unit:kelvin";
        private Kelvin() {}

        @Seed.Property(role = Symbol.KEY)
        static final String symbol = "K";

        @Seed.Property(role = DimensionVocabulary.Dimension.KEY,
                       qualifiers = {DimensionVocabulary.Temperature.KEY})
        static final long temperatureExponent = 1;

        @Seed.Property(role = ScaleNumerator.KEY)
        static final long scaleNumerator = 1;

        @Seed.Property(role = ScaleDenominator.KEY)
        static final long scaleDenominator = 1;

        @Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss = "the SI base unit of thermodynamic temperature";

        @Frame(predicate = LexicalVocabulary.Lexeme.KEY,
          field = @Binding(role = ThematicRole.Value.KEY,
            qualifiers = {Language.English.KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String englishNounLemma = "kelvin";
    }

    // ==================================================================================
    // Amount of substance
    // ==================================================================================

    /** SI base unit of amount of substance. */
    @Seed.Item(key = Mole.KEY, head = Unit.KEY)
    public static final class Mole {
        public static final String KEY = "cg.unit:mole";
        private Mole() {}

        @Seed.Property(role = Symbol.KEY)
        static final String symbol = "mol";

        @Seed.Property(role = DimensionVocabulary.Dimension.KEY,
                       qualifiers = {DimensionVocabulary.Amount.KEY})
        static final long amountExponent = 1;

        @Seed.Property(role = ScaleNumerator.KEY)
        static final long scaleNumerator = 1;

        @Seed.Property(role = ScaleDenominator.KEY)
        static final long scaleDenominator = 1;

        @Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss = "the SI base unit of amount of substance";

        @Frame(predicate = LexicalVocabulary.Lexeme.KEY,
          field = @Binding(role = ThematicRole.Value.KEY,
            qualifiers = {Language.English.KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String englishNounLemma = "mole";
    }

    // ==================================================================================
    // Luminous intensity
    // ==================================================================================

    /** SI base unit of luminous intensity. */
    @Seed.Item(key = Candela.KEY, head = Unit.KEY)
    public static final class Candela {
        public static final String KEY = "cg.unit:candela";
        private Candela() {}

        @Seed.Property(role = Symbol.KEY)
        static final String symbol = "cd";

        @Seed.Property(role = DimensionVocabulary.Dimension.KEY,
                       qualifiers = {DimensionVocabulary.LuminousIntensity.KEY})
        static final long luminousExponent = 1;

        @Seed.Property(role = ScaleNumerator.KEY)
        static final long scaleNumerator = 1;

        @Seed.Property(role = ScaleDenominator.KEY)
        static final long scaleDenominator = 1;

        @Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss = "the SI base unit of luminous intensity";

        @Frame(predicate = LexicalVocabulary.Lexeme.KEY,
          field = @Binding(role = ThematicRole.Value.KEY,
            qualifiers = {Language.English.KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String englishNounLemma = "candela";
    }

    // ==================================================================================
    // Variable-bearing units
    //
    // Scale expressions are inlined as Body field values on the unit class. The
    // @Seed.Property handling reads the Body directly as the binding's target,
    // so no separate named-seed item is needed for the expression. Use a named
    // seed only when an expression is canonically reused outside its owning
    // unit (multi-unit conversion factors, externally referenced constants).
    // ==================================================================================

    /**
     * Em — a length unit equal to the BaseFontSize of the current typography
     * scope. The conversion is a direct reference to a {@link Variable}; the
     * resolver substitutes whatever Quantity the session has bound for
     * {@link TypographyVocabulary.BaseFontSize} at the use site.
     *
     * <p>Unlike {@link Pixel}, no Multiply wrapper is needed — the variable
     * itself <i>is</i> the equivalent value (a Quantity in pixels), not a factor
     * to be multiplied by something else.
     */
    @Seed.Item(key = Em.KEY, head = Unit.KEY)
    public static final class Em {
        public static final String KEY = "cg.unit:em";
        private Em() {}

        @Seed.Property(role = Symbol.KEY)
        static final String symbol = "em";

        @Seed.Property(role = DimensionVocabulary.Dimension.KEY,
                       qualifiers = {DimensionVocabulary.Length.KEY})
        static final long lengthExponent = 1;

        @Seed.Property(role = EquivalentInBase.KEY)
        static final ItemRef equivalentInBase = ItemRef.iid(TypographyVocabulary.BaseFontSize.KEY);

        @Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss =
                "em — typography-relative length unit equal to the current scope's "
                        + "BaseFontSize";

        @Frame(predicate = LexicalVocabulary.Lexeme.KEY,
          field = @Binding(role = ThematicRole.Value.KEY,
            qualifiers = {Language.English.KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String englishNounLemma = "em";
    }

    /**
     * Pixel — a length unit whose conversion to the SI base meter depends on the
     * active display. The {@link EquivalentInBase} binding carries the scale
     * expression {@code DevicePixelSize × Quantity(1, Meter)} inline as a Body
     * literal — the resolver evaluates it against the session's bound
     * {@link SpatialVocabulary.DevicePixelSize} at layout time.
     */
    @Seed.Item(key = Pixel.KEY, head = Unit.KEY)
    public static final class Pixel {
        public static final String KEY = "cg.unit:pixel";
        private Pixel() {}

        @Seed.Property(role = Symbol.KEY)
        static final String symbol = "px";

        @Seed.Property(role = DimensionVocabulary.Dimension.KEY,
                       qualifiers = {DimensionVocabulary.Length.KEY})
        static final long lengthExponent = 1;

        /** {@code DevicePixelSize × Quantity{Value=1, @Meter=1}} — inline. */
        @Seed.Property(role = EquivalentInBase.KEY)
        static final Body equivalentInBase = (Body) Body.compose(ItemRef.iid(Multiply.KEY))
                .binding(ItemRef.iid(ThematicRole.Theme.KEY)).index(0)
                    .target(ItemRef.iid(SpatialVocabulary.DevicePixelSize.KEY))
                .binding(ItemRef.iid(ThematicRole.Theme.KEY)).index(1)
                    .target((Body) Body.compose(ItemRef.iid(Quantity.KEY))
                            .value(1L)                              // magnitude
                            .with(ItemRef.iid(Meter.KEY), 1L)               // @Meter exponent = 1 (tiny shape)
                            .build())
                .build();

        @Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss =
                "pixel — device-relative length unit; conversion depends on DevicePixelSize "
                        + "bound at layout time";

        @Frame(predicate = LexicalVocabulary.Lexeme.KEY,
          field = @Binding(role = ThematicRole.Value.KEY,
            qualifiers = {Language.English.KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String englishNounLemma = "pixel";
    }

    // ==================================================================================
    // Mass units (continued)
    // ==================================================================================

    /** 1 g = 1/1000 kg. */
    @Seed.Item(key = Gram.KEY, head = Unit.KEY)
    public static final class Gram {
        public static final String KEY = "cg.unit:gram";
        private Gram() {}

        @Seed.Property(role = Symbol.KEY)
        static final String symbol = "g";

        @Seed.Property(role = DimensionVocabulary.Dimension.KEY,
                       qualifiers = {DimensionVocabulary.Mass.KEY})
        static final long massExponent = 1;

        @Seed.Property(role = ScaleNumerator.KEY)
        static final long scaleNumerator = 1;

        @Seed.Property(role = ScaleDenominator.KEY)
        static final long scaleDenominator = 1000;

        @Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss = "one thousandth of a kilogram";

        @Frame(predicate = LexicalVocabulary.Lexeme.KEY,
          field = @Binding(role = ThematicRole.Value.KEY,
            qualifiers = {Language.English.KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String englishNounLemma = "gram";
    }
}
