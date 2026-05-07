package dev.everydaythings.graph.runtime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Smoke tests for runtime detection of GraalVM Polyglot.
 *
 * <p>These tests don't assert WHETHER Polyglot is available — that's
 * environment-dependent. They assert the detection runs without throwing
 * and produces consistent results.
 */
class PolyglotEnvironmentTest {

    @Test
    @DisplayName("detection runs without throwing, regardless of host JVM")
    void detectionDoesNotThrow() {
        // Just calling this should never throw, no matter the JVM
        boolean available = PolyglotEnvironment.isAvailable();
        // tautology to assert SOMETHING
        assertThat(available).isIn(true, false);
    }

    @Test
    @DisplayName("availableLanguages returns empty set if not available, otherwise non-null")
    void availableLanguagesIsConsistent() {
        var languages = PolyglotEnvironment.availableLanguages();
        assertThat(languages).isNotNull();
        if (!PolyglotEnvironment.isAvailable()) {
            assertThat(languages).isEmpty();
        }
    }

    @Test
    @DisplayName("repeated calls return the same cached result")
    void detectionIsCached() {
        boolean first = PolyglotEnvironment.isAvailable();
        boolean second = PolyglotEnvironment.isAvailable();
        assertThat(first).isEqualTo(second);
    }
}
