package dev.everydaythings.graph.value;

import dev.everydaythings.graph.item.Item;
import dev.everydaythings.graph.item.Manifest;
import dev.everydaythings.graph.item.component.Type;
import dev.everydaythings.graph.item.id.ItemID;
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
    @Seed
    public static final Dimension LENGTH = new Dimension(
            "cg.dim:length", "L", "length",
            Map.of("en", "spatial extent in one direction")
    );

    /** Time dimension (T) - SI base: second */
    @Seed
    public static final Dimension TIME = new Dimension(
            "cg.dim:time", "T", "time",
            Map.of("en", "duration of events")
    );

    /** Mass dimension (M) - SI base: kilogram */
    @Seed
    public static final Dimension MASS = new Dimension(
            "cg.dim:mass", "M", "mass",
            Map.of("en", "quantity of matter")
    );

    /** Electric current dimension (I) - SI base: ampere */
    @Seed
    public static final Dimension ELECTRIC_CURRENT = new Dimension(
            "cg.dim:electric-current", "I", "electric current",
            Map.of("en", "flow of electric charge")
    );

    /** Thermodynamic temperature dimension (Θ) - SI base: kelvin */
    @Seed
    public static final Dimension TEMPERATURE = new Dimension(
            "cg.dim:temperature", "Θ", "temperature",
            Map.of("en", "average kinetic energy of particles")
    );

    /** Amount of substance dimension (N) - SI base: mole */
    @Seed
    public static final Dimension AMOUNT = new Dimension(
            "cg.dim:amount", "N", "amount of substance",
            Map.of("en", "number of elementary entities")
    );

    /** Luminous intensity dimension (J) - SI base: candela */
    @Seed
    public static final Dimension LUMINOUS_INTENSITY = new Dimension(
            "cg.dim:luminous-intensity", "J", "luminous intensity",
            Map.of("en", "luminous power per solid angle")
    );

    // ==================================================================================
    // INSTANCE FIELDS
    // ==================================================================================

    /** The canonical key (e.g., "cg.dim:length") */
    @Getter
    @Frame(handle = "key")
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
