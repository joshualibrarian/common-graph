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
 * Suffix — a generational or credential suffix following a person's name
 * (Jr., Sr., III, PhD, MD, Esq., ...).  Atomic-text {@link Name} subtype.
 */
@Seed.Item(key = Suffix.KEY, head = Name.KEY)
public final class Suffix extends Name {

    public static final String KEY = "cg.archetype:name-suffix";

    private Suffix(String canonical) {
        super(ItemRef.iid(KEY), canonical);
    }

    @Decode
    public static Suffix fromText(String text) {
        return new Suffix(validateName(text, "Suffix"));
    }

    public static Suffix of(String text) {
        return fromText(text);
    }

    @Seed.Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
    static final String englishGloss =
            "a generational or credential suffix following a person's name "
                    + "(Jr., Sr., III, PhD, MD, Esq., ...)";

    @Seed.Frame(predicate = LexicalVocabulary.Lexeme.KEY,
          field = @Seed.Binding(role = ThematicRole.Value.KEY,
                qualifiers = {Language.English.KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
    static final String englishNounLemma = "name suffix";
}
