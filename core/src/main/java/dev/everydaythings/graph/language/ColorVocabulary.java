package dev.everydaythings.graph.language;

import dev.everydaythings.graph.frame.ItemFrame;
import dev.everydaythings.graph.frame.ItemFrame.Bind;
import dev.everydaythings.graph.item.ItemSeed;
import dev.everydaythings.graph.item.id.ItemID;
import dev.everydaythings.graph.language.CoreVocabulary;

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
        public static final int VALUE = 0xFFFFFF;
        @ItemFrame(predicate = SememeGloss.KEY,
                   fieldAs = @Bind(role = ThematicRole.Name.KEY, qualifiers = {Language.ENGLISH_KEY}))
        static final String gloss = "the color white; achromatic color of maximum lightness";

        @ItemFrame(predicate = CoreVocabulary.Lexeme.KEY,
                   fieldAs = @Bind(role = ThematicRole.Name.KEY,
                                   qualifiers = {Language.ENGLISH_KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String[] words = {"white"};

        @ItemFrame(predicate = CoreVocabulary.Symbol.KEY)
        static final String symbol = "#FFFFFF";
    }

    @ItemSeed(key = Black.KEY)
    public static class Black {
        public static final String KEY = "cg:color/black";
        public static final ItemID IID = ItemID.fromString(KEY);
        public static final int VALUE = 0x000000;
        @ItemFrame(predicate = SememeGloss.KEY,
                   fieldAs = @Bind(role = ThematicRole.Name.KEY, qualifiers = {Language.ENGLISH_KEY}))
        static final String gloss = "the color black; achromatic color of minimum lightness";

        @ItemFrame(predicate = CoreVocabulary.Lexeme.KEY,
                   fieldAs = @Bind(role = ThematicRole.Name.KEY,
                                   qualifiers = {Language.ENGLISH_KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String[] words = {"black"};

        @ItemFrame(predicate = CoreVocabulary.Symbol.KEY)
        static final String symbol = "#000000";
    }

    @ItemSeed(key = Gray.KEY)
    public static class Gray {
        public static final String KEY = "cg:color/gray";
        public static final ItemID IID = ItemID.fromString(KEY);
        public static final int VALUE = 0x808080;
        @ItemFrame(predicate = SememeGloss.KEY,
                   fieldAs = @Bind(role = ThematicRole.Name.KEY, qualifiers = {Language.ENGLISH_KEY}))
        static final String gloss = "the color gray; neutral midtone between black and white";

        @ItemFrame(predicate = CoreVocabulary.Lexeme.KEY,
                   fieldAs = @Bind(role = ThematicRole.Name.KEY,
                                   qualifiers = {Language.ENGLISH_KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String[] words = {"gray", "grey"};

        @ItemFrame(predicate = CoreVocabulary.Symbol.KEY)
        static final String symbol = "#808080";
    }

    // ==================================================================================
    // PRIMARY
    // ==================================================================================

    @ItemSeed(key = Red.KEY)
    public static class Red {
        public static final String KEY = "cg:color/red";
        public static final ItemID IID = ItemID.fromString(KEY);
        public static final int VALUE = 0xFF0000;
        @ItemFrame(predicate = SememeGloss.KEY,
                   fieldAs = @Bind(role = ThematicRole.Name.KEY, qualifiers = {Language.ENGLISH_KEY}))
        static final String gloss = "the color red";

        @ItemFrame(predicate = CoreVocabulary.Lexeme.KEY,
                   fieldAs = @Bind(role = ThematicRole.Name.KEY,
                                   qualifiers = {Language.ENGLISH_KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String[] words = {"red"};

        @ItemFrame(predicate = CoreVocabulary.Symbol.KEY)
        static final String symbol = "#FF0000";
    }

    @ItemSeed(key = Green.KEY)
    public static class Green {
        public static final String KEY = "cg:color/green";
        public static final ItemID IID = ItemID.fromString(KEY);
        public static final int VALUE = 0x00FF00;
        @ItemFrame(predicate = SememeGloss.KEY,
                   fieldAs = @Bind(role = ThematicRole.Name.KEY, qualifiers = {Language.ENGLISH_KEY}))
        static final String gloss = "the color green";

        @ItemFrame(predicate = CoreVocabulary.Lexeme.KEY,
                   fieldAs = @Bind(role = ThematicRole.Name.KEY,
                                   qualifiers = {Language.ENGLISH_KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String[] words = {"green"};

        @ItemFrame(predicate = CoreVocabulary.Symbol.KEY)
        static final String symbol = "#00FF00";
    }

    @ItemSeed(key = Blue.KEY)
    public static class Blue {
        public static final String KEY = "cg:color/blue";
        public static final ItemID IID = ItemID.fromString(KEY);
        public static final int VALUE = 0x0000FF;
        @ItemFrame(predicate = SememeGloss.KEY,
                   fieldAs = @Bind(role = ThematicRole.Name.KEY, qualifiers = {Language.ENGLISH_KEY}))
        static final String gloss = "the color blue";

        @ItemFrame(predicate = CoreVocabulary.Lexeme.KEY,
                   fieldAs = @Bind(role = ThematicRole.Name.KEY,
                                   qualifiers = {Language.ENGLISH_KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String[] words = {"blue"};

        @ItemFrame(predicate = CoreVocabulary.Symbol.KEY)
        static final String symbol = "#0000FF";
    }

    // ==================================================================================
    // SECONDARY
    // ==================================================================================

    @ItemSeed(key = Yellow.KEY)
    public static class Yellow {
        public static final String KEY = "cg:color/yellow";
        public static final ItemID IID = ItemID.fromString(KEY);
        public static final int VALUE = 0xFFFF00;
        @ItemFrame(predicate = SememeGloss.KEY,
                   fieldAs = @Bind(role = ThematicRole.Name.KEY, qualifiers = {Language.ENGLISH_KEY}))
        static final String gloss = "the color yellow";

        @ItemFrame(predicate = CoreVocabulary.Lexeme.KEY,
                   fieldAs = @Bind(role = ThematicRole.Name.KEY,
                                   qualifiers = {Language.ENGLISH_KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String[] words = {"yellow"};

        @ItemFrame(predicate = CoreVocabulary.Symbol.KEY)
        static final String symbol = "#FFFF00";
    }

    @ItemSeed(key = Cyan.KEY)
    public static class Cyan {
        public static final String KEY = "cg:color/cyan";
        public static final ItemID IID = ItemID.fromString(KEY);
        public static final int VALUE = 0x00FFFF;
        @ItemFrame(predicate = SememeGloss.KEY,
                   fieldAs = @Bind(role = ThematicRole.Name.KEY, qualifiers = {Language.ENGLISH_KEY}))
        static final String gloss = "the color cyan";

        @ItemFrame(predicate = CoreVocabulary.Lexeme.KEY,
                   fieldAs = @Bind(role = ThematicRole.Name.KEY,
                                   qualifiers = {Language.ENGLISH_KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String[] words = {"cyan", "aqua"};

        @ItemFrame(predicate = CoreVocabulary.Symbol.KEY)
        static final String symbol = "#00FFFF";
    }

    @ItemSeed(key = Magenta.KEY)
    public static class Magenta {
        public static final String KEY = "cg:color/magenta";
        public static final ItemID IID = ItemID.fromString(KEY);
        public static final int VALUE = 0xFF00FF;
        @ItemFrame(predicate = SememeGloss.KEY,
                   fieldAs = @Bind(role = ThematicRole.Name.KEY, qualifiers = {Language.ENGLISH_KEY}))
        static final String gloss = "the color magenta";

        @ItemFrame(predicate = CoreVocabulary.Lexeme.KEY,
                   fieldAs = @Bind(role = ThematicRole.Name.KEY,
                                   qualifiers = {Language.ENGLISH_KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String[] words = {"magenta", "fuchsia"};

        @ItemFrame(predicate = CoreVocabulary.Symbol.KEY)
        static final String symbol = "#FF00FF";
    }

    // ==================================================================================
    // TERTIARY / COMMON
    // ==================================================================================

    @ItemSeed(key = Orange.KEY)
    public static class Orange {
        public static final String KEY = "cg:color/orange";
        public static final ItemID IID = ItemID.fromString(KEY);
        public static final int VALUE = 0xFF8000;
        @ItemFrame(predicate = SememeGloss.KEY,
                   fieldAs = @Bind(role = ThematicRole.Name.KEY, qualifiers = {Language.ENGLISH_KEY}))
        static final String gloss = "the color orange";

        @ItemFrame(predicate = CoreVocabulary.Lexeme.KEY,
                   fieldAs = @Bind(role = ThematicRole.Name.KEY,
                                   qualifiers = {Language.ENGLISH_KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String[] words = {"orange"};

        @ItemFrame(predicate = CoreVocabulary.Symbol.KEY)
        static final String symbol = "#FF8000";
    }

    @ItemSeed(key = Purple.KEY)
    public static class Purple {
        public static final String KEY = "cg:color/purple";
        public static final ItemID IID = ItemID.fromString(KEY);
        public static final int VALUE = 0x800080;
        @ItemFrame(predicate = SememeGloss.KEY,
                   fieldAs = @Bind(role = ThematicRole.Name.KEY, qualifiers = {Language.ENGLISH_KEY}))
        static final String gloss = "the color purple";

        @ItemFrame(predicate = CoreVocabulary.Lexeme.KEY,
                   fieldAs = @Bind(role = ThematicRole.Name.KEY,
                                   qualifiers = {Language.ENGLISH_KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String[] words = {"purple", "violet"};

        @ItemFrame(predicate = CoreVocabulary.Symbol.KEY)
        static final String symbol = "#800080";
    }

    @ItemSeed(key = Pink.KEY)
    public static class Pink {
        public static final String KEY = "cg:color/pink";
        public static final ItemID IID = ItemID.fromString(KEY);
        public static final int VALUE = 0xFFC0CB;
        @ItemFrame(predicate = SememeGloss.KEY,
                   fieldAs = @Bind(role = ThematicRole.Name.KEY, qualifiers = {Language.ENGLISH_KEY}))
        static final String gloss = "the color pink";

        @ItemFrame(predicate = CoreVocabulary.Lexeme.KEY,
                   fieldAs = @Bind(role = ThematicRole.Name.KEY,
                                   qualifiers = {Language.ENGLISH_KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String[] words = {"pink"};

        @ItemFrame(predicate = CoreVocabulary.Symbol.KEY)
        static final String symbol = "#FFC0CB";
    }

    @ItemSeed(key = Brown.KEY)
    public static class Brown {
        public static final String KEY = "cg:color/brown";
        public static final ItemID IID = ItemID.fromString(KEY);
        public static final int VALUE = 0x8B4513;
        @ItemFrame(predicate = SememeGloss.KEY,
                   fieldAs = @Bind(role = ThematicRole.Name.KEY, qualifiers = {Language.ENGLISH_KEY}))
        static final String gloss = "the color brown";

        @ItemFrame(predicate = CoreVocabulary.Lexeme.KEY,
                   fieldAs = @Bind(role = ThematicRole.Name.KEY,
                                   qualifiers = {Language.ENGLISH_KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String[] words = {"brown"};

        @ItemFrame(predicate = CoreVocabulary.Symbol.KEY)
        static final String symbol = "#8B4513";
    }

    // ==================================================================================
    // Utility
    // ==================================================================================

    private static String hex(int rgb) {
        return String.format("#%06X", rgb);
    }
}
