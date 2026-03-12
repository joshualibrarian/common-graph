package dev.everydaythings.graph.item;

import dev.everydaythings.graph.item.action.ActionResult;
import dev.everydaythings.graph.runtime.Librarian;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for the full verb dispatch flow.
 */
@Tag("slow")
class VerbDispatchTest {

    @Test
    void dispatchCreateViaToken(@TempDir Path testDir) {
        try (Librarian lib = Librarian.open(testDir)) {
            // Dispatch "new" command (should resolve to CREATE verb -> actionNew)
            ActionResult result = lib.dispatch("new", List.of());

            System.out.println("Dispatch 'new' result:");
            System.out.println("  Success: " + result.success());
            if (result.success()) {
                System.out.println("  Result type: " + (result.value() != null ? result.value().getClass().getSimpleName() : "null"));
            } else {
                System.out.println("  Error: " + result.error());
                if (result.error() != null) {
                    result.error().printStackTrace(System.out);
                }
            }

            // actionNew should succeed (though it may return null or partial result)
            // The important thing is it doesn't fail with "Unknown command"
            assertThat(result.success() ||
                       (result.error() != null && !result.error().getMessage().contains("Unknown command")))
                    .as("Dispatch should find 'new' command via vocabulary")
                    .isTrue();
        }
    }

    @Test
    void dispatchCreateDirectly(@TempDir Path testDir) {
        try (Librarian lib = Librarian.open(testDir)) {
            // Dispatch "create" command
            ActionResult result = lib.dispatch("create", List.of());

            System.out.println("Dispatch 'create' result:");
            System.out.println("  Success: " + result.success());
            if (!result.success() && result.error() != null) {
                System.out.println("  Error: " + result.error().getMessage());
            }

            // Should not fail with "Unknown command"
            if (!result.success() && result.error() != null) {
                assertThat(result.error().getMessage())
                        .as("Should not be unknown command - vocabulary should resolve 'create'")
                        .doesNotContain("Unknown command");
            }
        }
    }

    @Test
    void vocabularyContainsExpectedVerbs(@TempDir Path testDir) {
        try (Librarian lib = Librarian.open(testDir)) {
            Vocabulary vocab = lib.vocabulary();

            System.out.println("Vocabulary contents:");
            for (VerbEntry entry : vocab) {
                System.out.println("  " + entry.sememeKey() + " -> " + entry.methodName() +
                                   " (source=" + entry.source() + ", target=" +
                                   (entry.target() != null ? entry.target().getClass().getSimpleName() : "null") + ")");
            }

            assertThat(vocab.size())
                    .as("Vocabulary should have verbs")
                    .isGreaterThan(0);
        }
    }
}
