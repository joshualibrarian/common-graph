package dev.everydaythings.graph.item.component.expression;

import dev.everydaythings.graph.Canonical;
import dev.everydaythings.graph.Canonical.Canon;
import dev.everydaythings.graph.item.Item;
import dev.everydaythings.graph.item.id.HandleID;
import dev.everydaythings.graph.item.id.ItemID;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.Optional;

/**
 * Property access expression ({@code object.property}).
 *
 * <p>Evaluates the object expression, then accesses a named property on the result.
 * This is the universal data access path — the same mechanism reads:
 * <ul>
 *   <li>Object properties via fluent getters ({@code session.actor()})</li>
 *   <li>Map entries ({@code map.get("key")})</li>
 *   <li>Item components by handle ({@code item.chess})</li>
 * </ul>
 *
 * <p>In the Pratt parser, DOT is the tightest-binding left-associative
 * infix operation, so {@code a.b.c} parses as {@code (a.b).c}.
 *
 * @param object   The object expression to access a property on
 * @param property The property name
 */
public record PropertyAccessExpression(
        @Canon(order = 0) Expression object,
        @Canon(order = 1) String property
) implements Expression, Canonical {

    // ==================================================================================
    // Expression Implementation
    // ==================================================================================

    @Override
    public Object evaluate(EvaluationContext context) {
        Object obj = object.evaluate(context);
        if (obj == null) return null;
        return accessProperty(obj, property);
    }

    /**
     * Access a named property on an object.
     *
     * <p>Resolution order:
     * <ol>
     *   <li>Map entry (if object is a Map)</li>
     *   <li>Item component by handle (if object is an Item)</li>
     *   <li>Optional unwrap (if object is an Optional)</li>
     *   <li>Fluent getter: {@code obj.property()}</li>
     *   <li>JavaBean getter: {@code obj.getProperty()}</li>
     * </ol>
     */
    public static Object accessProperty(Object obj, String prop) {
        if (obj == null || prop == null) return null;

        // Map entry
        if (obj instanceof Map<?, ?> map) {
            Object val = map.get(prop);
            if (val != null) return val;
        }

        // Item component by handle
        if (obj instanceof Item item) {
            HandleID handleId = HandleID.of(prop);
            Optional<?> component = item.content().getLive(handleId, Object.class);
            if (component.isPresent()) return component.get();
        }

        // Optional unwrap
        if (obj instanceof Optional<?> opt) {
            return opt.map(inner -> accessProperty(inner, prop)).orElse(null);
        }

        // Fluent getter: obj.property()
        try {
            Method method = obj.getClass().getMethod(prop);
            if (method.getParameterCount() == 0) {
                return method.invoke(obj);
            }
        } catch (NoSuchMethodException ignored) {
            // Try JavaBean style
        } catch (Exception e) {
            // Invocation failed
            return null;
        }

        // JavaBean getter: obj.getProperty()
        try {
            String getter = "get" + Character.toUpperCase(prop.charAt(0)) + prop.substring(1);
            Method method = obj.getClass().getMethod(getter);
            if (method.getParameterCount() == 0) {
                return method.invoke(obj);
            }
        } catch (Exception ignored) {
            // No such getter
        }

        // Boolean getter: obj.isProperty()
        try {
            String isGetter = "is" + Character.toUpperCase(prop.charAt(0)) + prop.substring(1);
            Method method = obj.getClass().getMethod(isGetter);
            if (method.getParameterCount() == 0 && method.getReturnType() == boolean.class) {
                return method.invoke(obj);
            }
        } catch (Exception ignored) {
            // No such getter
        }

        return null;
    }

    @Override
    public ItemID resultType() {
        return null; // Cannot determine statically
    }

    @Override
    public String toExpressionString() {
        String objStr = object.toExpressionString();
        // Wrap complex expressions in parens for clarity
        if (object instanceof BinaryExpression || object instanceof ConditionalExpression) {
            objStr = "(" + objStr + ")";
        }
        return objStr + "." + property;
    }

    @Override
    public boolean hasDependencies() {
        return object.hasDependencies();
    }

    @Override
    public boolean referencesLocal(String handle) {
        return object.referencesLocal(handle);
    }
}
