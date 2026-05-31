package dev.everydaythings.graph.value.identifier;

import dev.everydaythings.graph.Seed;
import dev.everydaythings.graph.canonical.Decode;
import dev.everydaythings.graph.GrammaticalFeature;
import dev.everydaythings.graph.language.Language;
import dev.everydaythings.graph.language.LexicalVocabulary;
import dev.everydaythings.graph.PartOfSpeech;
import dev.everydaythings.graph.ThematicRole;
import dev.everydaythings.graph.ref.ItemRef;

/** MiddleName — a name positioned between given and family.  Atomic-text {@link Name} subtype. */
@Seed.Item(key = MiddleName.KEY, head = Name.KEY)
public final class MiddleName extends Name {

    public static final String KEY = "cg.archetype:middle-name";

    private MiddleName(String canonical) {
        super(ItemRef.iid(KEY), canonical);
    }

    @Decode
    public static MiddleName fromText(String text) {
        return new MiddleName(validateName(text, "MiddleName"));
    }

    public static MiddleName of(String text) {
        return fromText(text);
    }

    @Seed.Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
    static final String englishGloss = "a name part positioned between the given and family names";

    @Seed.Frame(predicate = LexicalVocabulary.Lexeme.KEY,
          field = @Seed.Binding(role = ThematicRole.Value.KEY,
                qualifiers = {Language.English.KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
    static final String englishNounLemma = "middle name";
}
