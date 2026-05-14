package dev.everydaythings.graph;

import dev.everydaythings.graph.id.ItemID;
import dev.everydaythings.graph.linguistics.GrammaticalFeature;
import dev.everydaythings.graph.linguistics.Gloss;
import dev.everydaythings.graph.linguistics.Language;
import dev.everydaythings.graph.linguistics.Lexeme;
import dev.everydaythings.graph.linguistics.PartOfSpeech;
import dev.everydaythings.graph.semantics.ThematicRole;
import static dev.everydaythings.graph.Seed.*;

/**
 * Presentation vocabulary — palette-slot sememes that name the semantic role
 * a color (or other visual treatment) plays in a UI.
 *
 * <p>"Primary," "Secondary," "Accent," "Surface," etc. are language-neutral
 * roles a UI assigns concrete colors to. The theming layer maps each slot to
 * a {@link ColorVocabulary} value (or to a user-defined override) at render
 * time.
 */
public final class PresentationVocabulary {

    private PresentationVocabulary() {}

    @Seed.Item(key = Primary.KEY)
    public static final class Primary {
        public static final String KEY = "cg.presentation:primary";
        public static final ItemID IID = ItemID.fromString(KEY);
        private Primary() {}

        @Frame(predicate = Gloss.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss = "primary brand color for prominent UI elements";

        @Frame(predicate = Lexeme.KEY,
          field = @Binding(role = ThematicRole.Value.KEY,
            qualifiers = {Language.English.KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String englishNounLemma = "primary";
    }

    @Seed.Item(key = Secondary.KEY)
    public static final class Secondary {
        public static final String KEY = "cg.presentation:secondary";
        public static final ItemID IID = ItemID.fromString(KEY);
        private Secondary() {}

        @Frame(predicate = Gloss.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss = "secondary brand color for supporting UI elements";

        @Frame(predicate = Lexeme.KEY,
          field = @Binding(role = ThematicRole.Value.KEY,
            qualifiers = {Language.English.KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String englishNounLemma = "secondary";
    }

    @Seed.Item(key = Accent.KEY)
    public static final class Accent {
        public static final String KEY = "cg.presentation:accent";
        public static final ItemID IID = ItemID.fromString(KEY);
        private Accent() {}

        @Frame(predicate = Gloss.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss = "accent color for emphasis and call-to-action elements";

        @Frame(predicate = Lexeme.KEY,
          field = @Binding(role = ThematicRole.Value.KEY,
            qualifiers = {Language.English.KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String englishNounLemma = "accent";
    }

    @Seed.Item(key = Surface.KEY)
    public static final class Surface {
        public static final String KEY = "cg.presentation:surface";
        public static final ItemID IID = ItemID.fromString(KEY);
        private Surface() {}

        @Frame(predicate = Gloss.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss = "background surface color for content areas";

        @Frame(predicate = Lexeme.KEY,
          field = @Binding(role = ThematicRole.Value.KEY,
            qualifiers = {Language.English.KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String englishNounLemma = "surface";
    }

    @Seed.Item(key = OnPrimary.KEY)
    public static final class OnPrimary {
        public static final String KEY = "cg.presentation:on-primary";
        public static final ItemID IID = ItemID.fromString(KEY);
        private OnPrimary() {}

        @Frame(predicate = Gloss.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss =
                "text and icon color used on primary-colored backgrounds";
    }

    @Seed.Item(key = OnSurface.KEY)
    public static final class OnSurface {
        public static final String KEY = "cg.presentation:on-surface";
        public static final ItemID IID = ItemID.fromString(KEY);
        private OnSurface() {}

        @Frame(predicate = Gloss.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss =
                "text and icon color used on surface-colored backgrounds";
    }

    @Seed.Item(key = Error.KEY)
    public static final class Error {
        public static final String KEY = "cg.presentation:error";
        public static final ItemID IID = ItemID.fromString(KEY);
        private Error() {}

        @Frame(predicate = Gloss.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss = "color indicating error or destructive state";

        @Frame(predicate = Lexeme.KEY,
          field = @Binding(role = ThematicRole.Value.KEY,
            qualifiers = {Language.English.KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String englishNounLemma = "error";
    }

    @Seed.Item(key = Outline.KEY)
    public static final class Outline {
        public static final String KEY = "cg.presentation:outline";
        public static final ItemID IID = ItemID.fromString(KEY);
        private Outline() {}

        @Frame(predicate = Gloss.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss = "color for borders, dividers, and outlines";

        @Frame(predicate = Lexeme.KEY,
          field = @Binding(role = ThematicRole.Value.KEY,
            qualifiers = {Language.English.KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String englishNounLemma = "outline";
    }

    @Seed.Item(key = Muted.KEY)
    public static final class Muted {
        public static final String KEY = "cg.presentation:muted";
        public static final ItemID IID = ItemID.fromString(KEY);
        private Muted() {}

        @Frame(predicate = Gloss.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss = "subdued color for disabled or low-emphasis elements";

        @Frame(predicate = Lexeme.KEY,
          field = @Binding(role = ThematicRole.Value.KEY,
            qualifiers = {Language.English.KEY, PartOfSpeech.Adjective.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String englishAdjectiveLemma = "muted";
    }
}
