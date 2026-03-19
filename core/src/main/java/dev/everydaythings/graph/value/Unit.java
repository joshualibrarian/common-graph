package dev.everydaythings.graph.value;

import dev.everydaythings.graph.frame.ItemFrame;
import dev.everydaythings.graph.item.Implements;
import dev.everydaythings.graph.item.Item;
import dev.everydaythings.graph.item.ItemSeed;
import dev.everydaythings.graph.item.Manifest;
import dev.everydaythings.graph.item.id.ItemID;
import dev.everydaythings.graph.language.GrammaticalFeature;
import dev.everydaythings.graph.language.Language;
import dev.everydaythings.graph.language.PartOfSpeech;
import dev.everydaythings.graph.language.Sememe;
import dev.everydaythings.graph.language.SememeGloss;
import dev.everydaythings.graph.item.user.Signer;
import dev.everydaythings.graph.language.CoreVocabulary;
import dev.everydaythings.graph.runtime.Librarian;
import lombok.Getter;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * A Unit of measurement as a first-class Item.
 *
 * <p>This is a self-describing type. The class IS the definition.
 *
 * <p>Units have:
 * <ul>
 *   <li>symbol: short display symbol (e.g., "m", "mm", "in")</li>
 *   <li>names: language-tagged display names</li>
 *   <li>dimensions: map of Dimension IID → exponent</li>
 *   <li>scaleP/scaleQ: rational conversion to base unit (value_base = value * scaleP/scaleQ)</li>
 * </ul>
 *
 * <p>Dimensions are Items, referenced by their IID. This allows:
 * <ul>
 *   <li>Extension with new dimensions (information, currency, etc.)</li>
 *   <li>Self-describing dimensional analysis</li>
 * </ul>
 *
 * <p>Example dimensional formulas:
 * <ul>
 *   <li>meter: {LENGTH: 1}</li>
 *   <li>m/s (velocity): {LENGTH: 1, TIME: -1}</li>
 *   <li>kg*m/s² (force): {MASS: 1, LENGTH: 1, TIME: -2}</li>
 * </ul>
 *
 * <p>Usage:
 * <pre>{@code
 * // Reference a unit
 * Unit meter = Unit.Meter.SEED;
 * ItemID meterId = meter.iid();
 *
 * // Get unit dimensions
 * Map<ItemID, Integer> dims = meter.dimensions();
 * }</pre>
 */
@Implements(Unit.KEY)
public class Unit extends Sememe {

    // ==================================================================================
    // TYPE DEFINITION
    // ==================================================================================

    public static final String KEY = "cg.sememe:unit";

    /** Helper: deterministic IID from canonical key (avoids triggering class init on Dimension). */
    private static ItemID dim(String key) { return ItemID.fromString(key); }

    // ==================================================================================
    // SEED INSTANCES - Length
    // ==================================================================================

    @Implements(Meter.KEY) @ItemSeed(key = Meter.KEY)
    public static class Meter extends Unit {
        public static final String KEY = "cg.unit:meter";
        public static final ItemID IID = ItemID.fromString(KEY);
        Meter() { super(KEY, "m", Map.of("en", "meter", "en-GB", "metre"), Map.of(dim(Dimension.Length.KEY), 1), 1, 1); }
        @ItemFrame(key = {SememeGloss.KEY, Language.ENGLISH_KEY}) static final String gloss = "meter";
        @ItemFrame(key = {CoreVocabulary.Symbol.KEY}) static final String sym = "m";
        @ItemFrame(key = {CoreVocabulary.Lexeme.KEY, Language.ENGLISH_KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}) static final String word = "meter";
    }
    @Implements(Millimeter.KEY) @ItemSeed(key = Millimeter.KEY)
    public static class Millimeter extends Unit {
        public static final String KEY = "cg.unit:millimeter";
        public static final ItemID IID = ItemID.fromString(KEY);
        Millimeter() { super(KEY, "mm", Map.of("en", "millimeter", "en-GB", "millimetre"), Map.of(dim(Dimension.Length.KEY), 1), 1, 1000); }
        @ItemFrame(key = {SememeGloss.KEY, Language.ENGLISH_KEY}) static final String gloss = "millimeter";
        @ItemFrame(key = {CoreVocabulary.Symbol.KEY}) static final String sym = "mm";
        @ItemFrame(key = {CoreVocabulary.Lexeme.KEY, Language.ENGLISH_KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}) static final String word = "millimeter";
    }
    @Implements(Centimeter.KEY) @ItemSeed(key = Centimeter.KEY)
    public static class Centimeter extends Unit {
        public static final String KEY = "cg.unit:centimeter";
        public static final ItemID IID = ItemID.fromString(KEY);
        Centimeter() { super(KEY, "cm", Map.of("en", "centimeter", "en-GB", "centimetre"), Map.of(dim(Dimension.Length.KEY), 1), 1, 100); }
        @ItemFrame(key = {SememeGloss.KEY, Language.ENGLISH_KEY}) static final String gloss = "centimeter";
        @ItemFrame(key = {CoreVocabulary.Symbol.KEY}) static final String sym = "cm";
        @ItemFrame(key = {CoreVocabulary.Lexeme.KEY, Language.ENGLISH_KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}) static final String word = "centimeter";
    }
    @Implements(Kilometer.KEY) @ItemSeed(key = Kilometer.KEY)
    public static class Kilometer extends Unit {
        public static final String KEY = "cg.unit:kilometer";
        public static final ItemID IID = ItemID.fromString(KEY);
        Kilometer() { super(KEY, "km", Map.of("en", "kilometer", "en-GB", "kilometre"), Map.of(dim(Dimension.Length.KEY), 1), 1000, 1); }
        @ItemFrame(key = {SememeGloss.KEY, Language.ENGLISH_KEY}) static final String gloss = "kilometer";
        @ItemFrame(key = {CoreVocabulary.Symbol.KEY}) static final String sym = "km";
        @ItemFrame(key = {CoreVocabulary.Lexeme.KEY, Language.ENGLISH_KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}) static final String word = "kilometer";
    }
    @Implements(Inch.KEY) @ItemSeed(key = Inch.KEY)
    public static class Inch extends Unit {
        public static final String KEY = "cg.unit:inch";
        public static final ItemID IID = ItemID.fromString(KEY);
        Inch() { super(KEY, "in", Map.of("en", "inch"), Map.of(dim(Dimension.Length.KEY), 1), 127, 5000); }
        @ItemFrame(key = {SememeGloss.KEY, Language.ENGLISH_KEY}) static final String gloss = "inch";
        @ItemFrame(key = {CoreVocabulary.Symbol.KEY}) static final String sym = "in";
        @ItemFrame(key = {CoreVocabulary.Lexeme.KEY, Language.ENGLISH_KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}) static final String word = "inch";
    }
    @Implements(Foot.KEY) @ItemSeed(key = Foot.KEY)
    public static class Foot extends Unit {
        public static final String KEY = "cg.unit:foot";
        public static final ItemID IID = ItemID.fromString(KEY);
        Foot() { super(KEY, "ft", Map.of("en", "foot"), Map.of(dim(Dimension.Length.KEY), 1), 381, 1250); }
        @ItemFrame(key = {SememeGloss.KEY, Language.ENGLISH_KEY}) static final String gloss = "foot";
        @ItemFrame(key = {CoreVocabulary.Symbol.KEY}) static final String sym = "ft";
        @ItemFrame(key = {CoreVocabulary.Lexeme.KEY, Language.ENGLISH_KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}) static final String word = "foot";
    }

    // ==================================================================================
    // SEED INSTANCES - Time
    // ==================================================================================

    @Implements(Second.KEY) @ItemSeed(key = Second.KEY)
    public static class Second extends Unit {
        public static final String KEY = "cg.unit:second";
        public static final ItemID IID = ItemID.fromString(KEY);
        Second() { super(KEY, "s", Map.of("en", "second"), Map.of(dim(Dimension.Time.KEY), 1), 1, 1); }
        @ItemFrame(key = {SememeGloss.KEY, Language.ENGLISH_KEY}) static final String gloss = "second";
        @ItemFrame(key = {CoreVocabulary.Symbol.KEY}) static final String sym = "s";
        @ItemFrame(key = {CoreVocabulary.Lexeme.KEY, Language.ENGLISH_KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}) static final String word = "second";
    }
    @Implements(Millisecond.KEY) @ItemSeed(key = Millisecond.KEY)
    public static class Millisecond extends Unit {
        public static final String KEY = "cg.unit:millisecond";
        public static final ItemID IID = ItemID.fromString(KEY);
        Millisecond() { super(KEY, "ms", Map.of("en", "millisecond"), Map.of(dim(Dimension.Time.KEY), 1), 1, 1000); }
        @ItemFrame(key = {SememeGloss.KEY, Language.ENGLISH_KEY}) static final String gloss = "millisecond";
        @ItemFrame(key = {CoreVocabulary.Symbol.KEY}) static final String sym = "ms";
        @ItemFrame(key = {CoreVocabulary.Lexeme.KEY, Language.ENGLISH_KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}) static final String word = "millisecond";
    }
    @Implements(Minute.KEY) @ItemSeed(key = Minute.KEY)
    public static class Minute extends Unit {
        public static final String KEY = "cg.unit:minute";
        public static final ItemID IID = ItemID.fromString(KEY);
        Minute() { super(KEY, "min", Map.of("en", "minute"), Map.of(dim(Dimension.Time.KEY), 1), 60, 1); }
        @ItemFrame(key = {SememeGloss.KEY, Language.ENGLISH_KEY}) static final String gloss = "minute";
        @ItemFrame(key = {CoreVocabulary.Symbol.KEY}) static final String sym = "min";
        @ItemFrame(key = {CoreVocabulary.Lexeme.KEY, Language.ENGLISH_KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}) static final String word = "minute";
    }
    @Implements(Hour.KEY) @ItemSeed(key = Hour.KEY)
    public static class Hour extends Unit {
        public static final String KEY = "cg.unit:hour";
        public static final ItemID IID = ItemID.fromString(KEY);
        Hour() { super(KEY, "h", Map.of("en", "hour"), Map.of(dim(Dimension.Time.KEY), 1), 3600, 1); }
        @ItemFrame(key = {SememeGloss.KEY, Language.ENGLISH_KEY}) static final String gloss = "hour";
        @ItemFrame(key = {CoreVocabulary.Symbol.KEY}) static final String sym = "h";
        @ItemFrame(key = {CoreVocabulary.Lexeme.KEY, Language.ENGLISH_KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}) static final String word = "hour";
    }

    // ==================================================================================
    // SEED INSTANCES - Mass
    // ==================================================================================

    @Implements(Kilogram.KEY) @ItemSeed(key = Kilogram.KEY)
    public static class Kilogram extends Unit {
        public static final String KEY = "cg.unit:kilogram";
        public static final ItemID IID = ItemID.fromString(KEY);
        Kilogram() { super(KEY, "kg", Map.of("en", "kilogram"), Map.of(dim(Dimension.Mass.KEY), 1), 1, 1); }
        @ItemFrame(key = {SememeGloss.KEY, Language.ENGLISH_KEY}) static final String gloss = "kilogram";
        @ItemFrame(key = {CoreVocabulary.Symbol.KEY}) static final String sym = "kg";
        @ItemFrame(key = {CoreVocabulary.Lexeme.KEY, Language.ENGLISH_KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}) static final String word = "kilogram";
    }
    @Implements(Gram.KEY) @ItemSeed(key = Gram.KEY)
    public static class Gram extends Unit {
        public static final String KEY = "cg.unit:gram";
        public static final ItemID IID = ItemID.fromString(KEY);
        Gram() { super(KEY, "g", Map.of("en", "gram"), Map.of(dim(Dimension.Mass.KEY), 1), 1, 1000); }
        @ItemFrame(key = {SememeGloss.KEY, Language.ENGLISH_KEY}) static final String gloss = "gram";
        @ItemFrame(key = {CoreVocabulary.Symbol.KEY}) static final String sym = "g";
        @ItemFrame(key = {CoreVocabulary.Lexeme.KEY, Language.ENGLISH_KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}) static final String word = "gram";
    }
    @Implements(Pound.KEY) @ItemSeed(key = Pound.KEY)
    public static class Pound extends Unit {
        public static final String KEY = "cg.unit:pound";
        public static final ItemID IID = ItemID.fromString(KEY);
        Pound() { super(KEY, "lb", Map.of("en", "pound"), Map.of(dim(Dimension.Mass.KEY), 1), 45359237, 100000000); }
        @ItemFrame(key = {SememeGloss.KEY, Language.ENGLISH_KEY}) static final String gloss = "pound";
        @ItemFrame(key = {CoreVocabulary.Symbol.KEY}) static final String sym = "lb";
        @ItemFrame(key = {CoreVocabulary.Lexeme.KEY, Language.ENGLISH_KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}) static final String word = "pound";
    }

    // ==================================================================================
    // SEED INSTANCES - Derived/Compound
    // ==================================================================================

    @Implements(MeterPerSecond.KEY) @ItemSeed(key = MeterPerSecond.KEY)
    public static class MeterPerSecond extends Unit {
        public static final String KEY = "cg.unit:meter-per-second";
        public static final ItemID IID = ItemID.fromString(KEY);
        MeterPerSecond() { super(KEY, "m/s", Map.of("en", "meter per second"), Map.of(dim(Dimension.Length.KEY), 1, dim(Dimension.Time.KEY), -1), 1, 1); }
        @ItemFrame(key = {SememeGloss.KEY, Language.ENGLISH_KEY}) static final String gloss = "meter per second";
        @ItemFrame(key = {CoreVocabulary.Symbol.KEY}) static final String sym = "m/s";
        @ItemFrame(key = {CoreVocabulary.Lexeme.KEY, Language.ENGLISH_KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}) static final String word = "velocity";
    }
    @Implements(Newton.KEY) @ItemSeed(key = Newton.KEY)
    public static class Newton extends Unit {
        public static final String KEY = "cg.unit:newton";
        public static final ItemID IID = ItemID.fromString(KEY);
        Newton() { super(KEY, "N", Map.of("en", "newton"), Map.of(dim(Dimension.Mass.KEY), 1, dim(Dimension.Length.KEY), 1, dim(Dimension.Time.KEY), -2), 1, 1); }
        @ItemFrame(key = {SememeGloss.KEY, Language.ENGLISH_KEY}) static final String gloss = "newton";
        @ItemFrame(key = {CoreVocabulary.Symbol.KEY}) static final String sym = "N";
        @ItemFrame(key = {CoreVocabulary.Lexeme.KEY, Language.ENGLISH_KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}) static final String word = "newton";
    }
    @Implements(Joule.KEY) @ItemSeed(key = Joule.KEY)
    public static class Joule extends Unit {
        public static final String KEY = "cg.unit:joule";
        public static final ItemID IID = ItemID.fromString(KEY);
        Joule() { super(KEY, "J", Map.of("en", "joule"), Map.of(dim(Dimension.Mass.KEY), 1, dim(Dimension.Length.KEY), 2, dim(Dimension.Time.KEY), -2), 1, 1); }
        @ItemFrame(key = {SememeGloss.KEY, Language.ENGLISH_KEY}) static final String gloss = "joule";
        @ItemFrame(key = {CoreVocabulary.Symbol.KEY}) static final String sym = "J";
        @ItemFrame(key = {CoreVocabulary.Lexeme.KEY, Language.ENGLISH_KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}) static final String word = "joule";
    }
    @Implements(Watt.KEY) @ItemSeed(key = Watt.KEY)
    public static class Watt extends Unit {
        public static final String KEY = "cg.unit:watt";
        public static final ItemID IID = ItemID.fromString(KEY);
        Watt() { super(KEY, "W", Map.of("en", "watt"), Map.of(dim(Dimension.Mass.KEY), 1, dim(Dimension.Length.KEY), 2, dim(Dimension.Time.KEY), -3), 1, 1); }
        @ItemFrame(key = {SememeGloss.KEY, Language.ENGLISH_KEY}) static final String gloss = "watt";
        @ItemFrame(key = {CoreVocabulary.Symbol.KEY}) static final String sym = "W";
        @ItemFrame(key = {CoreVocabulary.Lexeme.KEY, Language.ENGLISH_KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}) static final String word = "watt";
    }

    // ==================================================================================
    // SEED INSTANCES - UI/Layout Units (contextual lengths and ratios)
    // ==================================================================================

    @Implements(CharacterWidth.KEY) @ItemSeed(key = CharacterWidth.KEY)
    public static class CharacterWidth extends Unit {
        public static final String KEY = "cg.unit:ch";
        public static final ItemID IID = ItemID.fromString(KEY);
        CharacterWidth() { super(KEY, "ch", Map.of("en", "character width"), Map.of(dim(Dimension.Length.KEY), 1), 1, 1); }
        @ItemFrame(key = {SememeGloss.KEY, Language.ENGLISH_KEY}) static final String gloss = "character width";
        @ItemFrame(key = {CoreVocabulary.Symbol.KEY}) static final String sym = "ch";
        @ItemFrame(key = {CoreVocabulary.Lexeme.KEY, Language.ENGLISH_KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}) static final String word = "character width";
    }
    @Implements(LineHeight.KEY) @ItemSeed(key = LineHeight.KEY)
    public static class LineHeight extends Unit {
        public static final String KEY = "cg.unit:ln";
        public static final ItemID IID = ItemID.fromString(KEY);
        LineHeight() { super(KEY, "ln", Map.of("en", "line height"), Map.of(dim(Dimension.Length.KEY), 1), 1, 1); }
        @ItemFrame(key = {SememeGloss.KEY, Language.ENGLISH_KEY}) static final String gloss = "line height";
        @ItemFrame(key = {CoreVocabulary.Symbol.KEY}) static final String sym = "ln";
        @ItemFrame(key = {CoreVocabulary.Lexeme.KEY, Language.ENGLISH_KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}) static final String word = "line height";
    }
    @Implements(Pixel.KEY) @ItemSeed(key = Pixel.KEY)
    public static class Pixel extends Unit {
        public static final String KEY = "cg.unit:px";
        public static final ItemID IID = ItemID.fromString(KEY);
        Pixel() { super(KEY, "px", Map.of("en", "pixel"), Map.of(dim(Dimension.Length.KEY), 1), 127, 4838400); }
        @ItemFrame(key = {SememeGloss.KEY, Language.ENGLISH_KEY}) static final String gloss = "pixel";
        @ItemFrame(key = {CoreVocabulary.Symbol.KEY}) static final String sym = "px";
        @ItemFrame(key = {CoreVocabulary.Lexeme.KEY, Language.ENGLISH_KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}) static final String word = "pixel";
    }
    @Implements(Percent.KEY) @ItemSeed(key = Percent.KEY)
    public static class Percent extends Unit {
        public static final String KEY = "cg.unit:percent";
        public static final ItemID IID = ItemID.fromString(KEY);
        Percent() { super(KEY, "%", Map.of("en", "percent"), Map.of(), 1, 100); }
        @ItemFrame(key = {SememeGloss.KEY, Language.ENGLISH_KEY}) static final String gloss = "percent";
        @ItemFrame(key = {CoreVocabulary.Symbol.KEY}) static final String sym = "%";
        @ItemFrame(key = {CoreVocabulary.Lexeme.KEY, Language.ENGLISH_KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}) static final String word = "percent";
    }
    @Implements(Fraction.KEY) @ItemSeed(key = Fraction.KEY)
    public static class Fraction extends Unit {
        public static final String KEY = "cg.unit:fr";
        public static final ItemID IID = ItemID.fromString(KEY);
        Fraction() { super(KEY, "fr", Map.of("en", "fraction"), Map.of(), 1, 1); }
        @ItemFrame(key = {SememeGloss.KEY, Language.ENGLISH_KEY}) static final String gloss = "fraction";
        @ItemFrame(key = {CoreVocabulary.Symbol.KEY}) static final String sym = "fr";
        @ItemFrame(key = {CoreVocabulary.Lexeme.KEY, Language.ENGLISH_KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}) static final String word = "fraction";
    }
    @Implements(Em.KEY) @ItemSeed(key = Em.KEY)
    public static class Em extends Unit {
        public static final String KEY = "cg.unit:em";
        public static final ItemID IID = ItemID.fromString(KEY);
        Em() { super(KEY, "em", Map.of("en", "em"), Map.of(dim(Dimension.Length.KEY), 1), 1, 1); }
        @ItemFrame(key = {SememeGloss.KEY, Language.ENGLISH_KEY}) static final String gloss = "em";
        @ItemFrame(key = {CoreVocabulary.Symbol.KEY}) static final String sym = "em";
        @ItemFrame(key = {CoreVocabulary.Lexeme.KEY, Language.ENGLISH_KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}) static final String word = "em";
    }
    @Implements(Rem.KEY) @ItemSeed(key = Rem.KEY)
    public static class Rem extends Unit {
        public static final String KEY = "cg.unit:rem";
        public static final ItemID IID = ItemID.fromString(KEY);
        Rem() { super(KEY, "rem", Map.of("en", "root em"), Map.of(dim(Dimension.Length.KEY), 1), 1, 1); }
        @ItemFrame(key = {SememeGloss.KEY, Language.ENGLISH_KEY}) static final String gloss = "root em";
        @ItemFrame(key = {CoreVocabulary.Symbol.KEY}) static final String sym = "rem";
        @ItemFrame(key = {CoreVocabulary.Lexeme.KEY, Language.ENGLISH_KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}) static final String word = "rem";
    }

    // ==================================================================================
    // SEED LOOKUP
    // ==================================================================================

    // Lazy holder to avoid circular static init (HashID -> Unit.CharacterWidth -> Unit.<clinit>)
    private static class Seeds {
        static final List<Unit> ALL = List.of(
                new Meter(), new Millimeter(), new Centimeter(), new Kilometer(), new Inch(), new Foot(),
                new Second(), new Millisecond(), new Minute(), new Hour(),
                new Kilogram(), new Gram(), new Pound(),
                new MeterPerSecond(), new Newton(), new Joule(), new Watt(),
                new CharacterWidth(), new LineHeight(), new Pixel(), new Percent(), new Fraction(), new Em(), new Rem()
        );
        static final Map<ItemID, Unit> BY_ID = buildById();
        private static Map<ItemID, Unit> buildById() {
            Map<ItemID, Unit> out = new LinkedHashMap<>();
            for (Unit u : ALL) out.put(u.iid(), u);
            return Map.copyOf(out);
        }
    }

    /** Look up a seed unit by IID. Returns null if not a known seed unit. */
    public static Unit lookupSeed(ItemID iid) {
        return iid != null ? Seeds.BY_ID.get(iid) : null;
    }

    /** Look up a seed unit by symbol (e.g., "em", "px", "m"). */
    public static Unit lookupBySymbol(String symbol) {
        if (symbol == null) return null;
        for (Unit u : Seeds.ALL) {
            if (symbol.equals(u.symbol())) return u;
        }
        return null;
    }

    /** Get all seed units. */
    public static List<Unit> seeds() {
        return Seeds.ALL;
    }


    // ==================================================================================
    // INSTANCE FIELDS
    // ==================================================================================

    /** Short symbol (e.g., "m", "mm", "in") */
    @Getter
    private String symbol;

    /** Language-tagged display names */
    @Getter
    @ItemFrame(key = {CoreVocabulary.Names.KEY})
    private Map<String, String> names;

    /**
     * Dimensional formula: maps Dimension IID to exponent.
     * e.g., velocity = {LENGTH: 1, TIME: -1}
     */
    @Getter
    @ItemFrame(key = {CoreVocabulary.DimensionFormula.KEY})
    private Map<ItemID, Integer> dimensions;

    /**
     * Rational scale numerator for conversion to base unit.
     * value_in_base = value_in_this * (scaleP / scaleQ)
     */
    @Getter
    @ItemFrame(key = {CoreVocabulary.ScaleNumerator.KEY})
    private long scaleP;

    /**
     * Rational scale denominator for conversion to base unit.
     */
    @Getter
    @ItemFrame(key = {CoreVocabulary.ScaleDenominator.KEY})
    private long scaleQ;

    // ==================================================================================
    // CONSTRUCTORS
    // ==================================================================================

    /**
     * Create a seed unit (no librarian, deterministic IID from key).
     */
    protected Unit(String canonicalKey, String symbol, Map<String, String> names,
                   Map<ItemID, Integer> dimensions, long scaleP, long scaleQ) {
        super(canonicalKey);
        this.symbol = symbol;
        this.names = Map.copyOf(names);
        this.dimensions = Map.copyOf(dimensions);
        this.scaleP = scaleP;
        this.scaleQ = scaleQ;
        normalizeScale();
    }

    /**
     * Type seed constructor.
     */
    protected Unit(ItemID typeId) {
        super(typeId);
    }

    /**
     * Hydration constructor.
     */
    @SuppressWarnings("unused")
    protected Unit(Librarian librarian, Manifest manifest) {
        super(librarian, manifest);
    }


    // ==================================================================================
    // SCALE NORMALIZATION
    // ==================================================================================

    private void normalizeScale() {
        if (scaleQ == 0L) throw new IllegalArgumentException("scaleQ cannot be zero");
        if (scaleQ < 0L) { scaleQ = -scaleQ; scaleP = -scaleP; }
        if (scaleP == 0L) { scaleP = 0L; scaleQ = 1L; return; }
        long g = gcd(Math.abs(scaleP), scaleQ);
        if (g > 1L) { scaleP /= g; scaleQ /= g; }
    }

    // ==================================================================================
    // CONVENIENCE METHODS
    // ==================================================================================

    /**
     * Get display name for a language.
     */
    public String name(String lang) {
        return names != null ? names.get(lang) : null;
    }

    /**
     * Get English name.
     */
    public String nameEn() {
        return names != null ? names.get("en") : null;
    }

    /**
     * Check if this unit has a given dimension.
     */
    public boolean hasDimension(Dimension dim) {
        return dimensions != null && dimensions.containsKey(dim.iid()) && dimensions.get(dim.iid()) != 0;
    }

    /**
     * Get exponent for a dimension (0 if not present).
     */
    public int exponent(Dimension dim) {
        return dimensions != null ? dimensions.getOrDefault(dim.iid(), 0) : 0;
    }

    /**
     * Check if this unit is dimensionless (all exponents are 0 or map is empty).
     */
    public boolean isDimensionless() {
        return dimensions == null || dimensions.isEmpty() ||
               dimensions.values().stream().allMatch(exp -> exp == 0);
    }

    /**
     * Check if two units have the same dimensions (can be converted).
     */
    public boolean isCompatibleWith(Unit other) {
        if (dimensions == null || other.dimensions == null) {
            return dimensions == null && other.dimensions == null;
        }
        // Same dimensions with same exponents
        if (this.dimensions.size() != other.dimensions.size()) {
            return false;
        }
        for (var entry : this.dimensions.entrySet()) {
            Integer otherExp = other.dimensions.get(entry.getKey());
            if (otherExp == null || !otherExp.equals(entry.getValue())) {
                return false;
            }
        }
        return true;
    }

    /**
     * Convert a value from this unit to another compatible unit.
     *
     * @param value The value in this unit
     * @param target The target unit
     * @return The value in the target unit
     * @throws IllegalArgumentException if units are not compatible
     */
    public double convert(double value, Unit target) {
        if (!isCompatibleWith(target)) {
            throw new IllegalArgumentException(
                    "Cannot convert from " + symbol + " to " + target.symbol +
                    " - incompatible dimensions");
        }
        // value_base = value * scaleP / scaleQ
        // value_target = value_base * target.scaleQ / target.scaleP
        return value * scaleP * target.scaleQ / (scaleQ * target.scaleP);
    }

    @Override
    public String toString() {
        return symbol + " (" + nameEn() + ")";
    }

    @Override
    public String displayToken() {
        String en = nameEn();
        if (en != null && symbol != null && !symbol.equals(en)) {
            return en + " (" + symbol + ")";
        }
        return en != null ? en : (symbol != null ? symbol : getClass().getSimpleName());
    }

    @Override
    public Stream<TokenEntry> extractTokens() {
        List<TokenEntry> tokens = new ArrayList<>();

        // High priority: the symbol (e.g., "mm", "kg")
        if (symbol != null && !symbol.isBlank()) {
            tokens.add(new TokenEntry(symbol, 1.0f));
        }

        // All language-tagged names (e.g., "millimeter", "metre")
        if (names != null) {
            for (String name : names.values()) {
                if (name != null && !name.isBlank()) {
                    tokens.add(new TokenEntry(name, 0.9f));
                }
            }
        }

        return tokens.stream();
    }

    /** Greatest common divisor (non-negative inputs preferred). Returns >= 0. */
    public static long gcd(long a, long b) {
        if (a < 0) a = -a;
        if (b < 0) b = -b;
        if (a == 0) return b;
        if (b == 0) return a;
        while (b != 0) {
            long t = a % b;
            a = b;
            b = t;
        }
        return a;
    }
}
