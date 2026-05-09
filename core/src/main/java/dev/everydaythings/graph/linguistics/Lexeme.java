package dev.everydaythings.graph.linguistics;

import dev.everydaythings.graph.Seed;
import dev.everydaythings.graph.item.Item;
import dev.everydaythings.graph.item.id.ItemID;
import dev.everydaythings.graph.runtime.Librarian;

/**
 * The lexeme predicate — a word in a language that points at a sememe (meaning).
 *
 * <p>A LEXEME frame attaches a surface form (lemma, inflected form, idiom phrase)
 * to its endorsing sememe. The lemma+language+POS combination identifies "this
 * word in this language with this part-of-speech expresses this meaning."
 *
 * <p>Body shape (typically created via {@code @Bind} on a seed class):
 * <pre>
 * LEXEME
 *     VALUE [LANGUAGE, POS, FEATURE...] → "lemma-or-form"
 *     [+ optional context bindings]
 * </pre>
 *
 * <p>Lexeme qualifiers (on the VALUE binding) carry:
 * <ul>
 *   <li>Language (English, Spanish, ...)</li>
 *   <li>Part of Speech (Noun, Verb, ...)</li>
 *   <li>Grammatical features (Lemma for canonical form; Past, Plural, etc. for
 *       inflected forms)</li>
 * </ul>
 *
 * <p>The sememe-being-named is implicit via the endorsing seed manifest. Same
 * surface form in different languages = different LEXEME frames pointing at
 * the same sememe (synonyms across languages).
 *
 * <p>Polysemy: the same word with the same language + POS may map to multiple
 * sememes — handled via multiple LEXEME frames, each endorsed by a different
 * sememe.
 *
 * <p>Phase 1 keeps {@code onFrameAssembled} minimal — body persistence is enough.
 * Future: token-dictionary indexing for word→sememe lookup at parse time.
 */
@Seed.Item(key = Lexeme.KEY)
@Seed.Embodies(key = Lexeme.KEY)
public class Lexeme extends Item {

    /** Canonical key for the lexeme sememe. */
    public static final String KEY = "cg.sememe:lexeme";

    /** The deterministic IID for the lexeme sememe. */
    public static final ItemID IID = ItemID.fromString(KEY);

    public Lexeme(ItemID iid, Librarian librarian) {
        super(iid, librarian);
    }
}
