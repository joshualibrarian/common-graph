package dev.everydaythings.graph.runtime;

import dev.everydaythings.graph.item.Item;
import dev.everydaythings.graph.item.relation.Relation;
import dev.everydaythings.graph.item.VerbEntry;
import dev.everydaythings.graph.item.action.ActionResult;
import dev.everydaythings.graph.item.id.ItemID;
import dev.everydaythings.graph.language.Sememe;
import dev.everydaythings.graph.item.user.SignerTest;
import dev.everydaythings.graph.library.Library;
import org.junit.jupiter.api.*;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for Librarian - the root Item that hosts a graph.
 *
 * <p>Inherits:
 * <ul>
 *   <li>All universal Item tests from {@link dev.everydaythings.graph.item.ItemTest}</li>
 *   <li>All Signer tests from {@link SignerTest}</li>
 * </ul>
 *
 * <p>Plus Librarian-specific functionality tests.
 */
@DisplayName("Librarian") @Disabled
class LibrarianTest extends SignerTest {

    // Typed reference to the librarian (same instance as 'item' and signer())
    private Librarian librarian;

    @Override
    protected Item createItem(Path tempDir) {
        librarian = Librarian.open(tempDir);
        return librarian;
    }

    @Override
    protected void closeItem() throws Exception {
        if (librarian != null) {
            librarian.close();
        }
    }

    // ==================================================================================
    // Librarian Identity
    // ==================================================================================

    @Nested
    @DisplayName("Librarian Identity")
    class LibrarianIdentity {

        @Test
        @DisplayName("is a fresh boot on first creation")
        void isFreshBootOnFirstCreation() {
            assertThat(librarian.freshBoot())
                    .as("First boot should be fresh")
                    .isTrue();
        }

        @Test
        @DisplayName("preserves identity across reopen")
        void preservesIdentityAcrossReopen() {
            ItemID originalIid = librarian.iid();
            Path rootPath = librarian.rootPath();

            // Close and reopen
            librarian.close();
            librarian = Librarian.open(rootPath);

            assertThat(librarian.iid())
                    .as("IID should be preserved")
                    .isEqualTo(originalIid);

            assertThat(librarian.freshBoot())
                    .as("Reopen should not be fresh boot")
                    .isFalse();
        }

        @Test
        @DisplayName("preserves public key across reopen")
        void publicKeyPreservedAcrossReopen() {
            byte[] originalKeyBytes = librarian.publicKey().spki();
            Path rootPath = librarian.rootPath();

            librarian.close();
            librarian = Librarian.open(rootPath);

            assertThat(librarian.publicKey().spki())
                    .as("Public key should be preserved")
                    .isEqualTo(originalKeyBytes);
        }
    }

    // ==================================================================================
    // Library Component
    // ==================================================================================

    @Nested
    @DisplayName("Library Component")
    class LibraryComponent {

        @Test
        @DisplayName("has a library")
        void hasLibrary() {
            assertThat(librarian.library())
                    .as("Library component")
                    .isNotNull();
        }

        @Test
        @DisplayName("library is accessible via component lookup")
        void libraryAccessibleViaComponentLookup() {
            Object comp = librarian.component("library");

            assertThat(comp)
                    .as("Library via component()")
                    .isNotNull()
                    .isInstanceOf(Library.class);
        }

        @Test
        @DisplayName("library has primary store")
        void libraryHasPrimaryStore() {
            assertThat(librarian.library().primaryStore())
                    .as("Primary store")
                    .isPresent();
        }

        @Test
        @DisplayName("library can execute queries")
        void libraryCanExecuteQueries() {
            // Library owns the index internally; we verify via query API
            // Query for implemented-by relations (should return types)
            var results = librarian.library().byPredicate(
                    dev.everydaythings.graph.language.Sememe.IMPLEMENTED_BY.iid()).toList();
            assertThat(results)
                    .as("Library should have indexed implementedBy relations")
                    .isNotEmpty();
        }
    }

    // ==================================================================================
    // Librarian Verbs
    // ==================================================================================

    @Nested
    @DisplayName("Librarian Verbs")
    class LibrarianVerbs {

        @Test
        @DisplayName("debug: print all verbs")
        void debugPrintVerbs() {
            System.out.println("=== ALL REGISTERED VERBS ===");
            for (VerbEntry v : librarian.vocabulary()) {
                System.out.println("  " + v.sememeKey() + " → " + v.methodName()
                    + " [" + v.source() + "] doc=" + v.doc());
            }
            System.out.println("=== END VERBS ===");
        }

        @Test
        @DisplayName("has CREATE verb from base Item")
        void hasCreateVerb() {
            assertThat(librarian.vocabulary().lookup(ItemID.fromString(Sememe.CREATE)))
                    .as("CREATE verb from base Item")
                    .isPresent();
        }

        @Test
        @DisplayName("has GET verb")
        void hasGetVerb() {
            assertThat(librarian.vocabulary().lookup(ItemID.fromString(Sememe.GET)))
                    .as("GET verb")
                    .isPresent();
        }

        @Test
        @DisplayName("has QUERY verb")
        void hasQueryVerb() {
            assertThat(librarian.vocabulary().lookup(ItemID.fromString(Sememe.QUERY)))
                    .as("QUERY verb")
                    .isPresent();
        }
    }

    // ==================================================================================
    // Type Registry
    // ==================================================================================

    @Nested
    @DisplayName("Type Registry")
    class TypeRegistry {

        @Test
        @DisplayName("can list types")
        void canListTypes() {
            List<ItemID> types = librarian.types().toList();

            assertThat(types)
                    .as("Types list")
                    .isNotEmpty();
        }

        @Test
        @DisplayName("Item type is registered")
        void itemTypeIsRegistered() {
            List<ItemID> types = librarian.types().toList();

            assertThat(types)
                    .as("Types should include Item")
                    .contains(ItemID.fromString(Item.KEY));
        }

        @Test
        @DisplayName("can get Item type seed")
        void canGetItemTypeSeed() {
            var itemType = librarian.get(ItemID.fromString(Item.KEY), Item.class);

            assertThat(itemType)
                    .as("Item type seed")
                    .isPresent();
        }
    }

    // ==================================================================================
    // Item Creation via Librarian
    // ==================================================================================

    @Nested
    @DisplayName("Item Creation")
    class ItemCreation {

        @Test
        @DisplayName("can create plain items")
        void canCreatePlainItems() {
            Item newItem = Item.create(librarian);

            assertThat(newItem)
                    .as("Created item")
                    .isNotNull();
            assertThat(newItem.iid())
                    .as("New item IID")
                    .isNotNull();
            assertThat(newItem.dirty())
                    .as("New item should be dirty")
                    .isTrue();
        }

        @Test
        @DisplayName("can create items via 'new' action on type")
        void canCreateItemsViaNewAction() {
            Item itemType = librarian.get(ItemID.fromString(Item.KEY), Item.class)
                    .orElseThrow(() -> new AssertionError("Item type not found"));

            ActionResult result = librarian.dispatch(itemType, "new", List.of());

            assertThat(result.success())
                    .as("'new' action should succeed")
                    .isTrue();

            Item created = (Item) result.value();
            assertThat(created)
                    .as("Created item")
                    .isNotNull();
            assertThat(created.iid())
                    .isNotEqualTo(itemType.iid());
        }
    }

    // ==================================================================================
    // Relations via Librarian
    // ==================================================================================

    @Nested
    @DisplayName("Relations via Librarian")
    class RelationsViaLibrarian {

        @Test
        @DisplayName("items can create relations")
        void itemsCanCreateRelations() {
            Item author = Item.create(librarian);
            Item book = Item.create(librarian);
            ItemID wroteId = ItemID.fromString("cg.predicate:wrote");

            Relation relation = author.relate(wroteId, book);

            assertThat(relation)
                    .as("Created relation")
                    .isNotNull();
            assertThat(relation.subject())
                    .isEqualTo(author.iid());
            assertThat(relation.predicate())
                    .isEqualTo(wroteId);
        }

        @Test
        @DisplayName("relations are queryable from subject")
        void relationsQueryableFromSubject() {
            Item author = Item.create(librarian);
            Item book = Item.create(librarian);
            ItemID wroteId = ItemID.fromString("cg.predicate:wrote");

            author.relate(wroteId, book);

            List<Relation> relations = author.relations().toList();

            assertThat(relations)
                    .as("Relations from author")
                    .hasSize(1);
        }

        @Test
        @DisplayName("relations are queryable to object")
        void relationsQueryableToObject() {
            Item author = Item.create(librarian);
            Item book = Item.create(librarian);
            ItemID wroteId = ItemID.fromString("cg.predicate:wrote");

            author.relate(wroteId, book);

            List<Relation> relations = book.relations().toList();

            assertThat(relations)
                    .as("Relations to book")
                    .hasSize(1);
        }

        @Test
        @DisplayName("multiple relations can be created")
        void multipleRelationsCanBeCreated() {
            Item author = Item.create(librarian);
            Item book1 = Item.create(librarian);
            Item book2 = Item.create(librarian);
            ItemID wroteId = ItemID.fromString("cg.predicate:wrote");

            author.relate(wroteId, book1);
            author.relate(wroteId, book2);

            List<Relation> relations = author.relations(wroteId).toList();

            assertThat(relations)
                    .as("Relations with 'wrote' predicate")
                    .hasSize(2);
        }
    }

    // ==================================================================================
    // Version Management
    // ==================================================================================

    @Nested
    @DisplayName("Version Management")
    class VersionManagement {

        @Test
        @DisplayName("has base version after first boot")
        void hasBaseVersionAfterFirstBoot() {
            // Librarian commits on first boot
            assertThat(librarian.base())
                    .as("Base version")
                    .isNotNull();
        }

        @Test
        @DisplayName("is not dirty after first boot commit")
        void notDirtyAfterFirstBootCommit() {
            assertThat(librarian.dirty())
                    .as("Should not be dirty after first boot")
                    .isFalse();
        }

        @Test
        @DisplayName("items can be committed")
        void itemsCanBeCommitted() {
            Item newItem = Item.create(librarian);

            assertThat(newItem.base()).isNull();
            assertThat(newItem.dirty()).isTrue();

            var vid = newItem.commit(librarian);

            assertThat(vid)
                    .as("Commit returns VID")
                    .isNotNull();
            assertThat(newItem.base())
                    .as("Base is set after commit")
                    .isEqualTo(vid);
            assertThat(newItem.dirty())
                    .as("Not dirty after commit")
                    .isFalse();
        }
    }
}
