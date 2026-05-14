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
    @Seed.Item(key = Symbol.KEY, head = CoreVocabulary.Predicate.KEY)
    public static final class Symbol {
        public static final String KEY = "cg.core:symbol";
        public static final ItemID IID = ItemID.fromString(KEY);
        private Symbol() {}

        @Frame(predicate = Gloss.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss =
                "language-neutral symbolic representation of a sememe — "
                        + "a hex string, mathematical glyph, unit symbol, etc.";
    }

    // ==================================================================================
    // ACHROMATIC
    // ==================================================================================

    @Seed.Item(key = White.KEY)
    public static final class White {
        public static final String KEY = "cg:color/white";
        public static final ItemID IID = ItemID.fromString(KEY);
        public static final int RGB = 0xFFFFFF;
        private White() {}

        @Frame(predicate = Gloss.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss = "achromatic color of maximum lightness";

        @Frame(predicate = Lexeme.KEY,
          field = @Binding(role = ThematicRole.Value.KEY,
            qualifiers = {Language.English.KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String englishNounLemma = "white";

        @Frame(predicate = Symbol.KEY,
          field = @Binding(role = ThematicRole.Value.KEY))
        static final String symbol = "#FFFFFF";
    }

    @Seed.Item(key = Black.KEY)
    public static final class Black {
        public static final String KEY = "cg:color/black";
        public static final ItemID IID = ItemID.fromString(KEY);
        public static final int RGB = 0x000000;
        private Black() {}

        @Frame(predicate = Gloss.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss = "achromatic color of minimum lightness";

        @Frame(predicate = Lexeme.KEY,
          field = @Binding(role = ThematicRole.Value.KEY,
            qualifiers = {Language.English.KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String englishNounLemma = "black";

        @Frame(predicate = Symbol.KEY,
          field = @Binding(role = ThematicRole.Value.KEY))
        static final String symbol = "#000000";
    }

    @Seed.Item(key = Gray.KEY)
    public static final class Gray {
        public static final String KEY = "cg:color/gray";
        public static final ItemID IID = ItemID.fromString(KEY);
        public static final int RGB = 0x808080;
        private Gray() {}

        @Frame(predicate = Gloss.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss = "neutral midtone between black and white";

        @Frame(predicate = Lexeme.KEY,
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

    @Seed.Item(key = Red.KEY)
    public static final class Red {
        public static final String KEY = "cg:color/red";
        public static final ItemID IID = ItemID.fromString(KEY);
        public static final int RGB = 0xFF0000;
        private Red() {}

        @Frame(predicate = Gloss.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss = "the color red";

        @Frame(predicate = Lexeme.KEY,
          field = @Binding(role = ThematicRole.Value.KEY,
            qualifiers = {Language.English.KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String englishNounLemma = "red";

        @Frame(predicate = Symbol.KEY,
          field = @Binding(role = ThematicRole.Value.KEY))
        static final String symbol = "#FF0000";
    }

    @Seed.Item(key = Green.KEY)
    public static final class Green {
        public static final String KEY = "cg:color/green";
        public static final ItemID IID = ItemID.fromString(KEY);
        public static final int RGB = 0x00FF00;
        private Green() {}

        @Frame(predicate = Gloss.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss = "the color green";

        @Frame(predicate = Lexeme.KEY,
          field = @Binding(role = ThematicRole.Value.KEY,
            qualifiers = {Language.English.KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String englishNounLemma = "green";

        @Frame(predicate = Symbol.KEY,
          field = @Binding(role = ThematicRole.Value.KEY))
        static final String symbol = "#00FF00";
    }

    @Seed.Item(key = Blue.KEY)
    public static final class Blue {
        public static final String KEY = "cg:color/blue";
        public static final ItemID IID = ItemID.fromString(KEY);
        public static final int RGB = 0x0000FF;
        private Blue() {}

        @Frame(predicate = Gloss.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss = "the color blue";

        @Frame(predicate = Lexeme.KEY,
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

    @Seed.Item(key = Yellow.KEY)
    public static final class Yellow {
        public static final String KEY = "cg:color/yellow";
        public static final ItemID IID = ItemID.fromString(KEY);
        public static final int RGB = 0xFFFF00;
        private Yellow() {}

        @Frame(predicate = Gloss.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss = "the color yellow";

        @Frame(predicate = Lexeme.KEY,
          field = @Binding(role = ThematicRole.Value.KEY,
            qualifiers = {Language.English.KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String englishNounLemma = "yellow";

        @Frame(predicate = Symbol.KEY,
          field = @Binding(role = ThematicRole.Value.KEY))
        static final String symbol = "#FFFF00";
    }

    @Seed.Item(key = Cyan.KEY)
    public static final class Cyan {
        public static final String KEY = "cg:color/cyan";
        public static final ItemID IID = ItemID.fromString(KEY);
        public static final int RGB = 0x00FFFF;
        private Cyan() {}

        @Frame(predicate = Gloss.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss = "the color cyan";

        @Frame(predicate = Lexeme.KEY,
          field = @Binding(role = ThematicRole.Value.KEY,
            qualifiers = {Language.English.KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String englishNounLemma = "cyan";

        @Frame(predicate = Symbol.KEY,
          field = @Binding(role = ThematicRole.Value.KEY))
        static final String symbol = "#00FFFF";
    }

    @Seed.Item(key = Magenta.KEY)
    public static final class Magenta {
        public static final String KEY = "cg:color/magenta";
        public static final ItemID IID = ItemID.fromString(KEY);
        public static final int RGB = 0xFF00FF;
        private Magenta() {}

        @Frame(predicate = Gloss.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss = "the color magenta";

        @Frame(predicate = Lexeme.KEY,
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

    @Seed.Item(key = Orange.KEY)
    public static final class Orange {
        public static final String KEY = "cg:color/orange";
        public static final ItemID IID = ItemID.fromString(KEY);
        public static final int RGB = 0xFF8000;
        private Orange() {}

        @Frame(predicate = Gloss.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss = "the color orange";

        @Frame(predicate = Lexeme.KEY,
          field = @Binding(role = ThematicRole.Value.KEY,
            qualifiers = {Language.English.KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String englishNounLemma = "orange";

        @Frame(predicate = Symbol.KEY,
          field = @Binding(role = ThematicRole.Value.KEY))
        static final String symbol = "#FF8000";
    }

    @Seed.Item(key = Purple.KEY)
    public static final class Purple {
        public static final String KEY = "cg:color/purple";
        public static final ItemID IID = ItemID.fromString(KEY);
        public static final int RGB = 0x800080;
        private Purple() {}

        @Frame(predicate = Gloss.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss = "the color purple";

        @Frame(predicate = Lexeme.KEY,
          field = @Binding(role = ThematicRole.Value.KEY,
            qualifiers = {Language.English.KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String englishNounLemma = "purple";

        @Frame(predicate = Symbol.KEY,
          field = @Binding(role = ThematicRole.Value.KEY))
        static final String symbol = "#800080";
    }

    @Seed.Item(key = Pink.KEY)
    public static final class Pink {
        public static final String KEY = "cg:color/pink";
        public static final ItemID IID = ItemID.fromString(KEY);
        public static final int RGB = 0xFFC0CB;
        private Pink() {}

        @Frame(predicate = Gloss.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss = "the color pink";

        @Frame(predicate = Lexeme.KEY,
          field = @Binding(role = ThematicRole.Value.KEY,
            qualifiers = {Language.English.KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String englishNounLemma = "pink";

        @Frame(predicate = Symbol.KEY,
          field = @Binding(role = ThematicRole.Value.KEY))
        static final String symbol = "#FFC0CB";
    }

    @Seed.Item(key = Brown.KEY)
    public static final class Brown {
        public static final String KEY = "cg:color/brown";
        public static final ItemID IID = ItemID.fromString(KEY);
        public static final int RGB = 0x8B4513;
        private Brown() {}

        @Frame(predicate = Gloss.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss = "the color brown";

        @Frame(predicate = Lexeme.KEY,
          field = @Binding(role = ThematicRole.Value.KEY,
            qualifiers = {Language.English.KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String englishNounLemma = "brown";

        @Frame(predicate = Symbol.KEY,
          field = @Binding(role = ThematicRole.Value.KEY))
        static final String symbol = "#8B4513";
    }
}
