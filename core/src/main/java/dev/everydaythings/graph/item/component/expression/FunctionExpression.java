package dev.everydaythings.graph.item.component.expression;

import dev.everydaythings.graph.Canonical;
import dev.everydaythings.graph.Canonical.Canon;
import dev.everydaythings.graph.item.Item;
import dev.everydaythings.graph.item.component.ExpressionComponent;
import dev.everydaythings.graph.item.id.HandleID;
import dev.everydaythings.graph.value.Function;

import java.util.List;
import java.util.stream.Collectors;

/**
 * A function call expression (fn(arg1, arg2, ...)).
 *
 * <p>Functions can be:
 * <ul>
 *   <li><b>Built-in</b>: Standard functions like {@code map}, {@code filter}, {@code format}</li>
 *   <li><b>Item-defined</b>: Functions defined as Items with expression bodies</li>
 * </ul>
 *
 * <p>Examples:
 * <pre>{@code
 * // Built-in function
 * fn("format", literal("Hello, %s!"), ref("name"))
 *
 * // Collection operations
 * fn("map", ref("items"), lambda("x", add(ref("x"), literal(1))))
 * fn("filter", ref("items"), lambda("x", greaterThan(ref("x"), literal(0))))
 *
 * // Type coercion
 * fn("toString", ref("value"))
 * fn("toNumber", ref("input"))
 * }</pre>
 *
 * @param function  The function name or ItemID
 * @param arguments The argument expressions
 */
public record FunctionExpression(
        @Canon(order = 0) String function,
        @Canon(order = 1) List<Expression> arguments
) implements Expression, Canonical {

    public FunctionExpression {
        arguments = List.copyOf(arguments);
    }

    // ==================================================================================
    // Factories
    // ==================================================================================

    public static FunctionExpression call(String function, Expression... args) {
        return new FunctionExpression(function, List.of(args));
    }

    public static FunctionExpression call(String function, List<Expression> args) {
        return new FunctionExpression(function, args);
    }

    // ==================================================================================
    // Expression Implementation
    // ==================================================================================

    @Override
    public Object evaluate(EvaluationContext context) {
        // 1. Check for user-defined function on the owner item
        Item owner = context.owner();
        if (owner != null) {
            HandleID hid = HandleID.of(function);
            var exprOpt = owner.content().getLive(hid, ExpressionComponent.class);
            if (exprOpt.isPresent()) {
                ExpressionComponent fn = exprOpt.get();
                if (fn.isFunction()) {
                    return evaluateItemFunction(fn, context);
                }
            }
        }

        // 2. Evaluate all arguments
        List<Object> args = arguments.stream()
                .map(arg -> arg.evaluate(context))
                .toList();

        // 3. Look up the Function sememe and delegate
        Function fn = Function.lookupByName(function);
        if (fn != null) {
            return fn.evaluate(args, context);
        }

        // Unknown function
        return null;
    }

    private Object evaluateItemFunction(ExpressionComponent fn, EvaluationContext context) {
        List<String> params = fn.params();
        if (arguments.size() != params.size()) {
            throw new IllegalArgumentException(
                    "Function expects " + params.size() + " argument(s), got " + arguments.size());
        }
        // Evaluate arguments and bind to parameter names
        EvaluationContext fnContext = context;
        for (int i = 0; i < params.size(); i++) {
            Object argValue = arguments.get(i).evaluate(context);
            fnContext = fnContext.withBinding(params.get(i), argValue);
        }
        return fn.expression().evaluate(fnContext);
    }

    @Override
    public ItemID resultType() {
        // Depends on the function - would need a function registry
        return null;
    }

    @Override
    public String toExpressionString() {
        String args = arguments.stream()
                .map(Expression::toExpressionString)
                .collect(Collectors.joining(", "));
        return function + "(" + args + ")";
    }

    @Override
    public boolean hasDependencies() {
        return arguments.stream().anyMatch(Expression::hasDependencies);
    }

    @Override
    public boolean referencesLocal(String handle) {
        return arguments.stream().anyMatch(arg -> arg.referencesLocal(handle));
    }
}
