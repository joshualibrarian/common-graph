package dev.everydaythings.graph.game;

/**
 * Visibility rules for game zones.
 *
 * <p>Controls what a viewer can see of a zone's contents.
 * Game logic always has full access; visibility is a display and
 * transmission concern.
 *
 * @see Zone#contentsVisibleTo(int)
 */
public enum ZoneVisibility {

    /** Everyone can see all contents (community cards, discard pile). */
    PUBLIC,

    /** No one can see contents (face-down deck). */
    HIDDEN,

    /** Only the top item is visible (draw pile with face-up top). */
    TOP_ONLY,

    /** Only the owning player can see contents (hand). */
    OWNER,

    /**
     * Owner sees contents; others see count only.
     * Useful where knowing the count matters (UNO: "2 cards left").
     */
    OWNER_AND_COUNT
}
