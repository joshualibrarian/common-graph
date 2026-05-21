package dev.everydaythings.graph.language;

import dev.everydaythings.graph.CoreVocabulary;
import dev.everydaythings.graph.Seed;
import dev.everydaythings.graph.item.Item;
import dev.everydaythings.graph.ref.ItemRef;
import dev.everydaythings.graph.runtime.librarian.Librarian;

/**
 * Predicate carrying a sememe's CILI (Collaborative Interlingual Index) identifier.
 *
 * <p>CILI is a language-neutral concept index — its identifiers are stable across
 * language editions and merges, useful for cross-vocabulary alignment.
 *
 * <p>Body shape:
 * <pre>
 * CILI_ID
 *     VALUE → "i12345"
 * </pre>
 *
 * <p>Used alongside {@link CoreVocabulary.Source} on imported sememes whose source vocabulary
 * carries CILI alignments. Multiple imports referencing the same CILI ID
 * indicate the same underlying concept across vocabularies.
 *
 * <p>Created when CILI-aware imports begin; not used on hand-written CG sememes.
 */
@Seed.Item(key = CiliId.KEY)
@Seed.Embodies(key = CiliId.KEY)
public class CiliId extends Item {

    /** Canonical key for the CILI-id sememe. */
    public static final String KEY = "cg.sememe:cili-id";

    /** The deterministic IID for the CILI-id sememe. */

    public CiliId(ItemRef iid, Librarian librarian) {
        super(iid, librarian);
    }
}
