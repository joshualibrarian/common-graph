package dev.everydaythings.graph.actor;

import dev.everydaythings.graph.Seed;
import dev.everydaythings.graph.item.Item;
import dev.everydaythings.graph.ref.ItemRef;
import dev.everydaythings.graph.language.GrammaticalFeature;
import dev.everydaythings.graph.language.Language;
import dev.everydaythings.graph.language.LexicalVocabulary;
import dev.everydaythings.graph.language.PartOfSpeech;
import dev.everydaythings.graph.language.ThematicRole;
import dev.everydaythings.graph.runtime.librarian.Librarian;

/**
 * Person — a human, modeled as an {@link Item}.
 *
 * <p>A Person item represents a specific human being.  It may or may not have
 * any associated {@link dev.everydaythings.graph.cryptography.Signer Signers};
 * the connection is made via the
 * {@link dev.everydaythings.graph.cryptography.IdentityVocabulary.Represents
 * REPRESENTS} predicate.  Historical figures, the deceased, and people who
 * have never held cryptographic keys can all be modeled as Persons.
 *
 * <p>Persons have no canonical bytes to derive an IID from (deriving from PII
 * would be a privacy disaster), so Person IIDs are typically random or
 * assigned by the creating party.
 */
@Seed.Item(key = Person.KEY)
@Seed.Cili("i35562")
public class Person extends Item {

    /** Canonical key for the Person archetype. */
    public static final String KEY = "cg.archetype:person";

    @Seed.Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
    static final String englishGloss =
            "a human being; a kind of actor that one or more signers may represent";

    @Seed.Frame(predicate = LexicalVocabulary.Lexeme.KEY,
          field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
    static final String englishNounLemma = "person";

    public Person(ItemRef iid) {
        super(iid);
    }

    public Person(ItemRef iid, Librarian librarian) {
        super(iid, librarian);
    }
}
