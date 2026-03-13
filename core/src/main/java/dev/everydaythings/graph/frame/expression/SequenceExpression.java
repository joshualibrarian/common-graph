package dev.everydaythings.graph.frame.expression;

import dev.everydaythings.graph.Canonical;
import dev.everydaythings.graph.Canonical.Canon;
import dev.everydaythings.graph.item.id.ItemID;

import java.util.List;
import java.util.stream.Collectors;

/**
 * A sequence of expressions (expr1; expr2; expr3).
 *
 * <p>Evaluates each expression in order, returning the result of the last one.
 * Useful for:
 * <ul>
 *   <li>Side-effecting operations in sequence</li>
 *   <li>Building up intermediate values</li>
 *   <li>Representing a "script" as an expression</li>
 * </ul>
 *
 * <p>Example:
 * <pre>{@code
 * // Compute several things, return the last
 * seq(
 *   assign("x", literal(10)),
 *   assign("y", literal(20)),
 *   add(ref("x"), ref("y"))
 * )
 * // Evaluates to 30
 * }</pre>
 *
 * @param expressions The expressions to evaluate in sequence
 */
public record SequenceExpression(
        @Canon(order = 0) List<Expression> expressions
) implements Expression, Canonical {

    public SequenceExpression {
        expressions = List.copyOf(expressions);
    }

    // ==================================================================================
    // Factories
    // ==================================================================================

    public static SequenceExpression of(Expression... expressions) {
        return new SequenceExpression(List.of(expressions));
    }

    public static SequenceExpression of(List<Expression> expressions) {
        return new SequenceExpression(expressions);
    }

    // ==================================================================================
    // Expression Implementation
    // ==================================================================================

    @Override
    public Object evaluate(EvaluationContext context) {
        if (expressions.isEmpty()) {
            return null;
        }

        Object result = null;
        for (Expression expr : expressions) {
            result = expr.evaluate(context);
        }
        return result;
    }

    @Override
    public ItemID resultType() {
        // Type of last expression
        if (expressions.isEmpty()) {
            return null;
        }
        return expressions.getLast().resultType();
    }

    @Override
    public String toExpressionString() {
        if (expressions.isEmpty()) {
            return "()";
        }
        if (expressions.size() == 1) {
            return expressions.getFirst().toExpressionString();
        }
        return expressions.stream()
                .map(Expression::toExpressionString)
                .collect(Collectors.joining("; "));
    }

    @Override
    public boolean hasDependencies() {
        return expressions.stream().anyMatch(Expression::hasDependencies);
    }

    @Override
    public boolean referencesLocal(String handle) {
        return expressions.stream().anyMatch(e -> e.referencesLocal(handle));
    }
}
