package dev.everydaythings.graph.value;

import dev.everydaythings.graph.item.Manifest;
import dev.everydaythings.graph.item.component.Type;
import dev.everydaythings.graph.item.component.expression.EvaluationContext;
import dev.everydaythings.graph.item.component.expression.Expression;
import dev.everydaythings.graph.item.id.ItemID;
import dev.everydaythings.graph.language.NounSememe;
import dev.everydaythings.graph.runtime.Librarian;
import lombok.Getter;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * An Operator — a noun sememe with symbol, precedence, and evaluation.
 *
 * <p>Every operator — arithmetic, comparison, logical, structural — is a
 * noun in the vocabulary, discoverable and relatable. Operators know their
 * symbol, precedence, associativity, fixity, and how to evaluate themselves.
 *
 * <p>By extending {@link NounSememe}, operators inherit glosses, tokens,
 * symbols, and dictionary registration — making them discoverable through
 * the same vocabulary pipeline as any other sememe.
 *
 * <p>This unifies the expression system: verb frames, mathematical formulas,
 * queries, and reactive rules all compose using the same operator vocabulary.
 */
@Type(value = Operator.KEY, glyph = "➕")
public class Operator extends NounSememe {

    public static final String KEY = "cg:type/operator";

    public enum Associativity { LEFT, RIGHT, NONE }
    public enum Fixity { PREFIX, INFIX, POSTFIX }

    // ==================================================================================
    // SEED INSTANCES
    // ==================================================================================

    // --- Logical ---
    @Seed public static final Operator AND = new Operator(
            "cg.op:and", "&&", "and", 2, 1, Associativity.LEFT, Fixity.INFIX);
    @Seed public static final Operator OR = new Operator(
            "cg.op:or", "||", "or", 2, 0, Associativity.LEFT, Fixity.INFIX);
    @Seed public static final Operator NOT = new Operator(
            "cg.op:not", "!", "not", 1, 25, Associativity.RIGHT, Fixity.PREFIX);

    // --- Arithmetic ---
    @Seed public static final Operator ADD = new Operator(
            "cg.op:add", "+", "add", 2, 10, Associativity.LEFT, Fixity.INFIX);
    @Seed public static final Operator SUBTRACT = new Operator(
            "cg.op:sub", "-", "subtract", 2, 10, Associativity.LEFT, Fixity.INFIX);
    @Seed public static final Operator MULTIPLY = new Operator(
            "cg.op:mul", "*", "multiply", 2, 20, Associativity.LEFT, Fixity.INFIX);
    @Seed public static final Operator DIVIDE = new Operator(
            "cg.op:div", "/", "divide", 2, 20, Associativity.LEFT, Fixity.INFIX);
    @Seed public static final Operator MODULO = new Operator(
            "cg.op:mod", "%", "modulo", 2, 20, Associativity.LEFT, Fixity.INFIX);
    @Seed public static final Operator POWER = new Operator(
            "cg.op:pow", "^", "power", 2, 30, Associativity.RIGHT, Fixity.INFIX);
    @Seed public static final Operator NEGATE = new Operator(
            "cg.op:neg", "-", "negate", 1, 25, Associativity.RIGHT, Fixity.PREFIX);

    // --- Comparison ---
    @Seed public static final Operator EQUAL = new Operator(
            "cg.op:eq", "==", "equal", 2, 5, Associativity.NONE, Fixity.INFIX);
    @Seed public static final Operator NOT_EQUAL = new Operator(
            "cg.op:ne", "!=", "not equal", 2, 5, Associativity.NONE, Fixity.INFIX);
    @Seed public static final Operator LESS_THAN = new Operator(
            "cg.op:lt", "<", "less than", 2, 5, Associativity.NONE, Fixity.INFIX);
    @Seed public static final Operator GREATER_THAN = new Operator(
            "cg.op:gt", ">", "greater than", 2, 5, Associativity.NONE, Fixity.INFIX);
    @Seed public static final Operator LESS_OR_EQUAL = new Operator(
            "cg.op:le", "<=", "less or equal", 2, 5, Associativity.NONE, Fixity.INFIX);
    @Seed public static final Operator GREATER_OR_EQUAL = new Operator(
            "cg.op:ge", ">=", "greater or equal", 2, 5, Associativity.NONE, Fixity.INFIX);

    // --- String ---
    @Seed public static final Operator CONCAT = new Operator(
            "cg.op:concat", "++", "concat", 2, 10, Associativity.LEFT, Fixity.INFIX);

    // --- Collection ---
    @Seed public static final Operator IN = new Operator(
            "cg.op:in", "in", "in", 2, 5, Associativity.NONE, Fixity.INFIX);
    @Seed public static final Operator CONTAINS = new Operator(
            "cg.op:contains", "contains", "contains", 2, 5, Associativity.NONE, Fixity.INFIX);

    // --- Structural ---
    @Seed public static final Operator ASSIGN = new Operator(
            "cg.op:assign", "=", "assign", 2, -5, Associativity.RIGHT, Fixity.INFIX);
    @Seed public static final Operator PIPE = new Operator(
            "cg.op:pipe", "|>", "pipe", 2, -10, Associativity.LEFT, Fixity.INFIX);

    // ==================================================================================
    // LOOKUP TABLES
    // ==================================================================================

    private static final List<Operator> SEED_OPERATORS = List.of(
            AND, OR, NOT,
            ADD, SUBTRACT, MULTIPLY, DIVIDE, MODULO, POWER, NEGATE,
            EQUAL, NOT_EQUAL, LESS_THAN, GREATER_THAN, LESS_OR_EQUAL, GREATER_OR_EQUAL,
            CONCAT, IN, CONTAINS,
            ASSIGN, PIPE
    );
    private static final Map<ItemID, Operator> SEED_BY_ID = buildSeedById();
    private static final Map<String, Operator> INFIX_BY_SYMBOL = buildInfixBySymbol();
    private static final Map<String, Operator> PREFIX_BY_SYMBOL = buildPrefixBySymbol();

    // ==================================================================================
    // INSTANCE FIELDS (operator-specific; canonicalKey, glosses, symbols inherited)
    // ==================================================================================

    @Getter @ContentField
    private int arity;

    @Getter @ContentField
    private int precedence;

    @Getter @ContentField
    private Associativity associativity;

    @Getter @ContentField
    private Fixity fixity;

    // ==================================================================================
    // CONSTRUCTORS
    // ==================================================================================

    /** Seed constructor — symbol and name map to Sememe's symbols and tokens/glosses. */
    public Operator(String canonicalKey, String symbol, String name,
                    int arity, int precedence,
                    Associativity associativity, Fixity fixity) {
        super(canonicalKey,
                Map.of("en", name),     // name serves as English gloss
                Map.of(),               // no CILI sources for operators
                List.of(symbol),        // symbol as universal symbol
                List.of(name));         // name also as English token
        this.arity = arity;
        this.precedence = precedence;
        this.associativity = associativity != null ? associativity : Associativity.LEFT;
        this.fixity = fixity != null ? fixity : Fixity.INFIX;
    }

    /** Type seed constructor. */
    @SuppressWarnings("unused")
    protected Operator(ItemID typeId) {
        super(typeId);
    }

    /** Hydration constructor. */
    @SuppressWarnings("unused")
    protected Operator(Librarian librarian, Manifest manifest) {
        super(librarian, manifest);
    }

    // ==================================================================================
    // EVALUATION — Binary
    // ==================================================================================

    /**
     * Evaluate this binary operator with two operand expressions.
     *
     * <p>Short-circuit operators (AND, OR) evaluate lazily.
     * All others evaluate both operands first.
     */
    public Object evaluate(Expression left, Expression right, EvaluationContext context) {
        if (canonicalKey() == null) {
            throw new UnsupportedOperationException("Operator has no canonical key");
        }

        // Short-circuit: evaluate left first, maybe skip right
        if (isShortCircuit()) {
            return evaluateShortCircuit(left, right, context);
        }

        // Eager: evaluate both operands
        Object leftVal = left.evaluate(context);
        Object rightVal = right.evaluate(context);

        // Resolve ItemIDs to Units for quantity construction
        if (leftVal instanceof ItemID lid) {
            Unit u = Unit.lookupSeed(lid);
            if (u != null) leftVal = u;
        }
        if (rightVal instanceof ItemID rid) {
            Unit u = Unit.lookupSeed(rid);
            if (u != null) rightVal = u;
        }

        return applyBinary(leftVal, rightVal);
    }

    private Object evaluateShortCircuit(Expression left, Expression right, EvaluationContext context) {
        return switch (canonicalKey()) {
            case "cg.op:and" -> {
                Object leftVal = left.evaluate(context);
                if (!toBoolean(leftVal)) yield false;
                yield toBoolean(right.evaluate(context));
            }
            case "cg.op:or" -> {
                Object leftVal = left.evaluate(context);
                if (toBoolean(leftVal)) yield true;
                yield toBoolean(right.evaluate(context));
            }
            default -> throw new UnsupportedOperationException(
                    "Not a short-circuit operator: " + canonicalKey());
        };
    }

    private Object applyBinary(Object left, Object right) {
        return switch (canonicalKey()) {
            // Arithmetic
            case "cg.op:add" -> {
                if (left instanceof Quantity lq && right instanceof Quantity rq)
                    yield quantityAdd(lq, rq);
                if (left instanceof Number l && right instanceof Number r)
                    yield toNumber(l) + toNumber(r);
                yield String.valueOf(left) + String.valueOf(right);
            }
            case "cg.op:sub" -> {
                if (left instanceof Quantity lq && right instanceof Quantity rq)
                    yield quantitySubtract(lq, rq);
                yield toNumber(left) - toNumber(right);
            }
            case "cg.op:mul" -> {
                // Quantity construction: Number * Unit → Quantity
                if (left instanceof Number n && right instanceof Unit u)
                    yield toQuantity(n, u);
                if (left instanceof Unit u && right instanceof Number n)
                    yield toQuantity(n, u);
                // Scaling: Number * Quantity → Quantity
                if (left instanceof Number n && right instanceof Quantity q)
                    yield scaleQuantity(q, toNumber(n));
                if (left instanceof Quantity q && right instanceof Number n)
                    yield scaleQuantity(q, toNumber(n));
                yield toNumber(left) * toNumber(right);
            }
            case "cg.op:div" -> {
                double r;
                // Quantity / Number → scaled Quantity
                if (left instanceof Quantity q && right instanceof Number n) {
                    r = toNumber(n);
                    if (r == 0) throw new ArithmeticException("Division by zero");
                    yield scaleQuantity(q, 1.0 / r);
                }
                // Quantity / Quantity → dimensionless ratio (if compatible)
                if (left instanceof Quantity lq && right instanceof Quantity rq) {
                    yield quantityDivide(lq, rq);
                }
                r = toNumber(right);
                if (r == 0) throw new ArithmeticException("Division by zero");
                yield toNumber(left) / r;
            }
            case "cg.op:mod" -> {
                double r = toNumber(right);
                if (r == 0) throw new ArithmeticException("Modulo by zero");
                yield toNumber(left) % r;
            }
            case "cg.op:pow" -> Math.pow(toNumber(left), toNumber(right));

            // Comparison
            case "cg.op:eq" -> valueEquals(left, right);
            case "cg.op:ne" -> !valueEquals(left, right);
            case "cg.op:lt" -> valueCompare(left, right) < 0;
            case "cg.op:gt" -> valueCompare(left, right) > 0;
            case "cg.op:le" -> valueCompare(left, right) <= 0;
            case "cg.op:ge" -> valueCompare(left, right) >= 0;

            // String
            case "cg.op:concat" -> String.valueOf(left) + String.valueOf(right);

            // Collection
            case "cg.op:in" -> {
                if (right instanceof List<?> list) yield list.contains(left);
                yield false;
            }
            case "cg.op:contains" -> {
                if (left instanceof List<?> list) yield list.contains(right);
                yield false;
            }

            // Structural — handled by the evaluator, not here
            case "cg.op:assign", "cg.op:pipe" ->
                    throw new UnsupportedOperationException(
                            canonicalKey() + " must be handled by the evaluator, not evaluated directly");

            default -> throw new UnsupportedOperationException(
                    "No evaluator for operator: " + canonicalKey());
        };
    }

    // ==================================================================================
    // EVALUATION — Unary
    // ==================================================================================

    /**
     * Evaluate this unary operator with one operand expression.
     */
    public Object evaluateUnary(Expression operand, EvaluationContext context) {
        Object val = operand.evaluate(context);
        return switch (canonicalKey()) {
            case "cg.op:neg" -> {
                if (val instanceof Quantity q)
                    yield scaleQuantity(q, -1.0);
                if (val instanceof Number n) yield -n.doubleValue();
                yield 0;
            }
            case "cg.op:not" -> !toBoolean(val);
            default -> throw new UnsupportedOperationException(
                    "Not a unary operator: " + canonicalKey());
        };
    }

    // ==================================================================================
    // CONVENIENCE ACCESSORS (derived from inherited Sememe fields)
    // ==================================================================================

    /** Primary symbol (first element of inherited symbols list). */
    public String symbol() {
        List<String> syms = symbols();
        return (syms != null && !syms.isEmpty()) ? syms.getFirst() : null;
    }

    /** English name (first token or derived from canonical key). */
    public String name() {
        List<String> toks = tokens();
        return (toks != null && !toks.isEmpty()) ? toks.getFirst() : displayToken();
    }

    // ==================================================================================
    // PREDICATES
    // ==================================================================================

    public boolean isShortCircuit() {
        return "cg.op:and".equals(canonicalKey()) || "cg.op:or".equals(canonicalKey());
    }

    public boolean isPrefix() {
        return fixity == Fixity.PREFIX;
    }

    public boolean isInfix() {
        return fixity == Fixity.INFIX;
    }

    // ==================================================================================
    // TYPE COERCION AND HELPERS
    // ==================================================================================

    public static boolean toBoolean(Object value) {
        if (value == null) return false;
        if (value instanceof Boolean b) return b;
        if (value instanceof Number n) return n.doubleValue() != 0;
        if (value instanceof String s) return !s.isEmpty();
        if (value instanceof List<?> l) return !l.isEmpty();
        return true;
    }

    public static double toNumber(Object o) {
        if (o instanceof Number n) return n.doubleValue();
        if (o instanceof String s) {
            try { return Double.parseDouble(s); }
            catch (NumberFormatException e) { return 0; }
        }
        return 0;
    }

    public static boolean valueEquals(Object a, Object b) {
        if (a == null && b == null) return true;
        if (a == null || b == null) return false;
        if (a instanceof Number && b instanceof Number) {
            return toNumber(a) == toNumber(b);
        }
        return a.equals(b);
    }

    @SuppressWarnings("unchecked")
    public static int valueCompare(Object a, Object b) {
        if (a instanceof Number && b instanceof Number) {
            return Double.compare(toNumber(a), toNumber(b));
        }
        if (a instanceof Comparable c && b != null) {
            try { return c.compareTo(b); }
            catch (ClassCastException e) { return 0; }
        }
        return 0;
    }

    // ==================================================================================
    // QUANTITY ARITHMETIC
    // ==================================================================================

    /** Number * Unit → Quantity */
    private static Quantity toQuantity(Number n, Unit u) {
        return Quantity.of(decimalFrom(toNumber(n)), u.iid());
    }

    /** Scale a quantity by a factor. */
    private static Quantity scaleQuantity(Quantity q, double factor) {
        return Quantity.of(decimalFrom(q.value().toDouble() * factor), q.unit());
    }

    /** Add two quantities, converting right to left's unit. */
    private static Quantity quantityAdd(Quantity left, Quantity right) {
        Unit lu = Unit.lookupSeed(left.unit());
        Unit ru = Unit.lookupSeed(right.unit());
        if (lu == null || ru == null)
            throw new ArithmeticException("Unknown unit in quantity arithmetic");
        if (!lu.isCompatibleWith(ru))
            throw new ArithmeticException("Cannot add " + lu.symbol() + " and " + ru.symbol()
                    + " — incompatible dimensions");
        double converted = ru.convert(right.value().toDouble(), lu);
        return Quantity.of(decimalFrom(left.value().toDouble() + converted), left.unit());
    }

    /** Subtract two quantities, converting right to left's unit. */
    private static Quantity quantitySubtract(Quantity left, Quantity right) {
        Unit lu = Unit.lookupSeed(left.unit());
        Unit ru = Unit.lookupSeed(right.unit());
        if (lu == null || ru == null)
            throw new ArithmeticException("Unknown unit in quantity arithmetic");
        if (!lu.isCompatibleWith(ru))
            throw new ArithmeticException("Cannot subtract " + ru.symbol() + " from " + lu.symbol()
                    + " — incompatible dimensions");
        double converted = ru.convert(right.value().toDouble(), lu);
        return Quantity.of(decimalFrom(left.value().toDouble() - converted), left.unit());
    }

    /** Divide two quantities — returns dimensionless ratio if compatible. */
    private static double quantityDivide(Quantity left, Quantity right) {
        Unit lu = Unit.lookupSeed(left.unit());
        Unit ru = Unit.lookupSeed(right.unit());
        if (lu == null || ru == null)
            throw new ArithmeticException("Unknown unit in quantity arithmetic");
        if (!lu.isCompatibleWith(ru))
            throw new ArithmeticException("Cannot divide " + lu.symbol() + " by " + ru.symbol()
                    + " — incompatible dimensions");
        double converted = ru.convert(right.value().toDouble(), lu);
        if (converted == 0) throw new ArithmeticException("Division by zero");
        return left.value().toDouble() / converted;
    }

    /** Convert a double to Decimal via string (exact representation). */
    private static Decimal decimalFrom(double d) {
        // For clean integers, avoid floating-point artifacts
        if (d == Math.floor(d) && !Double.isInfinite(d)) {
            return Decimal.ofLong((long) d);
        }
        return Decimal.parse(String.valueOf(d));
    }

    // ==================================================================================
    // LOOKUP
    // ==================================================================================

    /**
     * Look up an Operator by IID. Checks seeds first, then librarian.
     */
    public static Operator lookup(ItemID iid, EvaluationContext context) {
        Operator seed = lookupKnown(iid);
        if (seed != null) return seed;

        if (context != null && context.librarian() != null) {
            return context.librarian().get(iid, Operator.class).orElse(null);
        }
        return null;
    }

    /**
     * Look up a seed operator by IID (no context required).
     */
    public static Operator lookupKnown(ItemID iid) {
        if (iid == null) return null;
        return SEED_BY_ID.get(iid);
    }

    /**
     * Find an infix operator by symbol text.
     */
    public static Operator fromSymbol(String text) {
        if (text == null) return null;
        String trimmed = text.trim();
        Operator op = INFIX_BY_SYMBOL.get(trimmed);
        if (op != null) return op;
        return PREFIX_BY_SYMBOL.get(trimmed);
    }

    /**
     * Find an infix operator by symbol.
     */
    public static Operator infixFromSymbol(String text) {
        if (text == null) return null;
        return INFIX_BY_SYMBOL.get(text.trim());
    }

    /**
     * Find a prefix operator by symbol.
     */
    public static Operator prefixFromSymbol(String text) {
        if (text == null) return null;
        return PREFIX_BY_SYMBOL.get(text.trim());
    }

    /**
     * Get all seed operators.
     */
    public static List<Operator> seeds() {
        return SEED_OPERATORS;
    }

    // ==================================================================================
    // STATIC BUILDERS
    // ==================================================================================

    private static Map<ItemID, Operator> buildSeedById() {
        Map<ItemID, Operator> out = new LinkedHashMap<>();
        for (Operator op : SEED_OPERATORS) {
            out.put(op.iid(), op);
        }
        return Map.copyOf(out);
    }

    private static Map<String, Operator> buildInfixBySymbol() {
        Map<String, Operator> out = new LinkedHashMap<>();
        for (Operator op : SEED_OPERATORS) {
            if (op.fixity() == Fixity.INFIX && op.symbol() != null && !op.symbol().isBlank()) {
                out.put(op.symbol(), op);
            }
        }
        // Aliases
        out.put("&", AND);
        out.put("|", OR);
        out.put("**", POWER);
        return Map.copyOf(out);
    }

    private static Map<String, Operator> buildPrefixBySymbol() {
        Map<String, Operator> out = new LinkedHashMap<>();
        for (Operator op : SEED_OPERATORS) {
            if (op.fixity() == Fixity.PREFIX && op.symbol() != null && !op.symbol().isBlank()) {
                out.put(op.symbol(), op);
            }
        }
        return Map.copyOf(out);
    }

    @Override
    public String toString() {
        return symbol() + " (" + name() + ")";
    }
}
