package dev.everydaythings.graph.language;

import dev.everydaythings.graph.frame.ItemFrame;
import dev.everydaythings.graph.frame.ItemFrame.Bind;
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
        @ItemFrame(predicate = SememeGloss.KEY,
                   fieldAs = @Bind(role = ThematicRole.Value.KEY, qualifiers = {Language.ENGLISH_KEY}))
        static final String gloss = "indicating target or destination";

        @ItemFrame(predicate = CoreVocabulary.Lexeme.KEY,
                   fieldAs = @Bind(role = ThematicRole.Value.KEY,
                                   qualifiers = {Language.ENGLISH_KEY, PartOfSpeech.Preposition.KEY, GrammaticalFeature.Lemma.KEY}, index = true))
        static final String[] words = {"on", "to", "into"};

        @ItemFrame(predicate = CoreVocabulary.AssignedRole.KEY)
        static final String role = ThematicRole.Goal.KEY;
    }

    @ItemSeed(key = With.KEY)
    public static class With {
        public static final String KEY = "cg.prep:with";
        public static final ItemID IID = ItemID.fromString(KEY);
        @ItemFrame(predicate = SememeGloss.KEY,
                   fieldAs = @Bind(role = ThematicRole.Value.KEY, qualifiers = {Language.ENGLISH_KEY}))
        static final String gloss = "indicating tool or means";

        @ItemFrame(predicate = CoreVocabulary.Lexeme.KEY,
                   fieldAs = @Bind(role = ThematicRole.Value.KEY,
                                   qualifiers = {Language.ENGLISH_KEY, PartOfSpeech.Preposition.KEY, GrammaticalFeature.Lemma.KEY}, index = true))
        static final String[] words = {"with", "using"};

        @ItemFrame(predicate = CoreVocabulary.AssignedRole.KEY)
        static final String role = ThematicRole.Instrument.KEY;
    }

    @ItemSeed(key = From.KEY)
    public static class From {
        public static final String KEY = "cg.prep:from";
        public static final ItemID IID = ItemID.fromString(KEY);
        @ItemFrame(predicate = SememeGloss.KEY,
                   fieldAs = @Bind(role = ThematicRole.Value.KEY, qualifiers = {Language.ENGLISH_KEY}))
        static final String gloss = "indicating origin or source";

        @ItemFrame(predicate = CoreVocabulary.Lexeme.KEY,
                   fieldAs = @Bind(role = ThematicRole.Value.KEY,
                                   qualifiers = {Language.ENGLISH_KEY, PartOfSpeech.Preposition.KEY, GrammaticalFeature.Lemma.KEY}, index = true))
        static final String[] words = {"from"};

        @ItemFrame(predicate = CoreVocabulary.AssignedRole.KEY)
        static final String role = ThematicRole.Source.KEY;
    }

    @ItemSeed(key = For.KEY)
    public static class For {
        public static final String KEY = "cg.prep:for";
        public static final ItemID IID = ItemID.fromString(KEY);
        @ItemFrame(predicate = SememeGloss.KEY,
                   fieldAs = @Bind(role = ThematicRole.Value.KEY, qualifiers = {Language.ENGLISH_KEY}))
        static final String gloss = "indicating beneficiary or recipient";

        @ItemFrame(predicate = CoreVocabulary.Lexeme.KEY,
                   fieldAs = @Bind(role = ThematicRole.Value.KEY,
                                   qualifiers = {Language.ENGLISH_KEY, PartOfSpeech.Preposition.KEY, GrammaticalFeature.Lemma.KEY}, index = true))
        static final String[] words = {"for"};

        @ItemFrame(predicate = CoreVocabulary.AssignedRole.KEY)
        static final String role = ThematicRole.Recipient.KEY;
    }

    @ItemSeed(key = Between.KEY)
    public static class Between {
        public static final String KEY = "cg.prep:between";
        public static final ItemID IID = ItemID.fromString(KEY);
        @ItemFrame(predicate = SememeGloss.KEY,
                   fieldAs = @Bind(role = ThematicRole.Value.KEY, qualifiers = {Language.ENGLISH_KEY}))
        static final String gloss = "indicating companions or participants";

        @ItemFrame(predicate = CoreVocabulary.Lexeme.KEY,
                   fieldAs = @Bind(role = ThematicRole.Value.KEY,
                                   qualifiers = {Language.ENGLISH_KEY, PartOfSpeech.Preposition.KEY, GrammaticalFeature.Lemma.KEY}, index = true))
        static final String[] words = {"between"};

        @ItemFrame(predicate = CoreVocabulary.AssignedRole.KEY)
        static final String role = ThematicRole.Partner.KEY;
    }

    @ItemSeed(key = Named.KEY)
    public static class Named {
        public static final String KEY = "cg.prep:named";
        public static final ItemID IID = ItemID.fromString(KEY);
        @ItemFrame(predicate = SememeGloss.KEY,
                   fieldAs = @Bind(role = ThematicRole.Value.KEY, qualifiers = {Language.ENGLISH_KEY}))
        static final String gloss = "indicating designation or label";

        @ItemFrame(predicate = CoreVocabulary.Lexeme.KEY,
                   fieldAs = @Bind(role = ThematicRole.Value.KEY,
                                   qualifiers = {Language.ENGLISH_KEY, PartOfSpeech.Preposition.KEY, GrammaticalFeature.Lemma.KEY}, index = true))
        static final String[] words = {"named", "called"};

        @ItemFrame(predicate = CoreVocabulary.AssignedRole.KEY)
        static final String role = ThematicRole.Value.KEY;
    }
}
