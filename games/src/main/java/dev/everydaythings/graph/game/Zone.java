package dev.everydaythings.graph.game;

import java.util.*;

/**
 * A named zone holding an ordered list of items with a visibility rule.
 *
 * <p>Zones model any named container in a game: hands, decks, discard piles,
 * supply stacks, racks, reserves. Items are ordered — stack and queue
 * semantics are both supported:
 * <ul>
 *   <li>{@link #addTop}/{@link #removeTop} — stack (LIFO)</li>
 *   <li>{@link #addBottom}/{@link #removeBottom} — queue (FIFO)</li>
 *   <li>{@link #addAt}/{@link #removeAt} — indexed access</li>
 * </ul>
 *
 * <p>Visibility separates game logic from display. Game logic always sees
 * everything via {@link #contents()}. Renderers and network sync use
 * {@link #contentsVisibleTo(int)} to filter per viewer.
 *
 * <p>Per-player zones use a naming convention: "hand:2" means seat 2's hand.
 * The owner is parsed from the suffix.
 *
 * @param <T> the type of items in this zone
 * @see ZoneMap
 * @see ZoneVisibility
 */
public class Zone<T> {

    private final String name;
    private final ZoneVisibility visibility;
    private final List<T> contents = new ArrayList<>();

    public Zone(String name, ZoneVisibility visibility) {
        this.name = Objects.requireNonNull(name, "name");
        this.visibility = Objects.requireNonNull(visibility, "visibility");
    }

    public String name() { return name; }

    public ZoneVisibility visibility() { return visibility; }

    public int size() { return contents.size(); }

    public boolean isEmpty() { return contents.isEmpty(); }

    // ==================================================================================
    // Visibility-Aware Queries
    // ==================================================================================

    /**
     * Get contents visible to a specific viewer.
     *
     * @param viewerSeat the seat of the player viewing (-1 for spectator)
     * @return the items this viewer can see (may be empty)
     */
    public List<T> contentsVisibleTo(int viewerSeat) {
        return switch (visibility) {
            case PUBLIC -> List.copyOf(contents);
            case HIDDEN -> List.of();
            case TOP_ONLY -> contents.isEmpty()
                    ? List.of()
                    : List.of(contents.getLast());
            case OWNER, OWNER_AND_COUNT -> ownerSeat() == viewerSeat
                    ? List.copyOf(contents)
                    : List.of();
        };
    }

    /**
     * Full contents (for game logic, not display).
     * Game logic always sees everything; visibility is a display concern.
     */
    public List<T> contents() {
        return List.copyOf(contents);
    }

    /**
     * Get the top item without removing it.
     */
    public Optional<T> peek() {
        return contents.isEmpty()
                ? Optional.empty()
                : Optional.of(contents.getLast());
    }

    /**
     * Check if this zone contains a specific item.
     */
    public boolean contains(T item) {
        return contents.contains(item);
    }

    // ==================================================================================
    // Mutations
    // ==================================================================================

    /** Add item to the top (end) of the zone. */
    public void addTop(T item) {
        Objects.requireNonNull(item, "item");
        contents.add(item);
    }

    /** Add item to the bottom (start) of the zone. */
    public void addBottom(T item) {
        Objects.requireNonNull(item, "item");
        contents.addFirst(item);
    }

    /** Add item at a specific index. */
    public void addAt(int index, T item) {
        Objects.requireNonNull(item, "item");
        contents.add(index, item);
    }

    /** Add all items to the top. */
    public void addAll(Collection<T> items) {
        Objects.requireNonNull(items, "items");
        contents.addAll(items);
    }

    /** Remove and return the top (last) item. */
    public Optional<T> removeTop() {
        return contents.isEmpty()
                ? Optional.empty()
                : Optional.of(contents.removeLast());
    }

    /** Remove and return the bottom (first) item. */
    public Optional<T> removeBottom() {
        return contents.isEmpty()
                ? Optional.empty()
                : Optional.of(contents.removeFirst());
    }

    /** Remove and return the item at a specific index. */
    public Optional<T> removeAt(int index) {
        if (index < 0 || index >= contents.size()) return Optional.empty();
        return Optional.of(contents.remove(index));
    }

    /** Remove a specific item (first occurrence). */
    public boolean remove(T item) {
        return contents.remove(item);
    }

    /** Remove all items. */
    public void clear() {
        contents.clear();
    }

    /**
     * Shuffle the zone contents deterministically.
     *
     * @param random a GameRandom for verifiable determinism
     */
    public void shuffle(GameRandom random) {
        random.shuffle(contents);
    }

    // ==================================================================================
    // Owner Resolution
    // ==================================================================================

    /**
     * Parse the owner seat from the zone name.
     *
     * <p>Convention: "hand:2" means seat 2 owns this zone.
     * Returns -1 if no owner suffix is present.
     */
    public int ownerSeat() {
        int colonIdx = name.lastIndexOf(':');
        if (colonIdx < 0 || colonIdx == name.length() - 1) return -1;
        try {
            return Integer.parseInt(name.substring(colonIdx + 1));
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    @Override
    public String toString() {
        return "Zone[" + name + ", " + visibility + ", " + size() + " items]";
    }
}
