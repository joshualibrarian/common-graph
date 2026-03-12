package dev.everydaythings.graph.item.component.expression;

import dev.everydaythings.graph.Canonical;
import dev.everydaythings.graph.Canonical.Canon;
import dev.everydaythings.graph.item.id.ItemID;
import dev.everydaythings.graph.value.Operator;

/**
 * A binary operation expression (left op right).
 *
 * <p>All operators — arithmetic, comparison, logical, string, collection —
 * are first-class {@link Operator} Items. The operatorId references the
 * Operator item, which knows how to evaluate itself.
 *
 * @param left       Left operand expression
 * @param operatorId The operator ItemID (references an Operator item)
 * @param right      Right operand expression
 */
public record BinaryExpression(
        @Canon(order = 0) Expression left,
        @Canon(order = 1) ItemID operatorId,
        @Canon(order = 2) Expression right
) implements Expression, Canonical {

    // ==================================================================================
    // Factories — Logical
    // ==================================================================================

    public static BinaryExpression and(Expression left, Expression right) {
        return new BinaryExpression(left, Operator.And.SEED.iid(), right);
    }

    public static BinaryExpression or(Expression left, Expression right) {
        return new BinaryExpression(left, Operator.Or.SEED.iid(), right);
    }

    // ==================================================================================
    // Factories — Arithmetic
    // ==================================================================================

    public static BinaryExpression add(Expression left, Expression right) {
        return new BinaryExpression(left, Operator.Add.SEED.iid(), right);
    }

    public static BinaryExpression subtract(Expression left, Expression right) {
        return new BinaryExpression(left, Operator.Subtract.SEED.iid(), right);
    }

    public static BinaryExpression multiply(Expression left, Expression right) {
        return new BinaryExpression(left, Operator.Multiply.SEED.iid(), right);
    }

    public static BinaryExpression divide(Expression left, Expression right) {
        return new BinaryExpression(left, Operator.Divide.SEED.iid(), right);
    }

    public static BinaryExpression modulo(Expression left, Expression right) {
        return new BinaryExpression(left, Operator.Modulo.SEED.iid(), right);
    }

    public static BinaryExpression power(Expression left, Expression right) {
        return new BinaryExpression(left, Operator.Power.SEED.iid(), right);
    }

    // ==================================================================================
    // Factories — Comparison
    // ==================================================================================

    public static BinaryExpression equal(Expression left, Expression right) {
        return new BinaryExpression(left, Operator.Equal.SEED.iid(), right);
    }

    public static BinaryExpression notEqual(Expression left, Expression right) {
        return new BinaryExpression(left, Operator.NotEqual.SEED.iid(), right);
    }

    public static BinaryExpression lessThan(Expression left, Expression right) {
        return new BinaryExpression(left, Operator.LessThan.SEED.iid(), right);
    }

    public static BinaryExpression greaterThan(Expression left, Expression right) {
        return new BinaryExpression(left, Operator.GreaterThan.SEED.iid(), right);
    }

    public static BinaryExpression lessOrEqual(Expression left, Expression right) {
        return new BinaryExpression(left, Operator.LessOrEqual.SEED.iid(), right);
    }

    public static BinaryExpression greaterOrEqual(Expression left, Expression right) {
        return new BinaryExpression(left, Operator.GreaterOrEqual.SEED.iid(), right);
    }

    // ==================================================================================
    // Factories — String
    // ==================================================================================

    public static BinaryExpression concat(Expression left, Expression right) {
        return new BinaryExpression(left, Operator.Concat.SEED.iid(), right);
    }

    // ==================================================================================
    // Factories — Structural
    // ==================================================================================

    public static BinaryExpression assign(Expression left, Expression right) {
        return new BinaryExpression(left, Operator.Assign.SEED.iid(), right);
    }

    public static BinaryExpression pipe(Expression left, Expression right) {
        return new BinaryExpression(left, Operator.Pipe.SEED.iid(), right);
    }

    // ==================================================================================
    // Expression Implementation
    // ==================================================================================

    @Override
    public Object evaluate(EvaluationContext context) {
        Operator op = Operator.lookup(operatorId, context);
        if (op == null) {
            throw new IllegalStateException("Unknown operator: " + operatorId);
        }
        return op.evaluate(left, right, context);
    }

    @Override
    public ItemID resultType() {
        return null; // TODO: infer from operator category
    }

    @Override
    public String toExpressionString() {
        String leftStr = left.toExpressionString();
        String rightStr = right.toExpressionString();

        if (left instanceof BinaryExpression) {
            leftStr = "(" + leftStr + ")";
        }
        if (right instanceof BinaryExpression) {
            rightStr = "(" + rightStr + ")";
        }

        return leftStr + " " + getOperatorSymbol() + " " + rightStr;
    }

    public String getOperatorSymbol() {
        Operator op = Operator.lookupKnown(operatorId);
        if (op != null) return op.symbol();
        return operatorId.toString();
    }

    public boolean isAnd() {
        return operatorId.equals(Operator.And.SEED.iid());
    }

    public boolean isOr() {
        return operatorId.equals(Operator.Or.SEED.iid());
    }

    @Override
    public boolean hasDependencies() {
        return left.hasDependencies() || right.hasDependencies();
    }

    @Override
    public boolean referencesLocal(String handle) {
        return left.referencesLocal(handle) || right.referencesLocal(handle);
    }
}
