package dev.everydaythings.graph.language;

import dev.everydaythings.graph.item.id.ItemID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for English regular morphology rules and irregular override lookup.
 */
@DisplayName("English Morphology")
class EnglishOldMorphologyTest {

    // Feature IIDs
    static final ItemID PAST = GrammaticalFeature.Past.IID;
    static final ItemID PRESENT = GrammaticalFeature.Present.IID;
    static final ItemID PLURAL = GrammaticalFeature.Plural.IID;
    static final ItemID THIRD_PERSON = GrammaticalFeature.ThirdPerson.IID;
    static final ItemID SINGULAR = GrammaticalFeature.Singular.IID;
    static final ItemID PARTICIPLE = GrammaticalFeature.Participle.IID;
    static final ItemID COMPARATIVE = GrammaticalFeature.Comparative.IID;
    static final ItemID SUPERLATIVE = GrammaticalFeature.Superlative.IID;

    // Use English as a seed (no librarian needed for morphology)
    static final EnglishOld ENGLISH_OLD = new EnglishOld(ItemID.fromString(EnglishOld.KEY));

    // ==================================================================================
    // VERB INFLECTION
    // ==================================================================================

    @Nested
    @DisplayName("Verb inflection")
    class VerbInflection {

        @Test
        @DisplayName("3rd person singular present: add -s")
        void thirdPersonSingular() {
            assertThat(ENGLISH_OLD.inflect("run", PartOfSpeech.VERB, Set.of(THIRD_PERSON)))
                    .isEqualTo("runs");
            assertThat(ENGLISH_OLD.inflect("eat", PartOfSpeech.VERB, Set.of(THIRD_PERSON)))
                    .isEqualTo("eats");
            assertThat(ENGLISH_OLD.inflect("walk", PartOfSpeech.VERB, Set.of(THIRD_PERSON)))
                    .isEqualTo("walks");
        }

        @Test
        @DisplayName("3rd person singular present: add -es for sibilants")
        void thirdPersonSibilant() {
            assertThat(ENGLISH_OLD.inflect("pass", PartOfSpeech.VERB, Set.of(THIRD_PERSON)))
                    .isEqualTo("passes");
            assertThat(ENGLISH_OLD.inflect("watch", PartOfSpeech.VERB, Set.of(THIRD_PERSON)))
                    .isEqualTo("watches");
            assertThat(ENGLISH_OLD.inflect("fix", PartOfSpeech.VERB, Set.of(THIRD_PERSON)))
                    .isEqualTo("fixes");
            assertThat(ENGLISH_OLD.inflect("push", PartOfSpeech.VERB, Set.of(THIRD_PERSON)))
                    .isEqualTo("pushes");
            assertThat(ENGLISH_OLD.inflect("buzz", PartOfSpeech.VERB, Set.of(THIRD_PERSON)))
                    .isEqualTo("buzzes");
        }

        @Test
        @DisplayName("3rd person singular present: consonant+y → -ies")
        void thirdPersonConsonantY() {
            assertThat(ENGLISH_OLD.inflect("carry", PartOfSpeech.VERB, Set.of(THIRD_PERSON)))
                    .isEqualTo("carries");
            assertThat(ENGLISH_OLD.inflect("study", PartOfSpeech.VERB, Set.of(THIRD_PERSON)))
                    .isEqualTo("studies");
        }

        @Test
        @DisplayName("3rd person singular present: vowel+y → -ys")
        void thirdPersonVowelY() {
            assertThat(ENGLISH_OLD.inflect("play", PartOfSpeech.VERB, Set.of(THIRD_PERSON)))
                    .isEqualTo("plays");
            assertThat(ENGLISH_OLD.inflect("enjoy", PartOfSpeech.VERB, Set.of(THIRD_PERSON)))
                    .isEqualTo("enjoys");
        }

        @Test
        @DisplayName("past tense: add -ed")
        void pastRegular() {
            assertThat(ENGLISH_OLD.inflect("walk", PartOfSpeech.VERB, Set.of(PAST)))
                    .isEqualTo("walked");
            assertThat(ENGLISH_OLD.inflect("play", PartOfSpeech.VERB, Set.of(PAST)))
                    .isEqualTo("played");
        }

        @Test
        @DisplayName("past tense: ends in e → add -d")
        void pastEndsInE() {
            assertThat(ENGLISH_OLD.inflect("love", PartOfSpeech.VERB, Set.of(PAST)))
                    .isEqualTo("loved");
            assertThat(ENGLISH_OLD.inflect("create", PartOfSpeech.VERB, Set.of(PAST)))
                    .isEqualTo("created");
        }

        @Test
        @DisplayName("past tense: consonant+y → -ied")
        void pastConsonantY() {
            assertThat(ENGLISH_OLD.inflect("carry", PartOfSpeech.VERB, Set.of(PAST)))
                    .isEqualTo("carried");
            assertThat(ENGLISH_OLD.inflect("study", PartOfSpeech.VERB, Set.of(PAST)))
                    .isEqualTo("studied");
        }

        @Test
        @DisplayName("past tense: double consonant")
        void pastDoubleConsonant() {
            assertThat(ENGLISH_OLD.inflect("stop", PartOfSpeech.VERB, Set.of(PAST)))
                    .isEqualTo("stopped");
            assertThat(ENGLISH_OLD.inflect("plan", PartOfSpeech.VERB, Set.of(PAST)))
                    .isEqualTo("planned");
        }

        @Test
        @DisplayName("present participle: add -ing")
        void presentParticiple() {
            assertThat(ENGLISH_OLD.inflect("walk", PartOfSpeech.VERB, Set.of(PARTICIPLE, PRESENT)))
                    .isEqualTo("walking");
            assertThat(ENGLISH_OLD.inflect("play", PartOfSpeech.VERB, Set.of(PARTICIPLE, PRESENT)))
                    .isEqualTo("playing");
        }

        @Test
        @DisplayName("present participle: drop -e + -ing")
        void presentParticipleDropE() {
            assertThat(ENGLISH_OLD.inflect("love", PartOfSpeech.VERB, Set.of(PARTICIPLE, PRESENT)))
                    .isEqualTo("loving");
            assertThat(ENGLISH_OLD.inflect("create", PartOfSpeech.VERB, Set.of(PARTICIPLE, PRESENT)))
                    .isEqualTo("creating");
        }

        @Test
        @DisplayName("present participle: -ie → -ying")
        void presentParticipleIeToYing() {
            assertThat(ENGLISH_OLD.inflect("die", PartOfSpeech.VERB, Set.of(PARTICIPLE, PRESENT)))
                    .isEqualTo("dying");
            assertThat(ENGLISH_OLD.inflect("lie", PartOfSpeech.VERB, Set.of(PARTICIPLE, PRESENT)))
                    .isEqualTo("lying");
        }

        @Test
        @DisplayName("present participle: double consonant")
        void presentParticipleDouble() {
            assertThat(ENGLISH_OLD.inflect("run", PartOfSpeech.VERB, Set.of(PARTICIPLE, PRESENT)))
                    .isEqualTo("running");
            assertThat(ENGLISH_OLD.inflect("stop", PartOfSpeech.VERB, Set.of(PARTICIPLE, PRESENT)))
                    .isEqualTo("stopping");
        }

        @Test
        @DisplayName("present participle: -ee stays")
        void presentParticipleEeStays() {
            assertThat(ENGLISH_OLD.inflect("see", PartOfSpeech.VERB, Set.of(PARTICIPLE, PRESENT)))
                    .isEqualTo("seeing");
        }

        @Test
        @DisplayName("empty features returns lemma")
        void emptyFeaturesReturnsLemma() {
            assertThat(ENGLISH_OLD.inflect("run", PartOfSpeech.VERB, Set.of()))
                    .isEqualTo("run");
        }
    }

    // ==================================================================================
    // NOUN INFLECTION
    // ==================================================================================

    @Nested
    @DisplayName("Noun inflection")
    class NounInflection {

        @Test
        @DisplayName("regular plural: add -s")
        void pluralRegular() {
            assertThat(ENGLISH_OLD.inflect("cat", PartOfSpeech.NOUN, Set.of(PLURAL)))
                    .isEqualTo("cats");
            assertThat(ENGLISH_OLD.inflect("dog", PartOfSpeech.NOUN, Set.of(PLURAL)))
                    .isEqualTo("dogs");
        }

        @Test
        @DisplayName("sibilant plural: add -es")
        void pluralSibilant() {
            assertThat(ENGLISH_OLD.inflect("box", PartOfSpeech.NOUN, Set.of(PLURAL)))
                    .isEqualTo("boxes");
            assertThat(ENGLISH_OLD.inflect("church", PartOfSpeech.NOUN, Set.of(PLURAL)))
                    .isEqualTo("churches");
            assertThat(ENGLISH_OLD.inflect("bus", PartOfSpeech.NOUN, Set.of(PLURAL)))
                    .isEqualTo("buses");
        }

        @Test
        @DisplayName("consonant+y plural: -ies")
        void pluralConsonantY() {
            assertThat(ENGLISH_OLD.inflect("baby", PartOfSpeech.NOUN, Set.of(PLURAL)))
                    .isEqualTo("babies");
            assertThat(ENGLISH_OLD.inflect("city", PartOfSpeech.NOUN, Set.of(PLURAL)))
                    .isEqualTo("cities");
        }

        @Test
        @DisplayName("vowel+y plural: -ys")
        void pluralVowelY() {
            assertThat(ENGLISH_OLD.inflect("boy", PartOfSpeech.NOUN, Set.of(PLURAL)))
                    .isEqualTo("boys");
            assertThat(ENGLISH_OLD.inflect("key", PartOfSpeech.NOUN, Set.of(PLURAL)))
                    .isEqualTo("keys");
        }
    }

    // ==================================================================================
    // ADJECTIVE INFLECTION
    // ==================================================================================

    @Nested
    @DisplayName("Adjective inflection")
    class AdjectiveInflection {

        @Test
        @DisplayName("comparative: add -er")
        void comparative() {
            assertThat(ENGLISH_OLD.inflect("tall", PartOfSpeech.ADJECTIVE, Set.of(COMPARATIVE)))
                    .isEqualTo("taller");
            assertThat(ENGLISH_OLD.inflect("cold", PartOfSpeech.ADJECTIVE, Set.of(COMPARATIVE)))
                    .isEqualTo("colder");
        }

        @Test
        @DisplayName("comparative: ends in -e → add -r")
        void comparativeEndsInE() {
            assertThat(ENGLISH_OLD.inflect("nice", PartOfSpeech.ADJECTIVE, Set.of(COMPARATIVE)))
                    .isEqualTo("nicer");
            assertThat(ENGLISH_OLD.inflect("large", PartOfSpeech.ADJECTIVE, Set.of(COMPARATIVE)))
                    .isEqualTo("larger");
        }

        @Test
        @DisplayName("comparative: consonant+y → -ier")
        void comparativeConsonantY() {
            assertThat(ENGLISH_OLD.inflect("happy", PartOfSpeech.ADJECTIVE, Set.of(COMPARATIVE)))
                    .isEqualTo("happier");
            assertThat(ENGLISH_OLD.inflect("easy", PartOfSpeech.ADJECTIVE, Set.of(COMPARATIVE)))
                    .isEqualTo("easier");
        }

        @Test
        @DisplayName("comparative: double consonant")
        void comparativeDouble() {
            assertThat(ENGLISH_OLD.inflect("big", PartOfSpeech.ADJECTIVE, Set.of(COMPARATIVE)))
                    .isEqualTo("bigger");
            assertThat(ENGLISH_OLD.inflect("hot", PartOfSpeech.ADJECTIVE, Set.of(COMPARATIVE)))
                    .isEqualTo("hotter");
        }

        @Test
        @DisplayName("superlative: add -est")
        void superlative() {
            assertThat(ENGLISH_OLD.inflect("tall", PartOfSpeech.ADJECTIVE, Set.of(SUPERLATIVE)))
                    .isEqualTo("tallest");
        }

        @Test
        @DisplayName("superlative: consonant+y → -iest")
        void superlativeConsonantY() {
            assertThat(ENGLISH_OLD.inflect("happy", PartOfSpeech.ADJECTIVE, Set.of(SUPERLATIVE)))
                    .isEqualTo("happiest");
        }

        @Test
        @DisplayName("superlative: double consonant")
        void superlativeDouble() {
            assertThat(ENGLISH_OLD.inflect("big", PartOfSpeech.ADJECTIVE, Set.of(SUPERLATIVE)))
                    .isEqualTo("biggest");
        }
    }

    // ==================================================================================
    // IRREGULAR OVERRIDES VIA LEXEME
    // ==================================================================================

    @Nested
    @DisplayName("Irregular override via Lexeme")
    class IrregularOverride {

        @Test
        @DisplayName("irregular past tense overrides regular rule")
        void irregularPastOverrides() {
            Lexeme run = new Lexeme("run", Language.ENGLISH,
                    ItemID.fromString("cg.test:run-sememe"), PartOfSpeech.VERB, 1.0f,
                    List.of(
                            FormEntry.of("ran", GrammaticalFeature.Past.IID),
                            FormEntry.of("run", GrammaticalFeature.Past.IID, GrammaticalFeature.Participle.IID),
                            FormEntry.of("running", GrammaticalFeature.Present.IID, GrammaticalFeature.Participle.IID)
                    ));

            // Irregular past: "ran" (not "runned")
            assertThat(ENGLISH_OLD.inflect(run, Set.of(PAST))).isEqualTo("ran");

            // Irregular past participle: "run" (not "runned")
            assertThat(ENGLISH_OLD.inflect(run, Set.of(PAST, PARTICIPLE))).isEqualTo("run");

            // Irregular present participle: "running" (override for double consonant)
            assertThat(ENGLISH_OLD.inflect(run, Set.of(PRESENT, PARTICIPLE))).isEqualTo("running");

            // Regular 3rd person (no override): falls through to algorithm
            assertThat(ENGLISH_OLD.inflect(run, Set.of(THIRD_PERSON))).isEqualTo("runs");
        }

        @Test
        @DisplayName("irregular plural overrides regular rule")
        void irregularPluralOverrides() {
            Lexeme child = new Lexeme("child", Language.ENGLISH,
                    ItemID.fromString("cg.test:child-sememe"), PartOfSpeech.NOUN, 1.0f,
                    List.of(FormEntry.of("children", GrammaticalFeature.Plural.IID)));

            // Irregular plural: "children" (not "childs")
            assertThat(ENGLISH_OLD.inflect(child, Set.of(PLURAL))).isEqualTo("children");
        }

        @Test
        @DisplayName("irregular comparative overrides regular rule")
        void irregularComparativeOverrides() {
            Lexeme good = new Lexeme("good", Language.ENGLISH,
                    ItemID.fromString("cg.test:good-sememe"), PartOfSpeech.ADJECTIVE, 1.0f,
                    List.of(
                            FormEntry.of("better", GrammaticalFeature.Comparative.IID),
                            FormEntry.of("best", GrammaticalFeature.Superlative.IID)
                    ));

            assertThat(ENGLISH_OLD.inflect(good, Set.of(COMPARATIVE))).isEqualTo("better");
            assertThat(ENGLISH_OLD.inflect(good, Set.of(SUPERLATIVE))).isEqualTo("best");
        }

        @Test
        @DisplayName("empty features returns lemma even with overrides")
        void emptyFeaturesReturnsLemma() {
            Lexeme run = new Lexeme("run", Language.ENGLISH,
                    ItemID.fromString("cg.test:run-sememe"), PartOfSpeech.VERB, 1.0f,
                    List.of(FormEntry.of("ran", GrammaticalFeature.Past.IID)));

            assertThat(ENGLISH_OLD.inflect(run, Set.of())).isEqualTo("run");
            assertThat(ENGLISH_OLD.inflect(run, null)).isEqualTo("run");
        }

        @Test
        @DisplayName("lexeme with no overrides uses regular rules")
        void noOverridesUsesRegularRules() {
            Lexeme walk = Lexeme.of("walk", Language.ENGLISH,
                    ItemID.fromString("cg.test:walk-sememe"), PartOfSpeech.VERB);

            assertThat(ENGLISH_OLD.inflect(walk, Set.of(PAST))).isEqualTo("walked");
            assertThat(ENGLISH_OLD.inflect(walk, Set.of(PARTICIPLE, PRESENT))).isEqualTo("walking");
            assertThat(ENGLISH_OLD.inflect(walk, Set.of(THIRD_PERSON))).isEqualTo("walks");
        }
    }
}
