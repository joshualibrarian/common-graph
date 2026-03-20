package dev.everydaythings.graph.runtime;

import dev.everydaythings.graph.language.Language;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MoveVerbResolutionTest {

    @Test
    void moveToken_resolvesInDictionary() {
        Librarian lib = Librarian.createInMemory();
        LocalLibrarian local = new LocalLibrarian(lib);

        var postings = local.lookup("move").toList();
        System.err.println("move postings (unscoped): " + postings.size());
        for (var p : postings) {
            System.err.println("  token=" + p.token() + " target=" + p.target().encodeText()
                    + " features=" + p.features()
                    + " scope=" + (p.scope() != null ? p.scope().encodeText() : "null"));
        }

        var englishPostings = local.lookup("move", Language.ENGLISH).toList();
        System.err.println("move postings (English): " + englishPostings.size());
        for (var p : englishPostings) {
            System.err.println("  token=" + p.token() + " target=" + p.target().encodeText()
                    + " features=" + p.features());
        }

        assertThat(postings).as("'move' should resolve somewhere").isNotEmpty();
    }
}
