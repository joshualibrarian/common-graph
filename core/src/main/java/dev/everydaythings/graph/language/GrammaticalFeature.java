package dev.everydaythings.graph.language;

import dev.everydaythings.graph.item.Manifest;
import dev.everydaythings.graph.item.Type;
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
@Type(value = GrammaticalFeature.KEY, glyph = "\uD83D\uDD24", color = 0x70B0D0)
public class GrammaticalFeature extends Sememe {

    public static final String KEY = "cg:type/grammatical-feature";

    // ==================================================================================
    // BASE FORM
    // ==================================================================================

    public static class Lemma {
        public static final String KEY = "cg.feat:lemma";
        @Seed public static final GrammaticalFeature SEED = new GrammaticalFeature(KEY)
                .gloss(ENG, "the base or dictionary form of a word")
                .cili("i71975").word(LEMMA, ENG, "lemma");
    }

    // ==================================================================================
    // TENSE
    // ==================================================================================

    public static class Past {
        public static final String KEY = "cg.feat:past";
        @Seed public static final GrammaticalFeature SEED = new GrammaticalFeature(KEY)
                .gloss(ENG, "past tense")
                .cili("i69743").word(LEMMA, ENG, "past");
    }

    public static class Present {
        public static final String KEY = "cg.feat:present";
        @Seed public static final GrammaticalFeature SEED = new GrammaticalFeature(KEY)
                .gloss(ENG, "present tense")
                .cili("i69740").word(LEMMA, ENG, "present");
    }

    public static class Future {
        public static final String KEY = "cg.feat:future";
        @Seed public static final GrammaticalFeature SEED = new GrammaticalFeature(KEY)
                .gloss(ENG, "future tense")
                .cili("i69744").word(LEMMA, ENG, "future");
    }

    // ==================================================================================
    // NUMBER
    // ==================================================================================

    public static class Singular {
        public static final String KEY = "cg.feat:singular";
        @Seed public static final GrammaticalFeature SEED = new GrammaticalFeature(KEY)
                .gloss(ENG, "singular number")
                .cili("i69586").word(LEMMA, ENG, "singular");
    }

    public static class Plural {
        public static final String KEY = "cg.feat:plural";
        @Seed public static final GrammaticalFeature SEED = new GrammaticalFeature(KEY)
                .gloss(ENG, "plural number")
                .cili("i69585").word(LEMMA, ENG, "plural");
    }

    // ==================================================================================
    // PERSON
    // ==================================================================================

    public static class FirstPerson {
        public static final String KEY = "cg.feat:first-person";
        @Seed public static final GrammaticalFeature SEED = new GrammaticalFeature(KEY)
                .gloss(ENG, "first person")
                .cili("i69730").word(LEMMA, ENG, "first-person");
    }

    public static class SecondPerson {
        public static final String KEY = "cg.feat:second-person";
        @Seed public static final GrammaticalFeature SEED = new GrammaticalFeature(KEY)
                .gloss(ENG, "second person")
                .cili("i69731").word(LEMMA, ENG, "second-person");
    }

    public static class ThirdPerson {
        public static final String KEY = "cg.feat:third-person";
        @Seed public static final GrammaticalFeature SEED = new GrammaticalFeature(KEY)
                .gloss(ENG, "third person")
                .cili("i69732").word(LEMMA, ENG, "third-person");
    }

    // ==================================================================================
    // FORM / ASPECT
    // ==================================================================================

    public static class Participle {
        public static final String KEY = "cg.feat:participle";
        @Seed public static final GrammaticalFeature SEED = new GrammaticalFeature(KEY)
                .gloss(ENG, "participle form")
                .cili("i69745").word(LEMMA, ENG, "participle");
    }

    public static class Progressive {
        public static final String KEY = "cg.feat:progressive";
        @Seed public static final GrammaticalFeature SEED = new GrammaticalFeature(KEY)
                .gloss(ENG, "progressive aspect")
                .cili("i109457").word(LEMMA, ENG, "progressive");
    }

    public static class Perfect {
        public static final String KEY = "cg.feat:perfect";
        @Seed public static final GrammaticalFeature SEED = new GrammaticalFeature(KEY)
                .gloss(ENG, "perfect aspect")
                .cili("i109459").word(LEMMA, ENG, "perfect");
    }

    // ==================================================================================
    // MOOD
    // ==================================================================================

    public static class Imperative {
        public static final String KEY = "cg.feat:imperative";
        @Seed public static final GrammaticalFeature SEED = new GrammaticalFeature(KEY)
                .gloss(ENG, "imperative mood")
                .cili("i109438").word(LEMMA, ENG, "imperative");
    }

    public static class Subjunctive {
        public static final String KEY = "cg.feat:subjunctive";
        @Seed public static final GrammaticalFeature SEED = new GrammaticalFeature(KEY)
                .gloss(ENG, "subjunctive mood")
                .cili("i109436").word(LEMMA, ENG, "subjunctive");
    }

    public static class Infinitive {
        public static final String KEY = "cg.feat:infinitive";
        @Seed public static final GrammaticalFeature SEED = new GrammaticalFeature(KEY)
                .gloss(ENG, "infinitive form")
                .cili("i69687").word(LEMMA, ENG, "infinitive");
    }

    // ==================================================================================
    // DEGREE (adjectives/adverbs)
    // ==================================================================================

    public static class Comparative {
        public static final String KEY = "cg.feat:comparative";
        @Seed public static final GrammaticalFeature SEED = new GrammaticalFeature(KEY)
                .gloss(ENG, "comparative degree")
                .cili("i69707").word(LEMMA, ENG, "comparative");
    }

    public static class Superlative {
        public static final String KEY = "cg.feat:superlative";
        @Seed public static final GrammaticalFeature SEED = new GrammaticalFeature(KEY)
                .gloss(ENG, "superlative degree")
                .cili("i69708").word(LEMMA, ENG, "superlative");
    }

    // ==================================================================================
    // VOICE
    // ==================================================================================

    public static class Passive {
        public static final String KEY = "cg.feat:passive";
        @Seed public static final GrammaticalFeature SEED = new GrammaticalFeature(KEY)
                .gloss(ENG, "passive voice")
                .cili("i109444").word(LEMMA, ENG, "passive");
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
        super(canonicalKey, PartOfSpeech.NOUN);
    }

    /** Seed constructor. */
    public GrammaticalFeature(String canonicalKey, Map<String, String> glosses, List<String> tokens) {
        super(canonicalKey, PartOfSpeech.NOUN, glosses, Map.of(), tokens);
    }

    /** Seed constructor (with sources for CILI). */
    public GrammaticalFeature(String canonicalKey, Map<String, String> glosses,
                              Map<String, String> sources, List<String> tokens) {
        super(canonicalKey, PartOfSpeech.NOUN, glosses, sources, tokens);
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
