package dev.everydaythings.graph.item;

import dev.everydaythings.graph.item.component.Log;
import dev.everydaythings.graph.item.id.ItemID;
import dev.everydaythings.graph.language.Sememe;
import dev.everydaythings.graph.runtime.Librarian;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for the Vocabulary system - verbs as Sememes.
 */
class VocabularyTest {

    @Test
    void itemHasVocabulary(@TempDir Path testDir) {
        try (Librarian lib = Librarian.open(testDir)) {
            // Every item should have a vocabulary
            assertThat(lib.vocabulary()).isNotNull();
        }
    }

    @Test
    void vocabularyContainsCreateVerb(@TempDir Path testDir) {
        try (Librarian lib = Librarian.open(testDir)) {
            Vocabulary vocab = lib.vocabulary();

            // Item.actionNew() is annotated with @Verb("cg.verb:create")
            ItemID createSememe = ItemID.fromString(Sememe.CREATE);
            Optional<VerbEntry> createVerb = vocab.lookup(createSememe);

            assertThat(createVerb)
                    .as("Vocabulary should contain the CREATE verb from actionNew()")
                    .isPresent();

            assertThat(createVerb.get().methodName())
                    .as("CREATE verb should map to actionNew method")
                    .isEqualTo("actionNew");
        }
    }

    @Test
    void verbSememeHasTokens() {
        // The CREATE Sememe should have tokens like "create", "new", "make"
        assertThat(Sememe.create.tokens())
                .as("CREATE Sememe should have token aliases")
                .contains("create", "new", "make");

        assertThat(Sememe.get.tokens())
                .as("GET Sememe should have token aliases")
                .contains("get", "retrieve", "fetch", "lookup");
    }

    @Test
    void verbSpecFromAnnotation(@TempDir Path testDir) {
        try (Librarian lib = Librarian.open(testDir)) {
            // Get the schema for Librarian class
            ItemSchema schema = ItemScanner.schemaFor(Librarian.class);

            // Schema should have verbSpecs from @Verb annotations
            assertThat(schema.verbSpecs())
                    .as("Schema should contain verb specs")
                    .isNotEmpty();

            // Find the CREATE verb spec
            Optional<VerbSpec> createSpec = schema.verbSpecs().stream()
                    .filter(vs -> vs.sememeId().equals(ItemID.fromString(Sememe.CREATE)))
                    .findFirst();

            assertThat(createSpec)
                    .as("Schema should have CREATE verb spec")
                    .isPresent();
        }
    }

    @Test
    void verbEntryHasCorrectSource(@TempDir Path testDir) {
        try (Librarian lib = Librarian.open(testDir)) {
            Vocabulary vocab = lib.vocabulary();

            ItemID createSememe = ItemID.fromString(Sememe.CREATE);
            Optional<VerbEntry> createVerb = vocab.lookup(createSememe);

            assertThat(createVerb).isPresent();
            assertThat(createVerb.get().source())
                    .as("Item verb should have ITEM source")
                    .isEqualTo(VerbSpec.VerbSource.ITEM);
        }
    }

    @Test
    void componentVerbsAreScanned() {
        // Test that @Verb annotations are scanned from component classes
        // Log has verbs: PUT (append), LIST (read), COUNT (count), SHOW (tail), GET (latest)

        List<VerbSpec> verbs = ItemScanner.scanComponentVerbs(Log.class, "log");

        assertThat(verbs)
                .as("Log should have component verbs scanned")
                .isNotEmpty();

        // Check for PUT verb (append method)
        Optional<VerbSpec> putVerb = verbs.stream()
                .filter(vs -> vs.sememeId().equals(ItemID.fromString(Sememe.PUT)))
                .findFirst();

        assertThat(putVerb)
                .as("Log should have PUT verb from append method")
                .isPresent();

        assertThat(putVerb.get().methodName())
                .as("PUT verb should map to append method")
                .isEqualTo("append");

        assertThat(putVerb.get().source())
                .as("Component verb should have COMPONENT source")
                .isEqualTo(VerbSpec.VerbSource.COMPONENT);

        // Check for LIST verb (read method)
        Optional<VerbSpec> listVerb = verbs.stream()
                .filter(vs -> vs.sememeId().equals(ItemID.fromString(Sememe.LIST)))
                .findFirst();

        assertThat(listVerb)
                .as("Log should have LIST verb from read method")
                .isPresent();

        assertThat(listVerb.get().methodName())
                .as("LIST verb should map to read method")
                .isEqualTo("read");

        // Check for SHOW verb (tail method)
        Optional<VerbSpec> showVerb = verbs.stream()
                .filter(vs -> vs.sememeId().equals(ItemID.fromString(Sememe.SHOW)))
                .findFirst();

        assertThat(showVerb)
                .as("Log should have SHOW verb from tail method")
                .isPresent();

        assertThat(showVerb.get().methodName())
                .as("SHOW verb should map to tail method")
                .isEqualTo("tail");

        // Check for GET verb (latest method)
        Optional<VerbSpec> getVerb = verbs.stream()
                .filter(vs -> vs.sememeId().equals(ItemID.fromString(Sememe.GET)))
                .findFirst();

        assertThat(getVerb)
                .as("Log should have GET verb from latest method")
                .isPresent();

        assertThat(getVerb.get().methodName())
                .as("GET verb should map to latest method")
                .isEqualTo("latest");
    }

    @Test
    void librarianVerbsAreScanned(@TempDir Path testDir) {
        try (Librarian lib = Librarian.open(testDir)) {
            // Get the schema for Librarian class
            ItemSchema schema = ItemScanner.schemaFor(Librarian.class);

            // Should have GET verb for get() method
            Optional<VerbSpec> getVerb = schema.verbSpecs().stream()
                    .filter(vs -> vs.sememeId().equals(ItemID.fromString(Sememe.GET)))
                    .findFirst();

            assertThat(getVerb)
                    .as("Librarian schema should have GET verb")
                    .isPresent();

            assertThat(getVerb.get().methodName())
                    .as("GET verb should map to get method")
                    .isEqualTo("get");

            // Should have LIST verb for types() method
            Optional<VerbSpec> listVerb = schema.verbSpecs().stream()
                    .filter(vs -> vs.sememeId().equals(ItemID.fromString(Sememe.LIST)))
                    .findFirst();

            assertThat(listVerb)
                    .as("Librarian schema should have LIST verb")
                    .isPresent();

            assertThat(listVerb.get().methodName())
                    .as("LIST verb should map to types method")
                    .isEqualTo("types");

            // Should have QUERY verb for query() method
            Optional<VerbSpec> queryVerb = schema.verbSpecs().stream()
                    .filter(vs -> vs.sememeId().equals(ItemID.fromString(Sememe.QUERY)))
                    .findFirst();

            assertThat(queryVerb)
                    .as("Librarian schema should have QUERY verb")
                    .isPresent();

            assertThat(queryVerb.get().methodName())
                    .as("QUERY verb should map to query method")
                    .isEqualTo("query");
        }
    }

    @Test
    void tokenLookupResolvesToSememe(@TempDir Path testDir) {
        try (Librarian lib = Librarian.open(testDir)) {
            var tokenDict = lib.tokenIndex();
            assertThat(tokenDict).as("TokenDictionary should be available").isNotNull();

            // Look up "create" - should find CREATE Sememe
            var createPostings = tokenDict.lookup("create").toList();
            assertThat(createPostings)
                    .as("Token 'create' should have postings")
                    .isNotEmpty();

            // The posting should point to CREATE Sememe's IID
            ItemID createSememeId = ItemID.fromString(Sememe.CREATE);
            boolean foundCreate = createPostings.stream()
                    .anyMatch(p -> p.target().equals(createSememeId));
            assertThat(foundCreate)
                    .as("Token 'create' should resolve to CREATE Sememe")
                    .isTrue();

            // Also test alias: "new" should also resolve to CREATE
            var newPostings = tokenDict.lookup("new").toList();
            assertThat(newPostings)
                    .as("Token 'new' should have postings")
                    .isNotEmpty();

            boolean foundCreateFromNew = newPostings.stream()
                    .anyMatch(p -> p.target().equals(createSememeId));
            assertThat(foundCreateFromNew)
                    .as("Token 'new' (alias) should resolve to CREATE Sememe")
                    .isTrue();
        }
    }

    @Test
    void vocabularyLookupTokenResolvesVerb(@TempDir Path testDir) {
        try (Librarian lib = Librarian.open(testDir)) {
            Vocabulary vocab = lib.vocabulary();

            // The vocabulary's lookupToken should resolve "create" to the CREATE verb
            // (This tests the full flow: token → TokenDictionary → Sememe → Vocabulary)
            Optional<VerbEntry> createVerb = vocab.lookupToken("create", lib);

            assertThat(createVerb)
                    .as("Vocabulary.lookupToken('create') should find the CREATE verb")
                    .isPresent();

            assertThat(createVerb.get().sememeId())
                    .as("Looked up verb should be the CREATE Sememe")
                    .isEqualTo(ItemID.fromString(Sememe.CREATE));

            assertThat(createVerb.get().methodName())
                    .as("CREATE verb should map to actionNew method")
                    .isEqualTo("actionNew");
        }
    }
}
