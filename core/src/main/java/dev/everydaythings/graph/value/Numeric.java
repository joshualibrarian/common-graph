package dev.everydaythings.graph.value;

/**
 * Marker interface for numeric values that support arithmetic.
 *
 * <p>Numeric values:
 * <ul>
 *   <li>Can participate in arithmetic operations</li>
 *   <li>Can be attached to units (via {@link dev.everydaythings.graph.value.scalar.Quantity})</li>
 *   <li>Can be compared and ordered</li>
 * </ul>
 *
 * <p>Implementations include:
 * <ul>
 *   <li>{@link dev.everydaythings.graph.value.scalar.Decimal} - arbitrary precision decimal</li>
 *   <li>{@link dev.everydaythings.graph.value.scalar.Rational} - exact fractions</li>
 *   <li>Integer types</li>
 *   <li>Float64 (use sparingly - not identity-safe)</li>
 * </ul>
 *
 * @see Value
 * @see dev.everydaythings.graph.value.scalar.Quantity
 */
public interface Numeric extends Value {

    // For now, a marker interface.
    // Arithmetic operations and comparison methods can be added as needed.
}
