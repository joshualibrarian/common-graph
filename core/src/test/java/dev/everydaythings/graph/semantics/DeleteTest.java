package dev.everydaythings.graph.semantics;

import dev.everydaythings.graph.canonical.Scope;

import dev.everydaythings.graph.canonical.Canonical;
import dev.everydaythings.graph.datum.Binding;
import dev.everydaythings.graph.datum.Body;
import dev.everydaythings.graph.item.Item;
import dev.everydaythings.graph.id.ContentID;
import dev.everydaythings.graph.id.DatumID;
import dev.everydaythings.graph.id.ItemID;
import dev.everydaythings.graph.id.ItemRef;
import dev.everydaythings.graph.identity.Signer;
import dev.everydaythings.graph.runtime.Librarian;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises DELETE: implicit-authorization removal of items from local storage.
 *
 * <p>Validates:
 * <ul>
 *   <li>Self-authored DELETE removes manifests and their records</li>
 *   <li>Cache eviction follows storage removal</li>
 *   <li>Index entries are torn down (TYPE_INDEX, FORWARD_BINDINGS, RECORDS_BY_BODY)</li>
 *   <li>Other-signed DELETE is NOT auto-honored (requires explicit endorsement; deferred)</li>
 *   <li>The DELETE frame itself remains as audit</li>
 * </ul>
 */
class DeleteTest {

    @Nested
    @DisplayName("Self-authored DELETE")
    class SelfAuthored {

        @Test
        @DisplayName("removes the item's manifest from storage")
        void removesManifest() {
            Librarian lib = Librarian.inMemory();
            lib.bootstrap();

            ItemID iid = ItemID.fromString("doc-1");
            new Item(iid, lib).commit(List.of());
            assertThat(lib.library().manifestCidsForItem(iid)).hasSize(1);

            // Lib publishes its own DELETE frame (signed by lib).
            Body deleteBody = Body.of(
                    ItemRef.of(Delete.IID),
                    List.of(Binding.ref(ThematicRole.Theme.IID, iid))
            );
            lib.assembleFrame(deleteBody, lib);

            assertThat(lib.library().manifestCidsForItem(iid)).isEmpty();
        }

        @Test
        @DisplayName("evicts the item from the cache")
        void evictsFromCache() {
            Librarian lib = Librarian.inMemory();
            lib.bootstrap();

            ItemID iid = ItemID.fromString("doc-1");
            Item original = new Item(iid, lib);
            original.commit(List.of());

            // After commit, item is cached.
            assertThat(lib.fetchItem(iid)).hasValueSatisfying(i ->
                    assertThat(i).isSameAs(original));

            Body deleteBody = Body.of(
                    ItemRef.of(Delete.IID),
                    List.of(Binding.ref(ThematicRole.Theme.IID, iid))
            );
            lib.assembleFrame(deleteBody, lib);

            // Cache and storage both gone.
            assertThat(lib.fetchItem(iid)).isEmpty();
        }

        @Test
        @DisplayName("removes records along with the manifest")
        void removesRecords() {
            Librarian lib = Librarian.inMemory();
            lib.bootstrap();

            ItemID iid = ItemID.fromString("doc-1");
            Item item = new Item(iid, lib);
            item.commit(List.of());

            DatumID manifestCid = lib.library().manifestCidsForItem(iid).getFirst();
            // Commit auto-produced a record for the manifest.
            List<DatumID> recordCids = lib.library().recordCidsForBody(manifestCid);
            assertThat(recordCids).hasSize(1);

            Body deleteBody = Body.of(
                    ItemRef.of(Delete.IID),
                    List.of(Binding.ref(ThematicRole.Theme.IID, iid))
            );
            lib.assembleFrame(deleteBody, lib);

            // The record's bytes are gone from OBJECTS.
            assertThat(lib.has(recordCids.getFirst())).isFalse();
            // RECORDS_BY_BODY entry is torn down.
            assertThat(lib.library().recordCidsForBody(manifestCid)).isEmpty();
            // Manifest body bytes also gone.
            assertThat(lib.has(manifestCid)).isFalse();
        }

        @Test
        @DisplayName("tears down all index entries for the deleted manifest")
        void tearsDownIndexes() {
            Librarian lib = Librarian.inMemory();
            lib.bootstrap();

            ItemID iid = ItemID.fromString("doc-1");
            new Item(iid, lib).commit(List.of());

            // Pre-delete: indexed in FORWARD_BINDINGS via ITEM_ID role.
            assertThat(lib.library().manifestCidsForItem(iid)).isNotEmpty();

            Body deleteBody = Body.of(
                    ItemRef.of(Delete.IID),
                    List.of(Binding.ref(ThematicRole.Theme.IID, iid))
            );
            lib.assembleFrame(deleteBody, lib);

            // Post-delete: no index entries for this iid in any forward query.
            assertThat(lib.library().manifestCidsForItem(iid)).isEmpty();
        }

        @Test
        @DisplayName("DELETE frame itself remains as audit")
        void deleteFrameRemainsAsAudit() {
            Librarian lib = Librarian.inMemory();
            lib.bootstrap();

            ItemID iid = ItemID.fromString("doc-1");
            new Item(iid, lib).commit(List.of());

            Body deleteBody = Body.of(
                    ItemRef.of(Delete.IID),
                    List.of(Binding.ref(ThematicRole.Theme.IID, iid))
            );
            ContentID deleteFrameCid = ContentID.of(
                    lib.assembleFrame(deleteBody, lib).body().encodeBinary(Scope.BODY));

            // The item is gone, but the DELETE frame's body remains.
            assertThat(lib.library().manifestCidsForItem(iid)).isEmpty();
            assertThat(lib.has(deleteFrameCid)).isTrue();
        }

        @Test
        @DisplayName("DELETE without a THEME binding silently does nothing")
        void noThemeIsNoOp() {
            Librarian lib = Librarian.inMemory();
            lib.bootstrap();

            ItemID iid = ItemID.fromString("doc-1");
            new Item(iid, lib).commit(List.of());

            Body deleteBody = Body.of(ItemRef.of(Delete.IID), List.of());
            lib.assembleFrame(deleteBody, lib);

            // Item still there.
            assertThat(lib.library().manifestCidsForItem(iid)).hasSize(1);
        }

        @Test
        @DisplayName("DELETE on a non-existent item is a no-op (no error)")
        void deleteUnknownItem() {
            Librarian lib = Librarian.inMemory();
            lib.bootstrap();

            ItemID nonexistent = ItemID.fromString("nobody");
            Body deleteBody = Body.of(
                    ItemRef.of(Delete.IID),
                    List.of(Binding.ref(ThematicRole.Theme.IID, nonexistent))
            );
            // Should not throw.
            lib.assembleFrame(deleteBody, lib);
        }
    }

    @Nested
    @DisplayName("Other-signed DELETE")
    class OtherSigned {

        @Test
        @DisplayName("DELETE signed by another principal is NOT auto-honored")
        void otherSignedDeleteIgnored() {
            Librarian lib = Librarian.inMemory();
            lib.bootstrap();

            ItemID iid = ItemID.fromString("doc-1");
            new Item(iid, lib).commit(List.of());
            assertThat(lib.library().manifestCidsForItem(iid)).hasSize(1);

            // alice (a separate Signer) publishes a DELETE on lib's item.
            Signer alice = Signer.inMemory();
            Body deleteBody = Body.of(
                    ItemRef.of(Delete.IID),
                    List.of(Binding.ref(ThematicRole.Theme.IID, iid))
            );
            lib.assembleFrame(deleteBody, alice);

            // Item should still be there — lib didn't sign the DELETE.
            assertThat(lib.library().manifestCidsForItem(iid)).hasSize(1);
        }

        @Test
        @DisplayName("Other-signed DELETE frame is still persisted as data")
        void otherSignedDeletePersisted() {
            Librarian lib = Librarian.inMemory();
            lib.bootstrap();

            ItemID iid = ItemID.fromString("doc-1");
            new Item(iid, lib).commit(List.of());

            Signer alice = Signer.inMemory();
            Body deleteBody = Body.of(
                    ItemRef.of(Delete.IID),
                    List.of(Binding.ref(ThematicRole.Theme.IID, iid))
            );
            ContentID frameCid = ContentID.of(
                    lib.assembleFrame(deleteBody, alice).body().encodeBinary(Scope.BODY));

            // The DELETE frame is in storage even though it wasn't honored.
            assertThat(lib.has(frameCid)).isTrue();
        }
    }

    @Nested
    @DisplayName("Library.delete (low-level)")
    class LowLevelDelete {

        @Test
        @DisplayName("delete returns true when CID was present, false when absent")
        void deleteReturnValue() {
            Librarian lib = Librarian.inMemory();
            Body body = Body.of(
                    ItemRef.of(ItemID.fromString("cg.predicate:test")),
                    List.of()
            );
            DatumID cid = lib.persist(body);

            assertThat(lib.library().delete(cid)).isTrue();
            assertThat(lib.library().delete(cid)).isFalse();  // already gone
        }

        @Test
        @DisplayName("delete tears down RECORDS_BY_BODY index entries")
        void deleteRecordTearsDown() {
            Librarian lib = Librarian.inMemory();
            ItemID iid = ItemID.fromString("doc-1");
            Item item = new Item(iid, lib);
            item.commit(List.of());

            DatumID manifestCid = lib.library().manifestCidsForItem(iid).getFirst();
            DatumID recordCid = lib.library().recordCidsForBody(manifestCid).getFirst();

            // Delete the record directly.
            lib.library().delete(recordCid);

            // Index no longer references it.
            assertThat(lib.library().recordCidsForBody(manifestCid)).isEmpty();
            assertThat(lib.has(recordCid)).isFalse();
        }
    }
}
