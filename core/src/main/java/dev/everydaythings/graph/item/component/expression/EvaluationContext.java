package dev.everydaythings.graph.item.component.expression;

import dev.everydaythings.graph.item.Item;
import dev.everydaythings.graph.item.id.ItemID;
import dev.everydaythings.graph.library.Library;
import dev.everydaythings.graph.runtime.Librarian;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Context for expression evaluation.
 *
 * <p>Provides access to:
 * <ul>
 *   <li>The graph (via Librarian) for resolving references</li>
 *   <li>The owning Item for local references and component storage</li>
 *   <li>Local bindings from let-expressions and function parameters</li>
 *   <li>Evaluation cache to avoid re-computing the same expression</li>
 * </ul>
 *
 * <p>Persistent state (variables, functions) lives on the owner Item as
 * ExpressionComponents — not in this context. This context is ephemeral
 * and created fresh for each evaluation.
 *
 * <p>Contexts can be nested for let-bindings, creating a scope chain.
 */
public class EvaluationContext {

    private final Librarian librarian;
    private final Item owner;
    private final EvaluationContext parent;
    private final Map<String, Object> bindings;
    private final Map<Expression, Object> cache;

    // ==================================================================================
    // Constructors
    // ==================================================================================

    /**
     * Create a root context with a librarian and owner.
     */
    public EvaluationContext(Librarian librarian, Item owner) {
        this.librarian = librarian;
        this.owner = owner;
        this.parent = null;
        this.bindings = new HashMap<>();
        this.cache = new HashMap<>();
    }

    /**
     * Create a child context with additional bindings.
     */
    private EvaluationContext(EvaluationContext parent, Map<String, Object> bindings) {
        this.librarian = parent.librarian;
        this.owner = parent.owner;
        this.parent = parent;
        this.bindings = bindings;
        this.cache = parent.cache;
    }

    // ==================================================================================
    // Factory Methods
    // ==================================================================================

    /**
     * Create a root context for evaluating expressions owned by a Librarian.
     */
    public static EvaluationContext forLibrarian(Librarian librarian) {
        return new EvaluationContext(librarian, librarian);
    }

    /**
     * Create a root context for evaluating expressions owned by an Item.
     */
    public static EvaluationContext forItem(Librarian librarian, Item item) {
        return new EvaluationContext(librarian, item);
    }

    // ==================================================================================
    // Accessors
    // ==================================================================================

    /**
     * Get the librarian for graph access.
     */
    public Librarian librarian() {
        return librarian;
    }

    /**
     * Get the owning item (for local references and component storage).
     */
    public Item owner() {
        return owner;
    }

    /**
     * Get the library for relation queries.
     */
    public Optional<Library> library() {
        if (librarian == null) return Optional.empty();
        return Optional.of(librarian.library());
    }

    // ==================================================================================
    // Bindings (for let-expressions and function parameters)
    // ==================================================================================

    /**
     * Look up a binding by name.
     *
     * <p>Searches the let-binding chain only. Persistent values
     * (variables, functions) are stored as components on the owner Item
     * and resolved by {@link ReferenceExpression} and {@link FunctionExpression}.
     */
    public Optional<Object> lookup(String name) {
        if (bindings.containsKey(name)) {
            return Optional.ofNullable(bindings.get(name));
        }
        if (parent != null) {
            return parent.lookup(name);
        }
        return Optional.empty();
    }

    /**
     * Create a child context with a new binding.
     */
    public EvaluationContext withBinding(String name, Object value) {
        Map<String, Object> newBindings = new HashMap<>();
        newBindings.put(name, value);
        return new EvaluationContext(this, newBindings);
    }

    /**
     * Create a child context with multiple new bindings.
     */
    public EvaluationContext withBindings(Map<String, Object> newBindings) {
        return new EvaluationContext(this, new HashMap<>(newBindings));
    }

    // ==================================================================================
    // Caching
    // TODO: Cache is keyed implicitly (assumes same context). If the same
    //  ExpressionComponent is evaluated with different contexts (different owner,
    //  different bindings), stale results could be returned. Currently safe because
    //  contexts are always fresh, but would need context-keyed caching if that changes.
    // ==================================================================================

    /**
     * Get a cached result for an expression.
     */
    public Optional<Object> getCached(Expression expr) {
        return Optional.ofNullable(cache.get(expr));
    }

    /**
     * Cache a result for an expression.
     */
    public void cache(Expression expr, Object result) {
        cache.put(expr, result);
    }

    /**
     * Evaluate an expression with caching.
     */
    public Object evaluateWithCache(Expression expr) {
        return getCached(expr).orElseGet(() -> {
            Object result = expr.evaluate(this);
            cache(expr, result);
            return result;
        });
    }

    // ==================================================================================
    // Item Resolution
    // ==================================================================================

    /**
     * Resolve an ItemID to an Item.
     */
    public Optional<Item> resolve(ItemID iid) {
        if (librarian == null) return Optional.empty();
        return librarian.get(iid, Item.class);
    }
}
