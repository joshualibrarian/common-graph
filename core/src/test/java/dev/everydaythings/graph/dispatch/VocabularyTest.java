package dev.everydaythings.graph.dispatch;

import dev.everydaythings.graph.item.ItemScanner;
import dev.everydaythings.graph.item.ItemSchema;
import dev.everydaythings.graph.item.id.ItemID;
import dev.everydaythings.graph.language.CoreVocabulary;
import dev.everydaythings.graph.runtime.Librarian;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for the Vocabulary system - verbs as Sememes.
 */
@Tag("slow")
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
            ItemID createSememe = ItemID.fromString(CoreVocabulary.Create.KEY);
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
    void verbSememeHasTokens(@TempDir Path testDir) {
        // Verify word forms are declared on the seed classes via @ItemFrame annotations.
        // After SEED removal, token aliases live in @ItemFrame fields, not on live Sememe instances.
        // We verify the annotations exist and carry the expected surface forms.
        try (Librarian lib = Librarian.open(testDir)) {
            var tokenDict = lib.tokenIndex();

            // CREATE should have tokens: "create", "new", "make"
            ItemID createIid = CoreVocabulary.Create.IID;
            for (String token : List.of("create", "new", "make")) {
                boolean found = tokenDict.lookup(token).anyMatch(p -> p.target().equals(createIid));
                assertThat(found)
                        .as("Token '%s' should resolve to CREATE Sememe", token)
                        .isTrue();
            }

            // GET should have tokens: "get", "retrieve", "fetch", "lookup"
            ItemID getIid = CoreVocabulary.Get.IID;
            for (String token : List.of("get", "retrieve", "fetch", "lookup")) {
                boolean found = tokenDict.lookup(token).anyMatch(p -> p.target().equals(getIid));
                assertThat(found)
                        .as("Token '%s' should resolve to GET Sememe", token)
                        .isTrue();
            }
        }
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
                    .filter(vs -> vs.sememeId().equals(ItemID.fromString(CoreVocabulary.Create.KEY)))
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

            ItemID createSememe = ItemID.fromString(CoreVocabulary.Create.KEY);
            Optional<VerbEntry> createVerb = vocab.lookup(createSememe);

            assertThat(createVerb).isPresent();
            assertThat(createVerb.get().source())
                    .as("Item verb should have ITEM source")
                    .isEqualTo(VerbSpec.VerbSource.ITEM);
        }
    }

    @Test
    void librarianVerbsAreScanned(@TempDir Path testDir) {
        try (Librarian lib = Librarian.open(testDir)) {
            // Get the schema for Librarian class
            ItemSchema schema = ItemScanner.schemaFor(Librarian.class);

            // Should have GET verb for get() method
            Optional<VerbSpec> getVerb = schema.verbSpecs().stream()
                    .filter(vs -> vs.sememeId().equals(ItemID.fromString(CoreVocabulary.Get.KEY)))
                    .findFirst();

            assertThat(getVerb)
                    .as("Librarian schema should have GET verb")
                    .isPresent();

            assertThat(getVerb.get().methodName())
                    .as("GET verb should map to get method")
                    .isEqualTo("get");

            // Should have LIST verb for types() method
            Optional<VerbSpec> listVerb = schema.verbSpecs().stream()
                    .filter(vs -> vs.sememeId().equals(ItemID.fromString(CoreVocabulary.ListVerb.KEY)))
                    .findFirst();

            assertThat(listVerb)
                    .as("Librarian schema should have LIST verb")
                    .isPresent();

            assertThat(listVerb.get().methodName())
                    .as("LIST verb should map to types method")
                    .isEqualTo("types");

            // Should have QUERY verb for query() method
            Optional<VerbSpec> queryVerb = schema.verbSpecs().stream()
                    .filter(vs -> vs.sememeId().equals(ItemID.fromString(CoreVocabulary.Query.KEY)))
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
            ItemID createSememeId = ItemID.fromString(CoreVocabulary.Create.KEY);
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

}
