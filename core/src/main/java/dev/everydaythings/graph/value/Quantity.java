package dev.everydaythings.graph.value;

import dev.everydaythings.graph.canonical.Canon;
import dev.everydaythings.graph.id.ItemID;
import lombok.Getter;

import java.util.Objects;

/**
 * A numeric value with a unit reference.
 *
 * <p>Identity includes the unit (your explicit decision):
 * 1 inch and 25.4 mm are NOT the same quantity object; conversion happens at evaluation time.
 */
@Getter
public final class Quantity implements Value {

    @Canon(order = 1)
    private final Decimal value;

    @Canon(order = 2)
    private final ItemID unit;

    public Quantity(Decimal value, ItemID unit) {
        this.value = Objects.requireNonNull(value, "value");
        this.unit = Objects.requireNonNull(unit, "unit");
    }

    public static Quantity of(Decimal value, ItemID unit) {
        return new Quantity(value, unit);
    }

    public static Quantity decimal(long unscaled, int scale, ItemID unit) {
        return new Quantity(Decimal.of(unscaled, scale), unit);
    }

    @Override
    public String token() {
        // TODO: resolve unit symbol via Unit sememe lookup once the
        // unit/dimension vocabulary is restored.
        return value.token() + " " + unit;
    }

    @Override
    public String toString() {
        return token();
    }

    // ==================================================================================
    // Renderable Implementation
    // ==================================================================================

    @Override
    public String emoji() {
        return "#";  // Numeric quantity
    }

    @Override
    public String colorCategory() {
        return "value";
    }

    // No-arg constructor for Canonical decoding
    @SuppressWarnings("unused")
    private Quantity() { this.value = null; this.unit = null; }
}
