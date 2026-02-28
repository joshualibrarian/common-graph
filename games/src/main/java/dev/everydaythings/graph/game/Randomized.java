package dev.everydaythings.graph.game;

/**
 * Capability marker: this game uses randomness (dice, shuffles, draws).
 *
 * <p>Games implementing this signal that their Op type includes
 * randomness-triggering operations and that their {@code fold()} method
 * creates {@link GameRandom} instances from event CIDs for verifiable,
 * deterministic random outcomes.
 *
 * <p>This is a marker interface — it carries no methods. The actual
 * randomness is generated inside {@code fold()} via
 * {@link GameRandom#fromEvent(dev.everydaythings.graph.item.component.Dag.Event)}.
 *
 * @see GameRandom
 */
public interface Randomized {
    // Marker interface.
}
