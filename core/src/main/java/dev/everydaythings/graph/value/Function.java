package dev.everydaythings.graph.value;

import dev.everydaythings.graph.item.Manifest;
import dev.everydaythings.graph.item.component.Type;
import dev.everydaythings.graph.item.component.expression.EvaluationContext;
import dev.everydaythings.graph.item.id.ItemID;
import dev.everydaythings.graph.language.NounSememe;
import dev.everydaythings.graph.runtime.Librarian;
import lombok.Getter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * A Function — a noun sememe with arity, category, and evaluation semantics.
 *
 * <p>Every built-in function (sqrt, abs, format, etc.) is a noun in the
 * vocabulary, discoverable and relatable. By extending {@link NounSememe},
 * functions inherit glosses, tokens, symbols, and dictionary registration —
 * making them show up in completions alongside verbs, operators, and units.
 *
 * <p>The function name is registered as a universal symbol (language-neutral,
 * like mathematical notation). English aliases are registered as tokens.
 *
 * <p>Evaluation lives on the Function itself via {@link #evaluate(List, EvaluationContext)},
 * dispatched by canonical key — same pattern as {@link Operator}.
 * {@link dev.everydaythings.graph.item.component.expression.FunctionExpression}
 * looks up the Function by name and delegates.
 */
@Type(value = Function.KEY, glyph = "ƒ")
public class Function extends NounSememe {

    public static final String KEY = "cg:type/function";

    // ==================================================================================
    // INSTANCE FIELDS (function-specific; canonicalKey, glosses, symbols inherited)
    // ==================================================================================

    @Getter @Frame
    private int minArity;

    @Getter @Frame
    private int maxArity;  // -1 for variadic

    @Getter @Frame
    private String category;

    // ==================================================================================
    // CATEGORIES
    // ==================================================================================

    public static final String MATH = "math";
    public static final String STRING = "string";
    public static final String COLLECTION = "collection";
    public static final String COERCION = "coercion";
    public static final String UTILITY = "utility";
    public static final String TIME = "time";

    // ==================================================================================
    // SEED INSTANCES — Math
    // ==================================================================================

    public static class Abs {
        public static final String KEY = "cg.fn:abs";
        @Seed public static final Function SEED = fn(KEY, "abs", "compute the absolute value of a number", 1, 1, MATH);
    }
    public static class Ceil {
        public static final String KEY = "cg.fn:ceil";
        @Seed public static final Function SEED = fn(KEY, "ceil", "round up to the nearest integer", 1, 1, MATH);
    }
    public static class Floor {
        public static final String KEY = "cg.fn:floor";
        @Seed public static final Function SEED = fn(KEY, "floor", "round down to the nearest integer", 1, 1, MATH);
    }
    public static class Round {
        public static final String KEY = "cg.fn:round";
        @Seed public static final Function SEED = fn(KEY, "round", "round to the nearest integer", 1, 1, MATH);
    }
    public static class Sqrt {
        public static final String KEY = "cg.fn:sqrt";
        @Seed public static final Function SEED = fn(KEY, "sqrt", "compute the positive square root", 1, 1, MATH, "square root");
    }
    public static class Pow {
        public static final String KEY = "cg.fn:pow";
        @Seed public static final Function SEED = fn(KEY, "pow", "raise to a power", 2, 2, MATH, "power", "exponent");
    }
    public static class Log {
        public static final String KEY = "cg.fn:log";
        @Seed public static final Function SEED = fn(KEY, "log", "compute the natural logarithm", 1, 1, MATH, "logarithm");
    }
    public static class Sin {
        public static final String KEY = "cg.fn:sin";
        @Seed public static final Function SEED = fn(KEY, "sin", "compute the sine", 1, 1, MATH, "sine");
    }
    public static class Cos {
        public static final String KEY = "cg.fn:cos";
        @Seed public static final Function SEED = fn(KEY, "cos", "compute the cosine", 1, 1, MATH, "cosine");
    }
    public static class Tan {
        public static final String KEY = "cg.fn:tan";
        @Seed public static final Function SEED = fn(KEY, "tan", "compute the tangent", 1, 1, MATH, "tangent");
    }
    public static class Random {
        public static final String KEY = "cg.fn:random";
        @Seed public static final Function SEED = fn(KEY, "random", "generate a random number between 0 and 1", 0, 0, MATH, "rand");
    }

    // ==================================================================================
    // SEED INSTANCES — Type Coercion
    // ==================================================================================

    public static class ToString {
        public static final String KEY = "cg.fn:tostring";
        @Seed public static final Function SEED = fn(KEY, "toString", "convert a value to its string representation", 1, 1, COERCION, "str");
    }
    public static class ToNumber {
        public static final String KEY = "cg.fn:tonumber";
        @Seed public static final Function SEED = fn(KEY, "toNumber", "convert a value to a number", 1, 1, COERCION, "num");
    }
    public static class ToBool {
        public static final String KEY = "cg.fn:tobool";
        @Seed public static final Function SEED = fn(KEY, "toBool", "convert a value to a boolean", 1, 1, COERCION, "bool");
    }

    // ==================================================================================
    // SEED INSTANCES — String
    // ==================================================================================

    public static class Upper {
        public static final String KEY = "cg.fn:upper";
        @Seed public static final Function SEED = fn(KEY, "upper", "convert to uppercase", 1, 1, STRING, "uppercase");
    }
    public static class Lower {
        public static final String KEY = "cg.fn:lower";
        @Seed public static final Function SEED = fn(KEY, "lower", "convert to lowercase", 1, 1, STRING, "lowercase");
    }
    public static class Trim {
        public static final String KEY = "cg.fn:trim";
        @Seed public static final Function SEED = fn(KEY, "trim", "remove leading and trailing whitespace", 1, 1, STRING);
    }
    public static class Length {
        public static final String KEY = "cg.fn:length";
        @Seed public static final Function SEED = fn(KEY, "length", "get the length of a string or collection", 1, 1, STRING, "len");
    }
    public static class Substring {
        public static final String KEY = "cg.fn:substring";
        @Seed public static final Function SEED = fn(KEY, "substring", "extract a portion of a string", 2, 3, STRING, "substr");
    }
    public static class Split {
        public static final String KEY = "cg.fn:split";
        @Seed public static final Function SEED = fn(KEY, "split", "split a string by delimiter", 2, 2, STRING);
    }
    public static class Join {
        public static final String KEY = "cg.fn:join";
        @Seed public static final Function SEED = fn(KEY, "join", "join a list into a string with delimiter", 2, 2, STRING);
    }
    public static class Format {
        public static final String KEY = "cg.fn:format";
        @Seed public static final Function SEED = fn(KEY, "format", "format a string with arguments", 1, -1, STRING);
    }

    // ==================================================================================
    // SEED INSTANCES — Collection
    // ==================================================================================

    public static class MapFn {
        public static final String KEY = "cg.fn:map";
        @Seed public static final Function SEED = fn(KEY, "map", "apply a function to each element", 2, 2, COLLECTION);
    }
    public static class Filter {
        public static final String KEY = "cg.fn:filter";
        @Seed public static final Function SEED = fn(KEY, "filter", "keep elements matching a predicate", 2, 2, COLLECTION);
    }
    public static class Reduce {
        public static final String KEY = "cg.fn:reduce";
        @Seed public static final Function SEED = fn(KEY, "reduce", "fold a collection into a single value", 3, 3, COLLECTION, "fold");
    }
    public static class Range {
        public static final String KEY = "cg.fn:range";
        @Seed public static final Function SEED = fn(KEY, "range", "generate a sequence of integers", 1, 3, COLLECTION);
    }
    public static class Reverse {
        public static final String KEY = "cg.fn:reverse";
        @Seed public static final Function SEED = fn(KEY, "reverse", "reverse a list or string", 1, 1, COLLECTION);
    }
    public static class Sort {
        public static final String KEY = "cg.fn:sort";
        @Seed public static final Function SEED = fn(KEY, "sort", "sort a list", 1, 1, COLLECTION);
    }
    public static class Unique {
        public static final String KEY = "cg.fn:unique";
        @Seed public static final Function SEED = fn(KEY, "unique", "remove duplicates from a list", 1, 1, COLLECTION, "distinct");
    }
    public static class Flatten {
        public static final String KEY = "cg.fn:flatten";
        @Seed public static final Function SEED = fn(KEY, "flatten", "flatten nested lists into a single list", 1, 1, COLLECTION);
    }

    // ==================================================================================
    // SEED INSTANCES — Utility
    // ==================================================================================

    public static class Typeof {
        public static final String KEY = "cg.fn:typeof";
        @Seed public static final Function SEED = fn(KEY, "typeof", "get the type name of a value", 1, 1, UTILITY, "type");
    }
    public static class IsNull {
        public static final String KEY = "cg.fn:isnull";
        @Seed public static final Function SEED = fn(KEY, "isNull", "check whether a value is null", 1, 1, UTILITY, "null?");
    }
    public static class Coalesce {
        public static final String KEY = "cg.fn:coalesce";
        @Seed public static final Function SEED = fn(KEY, "coalesce", "return the first non-null argument", 1, -1, UTILITY);
    }
    public static class Default {
        public static final String KEY = "cg.fn:default";
        @Seed public static final Function SEED = fn(KEY, "default", "return a default value if the first is null", 2, 2, UTILITY);
    }

    // ==================================================================================
    // SEED INSTANCES — Time
    // ==================================================================================

    public static class Now {
        public static final String KEY = "cg.fn:now";
        @Seed public static final Function SEED = fn(KEY, "now", "current time in milliseconds", 0, 0, TIME);
    }
    public static class Timestamp {
        public static final String KEY = "cg.fn:timestamp";
        @Seed public static final Function SEED = fn(KEY, "timestamp", "current time in seconds since epoch", 0, 0, TIME);
    }

    // ==================================================================================
    // LOOKUP (lazy holder to avoid circular static init)
    // ==================================================================================

    private static class Seeds {
        static final List<Function> ALL = List.of(
                // Math
                Abs.SEED, Ceil.SEED, Floor.SEED, Round.SEED, Sqrt.SEED,
                Pow.SEED, Log.SEED, Sin.SEED, Cos.SEED, Tan.SEED, Random.SEED,
                // Coercion
                ToString.SEED, ToNumber.SEED, ToBool.SEED,
                // String
                Upper.SEED, Lower.SEED, Trim.SEED, Length.SEED, Substring.SEED,
                Split.SEED, Join.SEED, Format.SEED,
                // Collection
                MapFn.SEED, Filter.SEED, Reduce.SEED, Range.SEED, Reverse.SEED,
                Sort.SEED, Unique.SEED, Flatten.SEED,
                // Utility
                Typeof.SEED, IsNull.SEED, Coalesce.SEED, Default.SEED,
                // Time
                Now.SEED, Timestamp.SEED
        );
        static final Map<String, Function> BY_NAME = buildByName();
    }

    /**
     * Look up a seed function by name (case-insensitive).
     * Checks primary name and all aliases.
     */
    public static Function lookupByName(String name) {
        if (name == null) return null;
        return Seeds.BY_NAME.get(name.toLowerCase());
    }

    /** Get all seed functions. */
    public static List<Function> seeds() {
        return Seeds.ALL;
    }

    // ==================================================================================
    // CONSTRUCTORS
    // ==================================================================================

    /** Seed constructor — name and aliases map to Sememe's symbols and tokens. */
    public Function(String canonicalKey, String name, String gloss,
                    int minArity, int maxArity, String category,
                    List<String> aliases) {
        super(canonicalKey,
                Map.of("en", gloss),
                Map.of(),
                List.of(name),       // primary name as universal symbol
                aliases);            // aliases as English tokens
        this.minArity = minArity;
        this.maxArity = maxArity;
        this.category = category;
    }

    /** Type seed constructor. */
    @SuppressWarnings("unused")
    protected Function(ItemID typeId) {
        super(typeId);
    }

    /** Hydration constructor. */
    @SuppressWarnings("unused")
    protected Function(Librarian librarian, Manifest manifest) {
        super(librarian, manifest);
    }

    // ==================================================================================
    // CONVENIENCE ACCESSORS
    // ==================================================================================

    /** Primary function name (first symbol). */
    public String name() {
        List<String> syms = symbols();
        return (syms != null && !syms.isEmpty()) ? syms.getFirst() : displayToken();
    }

    /** Whether this function accepts a variable number of arguments. */
    public boolean isVariadic() {
        return maxArity < 0;
    }

    // ==================================================================================
    // EVALUATION
    // ==================================================================================

    /**
     * Evaluate this function with the given arguments.
     *
     * <p>Dispatches by canonical key, same pattern as {@link Operator#evaluate}.
     */
    @SuppressWarnings("unchecked")
    public Object evaluate(List<Object> args, EvaluationContext context) {
        return switch (canonicalKey()) {
            // --- Type coercion ---
            case "cg.fn:tostring" -> args.isEmpty() ? "" : String.valueOf(args.getFirst());
            case "cg.fn:tonumber" -> {
                if (args.isEmpty()) yield 0.0;
                Object arg = args.getFirst();
                if (arg instanceof Number n) yield n.doubleValue();
                try {
                    yield Double.parseDouble(String.valueOf(arg));
                } catch (NumberFormatException e) {
                    yield 0.0;
                }
            }
            case "cg.fn:tobool" -> {
                if (args.isEmpty()) yield false;
                Object arg = args.getFirst();
                if (arg instanceof Boolean b) yield b;
                if (arg instanceof Number n) yield n.doubleValue() != 0;
                if (arg instanceof String s) yield !s.isEmpty();
                yield arg != null;
            }

            // --- String ---
            case "cg.fn:format" -> {
                if (args.isEmpty()) yield "";
                String fmt = String.valueOf(args.getFirst());
                Object[] fmtArgs = args.subList(1, args.size()).toArray();
                yield String.format(fmt, fmtArgs);
            }
            case "cg.fn:upper" -> args.isEmpty() ? "" : String.valueOf(args.getFirst()).toUpperCase();
            case "cg.fn:lower" -> args.isEmpty() ? "" : String.valueOf(args.getFirst()).toLowerCase();
            case "cg.fn:trim" -> args.isEmpty() ? "" : String.valueOf(args.getFirst()).trim();
            case "cg.fn:length" -> {
                if (args.isEmpty()) yield 0;
                Object arg = args.getFirst();
                if (arg instanceof String s) yield s.length();
                if (arg instanceof List<?> l) yield l.size();
                yield 0;
            }
            case "cg.fn:substring" -> {
                if (args.size() < 2) yield "";
                String s = String.valueOf(args.get(0));
                int start = ((Number) args.get(1)).intValue();
                if (args.size() >= 3) {
                    int end = ((Number) args.get(2)).intValue();
                    yield s.substring(Math.max(0, start), Math.min(s.length(), end));
                }
                yield s.substring(Math.max(0, start));
            }
            case "cg.fn:split" -> {
                if (args.size() < 2) yield List.of();
                String s = String.valueOf(args.get(0));
                String delim = String.valueOf(args.get(1));
                yield List.of(s.split(delim));
            }
            case "cg.fn:join" -> {
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

            // --- Collection ---
            case "cg.fn:map" -> {
                // TODO: Needs lambda support
                if (args.isEmpty()) yield List.of();
                yield args.getFirst();
            }
            case "cg.fn:filter" -> {
                // TODO: Needs lambda support
                if (args.isEmpty()) yield List.of();
                yield args.getFirst();
            }
            case "cg.fn:reduce" -> {
                // TODO: Needs lambda support
                if (args.size() < 2) yield null;
                yield args.get(1);
            }
            case "cg.fn:range" -> {
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
            case "cg.fn:reverse" -> {
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
            case "cg.fn:sort" -> {
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
            case "cg.fn:unique" -> {
                if (args.isEmpty()) yield List.of();
                Object arg = args.getFirst();
                if (arg instanceof List<?> list) {
                    yield list.stream().distinct().toList();
                }
                yield arg;
            }
            case "cg.fn:flatten" -> {
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

            // --- Math ---
            case "cg.fn:abs" -> args.isEmpty() ? 0 : Math.abs(((Number) args.getFirst()).doubleValue());
            case "cg.fn:ceil" -> args.isEmpty() ? 0 : Math.ceil(((Number) args.getFirst()).doubleValue());
            case "cg.fn:floor" -> args.isEmpty() ? 0 : Math.floor(((Number) args.getFirst()).doubleValue());
            case "cg.fn:round" -> args.isEmpty() ? 0 : Math.round(((Number) args.getFirst()).doubleValue());
            case "cg.fn:sqrt" -> args.isEmpty() ? 0 : Math.sqrt(((Number) args.getFirst()).doubleValue());
            case "cg.fn:pow" -> args.size() < 2 ? 0 :
                    Math.pow(((Number) args.get(0)).doubleValue(), ((Number) args.get(1)).doubleValue());
            case "cg.fn:log" -> args.isEmpty() ? 0 : Math.log(((Number) args.getFirst()).doubleValue());
            case "cg.fn:sin" -> args.isEmpty() ? 0 : Math.sin(((Number) args.getFirst()).doubleValue());
            case "cg.fn:cos" -> args.isEmpty() ? 0 : Math.cos(((Number) args.getFirst()).doubleValue());
            case "cg.fn:tan" -> args.isEmpty() ? 0 : Math.tan(((Number) args.getFirst()).doubleValue());
            case "cg.fn:random" -> Math.random();

            // --- Time ---
            case "cg.fn:now" -> System.currentTimeMillis();
            case "cg.fn:timestamp" -> System.currentTimeMillis() / 1000;

            // --- Utility ---
            case "cg.fn:typeof" -> {
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
            case "cg.fn:isnull" -> args.isEmpty() || args.getFirst() == null;
            case "cg.fn:coalesce" -> args.stream().filter(a -> a != null).findFirst().orElse(null);
            case "cg.fn:default" -> {
                if (args.size() < 2) yield null;
                Object val = args.get(0);
                Object def = args.get(1);
                yield val != null ? val : def;
            }

            default -> throw new UnsupportedOperationException(
                    "No evaluator for function: " + canonicalKey());
        };
    }

    // ==================================================================================
    // FACTORIES (compact seed declaration)
    // ==================================================================================

    /** Create a seed function with no aliases. */
    private static Function fn(String key, String name, String gloss,
                               int minArity, int maxArity, String category) {
        return new Function(key, name, gloss, minArity, maxArity, category, List.of());
    }

    /** Create a seed function with aliases. */
    private static Function fn(String key, String name, String gloss,
                               int minArity, int maxArity, String category,
                               String... aliases) {
        return new Function(key, name, gloss, minArity, maxArity, category, List.of(aliases));
    }

    // ==================================================================================
    // STATIC BUILDERS
    // ==================================================================================

    private static Map<String, Function> buildByName() {
        Map<String, Function> out = new LinkedHashMap<>();
        for (Function f : Seeds.ALL) {
            // Primary name
            String name = f.name();
            if (name != null) out.put(name.toLowerCase(), f);
            // Aliases (tokens)
            List<String> toks = f.tokens();
            if (toks != null) {
                for (String alias : toks) {
                    if (alias != null) out.put(alias.toLowerCase(), f);
                }
            }
        }
        return Map.copyOf(out);
    }

    @Override
    public String toString() {
        return name() + "(" + (isVariadic() ? "..." : minArity == maxArity
                ? String.valueOf(minArity) : minArity + "-" + maxArity) + ")";
    }
}
