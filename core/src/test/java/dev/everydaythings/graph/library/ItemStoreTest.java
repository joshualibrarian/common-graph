package dev.everydaythings.graph.library;

import dev.everydaythings.graph.Canonical;
import dev.everydaythings.graph.item.Literal;
import dev.everydaythings.graph.item.Manifest;
import dev.everydaythings.graph.item.component.BindingTarget;
import dev.everydaythings.graph.item.component.FrameBody;
import dev.everydaythings.graph.item.id.*;
import dev.everydaythings.graph.language.ThematicRole;
import dev.everydaythings.graph.language.NounSememe;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Abstract base test for ItemStore implementations.
 *
 * <p>These tests verify the fundamental contracts that ALL ItemStore implementations
 * must satisfy. Subclasses provide the specific ItemStore implementation to test.
 *
 * <p>Usage:
 * <pre>{@code
 * class MapDBItemStoreTest extends ItemStoreTest {
 *     @Override
 *     protected ItemStore createStore(Path tempDir) {
 *         return MapDBItemStore.memory();
 *     }
 * }
 * }</pre>
 */
@Disabled("Store tests — refactoring later")
public abstract class ItemStoreTest {

    @TempDir
    protected Path tempDir;

    protected ItemStore store;

    // ==================================================================================
    // Template Methods - Subclasses Override
    // ==================================================================================

    /**
     * Create the ItemStore to test.
     *
     * <p>Called before each test. The returned store should be ready for testing.
     *
     * @param tempDir A temporary directory for file-based stores
     */
    protected abstract ItemStore createStore(Path tempDir);

    @BeforeEach
    void setUpStore() {
        store = createStore(tempDir);
    }

    @AfterEach
    void tearDownStore() {
        if (store != null) {
            store.close();
            store = null;
        }
    }

    // ==================================================================================
    // Test Helpers
    // ==================================================================================

    /**
     * Create a test ItemID with a predictable value.
     */
    protected ItemID testItemID(String seed) {
        return ItemID.fromString("cg:test/" + seed);
    }

    /**
     * A test type ID for manifests.
     */
    protected static final ItemID TEST_TYPE = ItemID.fromString("cg:test/type");

    /**
     * Another test type ID (for different versions).
     */
    protected static final ItemID TEST_TYPE_2 = ItemID.fromString("cg:test/type2");

    /**
     * Create a simple test manifest.
     */
    protected Manifest testManifest(ItemID iid) {
        return Manifest.builder()
                .iid(iid)
                .type(TEST_TYPE)
                .build();
    }

    /**
     * Create a simple test frame body.
     */
    protected FrameBody testFrameBody(ItemID subject, ItemID predicate, String literalValue) {
        return FrameBody.of(predicate, subject,
                java.util.Map.of(ThematicRole.Target.SEED.iid(), Literal.ofText(literalValue)));
    }

    /**
     * Create a test frame body with an ItemID target.
     */
    protected FrameBody testFrameBody(ItemID subject, ItemID predicate, ItemID object) {
        return FrameBody.of(predicate, subject,
                java.util.Map.of(ThematicRole.Target.SEED.iid(), BindingTarget.iid(object)));
    }

    // ==================================================================================
    // Manifest Tests
    // ==================================================================================

    @Nested
    @DisplayName("Manifests")
    class Manifests {

        @Test
        @DisplayName("persist and retrieve manifest")
        void persistAndRetrieveManifest() {
            ItemID iid = testItemID("manifest-test");
            Manifest manifest = testManifest(iid);
            byte[] record = manifest.encodeBinary(Canonical.Scope.RECORD);

            // Persist
            ContentID vid = store.manifest(manifest);

            assertThat(vid)
                    .as("ContentID from persist")
                    .isNotNull();

            // Retrieve
            var retrieved = store.manifest(iid, vid);

            assertThat(retrieved)
                    .as("Retrieved manifest")
                    .isPresent();

            assertThat(retrieved.get().iid())
                    .as("Manifest IID")
                    .isEqualTo(iid);
        }

        @Test
        @DisplayName("retrieve non-existent manifest returns empty")
        void retrieveNonExistentManifestReturnsEmpty() {
            ItemID iid = testItemID("nonexistent");
            ContentID vid = new ContentID(new byte[32], dev.everydaythings.graph.Hash.DEFAULT);

            var retrieved = store.manifest(iid, vid);

            assertThat(retrieved)
                    .as("Non-existent manifest")
                    .isEmpty();
        }

        @Test
        @DisplayName("persist multiple versions of same item")
        void persistMultipleVersions() {
            ItemID iid = testItemID("multi-version");

            // Create and persist two different manifests for the same IID
            Manifest m1 = Manifest.builder()
                    .iid(iid)
                    .type(TEST_TYPE)
                    .build();

            Manifest m2 = Manifest.builder()
                    .iid(iid)
                    .type(TEST_TYPE_2)  // Different type
                    .build();

            ContentID vid1 = store.manifest(m1);
            ContentID vid2 = store.manifest(m2);

            // VIDs should be different (different content)
            assertThat(vid1)
                    .as("First version")
                    .isNotEqualTo(vid2);

            // Both should be retrievable
            assertThat(store.manifest(iid, vid1)).isPresent();
            assertThat(store.manifest(iid, vid2)).isPresent();
        }

        @Test
        @DisplayName("iterate manifests for specific item")
        void iterateManifestsForItem() {
            ItemID iid = testItemID("iterate-test");

            // Store two versions
            Manifest m1 = Manifest.builder().iid(iid).type(TEST_TYPE).build();
            Manifest m2 = Manifest.builder().iid(iid).type(TEST_TYPE_2).build();

            store.manifest(m1);
            store.manifest(m2);

            // Iterate
            var manifests = store.manifests(iid).toList();

            assertThat(manifests)
                    .as("Manifests for item")
                    .hasSize(2);
        }

        @Test
        @DisplayName("iterate all manifests")
        void iterateAllManifests() {
            // Store manifests for different items
            store.manifest(testManifest(testItemID("item1")));
            store.manifest(testManifest(testItemID("item2")));
            store.manifest(testManifest(testItemID("item3")));

            // Iterate all (null filter)
            var manifests = store.manifests(null).toList();

            assertThat(manifests)
                    .as("All manifests")
                    .hasSizeGreaterThanOrEqualTo(3);
        }
    }

    // ==================================================================================
    // Frame Body Tests
    // ==================================================================================

    @Nested
    @DisplayName("Frame Bodies")
    class FrameBodies {

        @Test
        @DisplayName("persist and retrieve frame body")
        void persistAndRetrieveFrameBody() {
            ItemID subject = testItemID("subject");
            FrameBody body = testFrameBody(subject, NounSememe.Title.SEED.iid(), "Test Title");

            // Persist
            ContentID cid = store.relation(body);

            assertThat(cid)
                    .as("ContentID from persist")
                    .isNotNull();

            // Retrieve
            var retrieved = store.relation(cid);

            assertThat(retrieved)
                    .as("Retrieved frame body")
                    .isPresent();

            assertThat(retrieved.get().theme())
                    .as("Frame body theme")
                    .isEqualTo(subject);
        }

        @Test
        @DisplayName("retrieve non-existent frame body returns empty")
        void retrieveNonExistentFrameBodyReturnsEmpty() {
            ContentID cid = new ContentID(new byte[32], dev.everydaythings.graph.Hash.DEFAULT);

            var retrieved = store.relation(cid);

            assertThat(retrieved)
                    .as("Non-existent frame body")
                    .isEmpty();
        }

        @Test
        @DisplayName("persist multiple frame bodies for same theme")
        void persistMultipleFrameBodiesForTheme() {
            ItemID subject = testItemID("multi-relation");

            FrameBody b1 = testFrameBody(subject, NounSememe.Title.SEED.iid(), "Title");
            FrameBody b2 = testFrameBody(subject, NounSememe.Description.SEED.iid(), "Description");

            ContentID cid1 = store.relation(b1);
            ContentID cid2 = store.relation(b2);

            // CIDs should be different
            assertThat(cid1)
                    .as("First frame body")
                    .isNotEqualTo(cid2);

            // Both should be retrievable
            assertThat(store.relation(cid1)).isPresent();
            assertThat(store.relation(cid2)).isPresent();
        }

        @Test
        @DisplayName("iterate frame bodies for specific theme")
        void iterateFrameBodiesForTheme() {
            ItemID subject = testItemID("iterate-rels");

            store.relation(testFrameBody(subject, NounSememe.Title.SEED.iid(), "Title"));
            store.relation(testFrameBody(subject, NounSememe.Description.SEED.iid(), "Desc"));

            var frameBodies = store.relations()
                    .filter(r -> subject.equals(r.theme()))
                    .toList();

            assertThat(frameBodies)
                    .as("Frame bodies for theme")
                    .hasSize(2);
        }

        @Test
        @DisplayName("iterate all frame bodies")
        void iterateAllFrameBodies() {
            // Store frame bodies for different themes
            store.relation(testFrameBody(testItemID("s1"), NounSememe.Title.SEED.iid(), "T1"));
            store.relation(testFrameBody(testItemID("s2"), NounSememe.Title.SEED.iid(), "T2"));
            store.relation(testFrameBody(testItemID("s3"), NounSememe.Title.SEED.iid(), "T3"));

            var frameBodies = store.relations().toList();

            assertThat(frameBodies)
                    .as("All frame bodies")
                    .hasSizeGreaterThanOrEqualTo(3);
        }
    }

    // ==================================================================================
    // Content Tests
    // ==================================================================================

    @Nested
    @DisplayName("Content")
    class Content {

        @Test
        @DisplayName("persist and retrieve content")
        void persistAndRetrieveContent() {
            byte[] data = "Hello, World!".getBytes(StandardCharsets.UTF_8);

            // Persist
            ContentID cid = store.content(data);

            assertThat(cid)
                    .as("ContentID from persist")
                    .isNotNull();

            // Retrieve
            var retrieved = store.content(cid);

            assertThat(retrieved)
                    .as("Retrieved content")
                    .isPresent()
                    .hasValue(data);
        }

        @Test
        @DisplayName("retrieve non-existent content returns empty")
        void retrieveNonExistentContentReturnsEmpty() {
            ContentID cid = new ContentID(new byte[32], dev.everydaythings.graph.Hash.DEFAULT);

            var retrieved = store.content(cid);

            assertThat(retrieved)
                    .as("Non-existent content")
                    .isEmpty();
        }

        @Test
        @DisplayName("content is deduplicated by hash")
        void contentIsDeduplicated() {
            byte[] data = "Same content".getBytes(StandardCharsets.UTF_8);

            ContentID cid1 = store.content(data);
            ContentID cid2 = store.content(data);

            assertThat(cid1)
                    .as("Same content produces same CID")
                    .isEqualTo(cid2);
        }

        @Test
        @DisplayName("different content produces different CIDs")
        void differentContentProducesDifferentCIDs() {
            byte[] data1 = "Content 1".getBytes(StandardCharsets.UTF_8);
            byte[] data2 = "Content 2".getBytes(StandardCharsets.UTF_8);

            ContentID cid1 = store.content(data1);
            ContentID cid2 = store.content(data2);

            assertThat(cid1)
                    .as("Different content produces different CIDs")
                    .isNotEqualTo(cid2);
        }

        @Test
        @DisplayName("iterate all content")
        void iterateAllContent() {
            store.content("Content 1".getBytes(StandardCharsets.UTF_8));
            store.content("Content 2".getBytes(StandardCharsets.UTF_8));
            store.content("Content 3".getBytes(StandardCharsets.UTF_8));

            var contents = store.contents().toList();

            assertThat(contents)
                    .as("All content")
                    .hasSizeGreaterThanOrEqualTo(3);
        }
    }

    // ==================================================================================
    // Transaction Tests
    // ==================================================================================

    @Nested
    @DisplayName("Transactions")
    class Transactions {

        @Test
        @DisplayName("transaction commit persists data")
        void transactionCommitPersistsData() {
            ItemID iid = testItemID("tx-commit");
            Manifest manifest = testManifest(iid);
            byte[] record = manifest.encodeBinary(Canonical.Scope.RECORD);

            ContentID[] vidHolder = new ContentID[1];

            store.runInWriteTransaction(tx -> {
                vidHolder[0] = store.persistManifest(iid, record, tx);
            });

            // Should be retrievable after transaction
            var retrieved = store.manifest(iid, vidHolder[0]);
            assertThat(retrieved).isPresent();
        }

        @Test
        @DisplayName("transaction rollback discards data")
        void transactionRollbackDiscardsData() {
            ItemID iid = testItemID("tx-rollback");
            Manifest manifest = testManifest(iid);
            byte[] record = manifest.encodeBinary(Canonical.Scope.RECORD);

            ContentID[] vidHolder = new ContentID[1];

            try (WriteTransaction tx = store.beginWriteTransaction()) {
                vidHolder[0] = store.persistManifest(iid, record, tx);
                tx.rollback();  // Explicit rollback, no commit
            }

            // Should NOT be retrievable after rollback
            var retrieved = store.manifest(iid, vidHolder[0]);
            assertThat(retrieved)
                    .as("Data should not exist after rollback")
                    .isEmpty();
        }

        @Test
        @DisplayName("transaction auto-rollback on close without commit")
        void transactionAutoRollbackOnClose() {
            ItemID iid = testItemID("tx-autorollback");
            Manifest manifest = testManifest(iid);
            byte[] record = manifest.encodeBinary(Canonical.Scope.RECORD);

            ContentID[] vidHolder = new ContentID[1];

            try (WriteTransaction tx = store.beginWriteTransaction()) {
                vidHolder[0] = store.persistManifest(iid, record, tx);
                // No commit - should auto-rollback on close
            }

            // Should NOT be retrievable
            var retrieved = store.manifest(iid, vidHolder[0]);
            assertThat(retrieved)
                    .as("Data should not exist after auto-rollback")
                    .isEmpty();
        }

        @Test
        @DisplayName("multiple operations in single transaction")
        void multipleOperationsInTransaction() {
            ItemID iid = testItemID("tx-multi");

            store.runInWriteTransaction(tx -> {
                // Multiple manifests
                Manifest m1 = Manifest.builder().iid(iid).type(TEST_TYPE).build();
                store.persistManifest(iid, m1.encodeBinary(Canonical.Scope.RECORD), tx);

                // Frame body
                FrameBody b1 = testFrameBody(iid, NounSememe.Title.SEED.iid(), "Title");
                store.persistContent(b1.encodeBinary(Canonical.Scope.RECORD), tx);

                // Content
                store.persistContent("Transaction content".getBytes(StandardCharsets.UTF_8), tx);
            });

            // All should be retrievable
            assertThat(store.manifests(iid).count())
                    .as("Manifests in transaction")
                    .isGreaterThanOrEqualTo(1);

            assertThat(store.relations()
                    .filter(r -> iid.equals(r.theme()))
                    .count())
                    .as("Frame bodies in transaction")
                    .isGreaterThanOrEqualTo(1);
        }
    }

    // ==================================================================================
    // Lifecycle Tests
    // ==================================================================================

    @Nested
    @DisplayName("Lifecycle")
    class Lifecycle {

        @Test
        @DisplayName("store is writable by default")
        void storeIsWritable() {
            assertThat(store.isWritable())
                    .as("Store writability")
                    .isTrue();
        }

        @Test
        @DisplayName("close is idempotent")
        void closeIsIdempotent() {
            // Should not throw on multiple closes
            store.close();
            store.close();
        }
    }
}
