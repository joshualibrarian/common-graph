package dev.everydaythings.graph.importer;

import dev.everydaythings.graph.importer.WordNetImporter.LexicalEntryRecord;
import dev.everydaythings.graph.importer.WordNetImporter.SynsetRecord;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Smoke test for the LMF reader against the real OEWN 2025 resource bundle.
 *
 * <p>Verifies that the streaming XML parser handles the 85 MB file end-to-end,
 * and that known synsets/lexical entries resolve as expected.  The chosen
 * anchor — synset ili=i24940 for the math sense of "add" — is the same CILI id
 * we tagged onto the Add operator seed, so this test also verifies that our
 * tag is valid against the source data.
 *
 * <p>Tagged {@code slow} because parsing the full file takes several seconds
 * and the resource is 85 MB.
 */
@Tag("slow")
class LmfImporterTest {

    private static final String OEWN_RESOURCE = "/english-wordnet-2025.xml";

    @Test
    @DisplayName("LMF parser reads OEWN metadata (id, label, language, version)")
    void readsMetadata() throws IOException {
        try (LmfImporter importer = LmfImporter.fromResource(OEWN_RESOURCE)) {
            WordNetImporter.SourceMetadata meta = importer.metadata();
            assertThat(meta.id()).isEqualTo("oewn");
            assertThat(meta.label()).contains("Open English Wordnet");
            assertThat(meta.language()).isEqualTo("en");
            assertThat(meta.version()).isEqualTo("2025");
        }
    }

    @Test
    @DisplayName("Synset stream finds ili=i24940 (math 'add') with member 'add'")
    void findsMathAddSynset() throws IOException {
        try (LmfImporter importer = LmfImporter.fromResource(OEWN_RESOURCE)) {
            Optional<SynsetRecord> mathAdd = importer.synsets()
                    .filter(s -> "i24940".equals(s.ili()))
                    .findFirst();
            assertThat(mathAdd).as("synset with ili=i24940 (math 'add')").isPresent();
            SynsetRecord s = mathAdd.get();
            assertThat(s.pos()).isEqualTo("v");
            assertThat(s.members()).contains("add");
            assertThat(s.definition()).isNotEmpty();
        }
    }

    @Test
    @DisplayName("Lexical entry stream finds 'add' as a verb")
    void findsAddLexicalEntry() throws IOException {
        try (LmfImporter importer = LmfImporter.fromResource(OEWN_RESOURCE)) {
            Optional<LexicalEntryRecord> entry = importer.lexicalEntries()
                    .filter(e -> "add".equals(e.lemma()) && "v".equals(e.pos()))
                    .findFirst();
            assertThat(entry).as("lexical entry 'add' (verb)").isPresent();
            assertThat(entry.get().senses()).as("'add' has multiple verb senses").hasSizeGreaterThan(1);
        }
    }
}
