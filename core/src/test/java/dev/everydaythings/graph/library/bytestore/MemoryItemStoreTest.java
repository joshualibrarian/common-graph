package dev.everydaythings.graph.library.bytestore;

import dev.everydaythings.graph.item.Manifest;
import dev.everydaythings.graph.item.id.ItemID;
import dev.everydaythings.graph.item.id.VersionID;
import dev.everydaythings.graph.library.skiplist.SkipListItemStore;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

@DisplayName("MemoryItemStore")
class MemoryItemStoreTest {

    @Test
    @DisplayName("store and retrieve manifest")
    void storeAndRetrieveManifest() {
        try (var store = SkipListItemStore.create()) {
            ItemID iid = ItemID.fromString("test:item");
            Manifest m = Manifest.builder().iid(iid).build();

            // Store
            VersionID vid = store.manifest(m);
            assertThat(vid).isNotNull();

            // Retrieve
            var retrieved = store.manifest(iid, vid);
            assertThat(retrieved).isPresent();
            assertThat(retrieved.get().iid()).isEqualTo(iid);

            // hasManifest
            assertThat(store.hasManifest(iid, vid)).isTrue();
            assertThat(store.hasManifest(iid, new VersionID(new byte[34]))).isFalse();
        }
    }

    @Test
    @DisplayName("fluent API works")
    void fluentApiWorks() {
        try (var store = SkipListItemStore.create()) {
            // Use fluent API with proper ID type
            ItemID contentId = ItemID.fromString("test:content");
            byte[] value = "hello".getBytes();

            store.db(dev.everydaythings.graph.library.ItemStore.Column.PAYLOAD).key(contentId).put(value);

            byte[] retrieved = store.db(dev.everydaythings.graph.library.ItemStore.Column.PAYLOAD).key(contentId).get();
            assertThat(retrieved).isEqualTo(value);
        }
    }
}
