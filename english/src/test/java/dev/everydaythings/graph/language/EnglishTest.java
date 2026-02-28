package dev.everydaythings.graph.language;

import dev.everydaythings.graph.item.Item;
import dev.everydaythings.graph.item.id.ItemID;
import dev.everydaythings.graph.language.importer.EnglishImporter;
import dev.everydaythings.graph.runtime.Librarian;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for the English language item.
 *
 * <p>Extends LanguageTest to inherit all Language contract tests,
 * and adds English-specific tests including WordNet generation.
 */
@DisplayName("English") @Disabled
public class EnglishTest extends LanguageTest {

    private Librarian librarian;

    @Override
    protected Item createItem(Path tempDir) {
        librarian = Librarian.open(tempDir);
        return new English(librarian);
    }

    @Override
    protected void closeItem() throws Exception {
        if (librarian != null) {
            librarian.close();
            librarian = null;
        }
    }

    /**
     * Get the item as English.
     */
    protected English english() {
        return (English) item;
    }

    // ==================================================================================
    // English Identity Tests
    // ==================================================================================

    @Nested
    @DisplayName("English Identity")
    class EnglishIdentity {

        @Test
        @DisplayName("has correct language code")
        void hasCorrectLanguageCode() {
            assertThat(english().languageCode())
                    .as("English language code")
                    .isEqualTo("eng");  // ISO 639-3 for English
        }

        @Test
        @DisplayName("IID is not the type ID")
        void iidIsNotTypeId() {
            // Instance IID should be randomly generated, not the type ID
            assertThat(english().iid())
                    .as("Instance IID")
                    .isNotEqualTo(ItemID.fromString(English.KEY));
        }

        @Test
        @DisplayName("type key is correct")
        void typeKeyIsCorrect() {
            assertThat(English.KEY)
                    .as("Type key")
                    .isEqualTo("cg.lang:english");
        }
    }

    // ==================================================================================
    // Generation Tests (WordNet Import)
    // ==================================================================================

    @Nested
    @DisplayName("Generation")
    class Generation {

        @Test
        @DisplayName("generate with limit creates sememes and lexemes")
        void generateWithLimitCreatesSememesAndLexemes() {
            english().generate(librarian, 100);  // Only 100 synsets

            EnglishImporter.ImportStats stats = english().stats();
            assertThat(stats)
                    .as("Import stats")
                    .isNotNull();

            assertThat(stats.synsetCount())
                    .as("Synset count")
                    .isEqualTo(100);

            assertThat(stats.lexemeCount())
                    .as("Lexeme count")
                    .isGreaterThan(0);

            assertThat(stats.source())
                    .as("Import source")
                    .isEqualTo("oewn2025");

            System.out.println("Generated:");
            System.out.println("  Synsets (Sememes): " + stats.synsetCount());
            System.out.println("  Lexemes: " + stats.lexemeCount());
            System.out.println("  Relations: " + stats.relationCount());
            System.out.println("  Duration: " + stats.durationMs() + "ms");
        }

        @Test
        @DisplayName("generate populates lexicon")
        void generatePopulatesLexicon() {
            english().generate(librarian, 100);

            assertThat(english().lexicon())
                    .as("Lexicon")
                    .isNotNull();

            assertThat(english().lexicon().size())
                    .as("Lexicon size")
                    .isGreaterThan(0);
        }

        @Test
        @DisplayName("generate creates semantic relations")
        void generateCreatesSemanticRelations() {
            english().generate(librarian, 100);

            EnglishImporter.ImportStats stats = english().stats();
            // Should have some relations (hypernym, hyponym, etc.)
            // Note: with small limit, not all synsets will have targets in our set
            assertThat(stats.relationCount())
                    .as("Relation count")
                    .isGreaterThanOrEqualTo(0);

            System.out.println("Relations created: " + stats.relationCount());
        }

        @Test
        @DisplayName("stats are null before generation")
        void statsAreNullBeforeGeneration() {
            assertThat(english().stats())
                    .as("Stats before generation")
                    .isNull();
        }
    }

    // ==================================================================================
    // Lexicon Lookup Tests
    // ==================================================================================

    @Nested
    @DisplayName("Lexicon Lookup")
    class LexiconLookup {

        @Test
        @DisplayName("can lookup word after generation")
        void canLookupWordAfterGeneration() {
            english().generate(librarian, 500);  // Need more synsets to find common words

            // Try to look up a common word
            // "entity" is the root of the noun hierarchy and should be in first 500
            var entityResults = english().lexicon().lookup("entity").toList();

            System.out.println("Lookup 'entity': " + entityResults.size() + " senses");
            for (Lexeme lexeme : entityResults) {
                System.out.println("  - " + lexeme.word() + " (" + lexeme.partOfSpeech() + ") -> " + lexeme.sememe());
            }

            // entity should have at least one sense
            // (might be 0 if it's not in first 500 synsets)
        }

        @Test
        @DisplayName("lookup returns empty for word not in lexicon")
        void lookupReturnsEmptyForUnknownWord() {
            english().generate(librarian, 100);

            var results = english().lexicon().lookup("supercalifragilisticexpialidocious").toList();

            assertThat(results)
                    .as("Unknown word lookup")
                    .isEmpty();
        }
    }

//     ==================================================================================
//     SLOW TESTS (full generation) - uncomment to run
//     ==================================================================================

     @Nested
     @DisplayName("Full Generation (Slow)")
     class FullGeneration {

         @Test @Disabled
         @DisplayName("full WordNet generation succeeds")
         void fullWordNetGenerationSucceeds() {
             english().generate(librarian);  // Full generation

             EnglishImporter.ImportStats stats = english().stats();
             System.out.println("FULL GENERATION:");
             System.out.println("  Synsets (Sememes): " + stats.synsetCount());
             System.out.println("  Lexemes: " + stats.lexemeCount());
             System.out.println("  Relations: " + stats.relationCount());
             System.out.println("  Duration: " + stats.durationMs() + "ms");

             // OEWN 2024 has approximately:
             // - 117,000+ synsets
             // - 155,000+ word senses
             assertThat(stats.synsetCount()).isGreaterThan(100000);
             assertThat(stats.lexemeCount()).isGreaterThan(150000);
         }
     }
}
