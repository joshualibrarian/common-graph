package dev.everydaythings.graph.value;

import dev.everydaythings.graph.item.Item;
import dev.everydaythings.graph.item.Manifest;
import dev.everydaythings.graph.item.Type;
import dev.everydaythings.graph.item.id.ItemID;
import dev.everydaythings.graph.language.CoreVocabulary;
import dev.everydaythings.graph.runtime.Librarian;
import lombok.Getter;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * A physical dimension (length, time, mass, etc.) as a first-class Item.
 *
 * <p>This is a self-describing type. The class IS the definition.
 *
 * <p>Dimensions are the basis for dimensional analysis in units and quantities.
 * Dimensions are Items that can be discovered, extended, and reasoned about.
 *
 * <p>Core dimensions (SI base quantities):
 * <ul>
 *   <li>LENGTH (L) - meter</li>
 *   <li>TIME (T) - second</li>
 *   <li>MASS (M) - kilogram</li>
 *   <li>ELECTRIC_CURRENT (I) - ampere</li>
 *   <li>TEMPERATURE (Θ) - kelvin</li>
 *   <li>AMOUNT (N) - mole</li>
 *   <li>LUMINOUS_INTENSITY (J) - candela</li>
 * </ul>
 *
 * <p>Usage:
 * <pre>{@code
 * // Reference a dimension
 * Dimension len = Dimension.LENGTH;
 * ItemID lenId = len.iid();
 *
 * // Dimensional analysis: velocity = length / time
 * DimensionExpr velocity = DimensionExpr.of(Dimension.LENGTH, 1)
 *                                        .with(Dimension.TIME, -1);
 * }</pre>
 */
@Type(value = Dimension.KEY, glyph = "📐", color = 0x8060A0)
public class Dimension extends Item {

    // ==================================================================================
    // TYPE DEFINITION
    // ==================================================================================

    public static final String KEY = "cg:type/dimension";


    // ==================================================================================
    // SEED INSTANCES (SI base dimensions)
    // ==================================================================================

    /** Length dimension (L) - SI base: meter */
    public static class Length {
        public static final String KEY = "cg.dim:length";
        @Seed public static final Dimension SEED = new Dimension(
                KEY, "L", "length", Map.of("en", "spatial extent in one direction"));
    }

    /** Time dimension (T) - SI base: second */
    public static class Time {
        public static final String KEY = "cg.dim:time";
        @Seed public static final Dimension SEED = new Dimension(
                KEY, "T", "time", Map.of("en", "duration of events"));
    }

    /** Mass dimension (M) - SI base: kilogram */
    public static class Mass {
        public static final String KEY = "cg.dim:mass";
        @Seed public static final Dimension SEED = new Dimension(
                KEY, "M", "mass", Map.of("en", "quantity of matter"));
    }

    /** Electric current dimension (I) - SI base: ampere */
    public static class ElectricCurrent {
        public static final String KEY = "cg.dim:electric-current";
        @Seed public static final Dimension SEED = new Dimension(
                KEY, "I", "electric current", Map.of("en", "flow of electric charge"));
    }

    /** Thermodynamic temperature dimension (Θ) - SI base: kelvin */
    public static class Temperature {
        public static final String KEY = "cg.dim:temperature";
        @Seed public static final Dimension SEED = new Dimension(
                KEY, "Θ", "temperature", Map.of("en", "average kinetic energy of particles"));
    }

    /** Amount of substance dimension (N) - SI base: mole */
    public static class Amount {
        public static final String KEY = "cg.dim:amount";
        @Seed public static final Dimension SEED = new Dimension(
                KEY, "N", "amount of substance", Map.of("en", "number of elementary entities"));
    }

    /** Luminous intensity dimension (J) - SI base: candela */
    public static class LuminousIntensity {
        public static final String KEY = "cg.dim:luminous-intensity";
        @Seed public static final Dimension SEED = new Dimension(
                KEY, "J", "luminous intensity", Map.of("en", "luminous power per solid angle"));
    }


    // ==================================================================================
    // INSTANCE FIELDS
    // ==================================================================================

    /** The canonical key (e.g., "cg.dim:length") */
    @Getter
    @Frame(key = {CoreVocabulary.HashKey.KEY})
    private String canonicalKey;

    /** Symbol used in dimensional formulas (e.g., "L", "T", "M") */
    @Getter
    @Frame
    private String symbol;

    /** Human-readable name (e.g., "length", "time") */
    @Getter
    @Frame
    private String name;

    /** Descriptions by language */
    @Getter
    @Frame
    private Map<String, String> descriptions;

    // ==================================================================================
    // CONSTRUCTORS
    // ==================================================================================

    /**
     * Create a seed dimension (no librarian, deterministic IID from key).
     */
    public Dimension(String canonicalKey, String symbol, String name,
                     Map<String, String> descriptions) {
        super(ItemID.fromString(canonicalKey));
        this.canonicalKey = canonicalKey;
        this.symbol = symbol;
        this.name = name;
        this.descriptions = Map.copyOf(descriptions);
    }

    /**
     * Create a dimension with a librarian (for runtime creation).
     */
    public Dimension(Librarian librarian,
                     String canonicalKey, String symbol, String name,
                     Map<String, String> descriptions) {
        super(librarian, ItemID.fromString(canonicalKey));
        this.canonicalKey = canonicalKey;
        this.symbol = symbol;
        this.name = name;
        this.descriptions = Map.copyOf(descriptions);
    }

    /**
     * Type seed constructor - creates a minimal Dimension for use as type seed.
     *
     * <p>Used by SeedStore to create the "cg:type/dimension" type item.
     */
    @SuppressWarnings("unused")  // Used via reflection by SeedStore
    protected Dimension(ItemID typeId) {
        super(typeId);
    }

    /**
     * Hydration constructor - reconstructs a Dimension from a stored manifest.
     *
     * <p>Fields are bound via reflection in the base class hydrate() method.
     * The field initializations here are just defaults that get overwritten.
     */
    @SuppressWarnings("unused")  // Used via reflection for hydration
    private Dimension(Librarian librarian, Manifest manifest) {
        super(librarian, manifest);
        // Fields are set by bindFieldsFromTable() via reflection during super() call
        // Do NOT assign values here - it would overwrite what hydration set!
    }

    // ==================================================================================
    // CONVENIENCE
    // ==================================================================================

    /**
     * Get description for a language.
     */
    public String description(String lang) {
        return descriptions != null ? descriptions.get(lang) : null;
    }

    /**
     * Get English description.
     */
    public String descriptionEn() {
        return descriptions != null ? descriptions.get("en") : null;
    }

    @Override
    public String displayToken() {
        return name != null ? name : getClass().getSimpleName();
    }

    @Override
    public Stream<TokenEntry> extractTokens() {
        List<TokenEntry> tokens = new ArrayList<>();

        // Primary: the human-readable name (e.g., "length")
        if (name != null && !name.isBlank()) {
            tokens.add(new TokenEntry(name, 1.0f));
        }

        // High priority: the symbol (e.g., "L")
        if (symbol != null && !symbol.isBlank()) {
            tokens.add(new TokenEntry(symbol, 1.0f));
        }

        return tokens.stream();
    }

    @Override
    public String toString() {
        return symbol + " (" + name + ")";
    }
}
