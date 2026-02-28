package dev.everydaythings.graph.value;

import dev.everydaythings.graph.item.Item;
import dev.everydaythings.graph.item.Manifest;
import dev.everydaythings.graph.item.component.Type;
import dev.everydaythings.graph.item.component.expression.EvaluationContext;
import dev.everydaythings.graph.item.component.expression.Expression;
import dev.everydaythings.graph.item.id.ItemID;
import dev.everydaythings.graph.runtime.Librarian;
import lombok.Getter;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * An Operator as a first-class Item.
 *
 * <p>This is a self-describing type. The class IS the definition.
 *
 * <p>Operators are Items that can be discovered, related, and reasoned about.
 * Each operator has:
 * <ul>
 *   <li>canonicalKey: stable identifier (e.g., "cg.op:and")</li>
 *   <li>symbol: display symbol (e.g., "&&")</li>
 *   <li>name: human-readable name (e.g., "and")</li>
 *   <li>arity: number of operands (2 for binary)</li>
 *   <li>precedence: binding strength (higher = tighter)</li>
 * </ul>
 *
 * <p>The evaluation logic lives in this class, handling short-circuit
 * evaluation for logical operators.
 *
 * <p>Usage:
 * <pre>{@code
 * // Reference an operator
 * Operator and = Operator.AND;
 * ItemID andId = and.iid();
 *
 * // Evaluate with short-circuit
 * Object result = Operator.AND.evaluate(leftExpr, rightExpr, ctx);
 * }</pre>
 */
@Type(value = Operator.KEY, glyph = "➕")
public class Operator extends Item {

    // ==================================================================================
    // TYPE DEFINITION
    // ==================================================================================

    public static final String KEY = "cg:type/operator";

    public enum Associativity {
        LEFT,
        RIGHT,
        NONE
    }

    public enum Fixity {
        PREFIX,
        INFIX,
        POSTFIX
    }


    // ==================================================================================
    // SEED INSTANCES - Logical
    // ==================================================================================

    /** Logical AND operator - short-circuit evaluation */
    @Seed
    public static final Operator AND = new Operator(
            "cg.op:and", "&&", "and",
            2, 1, Associativity.LEFT, Fixity.INFIX  // arity, precedence, assoc, fixity
    );

    /** Logical OR operator - short-circuit evaluation */
    @Seed
    public static final Operator OR = new Operator(
            "cg.op:or", "||", "or",
            2, 0, Associativity.LEFT, Fixity.INFIX  // arity, precedence, assoc, fixity
    );

    private static final List<Operator> SEED_OPERATORS = List.of(AND, OR);
    private static final Map<ItemID, Operator> SEED_BY_ID = buildSeedById();
    private static final Map<String, Operator> SEED_BY_SYMBOL = buildSeedBySymbol();

    // ==================================================================================
    // INSTANCE FIELDS
    // ==================================================================================

    /** The canonical key (e.g., "cg.op:and") */
    @Getter
    @ContentField(handleKey = "key")
    private String canonicalKey;

    /** Symbol used in expressions (e.g., "&&", "||") */
    @Getter
    @ContentField
    private String symbol;

    /** Human-readable name (e.g., "and", "or") */
    @Getter
    @ContentField
    private String name;

    /** Number of operands (2 for binary operators) */
    @Getter
    @ContentField
    private int arity;

    /** Precedence level (higher binds tighter) */
    @Getter
    @ContentField
    private int precedence;

    /** Associativity for parse-time grouping. */
    @Getter
    @ContentField
    private Associativity associativity;

    /** Fixity for parse-time placement (prefix/infix/postfix). */
    @Getter
    @ContentField
    private Fixity fixity;

    // ==================================================================================
    // CONSTRUCTORS
    // ==================================================================================

    /**
     * Create a seed operator (no librarian, deterministic IID from key).
     */
    public Operator(String canonicalKey, String symbol, String name,
                    int arity, int precedence,
                    Associativity associativity, Fixity fixity) {
        super(ItemID.fromString(canonicalKey));
        this.canonicalKey = canonicalKey;
        this.symbol = symbol;
        this.name = name;
        this.arity = arity;
        this.precedence = precedence;
        this.associativity = associativity != null ? associativity : Associativity.LEFT;
        this.fixity = fixity != null ? fixity : Fixity.INFIX;
    }

    /**
     * Create an operator with a librarian (for runtime creation).
     */
    public Operator(Librarian librarian,
                    String canonicalKey, String symbol, String name,
                    int arity, int precedence,
                    Associativity associativity, Fixity fixity) {
        super(librarian, ItemID.fromString(canonicalKey));
        this.canonicalKey = canonicalKey;
        this.symbol = symbol;
        this.name = name;
        this.arity = arity;
        this.precedence = precedence;
        this.associativity = associativity != null ? associativity : Associativity.LEFT;
        this.fixity = fixity != null ? fixity : Fixity.INFIX;
    }

    /**
     * Type seed constructor - creates a minimal Operator for use as type seed.
     *
     * <p>Used by SeedStore to create the "cg:type/operator" type item.
     * Derives canonicalKey from the @Type annotation.
     */
    @SuppressWarnings("unused")  // Used via reflection by SeedStore
    protected Operator(ItemID typeId) {
        super(typeId);
        // For type seeds, derive canonicalKey from the type annotation
        Type typeAnnotation = getClass().getAnnotation(Type.class);
        if (typeAnnotation != null) {
            this.canonicalKey = typeAnnotation.value();
            this.name = extractNameFromKey(this.canonicalKey);
        }
    }

    /**
     * Extract a readable name from a type key (e.g., "cg:type/operator" → "operator").
     */
    private static String extractNameFromKey(String key) {
        if (key == null) return null;
        int lastSlash = key.lastIndexOf('/');
        if (lastSlash >= 0 && lastSlash < key.length() - 1) {
            return key.substring(lastSlash + 1);
        }
        int lastColon = key.lastIndexOf(':');
        if (lastColon >= 0 && lastColon < key.length() - 1) {
            return key.substring(lastColon + 1);
        }
        return key;
    }

    /**
     * Hydration constructor - reconstructs an Operator from a stored manifest.
     *
     * <p>Fields are bound via reflection in the base class hydrate() method.
     */
    @SuppressWarnings("unused")  // Used via reflection for hydration
    private Operator(Librarian librarian, Manifest manifest) {
        super(librarian, manifest);
        // Fields are set by bindFieldsFromTable() via reflection during super() call
        // Do NOT assign values here - it would overwrite what hydration set!
    }

    // ==================================================================================
    // EVALUATION
    // ==================================================================================

    /**
     * Evaluate this operator with the given operand expressions.
     *
     * <p>Handles short-circuit evaluation for AND/OR:
     * <ul>
     *   <li>AND: if left is false, don't evaluate right</li>
     *   <li>OR: if left is true, don't evaluate right</li>
     * </ul>
     *
     * @param left    Left operand expression
     * @param right   Right operand expression
     * @param context Evaluation context
     * @return The result of applying the operator
     * @throws UnsupportedOperationException if this operator has no evaluator
     */
    public Object evaluate(Expression left, Expression right, EvaluationContext context) {
        ItemID id = iid();

        if (id.equals(AND.iid())) {
            Object leftVal = left.evaluate(context);
            if (!toBoolean(leftVal)) return false;  // Short-circuit
            return toBoolean(right.evaluate(context));
        }

        if (id.equals(OR.iid())) {
            Object leftVal = left.evaluate(context);
            if (toBoolean(leftVal)) return true;  // Short-circuit
            return toBoolean(right.evaluate(context));
        }

        throw new UnsupportedOperationException("No evaluator for operator: " + id);
    }

    /**
     * Check if this operator requires short-circuit evaluation.
     *
     * <p>Short-circuit operators (AND, OR) must not evaluate the right
     * operand before checking the left operand's result.
     *
     * @return true if this operator short-circuits
     */
    public boolean isShortCircuit() {
        ItemID id = iid();
        return id.equals(AND.iid()) || id.equals(OR.iid());
    }

    // ==================================================================================
    // TYPE COERCION
    // ==================================================================================

    /**
     * Convert a value to boolean for logical operations.
     *
     * <p>Truthiness rules:
     * <ul>
     *   <li>null → false</li>
     *   <li>Boolean → its value</li>
     *   <li>Number → true if non-zero</li>
     *   <li>String → true if non-empty</li>
     *   <li>List → true if non-empty</li>
     *   <li>Everything else → true</li>
     * </ul>
     *
     * @param value The value to convert
     * @return The boolean value
     */
    public static boolean toBoolean(Object value) {
        if (value == null) return false;
        if (value instanceof Boolean b) return b;
        if (value instanceof Number n) return n.doubleValue() != 0;
        if (value instanceof String s) return !s.isEmpty();
        if (value instanceof List<?> l) return !l.isEmpty();
        return true;
    }

    // ==================================================================================
    // LOOKUP
    // ==================================================================================

    /**
     * Look up an Operator by its IID.
     *
     * <p>Checks seed instances first, then falls back to librarian lookup.
     *
     * @param iid     The operator IID
     * @param context Evaluation context (for librarian access)
     * @return The Operator, or null if not found
     */
    public static Operator lookup(ItemID iid, EvaluationContext context) {
        Operator seed = lookupKnown(iid);
        if (seed != null) return seed;

        // Fall back to librarian lookup
        if (context != null && context.librarian() != null) {
            return context.librarian().get(iid, Operator.class).orElse(null);
        }

        return null;
    }

    /**
     * Look up a known seed operator by IID without evaluation context.
     */
    public static Operator lookupKnown(ItemID iid) {
        if (iid == null) return null;
        return SEED_BY_ID.get(iid);
    }

    /**
     * Parse an operator from symbol text.
     *
     * @param text The symbol text (e.g., "&&", "AND", "||", "OR")
     * @return The Operator, or null if not recognized
     */
    public static Operator fromSymbol(String text) {
        if (text == null) return null;
        String trimmed = text.trim();
        return SEED_BY_SYMBOL.get(trimmed);
    }

    private static Map<ItemID, Operator> buildSeedById() {
        Map<ItemID, Operator> out = new LinkedHashMap<>();
        for (Operator op : SEED_OPERATORS) {
            out.put(op.iid(), op);
        }
        return Map.copyOf(out);
    }

    private static Map<String, Operator> buildSeedBySymbol() {
        Map<String, Operator> out = new LinkedHashMap<>();
        for (Operator op : SEED_OPERATORS) {
            if (op.symbol != null && !op.symbol.isBlank()) {
                out.put(op.symbol, op);
            }
        }
        // Backward-compatible aliases (still resolved as data-backed operators).
        out.put("&", AND);
        out.put("|", OR);
        return Map.copyOf(out);
    }

    // ==================================================================================
    // ITEM OVERRIDES
    // ==================================================================================

    @Override
    public String displayToken() {
        return name != null ? name : (symbol != null ? symbol : getClass().getSimpleName());
    }

    @Override
    public Stream<TokenEntry> extractTokens() {
        List<TokenEntry> tokens = new ArrayList<>();

        // Index only the symbol (e.g., "&&", "||").
        // English words ("and", "or") are owned by ConjunctionSememe seeds
        // and resolved through the completion system, not as operators.
        if (symbol != null && !symbol.isBlank()) {
            tokens.add(new TokenEntry(symbol, 1.0f));
        }

        return tokens.stream();
    }

    @Override
    public String toString() {
        return symbol + " (" + name + ")";
    }
}
