package dev.everydaythings.graph.language;

import dev.everydaythings.graph.item.ItemSeed;
import dev.everydaythings.graph.item.id.ItemID;

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
        @ItemSeed.Frame(key = {SememeGloss.KEY, Language.ENGLISH_KEY})
        static final String gloss = "is a kind of; is a type of; is a subclass of";

        @ItemSeed.Frame(key = {CoreVocabulary.CiliId.KEY})
        static final String cili = "i69569";

        @ItemSeed.Word(lang = Language.ENGLISH_KEY, pos = PartOfSpeech.Verb.KEY, features = {GrammaticalFeature.Lemma.KEY})
        static final String verb1 = "hypernym";
    }

    @ItemSeed(key = Hyponym.KEY, slots = {ThematicRole.Theme.KEY, ThematicRole.Goal.KEY})
    public static class Hyponym {
        public static final String KEY = "cg.rel:hyponym";
        public static final ItemID IID = ItemID.fromString(KEY);
        @ItemSeed.Frame(key = {SememeGloss.KEY, Language.ENGLISH_KEY})
        static final String gloss = "has subtype; has kind; is a superclass of";

        @ItemSeed.Frame(key = {CoreVocabulary.CiliId.KEY})
        static final String cili = "i69570";

        @ItemSeed.Word(lang = Language.ENGLISH_KEY, pos = PartOfSpeech.Verb.KEY, features = {GrammaticalFeature.Lemma.KEY})
        static final String verb1 = "hyponym";
    }

    @ItemSeed(key = InstanceOf.KEY, slots = {ThematicRole.Theme.KEY, ThematicRole.Goal.KEY})
    public static class InstanceOf {
        public static final String KEY = "cg.rel:instance-of";
        public static final ItemID IID = ItemID.fromString(KEY);
        @ItemSeed.Frame(key = {SememeGloss.KEY, Language.ENGLISH_KEY})
        static final String gloss = "is an instance of; has type; is a member of class";

        @ItemSeed.Frame(key = {CoreVocabulary.CiliId.KEY})
        static final String cili = "i35284";

        @ItemSeed.Word(lang = Language.ENGLISH_KEY, pos = PartOfSpeech.Verb.KEY, features = {GrammaticalFeature.Lemma.KEY})
        static final String verb1 = "instance-of";
    }

    @ItemSeed(key = Holonym.KEY, slots = {ThematicRole.Theme.KEY, ThematicRole.Goal.KEY})
    public static class Holonym {
        public static final String KEY = "cg.rel:holonym";
        public static final ItemID IID = ItemID.fromString(KEY);
        @ItemSeed.Frame(key = {SememeGloss.KEY, Language.ENGLISH_KEY})
        static final String gloss = "is a part of; is contained in";

        @ItemSeed.Frame(key = {CoreVocabulary.CiliId.KEY})
        static final String cili = "i69567";

        @ItemSeed.Word(lang = Language.ENGLISH_KEY, pos = PartOfSpeech.Verb.KEY, features = {GrammaticalFeature.Lemma.KEY})
        static final String verb1 = "holonym";
    }

    @ItemSeed(key = Meronym.KEY, slots = {ThematicRole.Theme.KEY, ThematicRole.Goal.KEY})
    public static class Meronym {
        public static final String KEY = "cg.rel:meronym";
        public static final ItemID IID = ItemID.fromString(KEY);
        @ItemSeed.Frame(key = {SememeGloss.KEY, Language.ENGLISH_KEY})
        static final String gloss = "has as a part; contains";

        @ItemSeed.Frame(key = {CoreVocabulary.CiliId.KEY})
        static final String cili = "i69575";

        @ItemSeed.Word(lang = Language.ENGLISH_KEY, pos = PartOfSpeech.Verb.KEY, features = {GrammaticalFeature.Lemma.KEY})
        static final String verb1 = "meronym";
    }

    @ItemSeed(key = Antonym.KEY, slots = {ThematicRole.Theme.KEY, ThematicRole.Goal.KEY})
    public static class Antonym {
        public static final String KEY = "cg.rel:antonym";
        public static final ItemID IID = ItemID.fromString(KEY);
        @ItemSeed.Frame(key = {SememeGloss.KEY, Language.ENGLISH_KEY})
        static final String gloss = "is the opposite of; contrasts with";

        @ItemSeed.Frame(key = {CoreVocabulary.CiliId.KEY})
        static final String cili = "i69547";

        @ItemSeed.Word(lang = Language.ENGLISH_KEY, pos = PartOfSpeech.Verb.KEY, features = {GrammaticalFeature.Lemma.KEY})
        static final String verb1 = "antonym";
    }

    @ItemSeed(key = SimilarTo.KEY, slots = {ThematicRole.Theme.KEY, ThematicRole.Goal.KEY})
    public static class SimilarTo {
        public static final String KEY = "cg.rel:similar-to";
        public static final ItemID IID = ItemID.fromString(KEY);
        @ItemSeed.Frame(key = {SememeGloss.KEY, Language.ENGLISH_KEY})
        static final String gloss = "is similar to; resembles in meaning";

        @ItemSeed.Frame(key = {CoreVocabulary.CiliId.KEY})
        static final String cili = "i34992";

        @ItemSeed.Word(lang = Language.ENGLISH_KEY, pos = PartOfSpeech.Verb.KEY, features = {GrammaticalFeature.Lemma.KEY})
        static final String verb1 = "similar-to";
    }

    @ItemSeed(key = Derivation.KEY, slots = {ThematicRole.Theme.KEY, ThematicRole.Goal.KEY})
    public static class Derivation {
        public static final String KEY = "cg.rel:derivation";
        public static final ItemID IID = ItemID.fromString(KEY);
        @ItemSeed.Frame(key = {SememeGloss.KEY, Language.ENGLISH_KEY})
        static final String gloss = "is derivationally related to";

        @ItemSeed.Frame(key = {CoreVocabulary.CiliId.KEY})
        static final String cili = "i37467";

        @ItemSeed.Word(lang = Language.ENGLISH_KEY, pos = PartOfSpeech.Verb.KEY, features = {GrammaticalFeature.Lemma.KEY})
        static final String verb1 = "derivation";
    }

    @ItemSeed(key = Domain.KEY, slots = {ThematicRole.Theme.KEY, ThematicRole.Goal.KEY})
    public static class Domain {
        public static final String KEY = "cg.rel:domain";
        public static final ItemID IID = ItemID.fromString(KEY);
        @ItemSeed.Frame(key = {SememeGloss.KEY, Language.ENGLISH_KEY})
        static final String gloss = "belongs to domain; is in the category of";

        @ItemSeed.Frame(key = {CoreVocabulary.CiliId.KEY})
        static final String cili = "i68336";

        @ItemSeed.Word(lang = Language.ENGLISH_KEY, pos = PartOfSpeech.Verb.KEY, features = {GrammaticalFeature.Lemma.KEY})
        static final String verb1 = "domain";
    }

    @ItemSeed(key = Entails.KEY, slots = {ThematicRole.Theme.KEY, ThematicRole.Goal.KEY})
    public static class Entails {
        public static final String KEY = "cg.rel:entails";
        public static final ItemID IID = ItemID.fromString(KEY);
        @ItemSeed.Frame(key = {SememeGloss.KEY, Language.ENGLISH_KEY})
        static final String gloss = "entails; necessarily implies";

        @ItemSeed.Frame(key = {CoreVocabulary.CiliId.KEY})
        static final String cili = "i34848";

        @ItemSeed.Word(lang = Language.ENGLISH_KEY, pos = PartOfSpeech.Verb.KEY, features = {GrammaticalFeature.Lemma.KEY})
        static final String verb1 = "entails";
    }

    @ItemSeed(key = Causes.KEY, slots = {ThematicRole.Theme.KEY, ThematicRole.Goal.KEY})
    public static class Causes {
        public static final String KEY = "cg.rel:causes";
        public static final ItemID IID = ItemID.fromString(KEY);
        @ItemSeed.Frame(key = {SememeGloss.KEY, Language.ENGLISH_KEY})
        static final String gloss = "causes; brings about";

        @ItemSeed.Frame(key = {CoreVocabulary.CiliId.KEY})
        static final String cili = "i29966";

        @ItemSeed.Word(lang = Language.ENGLISH_KEY, pos = PartOfSpeech.Verb.KEY, features = {GrammaticalFeature.Lemma.KEY})
        static final String verb1 = "causes";
    }

    @ItemSeed(key = SeeAlso.KEY, slots = {ThematicRole.Theme.KEY, ThematicRole.Goal.KEY})
    public static class SeeAlso {
        public static final String KEY = "cg.rel:see-also";
        public static final ItemID IID = ItemID.fromString(KEY);
        @ItemSeed.Frame(key = {SememeGloss.KEY, Language.ENGLISH_KEY})
        static final String gloss = "see also; is related to";

        @ItemSeed.Frame(key = {CoreVocabulary.CiliId.KEY})
        static final String cili = "i25271";

        @ItemSeed.Word(lang = Language.ENGLISH_KEY, pos = PartOfSpeech.Verb.KEY, features = {GrammaticalFeature.Lemma.KEY})
        static final String verb1 = "see-also";
    }
}
