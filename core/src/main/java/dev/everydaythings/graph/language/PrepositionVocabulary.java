package dev.everydaythings.graph.language;

import dev.everydaythings.graph.frame.ItemFrame;
import dev.everydaythings.graph.item.ItemSeed;
import dev.everydaythings.graph.item.id.ItemID;
import dev.everydaythings.graph.language.CoreVocabulary;

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
        @ItemFrame(key = {SememeGloss.KEY, Language.ENGLISH_KEY})
        static final String gloss = "indicating target or destination";

        @ItemFrame(key = {CoreVocabulary.Lexeme.KEY, Language.ENGLISH_KEY, PartOfSpeech.Preposition.KEY, GrammaticalFeature.Lemma.KEY})
        static final String prep1 = "on";

        @ItemFrame(key = {CoreVocabulary.Lexeme.KEY, Language.ENGLISH_KEY, PartOfSpeech.Preposition.KEY, GrammaticalFeature.Lemma.KEY})
        static final String prep2 = "to";

        @ItemFrame(key = {CoreVocabulary.Lexeme.KEY, Language.ENGLISH_KEY, PartOfSpeech.Preposition.KEY, GrammaticalFeature.Lemma.KEY})
        static final String prep3 = "into";

        @ItemFrame(key = {CoreVocabulary.AssignedRole.KEY})
        static final String role = ThematicRole.Goal.KEY;
    }

    @ItemSeed(key = With.KEY)
    public static class With {
        public static final String KEY = "cg.prep:with";
        public static final ItemID IID = ItemID.fromString(KEY);
        @ItemFrame(key = {SememeGloss.KEY, Language.ENGLISH_KEY})
        static final String gloss = "indicating tool or means";

        @ItemFrame(key = {CoreVocabulary.Lexeme.KEY, Language.ENGLISH_KEY, PartOfSpeech.Preposition.KEY, GrammaticalFeature.Lemma.KEY})
        static final String prep1 = "with";

        @ItemFrame(key = {CoreVocabulary.Lexeme.KEY, Language.ENGLISH_KEY, PartOfSpeech.Preposition.KEY, GrammaticalFeature.Lemma.KEY})
        static final String prep2 = "using";

        @ItemFrame(key = {CoreVocabulary.AssignedRole.KEY})
        static final String role = ThematicRole.Instrument.KEY;
    }

    @ItemSeed(key = From.KEY)
    public static class From {
        public static final String KEY = "cg.prep:from";
        public static final ItemID IID = ItemID.fromString(KEY);
        @ItemFrame(key = {SememeGloss.KEY, Language.ENGLISH_KEY})
        static final String gloss = "indicating origin or source";

        @ItemFrame(key = {CoreVocabulary.Lexeme.KEY, Language.ENGLISH_KEY, PartOfSpeech.Preposition.KEY, GrammaticalFeature.Lemma.KEY})
        static final String prep1 = "from";

        @ItemFrame(key = {CoreVocabulary.AssignedRole.KEY})
        static final String role = ThematicRole.Source.KEY;
    }

    @ItemSeed(key = For.KEY)
    public static class For {
        public static final String KEY = "cg.prep:for";
        public static final ItemID IID = ItemID.fromString(KEY);
        @ItemFrame(key = {SememeGloss.KEY, Language.ENGLISH_KEY})
        static final String gloss = "indicating beneficiary or recipient";

        @ItemFrame(key = {CoreVocabulary.Lexeme.KEY, Language.ENGLISH_KEY, PartOfSpeech.Preposition.KEY, GrammaticalFeature.Lemma.KEY})
        static final String prep1 = "for";

        @ItemFrame(key = {CoreVocabulary.AssignedRole.KEY})
        static final String role = ThematicRole.Recipient.KEY;
    }

    @ItemSeed(key = Between.KEY)
    public static class Between {
        public static final String KEY = "cg.prep:between";
        public static final ItemID IID = ItemID.fromString(KEY);
        @ItemFrame(key = {SememeGloss.KEY, Language.ENGLISH_KEY})
        static final String gloss = "indicating companions or participants";

        @ItemFrame(key = {CoreVocabulary.Lexeme.KEY, Language.ENGLISH_KEY, PartOfSpeech.Preposition.KEY, GrammaticalFeature.Lemma.KEY})
        static final String prep1 = "between";

        @ItemFrame(key = {CoreVocabulary.AssignedRole.KEY})
        static final String role = ThematicRole.Partner.KEY;
    }

    @ItemSeed(key = Named.KEY)
    public static class Named {
        public static final String KEY = "cg.prep:named";
        public static final ItemID IID = ItemID.fromString(KEY);
        @ItemFrame(key = {SememeGloss.KEY, Language.ENGLISH_KEY})
        static final String gloss = "indicating designation or label";

        @ItemFrame(key = {CoreVocabulary.Lexeme.KEY, Language.ENGLISH_KEY, PartOfSpeech.Preposition.KEY, GrammaticalFeature.Lemma.KEY})
        static final String prep1 = "named";

        @ItemFrame(key = {CoreVocabulary.Lexeme.KEY, Language.ENGLISH_KEY, PartOfSpeech.Preposition.KEY, GrammaticalFeature.Lemma.KEY})
        static final String prep2 = "called";

        @ItemFrame(key = {CoreVocabulary.AssignedRole.KEY})
        static final String role = ThematicRole.Name.KEY;
    }
}
