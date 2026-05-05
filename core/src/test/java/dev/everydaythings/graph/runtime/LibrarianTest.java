package dev.everydaythings.graph.runtime;

import dev.everydaythings.graph.frame.Binding;
import dev.everydaythings.graph.frame.Body;
import dev.everydaythings.graph.frame.Frame;
import dev.everydaythings.graph.item.Item;
import dev.everydaythings.graph.item.Manifest;
import dev.everydaythings.graph.item.id.ContentID;
import dev.everydaythings.graph.item.id.ItemID;
import dev.everydaythings.graph.item.id.ItemRef;
import dev.everydaythings.graph.item.user.Signer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class LibrarianTest {

    @Nested
    @DisplayName("Hierarchy")
    class Hierarchy {

        @Test
        @DisplayName("Librarian extends Signer (and therefore Item)")
        void extendsSigner() {
            Librarian lib = Librarian.inMemory();
            assertThat(lib).isInstanceOf(Signer.class);
            assertThat(lib).isInstanceOf(Item.class);
        }

        @Test
        @DisplayName("Librarian carries an iid")
        void carriesIid() {
            Librarian lib = Librarian.inMemory();
            assertThat(lib.iid()).isNotNull();
        }

        @Test
        @DisplayName("Librarian KEY is the archetype canonical key")
        void keyMatches() {
            assertThat(Librarian.KEY).isEqualTo("cg.archetype:librarian");
        }
    }

    @Nested
    @DisplayName("In-memory factory")
    class InMemoryFactory {

        @Test
        @DisplayName("inMemory() produces a usable Librarian with a Library")
        void inMemory() {
            Librarian lib = Librarian.inMemory();
            assertThat(lib.library()).isNotNull();
            assertThat(lib.rootPath()).isEmpty();
        }

        @Test
        @DisplayName("each inMemory() produces a fresh, independent Librarian")
        void eachFresh() {
            Librarian a = Librarian.inMemory();
            Librarian b = Librarian.inMemory();
            assertThat(a.iid()).isNotEqualTo(b.iid());
            assertThat(a.library()).isNotSameAs(b.library());
        }
    }

    @Nested
    @DisplayName("Storage delegation")
    class StorageDelegation {

        @Test
        @DisplayName("persist returns CID; fetch returns the bytes")
        void persistAndFetch() {
            Librarian lib = Librarian.inMemory();
            Body body = Body.of(
                    ItemRef.of(ItemID.fromString("cg.predicate:test")),
                    List.of()
            );

            ContentID cid = lib.persist(body);
            assertThat(cid).isEqualTo(body.cid());

            Optional<byte[]> fetched = lib.fetch(cid);
            assertThat(fetched).isPresent();
            assertThat(lib.has(cid)).isTrue();
        }

        @Test
        @DisplayName("fetchFrame returns the stored body wrapped as a Frame (records empty until index lands)")
        void fetchFrame() {
            Librarian lib = Librarian.inMemory();
            Body body = Body.of(
                    ItemRef.of(ItemID.fromString("cg.predicate:authored")),
                    List.of(Binding.ref(
                            ItemID.fromString("cg.role:theme"),
                            ItemID.fromString("hobbit")))
            );

            ContentID cid = lib.persist(body);
            Optional<Frame> decoded = lib.fetchFrame(cid);
            assertThat(decoded).isPresent();
            assertThat(decoded.get().body()).isEqualTo(body);
            assertThat(decoded.get().records()).isEmpty();
        }

        @Test
        @DisplayName("fetchManifest returns archetypal bodies wrapped as a Manifest")
        void fetchManifest() {
            Librarian lib = Librarian.inMemory();
            ItemID iid = ItemID.fromString("doc");
            Body manifestBody = Body.of(
                    ItemRef.of(ItemID.fromString("cg.archetype:document")),
                    List.of(Binding.ref(Manifest.ITEM_ID, iid))
            );

            ContentID cid = lib.persist(manifestBody);
            Optional<Manifest> decoded = lib.fetchManifest(cid);
            assertThat(decoded).isPresent();
            assertThat(decoded.get().itemId()).isEqualTo(iid);
            assertThat(decoded.get().records()).isEmpty();
        }

        @Test
        @DisplayName("fetchManifest returns empty for non-archetypal bodies (no ITEM_ID binding)")
        void fetchManifestNonArchetypal() {
            Librarian lib = Librarian.inMemory();
            Body propositional = Body.of(
                    ItemRef.of(ItemID.fromString("cg.predicate:authored")),
                    List.of()
            );
            ContentID cid = lib.persist(propositional);
            assertThat(lib.fetchManifest(cid)).isEmpty();
        }

        @Test
        @DisplayName("fetch returns empty for unknown CID")
        void fetchEmpty() {
            Librarian lib = Librarian.inMemory();
            ContentID unknown = ContentID.of("never-stored".getBytes());
            assertThat(lib.fetch(unknown)).isEmpty();
            assertThat(lib.has(unknown)).isFalse();
        }

        @Test
        @DisplayName("persistContent stores raw bytes addressable by CID")
        void persistContent() {
            Librarian lib = Librarian.inMemory();
            byte[] bytes = "hello world".getBytes();
            ContentID cid = lib.persistContent(bytes);

            Optional<byte[]> fetched = lib.fetch(cid);
            assertThat(fetched).isPresent();
            assertThat(fetched.get()).containsExactly(bytes);
        }
    }
}
