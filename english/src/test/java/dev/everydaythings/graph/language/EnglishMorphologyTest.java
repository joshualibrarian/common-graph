package dev.everydaythings.graph.language;

import dev.everydaythings.graph.item.id.ItemID;
import org.junit.jupiter.api.BeforeAll;
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
class EnglishMorphologyTest {

    // Feature IIDs
    static final ItemID PAST = GrammaticalFeature.PAST.iid();
    static final ItemID PRESENT = GrammaticalFeature.PRESENT.iid();
    static final ItemID PLURAL = GrammaticalFeature.PLURAL.iid();
    static final ItemID THIRD_PERSON = GrammaticalFeature.THIRD_PERSON.iid();
    static final ItemID SINGULAR = GrammaticalFeature.SINGULAR.iid();
    static final ItemID PARTICIPLE = GrammaticalFeature.PARTICIPLE.iid();
    static final ItemID COMPARATIVE = GrammaticalFeature.COMPARATIVE.iid();
    static final ItemID SUPERLATIVE = GrammaticalFeature.SUPERLATIVE.iid();

    // Use English as a seed (no librarian needed for morphology)
    static final English english = new English(ItemID.fromString(English.KEY));

    // ==================================================================================
    // VERB INFLECTION
    // ==================================================================================

    @Nested
    @DisplayName("Verb inflection")
    class VerbInflection {

        @Test
        @DisplayName("3rd person singular present: add -s")
        void thirdPersonSingular() {
            assertThat(english.inflect("run", PartOfSpeech.VERB, Set.of(THIRD_PERSON)))
                    .isEqualTo("runs");
            assertThat(english.inflect("eat", PartOfSpeech.VERB, Set.of(THIRD_PERSON)))
                    .isEqualTo("eats");
            assertThat(english.inflect("walk", PartOfSpeech.VERB, Set.of(THIRD_PERSON)))
                    .isEqualTo("walks");
        }

        @Test
        @DisplayName("3rd person singular present: add -es for sibilants")
        void thirdPersonSibilant() {
            assertThat(english.inflect("pass", PartOfSpeech.VERB, Set.of(THIRD_PERSON)))
                    .isEqualTo("passes");
            assertThat(english.inflect("watch", PartOfSpeech.VERB, Set.of(THIRD_PERSON)))
                    .isEqualTo("watches");
            assertThat(english.inflect("fix", PartOfSpeech.VERB, Set.of(THIRD_PERSON)))
                    .isEqualTo("fixes");
            assertThat(english.inflect("push", PartOfSpeech.VERB, Set.of(THIRD_PERSON)))
                    .isEqualTo("pushes");
            assertThat(english.inflect("buzz", PartOfSpeech.VERB, Set.of(THIRD_PERSON)))
                    .isEqualTo("buzzes");
        }

        @Test
        @DisplayName("3rd person singular present: consonant+y → -ies")
        void thirdPersonConsonantY() {
            assertThat(english.inflect("carry", PartOfSpeech.VERB, Set.of(THIRD_PERSON)))
                    .isEqualTo("carries");
            assertThat(english.inflect("study", PartOfSpeech.VERB, Set.of(THIRD_PERSON)))
                    .isEqualTo("studies");
        }

        @Test
        @DisplayName("3rd person singular present: vowel+y → -ys")
        void thirdPersonVowelY() {
            assertThat(english.inflect("play", PartOfSpeech.VERB, Set.of(THIRD_PERSON)))
                    .isEqualTo("plays");
            assertThat(english.inflect("enjoy", PartOfSpeech.VERB, Set.of(THIRD_PERSON)))
                    .isEqualTo("enjoys");
        }

        @Test
        @DisplayName("past tense: add -ed")
        void pastRegular() {
            assertThat(english.inflect("walk", PartOfSpeech.VERB, Set.of(PAST)))
                    .isEqualTo("walked");
            assertThat(english.inflect("play", PartOfSpeech.VERB, Set.of(PAST)))
                    .isEqualTo("played");
        }

        @Test
        @DisplayName("past tense: ends in e → add -d")
        void pastEndsInE() {
            assertThat(english.inflect("love", PartOfSpeech.VERB, Set.of(PAST)))
                    .isEqualTo("loved");
            assertThat(english.inflect("create", PartOfSpeech.VERB, Set.of(PAST)))
                    .isEqualTo("created");
        }

        @Test
        @DisplayName("past tense: consonant+y → -ied")
        void pastConsonantY() {
            assertThat(english.inflect("carry", PartOfSpeech.VERB, Set.of(PAST)))
                    .isEqualTo("carried");
            assertThat(english.inflect("study", PartOfSpeech.VERB, Set.of(PAST)))
                    .isEqualTo("studied");
        }

        @Test
        @DisplayName("past tense: double consonant")
        void pastDoubleConsonant() {
            assertThat(english.inflect("stop", PartOfSpeech.VERB, Set.of(PAST)))
                    .isEqualTo("stopped");
            assertThat(english.inflect("plan", PartOfSpeech.VERB, Set.of(PAST)))
                    .isEqualTo("planned");
        }

        @Test
        @DisplayName("present participle: add -ing")
        void presentParticiple() {
            assertThat(english.inflect("walk", PartOfSpeech.VERB, Set.of(PARTICIPLE, PRESENT)))
                    .isEqualTo("walking");
            assertThat(english.inflect("play", PartOfSpeech.VERB, Set.of(PARTICIPLE, PRESENT)))
                    .isEqualTo("playing");
        }

        @Test
        @DisplayName("present participle: drop -e + -ing")
        void presentParticipleDropE() {
            assertThat(english.inflect("love", PartOfSpeech.VERB, Set.of(PARTICIPLE, PRESENT)))
                    .isEqualTo("loving");
            assertThat(english.inflect("create", PartOfSpeech.VERB, Set.of(PARTICIPLE, PRESENT)))
                    .isEqualTo("creating");
        }

        @Test
        @DisplayName("present participle: -ie → -ying")
        void presentParticipleIeToYing() {
            assertThat(english.inflect("die", PartOfSpeech.VERB, Set.of(PARTICIPLE, PRESENT)))
                    .isEqualTo("dying");
            assertThat(english.inflect("lie", PartOfSpeech.VERB, Set.of(PARTICIPLE, PRESENT)))
                    .isEqualTo("lying");
        }

        @Test
        @DisplayName("present participle: double consonant")
        void presentParticipleDouble() {
            assertThat(english.inflect("run", PartOfSpeech.VERB, Set.of(PARTICIPLE, PRESENT)))
                    .isEqualTo("running");
            assertThat(english.inflect("stop", PartOfSpeech.VERB, Set.of(PARTICIPLE, PRESENT)))
                    .isEqualTo("stopping");
        }

        @Test
        @DisplayName("present participle: -ee stays")
        void presentParticipleEeStays() {
            assertThat(english.inflect("see", PartOfSpeech.VERB, Set.of(PARTICIPLE, PRESENT)))
                    .isEqualTo("seeing");
        }

        @Test
        @DisplayName("empty features returns lemma")
        void emptyFeaturesReturnsLemma() {
            assertThat(english.inflect("run", PartOfSpeech.VERB, Set.of()))
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
            assertThat(english.inflect("cat", PartOfSpeech.NOUN, Set.of(PLURAL)))
                    .isEqualTo("cats");
            assertThat(english.inflect("dog", PartOfSpeech.NOUN, Set.of(PLURAL)))
                    .isEqualTo("dogs");
        }

        @Test
        @DisplayName("sibilant plural: add -es")
        void pluralSibilant() {
            assertThat(english.inflect("box", PartOfSpeech.NOUN, Set.of(PLURAL)))
                    .isEqualTo("boxes");
            assertThat(english.inflect("church", PartOfSpeech.NOUN, Set.of(PLURAL)))
                    .isEqualTo("churches");
            assertThat(english.inflect("bus", PartOfSpeech.NOUN, Set.of(PLURAL)))
                    .isEqualTo("buses");
        }

        @Test
        @DisplayName("consonant+y plural: -ies")
        void pluralConsonantY() {
            assertThat(english.inflect("baby", PartOfSpeech.NOUN, Set.of(PLURAL)))
                    .isEqualTo("babies");
            assertThat(english.inflect("city", PartOfSpeech.NOUN, Set.of(PLURAL)))
                    .isEqualTo("cities");
        }

        @Test
        @DisplayName("vowel+y plural: -ys")
        void pluralVowelY() {
            assertThat(english.inflect("boy", PartOfSpeech.NOUN, Set.of(PLURAL)))
                    .isEqualTo("boys");
            assertThat(english.inflect("key", PartOfSpeech.NOUN, Set.of(PLURAL)))
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
            assertThat(english.inflect("tall", PartOfSpeech.ADJECTIVE, Set.of(COMPARATIVE)))
                    .isEqualTo("taller");
            assertThat(english.inflect("cold", PartOfSpeech.ADJECTIVE, Set.of(COMPARATIVE)))
                    .isEqualTo("colder");
        }

        @Test
        @DisplayName("comparative: ends in -e → add -r")
        void comparativeEndsInE() {
            assertThat(english.inflect("nice", PartOfSpeech.ADJECTIVE, Set.of(COMPARATIVE)))
                    .isEqualTo("nicer");
            assertThat(english.inflect("large", PartOfSpeech.ADJECTIVE, Set.of(COMPARATIVE)))
                    .isEqualTo("larger");
        }

        @Test
        @DisplayName("comparative: consonant+y → -ier")
        void comparativeConsonantY() {
            assertThat(english.inflect("happy", PartOfSpeech.ADJECTIVE, Set.of(COMPARATIVE)))
                    .isEqualTo("happier");
            assertThat(english.inflect("easy", PartOfSpeech.ADJECTIVE, Set.of(COMPARATIVE)))
                    .isEqualTo("easier");
        }

        @Test
        @DisplayName("comparative: double consonant")
        void comparativeDouble() {
            assertThat(english.inflect("big", PartOfSpeech.ADJECTIVE, Set.of(COMPARATIVE)))
                    .isEqualTo("bigger");
            assertThat(english.inflect("hot", PartOfSpeech.ADJECTIVE, Set.of(COMPARATIVE)))
                    .isEqualTo("hotter");
        }

        @Test
        @DisplayName("superlative: add -est")
        void superlative() {
            assertThat(english.inflect("tall", PartOfSpeech.ADJECTIVE, Set.of(SUPERLATIVE)))
                    .isEqualTo("tallest");
        }

        @Test
        @DisplayName("superlative: consonant+y → -iest")
        void superlativeConsonantY() {
            assertThat(english.inflect("happy", PartOfSpeech.ADJECTIVE, Set.of(SUPERLATIVE)))
                    .isEqualTo("happiest");
        }

        @Test
        @DisplayName("superlative: double consonant")
        void superlativeDouble() {
            assertThat(english.inflect("big", PartOfSpeech.ADJECTIVE, Set.of(SUPERLATIVE)))
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
                            FormEntry.of("ran", GrammaticalFeature.PAST),
                            FormEntry.of("run", GrammaticalFeature.PAST, GrammaticalFeature.PARTICIPLE),
                            FormEntry.of("running", GrammaticalFeature.PRESENT, GrammaticalFeature.PARTICIPLE)
                    ));

            // Irregular past: "ran" (not "runned")
            assertThat(english.inflect(run, Set.of(PAST))).isEqualTo("ran");

            // Irregular past participle: "run" (not "runned")
            assertThat(english.inflect(run, Set.of(PAST, PARTICIPLE))).isEqualTo("run");

            // Irregular present participle: "running" (override for double consonant)
            assertThat(english.inflect(run, Set.of(PRESENT, PARTICIPLE))).isEqualTo("running");

            // Regular 3rd person (no override): falls through to algorithm
            assertThat(english.inflect(run, Set.of(THIRD_PERSON))).isEqualTo("runs");
        }

        @Test
        @DisplayName("irregular plural overrides regular rule")
        void irregularPluralOverrides() {
            Lexeme child = new Lexeme("child", Language.ENGLISH,
                    ItemID.fromString("cg.test:child-sememe"), PartOfSpeech.NOUN, 1.0f,
                    List.of(FormEntry.of("children", GrammaticalFeature.PLURAL)));

            // Irregular plural: "children" (not "childs")
            assertThat(english.inflect(child, Set.of(PLURAL))).isEqualTo("children");
        }

        @Test
        @DisplayName("irregular comparative overrides regular rule")
        void irregularComparativeOverrides() {
            Lexeme good = new Lexeme("good", Language.ENGLISH,
                    ItemID.fromString("cg.test:good-sememe"), PartOfSpeech.ADJECTIVE, 1.0f,
                    List.of(
                            FormEntry.of("better", GrammaticalFeature.COMPARATIVE),
                            FormEntry.of("best", GrammaticalFeature.SUPERLATIVE)
                    ));

            assertThat(english.inflect(good, Set.of(COMPARATIVE))).isEqualTo("better");
            assertThat(english.inflect(good, Set.of(SUPERLATIVE))).isEqualTo("best");
        }

        @Test
        @DisplayName("empty features returns lemma even with overrides")
        void emptyFeaturesReturnsLemma() {
            Lexeme run = new Lexeme("run", Language.ENGLISH,
                    ItemID.fromString("cg.test:run-sememe"), PartOfSpeech.VERB, 1.0f,
                    List.of(FormEntry.of("ran", GrammaticalFeature.PAST)));

            assertThat(english.inflect(run, Set.of())).isEqualTo("run");
            assertThat(english.inflect(run, null)).isEqualTo("run");
        }

        @Test
        @DisplayName("lexeme with no overrides uses regular rules")
        void noOverridesUsesRegularRules() {
            Lexeme walk = Lexeme.of("walk", Language.ENGLISH,
                    ItemID.fromString("cg.test:walk-sememe"), PartOfSpeech.VERB);

            assertThat(english.inflect(walk, Set.of(PAST))).isEqualTo("walked");
            assertThat(english.inflect(walk, Set.of(PARTICIPLE, PRESENT))).isEqualTo("walking");
            assertThat(english.inflect(walk, Set.of(THIRD_PERSON))).isEqualTo("walks");
        }
    }
}
