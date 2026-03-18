package dev.everydaythings.graph.language;

import dev.everydaythings.graph.item.ItemSeed;
import dev.everydaythings.graph.item.id.ItemID;

/**
 * Seed vocabulary for colors — universal concepts with actual color values.
 *
 * <p>Each color Sememe carries a hex symbol ("#FF0000") as its language-neutral
 * representation and a {@code VALUE} constant for programmatic use.
 *
 * <p>These are the <em>colors themselves</em>, not game sides. Chess "white"
 * ({@link dev.everydaythings.graph.game.GameVocabulary.White}) is a game concept
 * associated with the color white but conceptually distinct.
 *
 * @see CoreVocabulary for core predicates and verbs
 * @see LexicalVocabulary for semantic relations
 */
public final class ColorVocabulary {

    private ColorVocabulary() {}


    // ==================================================================================
    // ACHROMATIC
    // ==================================================================================

    @ItemSeed(key = White.KEY)
    public static class White {
        public static final String KEY = "cg:color/white";
        public static final ItemID IID = ItemID.fromString(KEY);
        public static final int VALUE = 0xFFFFFF;        @ItemSeed.Frame(key = {SememeGloss.KEY, Language.ENGLISH_KEY})
        static final String gloss = "the color white; achromatic color of maximum lightness";

        @ItemSeed.Word(lang = Language.ENGLISH_KEY, pos = PartOfSpeech.Noun.KEY, features = {GrammaticalFeature.Lemma.KEY})
        static final String noun1 = "white";

        @ItemSeed.Frame(key = {CoreVocabulary.Symbol.KEY})
        static final String symbol = "#FFFFFF";
    }

    @ItemSeed(key = Black.KEY)
    public static class Black {
        public static final String KEY = "cg:color/black";
        public static final ItemID IID = ItemID.fromString(KEY);
        public static final int VALUE = 0x000000;        @ItemSeed.Frame(key = {SememeGloss.KEY, Language.ENGLISH_KEY})
        static final String gloss = "the color black; achromatic color of minimum lightness";

        @ItemSeed.Word(lang = Language.ENGLISH_KEY, pos = PartOfSpeech.Noun.KEY, features = {GrammaticalFeature.Lemma.KEY})
        static final String noun1 = "black";

        @ItemSeed.Frame(key = {CoreVocabulary.Symbol.KEY})
        static final String symbol = "#000000";
    }

    @ItemSeed(key = Gray.KEY)
    public static class Gray {
        public static final String KEY = "cg:color/gray";
        public static final ItemID IID = ItemID.fromString(KEY);
        public static final int VALUE = 0x808080;        @ItemSeed.Frame(key = {SememeGloss.KEY, Language.ENGLISH_KEY})
        static final String gloss = "the color gray; neutral midtone between black and white";

        @ItemSeed.Word(lang = Language.ENGLISH_KEY, pos = PartOfSpeech.Noun.KEY, features = {GrammaticalFeature.Lemma.KEY})
        static final String noun1 = "gray";

        @ItemSeed.Word(lang = Language.ENGLISH_KEY, pos = PartOfSpeech.Noun.KEY, features = {GrammaticalFeature.Lemma.KEY})
        static final String noun2 = "grey";

        @ItemSeed.Frame(key = {CoreVocabulary.Symbol.KEY})
        static final String symbol = "#808080";
    }

    // ==================================================================================
    // PRIMARY
    // ==================================================================================

    @ItemSeed(key = Red.KEY)
    public static class Red {
        public static final String KEY = "cg:color/red";
        public static final ItemID IID = ItemID.fromString(KEY);
        public static final int VALUE = 0xFF0000;        @ItemSeed.Frame(key = {SememeGloss.KEY, Language.ENGLISH_KEY})
        static final String gloss = "the color red";

        @ItemSeed.Word(lang = Language.ENGLISH_KEY, pos = PartOfSpeech.Noun.KEY, features = {GrammaticalFeature.Lemma.KEY})
        static final String noun1 = "red";

        @ItemSeed.Frame(key = {CoreVocabulary.Symbol.KEY})
        static final String symbol = "#FF0000";
    }

    @ItemSeed(key = Green.KEY)
    public static class Green {
        public static final String KEY = "cg:color/green";
        public static final ItemID IID = ItemID.fromString(KEY);
        public static final int VALUE = 0x00FF00;        @ItemSeed.Frame(key = {SememeGloss.KEY, Language.ENGLISH_KEY})
        static final String gloss = "the color green";

        @ItemSeed.Word(lang = Language.ENGLISH_KEY, pos = PartOfSpeech.Noun.KEY, features = {GrammaticalFeature.Lemma.KEY})
        static final String noun1 = "green";

        @ItemSeed.Frame(key = {CoreVocabulary.Symbol.KEY})
        static final String symbol = "#00FF00";
    }

    @ItemSeed(key = Blue.KEY)
    public static class Blue {
        public static final String KEY = "cg:color/blue";
        public static final ItemID IID = ItemID.fromString(KEY);
        public static final int VALUE = 0x0000FF;        @ItemSeed.Frame(key = {SememeGloss.KEY, Language.ENGLISH_KEY})
        static final String gloss = "the color blue";

        @ItemSeed.Word(lang = Language.ENGLISH_KEY, pos = PartOfSpeech.Noun.KEY, features = {GrammaticalFeature.Lemma.KEY})
        static final String noun1 = "blue";

        @ItemSeed.Frame(key = {CoreVocabulary.Symbol.KEY})
        static final String symbol = "#0000FF";
    }

    // ==================================================================================
    // SECONDARY
    // ==================================================================================

    @ItemSeed(key = Yellow.KEY)
    public static class Yellow {
        public static final String KEY = "cg:color/yellow";
        public static final ItemID IID = ItemID.fromString(KEY);
        public static final int VALUE = 0xFFFF00;        @ItemSeed.Frame(key = {SememeGloss.KEY, Language.ENGLISH_KEY})
        static final String gloss = "the color yellow";

        @ItemSeed.Word(lang = Language.ENGLISH_KEY, pos = PartOfSpeech.Noun.KEY, features = {GrammaticalFeature.Lemma.KEY})
        static final String noun1 = "yellow";

        @ItemSeed.Frame(key = {CoreVocabulary.Symbol.KEY})
        static final String symbol = "#FFFF00";
    }

    @ItemSeed(key = Cyan.KEY)
    public static class Cyan {
        public static final String KEY = "cg:color/cyan";
        public static final ItemID IID = ItemID.fromString(KEY);
        public static final int VALUE = 0x00FFFF;        @ItemSeed.Frame(key = {SememeGloss.KEY, Language.ENGLISH_KEY})
        static final String gloss = "the color cyan";

        @ItemSeed.Word(lang = Language.ENGLISH_KEY, pos = PartOfSpeech.Noun.KEY, features = {GrammaticalFeature.Lemma.KEY})
        static final String noun1 = "cyan";

        @ItemSeed.Word(lang = Language.ENGLISH_KEY, pos = PartOfSpeech.Noun.KEY, features = {GrammaticalFeature.Lemma.KEY})
        static final String noun2 = "aqua";

        @ItemSeed.Frame(key = {CoreVocabulary.Symbol.KEY})
        static final String symbol = "#00FFFF";
    }

    @ItemSeed(key = Magenta.KEY)
    public static class Magenta {
        public static final String KEY = "cg:color/magenta";
        public static final ItemID IID = ItemID.fromString(KEY);
        public static final int VALUE = 0xFF00FF;        @ItemSeed.Frame(key = {SememeGloss.KEY, Language.ENGLISH_KEY})
        static final String gloss = "the color magenta";

        @ItemSeed.Word(lang = Language.ENGLISH_KEY, pos = PartOfSpeech.Noun.KEY, features = {GrammaticalFeature.Lemma.KEY})
        static final String noun1 = "magenta";

        @ItemSeed.Word(lang = Language.ENGLISH_KEY, pos = PartOfSpeech.Noun.KEY, features = {GrammaticalFeature.Lemma.KEY})
        static final String noun2 = "fuchsia";

        @ItemSeed.Frame(key = {CoreVocabulary.Symbol.KEY})
        static final String symbol = "#FF00FF";
    }

    // ==================================================================================
    // TERTIARY / COMMON
    // ==================================================================================

    @ItemSeed(key = Orange.KEY)
    public static class Orange {
        public static final String KEY = "cg:color/orange";
        public static final ItemID IID = ItemID.fromString(KEY);
        public static final int VALUE = 0xFF8000;        @ItemSeed.Frame(key = {SememeGloss.KEY, Language.ENGLISH_KEY})
        static final String gloss = "the color orange";

        @ItemSeed.Word(lang = Language.ENGLISH_KEY, pos = PartOfSpeech.Noun.KEY, features = {GrammaticalFeature.Lemma.KEY})
        static final String noun1 = "orange";

        @ItemSeed.Frame(key = {CoreVocabulary.Symbol.KEY})
        static final String symbol = "#FF8000";
    }

    @ItemSeed(key = Purple.KEY)
    public static class Purple {
        public static final String KEY = "cg:color/purple";
        public static final ItemID IID = ItemID.fromString(KEY);
        public static final int VALUE = 0x800080;        @ItemSeed.Frame(key = {SememeGloss.KEY, Language.ENGLISH_KEY})
        static final String gloss = "the color purple";

        @ItemSeed.Word(lang = Language.ENGLISH_KEY, pos = PartOfSpeech.Noun.KEY, features = {GrammaticalFeature.Lemma.KEY})
        static final String noun1 = "purple";

        @ItemSeed.Word(lang = Language.ENGLISH_KEY, pos = PartOfSpeech.Noun.KEY, features = {GrammaticalFeature.Lemma.KEY})
        static final String noun2 = "violet";

        @ItemSeed.Frame(key = {CoreVocabulary.Symbol.KEY})
        static final String symbol = "#800080";
    }

    @ItemSeed(key = Pink.KEY)
    public static class Pink {
        public static final String KEY = "cg:color/pink";
        public static final ItemID IID = ItemID.fromString(KEY);
        public static final int VALUE = 0xFFC0CB;        @ItemSeed.Frame(key = {SememeGloss.KEY, Language.ENGLISH_KEY})
        static final String gloss = "the color pink";

        @ItemSeed.Word(lang = Language.ENGLISH_KEY, pos = PartOfSpeech.Noun.KEY, features = {GrammaticalFeature.Lemma.KEY})
        static final String noun1 = "pink";

        @ItemSeed.Frame(key = {CoreVocabulary.Symbol.KEY})
        static final String symbol = "#FFC0CB";
    }

    @ItemSeed(key = Brown.KEY)
    public static class Brown {
        public static final String KEY = "cg:color/brown";
        public static final ItemID IID = ItemID.fromString(KEY);
        public static final int VALUE = 0x8B4513;        @ItemSeed.Frame(key = {SememeGloss.KEY, Language.ENGLISH_KEY})
        static final String gloss = "the color brown";

        @ItemSeed.Word(lang = Language.ENGLISH_KEY, pos = PartOfSpeech.Noun.KEY, features = {GrammaticalFeature.Lemma.KEY})
        static final String noun1 = "brown";

        @ItemSeed.Frame(key = {CoreVocabulary.Symbol.KEY})
        static final String symbol = "#8B4513";
    }

    // ==================================================================================
    // Utility
    // ==================================================================================

    private static String hex(int rgb) {
        return String.format("#%06X", rgb);
    }
}
