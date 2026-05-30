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
 * Patronymic — a name derived from the father's given name, common in
 * Slavic, Scandinavian, Arabic, and other naming traditions
 * ("Petrovich", "Olafsdóttir", "ibn Khattab").  Atomic-text {@link Name}
 * subtype.
 */
@Seed.Item(key = Patronymic.KEY, head = Name.KEY)
public final class Patronymic extends Name {

    public static final String KEY = "cg.archetype:patronymic";

    private Patronymic(String canonical) {
        super(ItemRef.iid(KEY), canonical);
    }

    @Decode
    public static Patronymic fromText(String text) {
        return new Patronymic(validateName(text, "Patronymic"));
    }

    public static Patronymic of(String text) {
        return fromText(text);
    }

    @Seed.Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
    static final String englishGloss =
            "a name derived from the father's given name, common in Slavic, "
                    + "Scandinavian, Arabic, and other naming traditions";

    @Seed.Frame(predicate = LexicalVocabulary.Lexeme.KEY,
          field = @Seed.Binding(role = ThematicRole.Value.KEY,
                qualifiers = {Language.English.KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
    static final String englishNounLemma = "patronymic";
}
