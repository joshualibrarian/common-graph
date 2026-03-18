package dev.everydaythings.graph.language;

import dev.everydaythings.graph.item.Implements;
import dev.everydaythings.graph.item.ItemSeed;
import dev.everydaythings.graph.item.Manifest;
import dev.everydaythings.graph.item.id.ItemID;
import dev.everydaythings.graph.runtime.Librarian;

import java.util.List;
import java.util.Map;

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
@Implements(GrammaticalFeature.TypeSeed.KEY)
public class GrammaticalFeature extends Sememe {

    public static final String KEY = TypeSeed.KEY;

    @ItemSeed(key = TypeSeed.KEY)
    public static class TypeSeed {
        public static final String KEY = "cg.sememe:grammatical-feature";
        @Seed public static final Sememe SEED = new Sememe(KEY)
                .gloss("en", "an inflectional property of a word form")
                .word(PartOfSpeech.NOUN, GrammaticalFeature.Lemma.SEED, "en", "feature");

        @ItemSeed.Frame(key = {SememeGloss.KEY, Language.ENGLISH_KEY})
        static final String gloss = "an inflectional property of a word form";

        @ItemSeed.Word(lang = Language.ENGLISH_KEY, pos = PartOfSpeech.Noun.KEY,
                features = {GrammaticalFeature.Lemma.KEY})
        static final String noun = "feature";
    }

    // ==================================================================================
    // BASE FORM
    // ==================================================================================

    @ItemSeed(key = Lemma.KEY)
    public static class Lemma {
        public static final String KEY = "cg.feat:lemma";
        @Seed public static final GrammaticalFeature SEED = new GrammaticalFeature(KEY)
                .gloss(ENG, "the base or dictionary form of a word")
                .cili("i71975").word(LEMMA, ENG, "lemma");

        @ItemSeed.Frame(key = {SememeGloss.KEY, Language.ENGLISH_KEY})
        static final String gloss = "the base or dictionary form of a word";

        @ItemSeed.Frame(key = {CoreVocabulary.CiliId.KEY})
        static final String cili = "i71975";

        @ItemSeed.Word(lang = Language.ENGLISH_KEY, pos = PartOfSpeech.Noun.KEY,
                features = {GrammaticalFeature.Lemma.KEY})
        static final String noun = "lemma";
    }

    // ==================================================================================
    // TENSE
    // ==================================================================================

    @ItemSeed(key = Past.KEY)
    public static class Past {
        public static final String KEY = "cg.feat:past";
        @Seed public static final GrammaticalFeature SEED = new GrammaticalFeature(KEY)
                .gloss(ENG, "past tense")
                .cili("i69743").word(LEMMA, ENG, "past");

        @ItemSeed.Frame(key = {SememeGloss.KEY, Language.ENGLISH_KEY})
        static final String gloss = "past tense";

        @ItemSeed.Frame(key = {CoreVocabulary.CiliId.KEY})
        static final String cili = "i69743";

        @ItemSeed.Word(lang = Language.ENGLISH_KEY, pos = PartOfSpeech.Noun.KEY,
                features = {GrammaticalFeature.Lemma.KEY})
        static final String noun = "past";
    }

    @ItemSeed(key = Present.KEY)
    public static class Present {
        public static final String KEY = "cg.feat:present";
        @Seed public static final GrammaticalFeature SEED = new GrammaticalFeature(KEY)
                .gloss(ENG, "present tense")
                .cili("i69740").word(LEMMA, ENG, "present");

        @ItemSeed.Frame(key = {SememeGloss.KEY, Language.ENGLISH_KEY})
        static final String gloss = "present tense";

        @ItemSeed.Frame(key = {CoreVocabulary.CiliId.KEY})
        static final String cili = "i69740";

        @ItemSeed.Word(lang = Language.ENGLISH_KEY, pos = PartOfSpeech.Noun.KEY,
                features = {GrammaticalFeature.Lemma.KEY})
        static final String noun = "present";
    }

    @ItemSeed(key = Future.KEY)
    public static class Future {
        public static final String KEY = "cg.feat:future";
        @Seed public static final GrammaticalFeature SEED = new GrammaticalFeature(KEY)
                .gloss(ENG, "future tense")
                .cili("i69744").word(LEMMA, ENG, "future");

        @ItemSeed.Frame(key = {SememeGloss.KEY, Language.ENGLISH_KEY})
        static final String gloss = "future tense";

        @ItemSeed.Frame(key = {CoreVocabulary.CiliId.KEY})
        static final String cili = "i69744";

        @ItemSeed.Word(lang = Language.ENGLISH_KEY, pos = PartOfSpeech.Noun.KEY,
                features = {GrammaticalFeature.Lemma.KEY})
        static final String noun = "future";
    }

    // ==================================================================================
    // NUMBER
    // ==================================================================================

    @ItemSeed(key = Singular.KEY)
    public static class Singular {
        public static final String KEY = "cg.feat:singular";
        @Seed public static final GrammaticalFeature SEED = new GrammaticalFeature(KEY)
                .gloss(ENG, "singular number")
                .cili("i69586").word(LEMMA, ENG, "singular");

        @ItemSeed.Frame(key = {SememeGloss.KEY, Language.ENGLISH_KEY})
        static final String gloss = "singular number";

        @ItemSeed.Frame(key = {CoreVocabulary.CiliId.KEY})
        static final String cili = "i69586";

        @ItemSeed.Word(lang = Language.ENGLISH_KEY, pos = PartOfSpeech.Noun.KEY,
                features = {GrammaticalFeature.Lemma.KEY})
        static final String noun = "singular";
    }

    @ItemSeed(key = Plural.KEY)
    public static class Plural {
        public static final String KEY = "cg.feat:plural";
        @Seed public static final GrammaticalFeature SEED = new GrammaticalFeature(KEY)
                .gloss(ENG, "plural number")
                .cili("i69585").word(LEMMA, ENG, "plural");

        @ItemSeed.Frame(key = {SememeGloss.KEY, Language.ENGLISH_KEY})
        static final String gloss = "plural number";

        @ItemSeed.Frame(key = {CoreVocabulary.CiliId.KEY})
        static final String cili = "i69585";

        @ItemSeed.Word(lang = Language.ENGLISH_KEY, pos = PartOfSpeech.Noun.KEY,
                features = {GrammaticalFeature.Lemma.KEY})
        static final String noun = "plural";
    }

    // ==================================================================================
    // PERSON
    // ==================================================================================

    @ItemSeed(key = FirstPerson.KEY)
    public static class FirstPerson {
        public static final String KEY = "cg.feat:first-person";
        @Seed public static final GrammaticalFeature SEED = new GrammaticalFeature(KEY)
                .gloss(ENG, "first person")
                .cili("i69730").word(LEMMA, ENG, "first-person");

        @ItemSeed.Frame(key = {SememeGloss.KEY, Language.ENGLISH_KEY})
        static final String gloss = "first person";

        @ItemSeed.Frame(key = {CoreVocabulary.CiliId.KEY})
        static final String cili = "i69730";

        @ItemSeed.Word(lang = Language.ENGLISH_KEY, pos = PartOfSpeech.Noun.KEY,
                features = {GrammaticalFeature.Lemma.KEY})
        static final String noun = "first-person";
    }

    @ItemSeed(key = SecondPerson.KEY)
    public static class SecondPerson {
        public static final String KEY = "cg.feat:second-person";
        @Seed public static final GrammaticalFeature SEED = new GrammaticalFeature(KEY)
                .gloss(ENG, "second person")
                .cili("i69731").word(LEMMA, ENG, "second-person");

        @ItemSeed.Frame(key = {SememeGloss.KEY, Language.ENGLISH_KEY})
        static final String gloss = "second person";

        @ItemSeed.Frame(key = {CoreVocabulary.CiliId.KEY})
        static final String cili = "i69731";

        @ItemSeed.Word(lang = Language.ENGLISH_KEY, pos = PartOfSpeech.Noun.KEY,
                features = {GrammaticalFeature.Lemma.KEY})
        static final String noun = "second-person";
    }

    @ItemSeed(key = ThirdPerson.KEY)
    public static class ThirdPerson {
        public static final String KEY = "cg.feat:third-person";
        @Seed public static final GrammaticalFeature SEED = new GrammaticalFeature(KEY)
                .gloss(ENG, "third person")
                .cili("i69732").word(LEMMA, ENG, "third-person");

        @ItemSeed.Frame(key = {SememeGloss.KEY, Language.ENGLISH_KEY})
        static final String gloss = "third person";

        @ItemSeed.Frame(key = {CoreVocabulary.CiliId.KEY})
        static final String cili = "i69732";

        @ItemSeed.Word(lang = Language.ENGLISH_KEY, pos = PartOfSpeech.Noun.KEY,
                features = {GrammaticalFeature.Lemma.KEY})
        static final String noun = "third-person";
    }

    // ==================================================================================
    // FORM / ASPECT
    // ==================================================================================

    @ItemSeed(key = Participle.KEY)
    public static class Participle {
        public static final String KEY = "cg.feat:participle";
        @Seed public static final GrammaticalFeature SEED = new GrammaticalFeature(KEY)
                .gloss(ENG, "participle form")
                .cili("i69745").word(LEMMA, ENG, "participle");

        @ItemSeed.Frame(key = {SememeGloss.KEY, Language.ENGLISH_KEY})
        static final String gloss = "participle form";

        @ItemSeed.Frame(key = {CoreVocabulary.CiliId.KEY})
        static final String cili = "i69745";

        @ItemSeed.Word(lang = Language.ENGLISH_KEY, pos = PartOfSpeech.Noun.KEY,
                features = {GrammaticalFeature.Lemma.KEY})
        static final String noun = "participle";
    }

    @ItemSeed(key = Progressive.KEY)
    public static class Progressive {
        public static final String KEY = "cg.feat:progressive";
        @Seed public static final GrammaticalFeature SEED = new GrammaticalFeature(KEY)
                .gloss(ENG, "progressive aspect")
                .cili("i109457").word(LEMMA, ENG, "progressive");

        @ItemSeed.Frame(key = {SememeGloss.KEY, Language.ENGLISH_KEY})
        static final String gloss = "progressive aspect";

        @ItemSeed.Frame(key = {CoreVocabulary.CiliId.KEY})
        static final String cili = "i109457";

        @ItemSeed.Word(lang = Language.ENGLISH_KEY, pos = PartOfSpeech.Noun.KEY,
                features = {GrammaticalFeature.Lemma.KEY})
        static final String noun = "progressive";
    }

    @ItemSeed(key = Perfect.KEY)
    public static class Perfect {
        public static final String KEY = "cg.feat:perfect";
        @Seed public static final GrammaticalFeature SEED = new GrammaticalFeature(KEY)
                .gloss(ENG, "perfect aspect")
                .cili("i109459").word(LEMMA, ENG, "perfect");

        @ItemSeed.Frame(key = {SememeGloss.KEY, Language.ENGLISH_KEY})
        static final String gloss = "perfect aspect";

        @ItemSeed.Frame(key = {CoreVocabulary.CiliId.KEY})
        static final String cili = "i109459";

        @ItemSeed.Word(lang = Language.ENGLISH_KEY, pos = PartOfSpeech.Noun.KEY,
                features = {GrammaticalFeature.Lemma.KEY})
        static final String noun = "perfect";
    }

    // ==================================================================================
    // MOOD
    // ==================================================================================

    @ItemSeed(key = Imperative.KEY)
    public static class Imperative {
        public static final String KEY = "cg.feat:imperative";
        @Seed public static final GrammaticalFeature SEED = new GrammaticalFeature(KEY)
                .gloss(ENG, "imperative mood")
                .cili("i109438").word(LEMMA, ENG, "imperative");

        @ItemSeed.Frame(key = {SememeGloss.KEY, Language.ENGLISH_KEY})
        static final String gloss = "imperative mood";

        @ItemSeed.Frame(key = {CoreVocabulary.CiliId.KEY})
        static final String cili = "i109438";

        @ItemSeed.Word(lang = Language.ENGLISH_KEY, pos = PartOfSpeech.Noun.KEY,
                features = {GrammaticalFeature.Lemma.KEY})
        static final String noun = "imperative";
    }

    @ItemSeed(key = Subjunctive.KEY)
    public static class Subjunctive {
        public static final String KEY = "cg.feat:subjunctive";
        @Seed public static final GrammaticalFeature SEED = new GrammaticalFeature(KEY)
                .gloss(ENG, "subjunctive mood")
                .cili("i109436").word(LEMMA, ENG, "subjunctive");

        @ItemSeed.Frame(key = {SememeGloss.KEY, Language.ENGLISH_KEY})
        static final String gloss = "subjunctive mood";

        @ItemSeed.Frame(key = {CoreVocabulary.CiliId.KEY})
        static final String cili = "i109436";

        @ItemSeed.Word(lang = Language.ENGLISH_KEY, pos = PartOfSpeech.Noun.KEY,
                features = {GrammaticalFeature.Lemma.KEY})
        static final String noun = "subjunctive";
    }

    @ItemSeed(key = Infinitive.KEY)
    public static class Infinitive {
        public static final String KEY = "cg.feat:infinitive";
        @Seed public static final GrammaticalFeature SEED = new GrammaticalFeature(KEY)
                .gloss(ENG, "infinitive form")
                .cili("i69687").word(LEMMA, ENG, "infinitive");

        @ItemSeed.Frame(key = {SememeGloss.KEY, Language.ENGLISH_KEY})
        static final String gloss = "infinitive form";

        @ItemSeed.Frame(key = {CoreVocabulary.CiliId.KEY})
        static final String cili = "i69687";

        @ItemSeed.Word(lang = Language.ENGLISH_KEY, pos = PartOfSpeech.Noun.KEY,
                features = {GrammaticalFeature.Lemma.KEY})
        static final String noun = "infinitive";
    }

    // ==================================================================================
    // DEGREE (adjectives/adverbs)
    // ==================================================================================

    @ItemSeed(key = Comparative.KEY)
    public static class Comparative {
        public static final String KEY = "cg.feat:comparative";
        @Seed public static final GrammaticalFeature SEED = new GrammaticalFeature(KEY)
                .gloss(ENG, "comparative degree")
                .cili("i69707").word(LEMMA, ENG, "comparative");

        @ItemSeed.Frame(key = {SememeGloss.KEY, Language.ENGLISH_KEY})
        static final String gloss = "comparative degree";

        @ItemSeed.Frame(key = {CoreVocabulary.CiliId.KEY})
        static final String cili = "i69707";

        @ItemSeed.Word(lang = Language.ENGLISH_KEY, pos = PartOfSpeech.Noun.KEY,
                features = {GrammaticalFeature.Lemma.KEY})
        static final String noun = "comparative";
    }

    @ItemSeed(key = Superlative.KEY)
    public static class Superlative {
        public static final String KEY = "cg.feat:superlative";
        @Seed public static final GrammaticalFeature SEED = new GrammaticalFeature(KEY)
                .gloss(ENG, "superlative degree")
                .cili("i69708").word(LEMMA, ENG, "superlative");

        @ItemSeed.Frame(key = {SememeGloss.KEY, Language.ENGLISH_KEY})
        static final String gloss = "superlative degree";

        @ItemSeed.Frame(key = {CoreVocabulary.CiliId.KEY})
        static final String cili = "i69708";

        @ItemSeed.Word(lang = Language.ENGLISH_KEY, pos = PartOfSpeech.Noun.KEY,
                features = {GrammaticalFeature.Lemma.KEY})
        static final String noun = "superlative";
    }

    // ==================================================================================
    // VOICE
    // ==================================================================================

    @ItemSeed(key = Passive.KEY)
    public static class Passive {
        public static final String KEY = "cg.feat:passive";
        @Seed public static final GrammaticalFeature SEED = new GrammaticalFeature(KEY)
                .gloss(ENG, "passive voice")
                .cili("i109444").word(LEMMA, ENG, "passive");

        @ItemSeed.Frame(key = {SememeGloss.KEY, Language.ENGLISH_KEY})
        static final String gloss = "passive voice";

        @ItemSeed.Frame(key = {CoreVocabulary.CiliId.KEY})
        static final String cili = "i109444";

        @ItemSeed.Word(lang = Language.ENGLISH_KEY, pos = PartOfSpeech.Noun.KEY,
                features = {GrammaticalFeature.Lemma.KEY})
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
