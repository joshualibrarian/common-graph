package dev.everydaythings.graph.value;

import dev.everydaythings.graph.item.DisplayInfo;
import dev.everydaythings.graph.item.Item;
import dev.everydaythings.graph.item.Manifest;
import dev.everydaythings.graph.item.component.Type;
import dev.everydaythings.graph.item.id.ItemID;
import dev.everydaythings.graph.item.user.Signer;
import dev.everydaythings.graph.runtime.Librarian;
import lombok.Getter;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * A ValueType defines a type of {@link Value} that can appear in relations.
 *
 * <p>ValueTypes are Items, enabling:
 * <ul>
 *   <li>Type sharing by reference across the graph</li>
 *   <li>Third-party type packs (currencies, angles, addresses, etc.)</li>
 *   <li>Self-describing formulas and datasets</li>
 * </ul>
 *
 * <p>Value classes declare their type via {@code @Value.Type("cg.value:xxx")}
 * which references a ValueType seed item.
 *
 * <p>Usage:
 * <pre>{@code
 * // Reference a value type
 * ValueType decimal = ValueType.DECIMAL;
 * ItemID decimalId = decimal.iid();
 * }</pre>
 *
 * @see Value
 * @see Numeric
 */
@Type(value = ValueType.KEY, glyph = "🔢")
public class ValueType extends Item {

    // ==================================================================================
    // TYPE DEFINITION
    // ==================================================================================

    public static final String KEY = "cg:type/value-type";


    // ==================================================================================
    // SEED INSTANCES - Basic types
    // ==================================================================================

    public static class BooleanType {
        public static final String KEY = "cg.value:boolean";
        @Seed public static final ValueType SEED = new ValueType(KEY, "Boolean", null, null, null);
    }

    public static class TextType {
        public static final String KEY = "cg.value:text";
        @Seed public static final ValueType SEED = new ValueType(KEY, "Text", null, null, null);
    }

    public static class BytesType {
        public static final String KEY = "cg.value:bytes";
        @Seed public static final ValueType SEED = new ValueType(KEY, "Bytes", null, null, null);
    }

    public static class IpType {
        public static final String KEY = "cg.value:ip";
        @Seed public static final ValueType SEED = new ValueType(KEY, "IP Address", null, null, null);
    }

    public static class EndpointType {
        public static final String KEY = "cg.value:endpoint";
        @Seed public static final ValueType SEED = new ValueType(KEY, "Endpoint", null, null, null);
    }

    public static class InstantType {
        public static final String KEY = "cg.value:instant";
        @Seed public static final ValueType SEED = new ValueType(KEY, "Instant", null, null, null);
    }

    public static class QuantityType {
        public static final String KEY = "cg.value:quantity";
        @Seed public static final ValueType SEED = new ValueType(KEY, "Quantity", null, null,
                new UnitRules(UnitRules.AllowedDimsKind.ANY, null, false, false));
    }

    // ==================================================================================
    // SEED INSTANCES - Numeric types
    // ==================================================================================

    public static class DecimalType {
        public static final String KEY = "cg.value:decimal";
        @Seed public static final ValueType SEED = new ValueType(KEY, "Decimal",
                new CanonRules(CanonRules.Rounding.HALF_EVEN, null, true), null,
                new UnitRules(UnitRules.AllowedDimsKind.ANY, null, false, false));
    }

    public static class RationalType {
        public static final String KEY = "cg.value:rational";
        @Seed public static final ValueType SEED = new ValueType(KEY, "Rational", null, null,
                new UnitRules(UnitRules.AllowedDimsKind.ANY, null, false, false));
    }

    public static class CountType {
        public static final String KEY = "cg.value:count";
        @Seed public static final ValueType SEED = new ValueType(KEY, "Count", null, null,
                new UnitRules(UnitRules.AllowedDimsKind.DIMENSIONLESS, null, false, false));
    }

    public static class Float64Type {
        public static final String KEY = "cg.value:float64";
        @Seed public static final ValueType SEED = new ValueType(KEY, "Float64", null, null, null);
    }

    /**
     * General integer type (may have units like "5 items" or "10 pixels").
     *
     * <p>Semantic distinction from COUNT:
     * <ul>
     *   <li><b>COUNT</b> - Dimensionless integer for pure counting.</li>
     *   <li><b>INTEGER</b> - General integer that may have units.</li>
     * </ul>
     */
    public static class IntegerType {
        public static final String KEY = "cg.value:integer";
        @Seed public static final ValueType SEED = new ValueType(KEY, "Integer", null, null,
                new UnitRules(UnitRules.AllowedDimsKind.ANY, null, false, false));
    }

    // Note: No NUMBER type - CG-CBOR forbids IEEE 754 floats. Use DECIMAL or RATIONAL.

    // ==================================================================================
    // INSTANCE FIELDS
    // ==================================================================================

    /** The canonical key (e.g., "cg.value:decimal") */
    @Getter
    @Frame(handle = "key")
    private String canonicalKey;

    /** Human-readable name */
    @Getter
    @Frame
    private String name;

    /** Canonicalization rules (optional) */
    @Getter
    @Frame(handle = "canon")
    private CanonRules canonRules;

    /** Value bounds (optional) */
    @Getter
    @Frame
    private Bounds bounds;

    /** Unit rules for values of this type (optional) */
    @Getter
    @Frame
    private UnitRules unitRules;

    // ==================================================================================
    // CONSTRUCTORS
    // ==================================================================================

    /**
     * Create a seed value type (no librarian, deterministic IID from key).
     */
    public ValueType(String canonicalKey, String name,
                     CanonRules canonRules, Bounds bounds, UnitRules unitRules) {
        super(ItemID.fromString(canonicalKey));
        this.canonicalKey = canonicalKey;
        this.name = name;
        this.canonRules = canonRules;
        this.bounds = bounds;
        this.unitRules = unitRules;
    }

    /**
     * Create a value type with a librarian (for runtime creation).
     */
    public ValueType(Librarian librarian, String canonicalKey, String name,
                     CanonRules canonRules, Bounds bounds, UnitRules unitRules) {
        super(librarian, ItemID.fromString(canonicalKey));
        this.canonicalKey = canonicalKey;
        this.name = name;
        this.canonRules = canonRules;
        this.bounds = bounds;
        this.unitRules = unitRules;
    }

    /**
     * Type seed constructor - creates a minimal ValueType for use as type seed.
     *
     * <p>Used by SeedStore to create the "cg:type/value-type" type item.
     */
    @SuppressWarnings("unused")  // Used via reflection by SeedStore
    protected ValueType(ItemID typeId) {
        super(typeId);
    }

    /**
     * Hydration constructor - reconstructs a ValueType from a stored manifest.
     *
     * <p>Fields are bound via reflection in the base class hydrate() method.
     */
    @SuppressWarnings("unused")  // Used via reflection for hydration
    protected ValueType(Librarian librarian, Manifest manifest) {
        super(librarian, manifest);
        // Fields are set by bindFieldsFromTable() via reflection during super() call
        // Do NOT assign values here - it would overwrite what hydration set!
    }

    /**
     * Create and commit a value type.
     */
    public static ValueType create(Librarian librarian, Signer signer,
                                   String canonicalKey, String name,
                                   CanonRules canonRules, Bounds bounds, UnitRules unitRules) {
        ValueType valueType = new ValueType(librarian, canonicalKey, name,
                canonRules, bounds, unitRules);
        valueType.commit(signer);
        return valueType;
    }

    // ==================================================================================
    // CONVENIENCE METHODS
    // ==================================================================================

    /**
     * Check if this value type allows units.
     */
    public boolean allowsUnits() {
        return unitRules != null && unitRules.allowedKind() != UnitRules.AllowedDimsKind.DIMENSIONLESS;
    }

    // ==================================================================================
    // DISPLAY INFO
    // ==================================================================================

    @Override
    public DisplayInfo displayInfo() {
        // Delegate to parent's resolution, override name
        DisplayInfo base = super.displayInfo();
        return base.withName(name != null ? name : canonicalKey);
    }

    @Override
    public String displayToken() {
        return displayInfo().displayName();
    }

    @Override
    public String displaySubtitle() {
        return canonicalKey;
    }

    @Override
    public Stream<TokenEntry> extractTokens() {
        List<TokenEntry> tokens = new ArrayList<>();

        // Primary: the human-readable name (e.g., "Decimal", "Boolean")
        if (name != null && !name.isBlank()) {
            tokens.add(new TokenEntry(name, 1.0f));
        }

        // Also index the canonical key (e.g., "cg.value:decimal")
        if (canonicalKey != null && !canonicalKey.isBlank()) {
            tokens.add(new TokenEntry(canonicalKey, 0.9f));
            // And the short name part
            int colonIdx = canonicalKey.lastIndexOf(':');
            if (colonIdx >= 0 && colonIdx < canonicalKey.length() - 1) {
                String shortName = canonicalKey.substring(colonIdx + 1);
                if (!shortName.equalsIgnoreCase(name)) {
                    tokens.add(new TokenEntry(shortName, 0.8f));
                }
            }
        }

        return tokens.stream();
    }

    @Override
    public String toString() {
        return name;
    }
}
