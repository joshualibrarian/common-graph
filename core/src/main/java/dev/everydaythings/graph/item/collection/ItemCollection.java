package dev.everydaythings.graph.item.collection;

import dev.everydaythings.graph.item.component.FrameTable;

import java.util.AbstractSet;
import java.util.Iterator;
import java.util.Optional;

/**
 * Base interface for Item collections that auto-generate CRUD actions.
 *
 * <p>ItemCollection extends AbstractSet to provide standard collection semantics
 * while adding keyed lookup. When a field of this type is annotated with
 * {@link dev.everydaythings.graph.item.Item.Collection}, the following actions
 * are auto-generated:
 * <ul>
 *   <li>{@code <name>} - list all entries</li>
 *   <li>{@code <name> add <args>} - add an entry</li>
 *   <li>{@code <name> get <key>} - get entry by key</li>
 *   <li>{@code <name> remove <key>} - remove entry by key</li>
 * </ul>
 *
 * <p>Implementations:
 * <ul>
 *   <li>{@link FrameTable} - components (with embedded mount info)</li>
 * </ul>
 *
 * @param <K> Key type for lookup
 * @param <E> Entry type
 */
public abstract class ItemCollection<K, E> extends AbstractSet<E> {

    /**
     * Get entry by key.
     */
    public abstract Optional<E> get(K key);

    /**
     * Remove entry by key.
     *
     * @return true if removed
     */
    public abstract boolean removeByKey(K key);

    /**
     * Extract key from entry.
     */
    public abstract K keyOf(E entry);

    /**
     * Human-readable name for this collection (for action generation).
     */
    public abstract String collectionName();

    /**
     * Entry type class (for reflection).
     */
    public abstract Class<E> entryType();

    // AbstractSet requires these:
    @Override
    public abstract Iterator<E> iterator();

    @Override
    public abstract int size();

    // Delegate add to type-specific method
    @Override
    public boolean add(E entry) {
        return addEntry(entry);
    }

    /**
     * Add an entry. Returns true if added (false if duplicate key replaced).
     */
    protected abstract boolean addEntry(E entry);

    // Remove by entry delegates to removeByKey
    @Override
    public boolean remove(Object o) {
        if (entryType().isInstance(o)) {
            @SuppressWarnings("unchecked")
            E entry = (E) o;
            return removeByKey(keyOf(entry));
        }
        return false;
    }

    @Override
    public boolean contains(Object o) {
        if (entryType().isInstance(o)) {
            @SuppressWarnings("unchecked")
            E entry = (E) o;
            return get(keyOf(entry)).isPresent();
        }
        return false;
    }
}
