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
 * Unit meter = Unit.METER;
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


    // ==================================================================================
    // SEED INSTANCES - Length
    // ==================================================================================

    /** Meter - SI base unit of length */
    @Seed
    public static final Unit METER = new Unit(
            "cg.unit:meter", "m",
            Map.of("en", "meter", "en-GB", "metre"),
            Map.of(Dimension.LENGTH.iid(), 1),
            1, 1
    );

    /** Millimeter - 1/1000 of a meter */
    @Seed
    public static final Unit MILLIMETER = new Unit(
            "cg.unit:millimeter", "mm",
            Map.of("en", "millimeter", "en-GB", "millimetre"),
            Map.of(Dimension.LENGTH.iid(), 1),
            1, 1000
    );

    /** Centimeter - 1/100 of a meter */
    @Seed
    public static final Unit CENTIMETER = new Unit(
            "cg.unit:centimeter", "cm",
            Map.of("en", "centimeter", "en-GB", "centimetre"),
            Map.of(Dimension.LENGTH.iid(), 1),
            1, 100
    );

    /** Kilometer - 1000 meters */
    @Seed
    public static final Unit KILOMETER = new Unit(
            "cg.unit:kilometer", "km",
            Map.of("en", "kilometer", "en-GB", "kilometre"),
            Map.of(Dimension.LENGTH.iid(), 1),
            1000, 1
    );

    /** Inch - 0.0254 meters (127/5000) */
    @Seed
    public static final Unit INCH = new Unit(
            "cg.unit:inch", "in",
            Map.of("en", "inch"),
            Map.of(Dimension.LENGTH.iid(), 1),
            127, 5000
    );

    /** Foot - 12 inches = 0.3048 meters */
    @Seed
    public static final Unit FOOT = new Unit(
            "cg.unit:foot", "ft",
            Map.of("en", "foot"),
            Map.of(Dimension.LENGTH.iid(), 1),
            381, 1250
    );

    // ==================================================================================
    // SEED INSTANCES - Time
    // ==================================================================================

    /** Second - SI base unit of time */
    @Seed
    public static final Unit SECOND = new Unit(
            "cg.unit:second", "s",
            Map.of("en", "second"),
            Map.of(Dimension.TIME.iid(), 1),
            1, 1
    );

    /** Millisecond - 1/1000 of a second */
    @Seed
    public static final Unit MILLISECOND = new Unit(
            "cg.unit:millisecond", "ms",
            Map.of("en", "millisecond"),
            Map.of(Dimension.TIME.iid(), 1),
            1, 1000
    );

    /** Minute - 60 seconds */
    @Seed
    public static final Unit MINUTE = new Unit(
            "cg.unit:minute", "min",
            Map.of("en", "minute"),
            Map.of(Dimension.TIME.iid(), 1),
            60, 1
    );

    /** Hour - 3600 seconds */
    @Seed
    public static final Unit HOUR = new Unit(
            "cg.unit:hour", "h",
            Map.of("en", "hour"),
            Map.of(Dimension.TIME.iid(), 1),
            3600, 1
    );

    // ==================================================================================
    // SEED INSTANCES - Mass
    // ==================================================================================

    /** Kilogram - SI base unit of mass */
    @Seed
    public static final Unit KILOGRAM = new Unit(
            "cg.unit:kilogram", "kg",
            Map.of("en", "kilogram"),
            Map.of(Dimension.MASS.iid(), 1),
            1, 1
    );

    /** Gram - 1/1000 of a kilogram */
    @Seed
    public static final Unit GRAM = new Unit(
            "cg.unit:gram", "g",
            Map.of("en", "gram"),
            Map.of(Dimension.MASS.iid(), 1),
            1, 1000
    );

    /** Pound (mass) - 0.45359237 kg */
    @Seed
    public static final Unit POUND = new Unit(
            "cg.unit:pound", "lb",
            Map.of("en", "pound"),
            Map.of(Dimension.MASS.iid(), 1),
            45359237, 100000000
    );

    // ==================================================================================
    // SEED INSTANCES - Derived/Compound
    // ==================================================================================

    /** Meters per second - velocity */
    @Seed
    public static final Unit METER_PER_SECOND = new Unit(
            "cg.unit:meter-per-second", "m/s",
            Map.of("en", "meter per second"),
            Map.of(Dimension.LENGTH.iid(), 1, Dimension.TIME.iid(), -1),
            1, 1
    );

    /** Newton - force (kg*m/s²) */
    @Seed
    public static final Unit NEWTON = new Unit(
            "cg.unit:newton", "N",
            Map.of("en", "newton"),
            Map.of(Dimension.MASS.iid(), 1, Dimension.LENGTH.iid(), 1, Dimension.TIME.iid(), -2),
            1, 1
    );

    /** Joule - energy (kg*m²/s²) */
    @Seed
    public static final Unit JOULE = new Unit(
            "cg.unit:joule", "J",
            Map.of("en", "joule"),
            Map.of(Dimension.MASS.iid(), 1, Dimension.LENGTH.iid(), 2, Dimension.TIME.iid(), -2),
            1, 1
    );

    /** Watt - power (kg*m²/s³) */
    @Seed
    public static final Unit WATT = new Unit(
            "cg.unit:watt", "W",
            Map.of("en", "watt"),
            Map.of(Dimension.MASS.iid(), 1, Dimension.LENGTH.iid(), 2, Dimension.TIME.iid(), -3),
            1, 1
    );

    // ==================================================================================
    // SEED INSTANCES - UI/Layout Units (contextual lengths and ratios)
    // ==================================================================================

    /**
     * Character width (ch) - contextual length unit.
     *
     * <p>Width of the "0" character in the current font context.
     * Scale is 1:1 since actual conversion is context-dependent.
     *
     * <p>In TUI: 1ch = 1 cell width
     * <p>In GUI: 1ch ≈ 0.5-0.6em depending on font
     */
    @Seed
    public static final Unit CHARACTER_WIDTH = new Unit(
            "cg.unit:ch", "ch",
            Map.of("en", "character width", "en-alt", "ch"),
            Map.of(Dimension.LENGTH.iid(), 1),
            1, 1  // Contextual - actual scale determined at render time
    );

    /**
     * Line height (ln) - contextual length unit.
     *
     * <p>Height of one line in the current font/layout context.
     * Scale is 1:1 since actual conversion is context-dependent.
     *
     * <p>In TUI: 1ln = 1 cell height
     * <p>In GUI: 1ln ≈ 1.2-1.5em depending on line-height setting
     */
    @Seed
    public static final Unit LINE_HEIGHT = new Unit(
            "cg.unit:ln", "ln",
            Map.of("en", "line height", "en-alt", "line"),
            Map.of(Dimension.LENGTH.iid(), 1),
            1, 1  // Contextual - actual scale determined at render time
    );

    /**
     * Pixel (px) - device-dependent length unit.
     *
     * <p>One device pixel. Scale varies by display density.
     * Conventionally, 96px = 1 inch on a standard display.
     */
    @Seed
    public static final Unit PIXEL = new Unit(
            "cg.unit:px", "px",
            Map.of("en", "pixel"),
            Map.of(Dimension.LENGTH.iid(), 1),
            127, 4838400  // 1 inch = 96px, 1 inch = 0.0254m → 1px = 0.0254/96 m
    );

    /**
     * Percent (%) - dimensionless ratio.
     *
     * <p>A proportion of the container or reference value.
     * 100% = 1.0 in normalized form.
     */
    @Seed
    public static final Unit PERCENT = new Unit(
            "cg.unit:percent", "%",
            Map.of("en", "percent"),
            Map.of(),  // Dimensionless
            1, 100  // 1% = 0.01 in normalized form
    );

    /**
     * Fraction (fr) - dimensionless flexible unit.
     *
     * <p>A fraction of remaining space after fixed-size items.
     * Similar to CSS Grid's fr unit.
     * 1fr represents one share of available space.
     */
    @Seed
    public static final Unit FRACTION = new Unit(
            "cg.unit:fr", "fr",
            Map.of("en", "fraction", "en-alt", "flex fraction"),
            Map.of(),  // Dimensionless
            1, 1  // 1fr = 1 share (interpreted contextually)
    );

    /**
     * Em - relative length based on font size.
     *
     * <p>1em = current element's font-size.
     * Useful for scalable typography and spacing.
     */
    @Seed
    public static final Unit EM = new Unit(
            "cg.unit:em", "em",
            Map.of("en", "em"),
            Map.of(Dimension.LENGTH.iid(), 1),
            1, 1  // Contextual
    );

    /**
     * Rem - relative length based on root font size.
     *
     * <p>1rem = root element's font-size.
     * More predictable than em for consistent sizing.
     */
    @Seed
    public static final Unit REM = new Unit(
            "cg.unit:rem", "rem",
            Map.of("en", "root em"),
            Map.of(Dimension.LENGTH.iid(), 1),
            1, 1  // Contextual
    );

    // ==================================================================================
    // SEED LOOKUP
    // ==================================================================================

    private static final List<Unit> SEED_UNITS = List.of(
            METER, MILLIMETER, CENTIMETER, KILOMETER, INCH, FOOT,
            SECOND, MILLISECOND, MINUTE, HOUR,
            KILOGRAM, GRAM, POUND,
            METER_PER_SECOND, NEWTON, JOULE, WATT,
            CHARACTER_WIDTH, LINE_HEIGHT, PIXEL, PERCENT, FRACTION, EM, REM
    );

    private static final Map<ItemID, Unit> SEED_BY_ID = buildSeedById();

    /** Look up a seed unit by IID. Returns null if not a known seed unit. */
    public static Unit lookupSeed(ItemID iid) {
        return iid != null ? SEED_BY_ID.get(iid) : null;
    }

    /** Get all seed units. */
    public static List<Unit> seeds() {
        return SEED_UNITS;
    }

    private static Map<ItemID, Unit> buildSeedById() {
        Map<ItemID, Unit> out = new LinkedHashMap<>();
        for (Unit u : SEED_UNITS) {
            out.put(u.iid(), u);
        }
        return Map.copyOf(out);
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
