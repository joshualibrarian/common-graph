package dev.everydaythings.graph.item.component.expression;

import dev.everydaythings.graph.Canonical;
import dev.everydaythings.graph.Canonical.Canon;
import dev.everydaythings.graph.item.id.ItemID;

/**
 * A let-binding expression (let name = value in body).
 *
 * <p>Binds a name to a value, then evaluates the body with that binding in scope.
 * Essential for:
 * <ul>
 *   <li>Avoiding redundant computation</li>
 *   <li>Giving names to intermediate results</li>
 *   <li>Building complex expressions from simpler parts</li>
 * </ul>
 *
 * <p>Example:
 * <pre>{@code
 * // let x = 10 in x + x
 * let("x", literal(10), add(ref("x"), ref("x")))
 * // Evaluates to 20
 *
 * // Nested bindings
 * let("x", literal(10),
 *   let("y", literal(20),
 *     add(ref("x"), ref("y"))))
 * // Evaluates to 30
 * }</pre>
 *
 * @param name  The binding name
 * @param value The expression to bind
 * @param body  The body expression where the binding is in scope
 */
public record BindingExpression(
        @Canon(order = 0) String name,
        @Canon(order = 1) Expression value,
        @Canon(order = 2) Expression body
) implements Expression, Canonical {

    // ==================================================================================
    // Factories
    // ==================================================================================

    /**
     * Create a let-binding.
     */
    public static BindingExpression let(String name, Expression value, Expression body) {
        return new BindingExpression(name, value, body);
    }

    // ==================================================================================
    // Expression Implementation
    // ==================================================================================

    @Override
    public Object evaluate(EvaluationContext context) {
        // Evaluate the value expression
        Object boundValue = value.evaluate(context);

        // Create a new context with the binding
        EvaluationContext childContext = context.withBinding(name, boundValue);

        // Evaluate the body in the new context
        return body.evaluate(childContext);
    }

    @Override
    public ItemID resultType() {
        // Type of the body
        return body.resultType();
    }

    @Override
    public String toExpressionString() {
        return "let " + name + " = " + value.toExpressionString() +
               " in " + body.toExpressionString();
    }

    @Override
    public boolean hasDependencies() {
        return value.hasDependencies() || body.hasDependencies();
    }
}
