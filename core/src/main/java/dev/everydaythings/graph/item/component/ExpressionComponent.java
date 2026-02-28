package dev.everydaythings.graph.item.component;

import dev.everydaythings.graph.Canonical;
import dev.everydaythings.graph.Canonical.Canon;
import dev.everydaythings.graph.item.DisplayInfo;
import dev.everydaythings.graph.item.Item;
import dev.everydaythings.graph.item.component.expression.EvaluationContext;
import dev.everydaythings.graph.item.component.expression.Expression;
import dev.everydaythings.graph.item.component.expression.LiteralExpression;
import dev.everydaythings.graph.item.id.ItemID;
import dev.everydaythings.graph.runtime.Librarian;
import dev.everydaythings.graph.value.Value;
import lombok.Getter;
import lombok.extern.log4j.Log4j2;

import java.util.List;
import java.util.stream.Stream;

/**
 * An expression component - the fundamental content unit for computed values.
 *
 * <p>An ExpressionComponent consists of:
 * <ul>
 *   <li><b>Expression</b> (snapshot) - the recipe for computing the value</li>
 *   <li><b>Result</b> (stream) - the computed/cached result</li>
 * </ul>
 *
 * <p>This is the unifying abstraction: "Expressions all the way down."
 * Everything computable in the graph is an ExpressionComponent:
 * <ul>
 *   <li>Literals: {@code ExpressionComponent.literal(42)}</li>
 *   <li>Queries: {@code ExpressionComponent.pattern(WHAT, IMPLEMENTED_BY, ANY)}</li>
 *   <li>Computations: {@code ExpressionComponent.of(add(ref("x"), ref("y")))}</li>
 *   <li>References: {@code ExpressionComponent.of(ref(otherItem, "total"))}</li>
 * </ul>
 *
 * <p>The result of an expression can be any type, including:
 * <ul>
 *   <li>Primitives (numbers, strings, booleans)</li>
 *   <li>{@link Value} instances (Quantity, Endpoint, etc.)</li>
 *   <li>Lists of ItemIDs (from pattern queries)</li>
 *   <li>Items</li>
 * </ul>
 *
 * <p>ExpressionComponents can reference other ExpressionComponents across items,
 * creating a computation graph where changes propagate.
 *
 * @see Expression
 * @see Value
 */
@Log4j2
@Type(value = "cg:type/expression", glyph = "🧮")
public class ExpressionComponent implements Component, Canonical {

    // ==================================================================================
    // Canonical Fields
    // ==================================================================================

    /**
     * The expression that defines this component (the "snapshot").
     *
     * <p>This is what gets stored and is the canonical representation.
     * The expression is evaluated lazily to produce the result.
     */
    @Getter
    @Canon(order = 0)
    private final Expression expression;

    /**
     * Optional type hint for the result.
     *
     * <p>Can be null if type is inferred or dynamic.
     */
    @Getter
    @Canon(order = 1)
    private final ItemID resultType;

    // ==================================================================================
    // Transient State
    // ==================================================================================

    /**
     * Cached evaluation result (the "stream").
     */
    private transient Object cachedResult;

    /**
     * Whether the cached result is valid.
     */
    private transient boolean cacheValid = false;

    // ==================================================================================
    // Constructors
    // ==================================================================================

    private ExpressionComponent(Expression expression, ItemID resultType) {
        this.expression = expression;
        this.resultType = resultType;
    }

    /**
     * Required no-arg constructor for Canonical decoding.
     */
    @SuppressWarnings("unused")
    private ExpressionComponent() {
        this.expression = null;
        this.resultType = null;
    }

    // ==================================================================================
    // Factory Methods
    // ==================================================================================

    /**
     * Create an expression component from an expression.
     */
    public static ExpressionComponent of(Expression expression) {
        return new ExpressionComponent(expression, expression.resultType());
    }

    /**
     * Create an expression component from an expression with explicit type.
     */
    public static ExpressionComponent of(Expression expression, ItemID resultType) {
        return new ExpressionComponent(expression, resultType);
    }

    /**
     * Create a literal expression component.
     */
    public static ExpressionComponent literal(Object value) {
        return new ExpressionComponent(LiteralExpression.of(value), null);
    }

    /**
     * Create a literal integer expression component.
     */
    public static ExpressionComponent integer(long value) {
        return new ExpressionComponent(LiteralExpression.integer(value), null);
    }

    /**
     * Create a literal string expression component.
     */
    public static ExpressionComponent string(String value) {
        return new ExpressionComponent(LiteralExpression.string(value), null);
    }

    /**
     * Create a literal boolean expression component.
     */
    public static ExpressionComponent bool(boolean value) {
        return new ExpressionComponent(LiteralExpression.bool(value), null);
    }

    /**
     * Wrap an existing Value (Quantity, Endpoint, etc.) as an expression component.
     */
    public static ExpressionComponent value(Value value) {
        return new ExpressionComponent(LiteralExpression.of(value), null);
    }

    /**
     * Create a pattern query expression (replaces QueryComponent).
     */
    public static ExpressionComponent pattern(ItemID subject, ItemID predicate, ItemID object) {
        return new ExpressionComponent(
                dev.everydaythings.graph.item.component.expression.PatternExpression.pattern(
                        subject, predicate, object),
                null);
    }

    /**
     * Create a "subjects" query: ? → P → *
     */
    public static ExpressionComponent subjects(ItemID predicate) {
        return new ExpressionComponent(
                dev.everydaythings.graph.item.component.expression.PatternExpression.subjects(predicate),
                null);
    }

    /**
     * Create an "objects" query: * → P → ?
     */
    public static ExpressionComponent objects(ItemID predicate) {
        return new ExpressionComponent(
                dev.everydaythings.graph.item.component.expression.PatternExpression.objects(predicate),
                null);
    }

    /**
     * Create a default empty expression.
     */
    @Factory(label = "Empty", glyph = "🧮", primary = true,
            doc = "Empty expression with null literal")
    public static ExpressionComponent createDefault() {
        return literal(null);
    }

    // ==================================================================================
    // Evaluation
    // ==================================================================================

    /**
     * Evaluate this expression.
     *
     * @param context The evaluation context
     * @return The computed result
     */
    public Object evaluate(EvaluationContext context) {
        if (expression == null) {
            return null;
        }

        if (!cacheValid) {
            logger.debug("Evaluating expression: {}", expression.toExpressionString());
            cachedResult = expression.evaluate(context);
            cacheValid = true;
            logger.debug("Result: {}", cachedResult);
        }

        return cachedResult;
    }

    /**
     * Evaluate this expression using the librarian's context.
     *
     * @param librarian The librarian for graph access
     * @return The computed result
     */
    public Object evaluate(Librarian librarian) {
        return evaluate(EvaluationContext.forLibrarian(librarian));
    }

    /**
     * Evaluate this expression using the owning item's context.
     *
     * @param librarian The librarian for graph access
     * @param owner The owning item for evaluation context
     * @return The computed result
     */
    public Object evaluate(Librarian librarian, Item owner) {
        EvaluationContext context = owner != null
                ? EvaluationContext.forItem(librarian, owner)
                : EvaluationContext.forLibrarian(librarian);
        return evaluate(context);
    }

    /**
     * Evaluate and return as a typed result.
     */
    @SuppressWarnings("unchecked")
    public <T> T evaluateAs(EvaluationContext context, Class<T> type) {
        Object result = evaluate(context);
        if (result == null) return null;
        if (type.isInstance(result)) return (T) result;
        // TODO: Type coercion
        return null;
    }

    /**
     * Evaluate and return as a list of ItemIDs (for pattern queries).
     */
    @SuppressWarnings("unchecked")
    public List<ItemID> evaluateAsItemIds(Librarian librarian) {
        Object result = evaluate(librarian);
        if (result instanceof List<?> list) {
            return (List<ItemID>) list;
        }
        return List.of();
    }

    /**
     * Evaluate and return as a stream of Items (for pattern queries).
     */
    public Stream<Item> evaluateAsItems(Librarian librarian) {
        return evaluateAsItemIds(librarian).stream()
                .flatMap(id -> librarian.get(id, Item.class).stream());
    }

    /**
     * Invalidate the cache, forcing re-evaluation on next access.
     */
    public void invalidate() {
        cacheValid = false;
        cachedResult = null;
    }

    /**
     * Check if this expression has dependencies that might change.
     */
    public boolean hasDependencies() {
        return expression != null && expression.hasDependencies();
    }

    /**
     * Check if this is a pure literal (no computation needed).
     */
    public boolean isLiteral() {
        return expression instanceof LiteralExpression;
    }

    /**
     * Get the raw literal value if this is a literal.
     */
    public Object literalValue() {
        if (expression instanceof LiteralExpression lit) {
            return lit.value();
        }
        return null;
    }

    // ==================================================================================
    // Display Implementation
    // ==================================================================================

    public DisplayInfo displayInfo() {
        String name = "Expression";
        // Build DisplayInfo from annotation values
        Type typeAnnotation = getClass().getAnnotation(Type.class);
        int color = typeAnnotation != null ? typeAnnotation.color() : 0xB48C64;
        // Get glyph directly from annotation - "❓" if missing
        String glyph = (typeAnnotation != null && !typeAnnotation.glyph().isEmpty())
                ? typeAnnotation.glyph()
                : "❓";
        return DisplayInfo.builder()
                .name(name)
                .typeName("Expression")
                .color(dev.everydaythings.graph.value.Color.fromPacked(color))
                .iconText(glyph)
                .build();
    }

    @Override
    public String displayToken() {
        return displayInfo().displayName();
    }

    @Override
    public String displaySubtitle() {
        // Show the expression formula as subtitle
        if (expression == null) return "null";
        return expression.toExpressionString();
    }

    @Override
    public boolean isExpandable() {
        // Expandable if the result is a list (e.g., pattern query results)
        if (!cacheValid) return true;
        return cachedResult instanceof List<?>;
    }

    @Override
    public String colorCategory() {
        return "expression";
    }

    // ==================================================================================
    // Canonical Support
    // ==================================================================================

    /**
     * Encode this expression component to bytes.
     */
    public byte[] encode() {
        return encodeBinary(Canonical.Scope.RECORD);
    }

    /**
     * Decode an expression component from bytes.
     */
    public static ExpressionComponent decode(byte[] bytes) {
        return Canonical.decodeBinary(bytes, ExpressionComponent.class, Canonical.Scope.RECORD);
    }

    // ==================================================================================
    // Object Methods
    // ==================================================================================

    @Override
    public String toString() {
        return "ExpressionComponent{" + (expression != null ? expression.toExpressionString() : "null") + "}";
    }
}
