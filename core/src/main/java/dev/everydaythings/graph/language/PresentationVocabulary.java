package dev.everydaythings.graph.language;

import dev.everydaythings.graph.item.Item.Seed;

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

    private static final Sememe LEMMA = GrammaticalFeature.Lemma.SEED;

    public static class Primary {
        public static final String KEY = "cg.presentation:primary";
        @Seed public static final Sememe SEED = new Sememe(KEY)
                .gloss(Sememe.ENG, "primary brand color for prominent UI elements")
                .word(PartOfSpeech.NOUN, LEMMA, Sememe.ENG, "primary");
    }

    public static class Secondary {
        public static final String KEY = "cg.presentation:secondary";
        @Seed public static final Sememe SEED = new Sememe(KEY)
                .gloss(Sememe.ENG, "secondary brand color for supporting UI elements")
                .word(PartOfSpeech.NOUN, LEMMA, Sememe.ENG, "secondary");
    }

    public static class Accent {
        public static final String KEY = "cg.presentation:accent";
        @Seed public static final Sememe SEED = new Sememe(KEY)
                .gloss(Sememe.ENG, "accent color for emphasis and call-to-action elements")
                .word(PartOfSpeech.NOUN, LEMMA, Sememe.ENG, "accent");
    }

    public static class Surface {
        public static final String KEY = "cg.presentation:surface";
        @Seed public static final Sememe SEED = new Sememe(KEY)
                .gloss(Sememe.ENG, "background surface color for content areas")
                .word(PartOfSpeech.NOUN, LEMMA, Sememe.ENG, "surface");
    }

    public static class OnPrimary {
        public static final String KEY = "cg.presentation:on-primary";
        @Seed public static final Sememe SEED = new Sememe(KEY)
                .gloss(Sememe.ENG, "text and icon color used on primary-colored backgrounds")
                .word(PartOfSpeech.NOUN, LEMMA, Sememe.ENG, "on-primary");
    }

    public static class OnSurface {
        public static final String KEY = "cg.presentation:on-surface";
        @Seed public static final Sememe SEED = new Sememe(KEY)
                .gloss(Sememe.ENG, "text and icon color used on surface-colored backgrounds")
                .word(PartOfSpeech.NOUN, LEMMA, Sememe.ENG, "on-surface");
    }

    public static class Error {
        public static final String KEY = "cg.presentation:error";
        @Seed public static final Sememe SEED = new Sememe(KEY)
                .gloss(Sememe.ENG, "color indicating error or destructive state")
                .word(PartOfSpeech.NOUN, LEMMA, Sememe.ENG, "error");
    }

    public static class Outline {
        public static final String KEY = "cg.presentation:outline";
        @Seed public static final Sememe SEED = new Sememe(KEY)
                .gloss(Sememe.ENG, "color for borders, dividers, and outlines")
                .word(PartOfSpeech.NOUN, LEMMA, Sememe.ENG, "outline");
    }

    public static class Muted {
        public static final String KEY = "cg.presentation:muted";
        @Seed public static final Sememe SEED = new Sememe(KEY)
                .gloss(Sememe.ENG, "subdued color for disabled or low-emphasis elements")
                .word(PartOfSpeech.NOUN, LEMMA, Sememe.ENG, "muted");
    }
}
