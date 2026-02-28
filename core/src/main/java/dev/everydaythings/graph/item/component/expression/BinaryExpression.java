package dev.everydaythings.graph.item.component.expression;

import dev.everydaythings.graph.Canonical;
import dev.everydaythings.graph.Canonical.Canon;
import dev.everydaythings.graph.item.id.ItemID;
import dev.everydaythings.graph.value.Operator;

import java.util.List;

/**
 * A binary operation expression (left op right).
 *
 * <p>Represents operations with two operands:
 * <ul>
 *   <li>Arithmetic: {@code +}, {@code -}, {@code *}, {@code /}, {@code %}</li>
 *   <li>Comparison: {@code ==}, {@code !=}, {@code <}, {@code >}, {@code <=}, {@code >=}</li>
 *   <li>Logical: {@code &&}, {@code ||}</li>
 *   <li>String: {@code ++} (concatenation)</li>
 *   <li>Collection: {@code in}, {@code contains}</li>
 * </ul>
 *
 * <p>Logical operators (AND, OR) are now first-class Items. The operator field
 * stores the ItemID of the Operator item, enabling short-circuit evaluation
 * and making operators discoverable and relatable.
 *
 * @param left       Left operand expression
 * @param operatorId The operator ItemID (references Operator item or legacy enum)
 * @param right      Right operand expression
 */
public record BinaryExpression(
        @Canon(order = 0) Expression left,
        @Canon(order = 1) ItemID operatorId,
        @Canon(order = 2) Expression right
) implements Expression, Canonical {

    /**
     * Legacy binary operators (non-logical).
     *
     * <p>These operators are kept for backward compatibility. Logical operators
     * (AND, OR) have been migrated to first-class Operator Items.
     *
     * @deprecated Use Operator items for AND/OR. Other operators will migrate in future phases.
     */
    @Deprecated
    public enum LegacyOperator {
        // Arithmetic
        ADD("+"),
        SUBTRACT("-"),
        MULTIPLY("*"),
        DIVIDE("/"),
        MODULO("%"),
        POWER("**"),

        // Comparison
        EQUAL("=="),
        NOT_EQUAL("!="),
        LESS_THAN("<"),
        GREATER_THAN(">"),
        LESS_OR_EQUAL("<="),
        GREATER_OR_EQUAL(">="),

        // String
        CONCAT("++"),

        // Collection
        IN("in"),
        CONTAINS("contains");

        private final String symbol;

        LegacyOperator(String symbol) {
            this.symbol = symbol;
        }

        public String symbol() {
            return symbol;
        }

        /**
         * Get the ItemID for this legacy operator.
         *
         * <p>Returns a deterministic ID based on "cg.op.legacy:NAME".
         */
        public ItemID toItemId() {
            return ItemID.fromString("cg.op.legacy:" + name().toLowerCase().replace("_", "-"));
        }

        public static LegacyOperator fromSymbol(String symbol) {
            for (LegacyOperator op : values()) {
                if (op.symbol.equals(symbol)) {
                    return op;
                }
            }
            return null;
        }

        public static LegacyOperator fromItemId(ItemID id) {
            for (LegacyOperator op : values()) {
                if (op.toItemId().equals(id)) {
                    return op;
                }
            }
            return null;
        }
    }

    // ==================================================================================
    // Factories - Logical (use Operator Items)
    // ==================================================================================

    /**
     * Create a logical AND expression.
     */
    public static BinaryExpression and(Expression left, Expression right) {
        return new BinaryExpression(left, Operator.AND.iid(), right);
    }

    /**
     * Create a logical OR expression.
     */
    public static BinaryExpression or(Expression left, Expression right) {
        return new BinaryExpression(left, Operator.OR.iid(), right);
    }

    // ==================================================================================
    // Factories - Arithmetic (use legacy IDs for now)
    // ==================================================================================

    public static BinaryExpression add(Expression left, Expression right) {
        return new BinaryExpression(left, LegacyOperator.ADD.toItemId(), right);
    }

    public static BinaryExpression subtract(Expression left, Expression right) {
        return new BinaryExpression(left, LegacyOperator.SUBTRACT.toItemId(), right);
    }

    public static BinaryExpression multiply(Expression left, Expression right) {
        return new BinaryExpression(left, LegacyOperator.MULTIPLY.toItemId(), right);
    }

    public static BinaryExpression divide(Expression left, Expression right) {
        return new BinaryExpression(left, LegacyOperator.DIVIDE.toItemId(), right);
    }

    // ==================================================================================
    // Factories - Comparison (use legacy IDs for now)
    // ==================================================================================

    public static BinaryExpression equal(Expression left, Expression right) {
        return new BinaryExpression(left, LegacyOperator.EQUAL.toItemId(), right);
    }

    public static BinaryExpression notEqual(Expression left, Expression right) {
        return new BinaryExpression(left, LegacyOperator.NOT_EQUAL.toItemId(), right);
    }

    public static BinaryExpression lessThan(Expression left, Expression right) {
        return new BinaryExpression(left, LegacyOperator.LESS_THAN.toItemId(), right);
    }

    public static BinaryExpression greaterThan(Expression left, Expression right) {
        return new BinaryExpression(left, LegacyOperator.GREATER_THAN.toItemId(), right);
    }

    // ==================================================================================
    // Expression Implementation
    // ==================================================================================

    @Override
    public Object evaluate(EvaluationContext context) {
        // Check if this is an Operator Item (AND, OR)
        Operator op = Operator.lookup(operatorId, context);
        if (op != null) {
            // Delegate to Operator item - handles short-circuit evaluation
            return op.evaluate(left, right, context);
        }

        // Fall back to legacy operator handling
        LegacyOperator legacyOp = LegacyOperator.fromItemId(operatorId);
        if (legacyOp == null) {
            throw new IllegalStateException("Unknown operator: " + operatorId);
        }

        // Evaluate both sides (no short-circuit for legacy operators)
        Object leftVal = left.evaluate(context);
        Object rightVal = right.evaluate(context);
        return applyLegacyOperator(legacyOp, leftVal, rightVal);
    }

    private Object applyLegacyOperator(LegacyOperator op, Object left, Object right) {
        return switch (op) {
            // Arithmetic (numbers)
            case ADD -> {
                if (left instanceof Number l && right instanceof Number r) {
                    yield toNumber(l) + toNumber(r);
                }
                // String concatenation fallback
                yield String.valueOf(left) + String.valueOf(right);
            }
            case SUBTRACT -> toNumber(left) - toNumber(right);
            case MULTIPLY -> toNumber(left) * toNumber(right);
            case DIVIDE -> {
                double r = toNumber(right);
                if (r == 0) throw new ArithmeticException("Division by zero");
                yield toNumber(left) / r;
            }
            case MODULO -> {
                double r = toNumber(right);
                if (r == 0) throw new ArithmeticException("Modulo by zero");
                yield toNumber(left) % r;
            }
            case POWER -> Math.pow(toNumber(left), toNumber(right));

            // Comparison
            case EQUAL -> equals(left, right);
            case NOT_EQUAL -> !equals(left, right);
            case LESS_THAN -> compare(left, right) < 0;
            case GREATER_THAN -> compare(left, right) > 0;
            case LESS_OR_EQUAL -> compare(left, right) <= 0;
            case GREATER_OR_EQUAL -> compare(left, right) >= 0;

            // String
            case CONCAT -> String.valueOf(left) + String.valueOf(right);

            // Collection
            case IN -> {
                if (right instanceof List<?> list) {
                    yield list.contains(left);
                }
                yield false;
            }
            case CONTAINS -> {
                if (left instanceof List<?> list) {
                    yield list.contains(right);
                }
                yield false;
            }
        };
    }

    private static double toNumber(Object o) {
        if (o instanceof Number n) return n.doubleValue();
        if (o instanceof String s) {
            try {
                return Double.parseDouble(s);
            } catch (NumberFormatException e) {
                return 0;
            }
        }
        return 0;
    }

    private static boolean equals(Object a, Object b) {
        if (a == null && b == null) return true;
        if (a == null || b == null) return false;
        if (a instanceof Number && b instanceof Number) {
            return toNumber(a) == toNumber(b);
        }
        return a.equals(b);
    }

    @SuppressWarnings("unchecked")
    private static int compare(Object a, Object b) {
        if (a instanceof Number && b instanceof Number) {
            return Double.compare(toNumber(a), toNumber(b));
        }
        if (a instanceof Comparable c && b != null) {
            try {
                return c.compareTo(b);
            } catch (ClassCastException e) {
                return 0;
            }
        }
        return 0;
    }

    @Override
    public ItemID resultType() {
        // Check Operator items first
        if (operatorId.equals(Operator.AND.iid()) || operatorId.equals(Operator.OR.iid())) {
            return null; // TODO: Boolean type
        }

        // Legacy operators
        LegacyOperator legacyOp = LegacyOperator.fromItemId(operatorId);
        if (legacyOp != null) {
            return switch (legacyOp) {
                case EQUAL, NOT_EQUAL, LESS_THAN, GREATER_THAN,
                     LESS_OR_EQUAL, GREATER_OR_EQUAL, IN, CONTAINS -> null; // TODO: Boolean type
                case CONCAT -> null; // TODO: String type
                default -> null; // TODO: Number type
            };
        }
        return null;
    }

    @Override
    public String toExpressionString() {
        String leftStr = left.toExpressionString();
        String rightStr = right.toExpressionString();

        // Add parens if nested binary expression
        if (left instanceof BinaryExpression) {
            leftStr = "(" + leftStr + ")";
        }
        if (right instanceof BinaryExpression) {
            rightStr = "(" + rightStr + ")";
        }

        return leftStr + " " + getOperatorSymbol() + " " + rightStr;
    }

    /**
     * Get the display symbol for this operator.
     */
    public String getOperatorSymbol() {
        // Check Operator items first
        if (operatorId.equals(Operator.AND.iid())) return Operator.AND.symbol();
        if (operatorId.equals(Operator.OR.iid())) return Operator.OR.symbol();

        // Legacy operators
        LegacyOperator legacyOp = LegacyOperator.fromItemId(operatorId);
        if (legacyOp != null) {
            return legacyOp.symbol();
        }

        return operatorId.toString();
    }

    /**
     * Check if this is a logical AND expression.
     */
    public boolean isAnd() {
        return operatorId.equals(Operator.AND.iid());
    }

    /**
     * Check if this is a logical OR expression.
     */
    public boolean isOr() {
        return operatorId.equals(Operator.OR.iid());
    }

    @Override
    public boolean hasDependencies() {
        return left.hasDependencies() || right.hasDependencies();
    }
}
