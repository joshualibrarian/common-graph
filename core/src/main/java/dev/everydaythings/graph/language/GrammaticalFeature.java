package dev.everydaythings.graph.language;

import dev.everydaythings.graph.frame.ItemFrame;
import dev.everydaythings.graph.item.Implements;
import dev.everydaythings.graph.item.ItemSeed;
import dev.everydaythings.graph.item.Manifest;
import dev.everydaythings.graph.item.id.ItemID;
import dev.everydaythings.graph.runtime.Librarian;

import java.util.List;
import java.util.Map;
import dev.everydaythings.graph.language.CoreVocabulary;

/**
 * A grammatical feature sememe — describes an inflectional property of a word form.
 *
 * <p>Grammatical features (tense, number, person, degree, mood, etc.) are
 * language-agnostic concepts referenced by {@link ItemID}. Lexemes store
 * irregular forms keyed by sets of these features; Language subclasses
 * apply regular inflection rules based on feature sets.
 *
 * <p>Not all features are universal. Most languages distinguish singular/plural,
 * but some have DUAL (Arabic), PAUCAL (some Oceanic languages), or lack
 * number entirely. New features can be added as seed vocabulary without
 * changing any code.
 *
 * <p>Feature sets are minimal — you specify only the distinctive features.
 * For example, English past tense is just {PAST}, not {PAST, INDICATIVE, ACTIVE}.
 *
 * @see Lexeme
 * @see Language#inflect(Lexeme, java.util.Set)
 */
@Implements(GrammaticalFeature.KEY)
public class GrammaticalFeature extends Sememe {

    public static final String KEY = "cg.sememe:grammatical-feature";

    // ==================================================================================
    // BASE FORM
    // ==================================================================================

    @ItemSeed(key = Lemma.KEY)
    public static class Lemma {
        public static final String KEY = "cg.feat:lemma";
        public static final ItemID IID = ItemID.fromString(KEY);
        @ItemFrame(key = {SememeGloss.KEY, Language.ENGLISH_KEY})
        static final String gloss = "the base or dictionary form of a word";

        @ItemFrame(key = {CoreVocabulary.CiliId.KEY})
        static final String cili = "i71975";

        @ItemFrame(key = {CoreVocabulary.Lexeme.KEY, Language.ENGLISH_KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY})
        static final String noun = "lemma";
    }

    // ==================================================================================
    // TENSE
    // ==================================================================================

    @ItemSeed(key = Past.KEY)
    public static class Past {
        public static final String KEY = "cg.feat:past";
        public static final ItemID IID = ItemID.fromString(KEY);
        @ItemFrame(key = {SememeGloss.KEY, Language.ENGLISH_KEY})
        static final String gloss = "past tense";

        @ItemFrame(key = {CoreVocabulary.CiliId.KEY})
        static final String cili = "i69743";

        @ItemFrame(key = {CoreVocabulary.Lexeme.KEY, Language.ENGLISH_KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY})
        static final String noun = "past";
    }

    @ItemSeed(key = Present.KEY)
    public static class Present {
        public static final String KEY = "cg.feat:present";
        public static final ItemID IID = ItemID.fromString(KEY);
        @ItemFrame(key = {SememeGloss.KEY, Language.ENGLISH_KEY})
        static final String gloss = "present tense";

        @ItemFrame(key = {CoreVocabulary.CiliId.KEY})
        static final String cili = "i69740";

        @ItemFrame(key = {CoreVocabulary.Lexeme.KEY, Language.ENGLISH_KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY})
        static final String noun = "present";
    }

    @ItemSeed(key = Future.KEY)
    public static class Future {
        public static final String KEY = "cg.feat:future";
        public static final ItemID IID = ItemID.fromString(KEY);
        @ItemFrame(key = {SememeGloss.KEY, Language.ENGLISH_KEY})
        static final String gloss = "future tense";

        @ItemFrame(key = {CoreVocabulary.CiliId.KEY})
        static final String cili = "i69744";

        @ItemFrame(key = {CoreVocabulary.Lexeme.KEY, Language.ENGLISH_KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY})
        static final String noun = "future";
    }

    // ==================================================================================
    // NUMBER
    // ==================================================================================

    @ItemSeed(key = Singular.KEY)
    public static class Singular {
        public static final String KEY = "cg.feat:singular";
        public static final ItemID IID = ItemID.fromString(KEY);
        @ItemFrame(key = {SememeGloss.KEY, Language.ENGLISH_KEY})
        static final String gloss = "singular number";

        @ItemFrame(key = {CoreVocabulary.CiliId.KEY})
        static final String cili = "i69586";

        @ItemFrame(key = {CoreVocabulary.Lexeme.KEY, Language.ENGLISH_KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY})
        static final String noun = "singular";
    }

    @ItemSeed(key = Plural.KEY)
    public static class Plural {
        public static final String KEY = "cg.feat:plural";
        public static final ItemID IID = ItemID.fromString(KEY);
        @ItemFrame(key = {SememeGloss.KEY, Language.ENGLISH_KEY})
        static final String gloss = "plural number";

        @ItemFrame(key = {CoreVocabulary.CiliId.KEY})
        static final String cili = "i69585";

        @ItemFrame(key = {CoreVocabulary.Lexeme.KEY, Language.ENGLISH_KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY})
        static final String noun = "plural";
    }

    // ==================================================================================
    // PERSON
    // ==================================================================================

    @ItemSeed(key = FirstPerson.KEY)
    public static class FirstPerson {
        public static final String KEY = "cg.feat:first-person";
        public static final ItemID IID = ItemID.fromString(KEY);
        @ItemFrame(key = {SememeGloss.KEY, Language.ENGLISH_KEY})
        static final String gloss = "first person";

        @ItemFrame(key = {CoreVocabulary.CiliId.KEY})
        static final String cili = "i69730";

        @ItemFrame(key = {CoreVocabulary.Lexeme.KEY, Language.ENGLISH_KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY})
        static final String noun = "first-person";
    }

    @ItemSeed(key = SecondPerson.KEY)
    public static class SecondPerson {
        public static final String KEY = "cg.feat:second-person";
        public static final ItemID IID = ItemID.fromString(KEY);
        @ItemFrame(key = {SememeGloss.KEY, Language.ENGLISH_KEY})
        static final String gloss = "second person";

        @ItemFrame(key = {CoreVocabulary.CiliId.KEY})
        static final String cili = "i69731";

        @ItemFrame(key = {CoreVocabulary.Lexeme.KEY, Language.ENGLISH_KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY})
        static final String noun = "second-person";
    }

    @ItemSeed(key = ThirdPerson.KEY)
    public static class ThirdPerson {
        public static final String KEY = "cg.feat:third-person";
        public static final ItemID IID = ItemID.fromString(KEY);
        @ItemFrame(key = {SememeGloss.KEY, Language.ENGLISH_KEY})
        static final String gloss = "third person";

        @ItemFrame(key = {CoreVocabulary.CiliId.KEY})
        static final String cili = "i69732";

        @ItemFrame(key = {CoreVocabulary.Lexeme.KEY, Language.ENGLISH_KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY})
        static final String noun = "third-person";
    }

    // ==================================================================================
    // FORM / ASPECT
    // ==================================================================================

    @ItemSeed(key = Participle.KEY)
    public static class Participle {
        public static final String KEY = "cg.feat:participle";
        public static final ItemID IID = ItemID.fromString(KEY);
        @ItemFrame(key = {SememeGloss.KEY, Language.ENGLISH_KEY})
        static final String gloss = "participle form";

        @ItemFrame(key = {CoreVocabulary.CiliId.KEY})
        static final String cili = "i69745";

        @ItemFrame(key = {CoreVocabulary.Lexeme.KEY, Language.ENGLISH_KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY})
        static final String noun = "participle";
    }

    @ItemSeed(key = Progressive.KEY)
    public static class Progressive {
        public static final String KEY = "cg.feat:progressive";
        public static final ItemID IID = ItemID.fromString(KEY);
        @ItemFrame(key = {SememeGloss.KEY, Language.ENGLISH_KEY})
        static final String gloss = "progressive aspect";

        @ItemFrame(key = {CoreVocabulary.CiliId.KEY})
        static final String cili = "i109457";

        @ItemFrame(key = {CoreVocabulary.Lexeme.KEY, Language.ENGLISH_KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY})
        static final String noun = "progressive";
    }

    @ItemSeed(key = Perfect.KEY)
    public static class Perfect {
        public static final String KEY = "cg.feat:perfect";
        public static final ItemID IID = ItemID.fromString(KEY);
        @ItemFrame(key = {SememeGloss.KEY, Language.ENGLISH_KEY})
        static final String gloss = "perfect aspect";

        @ItemFrame(key = {CoreVocabulary.CiliId.KEY})
        static final String cili = "i109459";

        @ItemFrame(key = {CoreVocabulary.Lexeme.KEY, Language.ENGLISH_KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY})
        static final String noun = "perfect";
    }

    // ==================================================================================
    // MOOD
    // ==================================================================================

    @ItemSeed(key = Imperative.KEY)
    public static class Imperative {
        public static final String KEY = "cg.feat:imperative";
        public static final ItemID IID = ItemID.fromString(KEY);
        @ItemFrame(key = {SememeGloss.KEY, Language.ENGLISH_KEY})
        static final String gloss = "imperative mood";

        @ItemFrame(key = {CoreVocabulary.CiliId.KEY})
        static final String cili = "i109438";

        @ItemFrame(key = {CoreVocabulary.Lexeme.KEY, Language.ENGLISH_KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY})
        static final String noun = "imperative";
    }

    @ItemSeed(key = Subjunctive.KEY)
    public static class Subjunctive {
        public static final String KEY = "cg.feat:subjunctive";
        public static final ItemID IID = ItemID.fromString(KEY);
        @ItemFrame(key = {SememeGloss.KEY, Language.ENGLISH_KEY})
        static final String gloss = "subjunctive mood";

        @ItemFrame(key = {CoreVocabulary.CiliId.KEY})
        static final String cili = "i109436";

        @ItemFrame(key = {CoreVocabulary.Lexeme.KEY, Language.ENGLISH_KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY})
        static final String noun = "subjunctive";
    }

    @ItemSeed(key = Infinitive.KEY)
    public static class Infinitive {
        public static final String KEY = "cg.feat:infinitive";
        public static final ItemID IID = ItemID.fromString(KEY);
        @ItemFrame(key = {SememeGloss.KEY, Language.ENGLISH_KEY})
        static final String gloss = "infinitive form";

        @ItemFrame(key = {CoreVocabulary.CiliId.KEY})
        static final String cili = "i69687";

        @ItemFrame(key = {CoreVocabulary.Lexeme.KEY, Language.ENGLISH_KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY})
        static final String noun = "infinitive";
    }

    // ==================================================================================
    // DEGREE (adjectives/adverbs)
    // ==================================================================================

    @ItemSeed(key = Comparative.KEY)
    public static class Comparative {
        public static final String KEY = "cg.feat:comparative";
        public static final ItemID IID = ItemID.fromString(KEY);
        @ItemFrame(key = {SememeGloss.KEY, Language.ENGLISH_KEY})
        static final String gloss = "comparative degree";

        @ItemFrame(key = {CoreVocabulary.CiliId.KEY})
        static final String cili = "i69707";

        @ItemFrame(key = {CoreVocabulary.Lexeme.KEY, Language.ENGLISH_KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY})
        static final String noun = "comparative";
    }

    @ItemSeed(key = Superlative.KEY)
    public static class Superlative {
        public static final String KEY = "cg.feat:superlative";
        public static final ItemID IID = ItemID.fromString(KEY);
        @ItemFrame(key = {SememeGloss.KEY, Language.ENGLISH_KEY})
        static final String gloss = "superlative degree";

        @ItemFrame(key = {CoreVocabulary.CiliId.KEY})
        static final String cili = "i69708";

        @ItemFrame(key = {CoreVocabulary.Lexeme.KEY, Language.ENGLISH_KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY})
        static final String noun = "superlative";
    }

    // ==================================================================================
    // VOICE
    // ==================================================================================

    @ItemSeed(key = Passive.KEY)
    public static class Passive {
        public static final String KEY = "cg.feat:passive";
        public static final ItemID IID = ItemID.fromString(KEY);
        @ItemFrame(key = {SememeGloss.KEY, Language.ENGLISH_KEY})
        static final String gloss = "passive voice";

        @ItemFrame(key = {CoreVocabulary.CiliId.KEY})
        static final String cili = "i109444";

        @ItemFrame(key = {CoreVocabulary.Lexeme.KEY, Language.ENGLISH_KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY})
        static final String noun = "passive";
    }

    // ==================================================================================
    // CONSTRUCTORS
    // ==================================================================================

    /** Type seed constructor. */
    @SuppressWarnings("unused")
    protected GrammaticalFeature(ItemID typeId) {
        super(typeId);
    }

    /** Hydration constructor. */
    @SuppressWarnings("unused")
    protected GrammaticalFeature(Librarian librarian, Manifest manifest) {
        super(librarian, manifest);
    }

    /** Fluent seed constructor. */
    public GrammaticalFeature(String canonicalKey) {
        super(canonicalKey);
    }

    /** Seed constructor. */
    public GrammaticalFeature(String canonicalKey, Map<String, String> glosses, List<String> tokens) {
        super(canonicalKey, glosses, Map.of(), tokens);
    }

    /** Seed constructor (with sources for CILI). */
    public GrammaticalFeature(String canonicalKey, Map<String, String> glosses,
                              Map<String, String> sources, List<String> tokens) {
        super(canonicalKey, glosses, sources, tokens);
    }

    // ==================================================================================
    // COVARIANT OVERRIDES (fluent chaining returns GrammaticalFeature)
    // ==================================================================================

    @Override public GrammaticalFeature gloss(String lang, String text) { super.gloss(lang, text); return this; }
    @Override public GrammaticalFeature word(Sememe form, String lang, String surface) { super.word(form, lang, surface); return this; }
    @Override public GrammaticalFeature cili(String id) { super.cili(id); return this; }
    @Override public GrammaticalFeature symbol(String s) { super.symbol(s); return this; }
    @Override public GrammaticalFeature indexWeight(int weight) { super.indexWeight(weight); return this; }
}
