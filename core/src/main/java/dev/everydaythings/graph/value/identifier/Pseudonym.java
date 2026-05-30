package dev.everydaythings.graph.value.identifier;

import dev.everydaythings.graph.Seed;
import dev.everydaythings.graph.canonical.Decode;
import dev.everydaythings.graph.language.GrammaticalFeature;
import dev.everydaythings.graph.language.Language;
import dev.everydaythings.graph.language.LexicalVocabulary;
import dev.everydaythings.graph.language.PartOfSpeech;
import dev.everydaythings.graph.language.ThematicRole;
import dev.everydaythings.graph.ref.ItemRef;

/**
 * Pseudonym — a chosen name specifically intended to dissociate from one's
 * real identity.  Whistleblowers, anonymous authors, private-by-design
 * personas all use pseudonyms.
 *
 * <p>Distinguished from {@link Alias}: pseudonyms are intent-anonymous;
 * aliases may stay associated with the underlying person.  The structural
 * type is the same; the semantic difference matters for downstream
 * visibility, search, and disclosure rules.
 *
 * <p>Atomic-text {@link Name} subtype.
 */
@Seed.Item(key = Pseudonym.KEY, head = Name.KEY)
public final class Pseudonym extends Name {

    public static final String KEY = "cg.archetype:pseudonym";

    private Pseudonym(String canonical) {
        super(ItemRef.iid(KEY), canonical);
    }

    @Decode
    public static Pseudonym fromText(String text) {
        return new Pseudonym(validateName(text, "Pseudonym"));
    }

    public static Pseudonym of(String text) {
        return fromText(text);
    }

    @Seed.Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
    static final String englishGloss =
            "a chosen name intended to dissociate from one's real identity — used "
                    + "by whistleblowers, anonymous authors, and private-by-design personas";

    @Seed.Frame(predicate = LexicalVocabulary.Lexeme.KEY,
          field = @Seed.Binding(role = ThematicRole.Value.KEY,
                qualifiers = {Language.English.KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
    static final String englishNounLemma = "pseudonym";
}
