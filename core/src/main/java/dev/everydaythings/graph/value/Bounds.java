package dev.everydaythings.graph.value;

import dev.everydaythings.graph.Canonical;

import java.util.Optional;

/**
 * Numeric bounds constraint for value types.
 *
 * <p>Bounds are expressed as Decimal values for exact comparison.
 * Both min and max are optional (nullable).
 */
@Canonical.Canonization(classType = Canonical.ClassCollectionType.ARRAY)
public final class Bounds implements Canonical {

    @Canon(order = 0)
    private Decimal min;

    @Canon(order = 1)
    private Decimal max;

    public Bounds() {}

    public Bounds(Decimal min, Decimal max) {
        this.min = min;
        this.max = max;
    }

    public static Bounds of(Decimal min, Decimal max) {
        return new Bounds(min, max);
    }

    public static Bounds atLeast(Decimal min) {
        return new Bounds(min, null);
    }

    public static Bounds atMost(Decimal max) {
        return new Bounds(null, max);
    }

    public static Bounds between(long min, long max) {
        return new Bounds(Decimal.ofLong(min), Decimal.ofLong(max));
    }

    public Optional<Decimal> min() {
        return Optional.ofNullable(min);
    }

    public Optional<Decimal> max() {
        return Optional.ofNullable(max);
    }
}
