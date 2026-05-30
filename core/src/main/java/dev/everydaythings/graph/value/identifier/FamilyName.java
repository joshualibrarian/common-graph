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
 * FamilyName — the part of a person's name shared with family, typically
 * inherited from a parent (surname / last name in Western tradition).
 * Atomic-text {@link Name} subtype.
 */
@Seed.Item(key = FamilyName.KEY, head = Name.KEY)
public final class FamilyName extends Name {

    public static final String KEY = "cg.archetype:family-name";

    private FamilyName(String canonical) {
        super(ItemRef.iid(KEY), canonical);
    }

    @Decode
    public static FamilyName fromText(String text) {
        return new FamilyName(validateName(text, "FamilyName"));
    }

    public static FamilyName of(String text) {
        return fromText(text);
    }

    @Seed.Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
    static final String englishGloss =
            "the part of a person's name shared with family, typically inherited "
                    + "from a parent; surname or last name in Western tradition";

    @Seed.Frame(predicate = LexicalVocabulary.Lexeme.KEY,
          field = @Seed.Binding(role = ThematicRole.Value.KEY,
                qualifiers = {Language.English.KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
    static final String[] englishNounLemmas = {"family name", "surname", "last name"};
}
