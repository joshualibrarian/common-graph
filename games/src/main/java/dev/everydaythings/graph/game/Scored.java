package dev.everydaythings.graph.game;

/**
 * Capability: the game tracks scores or resources per player.
 *
 * <p>Covers:
 * <ul>
 *   <li>Simple scoring (Scrabble word points)</li>
 *   <li>Multi-resource tracking (Catan: wood, brick, ore, wheat, sheep)</li>
 *   <li>Victory points from multiple sources</li>
 *   <li>Money/currency (Monopoly, Poker chips)</li>
 * </ul>
 *
 * @see ScoreBoard
 */
public interface Scored {

    /**
     * Get the scoreboard tracking all players' scores/resources.
     */
    ScoreBoard scoreBoard();
}
