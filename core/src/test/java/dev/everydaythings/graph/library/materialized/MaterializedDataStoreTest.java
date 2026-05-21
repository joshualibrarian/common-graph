package dev.everydaythings.graph.library.materialized;

import dev.everydaythings.graph.datum.Binding;
import dev.everydaythings.graph.datum.Body;
import dev.everydaythings.graph.datum.Datum;
import dev.everydaythings.graph.encoding.CgCbor;
import dev.everydaythings.graph.encoding.Encoding;
import dev.everydaythings.graph.encoding.EncodingRegistry;
import dev.everydaythings.graph.language.ThematicRole;
import dev.everydaythings.graph.ref.ContentRef;
import dev.everydaythings.graph.ref.DatumRef;
import dev.everydaythings.graph.ref.ItemRef;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("MaterializedDataStore — DataStore backed by .item/ directory")
class MaterializedDataStoreTest {

    private final Encoding encoding = CgCbor.codec();
    private final EncodingRegistry registry = EncodingRegistry.defaultRegistry();
    private final ItemRef itemIid   = ItemRef.iid("cg.test:materialized-item");
    private final ItemRef codecRef  = ItemRef.iid(Encoding.CgCborV1.KEY);

    private Body simpleBody() {
        return Body.of(
                itemIid,
                List.of(Binding.literal(
                        ItemRef.iid(ThematicRole.Value.KEY),
                        "hello-materialized")));
    }

    @Nested
    @DisplayName("Mint")
    class Mint {

        @Test
        @DisplayName("Creates .item/iid, .item/codec, empty .item/objects/")
        void mintCreatesExpectedFiles(@TempDir Path tmp) throws IOException {
            MaterializedDataStore store = MaterializedDataStore.mint(tmp, itemIid, encoding);

            assertThat(Files.isDirectory(tmp.resolve(".item"))).isTrue();
            assertThat(Files.isRegularFile(tmp.resolve(".item/iid"))).isTrue();
            assertThat(Files.isRegularFile(tmp.resolve(".item/codec"))).isTrue();
            assertThat(Files.isDirectory(tmp.resolve(".item/objects"))).isTrue();
            assertThat(Files.exists(tmp.resolve(".item/head"))).isFalse();

            assertThat(store.iid()).isEqualTo(itemIid);
            assertThat(store.codecRef()).isEqualTo(codecRef);
            assertThat(store.head()).isEmpty();
        }

        @Test
        @DisplayName("Refuses to clobber an existing .item/")
        void mintRefusesToClobber(@TempDir Path tmp) throws IOException {
            MaterializedDataStore.mint(tmp, itemIid, encoding);
            assertThatThrownBy(() ->
                    MaterializedDataStore.mint(tmp, itemIid, encoding))
                    .isInstanceOf(IOException.class)
                    .hasMessageContaining(".item/");
        }
    }

    @Nested
    @DisplayName("Open")
    class Open {

        @Test
        @DisplayName("Reads iid / codec / head from existing .item/")
        void opensWithMetadata(@TempDir Path tmp) throws IOException {
            MaterializedDataStore created = MaterializedDataStore.mint(tmp, itemIid, encoding);
            ContentRef someCid = ContentRef.of(new byte[]{1, 2, 3});
            created.setHead(someCid);

            MaterializedDataStore opened = MaterializedDataStore.open(tmp, registry);
            assertThat(opened.iid()).isEqualTo(itemIid);
            assertThat(opened.codecRef()).isEqualTo(codecRef);
            assertThat(opened.head()).contains(someCid);
        }

        @Test
        @DisplayName("Fails cleanly on missing .item/")
        void failsOnMissingMetaDir(@TempDir Path tmp) {
            assertThatThrownBy(() -> MaterializedDataStore.open(tmp, registry))
                    .isInstanceOf(IOException.class)
                    .hasMessageContaining(".item/");
        }
    }

    @Nested
    @DisplayName("Datum API round-trip")
    class DatumApi {

        @Test
        @DisplayName("put/get round-trip preserves the Datum")
        void putGetRoundTrip(@TempDir Path tmp) throws IOException {
            MaterializedDataStore store = MaterializedDataStore.mint(tmp, itemIid, encoding);
            Body original = simpleBody();
            DatumRef stored = store.put(original);

            Optional<Datum> retrieved = store.get(stored);
            assertThat(retrieved).isPresent();
            assertThat(retrieved.get().datumId()).isEqualTo(stored);
        }

        @Test
        @DisplayName("has reports stored Datum")
        void hasReportsStored(@TempDir Path tmp) throws IOException {
            MaterializedDataStore store = MaterializedDataStore.mint(tmp, itemIid, encoding);
            DatumRef stored = store.put(simpleBody());
            assertThat(store.has(stored)).isTrue();
        }

        @Test
        @DisplayName("Unknown Datum returns empty")
        void unknownReturnsEmpty(@TempDir Path tmp) throws IOException {
            MaterializedDataStore store = MaterializedDataStore.mint(tmp, itemIid, encoding);
            DatumRef bogus = simpleBody().datumId();
            assertThat(store.get(bogus)).isEmpty();
            assertThat(store.has(bogus)).isFalse();
        }

        @Test
        @DisplayName("delete removes the Datum")
        void deleteRemoves(@TempDir Path tmp) throws IOException {
            MaterializedDataStore store = MaterializedDataStore.mint(tmp, itemIid, encoding);
            DatumRef stored = store.put(simpleBody());
            assertThat(store.delete(stored)).isTrue();
            assertThat(store.has(stored)).isFalse();
            assertThat(store.delete(stored)).isFalse();   // second delete is no-op
        }

        @Test
        @DisplayName("Datum survives close + reopen via lazy index rebuild")
        void datumSurvivesReopen(@TempDir Path tmp) throws IOException {
            MaterializedDataStore created = MaterializedDataStore.mint(tmp, itemIid, encoding);
            Body original = simpleBody();
            DatumRef stored = created.put(original);
            created.close();

            MaterializedDataStore reopened = MaterializedDataStore.open(tmp, registry);
            assertThat(reopened.has(stored)).isTrue();
            Optional<Datum> retrieved = reopened.get(stored);
            assertThat(retrieved).isPresent();
            assertThat(retrieved.get().datumId()).isEqualTo(stored);
        }
    }

    @Nested
    @DisplayName("Content-blob API round-trip")
    class ContentApi {

        @Test
        @DisplayName("putContent/getContent round-trip preserves bytes")
        void putGetContentRoundTrip(@TempDir Path tmp) throws IOException {
            MaterializedDataStore store = MaterializedDataStore.mint(tmp, itemIid, encoding);
            byte[] payload = "raw blob bytes".getBytes();
            ContentRef cid = store.putContent(payload);

            assertThat(store.hasContent(cid)).isTrue();
            Optional<byte[]> retrieved = store.getContent(cid);
            assertThat(retrieved).isPresent();
            assertThat(retrieved.get()).isEqualTo(payload);
        }

        @Test
        @DisplayName("deleteContent removes the blob")
        void deleteContentRemoves(@TempDir Path tmp) throws IOException {
            MaterializedDataStore store = MaterializedDataStore.mint(tmp, itemIid, encoding);
            ContentRef cid = store.putContent("blob".getBytes());
            assertThat(store.deleteContent(cid)).isTrue();
            assertThat(store.hasContent(cid)).isFalse();
            assertThat(store.deleteContent(cid)).isFalse();
        }
    }

    @Nested
    @DisplayName("Head")
    class Head {

        @Test
        @DisplayName("setHead persists across reopen")
        void setHeadPersists(@TempDir Path tmp) throws IOException {
            MaterializedDataStore created = MaterializedDataStore.mint(tmp, itemIid, encoding);
            ContentRef cid = ContentRef.of("manifest-bytes".getBytes());
            created.setHead(cid);

            assertThat(created.head()).contains(cid);
            assertThat(MaterializedDataStore.open(tmp, registry).head()).contains(cid);
        }

        @Test
        @DisplayName("setHead replaces the previous head atomically")
        void setHeadReplaces(@TempDir Path tmp) throws IOException {
            MaterializedDataStore store = MaterializedDataStore.mint(tmp, itemIid, encoding);
            ContentRef first  = ContentRef.of("first".getBytes());
            ContentRef second = ContentRef.of("second".getBytes());
            store.setHead(first);
            store.setHead(second);
            assertThat(store.head()).contains(second);

            assertThat(MaterializedDataStore.open(tmp, registry).head()).contains(second);
        }
    }
}
