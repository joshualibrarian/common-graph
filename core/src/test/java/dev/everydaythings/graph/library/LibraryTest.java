package dev.everydaythings.graph.library;

import dev.everydaythings.graph.frame.Binding;
import dev.everydaythings.graph.frame.Body;
import dev.everydaythings.graph.item.id.ContentID;
import dev.everydaythings.graph.item.id.ItemID;
import dev.everydaythings.graph.item.id.ItemRef;
import dev.everydaythings.graph.library.skiplist.SkipListDataStore;
import dev.everydaythings.graph.library.skiplist.SkipListIndexStore;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LibraryTest {

    private SkipListDataStore dataStore;
    private SkipListIndexStore indexStore;
    private Library library;

    @BeforeEach
    void setUp() {
        dataStore = SkipListDataStore.create();
        indexStore = SkipListIndexStore.create();
        library = new Library(dataStore, indexStore);
    }

    @AfterEach
    void tearDown() {
        dataStore.close();
        indexStore.close();
    }

    @Nested
    @DisplayName("Construction")
    class Construction {

        @Test
        @DisplayName("rejects null DataStore")
        void rejectsNullDataStore() {
            assertThatThrownBy(() -> new Library(null, indexStore))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("rejects null IndexStore")
        void rejectsNullIndexStore() {
            assertThatThrownBy(() -> new Library(dataStore, null))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("exposes both stores via accessors")
        void exposesStores() {
            assertThat(library.dataStore()).isSameAs(dataStore);
            assertThat(library.indexStore()).isSameAs(indexStore);
        }
    }

    @Nested
    @DisplayName("Datum persistence")
    class DatumPersistence {

        @Test
        @DisplayName("put returns the Datum's CID")
        void putReturnsCid() {
            Body body = Body.of(
                    ItemRef.of(ItemID.fromString("cg.predicate:authored")),
                    List.of(Binding.ref(
                            ItemID.fromString("cg.role:theme"),
                            ItemID.fromString("book")))
            );

            ContentID cid = library.put(body);
            assertThat(cid).isEqualTo(body.cid());
        }

        @Test
        @DisplayName("get retrieves bytes after put")
        void getRetrievesBytes() {
            Body body = Body.of(
                    ItemRef.of(ItemID.fromString("cg.predicate:authored")),
                    List.of()
            );

            ContentID cid = library.put(body);
            Optional<byte[]> retrieved = library.get(cid);
            assertThat(retrieved).isPresent();
        }

        @Test
        @DisplayName("get returns empty for unknown CID")
        void getEmptyForUnknown() {
            ContentID unknown = ContentID.of("never-stored".getBytes());
            assertThat(library.get(unknown)).isEmpty();
        }

        @Test
        @DisplayName("has returns true after put")
        void hasReturnsTrueAfterPut() {
            Body body = Body.of(
                    ItemRef.of(ItemID.fromString("cg.predicate:test")),
                    List.of()
            );
            ContentID cid = library.put(body);
            assertThat(library.has(cid)).isTrue();
        }

        @Test
        @DisplayName("has returns false for unknown CID")
        void hasFalseForUnknown() {
            ContentID unknown = ContentID.of("not-here".getBytes());
            assertThat(library.has(unknown)).isFalse();
        }

        @Test
        @DisplayName("idempotent: putting the same Datum twice yields the same CID")
        void idempotentPut() {
            Body body = Body.of(
                    ItemRef.of(ItemID.fromString("cg.predicate:test")),
                    List.of()
            );

            ContentID first = library.put(body);
            ContentID second = library.put(body);
            assertThat(second).isEqualTo(first);
        }
    }

    @Nested
    @DisplayName("Content blob persistence")
    class ContentPersistence {

        @Test
        @DisplayName("putContent stores arbitrary bytes")
        void putContent() {
            byte[] payload = "hello world".getBytes();
            ContentID cid = library.putContent(payload);

            assertThat(cid).isNotNull();
            assertThat(library.has(cid)).isTrue();

            Optional<byte[]> retrieved = library.get(cid);
            assertThat(retrieved).isPresent();
            assertThat(retrieved.get()).containsExactly(payload);
        }

        @Test
        @DisplayName("identical content produces identical CID")
        void identicalContentSameCid() {
            byte[] payload = "deduplicate me".getBytes();
            ContentID first = library.putContent(payload);
            ContentID second = library.putContent(payload);
            assertThat(second).isEqualTo(first);
        }
    }
}
