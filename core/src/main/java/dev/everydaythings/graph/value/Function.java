package dev.everydaythings.graph.value;

import dev.everydaythings.graph.item.Manifest;
import dev.everydaythings.graph.item.component.Type;
import dev.everydaythings.graph.item.id.ItemID;
import dev.everydaythings.graph.language.NounSememe;
import dev.everydaythings.graph.runtime.Librarian;
import lombok.Getter;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
 * <p>Evaluation is currently handled by
 * {@link dev.everydaythings.graph.item.component.expression.FunctionExpression},
 * which dispatches by name. The Function Item provides the vocabulary entry;
 * evaluation can migrate here in the future.
 */
@Type(value = Function.KEY, glyph = "ƒ")
public class Function extends NounSememe {

    public static final String KEY = "cg:type/function";

    // ==================================================================================
    // INSTANCE FIELDS (function-specific; canonicalKey, glosses, symbols inherited)
    // ==================================================================================

    @Getter @ContentField
    private int minArity;

    @Getter @ContentField
    private int maxArity;  // -1 for variadic

    @Getter @ContentField
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

    @Seed public static final Function ABS = fn("cg.fn:abs", "abs",
            "compute the absolute value of a number", 1, 1, MATH);
    @Seed public static final Function CEIL = fn("cg.fn:ceil", "ceil",
            "round up to the nearest integer", 1, 1, MATH);
    @Seed public static final Function FLOOR = fn("cg.fn:floor", "floor",
            "round down to the nearest integer", 1, 1, MATH);
    @Seed public static final Function ROUND = fn("cg.fn:round", "round",
            "round to the nearest integer", 1, 1, MATH);
    @Seed public static final Function SQRT = fn("cg.fn:sqrt", "sqrt",
            "compute the positive square root", 1, 1, MATH,
            "square root");
    @Seed public static final Function POW = fn("cg.fn:pow", "pow",
            "raise to a power", 2, 2, MATH,
            "power", "exponent");
    @Seed public static final Function LOG = fn("cg.fn:log", "log",
            "compute the natural logarithm", 1, 1, MATH,
            "logarithm");
    @Seed public static final Function SIN = fn("cg.fn:sin", "sin",
            "compute the sine", 1, 1, MATH, "sine");
    @Seed public static final Function COS = fn("cg.fn:cos", "cos",
            "compute the cosine", 1, 1, MATH, "cosine");
    @Seed public static final Function TAN = fn("cg.fn:tan", "tan",
            "compute the tangent", 1, 1, MATH, "tangent");
    @Seed public static final Function RANDOM = fn("cg.fn:random", "random",
            "generate a random number between 0 and 1", 0, 0, MATH,
            "rand");

    // ==================================================================================
    // SEED INSTANCES — Type Coercion
    // ==================================================================================

    @Seed public static final Function TO_STRING = fn("cg.fn:tostring", "toString",
            "convert a value to its string representation", 1, 1, COERCION,
            "str");
    @Seed public static final Function TO_NUMBER = fn("cg.fn:tonumber", "toNumber",
            "convert a value to a number", 1, 1, COERCION,
            "num");
    @Seed public static final Function TO_BOOL = fn("cg.fn:tobool", "toBool",
            "convert a value to a boolean", 1, 1, COERCION,
            "bool");

    // ==================================================================================
    // SEED INSTANCES — String
    // ==================================================================================

    @Seed public static final Function UPPER = fn("cg.fn:upper", "upper",
            "convert to uppercase", 1, 1, STRING,
            "uppercase");
    @Seed public static final Function LOWER = fn("cg.fn:lower", "lower",
            "convert to lowercase", 1, 1, STRING,
            "lowercase");
    @Seed public static final Function TRIM = fn("cg.fn:trim", "trim",
            "remove leading and trailing whitespace", 1, 1, STRING);
    @Seed public static final Function LENGTH = fn("cg.fn:length", "length",
            "get the length of a string or collection", 1, 1, STRING,
            "len");
    @Seed public static final Function SUBSTRING = fn("cg.fn:substring", "substring",
            "extract a portion of a string", 2, 3, STRING,
            "substr");
    @Seed public static final Function SPLIT = fn("cg.fn:split", "split",
            "split a string by delimiter", 2, 2, STRING);
    @Seed public static final Function JOIN = fn("cg.fn:join", "join",
            "join a list into a string with delimiter", 2, 2, STRING);
    @Seed public static final Function FORMAT = fn("cg.fn:format", "format",
            "format a string with arguments", 1, -1, STRING);

    // ==================================================================================
    // SEED INSTANCES — Collection
    // ==================================================================================

    @Seed public static final Function MAP = fn("cg.fn:map", "map",
            "apply a function to each element", 2, 2, COLLECTION);
    @Seed public static final Function FILTER = fn("cg.fn:filter", "filter",
            "keep elements matching a predicate", 2, 2, COLLECTION);
    @Seed public static final Function REDUCE = fn("cg.fn:reduce", "reduce",
            "fold a collection into a single value", 3, 3, COLLECTION,
            "fold");
    @Seed public static final Function RANGE = fn("cg.fn:range", "range",
            "generate a sequence of integers", 1, 3, COLLECTION);
    @Seed public static final Function REVERSE = fn("cg.fn:reverse", "reverse",
            "reverse a list or string", 1, 1, COLLECTION);
    @Seed public static final Function SORT = fn("cg.fn:sort", "sort",
            "sort a list", 1, 1, COLLECTION);
    @Seed public static final Function UNIQUE = fn("cg.fn:unique", "unique",
            "remove duplicates from a list", 1, 1, COLLECTION,
            "distinct");
    @Seed public static final Function FLATTEN = fn("cg.fn:flatten", "flatten",
            "flatten nested lists into a single list", 1, 1, COLLECTION);

    // ==================================================================================
    // SEED INSTANCES — Utility
    // ==================================================================================

    @Seed public static final Function TYPEOF = fn("cg.fn:typeof", "typeof",
            "get the type name of a value", 1, 1, UTILITY,
            "type");
    @Seed public static final Function IS_NULL = fn("cg.fn:isnull", "isNull",
            "check whether a value is null", 1, 1, UTILITY,
            "null?");
    @Seed public static final Function COALESCE = fn("cg.fn:coalesce", "coalesce",
            "return the first non-null argument", 1, -1, UTILITY);
    @Seed public static final Function DEFAULT = fn("cg.fn:default", "default",
            "return a default value if the first is null", 2, 2, UTILITY);

    // ==================================================================================
    // SEED INSTANCES — Time
    // ==================================================================================

    @Seed public static final Function NOW = fn("cg.fn:now", "now",
            "current time in milliseconds", 0, 0, TIME);
    @Seed public static final Function TIMESTAMP = fn("cg.fn:timestamp", "timestamp",
            "current time in seconds since epoch", 0, 0, TIME);

    // ==================================================================================
    // LOOKUP
    // ==================================================================================

    private static final List<Function> SEED_FUNCTIONS = List.of(
            // Math
            ABS, CEIL, FLOOR, ROUND, SQRT, POW, LOG, SIN, COS, TAN, RANDOM,
            // Coercion
            TO_STRING, TO_NUMBER, TO_BOOL,
            // String
            UPPER, LOWER, TRIM, LENGTH, SUBSTRING, SPLIT, JOIN, FORMAT,
            // Collection
            MAP, FILTER, REDUCE, RANGE, REVERSE, SORT, UNIQUE, FLATTEN,
            // Utility
            TYPEOF, IS_NULL, COALESCE, DEFAULT,
            // Time
            NOW, TIMESTAMP
    );

    private static final Map<String, Function> BY_NAME = buildByName();

    /**
     * Look up a seed function by name (case-insensitive).
     * Checks primary name and all aliases.
     */
    public static Function lookupByName(String name) {
        if (name == null) return null;
        return BY_NAME.get(name.toLowerCase());
    }

    /** Get all seed functions. */
    public static List<Function> seeds() {
        return SEED_FUNCTIONS;
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
        for (Function f : SEED_FUNCTIONS) {
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
