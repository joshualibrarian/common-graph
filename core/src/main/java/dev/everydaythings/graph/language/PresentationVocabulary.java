package dev.everydaythings.graph.language;

import dev.everydaythings.graph.item.ItemSeed;
import dev.everydaythings.graph.item.id.ItemID;

/**
 * Seed vocabulary for semantic presentation tokens (palette slots).
 *
 * <p>These sememes name palette slots used in the presentation cascade.
 * Actual color values are bound in {@link dev.everydaythings.graph.frame.PresentationConfig}
 * payloads stored in {@code (PRESENTATION)} config entries on individual
 * items, implementations, or sememes.
 *
 * @see dev.everydaythings.graph.frame.PresentationConfig
 * @see CoreVocabulary
 */
public final class PresentationVocabulary {

    private PresentationVocabulary() {}


    @ItemSeed(key = Primary.KEY)
    public static class Primary {
        public static final String KEY = "cg.presentation:primary";
        public static final ItemID IID = ItemID.fromString(KEY);
        @ItemSeed.Frame(key = {SememeGloss.KEY, Language.ENGLISH_KEY})
        static final String gloss = "primary brand color for prominent UI elements";

        @ItemSeed.Word(lang = Language.ENGLISH_KEY, pos = PartOfSpeech.Noun.KEY, features = {GrammaticalFeature.Lemma.KEY})
        static final String noun1 = "primary";
    }

    @ItemSeed(key = Secondary.KEY)
    public static class Secondary {
        public static final String KEY = "cg.presentation:secondary";
        public static final ItemID IID = ItemID.fromString(KEY);
        @ItemSeed.Frame(key = {SememeGloss.KEY, Language.ENGLISH_KEY})
        static final String gloss = "secondary brand color for supporting UI elements";

        @ItemSeed.Word(lang = Language.ENGLISH_KEY, pos = PartOfSpeech.Noun.KEY, features = {GrammaticalFeature.Lemma.KEY})
        static final String noun1 = "secondary";
    }

    @ItemSeed(key = Accent.KEY)
    public static class Accent {
        public static final String KEY = "cg.presentation:accent";
        public static final ItemID IID = ItemID.fromString(KEY);
        @ItemSeed.Frame(key = {SememeGloss.KEY, Language.ENGLISH_KEY})
        static final String gloss = "accent color for emphasis and call-to-action elements";

        @ItemSeed.Word(lang = Language.ENGLISH_KEY, pos = PartOfSpeech.Noun.KEY, features = {GrammaticalFeature.Lemma.KEY})
        static final String noun1 = "accent";
    }

    @ItemSeed(key = Surface.KEY)
    public static class Surface {
        public static final String KEY = "cg.presentation:surface";
        public static final ItemID IID = ItemID.fromString(KEY);
        @ItemSeed.Frame(key = {SememeGloss.KEY, Language.ENGLISH_KEY})
        static final String gloss = "background surface color for content areas";

        @ItemSeed.Word(lang = Language.ENGLISH_KEY, pos = PartOfSpeech.Noun.KEY, features = {GrammaticalFeature.Lemma.KEY})
        static final String noun1 = "surface";
    }

    @ItemSeed(key = OnPrimary.KEY)
    public static class OnPrimary {
        public static final String KEY = "cg.presentation:on-primary";
        public static final ItemID IID = ItemID.fromString(KEY);
        @ItemSeed.Frame(key = {SememeGloss.KEY, Language.ENGLISH_KEY})
        static final String gloss = "text and icon color used on primary-colored backgrounds";

        @ItemSeed.Word(lang = Language.ENGLISH_KEY, pos = PartOfSpeech.Noun.KEY, features = {GrammaticalFeature.Lemma.KEY})
        static final String noun1 = "on-primary";
    }

    @ItemSeed(key = OnSurface.KEY)
    public static class OnSurface {
        public static final String KEY = "cg.presentation:on-surface";
        public static final ItemID IID = ItemID.fromString(KEY);
        @ItemSeed.Frame(key = {SememeGloss.KEY, Language.ENGLISH_KEY})
        static final String gloss = "text and icon color used on surface-colored backgrounds";

        @ItemSeed.Word(lang = Language.ENGLISH_KEY, pos = PartOfSpeech.Noun.KEY, features = {GrammaticalFeature.Lemma.KEY})
        static final String noun1 = "on-surface";
    }

    @ItemSeed(key = Error.KEY)
    public static class Error {
        public static final String KEY = "cg.presentation:error";
        public static final ItemID IID = ItemID.fromString(KEY);
        @ItemSeed.Frame(key = {SememeGloss.KEY, Language.ENGLISH_KEY})
        static final String gloss = "color indicating error or destructive state";

        @ItemSeed.Word(lang = Language.ENGLISH_KEY, pos = PartOfSpeech.Noun.KEY, features = {GrammaticalFeature.Lemma.KEY})
        static final String noun1 = "error";
    }

    @ItemSeed(key = Outline.KEY)
    public static class Outline {
        public static final String KEY = "cg.presentation:outline";
        public static final ItemID IID = ItemID.fromString(KEY);
        @ItemSeed.Frame(key = {SememeGloss.KEY, Language.ENGLISH_KEY})
        static final String gloss = "color for borders, dividers, and outlines";

        @ItemSeed.Word(lang = Language.ENGLISH_KEY, pos = PartOfSpeech.Noun.KEY, features = {GrammaticalFeature.Lemma.KEY})
        static final String noun1 = "outline";
    }

    @ItemSeed(key = Muted.KEY)
    public static class Muted {
        public static final String KEY = "cg.presentation:muted";
        public static final ItemID IID = ItemID.fromString(KEY);
        @ItemSeed.Frame(key = {SememeGloss.KEY, Language.ENGLISH_KEY})
        static final String gloss = "subdued color for disabled or low-emphasis elements";

        @ItemSeed.Word(lang = Language.ENGLISH_KEY, pos = PartOfSpeech.Noun.KEY, features = {GrammaticalFeature.Lemma.KEY})
        static final String noun1 = "muted";
    }
}
