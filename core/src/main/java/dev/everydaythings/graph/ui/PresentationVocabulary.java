package dev.everydaythings.graph.ui;

import dev.everydaythings.graph.language.ThematicRole;
import dev.everydaythings.graph.id.ItemRef;
import dev.everydaythings.graph.language.*;

import static dev.everydaythings.graph.Seed.*;

/**
 * Presentation vocabulary — palette-slot sememes that name the semantic role
 * a color (or other visual treatment) plays in a UI.
 *
 * <p>"Primary," "Secondary," "Accent," "Surface," etc. are language-neutral
 * roles a UI assigns concrete colors to. The theming layer maps each slot to
 * a {@link dev.everydaythings.graph.value.Color Color} value (or to a
 * user-defined override) at render time.
 */
public final class PresentationVocabulary {

    private PresentationVocabulary() {}

    @Item(key = Primary.KEY)
    public static final class Primary {
        public static final String KEY = "cg.presentation:primary";
        private Primary() {}

        @Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss = "primary brand color for prominent UI elements";

        @Frame(predicate = LexicalVocabulary.Lexeme.KEY,
          field = @Binding(role = ThematicRole.Value.KEY,
            qualifiers = {Language.English.KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String englishNounLemma = "primary";
    }

    @Item(key = Secondary.KEY)
    public static final class Secondary {
        public static final String KEY = "cg.presentation:secondary";
        private Secondary() {}

        @Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss = "secondary brand color for supporting UI elements";

        @Frame(predicate = LexicalVocabulary.Lexeme.KEY,
          field = @Binding(role = ThematicRole.Value.KEY,
            qualifiers = {Language.English.KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String englishNounLemma = "secondary";
    }

    @Item(key = Accent.KEY)
    public static final class Accent {
        public static final String KEY = "cg.presentation:accent";
        private Accent() {}

        @Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss = "accent color for emphasis and call-to-action elements";

        @Frame(predicate = LexicalVocabulary.Lexeme.KEY,
          field = @Binding(role = ThematicRole.Value.KEY,
            qualifiers = {Language.English.KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String englishNounLemma = "accent";
    }

    @Item(key = Surface.KEY)
    public static final class Surface {
        public static final String KEY = "cg.presentation:surface";
        private Surface() {}

        @Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss = "background surface color for content areas";

        @Frame(predicate = LexicalVocabulary.Lexeme.KEY,
          field = @Binding(role = ThematicRole.Value.KEY,
            qualifiers = {Language.English.KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String englishNounLemma = "surface";
    }

    @Item(key = OnPrimary.KEY)
    public static final class OnPrimary {
        public static final String KEY = "cg.presentation:on-primary";
        private OnPrimary() {}

        @Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss =
                "text and icon color used on primary-colored backgrounds";
    }

    @Item(key = OnSurface.KEY)
    public static final class OnSurface {
        public static final String KEY = "cg.presentation:on-surface";
        private OnSurface() {}

        @Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss =
                "text and icon color used on surface-colored backgrounds";
    }

    @Item(key = Error.KEY)
    public static final class Error {
        public static final String KEY = "cg.presentation:error";
        private Error() {}

        @Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss = "color indicating error or destructive state";

        @Frame(predicate = LexicalVocabulary.Lexeme.KEY,
          field = @Binding(role = ThematicRole.Value.KEY,
            qualifiers = {Language.English.KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String englishNounLemma = "error";
    }

    @Item(key = Outline.KEY)
    public static final class Outline {
        public static final String KEY = "cg.presentation:outline";
        private Outline() {}

        @Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss = "color for borders, dividers, and outlines";

        @Frame(predicate = LexicalVocabulary.Lexeme.KEY,
          field = @Binding(role = ThematicRole.Value.KEY,
            qualifiers = {Language.English.KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String englishNounLemma = "outline";
    }

    @Item(key = Muted.KEY)
    public static final class Muted {
        public static final String KEY = "cg.presentation:muted";
        private Muted() {}

        @Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss = "subdued color for disabled or low-emphasis elements";

        @Frame(predicate = LexicalVocabulary.Lexeme.KEY,
          field = @Binding(role = ThematicRole.Value.KEY,
            qualifiers = {Language.English.KEY, PartOfSpeech.Adjective.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String englishAdjectiveLemma = "muted";
    }
}
