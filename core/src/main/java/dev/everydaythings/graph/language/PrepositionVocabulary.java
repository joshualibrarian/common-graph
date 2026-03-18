package dev.everydaythings.graph.language;

import dev.everydaythings.graph.item.ItemSeed;
import dev.everydaythings.graph.item.id.ItemID;

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
        public static final ItemID IID = ItemID.fromString(KEY);
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
        public static final ItemID IID = ItemID.fromString(KEY);
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
        public static final ItemID IID = ItemID.fromString(KEY);
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
        public static final ItemID IID = ItemID.fromString(KEY);
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
        public static final ItemID IID = ItemID.fromString(KEY);
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
        public static final ItemID IID = ItemID.fromString(KEY);
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
