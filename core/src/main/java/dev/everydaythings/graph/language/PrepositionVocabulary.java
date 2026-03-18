package dev.everydaythings.graph.language;

import dev.everydaythings.graph.item.Item.Seed;
import dev.everydaythings.graph.item.ItemSeed;

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

    @ItemSeed(key = On.KEY)
    public static class On {
        public static final String KEY = "cg.prep:on";
        @Seed public static final Sememe SEED = new Sememe(KEY)
                .gloss(Sememe.ENG, "indicating target or destination")
                .word(PartOfSpeech.PREPOSITION, GrammaticalFeature.Lemma.SEED, Sememe.ENG, "on").word(PartOfSpeech.PREPOSITION, GrammaticalFeature.Lemma.SEED, Sememe.ENG, "to").word(PartOfSpeech.PREPOSITION, GrammaticalFeature.Lemma.SEED, Sememe.ENG, "into")
                .role(ThematicRole.Goal.KEY);

        @ItemSeed.Frame(key = {SememeGloss.KEY, Language.ENGLISH_KEY})
        static final String gloss = "indicating target or destination";

        @ItemSeed.Word(lang = Language.ENGLISH_KEY, pos = PartOfSpeech.Preposition.KEY, features = {GrammaticalFeature.Lemma.KEY})
        static final String prep1 = "on";

        @ItemSeed.Word(lang = Language.ENGLISH_KEY, pos = PartOfSpeech.Preposition.KEY, features = {GrammaticalFeature.Lemma.KEY})
        static final String prep2 = "to";

        @ItemSeed.Word(lang = Language.ENGLISH_KEY, pos = PartOfSpeech.Preposition.KEY, features = {GrammaticalFeature.Lemma.KEY})
        static final String prep3 = "into";

        @ItemSeed.Frame(key = {CoreVocabulary.AssignedRole.KEY})
        static final String role = ThematicRole.Goal.KEY;
    }

    @ItemSeed(key = With.KEY)
    public static class With {
        public static final String KEY = "cg.prep:with";
        @Seed public static final Sememe SEED = new Sememe(KEY)
                .gloss(Sememe.ENG, "indicating tool or means")
                .word(PartOfSpeech.PREPOSITION, GrammaticalFeature.Lemma.SEED, Sememe.ENG, "with").word(PartOfSpeech.PREPOSITION, GrammaticalFeature.Lemma.SEED, Sememe.ENG, "using")
                .role(ThematicRole.Instrument.KEY);

        @ItemSeed.Frame(key = {SememeGloss.KEY, Language.ENGLISH_KEY})
        static final String gloss = "indicating tool or means";

        @ItemSeed.Word(lang = Language.ENGLISH_KEY, pos = PartOfSpeech.Preposition.KEY, features = {GrammaticalFeature.Lemma.KEY})
        static final String prep1 = "with";

        @ItemSeed.Word(lang = Language.ENGLISH_KEY, pos = PartOfSpeech.Preposition.KEY, features = {GrammaticalFeature.Lemma.KEY})
        static final String prep2 = "using";

        @ItemSeed.Frame(key = {CoreVocabulary.AssignedRole.KEY})
        static final String role = ThematicRole.Instrument.KEY;
    }

    @ItemSeed(key = From.KEY)
    public static class From {
        public static final String KEY = "cg.prep:from";
        @Seed public static final Sememe SEED = new Sememe(KEY)
                .gloss(Sememe.ENG, "indicating origin or source")
                .word(PartOfSpeech.PREPOSITION, GrammaticalFeature.Lemma.SEED, Sememe.ENG, "from")
                .role(ThematicRole.Source.KEY);

        @ItemSeed.Frame(key = {SememeGloss.KEY, Language.ENGLISH_KEY})
        static final String gloss = "indicating origin or source";

        @ItemSeed.Word(lang = Language.ENGLISH_KEY, pos = PartOfSpeech.Preposition.KEY, features = {GrammaticalFeature.Lemma.KEY})
        static final String prep1 = "from";

        @ItemSeed.Frame(key = {CoreVocabulary.AssignedRole.KEY})
        static final String role = ThematicRole.Source.KEY;
    }

    @ItemSeed(key = For.KEY)
    public static class For {
        public static final String KEY = "cg.prep:for";
        @Seed public static final Sememe SEED = new Sememe(KEY)
                .gloss(Sememe.ENG, "indicating beneficiary or recipient")
                .word(PartOfSpeech.PREPOSITION, GrammaticalFeature.Lemma.SEED, Sememe.ENG, "for")
                .role(ThematicRole.Recipient.KEY);

        @ItemSeed.Frame(key = {SememeGloss.KEY, Language.ENGLISH_KEY})
        static final String gloss = "indicating beneficiary or recipient";

        @ItemSeed.Word(lang = Language.ENGLISH_KEY, pos = PartOfSpeech.Preposition.KEY, features = {GrammaticalFeature.Lemma.KEY})
        static final String prep1 = "for";

        @ItemSeed.Frame(key = {CoreVocabulary.AssignedRole.KEY})
        static final String role = ThematicRole.Recipient.KEY;
    }

    @ItemSeed(key = Between.KEY)
    public static class Between {
        public static final String KEY = "cg.prep:between";
        @Seed public static final Sememe SEED = new Sememe(KEY)
                .gloss(Sememe.ENG, "indicating companions or participants")
                .word(PartOfSpeech.PREPOSITION, GrammaticalFeature.Lemma.SEED, Sememe.ENG, "between")
                .role(ThematicRole.Partner.KEY);

        @ItemSeed.Frame(key = {SememeGloss.KEY, Language.ENGLISH_KEY})
        static final String gloss = "indicating companions or participants";

        @ItemSeed.Word(lang = Language.ENGLISH_KEY, pos = PartOfSpeech.Preposition.KEY, features = {GrammaticalFeature.Lemma.KEY})
        static final String prep1 = "between";

        @ItemSeed.Frame(key = {CoreVocabulary.AssignedRole.KEY})
        static final String role = ThematicRole.Partner.KEY;
    }

    @ItemSeed(key = Named.KEY)
    public static class Named {
        public static final String KEY = "cg.prep:named";
        @Seed public static final Sememe SEED = new Sememe(KEY)
                .gloss(Sememe.ENG, "indicating designation or label")
                .word(PartOfSpeech.PREPOSITION, GrammaticalFeature.Lemma.SEED, Sememe.ENG, "named").word(PartOfSpeech.PREPOSITION, GrammaticalFeature.Lemma.SEED, Sememe.ENG, "called")
                .role(ThematicRole.Name.KEY);

        @ItemSeed.Frame(key = {SememeGloss.KEY, Language.ENGLISH_KEY})
        static final String gloss = "indicating designation or label";

        @ItemSeed.Word(lang = Language.ENGLISH_KEY, pos = PartOfSpeech.Preposition.KEY, features = {GrammaticalFeature.Lemma.KEY})
        static final String prep1 = "named";

        @ItemSeed.Word(lang = Language.ENGLISH_KEY, pos = PartOfSpeech.Preposition.KEY, features = {GrammaticalFeature.Lemma.KEY})
        static final String prep2 = "called";

        @ItemSeed.Frame(key = {CoreVocabulary.AssignedRole.KEY})
        static final String role = ThematicRole.Name.KEY;
    }
}
