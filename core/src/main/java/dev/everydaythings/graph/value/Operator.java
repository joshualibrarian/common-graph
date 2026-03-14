package dev.everydaythings.graph.value;

import dev.everydaythings.graph.item.Item;
import dev.everydaythings.graph.item.Manifest;
import dev.everydaythings.graph.frame.ExpressionComponent;
import dev.everydaythings.graph.item.Type;
import dev.everydaythings.graph.frame.expression.EvaluationContext;
import dev.everydaythings.graph.frame.expression.Expression;
import dev.everydaythings.graph.frame.expression.FunctionExpression;
import dev.everydaythings.graph.frame.expression.ReferenceExpression;
import dev.everydaythings.graph.frame.expression.SememeExpression;
import dev.everydaythings.graph.item.id.ItemID;
import dev.everydaythings.graph.language.PartOfSpeech;
import dev.everydaythings.graph.language.Sememe;
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
 * <p>By extending {@link Sememe}, operators inherit glosses, tokens,
 * symbols, and dictionary registration — making them discoverable through
 * the same vocabulary pipeline as any other sememe.
 *
 * <p>This unifies the expression system: verb frames, mathematical formulas,
 * queries, and reactive rules all compose using the same operator vocabulary.
 */
@Type(value = Operator.KEY, glyph = "➕")
public class Operator extends Sememe {

    public static final String KEY = "cg:type/operator";

    public enum Associativity { LEFT, RIGHT, NONE }
    public enum Fixity { PREFIX, INFIX, POSTFIX }

    // ==================================================================================
    // SEED INSTANCES
    // ==================================================================================

    // --- Logical ---
    public static class And {
        public static final String KEY = "cg.op:and";
        @Seed public static final Operator SEED = new Operator(KEY, "&&", "and", 2, 1, Associativity.LEFT, Fixity.INFIX);
    }
    public static class Or {
        public static final String KEY = "cg.op:or";
        @Seed public static final Operator SEED = new Operator(KEY, "||", "or", 2, 0, Associativity.LEFT, Fixity.INFIX);
    }
    public static class Not {
        public static final String KEY = "cg.op:not";
        @Seed public static final Operator SEED = new Operator(KEY, "!", "not", 1, 25, Associativity.RIGHT, Fixity.PREFIX);
    }

    // --- Arithmetic ---
    public static class Add {
        public static final String KEY = "cg.op:add";
        @Seed public static final Operator SEED = new Operator(KEY, "+", "add", 2, 10, Associativity.LEFT, Fixity.INFIX);
    }
    public static class Subtract {
        public static final String KEY = "cg.op:sub";
        @Seed public static final Operator SEED = new Operator(KEY, "-", "subtract", 2, 10, Associativity.LEFT, Fixity.INFIX);
    }
    public static class Multiply {
        public static final String KEY = "cg.op:mul";
        @Seed public static final Operator SEED = new Operator(KEY, "*", "multiply", 2, 20, Associativity.LEFT, Fixity.INFIX);
    }
    public static class Divide {
        public static final String KEY = "cg.op:div";
        @Seed public static final Operator SEED = new Operator(KEY, "/", "divide", 2, 20, Associativity.LEFT, Fixity.INFIX);
    }
    public static class Modulo {
        public static final String KEY = "cg.op:mod";
        @Seed public static final Operator SEED = new Operator(KEY, "%", "modulo", 2, 20, Associativity.LEFT, Fixity.INFIX);
    }
    public static class Power {
        public static final String KEY = "cg.op:pow";
        @Seed public static final Operator SEED = new Operator(KEY, "^", "power", 2, 30, Associativity.RIGHT, Fixity.INFIX);
    }
    public static class Negate {
        public static final String KEY = "cg.op:neg";
        @Seed public static final Operator SEED = new Operator(KEY, "-", "negate", 1, 25, Associativity.RIGHT, Fixity.PREFIX);
    }

    // --- Comparison ---
    public static class Equal {
        public static final String KEY = "cg.op:eq";
        @Seed public static final Operator SEED = new Operator(KEY, "==", "equal", 2, 5, Associativity.NONE, Fixity.INFIX);
    }
    public static class NotEqual {
        public static final String KEY = "cg.op:ne";
        @Seed public static final Operator SEED = new Operator(KEY, "!=", "not equal", 2, 5, Associativity.NONE, Fixity.INFIX);
    }
    public static class LessThan {
        public static final String KEY = "cg.op:lt";
        @Seed public static final Operator SEED = new Operator(KEY, "<", "less than", 2, 5, Associativity.NONE, Fixity.INFIX);
    }
    public static class GreaterThan {
        public static final String KEY = "cg.op:gt";
        @Seed public static final Operator SEED = new Operator(KEY, ">", "greater than", 2, 5, Associativity.NONE, Fixity.INFIX);
    }
    public static class LessOrEqual {
        public static final String KEY = "cg.op:le";
        @Seed public static final Operator SEED = new Operator(KEY, "<=", "less or equal", 2, 5, Associativity.NONE, Fixity.INFIX);
    }
    public static class GreaterOrEqual {
        public static final String KEY = "cg.op:ge";
        @Seed public static final Operator SEED = new Operator(KEY, ">=", "greater or equal", 2, 5, Associativity.NONE, Fixity.INFIX);
    }

    // --- String ---
    public static class Concat {
        public static final String KEY = "cg.op:concat";
        @Seed public static final Operator SEED = new Operator(KEY, "++", "concat", 2, 10, Associativity.LEFT, Fixity.INFIX);
    }

    // --- Collection ---
    public static class In {
        public static final String KEY = "cg.op:in";
        @Seed public static final Operator SEED = new Operator(KEY, "in", "in", 2, 5, Associativity.NONE, Fixity.INFIX);
    }
    public static class Contains {
        public static final String KEY = "cg.op:contains";
        @Seed public static final Operator SEED = new Operator(KEY, "contains", "contains", 2, 5, Associativity.NONE, Fixity.INFIX);
    }

    // --- Structural ---
    public static class Assign {
        public static final String KEY = "cg.op:assign";
        @Seed public static final Operator SEED = new Operator(KEY, "=", "assign", 2, -5, Associativity.RIGHT, Fixity.INFIX);
    }
    public static class IsOp {
        public static final String KEY = "cg.op:is";
        @Seed public static final Operator SEED = new Operator(KEY, "is", "is", 2, -5, Associativity.RIGHT, Fixity.INFIX);
    }
    public static class Pipe {
        public static final String KEY = "cg.op:pipe";
        @Seed public static final Operator SEED = new Operator(KEY, "|>", "pipe", 2, -10, Associativity.LEFT, Fixity.INFIX);
    }

    // ==================================================================================
    // LOOKUP TABLES
    // ==================================================================================

    // Lazy holder to avoid circular static init (Sememe -> CoreVocabulary -> Operator.<clinit>)
    private static class Seeds {
        static final List<Operator> ALL = List.of(
                And.SEED, Or.SEED, Not.SEED,
                Add.SEED, Subtract.SEED, Multiply.SEED, Divide.SEED, Modulo.SEED, Power.SEED, Negate.SEED,
                Equal.SEED, NotEqual.SEED, LessThan.SEED, GreaterThan.SEED, LessOrEqual.SEED, GreaterOrEqual.SEED,
                Concat.SEED, In.SEED, Contains.SEED,
                Assign.SEED, IsOp.SEED, Pipe.SEED
        );
        static final Map<ItemID, Operator> BY_ID;
        static final Map<String, Operator> INFIX_BY_SYMBOL;
        static final Map<String, Operator> PREFIX_BY_SYMBOL;
        static {
            Map<ItemID, Operator> byId = new LinkedHashMap<>();
            Map<String, Operator> infix = new LinkedHashMap<>();
            Map<String, Operator> prefix = new LinkedHashMap<>();
            for (Operator op : ALL) {
                byId.put(op.iid(), op);
                if (op.fixity() == Fixity.INFIX && op.symbol() != null && !op.symbol().isBlank())
                    infix.put(op.symbol(), op);
                if (op.fixity() == Fixity.PREFIX && op.symbol() != null && !op.symbol().isBlank())
                    prefix.put(op.symbol(), op);
            }
            infix.put("&", And.SEED);
            infix.put("|", Or.SEED);
            infix.put("**", Power.SEED);
            BY_ID = Map.copyOf(byId);
            INFIX_BY_SYMBOL = Map.copyOf(infix);
            PREFIX_BY_SYMBOL = Map.copyOf(prefix);
        }
    }

    // ==================================================================================
    // INSTANCE FIELDS (operator-specific; canonicalKey, glosses, symbols inherited)
    // ==================================================================================

    @Getter @Frame
    private int arity;

    @Getter @Frame
    private int precedence;

    @Getter @Frame
    private Associativity associativity;

    @Getter @Frame
    private Fixity fixity;

    // ==================================================================================
    // CONSTRUCTORS
    // ==================================================================================

    /** Seed constructor — symbol and name map to Sememe's symbols and tokens/glosses. */
    public Operator(String canonicalKey, String symbol, String name,
                    int arity, int precedence,
                    Associativity associativity, Fixity fixity) {
        super(canonicalKey, PartOfSpeech.NOUN,
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

        // Assignment: LHS is a name (or function definition), not a value to evaluate
        if ("cg.op:assign".equals(canonicalKey())) {
            return evaluateAssign(left, right, context);
        }

        // Copula "is": same as assignment but supports inverted form (value is property)
        if ("cg.op:is".equals(canonicalKey())) {
            return evaluateIs(left, right, context);
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

    /**
     * Evaluate assignment: {@code x = expr}, {@code f(x) = expr}, or {@code sememe = expr}.
     *
     * <p>The LHS determines the kind of assignment:
     * <ul>
     *   <li>{@link FunctionExpression} → function definition: store body + param names</li>
     *   <li>{@link ReferenceExpression} (local) → variable assignment: store expression</li>
     *   <li>{@link SememeExpression} → sememe-keyed property: store with sememe's token as handle</li>
     * </ul>
     */
    private Object evaluateAssign(Expression left, Expression right, EvaluationContext context) {
        Item owner = context.owner();
        if (owner == null) {
            throw new IllegalStateException("Cannot assign: no focused item");
        }

        if (left instanceof FunctionExpression fn) {
            // Function definition: f(x, y) = body → store as ExpressionComponent with params
            List<String> params = fn.arguments().stream()
                    .map(arg -> {
                        if (arg instanceof ReferenceExpression ref && ref.isLocal()) {
                            return ref.handle();
                        }
                        throw new IllegalArgumentException(
                                "Function parameters must be names, got: " + arg.toExpressionString());
                    })
                    .toList();
            owner.addComponent(fn.function(), ExpressionComponent.function(params, right));
            return fn.function() + "(" + String.join(", ", params) + ")";
        }

        // Local reference (NameToken → unresolved handle on the item)
        if (left instanceof ReferenceExpression ref && ref.isLocal()) {
            return assignToHandle(ref.handle(), right, owner, context);
        }

        // Sememe reference (RefToken → resolved dictionary match)
        if (left instanceof SememeExpression sem && sem.token() != null) {
            return assignToHandle(sem.token(), right, owner, context);
        }

        throw new IllegalArgumentException(
                "Cannot assign to: " + left.toExpressionString());
    }

    /**
     * Evaluate copula "is": {@code author is Tolkien} or {@code Tolkien is author}.
     *
     * <p>Same as assignment but supports inverted form. If the LHS isn't
     * assignable (not a local ref or sememe), tries swapping the operands.
     */
    private Object evaluateIs(Expression left, Expression right, EvaluationContext context) {
        // Try normal direction: left is property, right is value
        if (isAssignableExpression(left)) {
            return evaluateAssign(left, right, context);
        }

        // Try inverted: right is property, left is value
        if (isAssignableExpression(right)) {
            return evaluateAssign(right, left, context);
        }

        throw new IllegalArgumentException(
                "Cannot determine property in: " + left.toExpressionString()
                        + " is " + right.toExpressionString());
    }

    /**
     * Check if an expression can serve as the LHS of assignment.
     */
    private static boolean isAssignableExpression(Expression expr) {
        if (expr instanceof ReferenceExpression ref && ref.isLocal()) return true;
        if (expr instanceof SememeExpression sem && sem.token() != null) return true;
        if (expr instanceof FunctionExpression) return true;
        return false;
    }

    /**
     * Assign an expression to a named handle on the owner item.
     */
    private Object assignToHandle(String handle, Expression right, Item owner, EvaluationContext context) {
        // Circular reference detection: x = x + 1 is undefined
        if (right.referencesLocal(handle)) {
            throw new IllegalArgumentException(
                    "Circular reference: " + handle + " cannot depend on itself");
        }

        // Store the expression formula (reactive — re-evaluated on access)
        owner.addComponent(handle, ExpressionComponent.of(right));

        // Evaluate to show current value
        try {
            return right.evaluate(context);
        } catch (Exception e) {
            return handle + " = " + right.toExpressionString();
        }
    }

    private Object applyBinary(Object left, Object right) {
        return switch (canonicalKey()) {
            // Arithmetic
            case "cg.op:add" -> {
                if (left instanceof Quantity lq && right instanceof Quantity rq)
                    yield quantityAdd(lq, rq);
                if (left instanceof Number l && right instanceof Number r)
                    yield toNumber(l) + toNumber(r);
                // String concatenation only when at least one side is a string
                if (left instanceof String || right instanceof String)
                    yield String.valueOf(left) + String.valueOf(right);
                // Null operand — undefined reference
                if (left == null || right == null)
                    throw new IllegalArgumentException(
                            "Cannot add: operand is undefined (null)");
                // Fallback: try numeric coercion
                yield toNumber(left) + toNumber(right);
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
        return Seeds.BY_ID.get(iid);
    }

    /**
     * Find an infix operator by symbol text.
     */
    public static Operator fromSymbol(String text) {
        if (text == null) return null;
        String trimmed = text.trim();
        Operator op = Seeds.INFIX_BY_SYMBOL.get(trimmed);
        if (op != null) return op;
        return Seeds.PREFIX_BY_SYMBOL.get(trimmed);
    }

    /**
     * Find an infix operator by symbol.
     */
    public static Operator infixFromSymbol(String text) {
        if (text == null) return null;
        return Seeds.INFIX_BY_SYMBOL.get(text.trim());
    }

    /**
     * Find a prefix operator by symbol.
     */
    public static Operator prefixFromSymbol(String text) {
        if (text == null) return null;
        return Seeds.PREFIX_BY_SYMBOL.get(text.trim());
    }

    /**
     * Get all seed operators.
     */
    public static List<Operator> seeds() {
        return Seeds.ALL;
    }

    // ==================================================================================
    // STATIC BUILDERS
    // ==================================================================================


    @Override
    public String toString() {
        return symbol() + " (" + name() + ")";
    }
}
