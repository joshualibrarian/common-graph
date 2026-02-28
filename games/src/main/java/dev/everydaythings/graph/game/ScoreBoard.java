package dev.everydaythings.graph.game;

import java.util.*;

/**
 * Tracks per-player scores across named categories.
 *
 * <p>Each category is a named integer counter. Games define their own:
 * <ul>
 *   <li>Scrabble: just "score"</li>
 *   <li>Catan: "victory-points", "wood", "brick", "ore", "wheat", "sheep"</li>
 *   <li>Poker: "chips"</li>
 * </ul>
 *
 * <p>Simple games use the default {@link #DEFAULT} category via the
 * single-argument convenience methods.
 *
 * @see Scored
 */
public class ScoreBoard {

    /** Default score category for simple point-tracking games. */
    public static final String DEFAULT = "score";

    // seat -> category -> amount
    private final Map<Integer, Map<String, Integer>> scores = new LinkedHashMap<>();

    // ==================================================================================
    // Single-Category Convenience
    // ==================================================================================

    /** Get a player's score in the default category. */
    public int score(int seat) {
        return score(seat, DEFAULT);
    }

    /** Add to a player's score in the default category. */
    public void add(int seat, int delta) {
        add(seat, DEFAULT, delta);
    }

    /** Set a player's score in the default category. */
    public void set(int seat, int value) {
        set(seat, DEFAULT, value);
    }

    // ==================================================================================
    // Multi-Category
    // ==================================================================================

    /** Get a player's value in a named category. */
    public int score(int seat, String category) {
        return scores.getOrDefault(seat, Map.of())
                .getOrDefault(category, 0);
    }

    /** Add to a player's value in a named category. */
    public void add(int seat, String category, int delta) {
        scores.computeIfAbsent(seat, k -> new LinkedHashMap<>())
                .merge(category, delta, Integer::sum);
    }

    /** Set a player's value in a named category. */
    public void set(int seat, String category, int value) {
        scores.computeIfAbsent(seat, k -> new LinkedHashMap<>())
                .put(category, value);
    }

    /** Get all categories and their values for a player. */
    public Map<String, Integer> allScores(int seat) {
        return Collections.unmodifiableMap(
                scores.getOrDefault(seat, Map.of()));
    }

    /**
     * Get a leaderboard for a category (seat indices sorted descending by score).
     */
    public List<Integer> leaderboard(String category) {
        return scores.entrySet().stream()
                .sorted((a, b) -> Integer.compare(
                        b.getValue().getOrDefault(category, 0),
                        a.getValue().getOrDefault(category, 0)))
                .map(Map.Entry::getKey)
                .toList();
    }

    /** Get all known categories across all players. */
    public Set<String> categories() {
        Set<String> cats = new LinkedHashSet<>();
        scores.values().forEach(m -> cats.addAll(m.keySet()));
        return Collections.unmodifiableSet(cats);
    }

    /** Reset all scores. */
    public void clear() {
        scores.clear();
    }

    @Override
    public String toString() {
        return "ScoreBoard" + scores;
    }
}
