package dev.everydaythings.graph.value;

import dev.everydaythings.graph.item.Item;
import dev.everydaythings.graph.item.Item.Seed;
import dev.everydaythings.graph.item.Manifest;
import dev.everydaythings.graph.item.component.Type;
import dev.everydaythings.graph.item.id.ItemID;
import dev.everydaythings.graph.item.user.Signer;
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
@Type(value = Unit.KEY, glyph = "⚖️", color = 0x609080)
public class Unit extends Item {

    // ==================================================================================
    // TYPE DEFINITION
    // ==================================================================================

    public static final String KEY = "cg:type/unit";

    /** Helper: deterministic IID from canonical key (avoids triggering class init on Dimension). */
    private static ItemID dim(String key) { return ItemID.fromString(key); }

    // ==================================================================================
    // SEED INSTANCES - Length
    // ==================================================================================

    public static class Meter {
        public static final String KEY = "cg.unit:meter";
        @Seed public static final Unit SEED = new Unit(KEY, "m",
                Map.of("en", "meter", "en-GB", "metre"),
                Map.of(dim(Dimension.Length.KEY), 1), 1, 1);
    }
    public static class Millimeter {
        public static final String KEY = "cg.unit:millimeter";
        @Seed public static final Unit SEED = new Unit(KEY, "mm",
                Map.of("en", "millimeter", "en-GB", "millimetre"),
                Map.of(dim(Dimension.Length.KEY), 1), 1, 1000);
    }
    public static class Centimeter {
        public static final String KEY = "cg.unit:centimeter";
        @Seed public static final Unit SEED = new Unit(KEY, "cm",
                Map.of("en", "centimeter", "en-GB", "centimetre"),
                Map.of(dim(Dimension.Length.KEY), 1), 1, 100);
    }
    public static class Kilometer {
        public static final String KEY = "cg.unit:kilometer";
        @Seed public static final Unit SEED = new Unit(KEY, "km",
                Map.of("en", "kilometer", "en-GB", "kilometre"),
                Map.of(dim(Dimension.Length.KEY), 1), 1000, 1);
    }
    public static class Inch {
        public static final String KEY = "cg.unit:inch";
        @Seed public static final Unit SEED = new Unit(KEY, "in",
                Map.of("en", "inch"),
                Map.of(dim(Dimension.Length.KEY), 1), 127, 5000);
    }
    public static class Foot {
        public static final String KEY = "cg.unit:foot";
        @Seed public static final Unit SEED = new Unit(KEY, "ft",
                Map.of("en", "foot"),
                Map.of(dim(Dimension.Length.KEY), 1), 381, 1250);
    }

    // ==================================================================================
    // SEED INSTANCES - Time
    // ==================================================================================

    public static class Second {
        public static final String KEY = "cg.unit:second";
        @Seed public static final Unit SEED = new Unit(KEY, "s",
                Map.of("en", "second"),
                Map.of(dim(Dimension.Time.KEY), 1), 1, 1);
    }
    public static class Millisecond {
        public static final String KEY = "cg.unit:millisecond";
        @Seed public static final Unit SEED = new Unit(KEY, "ms",
                Map.of("en", "millisecond"),
                Map.of(dim(Dimension.Time.KEY), 1), 1, 1000);
    }
    public static class Minute {
        public static final String KEY = "cg.unit:minute";
        @Seed public static final Unit SEED = new Unit(KEY, "min",
                Map.of("en", "minute"),
                Map.of(dim(Dimension.Time.KEY), 1), 60, 1);
    }
    public static class Hour {
        public static final String KEY = "cg.unit:hour";
        @Seed public static final Unit SEED = new Unit(KEY, "h",
                Map.of("en", "hour"),
                Map.of(dim(Dimension.Time.KEY), 1), 3600, 1);
    }

    // ==================================================================================
    // SEED INSTANCES - Mass
    // ==================================================================================

    public static class Kilogram {
        public static final String KEY = "cg.unit:kilogram";
        @Seed public static final Unit SEED = new Unit(KEY, "kg",
                Map.of("en", "kilogram"),
                Map.of(dim(Dimension.Mass.KEY), 1), 1, 1);
    }
    public static class Gram {
        public static final String KEY = "cg.unit:gram";
        @Seed public static final Unit SEED = new Unit(KEY, "g",
                Map.of("en", "gram"),
                Map.of(dim(Dimension.Mass.KEY), 1), 1, 1000);
    }
    public static class Pound {
        public static final String KEY = "cg.unit:pound";
        @Seed public static final Unit SEED = new Unit(KEY, "lb",
                Map.of("en", "pound"),
                Map.of(dim(Dimension.Mass.KEY), 1), 45359237, 100000000);
    }

    // ==================================================================================
    // SEED INSTANCES - Derived/Compound
    // ==================================================================================

    public static class MeterPerSecond {
        public static final String KEY = "cg.unit:meter-per-second";
        @Seed public static final Unit SEED = new Unit(KEY, "m/s",
                Map.of("en", "meter per second"),
                Map.of(dim(Dimension.Length.KEY), 1, dim(Dimension.Time.KEY), -1), 1, 1);
    }
    public static class Newton {
        public static final String KEY = "cg.unit:newton";
        @Seed public static final Unit SEED = new Unit(KEY, "N",
                Map.of("en", "newton"),
                Map.of(dim(Dimension.Mass.KEY), 1, dim(Dimension.Length.KEY), 1, dim(Dimension.Time.KEY), -2), 1, 1);
    }
    public static class Joule {
        public static final String KEY = "cg.unit:joule";
        @Seed public static final Unit SEED = new Unit(KEY, "J",
                Map.of("en", "joule"),
                Map.of(dim(Dimension.Mass.KEY), 1, dim(Dimension.Length.KEY), 2, dim(Dimension.Time.KEY), -2), 1, 1);
    }
    public static class Watt {
        public static final String KEY = "cg.unit:watt";
        @Seed public static final Unit SEED = new Unit(KEY, "W",
                Map.of("en", "watt"),
                Map.of(dim(Dimension.Mass.KEY), 1, dim(Dimension.Length.KEY), 2, dim(Dimension.Time.KEY), -3), 1, 1);
    }

    // ==================================================================================
    // SEED INSTANCES - UI/Layout Units (contextual lengths and ratios)
    // ==================================================================================

    public static class CharacterWidth {
        public static final String KEY = "cg.unit:ch";
        @Seed public static final Unit SEED = new Unit(KEY, "ch",
                Map.of("en", "character width", "en-alt", "ch"),
                Map.of(dim(Dimension.Length.KEY), 1), 1, 1);
    }
    public static class LineHeight {
        public static final String KEY = "cg.unit:ln";
        @Seed public static final Unit SEED = new Unit(KEY, "ln",
                Map.of("en", "line height", "en-alt", "line"),
                Map.of(dim(Dimension.Length.KEY), 1), 1, 1);
    }
    public static class Pixel {
        public static final String KEY = "cg.unit:px";
        @Seed public static final Unit SEED = new Unit(KEY, "px",
                Map.of("en", "pixel"),
                Map.of(dim(Dimension.Length.KEY), 1), 127, 4838400);
    }
    public static class Percent {
        public static final String KEY = "cg.unit:percent";
        @Seed public static final Unit SEED = new Unit(KEY, "%",
                Map.of("en", "percent"),
                Map.of(), 1, 100);
    }
    public static class Fraction {
        public static final String KEY = "cg.unit:fr";
        @Seed public static final Unit SEED = new Unit(KEY, "fr",
                Map.of("en", "fraction", "en-alt", "flex fraction"),
                Map.of(), 1, 1);
    }
    public static class Em {
        public static final String KEY = "cg.unit:em";
        @Seed public static final Unit SEED = new Unit(KEY, "em",
                Map.of("en", "em"),
                Map.of(dim(Dimension.Length.KEY), 1), 1, 1);
    }
    public static class Rem {
        public static final String KEY = "cg.unit:rem";
        @Seed public static final Unit SEED = new Unit(KEY, "rem",
                Map.of("en", "root em"),
                Map.of(dim(Dimension.Length.KEY), 1), 1, 1);
    }

    // ==================================================================================
    // SEED LOOKUP
    // ==================================================================================

    // Lazy holder to avoid circular static init (HashID -> Unit.CharacterWidth -> Unit.<clinit>)
    private static class Seeds {
        static final List<Unit> ALL = List.of(
                Meter.SEED, Millimeter.SEED, Centimeter.SEED, Kilometer.SEED, Inch.SEED, Foot.SEED,
                Second.SEED, Millisecond.SEED, Minute.SEED, Hour.SEED,
                Kilogram.SEED, Gram.SEED, Pound.SEED,
                MeterPerSecond.SEED, Newton.SEED, Joule.SEED, Watt.SEED,
                CharacterWidth.SEED, LineHeight.SEED, Pixel.SEED, Percent.SEED, Fraction.SEED, Em.SEED, Rem.SEED
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

    /** Get all seed units. */
    public static List<Unit> seeds() {
        return Seeds.ALL;
    }


    // ==================================================================================
    // INSTANCE FIELDS
    // ==================================================================================

    /** The canonical key (e.g., "cg.unit:meter") */
    @Getter
    @Frame(handle = "key")
    private String canonicalKey;

    /** Short symbol (e.g., "m", "mm", "in") */
    @Getter
    @Frame
    private String symbol;

    /** Language-tagged display names */
    @Getter
    @Frame
    private Map<String, String> names;

    /**
     * Dimensional formula: maps Dimension IID to exponent.
     * e.g., velocity = {LENGTH: 1, TIME: -1}
     */
    @Getter
    @Frame
    private Map<ItemID, Integer> dimensions;

    /**
     * Rational scale numerator for conversion to base unit.
     * value_in_base = value_in_this * (scaleP / scaleQ)
     */
    @Getter
    @Frame
    private long scaleP;

    /**
     * Rational scale denominator for conversion to base unit.
     */
    @Getter
    @Frame
    private long scaleQ;

    // ==================================================================================
    // CONSTRUCTORS
    // ==================================================================================

    /**
     * Create a seed unit (no librarian, deterministic IID from key).
     */
    public Unit(String canonicalKey, String symbol, Map<String, String> names,
                Map<ItemID, Integer> dimensions, long scaleP, long scaleQ) {
        super(ItemID.fromString(canonicalKey));
        this.canonicalKey = canonicalKey;
        this.symbol = symbol;
        this.names = Map.copyOf(names);
        this.dimensions = Map.copyOf(dimensions);
        this.scaleP = scaleP;
        this.scaleQ = scaleQ;
        normalizeScale();
    }

    /**
     * Create a unit with a librarian (for runtime creation).
     */
    public Unit(Librarian librarian, String canonicalKey, String symbol,
                Map<String, String> names, Map<ItemID, Integer> dimensions,
                long scaleP, long scaleQ) {
        super(librarian, ItemID.fromString(canonicalKey));
        this.canonicalKey = canonicalKey;
        this.symbol = symbol;
        this.names = Map.copyOf(names);
        this.dimensions = Map.copyOf(dimensions);
        this.scaleP = scaleP;
        this.scaleQ = scaleQ;
        normalizeScale();
    }

    /**
     * Type seed constructor - creates a minimal Unit for use as type seed.
     *
     * <p>Used by SeedStore to create the "cg:type/unit" type item.
     */
    @SuppressWarnings("unused")  // Used via reflection by SeedStore
    protected Unit(ItemID typeId) {
        super(typeId);
    }

    /**
     * Hydration constructor - reconstructs a Unit from a stored manifest.
     *
     * <p>Fields are bound via reflection in the base class hydrate() method.
     */
    @SuppressWarnings("unused")  // Used via reflection for hydration
    private Unit(Librarian librarian, Manifest manifest) {
        super(librarian, manifest);
        // Fields are set by bindFieldsFromTable() via reflection during super() call
        // Do NOT assign values here - it would overwrite what hydration set!
    }

    /**
     * Create and commit a unit.
     */
    public static Unit create(Librarian librarian, Signer signer,
                              String canonicalKey, String symbol,
                              Map<String, String> names, Map<ItemID, Integer> dimensions,
                              long scaleP, long scaleQ) {
        Unit unit = new Unit(librarian, canonicalKey, symbol, names, dimensions, scaleP, scaleQ);
        unit.commit(signer);
        return unit;
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
