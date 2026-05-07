package dev.everydaythings.graph.linguistics;

import dev.everydaythings.graph.item.Embodies;
import dev.everydaythings.graph.item.Item;
import dev.everydaythings.graph.item.Seed;
import dev.everydaythings.graph.item.id.ItemID;
import dev.everydaythings.graph.runtime.Librarian;

/**
 * The gloss predicate — a per-language definition of a sememe.
 *
 * <p>A GLOSS frame attaches a human-readable definition to its endorsing seed,
 * scoped by language qualifier. One sememe can have many glosses (one per
 * language); each gloss frame is independent and supersedable.
 *
 * <p>Body shape (typically created via {@code @Bind} on a seed class):
 * <pre>
 * GLOSS
 *     VALUE [LANGUAGE] → "the human-readable definition"
 *     [+ optional context bindings]
 * </pre>
 *
 * <p>The relationship to the sememe-being-defined is implicit: the seed manifest
 * has an ENDORSES binding to this gloss frame body. So "gloss for X" =
 * "GLOSS frame endorsed by X's manifest."
 *
 * <p>Multiple glosses per language are allowed (alternative definitions, edited
 * over time, contributed by different parties). Receivers' trust matrices and
 * presentation logic decide which to display.
 *
 * <p>Phase 1 keeps {@code onFrameAssembled} minimal — body persistence is enough.
 * Future: token-dictionary indexing for "find sememes whose gloss matches X."
 */
@Seed(key = Gloss.KEY)
@Embodies(key = Gloss.KEY)
public class Gloss extends Item {

    /** Canonical key for the gloss sememe. */
    public static final String KEY = "cg.sememe:gloss";

    /** The deterministic IID for the gloss sememe. */
    public static final ItemID IID = ItemID.fromString(KEY);

    public Gloss(ItemID iid, Librarian librarian) {
        super(iid, librarian);
    }
}
