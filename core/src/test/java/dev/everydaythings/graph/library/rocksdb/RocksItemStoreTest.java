package dev.everydaythings.graph.library.rocksdb;

import dev.everydaythings.graph.library.ItemStore;
import dev.everydaythings.graph.library.ItemStoreTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for RocksItemStore.
 *
 * <p>Inherits all standard ItemStore contract tests from {@link ItemStoreTest}.
 * Adds RocksDB-specific tests if needed.
 */
@DisplayName("RocksItemStore")
class RocksItemStoreTest extends ItemStoreTest {

    @Override
    protected ItemStore createStore(Path tempDir) {
        return RocksItemStore.open(tempDir.resolve("rocks-store"));
    }

    // ==================================================================================
    // RocksDB-Specific Tests
    // ==================================================================================

    @Nested
    @DisplayName("RocksDB-Specific")
    class RocksDBSpecific {

        @Test
        @DisplayName("store persists data across reopen")
        void storePersistsDataAcrossReopen() {
            Path dbPath = tempDir.resolve("persist-test");

            byte[] testData = "RocksDB persistent data".getBytes();
            dev.everydaythings.graph.item.id.ContentID cid;

            // Create, write, and close
            try (RocksItemStore rocksStore = RocksItemStore.open(dbPath)) {
                cid = rocksStore.content(testData);
            }

            // Reopen and verify
            try (RocksItemStore rocksStore = RocksItemStore.open(dbPath)) {
                var retrieved = rocksStore.content(cid);
                assertThat(retrieved)
                        .as("Data should persist across reopen")
                        .isPresent()
                        .hasValue(testData);
            }
        }

        @Test
        @DisplayName("hasManifest returns true for existing manifest")
        void hasManifestReturnsTrueForExisting() {
            Path dbPath = tempDir.resolve("has-manifest-test");

            try (RocksItemStore rocksStore = RocksItemStore.open(dbPath)) {
                var iid = testItemID("has-manifest");
                var manifest = testManifest(iid);
                var vid = rocksStore.manifest(manifest);

                assertThat(rocksStore.hasManifest(iid, vid))
                        .as("hasManifest should return true")
                        .isTrue();
            }
        }

        @Test
        @DisplayName("hasManifest returns false for non-existing manifest")
        void hasManifestReturnsFalseForNonExisting() {
            Path dbPath = tempDir.resolve("no-manifest-test");

            try (RocksItemStore rocksStore = RocksItemStore.open(dbPath)) {
                var iid = testItemID("nonexistent");
                var vid = new dev.everydaythings.graph.item.id.VersionID(
                        new byte[32], dev.everydaythings.graph.Hash.DEFAULT);

                assertThat(rocksStore.hasManifest(iid, vid))
                        .as("hasManifest should return false")
                        .isFalse();
            }
        }
    }
}
