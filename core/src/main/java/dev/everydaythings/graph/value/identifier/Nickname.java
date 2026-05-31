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
 * Nickname — an informal or familiar name used by friends, family, or
 * community.  Atomic-text {@link Name} subtype.
 */
@Seed.Item(key = Nickname.KEY, head = Name.KEY)
public final class Nickname extends Name {

    public static final String KEY = "cg.archetype:nickname";

    private Nickname(String canonical) {
        super(ItemRef.iid(KEY), canonical);
    }

    @Decode
    public static Nickname fromText(String text) {
        return new Nickname(validateName(text, "Nickname"));
    }

    public static Nickname of(String text) {
        return fromText(text);
    }

    @Seed.Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
    static final String englishGloss =
            "an informal or familiar name used in place of or in addition to a "
                    + "person's given name";

    @Seed.Frame(predicate = LexicalVocabulary.Lexeme.KEY,
          field = @Seed.Binding(role = ThematicRole.Value.KEY,
                qualifiers = {Language.English.KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
    static final String[] englishNounLemmas = {"nickname", "pet name", "sobriquet"};
}
