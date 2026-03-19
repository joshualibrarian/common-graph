package dev.everydaythings.graph.language;

import dev.everydaythings.graph.frame.ItemFrame;
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

    @ItemSeed(key = Hypernym.KEY, slots = {ThematicRole.Theme.KEY, ThematicRole.Goal.KEY})
    public static class Hypernym {
        public static final String KEY = "cg.rel:hypernym";
        public static final ItemID IID = ItemID.fromString(KEY);
        @ItemFrame(key = {SememeGloss.KEY, Language.ENGLISH_KEY})
        static final String gloss = "is a kind of; is a type of; is a subclass of";

        @ItemFrame(key = {CoreVocabulary.CiliId.KEY})
        static final String cili = "i69569";

        @ItemFrame(key = {CoreVocabulary.Lexeme.KEY, Language.ENGLISH_KEY, PartOfSpeech.Verb.KEY, GrammaticalFeature.Lemma.KEY})
        static final String verb1 = "hypernym";
    }

    @ItemSeed(key = Hyponym.KEY, slots = {ThematicRole.Theme.KEY, ThematicRole.Goal.KEY})
    public static class Hyponym {
        public static final String KEY = "cg.rel:hyponym";
        public static final ItemID IID = ItemID.fromString(KEY);
        @ItemFrame(key = {SememeGloss.KEY, Language.ENGLISH_KEY})
        static final String gloss = "has subtype; has kind; is a superclass of";

        @ItemFrame(key = {CoreVocabulary.CiliId.KEY})
        static final String cili = "i69570";

        @ItemFrame(key = {CoreVocabulary.Lexeme.KEY, Language.ENGLISH_KEY, PartOfSpeech.Verb.KEY, GrammaticalFeature.Lemma.KEY})
        static final String verb1 = "hyponym";
    }

    @ItemSeed(key = InstanceOf.KEY, slots = {ThematicRole.Theme.KEY, ThematicRole.Goal.KEY})
    public static class InstanceOf {
        public static final String KEY = "cg.rel:instance-of";
        public static final ItemID IID = ItemID.fromString(KEY);
        @ItemFrame(key = {SememeGloss.KEY, Language.ENGLISH_KEY})
        static final String gloss = "is an instance of; has type; is a member of class";

        @ItemFrame(key = {CoreVocabulary.CiliId.KEY})
        static final String cili = "i35284";

        @ItemFrame(key = {CoreVocabulary.Lexeme.KEY, Language.ENGLISH_KEY, PartOfSpeech.Verb.KEY, GrammaticalFeature.Lemma.KEY})
        static final String verb1 = "instance-of";
    }

    @ItemSeed(key = Holonym.KEY, slots = {ThematicRole.Theme.KEY, ThematicRole.Goal.KEY})
    public static class Holonym {
        public static final String KEY = "cg.rel:holonym";
        public static final ItemID IID = ItemID.fromString(KEY);
        @ItemFrame(key = {SememeGloss.KEY, Language.ENGLISH_KEY})
        static final String gloss = "is a part of; is contained in";

        @ItemFrame(key = {CoreVocabulary.CiliId.KEY})
        static final String cili = "i69567";

        @ItemFrame(key = {CoreVocabulary.Lexeme.KEY, Language.ENGLISH_KEY, PartOfSpeech.Verb.KEY, GrammaticalFeature.Lemma.KEY})
        static final String verb1 = "holonym";
    }

    @ItemSeed(key = Meronym.KEY, slots = {ThematicRole.Theme.KEY, ThematicRole.Goal.KEY})
    public static class Meronym {
        public static final String KEY = "cg.rel:meronym";
        public static final ItemID IID = ItemID.fromString(KEY);
        @ItemFrame(key = {SememeGloss.KEY, Language.ENGLISH_KEY})
        static final String gloss = "has as a part; contains";

        @ItemFrame(key = {CoreVocabulary.CiliId.KEY})
        static final String cili = "i69575";

        @ItemFrame(key = {CoreVocabulary.Lexeme.KEY, Language.ENGLISH_KEY, PartOfSpeech.Verb.KEY, GrammaticalFeature.Lemma.KEY})
        static final String verb1 = "meronym";
    }

    @ItemSeed(key = Antonym.KEY, slots = {ThematicRole.Theme.KEY, ThematicRole.Goal.KEY})
    public static class Antonym {
        public static final String KEY = "cg.rel:antonym";
        public static final ItemID IID = ItemID.fromString(KEY);
        @ItemFrame(key = {SememeGloss.KEY, Language.ENGLISH_KEY})
        static final String gloss = "is the opposite of; contrasts with";

        @ItemFrame(key = {CoreVocabulary.CiliId.KEY})
        static final String cili = "i69547";

        @ItemFrame(key = {CoreVocabulary.Lexeme.KEY, Language.ENGLISH_KEY, PartOfSpeech.Verb.KEY, GrammaticalFeature.Lemma.KEY})
        static final String verb1 = "antonym";
    }

    @ItemSeed(key = SimilarTo.KEY, slots = {ThematicRole.Theme.KEY, ThematicRole.Goal.KEY})
    public static class SimilarTo {
        public static final String KEY = "cg.rel:similar-to";
        public static final ItemID IID = ItemID.fromString(KEY);
        @ItemFrame(key = {SememeGloss.KEY, Language.ENGLISH_KEY})
        static final String gloss = "is similar to; resembles in meaning";

        @ItemFrame(key = {CoreVocabulary.CiliId.KEY})
        static final String cili = "i34992";

        @ItemFrame(key = {CoreVocabulary.Lexeme.KEY, Language.ENGLISH_KEY, PartOfSpeech.Verb.KEY, GrammaticalFeature.Lemma.KEY})
        static final String verb1 = "similar-to";
    }

    @ItemSeed(key = Derivation.KEY, slots = {ThematicRole.Theme.KEY, ThematicRole.Goal.KEY})
    public static class Derivation {
        public static final String KEY = "cg.rel:derivation";
        public static final ItemID IID = ItemID.fromString(KEY);
        @ItemFrame(key = {SememeGloss.KEY, Language.ENGLISH_KEY})
        static final String gloss = "is derivationally related to";

        @ItemFrame(key = {CoreVocabulary.CiliId.KEY})
        static final String cili = "i37467";

        @ItemFrame(key = {CoreVocabulary.Lexeme.KEY, Language.ENGLISH_KEY, PartOfSpeech.Verb.KEY, GrammaticalFeature.Lemma.KEY})
        static final String verb1 = "derivation";
    }

    @ItemSeed(key = Domain.KEY, slots = {ThematicRole.Theme.KEY, ThematicRole.Goal.KEY})
    public static class Domain {
        public static final String KEY = "cg.rel:domain";
        public static final ItemID IID = ItemID.fromString(KEY);
        @ItemFrame(key = {SememeGloss.KEY, Language.ENGLISH_KEY})
        static final String gloss = "belongs to domain; is in the category of";

        @ItemFrame(key = {CoreVocabulary.CiliId.KEY})
        static final String cili = "i68336";

        @ItemFrame(key = {CoreVocabulary.Lexeme.KEY, Language.ENGLISH_KEY, PartOfSpeech.Verb.KEY, GrammaticalFeature.Lemma.KEY})
        static final String verb1 = "domain";
    }

    @ItemSeed(key = Entails.KEY, slots = {ThematicRole.Theme.KEY, ThematicRole.Goal.KEY})
    public static class Entails {
        public static final String KEY = "cg.rel:entails";
        public static final ItemID IID = ItemID.fromString(KEY);
        @ItemFrame(key = {SememeGloss.KEY, Language.ENGLISH_KEY})
        static final String gloss = "entails; necessarily implies";

        @ItemFrame(key = {CoreVocabulary.CiliId.KEY})
        static final String cili = "i34848";

        @ItemFrame(key = {CoreVocabulary.Lexeme.KEY, Language.ENGLISH_KEY, PartOfSpeech.Verb.KEY, GrammaticalFeature.Lemma.KEY})
        static final String verb1 = "entails";
    }

    @ItemSeed(key = Causes.KEY, slots = {ThematicRole.Theme.KEY, ThematicRole.Goal.KEY})
    public static class Causes {
        public static final String KEY = "cg.rel:causes";
        public static final ItemID IID = ItemID.fromString(KEY);
        @ItemFrame(key = {SememeGloss.KEY, Language.ENGLISH_KEY})
        static final String gloss = "causes; brings about";

        @ItemFrame(key = {CoreVocabulary.CiliId.KEY})
        static final String cili = "i29966";

        @ItemFrame(key = {CoreVocabulary.Lexeme.KEY, Language.ENGLISH_KEY, PartOfSpeech.Verb.KEY, GrammaticalFeature.Lemma.KEY})
        static final String verb1 = "causes";
    }

    @ItemSeed(key = SeeAlso.KEY, slots = {ThematicRole.Theme.KEY, ThematicRole.Goal.KEY})
    public static class SeeAlso {
        public static final String KEY = "cg.rel:see-also";
        public static final ItemID IID = ItemID.fromString(KEY);
        @ItemFrame(key = {SememeGloss.KEY, Language.ENGLISH_KEY})
        static final String gloss = "see also; is related to";

        @ItemFrame(key = {CoreVocabulary.CiliId.KEY})
        static final String cili = "i25271";

        @ItemFrame(key = {CoreVocabulary.Lexeme.KEY, Language.ENGLISH_KEY, PartOfSpeech.Verb.KEY, GrammaticalFeature.Lemma.KEY})
        static final String verb1 = "see-also";
    }
}
