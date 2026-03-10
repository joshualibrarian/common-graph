package dev.everydaythings.graph.item;

import dev.everydaythings.graph.item.action.ActionResult;
import dev.everydaythings.graph.item.id.ItemID;
import dev.everydaythings.graph.language.Sememe;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Abstract base test for universal Item behavior.
 *
 * <p>These tests verify the fundamental contracts that ALL Items must satisfy.
 * Subclasses provide the specific Item implementation to test.
 *
 * <p>Usage:
 * <pre>{@code
 * class LibrarianTest extends ItemTest {
 *     @Override
 *     protected Item createItem(Path tempDir) {
 *         return Librarian.open(tempDir.resolve("graph"));
 *     }
 * }
 * }</pre>
 */
public abstract class ItemTest {

    @TempDir
    protected Path tempDir;

    protected Item item;

    // ==================================================================================
    // Template Methods - Subclasses Override
    // ==================================================================================

    /**
     * Create the Item to test.
     *
     * <p>Called before each test. The returned item should be ready for testing.
     * If the item needs cleanup (like Librarian), handle it in {@link #closeItem()}.
     */
    protected abstract Item createItem(Path tempDir);

    /**
     * Close/cleanup the item after each test.
     *
     * <p>Override if your item needs cleanup. Default handles AutoCloseable items.
     */
    protected void closeItem() throws Exception {
        if (item instanceof AutoCloseable ac) {
            ac.close();
        }
    }

    @BeforeEach
    void setUpItem() {
        item = createItem(tempDir);
    }

    @AfterEach
    void tearDownItem() throws Exception {
        closeItem();
        item = null;
    }

    // ==================================================================================
    // Identity Tests
    // ==================================================================================

    @Nested
    @DisplayName("Identity")
    class Identity {

        @Test
        @DisplayName("has a non-null IID")
        void hasNonNullIid() {
            assertThat(item.iid())
                    .as("Item IID")
                    .isNotNull();
        }

        @Test
        @DisplayName("IID is stable across accesses")
        void iidIsStable() {
            ItemID first = item.iid();
            ItemID second = item.iid();

            assertThat(first)
                    .as("IID should be same instance")
                    .isSameAs(second);
        }

        @Test
        @DisplayName("IID has valid text encoding")
        void iidHasValidTextEncoding() {
            String encoded = item.iid().encodeText();

            assertThat(encoded)
                    .as("IID text encoding")
                    .isNotBlank()
                    .startsWith("iid:");
        }

        @Test
        @DisplayName("IID has valid binary encoding")
        void iidHasValidBinaryEncoding() {
            byte[] encoded = item.iid().encodeBinary();

            assertThat(encoded)
                    .as("IID binary encoding")
                    .isNotNull()
                    .hasSizeGreaterThan(0);
        }
    }

    // ==================================================================================
    // Lifecycle Tests
    // ==================================================================================

    @Nested
    @DisplayName("Lifecycle")
    class Lifecycle {

        @Test
        @DisplayName("item is created successfully")
        void itemIsCreated() {
            assertThat(item)
                    .as("Created item")
                    .isNotNull();
        }

        @Test
        @DisplayName("freshBoot flag is set appropriately")
        void freshBootFlagIsSet() {
            // freshBoot should be a boolean (true or false, not error)
            boolean fresh = item.freshBoot();
            assertThat(fresh).isIn(true, false);
        }
    }

    // ==================================================================================
    // Version State Tests
    // ==================================================================================

    @Nested
    @DisplayName("Version State")
    class VersionState {

        @Test
        @DisplayName("dirty flag is accessible")
        void dirtyFlagIsAccessible() {
            // Should not throw - just verify we can access it
            boolean dirty = item.dirty();
            assertThat(dirty).isIn(true, false);
        }

        @Test
        @DisplayName("base version is null or valid")
        void baseVersionIsNullOrValid() {
            var base = item.base();

            // base can be null (uncommitted) or a valid VersionID
            if (base != null) {
                assertThat(base.encodeBinary())
                        .as("Base version binary encoding")
                        .isNotEmpty();
            }
        }

        @Test
        @DisplayName("versions list is accessible")
        void versionsListIsAccessible() {
            var versions = item.versions();

            assertThat(versions)
                    .as("Versions list")
                    .isNotNull();
        }
    }

    // ==================================================================================
    // Verb Tests
    // ==================================================================================

    @Nested
    @DisplayName("Verbs")
    class Verbs {

        @Test
        @DisplayName("vocabulary exists")
        void vocabularyExists() {
            assertThat(item.vocabulary())
                    .as("Vocabulary")
                    .isNotNull();
        }

        @Test
        @DisplayName("vocabulary is populated")
        void vocabularyIsPopulated() {
            assertThat(item.vocabulary().size())
                    .as("Verb count")
                    .isGreaterThan(0);
        }

        @Test
        @DisplayName("has 'create' verb inherited from Item")
        void hasCreateVerb() {
            ItemID createSememe = ItemID.fromString(Sememe.CREATE);
            assertThat(item.vocabulary().lookup(createSememe))
                    .as("CREATE verb")
                    .isPresent();
        }

        @Test
        @DisplayName("verb lookup returns empty for unknown verb")
        void unknownVerbLookupReturnsEmpty() {
            ItemID unknownSememe = ItemID.fromString("cg.verb:nonexistent");
            assertThat(item.vocabulary().lookup(unknownSememe))
                    .as("Unknown verb lookup")
                    .isEmpty();
        }

        @Test
        @DisplayName("dispatch fails gracefully for unknown command")
        void dispatchFailsForUnknownCommand() {
            ActionResult result = item.dispatch("nonexistent_action_xyz", List.of());

            assertThat(result.success())
                    .as("Unknown command should fail")
                    .isFalse();

            assertThat(result.error())
                    .as("Should have error")
                    .isNotNull()
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    // ==================================================================================
    // Component Tests
    // ==================================================================================

    @Nested
    @DisplayName("Components")
    class Components {

        @Test
        @DisplayName("component table exists")
        void componentTableExists() {
            assertThat(item.content())
                    .as("Component table")
                    .isNotNull();
        }

        @Test
        @DisplayName("component lookup returns null for unknown handle")
        void unknownComponentReturnsNull() {
            Object comp = item.component("nonexistent_component_xyz");

            assertThat(comp)
                    .as("Unknown component")
                    .isNull();
        }
    }

    // ==================================================================================
    // Persistence Tests
    // ==================================================================================

    /**
     * Check if this item supports persist() (has a WorkingTreeStore).
     * Override in subclasses if you know the item doesn't have a store.
     */
    protected boolean supportsPersist() {
        // Default: assume items created with path-based constructor have a store
        return item.freshBoot() || item.base() != null;
    }

    @Nested
    @DisplayName("Persistence")
    class Persistence {

        @Test
        @DisplayName("persist() clears dirty flag")
        void persistClearsDirtyFlag() {
            assumeTrue(supportsPersist(), "Item does not support persist()");

            item.persist();

            assertThat(item.dirty())
                    .as("Dirty flag after persist")
                    .isFalse();
        }

        @Test
        @DisplayName("persist() is idempotent")
        void persistIsIdempotent() {
            assumeTrue(supportsPersist(), "Item does not support persist()");

            // Multiple persists should not throw
            item.persist();
            item.persist();

            assertThat(item.dirty()).isFalse();
        }

        @Test
        @DisplayName("persist() preserves component state")
        void persistPreservesComponentState() {
            assumeTrue(supportsPersist(), "Item does not support persist()");

            int componentCountBefore = item.content().size();

            item.persist();

            assertThat(item.content().size())
                    .as("Component count after persist")
                    .isEqualTo(componentCountBefore);
        }

        @Test
        @DisplayName("persist() preserves mount state")
        void persistPreservesMountState() {
            assumeTrue(supportsPersist(), "Item does not support persist()");

            long mountCountBefore = item.content().mounted().count();

            item.persist();

            assertThat(item.content().mounted().count())
                    .as("Mounted entry count after persist")
                    .isEqualTo(mountCountBefore);
        }
    }

    // ==================================================================================
    // Relation Tests
    // ==================================================================================

    @Nested
    @DisplayName("Relations")
    class Relations {

        @Test
        @DisplayName("relations() returns a stream")
        void relationsReturnsStream() {
            var relations = item.relations();

            assertThat(relations)
                    .as("Relations stream")
                    .isNotNull();

            // Consume the stream to verify it works
            var list = relations.toList();
            assertThat(list).isNotNull();
        }

        @Test
        @DisplayName("relations(predicate) returns a stream")
        void relationsWithPredicateReturnsStream() {
            ItemID predicate = ItemID.fromString("cg.predicate:test");
            var relations = item.relations(predicate);

            assertThat(relations)
                    .as("Relations with predicate stream")
                    .isNotNull();

            var list = relations.toList();
            assertThat(list).isNotNull();
        }
    }
}
