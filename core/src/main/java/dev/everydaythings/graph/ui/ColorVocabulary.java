package dev.everydaythings.graph.ui;

import dev.everydaythings.graph.CoreVocabulary;
import dev.everydaythings.graph.id.ItemRef;
import dev.everydaythings.graph.language.*;

import static dev.everydaythings.graph.Seed.*;

/**
 * Color vocabulary — sememes for universal color concepts, with hex symbol
 * frames and programmatic RGB constants.
 *
 * <p>Each color carries a {@link Symbol} frame holding its hex string and a
 * Java-level {@code RGB} constant for programmatic use.
 *
 * <p>These are the <em>colors themselves</em>, language-neutral. Domain
 * concepts that are <i>associated</i> with a color (Chess's "white" side,
 * GUI palette slots) live in their own vocabularies and reference these.
 */
public final class ColorVocabulary {

    private ColorVocabulary() {}

    /**
     * The SYMBOL predicate — carries a language-neutral symbolic representation
     * (hex string, mathematical glyph, unit symbol, etc.) for a sememe.
     */
    @Item(key = Symbol.KEY, head = CoreVocabulary.Predicate.KEY)
    public static final class Symbol {
        public static final String KEY = "cg.core:symbol";
        public static final ItemRef IID = ItemRef.fromString(KEY);
        private Symbol() {}

        @Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss =
                "language-neutral symbolic representation of a sememe — "
                        + "a hex string, mathematical glyph, unit symbol, etc.";
    }

    // ==================================================================================
    // ACHROMATIC
    // ==================================================================================

    @Item(key = White.KEY)
    public static final class White {
        public static final String KEY = "cg:color/white";
        public static final ItemRef IID = ItemRef.fromString(KEY);
        public static final int RGB = 0xFFFFFF;
        private White() {}

        @Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss = "achromatic color of maximum lightness";

        @Frame(predicate = LexicalVocabulary.Lexeme.KEY,
          field = @Binding(role = ThematicRole.Value.KEY,
            qualifiers = {Language.English.KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String englishNounLemma = "white";

        @Frame(predicate = Symbol.KEY,
          field = @Binding(role = ThematicRole.Value.KEY))
        static final String symbol = "#FFFFFF";
    }

    @Item(key = Black.KEY)
    public static final class Black {
        public static final String KEY = "cg:color/black";
        public static final ItemRef IID = ItemRef.fromString(KEY);
        public static final int RGB = 0x000000;
        private Black() {}

        @Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss = "achromatic color of minimum lightness";

        @Frame(predicate = LexicalVocabulary.Lexeme.KEY,
          field = @Binding(role = ThematicRole.Value.KEY,
            qualifiers = {Language.English.KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String englishNounLemma = "black";

        @Frame(predicate = Symbol.KEY,
          field = @Binding(role = ThematicRole.Value.KEY))
        static final String symbol = "#000000";
    }

    @Item(key = Gray.KEY)
    public static final class Gray {
        public static final String KEY = "cg:color/gray";
        public static final ItemRef IID = ItemRef.fromString(KEY);
        public static final int RGB = 0x808080;
        private Gray() {}

        @Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss = "neutral midtone between black and white";

        @Frame(predicate = LexicalVocabulary.Lexeme.KEY,
          field = @Binding(role = ThematicRole.Value.KEY,
            qualifiers = {Language.English.KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String englishNounLemma = "gray";

        @Frame(predicate = Symbol.KEY,
          field = @Binding(role = ThematicRole.Value.KEY))
        static final String symbol = "#808080";
    }

    // ==================================================================================
    // PRIMARY
    // ==================================================================================

    @Item(key = Red.KEY)
    public static final class Red {
        public static final String KEY = "cg:color/red";
        public static final ItemRef IID = ItemRef.fromString(KEY);
        public static final int RGB = 0xFF0000;
        private Red() {}

        @Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss = "the color red";

        @Frame(predicate = LexicalVocabulary.Lexeme.KEY,
          field = @Binding(role = ThematicRole.Value.KEY,
            qualifiers = {Language.English.KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String englishNounLemma = "red";

        @Frame(predicate = Symbol.KEY,
          field = @Binding(role = ThematicRole.Value.KEY))
        static final String symbol = "#FF0000";
    }

    @Item(key = Green.KEY)
    public static final class Green {
        public static final String KEY = "cg:color/green";
        public static final ItemRef IID = ItemRef.fromString(KEY);
        public static final int RGB = 0x00FF00;
        private Green() {}

        @Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss = "the color green";

        @Frame(predicate = LexicalVocabulary.Lexeme.KEY,
          field = @Binding(role = ThematicRole.Value.KEY,
            qualifiers = {Language.English.KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String englishNounLemma = "green";

        @Frame(predicate = Symbol.KEY,
          field = @Binding(role = ThematicRole.Value.KEY))
        static final String symbol = "#00FF00";
    }

    @Item(key = Blue.KEY)
    public static final class Blue {
        public static final String KEY = "cg:color/blue";
        public static final ItemRef IID = ItemRef.fromString(KEY);
        public static final int RGB = 0x0000FF;
        private Blue() {}

        @Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss = "the color blue";

        @Frame(predicate = LexicalVocabulary.Lexeme.KEY,
          field = @Binding(role = ThematicRole.Value.KEY,
            qualifiers = {Language.English.KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String englishNounLemma = "blue";

        @Frame(predicate = Symbol.KEY,
          field = @Binding(role = ThematicRole.Value.KEY))
        static final String symbol = "#0000FF";
    }

    // ==================================================================================
    // SECONDARY
    // ==================================================================================

    @Item(key = Yellow.KEY)
    public static final class Yellow {
        public static final String KEY = "cg:color/yellow";
        public static final ItemRef IID = ItemRef.fromString(KEY);
        public static final int RGB = 0xFFFF00;
        private Yellow() {}

        @Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss = "the color yellow";

        @Frame(predicate = LexicalVocabulary.Lexeme.KEY,
          field = @Binding(role = ThematicRole.Value.KEY,
            qualifiers = {Language.English.KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String englishNounLemma = "yellow";

        @Frame(predicate = Symbol.KEY,
          field = @Binding(role = ThematicRole.Value.KEY))
        static final String symbol = "#FFFF00";
    }

    @Item(key = Cyan.KEY)
    public static final class Cyan {
        public static final String KEY = "cg:color/cyan";
        public static final ItemRef IID = ItemRef.fromString(KEY);
        public static final int RGB = 0x00FFFF;
        private Cyan() {}

        @Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss = "the color cyan";

        @Frame(predicate = LexicalVocabulary.Lexeme.KEY,
          field = @Binding(role = ThematicRole.Value.KEY,
            qualifiers = {Language.English.KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String englishNounLemma = "cyan";

        @Frame(predicate = Symbol.KEY,
          field = @Binding(role = ThematicRole.Value.KEY))
        static final String symbol = "#00FFFF";
    }

    @Item(key = Magenta.KEY)
    public static final class Magenta {
        public static final String KEY = "cg:color/magenta";
        public static final ItemRef IID = ItemRef.fromString(KEY);
        public static final int RGB = 0xFF00FF;
        private Magenta() {}

        @Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss = "the color magenta";

        @Frame(predicate = LexicalVocabulary.Lexeme.KEY,
          field = @Binding(role = ThematicRole.Value.KEY,
            qualifiers = {Language.English.KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String englishNounLemma = "magenta";

        @Frame(predicate = Symbol.KEY,
          field = @Binding(role = ThematicRole.Value.KEY))
        static final String symbol = "#FF00FF";
    }

    // ==================================================================================
    // TERTIARY / COMMON
    // ==================================================================================

    @Item(key = Orange.KEY)
    public static final class Orange {
        public static final String KEY = "cg:color/orange";
        public static final ItemRef IID = ItemRef.fromString(KEY);
        public static final int RGB = 0xFF8000;
        private Orange() {}

        @Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss = "the color orange";

        @Frame(predicate = LexicalVocabulary.Lexeme.KEY,
          field = @Binding(role = ThematicRole.Value.KEY,
            qualifiers = {Language.English.KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String englishNounLemma = "orange";

        @Frame(predicate = Symbol.KEY,
          field = @Binding(role = ThematicRole.Value.KEY))
        static final String symbol = "#FF8000";
    }

    @Item(key = Purple.KEY)
    public static final class Purple {
        public static final String KEY = "cg:color/purple";
        public static final ItemRef IID = ItemRef.fromString(KEY);
        public static final int RGB = 0x800080;
        private Purple() {}

        @Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss = "the color purple";

        @Frame(predicate = LexicalVocabulary.Lexeme.KEY,
          field = @Binding(role = ThematicRole.Value.KEY,
            qualifiers = {Language.English.KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String englishNounLemma = "purple";

        @Frame(predicate = Symbol.KEY,
          field = @Binding(role = ThematicRole.Value.KEY))
        static final String symbol = "#800080";
    }

    @Item(key = Pink.KEY)
    public static final class Pink {
        public static final String KEY = "cg:color/pink";
        public static final ItemRef IID = ItemRef.fromString(KEY);
        public static final int RGB = 0xFFC0CB;
        private Pink() {}

        @Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss = "the color pink";

        @Frame(predicate = LexicalVocabulary.Lexeme.KEY,
          field = @Binding(role = ThematicRole.Value.KEY,
            qualifiers = {Language.English.KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String englishNounLemma = "pink";

        @Frame(predicate = Symbol.KEY,
          field = @Binding(role = ThematicRole.Value.KEY))
        static final String symbol = "#FFC0CB";
    }

    @Item(key = Brown.KEY)
    public static final class Brown {
        public static final String KEY = "cg:color/brown";
        public static final ItemRef IID = ItemRef.fromString(KEY);
        public static final int RGB = 0x8B4513;
        private Brown() {}

        @Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss = "the color brown";

        @Frame(predicate = LexicalVocabulary.Lexeme.KEY,
          field = @Binding(role = ThematicRole.Value.KEY,
            qualifiers = {Language.English.KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String englishNounLemma = "brown";

        @Frame(predicate = Symbol.KEY,
          field = @Binding(role = ThematicRole.Value.KEY))
        static final String symbol = "#8B4513";
    }
}
