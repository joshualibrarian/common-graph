package dev.everydaythings.graph.frame.expression;

import dev.everydaythings.graph.Canonical;
import dev.everydaythings.graph.Canonical.Canon;
import dev.everydaythings.graph.item.id.ItemID;

/**
 * A literal value expression.
 *
 * <p>The simplest expression - just returns its value unchanged.
 * Literals are pure (no dependencies) and always evaluate to themselves.
 *
 * <p>Examples:
 * <ul>
 *   <li>{@code 42} - integer literal</li>
 *   <li>{@code "hello"} - string literal</li>
 *   <li>{@code true} - boolean literal</li>
 *   <li>{@code 3.14} - decimal literal</li>
 *   <li>{@code iid:baaq...} - ItemID literal</li>
 * </ul>
 *
 * @param value The literal value (must be a serializable primitive or ItemID)
 */
public record LiteralExpression(
        @Canon(order = 0) Object value
) implements Expression, Canonical {

    // ==================================================================================
    // Factories
    // ==================================================================================

    public static LiteralExpression of(Object value) {
        return new LiteralExpression(value);
    }

    public static LiteralExpression integer(long value) {
        return new LiteralExpression(value);
    }

    public static LiteralExpression decimal(double value) {
        return new LiteralExpression(value);
    }

    public static LiteralExpression string(String value) {
        return new LiteralExpression(value);
    }

    public static LiteralExpression bool(boolean value) {
        return new LiteralExpression(value);
    }

    public static LiteralExpression item(ItemID value) {
        return new LiteralExpression(value);
    }

    public static LiteralExpression nil() {
        return new LiteralExpression(null);
    }

    // ==================================================================================
    // Expression Implementation
    // ==================================================================================

    @Override
    public Object evaluate(EvaluationContext context) {
        return value;
    }

    @Override
    public ItemID resultType() {
        if (value == null) return null;
        // TODO: Map Java types to graph types
        // e.g., Long -> cg:type/integer, String -> cg:type/text
        return null;
    }

    @Override
    public String toExpressionString() {
        if (value == null) return "nil";
        if (value instanceof String s) return "\"" + escapeString(s) + "\"";
        if (value instanceof ItemID iid) return iid.encodeText();
        return value.toString();
    }

    @Override
    public boolean hasDependencies() {
        return false; // Literals are pure
    }

    // ==================================================================================
    // Helpers
    // ==================================================================================

    private static String escapeString(String s) {
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\t", "\\t");
    }
}
