package dev.everydaythings.graph.value;

import dev.everydaythings.graph.Canonical;

/**
 * Canonicalization rules applied to numeric values.
 *
 * Keep this conservative: the core promise is deterministic encoding.
 */
@Canonical.Canonization(classType = Canonical.ClassCollectionType.ARRAY)
public final class CanonRules implements Canonical {

    public enum Rounding { HALF_EVEN, HALF_UP, FLOOR, CEILING, TRUNC }

    @Canon(order = 0)
    private Rounding rounding; // nullable

    /**
     * Clamp/normalize decimal scale (base-10 exponent). Example: -2 for cents.
     *
     * Interpretation: implementation may either clamp or reject values whose scale is smaller.
     */
    @Canon(order = 1)
    private Integer maxScale10; // nullable

    /**
     * If true, the runtime should strip trailing zeros in fixed-point decimal representation.
     * This can reduce multiple encodings for the same numeric value.
     */
    @Canon(order = 2)
    private Boolean stripTrailingZeros; // nullable

    public CanonRules() {}

    public CanonRules(Rounding rounding, Integer maxScale10, Boolean stripTrailingZeros) {
        this.rounding = rounding;
        this.maxScale10 = maxScale10;
        this.stripTrailingZeros = stripTrailingZeros;
    }

    public Rounding rounding() { return rounding; }
    public Integer maxScale10() { return maxScale10; }
    public Boolean stripTrailingZeros() { return stripTrailingZeros; }
}
