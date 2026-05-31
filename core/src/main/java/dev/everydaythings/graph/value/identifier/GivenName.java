package dev.everydaythings.graph.value.identifier;

import dev.everydaythings.graph.Seed;
import dev.everydaythings.graph.canonical.Decode;
import dev.everydaythings.graph.GrammaticalFeature;
import dev.everydaythings.graph.language.Language;
import dev.everydaythings.graph.language.LexicalVocabulary;
import dev.everydaythings.graph.PartOfSpeech;
import dev.everydaythings.graph.ThematicRole;
import dev.everydaythings.graph.ref.ItemRef;

/**
 * GivenName — the part of a person's name given to them at birth (first
 * name in Western tradition, personal name in others).  Atomic-text
 * {@link Name} subtype.
 */
@Seed.Item(key = GivenName.KEY, head = Name.KEY)
public final class GivenName extends Name {

    public static final String KEY = "cg.archetype:given-name";

    private GivenName(String canonical) {
        super(ItemRef.iid(KEY), canonical);
    }

    @Decode
    public static GivenName fromText(String text) {
        return new GivenName(validateName(text, "GivenName"));
    }

    /** Convenience alias for {@link #fromText(String)}. */
    public static GivenName of(String text) {
        return fromText(text);
    }

    @Seed.Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
    static final String englishGloss =
            "the part of a person's name given to them at birth, distinguishing "
                    + "them from family members; first name in Western tradition";

    @Seed.Frame(predicate = LexicalVocabulary.Lexeme.KEY,
          field = @Seed.Binding(role = ThematicRole.Value.KEY,
                qualifiers = {Language.English.KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
    static final String[] englishNounLemmas = {"given name", "first name", "forename", "Christian name"};
}
