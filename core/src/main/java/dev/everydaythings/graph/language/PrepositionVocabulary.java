package dev.everydaythings.graph.language;

import dev.everydaythings.graph.item.Item.Seed;

/**
 * Preposition vocabulary — function words that assign thematic roles.
 *
 * <p>Each preposition carries an {@link Sememe#role(String) assignedRole}
 * that tells the frame assembler which thematic role the preposition's
 * object fills. For example, "on" assigns {@link ThematicRole.Goal} —
 * in "create chess on myItem", "myItem" fills the GOAL role.
 *
 * @see CoreVocabulary for core predicates and action verbs
 */
public final class PrepositionVocabulary {

    private PrepositionVocabulary() {}

    public static class On {
        public static final String KEY = "cg.prep:on";
        @Seed public static final Sememe SEED = new Sememe(KEY, PartOfSpeech.PREPOSITION)
                .gloss(Sememe.ENG, "indicating target or destination")
                .word(Sememe.LEMMA, Sememe.ENG, "on").word(Sememe.LEMMA, Sememe.ENG, "to").word(Sememe.LEMMA, Sememe.ENG, "into")
                .role(ThematicRole.Goal.KEY);
    }

    public static class With {
        public static final String KEY = "cg.prep:with";
        @Seed public static final Sememe SEED = new Sememe(KEY, PartOfSpeech.PREPOSITION)
                .gloss(Sememe.ENG, "indicating tool or means")
                .word(Sememe.LEMMA, Sememe.ENG, "with").word(Sememe.LEMMA, Sememe.ENG, "using")
                .role(ThematicRole.Instrument.KEY);
    }

    public static class From {
        public static final String KEY = "cg.prep:from";
        @Seed public static final Sememe SEED = new Sememe(KEY, PartOfSpeech.PREPOSITION)
                .gloss(Sememe.ENG, "indicating origin or source")
                .word(Sememe.LEMMA, Sememe.ENG, "from")
                .role(ThematicRole.Source.KEY);
    }

    public static class For {
        public static final String KEY = "cg.prep:for";
        @Seed public static final Sememe SEED = new Sememe(KEY, PartOfSpeech.PREPOSITION)
                .gloss(Sememe.ENG, "indicating beneficiary or recipient")
                .word(Sememe.LEMMA, Sememe.ENG, "for")
                .role(ThematicRole.Recipient.KEY);
    }

    public static class Between {
        public static final String KEY = "cg.prep:between";
        @Seed public static final Sememe SEED = new Sememe(KEY, PartOfSpeech.PREPOSITION)
                .gloss(Sememe.ENG, "indicating companions or participants")
                .word(Sememe.LEMMA, Sememe.ENG, "between")
                .role(ThematicRole.Partner.KEY);
    }

    public static class Named {
        public static final String KEY = "cg.prep:named";
        @Seed public static final Sememe SEED = new Sememe(KEY, PartOfSpeech.PREPOSITION)
                .gloss(Sememe.ENG, "indicating designation or label")
                .word(Sememe.LEMMA, Sememe.ENG, "named").word(Sememe.LEMMA, Sememe.ENG, "called")
                .role(ThematicRole.Name.KEY);
    }
}
