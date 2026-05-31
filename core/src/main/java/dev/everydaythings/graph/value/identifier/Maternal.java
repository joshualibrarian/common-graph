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
 * Maternal — the maternal-surname / matronymic name part used in Spanish,
 * Portuguese, and related naming traditions where a person's full name
 * carries both paternal ({@link FamilyName}) and maternal surnames.
 * Atomic-text {@link Name} subtype.
 */
@Seed.Item(key = Maternal.KEY, head = Name.KEY)
public final class Maternal extends Name {

    public static final String KEY = "cg.archetype:maternal-name";

    private Maternal(String canonical) {
        super(ItemRef.iid(KEY), canonical);
    }

    @Decode
    public static Maternal fromText(String text) {
        return new Maternal(validateName(text, "Maternal"));
    }

    public static Maternal of(String text) {
        return fromText(text);
    }

    @Seed.Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
    static final String englishGloss =
            "the maternal-surname / matronymic name part used in Spanish, "
                    + "Portuguese, and related naming traditions";

    @Seed.Frame(predicate = LexicalVocabulary.Lexeme.KEY,
          field = @Seed.Binding(role = ThematicRole.Value.KEY,
                qualifiers = {Language.English.KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
    static final String[] englishNounLemmas = {"maternal name", "matronymic"};
}
