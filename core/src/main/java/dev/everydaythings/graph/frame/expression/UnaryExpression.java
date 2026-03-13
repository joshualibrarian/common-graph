package dev.everydaythings.graph.frame.expression;

import dev.everydaythings.graph.Canonical;
import dev.everydaythings.graph.Canonical.Canon;
import dev.everydaythings.graph.item.id.ItemID;

import java.util.List;

/**
 * A unary operation expression (op operand).
 *
 * <p>Represents operations with one operand:
 * <ul>
 *   <li>Arithmetic: {@code -} (negate)</li>
 *   <li>Logical: {@code !} (not)</li>
 *   <li>Collection: {@code count}, {@code first}, {@code last}, {@code empty}</li>
 * </ul>
 *
 * @param operator The unary operator
 * @param operand  The operand expression
 */
public record UnaryExpression(
        @Canon(order = 0) Operator operator,
        @Canon(order = 1) Expression operand
) implements Expression, Canonical {

    /**
     * Unary operators.
     */
    public enum Operator {
        // Arithmetic
        NEGATE("-"),

        // Logical
        NOT("!"),

        // Collection/aggregate
        COUNT("count"),
        FIRST("first"),
        LAST("last"),
        EMPTY("empty"),
        SUM("sum"),
        AVG("avg"),
        MIN("min"),
        MAX("max");

        private final String symbol;

        Operator(String symbol) {
            this.symbol = symbol;
        }

        public String symbol() {
            return symbol;
        }

        public static Operator fromSymbol(String symbol) {
            for (Operator op : values()) {
                if (op.symbol.equals(symbol)) {
                    return op;
                }
            }
            throw new IllegalArgumentException("Unknown operator: " + symbol);
        }
    }

    // ==================================================================================
    // Factories
    // ==================================================================================

    public static UnaryExpression negate(Expression operand) {
        return new UnaryExpression(Operator.NEGATE, operand);
    }

    public static UnaryExpression not(Expression operand) {
        return new UnaryExpression(Operator.NOT, operand);
    }

    public static UnaryExpression count(Expression operand) {
        return new UnaryExpression(Operator.COUNT, operand);
    }

    public static UnaryExpression first(Expression operand) {
        return new UnaryExpression(Operator.FIRST, operand);
    }

    public static UnaryExpression last(Expression operand) {
        return new UnaryExpression(Operator.LAST, operand);
    }

    public static UnaryExpression empty(Expression operand) {
        return new UnaryExpression(Operator.EMPTY, operand);
    }

    // ==================================================================================
    // Expression Implementation
    // ==================================================================================

    @Override
    public Object evaluate(EvaluationContext context) {
        Object val = operand.evaluate(context);

        return switch (operator) {
            case NEGATE -> {
                if (val instanceof Number n) {
                    yield -n.doubleValue();
                }
                yield 0;
            }
            case NOT -> !toBoolean(val);
            case COUNT -> {
                if (val instanceof List<?> list) {
                    yield list.size();
                }
                if (val instanceof String s) {
                    yield s.length();
                }
                yield val == null ? 0 : 1;
            }
            case FIRST -> {
                if (val instanceof List<?> list && !list.isEmpty()) {
                    yield list.getFirst();
                }
                yield null;
            }
            case LAST -> {
                if (val instanceof List<?> list && !list.isEmpty()) {
                    yield list.getLast();
                }
                yield null;
            }
            case EMPTY -> {
                if (val == null) yield true;
                if (val instanceof List<?> list) yield list.isEmpty();
                if (val instanceof String s) yield s.isEmpty();
                yield false;
            }
            case SUM -> {
                if (val instanceof List<?> list) {
                    yield list.stream()
                            .filter(x -> x instanceof Number)
                            .mapToDouble(x -> ((Number) x).doubleValue())
                            .sum();
                }
                yield val instanceof Number n ? n.doubleValue() : 0;
            }
            case AVG -> {
                if (val instanceof List<?> list && !list.isEmpty()) {
                    double sum = list.stream()
                            .filter(x -> x instanceof Number)
                            .mapToDouble(x -> ((Number) x).doubleValue())
                            .sum();
                    long count = list.stream()
                            .filter(x -> x instanceof Number)
                            .count();
                    yield count > 0 ? sum / count : 0;
                }
                yield val instanceof Number n ? n.doubleValue() : 0;
            }
            case MIN -> {
                if (val instanceof List<?> list) {
                    yield list.stream()
                            .filter(x -> x instanceof Number)
                            .mapToDouble(x -> ((Number) x).doubleValue())
                            .min()
                            .orElse(0);
                }
                yield val instanceof Number n ? n.doubleValue() : 0;
            }
            case MAX -> {
                if (val instanceof List<?> list) {
                    yield list.stream()
                            .filter(x -> x instanceof Number)
                            .mapToDouble(x -> ((Number) x).doubleValue())
                            .max()
                            .orElse(0);
                }
                yield val instanceof Number n ? n.doubleValue() : 0;
            }
        };
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
        return switch (operator) {
            case NOT, EMPTY -> null; // Boolean type
            case COUNT -> null; // Integer type
            default -> null;
        };
    }

    @Override
    public String toExpressionString() {
        String opStr = operand.toExpressionString();
        if (operand instanceof BinaryExpression) {
            opStr = "(" + opStr + ")";
        }

        return switch (operator) {
            case NEGATE, NOT -> operator.symbol() + opStr;
            default -> operator.symbol() + "(" + opStr + ")";
        };
    }

    @Override
    public boolean hasDependencies() {
        return operand.hasDependencies();
    }

    @Override
    public boolean referencesLocal(String handle) {
        return operand.referencesLocal(handle);
    }
}
