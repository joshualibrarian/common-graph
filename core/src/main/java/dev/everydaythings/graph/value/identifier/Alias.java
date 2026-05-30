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
 * Alias — a chosen alternative name used in place of one's given/family
 * name in social or written contexts.  Stage names, pen names, and
 * professional aliases all fit here.
 *
 * <p>Distinguished from {@link Pseudonym}: an Alias is a chosen alternative
 * (Bob Dylan, Madonna) that may still be associated with the underlying
 * person; a Pseudonym is specifically intended to dissociate (Mark Twain,
 * George Eliot — the writer's real identity was meant to stay hidden).
 *
 * <p>Atomic-text {@link Name} subtype.
 */
@Seed.Item(key = Alias.KEY, head = Name.KEY)
public final class Alias extends Name {

    public static final String KEY = "cg.archetype:alias";

    private Alias(String canonical) {
        super(ItemRef.iid(KEY), canonical);
    }

    @Decode
    public static Alias fromText(String text) {
        return new Alias(validateName(text, "Alias"));
    }

    public static Alias of(String text) {
        return fromText(text);
    }

    @Seed.Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
    static final String englishGloss =
            "a chosen alternative name used in social or professional contexts; "
                    + "stage names, pen names, and other public aliases (distinguished "
                    + "from pseudonyms by intent — aliases may stay linked to the person)";

    @Seed.Frame(predicate = LexicalVocabulary.Lexeme.KEY,
          field = @Seed.Binding(role = ThematicRole.Value.KEY,
                qualifiers = {Language.English.KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
    static final String[] englishNounLemmas = {"alias", "stage name", "pen name"};
}
