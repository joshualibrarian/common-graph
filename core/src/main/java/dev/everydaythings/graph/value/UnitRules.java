package dev.everydaythings.graph.value;

import dev.everydaythings.graph.Canonical;
import dev.everydaythings.graph.item.id.ItemID;

import java.util.List;
import java.util.Map;

/**
 * Policy for values that carry units.
 *
 * <p>This is attached to ValueTypes because the value type contract dictates whether
 * unit conversions are permitted and how canonicalization happens for identity.
 *
 * <p>When allowedKind is LIST, allowedDimensions specifies which dimensional
 * formulas are acceptable. Each map in the list represents a dimensional formula
 * (Dimension IID → exponent), matching the format used by Unit.dimensions().
 */
@Canonical.Canonization(classType = Canonical.ClassCollectionType.ARRAY)
public final class UnitRules implements Canonical {

    public enum AllowedDimsKind { ANY, DIMENSIONLESS, LIST }

    @Canon(order = 0)
    private AllowedDimsKind allowedKind = AllowedDimsKind.ANY;

    /**
     * If allowedKind==LIST, specifies which dimensional formulas are allowed.
     * Each map represents a dimensional formula: Dimension IID → exponent.
     * null for ANY or DIMENSIONLESS modes.
     */
    @Canon(order = 1)
    private List<Map<ItemID, Integer>> allowedDimensions; // nullable

    /** Allow affine units (e.g. Celsius). Default false for identity-safe types. */
    @Canon(order = 2)
    private boolean allowAffine = false;

    /** If true, canonicalize values to the canonical unit for their dimension. */
    @Canon(order = 3)
    private boolean canonicalizeToBase = true;

    public UnitRules() {}

    public UnitRules(AllowedDimsKind allowedKind, List<Map<ItemID, Integer>> allowedDimensions,
                     boolean allowAffine, boolean canonicalizeToBase) {
        this.allowedKind = allowedKind;
        this.allowedDimensions = allowedDimensions;
        this.allowAffine = allowAffine;
        this.canonicalizeToBase = canonicalizeToBase;
    }

    public AllowedDimsKind allowedKind() { return allowedKind; }
    public List<Map<ItemID, Integer>> allowedDimensions() { return allowedDimensions; }
    public boolean allowAffine() { return allowAffine; }
    public boolean canonicalizeToBase() { return canonicalizeToBase; }
}
