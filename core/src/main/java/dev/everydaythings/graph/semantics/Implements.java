package dev.everydaythings.graph.semantics;

import dev.everydaythings.graph.item.Seed;
import dev.everydaythings.graph.item.id.ItemID;

/**
 * The IMPLEMENTS predicate sememe — head of frames declaring "an implementation of
 * this concept exists, embodied in this artifact."
 *
 * <p>The {@link dev.everydaythings.graph.item.SeedProcessor} publishes IMPLEMENTS
 * frames at bootstrap for every {@link dev.everydaythings.graph.item.Mints @Mints}-
 * annotated class. The frames look like:
 *
 * <pre>
 * IMPLEMENTS { THEME → concept-IID, AGENT[runtime] → implementation-reference }
 * </pre>
 *
 * <p>{@link Create} consults these frames to find runnable instance classes when
 * minting via CREATE. Future work may add trust-weighted ordering when multiple
 * implementations exist for the same concept.
 */
@Seed(key = Implements.KEY)
public final class Implements {

    public static final String KEY = "cg.sememe:implements";
    public static final ItemID IID = ItemID.fromString(KEY);

    private Implements() {}
}
