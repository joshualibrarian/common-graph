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
 * Honorific — a courtesy or honorary title preceding a person's name
 * (Dr., Prof., Mr., Mrs., Hon., ...).  Atomic-text {@link Name} subtype.
 */
@Seed.Item(key = Honorific.KEY, head = Name.KEY)
public final class Honorific extends Name {

    public static final String KEY = "cg.archetype:honorific";

    private Honorific(String canonical) {
        super(ItemRef.iid(KEY), canonical);
    }

    @Decode
    public static Honorific fromText(String text) {
        return new Honorific(validateName(text, "Honorific"));
    }

    public static Honorific of(String text) {
        return fromText(text);
    }

    @Seed.Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
    static final String englishGloss =
            "a courtesy or honorary title preceding a person's name "
                    + "(Dr., Prof., Mr., Mrs., Hon., ...)";

    @Seed.Frame(predicate = LexicalVocabulary.Lexeme.KEY,
          field = @Seed.Binding(role = ThematicRole.Value.KEY,
                qualifiers = {Language.English.KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
    static final String[] englishNounLemmas = {"honorific", "title"};
}
