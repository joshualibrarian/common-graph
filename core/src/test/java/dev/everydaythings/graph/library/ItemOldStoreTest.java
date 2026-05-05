package dev.everydaythings.graph.library;

import dev.everydaythings.graph.Canonical;
import dev.everydaythings.graph.frame.Binding;
import dev.everydaythings.graph.item.ItemOld;
import dev.everydaythings.graph.item.Literal;
import dev.everydaythings.graph.item.ManifestOld;
import dev.everydaythings.graph.frame.BindingTarget;
import dev.everydaythings.graph.frame.FrameBodyOld;
import dev.everydaythings.graph.item.id.*;
import dev.everydaythings.graph.language.RuntimeVocabulary;
import dev.everydaythings.graph.language.ThematicRole;
import dev.everydaythings.graph.language.CoreVocabulary;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

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
public abstract class ItemOldStoreTest {

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
     * A test implementation binding for manifests.
     */
    protected static final Binding TEST_IMPL = ManifestOld.javaImplementation(ItemOld.class);

    /**
     * Another test implementation binding (for different versions).
     */
    protected static final Binding TEST_IMPL_2 = new Binding(
            RuntimeVocabulary.Java.IID, Literal.ofText("cg.test.Type2"));

    /**
     * Create a simple test manifest.
     */
    protected ManifestOld testManifest(ItemID iid) {
        return ManifestOld.builder()
                .iid(iid)
                .implementation(TEST_IMPL)
                .build();
    }

    /**
     * Create a simple test frame body.
     */
    protected FrameBodyOld testFrameBody(ItemID subject, ItemID predicate, String literalValue) {
        return FrameBodyOld.of(predicate, subject,
                java.util.Map.of(ThematicRole.Goal.IID, Literal.ofText(literalValue)));
    }

    /**
     * Create a test frame body with an ItemID target.
     */
    protected FrameBodyOld testFrameBody(ItemID subject, ItemID predicate, ItemID object) {
        return FrameBodyOld.of(predicate, subject,
                java.util.Map.of(ThematicRole.Goal.IID, BindingTarget.iid(object)));
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
            ManifestOld manifest = testManifest(iid);
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
            ManifestOld m1 = ManifestOld.builder()
                    .iid(iid)
                    .implementation(TEST_IMPL)
                    .build();

            ManifestOld m2 = ManifestOld.builder()
                    .iid(iid)
                    .implementation(TEST_IMPL_2)  // Different type
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
            ManifestOld m1 = ManifestOld.builder().iid(iid).implementation(TEST_IMPL).build();
            ManifestOld m2 = ManifestOld.builder().iid(iid).implementation(TEST_IMPL_2).build();

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
    class FrameOldBodies {

        @Test
        @DisplayName("persist and retrieve frame body")
        void persistAndRetrieveFrameBody() {
            ItemID subject = testItemID("subject");
            FrameBodyOld body = testFrameBody(subject, CoreVocabulary.Title.IID, "Test Title");

            // Persist
            ContentID cid = store.storeFrameBody(body);

            assertThat(cid)
                    .as("ContentID from persist")
                    .isNotNull();

            // Retrieve
            var retrieved = store.frameBody(cid);

            assertThat(retrieved)
                    .as("Retrieved frame body")
                    .isPresent();

            assertThat(retrieved.get().homeId())
                    .as("Frame body theme")
                    .isEqualTo(subject);
        }

        @Test
        @DisplayName("retrieve non-existent frame body returns empty")
        void retrieveNonExistentFrameBodyReturnsEmpty() {
            ContentID cid = new ContentID(new byte[32], dev.everydaythings.graph.Hash.DEFAULT);

            var retrieved = store.frameBody(cid);

            assertThat(retrieved)
                    .as("Non-existent frame body")
                    .isEmpty();
        }

        @Test
        @DisplayName("persist multiple frame bodies for same theme")
        void persistMultipleFrameBodiesForTheme() {
            ItemID subject = testItemID("multi-relation");

            FrameBodyOld b1 = testFrameBody(subject, CoreVocabulary.Title.IID, "Title");
            FrameBodyOld b2 = testFrameBody(subject, CoreVocabulary.Description.IID, "Description");

            ContentID cid1 = store.storeFrameBody(b1);
            ContentID cid2 = store.storeFrameBody(b2);

            // CIDs should be different
            assertThat(cid1)
                    .as("First frame body")
                    .isNotEqualTo(cid2);

            // Both should be retrievable
            assertThat(store.frameBody(cid1)).isPresent();
            assertThat(store.frameBody(cid2)).isPresent();
        }

        @Test
        @DisplayName("iterate frame bodies for specific theme")
        void iterateFrameBodiesForTheme() {
            ItemID subject = testItemID("iterate-rels");

            store.storeFrameBody(testFrameBody(subject, CoreVocabulary.Title.IID, "Title"));
            store.storeFrameBody(testFrameBody(subject, CoreVocabulary.Description.IID, "Desc"));

            var frameBodies = store.frameBodies()
                    .filter(r -> subject.equals(r.homeId()))
                    .toList();

            assertThat(frameBodies)
                    .as("Frame bodies for theme")
                    .hasSize(2);
        }

        @Test
        @DisplayName("iterate all frame bodies")
        void iterateAllFrameBodies() {
            // Store frame bodies for different themes
            store.storeFrameBody(testFrameBody(testItemID("s1"), CoreVocabulary.Title.IID, "T1"));
            store.storeFrameBody(testFrameBody(testItemID("s2"), CoreVocabulary.Title.IID, "T2"));
            store.storeFrameBody(testFrameBody(testItemID("s3"), CoreVocabulary.Title.IID, "T3"));

            var frameBodies = store.frameBodies().toList();

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
            ManifestOld manifest = testManifest(iid);
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
            ManifestOld manifest = testManifest(iid);
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
            ManifestOld manifest = testManifest(iid);
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
                ManifestOld m1 = ManifestOld.builder().iid(iid).implementation(TEST_IMPL).build();
                store.persistManifest(iid, m1.encodeBinary(Canonical.Scope.RECORD), tx);

                // Frame body
                FrameBodyOld b1 = testFrameBody(iid, CoreVocabulary.Title.IID, "Title");
                store.persistContent(b1.encodeBinary(Canonical.Scope.RECORD), tx);

                // Content
                store.persistContent("Transaction content".getBytes(StandardCharsets.UTF_8), tx);
            });

            // All should be retrievable
            assertThat(store.manifests(iid).count())
                    .as("Manifests in transaction")
                    .isGreaterThanOrEqualTo(1);

            assertThat(store.frameBodies()
                    .filter(r -> iid.equals(r.homeId()))
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
