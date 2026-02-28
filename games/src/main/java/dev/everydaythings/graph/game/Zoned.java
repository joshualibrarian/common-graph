package dev.everydaythings.graph.game;

/**
 * Capability: the game has named zones holding ordered collections of items.
 *
 * <p>Zones model any named container with visibility rules:
 * <ul>
 *   <li>Card games: hand, deck, discard, community, kitty</li>
 *   <li>Tile placement: bag, rack, discard</li>
 *   <li>Resource games: supply, bank, reserve</li>
 * </ul>
 *
 * <p>Visibility is a first-class concern: a player's hand is visible only to
 * them; the deck is hidden to all; community cards are public.
 *
 * @param <T> the type of item stored in zones (PlayingCard, Tile, ResourceCard)
 * @see ZoneMap
 * @see Zone
 * @see ZoneVisibility
 */
public interface Zoned<T> {

    /**
     * Get the zone map managing all named zones.
     */
    ZoneMap<T> zones();
}
