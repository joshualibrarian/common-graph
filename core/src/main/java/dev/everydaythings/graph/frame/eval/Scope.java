package dev.everydaythings.graph.frame.eval;

import dev.everydaythings.graph.item.ItemOld;
import dev.everydaythings.graph.runtime.LibrarianOld;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Evaluation scope — provides context for frame evaluation.
 *
 * <p>A Scope carries the librarian (graph access), the owner item (focused context),
 * variable bindings (from let-expressions and function parameters), and a shared
 * evaluation cache for memoization.
 *
 * <p>Scopes form a chain: child scopes created by let/function bindings can
 * see parent variables. Variable lookup walks the chain from innermost to outermost.
 */
public final class Scope {

    private final LibrarianOld librarian;
    private final ItemOld owner;
    private final Scope parent;
    private final Map<String, Object> variables;
    private final Map<Object, Object> cache;

    private Scope(LibrarianOld librarian, ItemOld owner, Scope parent,
                  Map<String, Object> variables, Map<Object, Object> cache) {
        this.librarian = librarian;
        this.owner = owner;
        this.parent = parent;
        this.variables = variables;
        this.cache = cache;
    }

    /**
     * Create a root scope with a librarian and owner item.
     */
    public static Scope of(LibrarianOld librarian, ItemOld owner) {
        return new Scope(librarian, owner, null, new HashMap<>(), new HashMap<>());
    }

    /**
     * Create a root scope with just a librarian (owner = librarian).
     */
    public static Scope of(LibrarianOld librarian) {
        return new Scope(librarian, librarian, null, new HashMap<>(), new HashMap<>());
    }

    /** The librarian for graph access. */
    public LibrarianOld librarian() { return librarian; }

    /** The focused item (owner of the evaluation). */
    public ItemOld owner() { return owner; }

    /**
     * Look up a variable by name in the scope chain.
     *
     * @param name the variable name
     * @return the value, or empty if not found
     */
    public Optional<Object> lookup(String name) {
        if (variables.containsKey(name)) {
            return Optional.ofNullable(variables.get(name));
        }
        if (parent != null) {
            return parent.lookup(name);
        }
        return Optional.empty();
    }

    /**
     * Create a child scope with a new variable binding.
     */
    public Scope withVariable(String name, Object value) {
        Map<String, Object> childVars = new HashMap<>();
        childVars.put(name, value);
        return new Scope(librarian, owner, this, childVars, cache);
    }

    /**
     * Create a child scope with multiple variable bindings.
     */
    public Scope withVariables(Map<String, Object> bindings) {
        return new Scope(librarian, owner, this, new HashMap<>(bindings), cache);
    }

    /**
     * Create a child scope with a different owner item.
     */
    public Scope withOwner(ItemOld newOwner) {
        return new Scope(librarian, newOwner, this, new HashMap<>(), cache);
    }

    /**
     * Get a cached evaluation result.
     */
    public Optional<Object> getCached(Object key) {
        return Optional.ofNullable(cache.get(key));
    }

    /**
     * Cache an evaluation result.
     */
    public void cache(Object key, Object value) {
        cache.put(key, value);
    }
}
