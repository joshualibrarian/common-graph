package dev.everydaythings.graph.language;

import dev.everydaythings.graph.frame.ItemFrame;
import dev.everydaythings.graph.frame.ItemFrame.Bind;
import dev.everydaythings.graph.item.ItemSeed;
import dev.everydaythings.graph.item.id.ItemID;
import dev.everydaythings.graph.language.CoreVocabulary;

/**
 * Lexical relation vocabulary — semantic relationships between concepts.
 *
 * <p>These are the WordNet pointer types: taxonomic (hypernym/hyponym),
 * mereological (holonym/meronym), and associative (antonym, entailment,
 * derivation, etc.) relations. Each is anchored to a CILI identifier.
 *
 * @see CoreVocabulary for core predicates and action verbs
 */
public final class LexicalVocabulary {

    private LexicalVocabulary() {}

    @ItemSeed(key = Hypernym.KEY)
    public static class Hypernym {
        public static final String KEY = "cg.rel:hypernym";
        public static final ItemID IID = ItemID.fromString(KEY);
        @ItemFrame(predicate = SememeGloss.KEY,
                   fieldAs = @Bind(role = ThematicRole.Value.KEY, qualifiers = {Language.ENGLISH_KEY}))
        static final String gloss = "is a kind of; is a type of; is a subclass of";

        @ItemFrame(predicate = CoreVocabulary.CiliId.KEY)
        static final String cili = "i69569";

        @ItemFrame(predicate = CoreVocabulary.Lexeme.KEY,
                   fieldAs = @Bind(role = ThematicRole.Value.KEY,
                                   qualifiers = {Language.ENGLISH_KEY, PartOfSpeech.Verb.KEY, GrammaticalFeature.Lemma.KEY}, index = true))
        static final String[] words = {"hypernym"};

        @ItemFrame(predicate = CoreVocabulary.Expects.KEY,
                   fieldAs = @Bind(role = ThematicRole.Topic.KEY, qualifiers = {ThematicRole.KEY, ThematicRole.Theme.KEY}))
        static final ItemID expectTheme = ThematicRole.Theme.IID;

        @ItemFrame(predicate = CoreVocabulary.Expects.KEY,
                   fieldAs = @Bind(role = ThematicRole.Topic.KEY, qualifiers = {ThematicRole.KEY, ThematicRole.Goal.KEY}))
        static final ItemID expectGoal = ThematicRole.Goal.IID;
    }

    @ItemSeed(key = Hyponym.KEY)
    public static class Hyponym {
        public static final String KEY = "cg.rel:hyponym";
        public static final ItemID IID = ItemID.fromString(KEY);
        @ItemFrame(predicate = SememeGloss.KEY,
                   fieldAs = @Bind(role = ThematicRole.Value.KEY, qualifiers = {Language.ENGLISH_KEY}))
        static final String gloss = "has subtype; has kind; is a superclass of";

        @ItemFrame(predicate = CoreVocabulary.CiliId.KEY)
        static final String cili = "i69570";

        @ItemFrame(predicate = CoreVocabulary.Lexeme.KEY,
                   fieldAs = @Bind(role = ThematicRole.Value.KEY,
                                   qualifiers = {Language.ENGLISH_KEY, PartOfSpeech.Verb.KEY, GrammaticalFeature.Lemma.KEY}, index = true))
        static final String[] words = {"hyponym"};

        @ItemFrame(predicate = CoreVocabulary.Expects.KEY,
                   fieldAs = @Bind(role = ThematicRole.Topic.KEY, qualifiers = {ThematicRole.KEY, ThematicRole.Theme.KEY}))
        static final ItemID expectTheme = ThematicRole.Theme.IID;

        @ItemFrame(predicate = CoreVocabulary.Expects.KEY,
                   fieldAs = @Bind(role = ThematicRole.Topic.KEY, qualifiers = {ThematicRole.KEY, ThematicRole.Goal.KEY}))
        static final ItemID expectGoal = ThematicRole.Goal.IID;
    }

    @ItemSeed(key = InstanceOf.KEY)
    public static class InstanceOf {
        public static final String KEY = "cg.rel:instance-of";
        public static final ItemID IID = ItemID.fromString(KEY);
        @ItemFrame(predicate = SememeGloss.KEY,
                   fieldAs = @Bind(role = ThematicRole.Value.KEY, qualifiers = {Language.ENGLISH_KEY}))
        static final String gloss = "is an instance of; has type; is a member of class";

        @ItemFrame(predicate = CoreVocabulary.CiliId.KEY)
        static final String cili = "i35284";

        @ItemFrame(predicate = CoreVocabulary.Lexeme.KEY,
                   fieldAs = @Bind(role = ThematicRole.Value.KEY,
                                   qualifiers = {Language.ENGLISH_KEY, PartOfSpeech.Verb.KEY, GrammaticalFeature.Lemma.KEY}, index = true))
        static final String[] words = {"instance-of"};

        @ItemFrame(predicate = CoreVocabulary.Expects.KEY,
                   fieldAs = @Bind(role = ThematicRole.Topic.KEY, qualifiers = {ThematicRole.KEY, ThematicRole.Theme.KEY}))
        static final ItemID expectTheme = ThematicRole.Theme.IID;

        @ItemFrame(predicate = CoreVocabulary.Expects.KEY,
                   fieldAs = @Bind(role = ThematicRole.Topic.KEY, qualifiers = {ThematicRole.KEY, ThematicRole.Goal.KEY}))
        static final ItemID expectGoal = ThematicRole.Goal.IID;
    }

    @ItemSeed(key = Holonym.KEY)
    public static class Holonym {
        public static final String KEY = "cg.rel:holonym";
        public static final ItemID IID = ItemID.fromString(KEY);
        @ItemFrame(predicate = SememeGloss.KEY,
                   fieldAs = @Bind(role = ThematicRole.Value.KEY, qualifiers = {Language.ENGLISH_KEY}))
        static final String gloss = "is a part of; is contained in";

        @ItemFrame(predicate = CoreVocabulary.CiliId.KEY)
        static final String cili = "i69567";

        @ItemFrame(predicate = CoreVocabulary.Lexeme.KEY,
                   fieldAs = @Bind(role = ThematicRole.Value.KEY,
                                   qualifiers = {Language.ENGLISH_KEY, PartOfSpeech.Verb.KEY, GrammaticalFeature.Lemma.KEY}, index = true))
        static final String[] words = {"holonym"};

        @ItemFrame(predicate = CoreVocabulary.Expects.KEY,
                   fieldAs = @Bind(role = ThematicRole.Topic.KEY, qualifiers = {ThematicRole.KEY, ThematicRole.Theme.KEY}))
        static final ItemID expectTheme = ThematicRole.Theme.IID;

        @ItemFrame(predicate = CoreVocabulary.Expects.KEY,
                   fieldAs = @Bind(role = ThematicRole.Topic.KEY, qualifiers = {ThematicRole.KEY, ThematicRole.Goal.KEY}))
        static final ItemID expectGoal = ThematicRole.Goal.IID;
    }

    @ItemSeed(key = Meronym.KEY)
    public static class Meronym {
        public static final String KEY = "cg.rel:meronym";
        public static final ItemID IID = ItemID.fromString(KEY);
        @ItemFrame(predicate = SememeGloss.KEY,
                   fieldAs = @Bind(role = ThematicRole.Value.KEY, qualifiers = {Language.ENGLISH_KEY}))
        static final String gloss = "has as a part; contains";

        @ItemFrame(predicate = CoreVocabulary.CiliId.KEY)
        static final String cili = "i69575";

        @ItemFrame(predicate = CoreVocabulary.Lexeme.KEY,
                   fieldAs = @Bind(role = ThematicRole.Value.KEY,
                                   qualifiers = {Language.ENGLISH_KEY, PartOfSpeech.Verb.KEY, GrammaticalFeature.Lemma.KEY}, index = true))
        static final String[] words = {"meronym"};

        @ItemFrame(predicate = CoreVocabulary.Expects.KEY,
                   fieldAs = @Bind(role = ThematicRole.Topic.KEY, qualifiers = {ThematicRole.KEY, ThematicRole.Theme.KEY}))
        static final ItemID expectTheme = ThematicRole.Theme.IID;

        @ItemFrame(predicate = CoreVocabulary.Expects.KEY,
                   fieldAs = @Bind(role = ThematicRole.Topic.KEY, qualifiers = {ThematicRole.KEY, ThematicRole.Goal.KEY}))
        static final ItemID expectGoal = ThematicRole.Goal.IID;
    }

    @ItemSeed(key = Antonym.KEY)
    public static class Antonym {
        public static final String KEY = "cg.rel:antonym";
        public static final ItemID IID = ItemID.fromString(KEY);
        @ItemFrame(predicate = SememeGloss.KEY,
                   fieldAs = @Bind(role = ThematicRole.Value.KEY, qualifiers = {Language.ENGLISH_KEY}))
        static final String gloss = "is the opposite of; contrasts with";

        @ItemFrame(predicate = CoreVocabulary.CiliId.KEY)
        static final String cili = "i69547";

        @ItemFrame(predicate = CoreVocabulary.Lexeme.KEY,
                   fieldAs = @Bind(role = ThematicRole.Value.KEY,
                                   qualifiers = {Language.ENGLISH_KEY, PartOfSpeech.Verb.KEY, GrammaticalFeature.Lemma.KEY}, index = true))
        static final String[] words = {"antonym"};

        @ItemFrame(predicate = CoreVocabulary.Expects.KEY,
                   fieldAs = @Bind(role = ThematicRole.Topic.KEY, qualifiers = {ThematicRole.KEY, ThematicRole.Theme.KEY}))
        static final ItemID expectTheme = ThematicRole.Theme.IID;

        @ItemFrame(predicate = CoreVocabulary.Expects.KEY,
                   fieldAs = @Bind(role = ThematicRole.Topic.KEY, qualifiers = {ThematicRole.KEY, ThematicRole.Goal.KEY}))
        static final ItemID expectGoal = ThematicRole.Goal.IID;
    }

    @ItemSeed(key = SimilarTo.KEY)
    public static class SimilarTo {
        public static final String KEY = "cg.rel:similar-to";
        public static final ItemID IID = ItemID.fromString(KEY);
        @ItemFrame(predicate = SememeGloss.KEY,
                   fieldAs = @Bind(role = ThematicRole.Value.KEY, qualifiers = {Language.ENGLISH_KEY}))
        static final String gloss = "is similar to; resembles in meaning";

        @ItemFrame(predicate = CoreVocabulary.CiliId.KEY)
        static final String cili = "i34992";

        @ItemFrame(predicate = CoreVocabulary.Lexeme.KEY,
                   fieldAs = @Bind(role = ThematicRole.Value.KEY,
                                   qualifiers = {Language.ENGLISH_KEY, PartOfSpeech.Verb.KEY, GrammaticalFeature.Lemma.KEY}, index = true))
        static final String[] words = {"similar-to"};

        @ItemFrame(predicate = CoreVocabulary.Expects.KEY,
                   fieldAs = @Bind(role = ThematicRole.Topic.KEY, qualifiers = {ThematicRole.KEY, ThematicRole.Theme.KEY}))
        static final ItemID expectTheme = ThematicRole.Theme.IID;

        @ItemFrame(predicate = CoreVocabulary.Expects.KEY,
                   fieldAs = @Bind(role = ThematicRole.Topic.KEY, qualifiers = {ThematicRole.KEY, ThematicRole.Goal.KEY}))
        static final ItemID expectGoal = ThematicRole.Goal.IID;
    }

    @ItemSeed(key = Derivation.KEY)
    public static class Derivation {
        public static final String KEY = "cg.rel:derivation";
        public static final ItemID IID = ItemID.fromString(KEY);
        @ItemFrame(predicate = SememeGloss.KEY,
                   fieldAs = @Bind(role = ThematicRole.Value.KEY, qualifiers = {Language.ENGLISH_KEY}))
        static final String gloss = "is derivationally related to";

        @ItemFrame(predicate = CoreVocabulary.CiliId.KEY)
        static final String cili = "i37467";

        @ItemFrame(predicate = CoreVocabulary.Lexeme.KEY,
                   fieldAs = @Bind(role = ThematicRole.Value.KEY,
                                   qualifiers = {Language.ENGLISH_KEY, PartOfSpeech.Verb.KEY, GrammaticalFeature.Lemma.KEY}, index = true))
        static final String[] words = {"derivation"};

        @ItemFrame(predicate = CoreVocabulary.Expects.KEY,
                   fieldAs = @Bind(role = ThematicRole.Topic.KEY, qualifiers = {ThematicRole.KEY, ThematicRole.Theme.KEY}))
        static final ItemID expectTheme = ThematicRole.Theme.IID;

        @ItemFrame(predicate = CoreVocabulary.Expects.KEY,
                   fieldAs = @Bind(role = ThematicRole.Topic.KEY, qualifiers = {ThematicRole.KEY, ThematicRole.Goal.KEY}))
        static final ItemID expectGoal = ThematicRole.Goal.IID;
    }

    @ItemSeed(key = Domain.KEY)
    public static class Domain {
        public static final String KEY = "cg.rel:domain";
        public static final ItemID IID = ItemID.fromString(KEY);
        @ItemFrame(predicate = SememeGloss.KEY,
                   fieldAs = @Bind(role = ThematicRole.Value.KEY, qualifiers = {Language.ENGLISH_KEY}))
        static final String gloss = "belongs to domain; is in the category of";

        @ItemFrame(predicate = CoreVocabulary.CiliId.KEY)
        static final String cili = "i68336";

        @ItemFrame(predicate = CoreVocabulary.Lexeme.KEY,
                   fieldAs = @Bind(role = ThematicRole.Value.KEY,
                                   qualifiers = {Language.ENGLISH_KEY, PartOfSpeech.Verb.KEY, GrammaticalFeature.Lemma.KEY}, index = true))
        static final String[] words = {"domain"};

        @ItemFrame(predicate = CoreVocabulary.Expects.KEY,
                   fieldAs = @Bind(role = ThematicRole.Topic.KEY, qualifiers = {ThematicRole.KEY, ThematicRole.Theme.KEY}))
        static final ItemID expectTheme = ThematicRole.Theme.IID;

        @ItemFrame(predicate = CoreVocabulary.Expects.KEY,
                   fieldAs = @Bind(role = ThematicRole.Topic.KEY, qualifiers = {ThematicRole.KEY, ThematicRole.Goal.KEY}))
        static final ItemID expectGoal = ThematicRole.Goal.IID;
    }

    @ItemSeed(key = Entails.KEY)
    public static class Entails {
        public static final String KEY = "cg.rel:entails";
        public static final ItemID IID = ItemID.fromString(KEY);
        @ItemFrame(predicate = SememeGloss.KEY,
                   fieldAs = @Bind(role = ThematicRole.Value.KEY, qualifiers = {Language.ENGLISH_KEY}))
        static final String gloss = "entails; necessarily implies";

        @ItemFrame(predicate = CoreVocabulary.CiliId.KEY)
        static final String cili = "i34848";

        @ItemFrame(predicate = CoreVocabulary.Lexeme.KEY,
                   fieldAs = @Bind(role = ThematicRole.Value.KEY,
                                   qualifiers = {Language.ENGLISH_KEY, PartOfSpeech.Verb.KEY, GrammaticalFeature.Lemma.KEY}, index = true))
        static final String[] words = {"entails"};

        @ItemFrame(predicate = CoreVocabulary.Expects.KEY,
                   fieldAs = @Bind(role = ThematicRole.Topic.KEY, qualifiers = {ThematicRole.KEY, ThematicRole.Theme.KEY}))
        static final ItemID expectTheme = ThematicRole.Theme.IID;

        @ItemFrame(predicate = CoreVocabulary.Expects.KEY,
                   fieldAs = @Bind(role = ThematicRole.Topic.KEY, qualifiers = {ThematicRole.KEY, ThematicRole.Goal.KEY}))
        static final ItemID expectGoal = ThematicRole.Goal.IID;
    }

    @ItemSeed(key = Causes.KEY)
    public static class Causes {
        public static final String KEY = "cg.rel:causes";
        public static final ItemID IID = ItemID.fromString(KEY);
        @ItemFrame(predicate = SememeGloss.KEY,
                   fieldAs = @Bind(role = ThematicRole.Value.KEY, qualifiers = {Language.ENGLISH_KEY}))
        static final String gloss = "causes; brings about";

        @ItemFrame(predicate = CoreVocabulary.CiliId.KEY)
        static final String cili = "i29966";

        @ItemFrame(predicate = CoreVocabulary.Lexeme.KEY,
                   fieldAs = @Bind(role = ThematicRole.Value.KEY,
                                   qualifiers = {Language.ENGLISH_KEY, PartOfSpeech.Verb.KEY, GrammaticalFeature.Lemma.KEY}, index = true))
        static final String[] words = {"causes"};

        @ItemFrame(predicate = CoreVocabulary.Expects.KEY,
                   fieldAs = @Bind(role = ThematicRole.Topic.KEY, qualifiers = {ThematicRole.KEY, ThematicRole.Theme.KEY}))
        static final ItemID expectTheme = ThematicRole.Theme.IID;

        @ItemFrame(predicate = CoreVocabulary.Expects.KEY,
                   fieldAs = @Bind(role = ThematicRole.Topic.KEY, qualifiers = {ThematicRole.KEY, ThematicRole.Goal.KEY}))
        static final ItemID expectGoal = ThematicRole.Goal.IID;
    }

    @ItemSeed(key = SeeAlso.KEY)
    public static class SeeAlso {
        public static final String KEY = "cg.rel:see-also";
        public static final ItemID IID = ItemID.fromString(KEY);
        @ItemFrame(predicate = SememeGloss.KEY,
                   fieldAs = @Bind(role = ThematicRole.Value.KEY, qualifiers = {Language.ENGLISH_KEY}))
        static final String gloss = "see also; is related to";

        @ItemFrame(predicate = CoreVocabulary.CiliId.KEY)
        static final String cili = "i25271";

        @ItemFrame(predicate = CoreVocabulary.Lexeme.KEY,
                   fieldAs = @Bind(role = ThematicRole.Value.KEY,
                                   qualifiers = {Language.ENGLISH_KEY, PartOfSpeech.Verb.KEY, GrammaticalFeature.Lemma.KEY}, index = true))
        static final String[] words = {"see-also"};

        @ItemFrame(predicate = CoreVocabulary.Expects.KEY,
                   fieldAs = @Bind(role = ThematicRole.Topic.KEY, qualifiers = {ThematicRole.KEY, ThematicRole.Theme.KEY}))
        static final ItemID expectTheme = ThematicRole.Theme.IID;

        @ItemFrame(predicate = CoreVocabulary.Expects.KEY,
                   fieldAs = @Bind(role = ThematicRole.Topic.KEY, qualifiers = {ThematicRole.KEY, ThematicRole.Goal.KEY}))
        static final ItemID expectGoal = ThematicRole.Goal.IID;
    }
}
