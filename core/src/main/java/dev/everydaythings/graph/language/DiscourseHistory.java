package dev.everydaythings.graph.language;

import dev.everydaythings.graph.item.Item;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Optional;

/**
 * Tracks recent discourse context for pronoun resolution.
 *
 * <p>Maintains a short history of recently mentioned, created, and navigated
 * items so that pronouns like "it", "that", and "last" can resolve to
 * concrete items.
 *
 * <p>Resolution rules:
 * <ul>
 *   <li>"it" / "that" → most recently created or mentioned item</li>
 *   <li>"this" → currently focused item (supplied externally)</li>
 *   <li>"last" → previous item before the most recent</li>
 * </ul>
 *
 * <p>This class is NOT thread-safe; it's expected to be owned by one Session.
 */
public class DiscourseHistory {

    private static final int MAX_HISTORY = 16;

    private final Deque<Item> history = new ArrayDeque<>(MAX_HISTORY);

    /**
     * Record that an item was just created, navigated to, or mentioned.
     */
    public void push(Item item) {
        if (item == null) return;
        // Remove if already in history (move to front)
        history.removeIf(i -> i.iid().equals(item.iid()));
        history.addFirst(item);
        while (history.size() > MAX_HISTORY) {
            history.removeLast();
        }
    }

    /**
     * The most recently pushed item ("it", "that").
     */
    public Optional<Item> mostRecent() {
        return Optional.ofNullable(history.peekFirst());
    }

    /**
     * The second most recent item ("last").
     */
    public Optional<Item> previous() {
        var iter = history.iterator();
        if (iter.hasNext()) iter.next(); // skip most recent
        return iter.hasNext() ? Optional.of(iter.next()) : Optional.empty();
    }

    /**
     * Resolve a pronoun sememe to its referent item.
     *
     * @param pronoun     The pronoun sememe to resolve
     * @param focusedItem The currently focused item ("this"), or null
     * @return The resolved item, or empty if unresolvable
     */
    public Optional<Item> resolve(Sememe pronoun, Item focusedItem) {
        String key = pronoun.canonicalKey();
        if (key == null) return Optional.empty();

        return switch (key) {
            case "cg.pronoun:it", "cg.pronoun:that" -> mostRecent();
            case "cg.pronoun:this" -> Optional.ofNullable(focusedItem);
            case "cg.pronoun:last" -> previous();
            default -> Optional.empty();
        };
    }

    /**
     * Number of items in history.
     */
    public int size() {
        return history.size();
    }

    /**
     * Clear all history.
     */
    public void clear() {
        history.clear();
    }
}
