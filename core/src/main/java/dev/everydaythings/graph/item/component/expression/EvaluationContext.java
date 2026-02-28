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
 *   <li>The owning Item for local references</li>
 *   <li>Local bindings from let-expressions</li>
 *   <li>Evaluation cache to avoid re-computing the same expression</li>
 * </ul>
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
        this.cache = parent.cache; // Share cache with parent
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
     * Get the owning item (for local references).
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
    // Bindings (for let-expressions)
    // ==================================================================================

    /**
     * Look up a binding by name.
     *
     * <p>Searches local bindings first, then parent scopes.
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
