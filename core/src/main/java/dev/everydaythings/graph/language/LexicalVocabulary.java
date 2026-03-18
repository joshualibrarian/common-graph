package dev.everydaythings.graph.language;

import dev.everydaythings.graph.item.Item.Seed;
import dev.everydaythings.graph.item.ItemSeed;

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
        @Seed public static final Sememe SEED = new Sememe(KEY)
                .gloss(Sememe.ENG, "is a kind of; is a type of; is a subclass of")
                .cili("i69569")
                .slot(ThematicRole.Theme.KEY).slot(ThematicRole.Goal.KEY)
                .word(PartOfSpeech.VERB, GrammaticalFeature.Lemma.SEED, Sememe.ENG, "hypernym");

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
        @Seed public static final Sememe SEED = new Sememe(KEY)
                .gloss(Sememe.ENG, "has subtype; has kind; is a superclass of")
                .cili("i69570")
                .slot(ThematicRole.Theme.KEY).slot(ThematicRole.Goal.KEY)
                .word(PartOfSpeech.VERB, GrammaticalFeature.Lemma.SEED, Sememe.ENG, "hyponym");

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
        @Seed public static final Sememe SEED = new Sememe(KEY)
                .gloss(Sememe.ENG, "is an instance of; has type; is a member of class")
                .cili("i35284")
                .slot(ThematicRole.Theme.KEY).slot(ThematicRole.Goal.KEY)
                .word(PartOfSpeech.VERB, GrammaticalFeature.Lemma.SEED, Sememe.ENG, "instance-of");

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
        @Seed public static final Sememe SEED = new Sememe(KEY)
                .gloss(Sememe.ENG, "is a part of; is contained in")
                .cili("i69567")
                .slot(ThematicRole.Theme.KEY).slot(ThematicRole.Goal.KEY)
                .word(PartOfSpeech.VERB, GrammaticalFeature.Lemma.SEED, Sememe.ENG, "holonym");

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
        @Seed public static final Sememe SEED = new Sememe(KEY)
                .gloss(Sememe.ENG, "has as a part; contains")
                .cili("i69575")
                .slot(ThematicRole.Theme.KEY).slot(ThematicRole.Goal.KEY)
                .word(PartOfSpeech.VERB, GrammaticalFeature.Lemma.SEED, Sememe.ENG, "meronym");

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
        @Seed public static final Sememe SEED = new Sememe(KEY)
                .gloss(Sememe.ENG, "is the opposite of; contrasts with")
                .cili("i69547")
                .slot(ThematicRole.Theme.KEY).slot(ThematicRole.Goal.KEY)
                .word(PartOfSpeech.VERB, GrammaticalFeature.Lemma.SEED, Sememe.ENG, "antonym");

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
        @Seed public static final Sememe SEED = new Sememe(KEY)
                .gloss(Sememe.ENG, "is similar to; resembles in meaning")
                .cili("i34992")
                .slot(ThematicRole.Theme.KEY).slot(ThematicRole.Goal.KEY)
                .word(PartOfSpeech.VERB, GrammaticalFeature.Lemma.SEED, Sememe.ENG, "similar-to");

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
        @Seed public static final Sememe SEED = new Sememe(KEY)
                .gloss(Sememe.ENG, "is derivationally related to")
                .cili("i37467")
                .slot(ThematicRole.Theme.KEY).slot(ThematicRole.Goal.KEY)
                .word(PartOfSpeech.VERB, GrammaticalFeature.Lemma.SEED, Sememe.ENG, "derivation");

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
        @Seed public static final Sememe SEED = new Sememe(KEY)
                .gloss(Sememe.ENG, "belongs to domain; is in the category of")
                .cili("i68336")
                .slot(ThematicRole.Theme.KEY).slot(ThematicRole.Goal.KEY)
                .word(PartOfSpeech.VERB, GrammaticalFeature.Lemma.SEED, Sememe.ENG, "domain");

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
        @Seed public static final Sememe SEED = new Sememe(KEY)
                .gloss(Sememe.ENG, "entails; necessarily implies")
                .cili("i34848")
                .slot(ThematicRole.Theme.KEY).slot(ThematicRole.Goal.KEY)
                .word(PartOfSpeech.VERB, GrammaticalFeature.Lemma.SEED, Sememe.ENG, "entails");

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
        @Seed public static final Sememe SEED = new Sememe(KEY)
                .gloss(Sememe.ENG, "causes; brings about")
                .cili("i29966")
                .slot(ThematicRole.Theme.KEY).slot(ThematicRole.Goal.KEY)
                .word(PartOfSpeech.VERB, GrammaticalFeature.Lemma.SEED, Sememe.ENG, "causes");

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
        @Seed public static final Sememe SEED = new Sememe(KEY)
                .gloss(Sememe.ENG, "see also; is related to")
                .cili("i25271")
                .slot(ThematicRole.Theme.KEY).slot(ThematicRole.Goal.KEY)
                .word(PartOfSpeech.VERB, GrammaticalFeature.Lemma.SEED, Sememe.ENG, "see-also");

        @ItemSeed.Frame(key = {SememeGloss.KEY, Language.ENGLISH_KEY})
        static final String gloss = "see also; is related to";

        @ItemSeed.Frame(key = {CoreVocabulary.CiliId.KEY})
        static final String cili = "i25271";

        @ItemSeed.Word(lang = Language.ENGLISH_KEY, pos = PartOfSpeech.Verb.KEY, features = {GrammaticalFeature.Lemma.KEY})
        static final String verb1 = "see-also";
    }
}
