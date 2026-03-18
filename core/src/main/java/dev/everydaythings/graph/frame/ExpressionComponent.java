package dev.everydaythings.graph.frame;

import dev.everydaythings.graph.item.Factory;
import dev.everydaythings.graph.item.Implements;
import dev.everydaythings.graph.item.ItemSeed;

import dev.everydaythings.graph.Canonical;
import dev.everydaythings.graph.Canonical.Canon;
import dev.everydaythings.graph.item.DisplayInfo;
import dev.everydaythings.graph.item.Item;
import dev.everydaythings.graph.frame.expression.EvaluationContext;
import dev.everydaythings.graph.frame.expression.Expression;
import dev.everydaythings.graph.frame.expression.LiteralExpression;
import dev.everydaythings.graph.item.id.ItemID;
import dev.everydaythings.graph.language.GrammaticalFeature;
import dev.everydaythings.graph.language.Language;
import dev.everydaythings.graph.language.PartOfSpeech;
import dev.everydaythings.graph.language.Sememe;
import dev.everydaythings.graph.language.SememeGloss;
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
@Implements(ExpressionComponent.KEY)
@ItemSeed(key = ExpressionComponent.KEY)
public class ExpressionComponent implements Canonical {

    public static final String KEY = "cg.sememe:expression";

    @ItemSeed.Frame(key = {SememeGloss.KEY, Language.ENGLISH_KEY})
    static final String seedGloss = "a computed expression component";

    @ItemSeed.Word(lang = Language.ENGLISH_KEY, pos = PartOfSpeech.Noun.KEY,
                   features = {GrammaticalFeature.Lemma.KEY})
    static final String seedNoun = "expression";

    public static class TypeSeed {
        public static final String KEY = "cg.sememe:expression";
        @Item.Seed public static final Sememe SEED = new Sememe(KEY)
                .gloss("en", "a computed expression component")
                .word(PartOfSpeech.NOUN, GrammaticalFeature.Lemma.SEED, "en", "expression");
    }

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

    /**
     * Parameter names for function definitions.
     *
     * <p>When non-null and non-empty, this ExpressionComponent represents a callable
     * function rather than a simple value. The expression field is the function body,
     * and params lists the parameter names that callers must provide.
     *
     * <p>Example: {@code f(x, y) = x + y} stores params=["x","y"], expression=add(ref("x"),ref("y"))
     */
    @Getter
    @Canon(order = 2)
    private final List<String> params;

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

    private ExpressionComponent(Expression expression, ItemID resultType, List<String> params) {
        this.expression = expression;
        this.resultType = resultType;
        this.params = params != null && !params.isEmpty() ? List.copyOf(params) : null;
    }

    /**
     * Required no-arg constructor for Canonical decoding.
     */
    @SuppressWarnings("unused")
    private ExpressionComponent() {
        this.expression = null;
        this.resultType = null;
        this.params = null;
    }

    // ==================================================================================
    // Factory Methods
    // ==================================================================================

    /**
     * Create an expression component from an expression.
     */
    public static ExpressionComponent of(Expression expression) {
        return new ExpressionComponent(expression, expression.resultType(), null);
    }

    /**
     * Create an expression component from an expression with explicit type.
     */
    public static ExpressionComponent of(Expression expression, ItemID resultType) {
        return new ExpressionComponent(expression, resultType, null);
    }

    /**
     * Create a literal expression component.
     */
    public static ExpressionComponent literal(Object value) {
        return new ExpressionComponent(LiteralExpression.of(value), null, null);
    }

    /**
     * Create a literal integer expression component.
     */
    public static ExpressionComponent integer(long value) {
        return new ExpressionComponent(LiteralExpression.integer(value), null, null);
    }

    /**
     * Create a literal string expression component.
     */
    public static ExpressionComponent string(String value) {
        return new ExpressionComponent(LiteralExpression.string(value), null, null);
    }

    /**
     * Create a literal boolean expression component.
     */
    public static ExpressionComponent bool(boolean value) {
        return new ExpressionComponent(LiteralExpression.bool(value), null, null);
    }

    /**
     * Wrap an existing Value (Quantity, Endpoint, etc.) as an expression component.
     */
    public static ExpressionComponent value(Value value) {
        return new ExpressionComponent(LiteralExpression.of(value), null, null);
    }

    /**
     * Create a function expression component (callable with parameters).
     *
     * <p>Example: {@code f(x, y) = x + y} → {@code function(List.of("x","y"), add(ref("x"),ref("y")))}
     */
    public static ExpressionComponent function(List<String> params, Expression body) {
        return new ExpressionComponent(body, null, params);
    }

    /**
     * Create a pattern query expression.
     */
    public static ExpressionComponent pattern(ItemID subject, ItemID predicate, ItemID object) {
        // Map S→P→O to frame query: theme=subject, predicate=predicate, binding(GOAL→object)
        var builder = dev.everydaythings.graph.frame.expression.FrameQuery.builder()
                .predicate(predicate).theme(subject);
        if (object != null) builder.binding(
                ItemID.fromString("cg.role:goal"),
                dev.everydaythings.graph.frame.BindingTarget.iid(object));
        return new ExpressionComponent(builder.build(), null, null);
    }

    /**
     * Create a "subjects" query: who has [predicate]?
     */
    public static ExpressionComponent subjects(ItemID predicate) {
        return new ExpressionComponent(
                dev.everydaythings.graph.frame.expression.FrameQuery.withPredicate(predicate),
                null, null);
    }

    /**
     * Create an "objects" query: what is the [predicate] of anything?
     */
    public static ExpressionComponent objects(ItemID predicate) {
        return new ExpressionComponent(
                dev.everydaythings.graph.frame.expression.FrameQuery.withPredicate(predicate),
                null, null);
    }

    /**
     * Create a frame query: predicate + theme, target is the variable.
     */
    public static ExpressionComponent frameQuery(ItemID predicate, ItemID theme) {
        return new ExpressionComponent(
                dev.everydaythings.graph.frame.expression.FrameQuery.of(predicate, theme),
                null, null);
    }

    /**
     * Create a frame query: all frames about a theme.
     */
    public static ExpressionComponent framesAbout(ItemID theme) {
        return new ExpressionComponent(
                dev.everydaythings.graph.frame.expression.FrameQuery.about(theme),
                null, null);
    }

    /**
     * Create a frame query: all frames with a given predicate.
     */
    public static ExpressionComponent framesWithPredicate(ItemID predicate) {
        return new ExpressionComponent(
                dev.everydaythings.graph.frame.expression.FrameQuery.withPredicate(predicate),
                null, null);
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
     * Check if this is a callable function (has parameter names).
     */
    public boolean isFunction() {
        return params != null && !params.isEmpty();
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
        Implements impl = getClass().getAnnotation(Implements.class);
        int color = impl != null ? 0x78788C : 0xB48C64;
        // Get glyph directly from annotation - "❓" if missing
        String glyph = (impl != null && !"D83DDCE6".isEmpty())
                ? "D83DDCE6"
                : "❓";
        return DisplayInfo.builder()
                .name(name)
                .typeName("Expression")
                .color(dev.everydaythings.graph.value.Color.fromPacked(color))
                .iconText(glyph)
                .build();
    }

    public String displayToken() {
        return displayInfo().displayName();
    }

    public String displaySubtitle() {
        if (expression == null) return "null";
        String formula = expression.toExpressionString();
        if (cacheValid && cachedResult != null) {
            return formula + " → " + cachedResult;
        }
        return formula;
    }

    public boolean isExpandable() {
        // Expandable if the result is a list (e.g., pattern query results)
        if (!cacheValid) return true;
        return cachedResult instanceof List<?>;
    }

    public String colorCategory() {
        return "expression";
    }

    // ==================================================================================
    // Canonical Support
    // ==================================================================================

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
