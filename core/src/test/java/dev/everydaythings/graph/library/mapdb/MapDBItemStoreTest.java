package dev.everydaythings.graph.library.mapdb;

import dev.everydaythings.graph.library.ItemStore;
import dev.everydaythings.graph.library.ItemStoreTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for MapDBItemStore.
 *
 * <p>Inherits all standard ItemStore contract tests from {@link ItemStoreTest}.
 * Adds MapDB-specific tests for in-memory and file-backed modes.
 */
@DisplayName("MapDBItemStore")
class MapDBItemStoreTest extends ItemStoreTest {

    @Override
    protected ItemStore createStore(Path tempDir) {
        // Use in-memory store for speed in tests
        return MapDBItemStore.memory();
    }

    // ==================================================================================
    // MapDB-Specific Tests
    // ==================================================================================

    @Nested
    @DisplayName("MapDB-Specific")
    class MapDBSpecific {

        @Test
        @DisplayName("in-memory store is open after creation")
        void inMemoryStoreIsOpen() {
            try (MapDBItemStore memStore = MapDBItemStore.memory()) {
                assertThat(memStore.isOpen())
                        .as("In-memory store should be open")
                        .isTrue();
            }
        }

        @Test
        @DisplayName("file-backed store persists data")
        void fileBackedStorePersistsData() {
            Path dbPath = tempDir.resolve("test-store.mapdb");

            // Create, write, and close
            byte[] testData = "Persistent data".getBytes();
            dev.everydaythings.graph.item.id.ContentID cid;

            try (MapDBItemStore fileStore = MapDBItemStore.file(dbPath)) {
                cid = fileStore.content(testData);
            }

            // Reopen and verify
            try (MapDBItemStore fileStore = MapDBItemStore.file(dbPath)) {
                var retrieved = fileStore.content(cid);
                assertThat(retrieved)
                        .as("Data should persist across reopen")
                        .isPresent()
                        .hasValue(testData);
            }
        }

        @Test
        @DisplayName("store is closed after close()")
        void storeIsClosedAfterClose() {
            MapDBItemStore memStore = MapDBItemStore.memory();
            memStore.close();

            assertThat(memStore.isOpen())
                    .as("Store should be closed")
                    .isFalse();
        }
    }
}
