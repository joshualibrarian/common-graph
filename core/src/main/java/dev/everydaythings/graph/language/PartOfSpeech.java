package dev.everydaythings.graph.language;

import dev.everydaythings.graph.item.ItemSeed;
import dev.everydaythings.graph.item.id.ItemID;

/**
 * Parts of speech as items — each is a seed Sememe with a deterministic IID.
 *
 * <p>A part of speech is just a concept — a noun that names a grammatical
 * category. {@code PART_OF_SPEECH} is a predicate, and every sememe has a
 * {@code PART_OF_SPEECH} frame whose value is one of these concept sememes.
 *
 * <p>All POS values are nouns (the word "verb" is a noun). Noun's POS
 * points to itself (self-referential). All others point to {@link #NOUN}.
 *
 * <p>The static {@link ItemID} constants ({@link #VERB}, {@link #NOUN}, etc.)
 * provide the same API shape as the old enum, keeping call sites unchanged.
 */
public final class PartOfSpeech {

    private PartOfSpeech() {}

    // ==================================================================================
    // ItemID CONSTANTS — same names as the old enum values
    // ==================================================================================

    public static final ItemID NOUN         = ItemID.fromString("cg.pos:noun");
    public static final ItemID VERB         = ItemID.fromString("cg.pos:verb");
    public static final ItemID ADJECTIVE    = ItemID.fromString("cg.pos:adjective");
    public static final ItemID ADVERB       = ItemID.fromString("cg.pos:adverb");
    public static final ItemID PRONOUN      = ItemID.fromString("cg.pos:pronoun");
    public static final ItemID CONJUNCTION  = ItemID.fromString("cg.pos:conjunction");
    public static final ItemID INTERJECTION = ItemID.fromString("cg.pos:interjection");
    public static final ItemID PREPOSITION  = ItemID.fromString("cg.pos:preposition");

    // ==================================================================================
    // PREDICATE — the PART_OF_SPEECH predicate itself
    // ==================================================================================

    /**
     * The predicate "part-of-speech" — every sememe has a frame keyed by this
     * predicate, with one of the POS value seeds as its target.
     */
    @ItemSeed(key = Predicate.KEY)
    public static class Predicate {
        public static final String KEY = "cg.core:part-of-speech";
        public static final ItemID IID = ItemID.fromString(KEY);
        @ItemSeed.Frame(key = {SememeGloss.KEY, Language.ENGLISH_KEY})
        static final String gloss = "the grammatical category of a word";

        @ItemSeed.Word(lang = Language.ENGLISH_KEY, pos = PartOfSpeech.Noun.KEY, features = {GrammaticalFeature.Lemma.KEY})
        static final String noun1 = "part-of-speech";
    }

    // ==================================================================================
    // POS VALUE SEEDS — all are nouns (the word "verb" is a noun)
    // ==================================================================================

    @ItemSeed(key = Noun.KEY)
    public static class Noun {
        public static final String KEY = "cg.pos:noun";
        public static final ItemID IID = ItemID.fromString(KEY);
        @ItemSeed.Frame(key = {SememeGloss.KEY, Language.ENGLISH_KEY})
        static final String gloss = "a word that names a person, place, thing, or idea";

        @ItemSeed.Frame(key = {CoreVocabulary.CiliId.KEY})
        static final String cili = "i73935";

        @ItemSeed.Word(lang = Language.ENGLISH_KEY, pos = PartOfSpeech.Noun.KEY, features = {GrammaticalFeature.Lemma.KEY})
        static final String noun1 = "noun";
    }

    @ItemSeed(key = Verb.KEY)
    public static class Verb {
        public static final String KEY = "cg.pos:verb";
        public static final ItemID IID = ItemID.fromString(KEY);
        @ItemSeed.Frame(key = {SememeGloss.KEY, Language.ENGLISH_KEY})
        static final String gloss = "a word that expresses an action or state";

        @ItemSeed.Frame(key = {CoreVocabulary.CiliId.KEY})
        static final String cili = "i73936";

        @ItemSeed.Word(lang = Language.ENGLISH_KEY, pos = PartOfSpeech.Noun.KEY, features = {GrammaticalFeature.Lemma.KEY})
        static final String noun1 = "verb";
    }

    @ItemSeed(key = Adjective.KEY)
    public static class Adjective {
        public static final String KEY = "cg.pos:adjective";
        public static final ItemID IID = ItemID.fromString(KEY);
        @ItemSeed.Frame(key = {SememeGloss.KEY, Language.ENGLISH_KEY})
        static final String gloss = "a word that modifies a noun";

        @ItemSeed.Frame(key = {CoreVocabulary.CiliId.KEY})
        static final String cili = "i73937";

        @ItemSeed.Word(lang = Language.ENGLISH_KEY, pos = PartOfSpeech.Noun.KEY, features = {GrammaticalFeature.Lemma.KEY})
        static final String noun1 = "adjective";
    }

    @ItemSeed(key = Adverb.KEY)
    public static class Adverb {
        public static final String KEY = "cg.pos:adverb";
        public static final ItemID IID = ItemID.fromString(KEY);
        @ItemSeed.Frame(key = {SememeGloss.KEY, Language.ENGLISH_KEY})
        static final String gloss = "a word that modifies a verb, adjective, or other adverb";

        @ItemSeed.Frame(key = {CoreVocabulary.CiliId.KEY})
        static final String cili = "i73938";

        @ItemSeed.Word(lang = Language.ENGLISH_KEY, pos = PartOfSpeech.Noun.KEY, features = {GrammaticalFeature.Lemma.KEY})
        static final String noun1 = "adverb";
    }

    @ItemSeed(key = Pronoun.KEY)
    public static class Pronoun {
        public static final String KEY = "cg.pos:pronoun";
        public static final ItemID IID = ItemID.fromString(KEY);
        @ItemSeed.Frame(key = {SememeGloss.KEY, Language.ENGLISH_KEY})
        static final String gloss = "a word that substitutes for a noun";

        @ItemSeed.Frame(key = {CoreVocabulary.CiliId.KEY})
        static final String cili = "i73939";

        @ItemSeed.Word(lang = Language.ENGLISH_KEY, pos = PartOfSpeech.Noun.KEY, features = {GrammaticalFeature.Lemma.KEY})
        static final String noun1 = "pronoun";
    }

    @ItemSeed(key = Conjunction.KEY)
    public static class Conjunction {
        public static final String KEY = "cg.pos:conjunction";
        public static final ItemID IID = ItemID.fromString(KEY);
        @ItemSeed.Frame(key = {SememeGloss.KEY, Language.ENGLISH_KEY})
        static final String gloss = "a word that connects clauses or sentences";

        @ItemSeed.Frame(key = {CoreVocabulary.CiliId.KEY})
        static final String cili = "i73940";

        @ItemSeed.Word(lang = Language.ENGLISH_KEY, pos = PartOfSpeech.Noun.KEY, features = {GrammaticalFeature.Lemma.KEY})
        static final String noun1 = "conjunction";
    }

    @ItemSeed(key = Interjection.KEY)
    public static class Interjection {
        public static final String KEY = "cg.pos:interjection";
        public static final ItemID IID = ItemID.fromString(KEY);
        @ItemSeed.Frame(key = {SememeGloss.KEY, Language.ENGLISH_KEY})
        static final String gloss = "a word expressing sudden feeling";

        @ItemSeed.Frame(key = {CoreVocabulary.CiliId.KEY})
        static final String cili = "i73941";

        @ItemSeed.Word(lang = Language.ENGLISH_KEY, pos = PartOfSpeech.Noun.KEY, features = {GrammaticalFeature.Lemma.KEY})
        static final String noun1 = "interjection";
    }

    @ItemSeed(key = Preposition.KEY)
    public static class Preposition {
        public static final String KEY = "cg.pos:preposition";
        public static final ItemID IID = ItemID.fromString(KEY);
        @ItemSeed.Frame(key = {SememeGloss.KEY, Language.ENGLISH_KEY})
        static final String gloss = "a word governing a noun or pronoun to express a relation";

        @ItemSeed.Frame(key = {CoreVocabulary.CiliId.KEY})
        static final String cili = "i73942";

        @ItemSeed.Word(lang = Language.ENGLISH_KEY, pos = PartOfSpeech.Noun.KEY, features = {GrammaticalFeature.Lemma.KEY})
        static final String noun1 = "preposition";
    }
}
