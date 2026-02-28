package dev.everydaythings.graph.item.component.expression;

import dev.everydaythings.graph.Canonical;
import dev.everydaythings.graph.Canonical.Canon;
import dev.everydaythings.graph.item.id.ItemID;

import java.util.ArrayList;
import java.util.Collections;
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
        // Evaluate all arguments
        List<Object> args = arguments.stream()
                .map(arg -> arg.evaluate(context))
                .toList();

        // Dispatch to built-in functions
        return evaluateBuiltin(function, args, context);
    }

    @SuppressWarnings("unchecked")
    private Object evaluateBuiltin(String fn, List<Object> args, EvaluationContext context) {
        return switch (fn.toLowerCase()) {
            // Type coercion
            case "tostring", "str" -> args.isEmpty() ? "" : String.valueOf(args.getFirst());
            case "tonumber", "num" -> {
                if (args.isEmpty()) yield 0.0;
                Object arg = args.getFirst();
                if (arg instanceof Number n) yield n.doubleValue();
                try {
                    yield Double.parseDouble(String.valueOf(arg));
                } catch (NumberFormatException e) {
                    yield 0.0;
                }
            }
            case "tobool", "bool" -> {
                if (args.isEmpty()) yield false;
                Object arg = args.getFirst();
                if (arg instanceof Boolean b) yield b;
                if (arg instanceof Number n) yield n.doubleValue() != 0;
                if (arg instanceof String s) yield !s.isEmpty();
                yield arg != null;
            }

            // String functions
            case "format" -> {
                if (args.isEmpty()) yield "";
                String fmt = String.valueOf(args.getFirst());
                Object[] fmtArgs = args.subList(1, args.size()).toArray();
                yield String.format(fmt, fmtArgs);
            }
            case "upper", "uppercase" -> args.isEmpty() ? "" : String.valueOf(args.getFirst()).toUpperCase();
            case "lower", "lowercase" -> args.isEmpty() ? "" : String.valueOf(args.getFirst()).toLowerCase();
            case "trim" -> args.isEmpty() ? "" : String.valueOf(args.getFirst()).trim();
            case "length", "len" -> {
                if (args.isEmpty()) yield 0;
                Object arg = args.getFirst();
                if (arg instanceof String s) yield s.length();
                if (arg instanceof List<?> l) yield l.size();
                yield 0;
            }
            case "substring", "substr" -> {
                if (args.size() < 2) yield "";
                String s = String.valueOf(args.get(0));
                int start = ((Number) args.get(1)).intValue();
                if (args.size() >= 3) {
                    int end = ((Number) args.get(2)).intValue();
                    yield s.substring(Math.max(0, start), Math.min(s.length(), end));
                }
                yield s.substring(Math.max(0, start));
            }
            case "split" -> {
                if (args.size() < 2) yield List.of();
                String s = String.valueOf(args.get(0));
                String delim = String.valueOf(args.get(1));
                yield List.of(s.split(delim));
            }
            case "join" -> {
                if (args.size() < 2) yield "";
                Object list = args.get(0);
                String delim = String.valueOf(args.get(1));
                if (list instanceof List<?> l) {
                    yield l.stream()
                            .map(String::valueOf)
                            .collect(Collectors.joining(delim));
                }
                yield "";
            }

            // Collection functions
            case "map" -> {
                // map(list, fn) - apply fn to each element
                // For now, just return list unchanged (lambda support needed)
                if (args.isEmpty()) yield List.of();
                yield args.getFirst();
            }
            case "filter" -> {
                // filter(list, predicate) - keep elements where predicate is true
                if (args.isEmpty()) yield List.of();
                yield args.getFirst();
            }
            case "reduce", "fold" -> {
                // reduce(list, initial, fn) - fold list into single value
                if (args.size() < 2) yield null;
                yield args.get(1);
            }
            case "range" -> {
                if (args.isEmpty()) yield List.of();
                int start = 0;
                int end;
                int step = 1;
                if (args.size() == 1) {
                    end = ((Number) args.get(0)).intValue();
                } else {
                    start = ((Number) args.get(0)).intValue();
                    end = ((Number) args.get(1)).intValue();
                    if (args.size() >= 3) {
                        step = ((Number) args.get(2)).intValue();
                    }
                }
                List<Integer> result = new ArrayList<>();
                for (int i = start; step > 0 ? i < end : i > end; i += step) {
                    result.add(i);
                }
                yield result;
            }
            case "reverse" -> {
                if (args.isEmpty()) yield List.of();
                Object arg = args.getFirst();
                if (arg instanceof List<?> list) {
                    List<Object> reversed = new ArrayList<>(list);
                    Collections.reverse(reversed);
                    yield reversed;
                }
                if (arg instanceof String s) {
                    yield new StringBuilder(s).reverse().toString();
                }
                yield arg;
            }
            case "sort" -> {
                if (args.isEmpty()) yield List.of();
                Object arg = args.getFirst();
                if (arg instanceof List<?> list) {
                    List<Object> sorted = new ArrayList<>(list);
                    sorted.sort((a, b) -> {
                        if (a instanceof Comparable c && b != null) {
                            try {
                                return c.compareTo(b);
                            } catch (ClassCastException e) {
                                return 0;
                            }
                        }
                        return 0;
                    });
                    yield sorted;
                }
                yield arg;
            }
            case "unique", "distinct" -> {
                if (args.isEmpty()) yield List.of();
                Object arg = args.getFirst();
                if (arg instanceof List<?> list) {
                    yield list.stream().distinct().toList();
                }
                yield arg;
            }
            case "flatten" -> {
                if (args.isEmpty()) yield List.of();
                Object arg = args.getFirst();
                if (arg instanceof List<?> list) {
                    List<Object> flat = new ArrayList<>();
                    for (Object item : list) {
                        if (item instanceof List<?> inner) {
                            flat.addAll(inner);
                        } else {
                            flat.add(item);
                        }
                    }
                    yield flat;
                }
                yield arg;
            }

            // Math functions
            case "abs" -> args.isEmpty() ? 0 : Math.abs(((Number) args.getFirst()).doubleValue());
            case "ceil" -> args.isEmpty() ? 0 : Math.ceil(((Number) args.getFirst()).doubleValue());
            case "floor" -> args.isEmpty() ? 0 : Math.floor(((Number) args.getFirst()).doubleValue());
            case "round" -> args.isEmpty() ? 0 : Math.round(((Number) args.getFirst()).doubleValue());
            case "sqrt" -> args.isEmpty() ? 0 : Math.sqrt(((Number) args.getFirst()).doubleValue());
            case "pow" -> args.size() < 2 ? 0 :
                    Math.pow(((Number) args.get(0)).doubleValue(), ((Number) args.get(1)).doubleValue());
            case "log" -> args.isEmpty() ? 0 : Math.log(((Number) args.getFirst()).doubleValue());
            case "sin" -> args.isEmpty() ? 0 : Math.sin(((Number) args.getFirst()).doubleValue());
            case "cos" -> args.isEmpty() ? 0 : Math.cos(((Number) args.getFirst()).doubleValue());
            case "tan" -> args.isEmpty() ? 0 : Math.tan(((Number) args.getFirst()).doubleValue());
            case "random", "rand" -> Math.random();

            // Date/time (basic)
            case "now" -> System.currentTimeMillis();
            case "timestamp" -> System.currentTimeMillis() / 1000;

            // Utility
            case "typeof", "type" -> {
                if (args.isEmpty()) yield "null";
                Object arg = args.getFirst();
                if (arg == null) yield "null";
                if (arg instanceof Number) yield "number";
                if (arg instanceof String) yield "string";
                if (arg instanceof Boolean) yield "boolean";
                if (arg instanceof List) yield "list";
                if (arg instanceof ItemID) yield "item";
                yield arg.getClass().getSimpleName().toLowerCase();
            }
            case "isnull", "null?" -> args.isEmpty() || args.getFirst() == null;
            case "coalesce" -> args.stream().filter(a -> a != null).findFirst().orElse(null);
            case "default" -> {
                if (args.size() < 2) yield null;
                Object val = args.get(0);
                Object def = args.get(1);
                yield val != null ? val : def;
            }

            default -> {
                // Unknown function - return null or throw
                yield null;
            }
        };
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
}
