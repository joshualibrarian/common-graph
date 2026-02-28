package dev.everydaythings.graph.game.dominoes;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.experimental.Accessors;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A domino tile with left and right pip values (0-6).
 *
 * <p>Tiles are normalized so left <= right for consistent equals/hashCode.
 * The double-six set has 28 tiles total (0-0 through 6-6).
 */
@Getter
@Accessors(fluent = true)
@EqualsAndHashCode
public class DominoTile {

    /** Maximum pip value in the double-six set. */
    public static final int MAX_PIPS = 6;

    /** Total tiles in a double-six set. */
    public static final int SET_SIZE = 28;

    private final int left;
    private final int right;

    /**
     * Create a tile. Values are normalized so left <= right.
     */
    public DominoTile(int a, int b) {
        if (a < 0 || a > MAX_PIPS || b < 0 || b > MAX_PIPS) {
            throw new IllegalArgumentException("Pip values must be 0-" + MAX_PIPS);
        }
        this.left = Math.min(a, b);
        this.right = Math.max(a, b);
    }

    /** Whether this is a double (both sides same). */
    public boolean isDouble() {
        return left == right;
    }

    /** Total pip count. */
    public int pipCount() {
        return left + right;
    }

    /** Whether this tile can connect to the given end value. */
    public boolean matches(int endValue) {
        return left == endValue || right == endValue;
    }

    /**
     * Get the other end of the tile when one end is connected.
     *
     * @param connectedEnd the end value that's connected
     * @return the value of the other end
     */
    public int otherEnd(int connectedEnd) {
        if (left == connectedEnd) return right;
        if (right == connectedEnd) return left;
        throw new IllegalArgumentException("Tile " + this + " is not connected at " + connectedEnd);
    }

    /**
     * Ordinal encoding for CBOR serialization (0-27).
     * Triangular number indexing: tile(a,b) where a <= b.
     */
    public int ordinal() {
        // Sum of (MAX_PIPS+1), (MAX_PIPS), ... for rows before 'left'
        // Plus offset within row
        int ord = 0;
        for (int i = 0; i < left; i++) {
            ord += (MAX_PIPS + 1) - i;
        }
        ord += (right - left);
        return ord;
    }

    /**
     * Decode a tile from its ordinal (0-27).
     */
    public static DominoTile fromOrdinal(int ordinal) {
        if (ordinal < 0 || ordinal >= SET_SIZE) {
            throw new IllegalArgumentException("Ordinal must be 0-" + (SET_SIZE - 1) + ", got: " + ordinal);
        }
        int left = 0;
        int remaining = ordinal;
        while (remaining >= (MAX_PIPS + 1) - left) {
            remaining -= (MAX_PIPS + 1) - left;
            left++;
        }
        int right = left + remaining;
        return new DominoTile(left, right);
    }

    /**
     * Unicode-style symbol for text rendering.
     */
    public String symbol() {
        return "[" + left + "|" + right + "]";
    }

    /**
     * Generate the full double-six set (28 tiles).
     */
    public static List<DominoTile> fullSet() {
        List<DominoTile> tiles = new ArrayList<>(SET_SIZE);
        for (int a = 0; a <= MAX_PIPS; a++) {
            for (int b = a; b <= MAX_PIPS; b++) {
                tiles.add(new DominoTile(a, b));
            }
        }
        return Collections.unmodifiableList(tiles);
    }

    @Override
    public String toString() {
        return symbol();
    }
}
