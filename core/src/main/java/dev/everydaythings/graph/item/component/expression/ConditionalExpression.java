package dev.everydaythings.graph.item.component.expression;

import dev.everydaythings.graph.Canonical;
import dev.everydaythings.graph.Canonical.Canon;
import dev.everydaythings.graph.item.id.ItemID;

import java.util.List;

/**
 * A conditional expression (if condition then thenExpr else elseExpr).
 *
 * <p>Evaluates the condition, then evaluates either the "then" or "else"
 * branch based on the truthiness of the condition.
 *
 * <p>Example:
 * <pre>{@code
 * // Simple if-then-else
 * cond(
 *   greaterThan(ref("age"), literal(18)),
 *   literal("adult"),
 *   literal("minor")
 * )
 * }</pre>
 *
 * @param condition The condition expression
 * @param thenExpr  Expression to evaluate if condition is true
 * @param elseExpr  Expression to evaluate if condition is false (can be null)
 */
public record ConditionalExpression(
        @Canon(order = 0) Expression condition,
        @Canon(order = 1) Expression thenExpr,
        @Canon(order = 2) Expression elseExpr
) implements Expression, Canonical {

    // ==================================================================================
    // Factories
    // ==================================================================================

    /**
     * Create a conditional with both branches.
     */
    public static ConditionalExpression ifThenElse(Expression condition,
                                                    Expression thenExpr,
                                                    Expression elseExpr) {
        return new ConditionalExpression(condition, thenExpr, elseExpr);
    }

    /**
     * Create a conditional with only a "then" branch (else returns null).
     */
    public static ConditionalExpression ifThen(Expression condition, Expression thenExpr) {
        return new ConditionalExpression(condition, thenExpr, null);
    }

    // ==================================================================================
    // Expression Implementation
    // ==================================================================================

    @Override
    public Object evaluate(EvaluationContext context) {
        Object condValue = condition.evaluate(context);
        boolean isTrue = toBoolean(condValue);

        if (isTrue) {
            return thenExpr.evaluate(context);
        } else if (elseExpr != null) {
            return elseExpr.evaluate(context);
        } else {
            return null;
        }
    }

    private static boolean toBoolean(Object o) {
        if (o == null) return false;
        if (o instanceof Boolean b) return b;
        if (o instanceof Number n) return n.doubleValue() != 0;
        if (o instanceof String s) return !s.isEmpty();
        if (o instanceof List<?> l) return !l.isEmpty();
        return true;
    }

    @Override
    public ItemID resultType() {
        // Could be type of either branch - need type unification
        // For now, try the then branch
        return thenExpr.resultType();
    }

    @Override
    public String toExpressionString() {
        String condStr = condition.toExpressionString();
        String thenStr = thenExpr.toExpressionString();

        if (elseExpr != null) {
            String elseStr = elseExpr.toExpressionString();
            return "if " + condStr + " then " + thenStr + " else " + elseStr;
        } else {
            return "if " + condStr + " then " + thenStr;
        }
    }

    @Override
    public boolean hasDependencies() {
        return condition.hasDependencies() ||
               thenExpr.hasDependencies() ||
               (elseExpr != null && elseExpr.hasDependencies());
    }
}
