package dev.everydaythings.graph.language;

import dev.everydaythings.graph.frame.ItemFrame;
import dev.everydaythings.graph.Implements;
import dev.everydaythings.graph.item.ItemSeed;
import dev.everydaythings.graph.item.ManifestOld;
import dev.everydaythings.graph.item.id.ItemID;
import dev.everydaythings.graph.runtime.LibrarianOld;

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
@Implements(GrammaticalFeature.KEY)
@ItemSeed(key = GrammaticalFeature.KEY)
public class GrammaticalFeature extends Sememe {

    public static final String KEY = "cg.sememe:grammatical-feature";

    // ==================================================================================
    // BASE FORM
    // ==================================================================================

    @ItemSeed(key = Lemma.KEY)
    public static class Lemma {
        public static final String KEY = "cg.feat:lemma";
        public static final ItemID IID = ItemID.fromString(KEY);

        @ItemFrame(predicate = SememeGloss.KEY,
                   fieldAs = @ItemFrame.Bind(role = ThematicRole.Value.KEY, qualifiers = {Language.ENGLISH_KEY}))
        static final String gloss = "the base or dictionary form of a word";

        @ItemFrame(predicate = CoreVocabulary.CiliId.KEY)
        static final String cili = "i71975";

        @ItemFrame(predicate = CoreVocabulary.Lexeme.KEY,
                   fieldAs = @ItemFrame.Bind(role = ThematicRole.Value.KEY,
                                             qualifiers = {Language.ENGLISH_KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}, index = true))
        static final String[] words = {"lemma"};
    }

    // ==================================================================================
    // TENSE
    // ==================================================================================

    @ItemSeed(key = Past.KEY)
    public static class Past {
        public static final String KEY = "cg.feat:past";
        public static final ItemID IID = ItemID.fromString(KEY);

        @ItemFrame(predicate = SememeGloss.KEY,
                   fieldAs = @ItemFrame.Bind(role = ThematicRole.Value.KEY, qualifiers = {Language.ENGLISH_KEY}))
        static final String gloss = "past tense";

        @ItemFrame(predicate = CoreVocabulary.CiliId.KEY)
        static final String cili = "i69743";

        @ItemFrame(predicate = CoreVocabulary.Lexeme.KEY,
                   fieldAs = @ItemFrame.Bind(role = ThematicRole.Value.KEY,
                                             qualifiers = {Language.ENGLISH_KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}, index = true))
        static final String[] words = {"past"};
    }

    @ItemSeed(key = Present.KEY)
    public static class Present {
        public static final String KEY = "cg.feat:present";
        public static final ItemID IID = ItemID.fromString(KEY);

        @ItemFrame(predicate = SememeGloss.KEY,
                   fieldAs = @ItemFrame.Bind(role = ThematicRole.Value.KEY, qualifiers = {Language.ENGLISH_KEY}))
        static final String gloss = "present tense";

        @ItemFrame(predicate = CoreVocabulary.CiliId.KEY)
        static final String cili = "i69740";

        @ItemFrame(predicate = CoreVocabulary.Lexeme.KEY,
                   fieldAs = @ItemFrame.Bind(role = ThematicRole.Value.KEY,
                                             qualifiers = {Language.ENGLISH_KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}, index = true))
        static final String[] words = {"present"};
    }

    @ItemSeed(key = Future.KEY)
    public static class Future {
        public static final String KEY = "cg.feat:future";
        public static final ItemID IID = ItemID.fromString(KEY);

        @ItemFrame(predicate = SememeGloss.KEY,
                   fieldAs = @ItemFrame.Bind(role = ThematicRole.Value.KEY, qualifiers = {Language.ENGLISH_KEY}))
        static final String gloss = "future tense";

        @ItemFrame(predicate = CoreVocabulary.CiliId.KEY)
        static final String cili = "i69744";

        @ItemFrame(predicate = CoreVocabulary.Lexeme.KEY,
                   fieldAs = @ItemFrame.Bind(role = ThematicRole.Value.KEY,
                                             qualifiers = {Language.ENGLISH_KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}, index = true))
        static final String[] words = {"future"};
    }

    // ==================================================================================
    // NUMBER
    // ==================================================================================

    @ItemSeed(key = Singular.KEY)
    public static class Singular {
        public static final String KEY = "cg.feat:singular";
        public static final ItemID IID = ItemID.fromString(KEY);

        @ItemFrame(predicate = SememeGloss.KEY,
                   fieldAs = @ItemFrame.Bind(role = ThematicRole.Value.KEY, qualifiers = {Language.ENGLISH_KEY}))
        static final String gloss = "singular number";

        @ItemFrame(predicate = CoreVocabulary.CiliId.KEY)
        static final String cili = "i69586";

        @ItemFrame(predicate = CoreVocabulary.Lexeme.KEY,
                   fieldAs = @ItemFrame.Bind(role = ThematicRole.Value.KEY,
                                             qualifiers = {Language.ENGLISH_KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}, index = true))
        static final String[] words = {"singular"};
    }

    @ItemSeed(key = Plural.KEY)
    public static class Plural {
        public static final String KEY = "cg.feat:plural";
        public static final ItemID IID = ItemID.fromString(KEY);

        @ItemFrame(predicate = SememeGloss.KEY,
                   fieldAs = @ItemFrame.Bind(role = ThematicRole.Value.KEY, qualifiers = {Language.ENGLISH_KEY}))
        static final String gloss = "plural number";

        @ItemFrame(predicate = CoreVocabulary.CiliId.KEY)
        static final String cili = "i69585";

        @ItemFrame(predicate = CoreVocabulary.Lexeme.KEY,
                   fieldAs = @ItemFrame.Bind(role = ThematicRole.Value.KEY,
                                             qualifiers = {Language.ENGLISH_KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}, index = true))
        static final String[] words = {"plural"};
    }

    // ==================================================================================
    // PERSON
    // ==================================================================================

    @ItemSeed(key = FirstPerson.KEY)
    public static class FirstPerson {
        public static final String KEY = "cg.feat:first-person";
        public static final ItemID IID = ItemID.fromString(KEY);

        @ItemFrame(predicate = SememeGloss.KEY,
                   fieldAs = @ItemFrame.Bind(role = ThematicRole.Value.KEY, qualifiers = {Language.ENGLISH_KEY}))
        static final String gloss = "first person";

        @ItemFrame(predicate = CoreVocabulary.CiliId.KEY)
        static final String cili = "i69730";

        @ItemFrame(predicate = CoreVocabulary.Lexeme.KEY,
                   fieldAs = @ItemFrame.Bind(role = ThematicRole.Value.KEY,
                                             qualifiers = {Language.ENGLISH_KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}, index = true))
        static final String[] words = {"first-person"};
    }

    @ItemSeed(key = SecondPerson.KEY)
    public static class SecondPerson {
        public static final String KEY = "cg.feat:second-person";
        public static final ItemID IID = ItemID.fromString(KEY);

        @ItemFrame(predicate = SememeGloss.KEY,
                   fieldAs = @ItemFrame.Bind(role = ThematicRole.Value.KEY, qualifiers = {Language.ENGLISH_KEY}))
        static final String gloss = "second person";

        @ItemFrame(predicate = CoreVocabulary.CiliId.KEY)
        static final String cili = "i69731";

        @ItemFrame(predicate = CoreVocabulary.Lexeme.KEY,
                   fieldAs = @ItemFrame.Bind(role = ThematicRole.Value.KEY,
                                             qualifiers = {Language.ENGLISH_KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}, index = true))
        static final String[] words = {"second-person"};
    }

    @ItemSeed(key = ThirdPerson.KEY)
    public static class ThirdPerson {
        public static final String KEY = "cg.feat:third-person";
        public static final ItemID IID = ItemID.fromString(KEY);

        @ItemFrame(predicate = SememeGloss.KEY,
                   fieldAs = @ItemFrame.Bind(role = ThematicRole.Value.KEY, qualifiers = {Language.ENGLISH_KEY}))
        static final String gloss = "third person";

        @ItemFrame(predicate = CoreVocabulary.CiliId.KEY)
        static final String cili = "i69732";

        @ItemFrame(predicate = CoreVocabulary.Lexeme.KEY,
                   fieldAs = @ItemFrame.Bind(role = ThematicRole.Value.KEY,
                                             qualifiers = {Language.ENGLISH_KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}, index = true))
        static final String[] words = {"third-person"};
    }

    // ==================================================================================
    // FORM / ASPECT
    // ==================================================================================

    @ItemSeed(key = Participle.KEY)
    public static class Participle {
        public static final String KEY = "cg.feat:participle";
        public static final ItemID IID = ItemID.fromString(KEY);

        @ItemFrame(predicate = SememeGloss.KEY,
                   fieldAs = @ItemFrame.Bind(role = ThematicRole.Value.KEY, qualifiers = {Language.ENGLISH_KEY}))
        static final String gloss = "participle form";

        @ItemFrame(predicate = CoreVocabulary.CiliId.KEY)
        static final String cili = "i69745";

        @ItemFrame(predicate = CoreVocabulary.Lexeme.KEY,
                   fieldAs = @ItemFrame.Bind(role = ThematicRole.Value.KEY,
                                             qualifiers = {Language.ENGLISH_KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}, index = true))
        static final String[] words = {"participle"};
    }

    @ItemSeed(key = Progressive.KEY)
    public static class Progressive {
        public static final String KEY = "cg.feat:progressive";
        public static final ItemID IID = ItemID.fromString(KEY);

        @ItemFrame(predicate = SememeGloss.KEY,
                   fieldAs = @ItemFrame.Bind(role = ThematicRole.Value.KEY, qualifiers = {Language.ENGLISH_KEY}))
        static final String gloss = "progressive aspect";

        @ItemFrame(predicate = CoreVocabulary.CiliId.KEY)
        static final String cili = "i109457";

        @ItemFrame(predicate = CoreVocabulary.Lexeme.KEY,
                   fieldAs = @ItemFrame.Bind(role = ThematicRole.Value.KEY,
                                             qualifiers = {Language.ENGLISH_KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}, index = true))
        static final String[] words = {"progressive"};
    }

    @ItemSeed(key = Perfect.KEY)
    public static class Perfect {
        public static final String KEY = "cg.feat:perfect";
        public static final ItemID IID = ItemID.fromString(KEY);

        @ItemFrame(predicate = SememeGloss.KEY,
                   fieldAs = @ItemFrame.Bind(role = ThematicRole.Value.KEY, qualifiers = {Language.ENGLISH_KEY}))
        static final String gloss = "perfect aspect";

        @ItemFrame(predicate = CoreVocabulary.CiliId.KEY)
        static final String cili = "i109459";

        @ItemFrame(predicate = CoreVocabulary.Lexeme.KEY,
                   fieldAs = @ItemFrame.Bind(role = ThematicRole.Value.KEY,
                                             qualifiers = {Language.ENGLISH_KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}, index = true))
        static final String[] words = {"perfect"};
    }

    // ==================================================================================
    // MOOD
    // ==================================================================================

    @ItemSeed(key = Imperative.KEY)
    public static class Imperative {
        public static final String KEY = "cg.feat:imperative";
        public static final ItemID IID = ItemID.fromString(KEY);

        @ItemFrame(predicate = SememeGloss.KEY,
                   fieldAs = @ItemFrame.Bind(role = ThematicRole.Value.KEY, qualifiers = {Language.ENGLISH_KEY}))
        static final String gloss = "imperative mood";

        @ItemFrame(predicate = CoreVocabulary.CiliId.KEY)
        static final String cili = "i109438";

        @ItemFrame(predicate = CoreVocabulary.Lexeme.KEY,
                   fieldAs = @ItemFrame.Bind(role = ThematicRole.Value.KEY,
                                             qualifiers = {Language.ENGLISH_KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}, index = true))
        static final String[] words = {"imperative"};
    }

    @ItemSeed(key = Subjunctive.KEY)
    public static class Subjunctive {
        public static final String KEY = "cg.feat:subjunctive";
        public static final ItemID IID = ItemID.fromString(KEY);

        @ItemFrame(predicate = SememeGloss.KEY,
                   fieldAs = @ItemFrame.Bind(role = ThematicRole.Value.KEY, qualifiers = {Language.ENGLISH_KEY}))
        static final String gloss = "subjunctive mood";

        @ItemFrame(predicate = CoreVocabulary.CiliId.KEY)
        static final String cili = "i109436";

        @ItemFrame(predicate = CoreVocabulary.Lexeme.KEY,
                   fieldAs = @ItemFrame.Bind(role = ThematicRole.Value.KEY,
                                             qualifiers = {Language.ENGLISH_KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}, index = true))
        static final String[] words = {"subjunctive"};
    }

    @ItemSeed(key = Infinitive.KEY)
    public static class Infinitive {
        public static final String KEY = "cg.feat:infinitive";
        public static final ItemID IID = ItemID.fromString(KEY);

        @ItemFrame(predicate = SememeGloss.KEY,
                   fieldAs = @ItemFrame.Bind(role = ThematicRole.Value.KEY, qualifiers = {Language.ENGLISH_KEY}))
        static final String gloss = "infinitive form";

        @ItemFrame(predicate = CoreVocabulary.CiliId.KEY)
        static final String cili = "i69687";

        @ItemFrame(predicate = CoreVocabulary.Lexeme.KEY,
                   fieldAs = @ItemFrame.Bind(role = ThematicRole.Value.KEY,
                                             qualifiers = {Language.ENGLISH_KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}, index = true))
        static final String[] words = {"infinitive"};
    }

    // ==================================================================================
    // DEGREE (adjectives/adverbs)
    // ==================================================================================

    @ItemSeed(key = Comparative.KEY)
    public static class Comparative {
        public static final String KEY = "cg.feat:comparative";
        public static final ItemID IID = ItemID.fromString(KEY);

        @ItemFrame(predicate = SememeGloss.KEY,
                   fieldAs = @ItemFrame.Bind(role = ThematicRole.Value.KEY, qualifiers = {Language.ENGLISH_KEY}))
        static final String gloss = "comparative degree";

        @ItemFrame(predicate = CoreVocabulary.CiliId.KEY)
        static final String cili = "i69707";

        @ItemFrame(predicate = CoreVocabulary.Lexeme.KEY,
                   fieldAs = @ItemFrame.Bind(role = ThematicRole.Value.KEY,
                                             qualifiers = {Language.ENGLISH_KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}, index = true))
        static final String[] words = {"comparative"};
    }

    @ItemSeed(key = Superlative.KEY)
    public static class Superlative {
        public static final String KEY = "cg.feat:superlative";
        public static final ItemID IID = ItemID.fromString(KEY);

        @ItemFrame(predicate = SememeGloss.KEY,
                   fieldAs = @ItemFrame.Bind(role = ThematicRole.Value.KEY, qualifiers = {Language.ENGLISH_KEY}))
        static final String gloss = "superlative degree";

        @ItemFrame(predicate = CoreVocabulary.CiliId.KEY)
        static final String cili = "i69708";

        @ItemFrame(predicate = CoreVocabulary.Lexeme.KEY,
                   fieldAs = @ItemFrame.Bind(role = ThematicRole.Value.KEY,
                                             qualifiers = {Language.ENGLISH_KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}, index = true))
        static final String[] words = {"superlative"};
    }

    // ==================================================================================
    // VOICE
    // ==================================================================================

    @ItemSeed(key = Passive.KEY)
    public static class Passive {
        public static final String KEY = "cg.feat:passive";
        public static final ItemID IID = ItemID.fromString(KEY);

        @ItemFrame(predicate = SememeGloss.KEY,
                   fieldAs = @ItemFrame.Bind(role = ThematicRole.Value.KEY, qualifiers = {Language.ENGLISH_KEY}))
        static final String gloss = "passive voice";

        @ItemFrame(predicate = CoreVocabulary.CiliId.KEY)
        static final String cili = "i109444";

        @ItemFrame(predicate = CoreVocabulary.Lexeme.KEY,
                   fieldAs = @ItemFrame.Bind(role = ThematicRole.Value.KEY,
                                             qualifiers = {Language.ENGLISH_KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}, index = true))
        static final String[] words = {"passive"};
    }

    // ==================================================================================
    // CASE
    // ==================================================================================

    @ItemSeed(key = Nominative.KEY)
    public static class Nominative {
        public static final String KEY = "cg.feat:nominative";
        public static final ItemID IID = ItemID.fromString(KEY);

        @ItemFrame(predicate = SememeGloss.KEY,
                   fieldAs = @ItemFrame.Bind(role = ThematicRole.Value.KEY, qualifiers = {Language.ENGLISH_KEY}))
        static final String gloss = "nominative case — the subject of a sentence";
    }

    @ItemSeed(key = Genitive.KEY)
    public static class Genitive {
        public static final String KEY = "cg.feat:genitive";
        public static final ItemID IID = ItemID.fromString(KEY);

        @ItemFrame(predicate = SememeGloss.KEY,
                   fieldAs = @ItemFrame.Bind(role = ThematicRole.Value.KEY, qualifiers = {Language.ENGLISH_KEY}))
        static final String gloss = "genitive case — possession or association";
    }

    @ItemSeed(key = Dative.KEY)
    public static class Dative {
        public static final String KEY = "cg.feat:dative";
        public static final ItemID IID = ItemID.fromString(KEY);

        @ItemFrame(predicate = SememeGloss.KEY,
                   fieldAs = @ItemFrame.Bind(role = ThematicRole.Value.KEY, qualifiers = {Language.ENGLISH_KEY}))
        static final String gloss = "dative case — the indirect object";
    }

    @ItemSeed(key = Accusative.KEY)
    public static class Accusative {
        public static final String KEY = "cg.feat:accusative";
        public static final ItemID IID = ItemID.fromString(KEY);

        @ItemFrame(predicate = SememeGloss.KEY,
                   fieldAs = @ItemFrame.Bind(role = ThematicRole.Value.KEY, qualifiers = {Language.ENGLISH_KEY}))
        static final String gloss = "accusative case — the direct object";
    }

    // ==================================================================================
    // GENDER
    // ==================================================================================

    @ItemSeed(key = Masculine.KEY)
    public static class Masculine {
        public static final String KEY = "cg.feat:masculine";
        public static final ItemID IID = ItemID.fromString(KEY);

        @ItemFrame(predicate = SememeGloss.KEY,
                   fieldAs = @ItemFrame.Bind(role = ThematicRole.Value.KEY, qualifiers = {Language.ENGLISH_KEY}))
        static final String gloss = "masculine grammatical gender";
    }

    @ItemSeed(key = Feminine.KEY)
    public static class Feminine {
        public static final String KEY = "cg.feat:feminine";
        public static final ItemID IID = ItemID.fromString(KEY);

        @ItemFrame(predicate = SememeGloss.KEY,
                   fieldAs = @ItemFrame.Bind(role = ThematicRole.Value.KEY, qualifiers = {Language.ENGLISH_KEY}))
        static final String gloss = "feminine grammatical gender";
    }

    @ItemSeed(key = Neuter.KEY)
    public static class Neuter {
        public static final String KEY = "cg.feat:neuter";
        public static final ItemID IID = ItemID.fromString(KEY);

        @ItemFrame(predicate = SememeGloss.KEY,
                   fieldAs = @ItemFrame.Bind(role = ThematicRole.Value.KEY, qualifiers = {Language.ENGLISH_KEY}))
        static final String gloss = "neuter grammatical gender";
    }

    // ==================================================================================
    // MOOD (additional)
    // ==================================================================================

    @ItemSeed(key = Indicative.KEY)
    public static class Indicative {
        public static final String KEY = "cg.feat:indicative";
        public static final ItemID IID = ItemID.fromString(KEY);

        @ItemFrame(predicate = SememeGloss.KEY,
                   fieldAs = @ItemFrame.Bind(role = ThematicRole.Value.KEY, qualifiers = {Language.ENGLISH_KEY}))
        static final String gloss = "indicative mood — statements of fact";
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
    protected GrammaticalFeature(LibrarianOld librarian, ManifestOld manifest) {
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
