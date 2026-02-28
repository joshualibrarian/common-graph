package dev.everydaythings.graph.item.component.expression;

import dev.everydaythings.graph.item.id.ItemID;
import dev.everydaythings.graph.runtime.Librarian;

/**
 * The core abstraction for computation in the graph.
 *
 * <p>An Expression represents a recipe for producing a value. Expressions are:
 * <ul>
 *   <li><b>Canonical</b> - stored as the "snapshot" of a Value component</li>
 *   <li><b>Composable</b> - expressions can reference other expressions</li>
 *   <li><b>Evaluable</b> - produce a result when given a context (Librarian)</li>
 * </ul>
 *
 * <p>This is the fundamental building block: "Expressions all the way down."
 * Every computable thing in the graph is an expression:
 * <ul>
 *   <li>Literal values: {@code 42}, {@code "hello"}, {@code true}</li>
 *   <li>Relations/queries: {@code ? → implemented-by → *}</li>
 *   <li>Operations: {@code a + b}, {@code !x}</li>
 *   <li>References: {@code otherItem.someValue}</li>
 *   <li>Function calls: {@code fn(args...)}</li>
 * </ul>
 *
 * <p>The graph becomes a live computation graph where values flow between
 * items and expressions compose infinitely.
 *
 * <p>This interface is intentionally NOT sealed to allow extension with
 * custom expression types. Core expression types are provided as records
 * implementing this interface.
 *
 * @see Value
 */
public interface Expression {

    /**
     * Evaluate this expression to produce a result.
     *
     * @param context Evaluation context providing graph access
     * @return The computed result (type depends on expression)
     */
    Object evaluate(EvaluationContext context);

    /**
     * Get the expected result type of this expression.
     *
     * <p>Returns null if the type cannot be determined statically.
     * Used for type checking and UI hints.
     *
     * @return The expected result type, or null if unknown
     */
    default ItemID resultType() {
        return null;
    }

    /**
     * Format this expression as a human-readable string.
     *
     * <p>Used for display in UI, debugging, and serialization to text.
     *
     * @return Human-readable expression string
     */
    String toExpressionString();

    /**
     * Check if this expression has any external dependencies.
     *
     * <p>A pure expression (like a literal) has no dependencies.
     * An expression referencing other items has dependencies.
     *
     * @return true if this expression depends on external state
     */
    default boolean hasDependencies() {
        return false;
    }
}
