package dev.everydaythings.graph.game;

/**
 * Capability marker: this game uses randomness (dice, shuffles, draws).
 *
 * <p>Games implementing this signal that their Op type includes
 * randomness-triggering operations and that their {@code apply()} method
 * creates {@link GameRandom} instances from {@link GameComponent#opSeed()}
 * for deterministic random outcomes.
 *
 * <p>This is a marker interface — it carries no methods. The actual
 * randomness is generated inside {@code apply()} via
 * {@link GameRandom#fromSeed(byte[])}.
 *
 * @see GameRandom
 */
public interface Randomized {
    // Marker interface.
}
