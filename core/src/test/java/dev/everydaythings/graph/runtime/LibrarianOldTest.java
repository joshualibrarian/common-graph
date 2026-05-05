package dev.everydaythings.graph.runtime;

import dev.everydaythings.graph.item.ItemOld;
import dev.everydaythings.graph.frame.FrameBodyOld;
import dev.everydaythings.graph.item.ItemOldTest;
import dev.everydaythings.graph.item.id.ItemID;
import dev.everydaythings.graph.item.user.SignerOldTest;
import dev.everydaythings.graph.language.ThematicRole;
import dev.everydaythings.graph.library.LibraryOld;
import org.junit.jupiter.api.*;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for Librarian - the root Item that hosts a graph.
 *
 * <p>Inherits:
 * <ul>
 *   <li>All universal Item tests from {@link ItemOldTest}</li>
 *   <li>All Signer tests from {@link SignerOldTest}</li>
 * </ul>
 *
 * <p>Plus Librarian-specific functionality tests.
 */
@DisplayName("Librarian") @Disabled
class LibrarianOldTest extends SignerOldTest {

    // Typed reference to the librarian (same instance as 'item' and signer())
    private LibrarianOld librarian;

    @Override
    protected ItemOld createItem(Path tempDir) {
        librarian = LibrarianOld.open(tempDir);
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
            librarian = LibrarianOld.open(rootPath);

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
            librarian = LibrarianOld.open(rootPath);

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
    class LibraryOldComponent {

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
                    .isInstanceOf(LibraryOld.class);
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
                    dev.everydaythings.graph.language.CoreVocabulary.ImplementedBy.IID).toList();
            assertThat(results)
                    .as("Library should have indexed implementedBy relations")
                    .isNotEmpty();
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
                    .contains(ItemID.fromString(ItemOld.KEY));
        }

        @Test
        @DisplayName("can get Item type seed")
        void canGetItemTypeSeed() {
            var itemType = librarian.get(ItemID.fromString(ItemOld.KEY), ItemOld.class);

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
    class ItemOldCreation {

        @Test
        @DisplayName("can create plain items")
        void canCreatePlainItems() {
            ItemOld newItem = ItemOld.create(librarian);

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

    }

    // ==================================================================================
    // Relations via Librarian
    // ==================================================================================

    @Nested
    @DisplayName("Relations via Librarian")
    class RelationsViaLibrarian {

        @Test
        @DisplayName("items can create frames via builder")
        void itemsCanCreateFrames() {
            ItemOld author = ItemOld.create(librarian);
            ItemOld book = ItemOld.create(librarian);
            ItemID wroteId = ItemID.fromString("cg.predicate:wrote");

            FrameBodyOld body = FrameBodyOld.builder(wroteId)
                    .bind(ThematicRole.Theme.IID, author.iid())
                    .bind(ThematicRole.Goal.IID, book.iid())
                    .build();
            librarian.storeFrame(body);

            assertThat(body)
                    .as("Created frame body")
                    .isNotNull();
            assertThat(body.homeId())
                    .isEqualTo(author.iid());
            assertThat(body.predicate())
                    .isEqualTo(wroteId);
        }

        @Test
        @DisplayName("frames are queryable from subject")
        void framesQueryableFromSubject() {
            ItemOld author = ItemOld.create(librarian);
            ItemOld book = ItemOld.create(librarian);
            ItemID wroteId = ItemID.fromString("cg.predicate:wrote");

            librarian.storeFrame(FrameBodyOld.builder(wroteId)
                    .bind(ThematicRole.Theme.IID, author.iid())
                    .bind(ThematicRole.Goal.IID, book.iid())
                    .build());

            List<FrameBodyOld> relations = author.relations().toList();

            assertThat(relations)
                    .as("Relations from author")
                    .hasSize(1);
        }

        @Test
        @DisplayName("frames are queryable to object")
        void framesQueryableToObject() {
            ItemOld author = ItemOld.create(librarian);
            ItemOld book = ItemOld.create(librarian);
            ItemID wroteId = ItemID.fromString("cg.predicate:wrote");

            librarian.storeFrame(FrameBodyOld.builder(wroteId)
                    .bind(ThematicRole.Theme.IID, author.iid())
                    .bind(ThematicRole.Goal.IID, book.iid())
                    .build());

            List<FrameBodyOld> relations = book.relations().toList();

            assertThat(relations)
                    .as("Relations to book")
                    .hasSize(1);
        }

        @Test
        @DisplayName("multiple frames can be created")
        void multipleFramesCanBeCreated() {
            ItemOld author = ItemOld.create(librarian);
            ItemOld book1 = ItemOld.create(librarian);
            ItemOld book2 = ItemOld.create(librarian);
            ItemID wroteId = ItemID.fromString("cg.predicate:wrote");

            librarian.storeFrame(FrameBodyOld.builder(wroteId)
                    .bind(ThematicRole.Theme.IID, author.iid())
                    .bind(ThematicRole.Goal.IID, book1.iid())
                    .build());
            librarian.storeFrame(FrameBodyOld.builder(wroteId)
                    .bind(ThematicRole.Theme.IID, author.iid())
                    .bind(ThematicRole.Goal.IID, book2.iid())
                    .build());

            List<FrameBodyOld> relations = author.relations(wroteId).toList();

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
            ItemOld newItem = ItemOld.create(librarian);

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
