package dev.everydaythings.graph.language;

import dev.everydaythings.graph.item.id.ItemID;
import dev.everydaythings.graph.language.importer.UniMorphReader;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("UniMorph Reader")
class UniMorphReaderTest {

    static final ItemID PAST = GrammaticalFeature.PAST.iid();
    static final ItemID PRESENT = GrammaticalFeature.PRESENT.iid();
    static final ItemID PARTICIPLE = GrammaticalFeature.PARTICIPLE.iid();
    static final ItemID THIRD_PERSON = GrammaticalFeature.THIRD_PERSON.iid();
    static final ItemID PLURAL = GrammaticalFeature.PLURAL.iid();
    static final ItemID COMPARATIVE = GrammaticalFeature.COMPARATIVE.iid();
    static final ItemID SUPERLATIVE = GrammaticalFeature.SUPERLATIVE.iid();

    // English instance for simplification tests
    static final Language english = new English(ItemID.fromString(English.KEY));

    // ==================================================================================
    // PARSING (universal — tests the core reader)
    // ==================================================================================

    @Nested
    @DisplayName("TSV Parsing")
    class Parsing {

        @Test
        @DisplayName("parses verb past tense")
        void parseVerbPast() throws IOException {
            var data = parse("run\tran\tV;PST\n");
            assertThat(data).containsKey("run");
            var entries = data.get("run");
            assertThat(entries).hasSize(1);
            assertThat(entries.get(0).form()).isEqualTo("ran");
            assertThat(entries.get(0).features()).contains(PAST);
            assertThat(entries.get(0).pos()).isEqualTo(PartOfSpeech.VERB);
        }

        @Test
        @DisplayName("parses verb present participle")
        void parseVerbPresentParticiple() throws IOException {
            var data = parse("run\trunning\tV;V.PTCP;PRS\n");
            var entries = data.get("run");
            assertThat(entries).hasSize(1);
            assertThat(entries.get(0).form()).isEqualTo("running");
            assertThat(entries.get(0).features()).contains(PARTICIPLE, PRESENT);
        }

        @Test
        @DisplayName("skips entries where form equals lemma")
        void skipBaseForm() throws IOException {
            var data = parse("run\trun\tV;V.PTCP;PST\n");
            assertThat(data).doesNotContainKey("run");
        }

        @Test
        @DisplayName("parses 3rd person singular present (raw features)")
        void parseThirdPerson() throws IOException {
            var data = parse("run\truns\tV;3;SG;PRS;IND\n");
            var entries = data.get("run");
            assertThat(entries).hasSize(1);
            assertThat(entries.get(0).form()).isEqualTo("runs");
            // Raw features include THIRD_PERSON, SINGULAR, PRESENT (IND is ignored)
            assertThat(entries.get(0).features()).contains(THIRD_PERSON);
        }

        @Test
        @DisplayName("parses noun plural")
        void parseNounPlural() throws IOException {
            var data = parse("child\tchildren\tN;PL\n");
            var entries = data.get("child");
            assertThat(entries).hasSize(1);
            assertThat(entries.get(0).form()).isEqualTo("children");
            assertThat(entries.get(0).features()).contains(PLURAL);
        }

        @Test
        @DisplayName("parses adjective comparative and superlative")
        void parseAdjectiveDegree() throws IOException {
            var data = parse("good\tbetter\tADJ;CMPR\ngood\tbest\tADJ;SPRL\n");
            var entries = data.get("good");
            assertThat(entries).hasSize(2);
            assertThat(entries.get(0).form()).isEqualTo("better");
            assertThat(entries.get(0).features()).contains(COMPARATIVE);
            assertThat(entries.get(1).form()).isEqualTo("best");
            assertThat(entries.get(1).features()).contains(SUPERLATIVE);
        }

        @Test
        @DisplayName("skips comments and blank lines")
        void skipCommentsAndBlanks() throws IOException {
            var data = parse("# comment\n\nrun\tran\tV;PST\n");
            assertThat(data).containsKey("run");
            assertThat(data).hasSize(1);
        }

        @Test
        @DisplayName("groups multiple entries per lemma")
        void groupsByLemma() throws IOException {
            var data = parse("""
                    run\tran\tV;PST
                    run\trunning\tV;V.PTCP;PRS
                    run\truns\tV;3;SG;PRS;IND
                    """);
            assertThat(data.get("run")).hasSize(3);
        }
    }

    // ==================================================================================
    // FEATURE SIMPLIFICATION (English-specific — tests Language.simplifyFeatures)
    // ==================================================================================

    @Nested
    @DisplayName("English Feature Simplification")
    class EnglishSimplification {

        @Test
        @DisplayName("verb: {THIRD_PERSON, SINGULAR, PRESENT} → {THIRD_PERSON}")
        void verbThirdPerson() {
            Set<ItemID> result = english.simplifyFeatures(
                    Set.of(THIRD_PERSON, GrammaticalFeature.SINGULAR.iid(), PRESENT),
                    PartOfSpeech.VERB);
            assertThat(result).isEqualTo(Set.of(THIRD_PERSON));
        }

        @Test
        @DisplayName("verb: {PAST} → {PAST}")
        void verbPast() {
            Set<ItemID> result = english.simplifyFeatures(Set.of(PAST), PartOfSpeech.VERB);
            assertThat(result).isEqualTo(Set.of(PAST));
        }

        @Test
        @DisplayName("verb: {PARTICIPLE, PAST} → {PARTICIPLE, PAST}")
        void verbPastParticiple() {
            Set<ItemID> result = english.simplifyFeatures(
                    Set.of(PARTICIPLE, PAST), PartOfSpeech.VERB);
            assertThat(result).isEqualTo(Set.of(PARTICIPLE, PAST));
        }

        @Test
        @DisplayName("verb: {PARTICIPLE, PRESENT} → {PARTICIPLE, PRESENT}")
        void verbPresentParticiple() {
            Set<ItemID> result = english.simplifyFeatures(
                    Set.of(PARTICIPLE, PRESENT), PartOfSpeech.VERB);
            assertThat(result).isEqualTo(Set.of(PARTICIPLE, PRESENT));
        }

        @Test
        @DisplayName("verb: {PARTICIPLE} alone → {PARTICIPLE, PRESENT} (gerund default)")
        void verbParticipleAlone() {
            Set<ItemID> result = english.simplifyFeatures(
                    Set.of(PARTICIPLE), PartOfSpeech.VERB);
            assertThat(result).isEqualTo(Set.of(PARTICIPLE, PRESENT));
        }

        @Test
        @DisplayName("noun: {PLURAL} → {PLURAL}")
        void nounPlural() {
            Set<ItemID> result = english.simplifyFeatures(Set.of(PLURAL), PartOfSpeech.NOUN);
            assertThat(result).isEqualTo(Set.of(PLURAL));
        }

        @Test
        @DisplayName("adjective: {COMPARATIVE} → {COMPARATIVE}")
        void adjComparative() {
            Set<ItemID> result = english.simplifyFeatures(
                    Set.of(COMPARATIVE), PartOfSpeech.ADJECTIVE);
            assertThat(result).isEqualTo(Set.of(COMPARATIVE));
        }

        @Test
        @DisplayName("unrecognized feature combo yields empty set")
        void unknownFeatures() {
            Set<ItemID> result = english.simplifyFeatures(
                    Set.of(GrammaticalFeature.FIRST_PERSON.iid(), PRESENT),
                    PartOfSpeech.VERB);
            assertThat(result).isEmpty();
        }
    }

    // ==================================================================================
    // RESOURCE LOADING
    // ==================================================================================

    @Test
    @DisplayName("load returns empty map when resource not found")
    void loadReturnsEmptyWhenNotFound() {
        Map<String, List<UniMorphReader.Entry>> data = UniMorphReader.load("/nonexistent");
        assertThat(data).isEmpty();
    }

    // ==================================================================================
    // HELPERS
    // ==================================================================================

    private Map<String, List<UniMorphReader.Entry>> parse(String tsv) throws IOException {
        return UniMorphReader.parse(new BufferedReader(new StringReader(tsv)));
    }
}
