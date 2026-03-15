package dev.everydaythings.graph.language;

import dev.everydaythings.graph.item.Item.Seed;

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

    private static final Sememe LEMMA = GrammaticalFeature.Lemma.SEED;

    // ==================================================================================
    // ACHROMATIC
    // ==================================================================================

    public static class White {
        public static final String KEY = "cg:color/white";
        public static final int VALUE = 0xFFFFFF;
        @Seed public static final Sememe SEED = new Sememe(KEY)
                .gloss(Sememe.ENG, "the color white; achromatic color of maximum lightness")
                .word(PartOfSpeech.NOUN, LEMMA, Sememe.ENG, "white")
                .symbol(hex(VALUE));
    }

    public static class Black {
        public static final String KEY = "cg:color/black";
        public static final int VALUE = 0x000000;
        @Seed public static final Sememe SEED = new Sememe(KEY)
                .gloss(Sememe.ENG, "the color black; achromatic color of minimum lightness")
                .word(PartOfSpeech.NOUN, LEMMA, Sememe.ENG, "black")
                .symbol(hex(VALUE));
    }

    public static class Gray {
        public static final String KEY = "cg:color/gray";
        public static final int VALUE = 0x808080;
        @Seed public static final Sememe SEED = new Sememe(KEY)
                .gloss(Sememe.ENG, "the color gray; neutral midtone between black and white")
                .word(PartOfSpeech.NOUN, LEMMA, Sememe.ENG, "gray")
                .word(PartOfSpeech.NOUN, LEMMA, Sememe.ENG, "grey")
                .symbol(hex(VALUE));
    }

    // ==================================================================================
    // PRIMARY
    // ==================================================================================

    public static class Red {
        public static final String KEY = "cg:color/red";
        public static final int VALUE = 0xFF0000;
        @Seed public static final Sememe SEED = new Sememe(KEY)
                .gloss(Sememe.ENG, "the color red")
                .word(PartOfSpeech.NOUN, LEMMA, Sememe.ENG, "red")
                .symbol(hex(VALUE));
    }

    public static class Green {
        public static final String KEY = "cg:color/green";
        public static final int VALUE = 0x00FF00;
        @Seed public static final Sememe SEED = new Sememe(KEY)
                .gloss(Sememe.ENG, "the color green")
                .word(PartOfSpeech.NOUN, LEMMA, Sememe.ENG, "green")
                .symbol(hex(VALUE));
    }

    public static class Blue {
        public static final String KEY = "cg:color/blue";
        public static final int VALUE = 0x0000FF;
        @Seed public static final Sememe SEED = new Sememe(KEY)
                .gloss(Sememe.ENG, "the color blue")
                .word(PartOfSpeech.NOUN, LEMMA, Sememe.ENG, "blue")
                .symbol(hex(VALUE));
    }

    // ==================================================================================
    // SECONDARY
    // ==================================================================================

    public static class Yellow {
        public static final String KEY = "cg:color/yellow";
        public static final int VALUE = 0xFFFF00;
        @Seed public static final Sememe SEED = new Sememe(KEY)
                .gloss(Sememe.ENG, "the color yellow")
                .word(PartOfSpeech.NOUN, LEMMA, Sememe.ENG, "yellow")
                .symbol(hex(VALUE));
    }

    public static class Cyan {
        public static final String KEY = "cg:color/cyan";
        public static final int VALUE = 0x00FFFF;
        @Seed public static final Sememe SEED = new Sememe(KEY)
                .gloss(Sememe.ENG, "the color cyan")
                .word(PartOfSpeech.NOUN, LEMMA, Sememe.ENG, "cyan")
                .word(PartOfSpeech.NOUN, LEMMA, Sememe.ENG, "aqua")
                .symbol(hex(VALUE));
    }

    public static class Magenta {
        public static final String KEY = "cg:color/magenta";
        public static final int VALUE = 0xFF00FF;
        @Seed public static final Sememe SEED = new Sememe(KEY)
                .gloss(Sememe.ENG, "the color magenta")
                .word(PartOfSpeech.NOUN, LEMMA, Sememe.ENG, "magenta")
                .word(PartOfSpeech.NOUN, LEMMA, Sememe.ENG, "fuchsia")
                .symbol(hex(VALUE));
    }

    // ==================================================================================
    // TERTIARY / COMMON
    // ==================================================================================

    public static class Orange {
        public static final String KEY = "cg:color/orange";
        public static final int VALUE = 0xFF8000;
        @Seed public static final Sememe SEED = new Sememe(KEY)
                .gloss(Sememe.ENG, "the color orange")
                .word(PartOfSpeech.NOUN, LEMMA, Sememe.ENG, "orange")
                .symbol(hex(VALUE));
    }

    public static class Purple {
        public static final String KEY = "cg:color/purple";
        public static final int VALUE = 0x800080;
        @Seed public static final Sememe SEED = new Sememe(KEY)
                .gloss(Sememe.ENG, "the color purple")
                .word(PartOfSpeech.NOUN, LEMMA, Sememe.ENG, "purple")
                .word(PartOfSpeech.NOUN, LEMMA, Sememe.ENG, "violet")
                .symbol(hex(VALUE));
    }

    public static class Pink {
        public static final String KEY = "cg:color/pink";
        public static final int VALUE = 0xFFC0CB;
        @Seed public static final Sememe SEED = new Sememe(KEY)
                .gloss(Sememe.ENG, "the color pink")
                .word(PartOfSpeech.NOUN, LEMMA, Sememe.ENG, "pink")
                .symbol(hex(VALUE));
    }

    public static class Brown {
        public static final String KEY = "cg:color/brown";
        public static final int VALUE = 0x8B4513;
        @Seed public static final Sememe SEED = new Sememe(KEY)
                .gloss(Sememe.ENG, "the color brown")
                .word(PartOfSpeech.NOUN, LEMMA, Sememe.ENG, "brown")
                .symbol(hex(VALUE));
    }

    // ==================================================================================
    // Utility
    // ==================================================================================

    private static String hex(int rgb) {
        return String.format("#%06X", rgb);
    }
}
