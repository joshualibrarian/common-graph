package dev.everydaythings.graph.linguistics;

import dev.everydaythings.graph.Seed;
import dev.everydaythings.graph.item.Item;
import dev.everydaythings.graph.id.ItemID;
import dev.everydaythings.graph.runtime.Librarian;

/**
 * The source-attribution predicate — names the dataset / vocabulary a sememe
 * was imported from.
 *
 * <p>Convention: hand-written CG-native sememes carry NO source frame; their
 * absence means "implicitly part of CG core." Imported sememes (from WordNet,
 * CILI, VerbNet, etc.) get a SOURCE frame via {@code @Bind} attribution.
 *
 * <p>Body shape:
 * <pre>
 * SOURCE
 *     VALUE              → @vocabulary-sememe (e.g., Oewn, Cili)
 *     ATTRIBUTE [VERSION] → "2025"             # optional version literal
 * </pre>
 *
 * <p>Specific identifier predicates ({@link WordnetSynsetId}, {@link CiliId})
 * carry the source's own ID for the sememe — alongside SOURCE, they pin the
 * imported sememe to its origin's identifier system for cross-vocabulary merge.
 *
 * <p>Source-vocabulary sememes are inner classes here for proximity (small
 * pure-data targets, no behavior of their own).
 */
@Seed.Item(key = Source.KEY)
@Seed.Embodies(key = Source.KEY)
public class Source extends Item {

    /** Canonical key for the source-attribution sememe. */
    public static final String KEY = "cg.sememe:source";

    /** The deterministic IID for the source-attribution sememe. */
    public static final ItemID IID = ItemID.fromString(KEY);

    public Source(ItemID iid, Librarian librarian) {
        super(iid, librarian);
    }

    // ==================================================================================
    // Source-vocabulary sememes (targets of SOURCE → VALUE bindings)
    // ==================================================================================

    /** Open English WordNet — the OEWN project (any release; version on the binding). */
    @Seed.Item(key = Oewn.KEY)
    public static final class Oewn {
        public static final String KEY = "cg.source:oewn";
        public static final ItemID IID = ItemID.fromString(KEY);
        private Oewn() {}
    }

    /** Collaborative Interlingual Index — language-neutral concept identifiers. */
    @Seed.Item(key = Cili.KEY)
    public static final class Cili {
        public static final String KEY = "cg.source:cili";
        public static final ItemID IID = ItemID.fromString(KEY);
        private Cili() {}
    }
}
