package dev.everydaythings.graph.language;

import dev.everydaythings.graph.item.ItemTest;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Abstract base test for Language behavior.
 *
 * <p>Tests the contracts that all Language items must satisfy,
 * including language code and lexicon management.
 *
 * <p>Subclasses provide specific Language implementations to test.
 */
@Disabled
public abstract class LanguageTest extends ItemTest {

    /**
     * Get the item as a Language.
     */
    protected Language language() {
        return (Language) item;
    }

    /**
     * Language items are created via Librarian.get() and don't have their own store.
     */
    @Override
    protected boolean supportsPersist() {
        return false;
    }

    // ==================================================================================
    // Language Code Tests
    // ==================================================================================

    @Nested
    @DisplayName("Language Code")
    class LanguageCode {

        @Test
        @DisplayName("has a language code")
        void hasLanguageCode() {
            assertThat(language().languageCode())
                    .as("Language code")
                    .isNotNull()
                    .isNotBlank();
        }

        @Test
        @DisplayName("language code is ISO 639 format")
        void languageCodeIsIsoFormat() {
            String code = language().languageCode();

            // ISO 639-1 is 2 chars, ISO 639-3 is 3 chars
            assertThat(code.length())
                    .as("Language code length")
                    .isBetween(2, 3);

            // Should be lowercase letters only
            assertThat(code)
                    .as("Language code format")
                    .matches("[a-z]+");
        }
    }

    // ==================================================================================
    // Lexicon Tests
    // ==================================================================================

    @Nested
    @DisplayName("Lexicon")
    class LexiconTests {

        @Test
        @DisplayName("has a lexicon")
        void hasLexicon() {
            assertThat(language().lexicon())
                    .as("Lexicon")
                    .isNotNull();
        }

        @Test
        @DisplayName("lexicon is associated with this language")
        void lexiconIsAssociatedWithLanguage() {
            Lexicon lex = language().lexicon();

            // The lexicon should be for this language's IID
            assertThat(lex.languageId())
                    .as("Lexicon language ID")
                    .isEqualTo(language().iid());
        }

        @Test
        @DisplayName("lexicon lookup returns stream for unknown word")
        void lexiconLookupReturnsStreamForUnknown() {
            var results = language().lexicon().lookup("xyznonexistentword123");

            assertThat(results)
                    .as("Lookup stream")
                    .isNotNull();

            // Unknown word should have no results
            assertThat(results.toList())
                    .as("Unknown word results")
                    .isEmpty();
        }
    }
}
