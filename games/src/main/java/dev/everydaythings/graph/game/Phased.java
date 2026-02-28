package dev.everydaythings.graph.game;

import java.util.List;

/**
 * Capability: turns have sub-phases that constrain what actions are legal.
 *
 * <p>Many games have structured turns:
 * <ul>
 *   <li>Catan: roll → trade → build</li>
 *   <li>Poker: deal → preflop → flop → turn → river → showdown</li>
 *   <li>MTG: untap → upkeep → draw → main → combat → main → end</li>
 * </ul>
 *
 * <p>Phases are game-defined strings, not a shared enum. Each game
 * defines its own phase vocabulary.
 */
public interface Phased {

    /**
     * The current phase within the current turn.
     *
     * @return phase name (game-specific, e.g., "roll", "trade", "build")
     */
    String currentPhase();

    /**
     * All phases in turn order (for UI display and validation).
     */
    List<String> phases();
}
