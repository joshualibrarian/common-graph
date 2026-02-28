package dev.everydaythings.graph.game;

import java.util.*;
import java.util.stream.Stream;

/**
 * Manages named zones with contents and visibility rules.
 *
 * <p>A ZoneMap is the card/tile analog of {@link BoardState}. Where BoardState
 * maps space-ids to pieces on a spatial board, ZoneMap maps zone-names to
 * ordered collections with visibility policies.
 *
 * <p>Zones can be shared (deck, discard, community) or per-player
 * (hand:0, hand:1). Per-player zones use the naming convention
 * "baseName:seatIndex".
 *
 * <h3>Example: Poker</h3>
 * <pre>{@code
 * ZoneMap<PlayingCard> zones = new ZoneMap<>();
 * zones.define("deck", ZoneVisibility.HIDDEN);
 * zones.define("community", ZoneVisibility.PUBLIC);
 * zones.define("discard", ZoneVisibility.PUBLIC);
 * zones.definePerPlayer("hand", 6, ZoneVisibility.OWNER);
 * }</pre>
 *
 * @param <T> the type of items in zones
 * @see Zone
 * @see Zoned
 */
public class ZoneMap<T> {

    private final Map<String, Zone<T>> zones = new LinkedHashMap<>();

    // ==================================================================================
    // Zone Definition
    // ==================================================================================

    /**
     * Define a new zone.
     *
     * @param name       zone name (e.g., "deck", "hand:0", "discard")
     * @param visibility who can see this zone's contents
     * @return this (for chaining)
     */
    public ZoneMap<T> define(String name, ZoneVisibility visibility) {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(visibility, "visibility");
        zones.put(name, new Zone<>(name, visibility));
        return this;
    }

    /**
     * Define per-player zones for each seat.
     *
     * <p>Creates "name:0", "name:1", ..., "name:(seats-1)".
     *
     * @param baseName   zone base name (e.g., "hand")
     * @param seats      number of player seats
     * @param visibility visibility rule
     * @return this (for chaining)
     */
    public ZoneMap<T> definePerPlayer(String baseName, int seats, ZoneVisibility visibility) {
        for (int i = 0; i < seats; i++) {
            define(baseName + ":" + i, visibility);
        }
        return this;
    }

    // ==================================================================================
    // Access
    // ==================================================================================

    /**
     * Get a zone by name.
     *
     * @throws IllegalArgumentException if zone doesn't exist
     */
    public Zone<T> zone(String name) {
        Zone<T> z = zones.get(name);
        if (z == null) throw new IllegalArgumentException("Unknown zone: " + name);
        return z;
    }

    /**
     * Get a player's personal zone.
     *
     * <p>Convenience: {@code zone("hand", 2)} resolves to {@code zone("hand:2")}.
     */
    public Zone<T> zone(String baseName, int seat) {
        return zone(baseName + ":" + seat);
    }

    /** Check if a zone exists. */
    public boolean hasZone(String name) {
        return zones.containsKey(name);
    }

    /** Stream all zone names. */
    public Stream<String> zoneNames() {
        return zones.keySet().stream();
    }

    /** Stream all zones. */
    public Stream<Zone<T>> allZones() {
        return zones.values().stream();
    }

    /** Number of defined zones. */
    public int zoneCount() {
        return zones.size();
    }

    // ==================================================================================
    // Transfer Operations
    // ==================================================================================

    /**
     * Move the top item from one zone to another.
     *
     * @param from source zone name
     * @param to   destination zone name
     * @return the moved item, or empty if source was empty
     */
    public Optional<T> transfer(String from, String to) {
        Zone<T> src = zone(from);
        Zone<T> dst = zone(to);
        return src.removeTop().map(item -> {
            dst.addTop(item);
            return item;
        });
    }

    /**
     * Move a specific item from one zone to another.
     *
     * @return true if the item was found and moved
     */
    public boolean transfer(String from, String to, T item) {
        Zone<T> src = zone(from);
        Zone<T> dst = zone(to);
        if (src.remove(item)) {
            dst.addTop(item);
            return true;
        }
        return false;
    }

    // ==================================================================================
    // Aggregate Queries
    // ==================================================================================

    /**
     * Total count of items across all zones.
     */
    public int totalCount() {
        return zones.values().stream().mapToInt(Zone::size).sum();
    }

    @Override
    public String toString() {
        return "ZoneMap[" + zoneCount() + " zones, " + totalCount() + " items]";
    }
}
