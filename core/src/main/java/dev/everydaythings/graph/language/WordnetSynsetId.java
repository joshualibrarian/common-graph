package dev.everydaythings.graph.language;

import dev.everydaythings.graph.CoreVocabulary;
import dev.everydaythings.graph.Seed;
import dev.everydaythings.graph.item.Item;
import dev.everydaythings.graph.id.ItemRef;
import dev.everydaythings.graph.runtime.librarian.Librarian;

/**
 * Predicate carrying a sememe's WordNet synset identifier.
 *
 * <p>WordNet synsets are typed by part-of-speech and a numeric offset; the
 * canonical text form is e.g. {@code "v#01617192"} for verbs, {@code "n#06354675"}
 * for nouns.
 *
 * <p>Body shape:
 * <pre>
 * WORDNET_SYNSET_ID
 *     VALUE → "v#01617192"
 * </pre>
 *
 * <p>Used alongside {@link CoreVocabulary.Source} on imported sememes to pin them to their
 * WordNet identifier — enabling cross-vocabulary deduplication when multiple
 * imports introduce sememes for the same concept.
 *
 * <p>Created when WordNet imports begin; not used on hand-written CG sememes.
 */
@Seed.Item(key = WordnetSynsetId.KEY)
@Seed.Embodies(key = WordnetSynsetId.KEY)
public class WordnetSynsetId extends Item {

    /** Canonical key for the WordNet-synset-id sememe. */
    public static final String KEY = "cg.sememe:wordnet-synset-id";

    /** The deterministic IID for the WordNet-synset-id sememe. */

    public WordnetSynsetId(ItemRef iid, Librarian librarian) {
        super(iid, librarian);
    }
}
