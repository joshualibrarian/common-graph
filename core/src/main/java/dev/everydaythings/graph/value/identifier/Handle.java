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
 * Handle — a generic username-shaped name used for online accounts,
 * unattached to any specific platform.  Atomic-text {@link Name} subtype.
 *
 * <p>Stored as plain text without any prefix.  Platforms that render
 * handles with a leading {@code @} (Twitter, GitHub) or {@code u/}
 * (Reddit) do so at display; the data layer carries just the text.  This
 * keeps Handles from colliding visually with IID notation ({@code @bciq…})
 * in serialized forms.
 *
 * <p>For platform-specific identifiers with their own validation rules
 * (TwitterId's 1-15 alphanumeric+underscore, GitHubUsername's 1-39
 * alphanumeric+dash, etc.), use a dedicated Identifier subtype rather
 * than Handle — those live outside the Name hierarchy because they're
 * structurally bound to an external system, not just a word someone
 * goes by.
 *
 * <p>Validation: baseline plain-text only.  Communities and platforms
 * impose stricter rules when ingesting.
 */
@Seed.Item(key = Handle.KEY, head = Name.KEY)
public final class Handle extends Name {

    public static final String KEY = "cg.archetype:handle";

    private Handle(String canonical) {
        super(ItemRef.iid(KEY), canonical);
    }

    @Decode
    public static Handle fromText(String text) {
        return new Handle(validateName(text, "Handle"));
    }

    public static Handle of(String text) {
        return fromText(text);
    }

    @Seed.Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
    static final String englishGloss =
            "a generic username-shaped name used for online accounts; stored as "
                    + "plain text (no @ or other prefix) and unattached to any specific "
                    + "platform — for platform-bound identifiers, use a dedicated subtype";

    @Seed.Frame(predicate = LexicalVocabulary.Lexeme.KEY,
          field = @Seed.Binding(role = ThematicRole.Value.KEY,
                qualifiers = {Language.English.KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
    static final String[] englishNounLemmas = {"handle", "username", "screen name"};
}
