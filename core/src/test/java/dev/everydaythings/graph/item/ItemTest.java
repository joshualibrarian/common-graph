package dev.everydaythings.graph.item;

import dev.everydaythings.graph.encoding.Canonical;
import dev.everydaythings.graph.encoding.HashTree;
import dev.everydaythings.graph.identity.VarSig;
import dev.everydaythings.graph.datum.Binding;
import dev.everydaythings.graph.datum.BindingTarget;
import dev.everydaythings.graph.datum.Body;
import dev.everydaythings.graph.datum.Frame;
import dev.everydaythings.graph.item.id.ContentID;
import dev.everydaythings.graph.item.id.DatumID;
import dev.everydaythings.graph.item.id.ItemID;
import dev.everydaythings.graph.item.id.ItemRef;
import dev.everydaythings.graph.identity.Signer;
import dev.everydaythings.graph.runtime.Librarian;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ItemTest {

    @Nested
    @DisplayName("Identity")
    class Identity {

        @Test
        @DisplayName("Item carries its iid")
        void carriesIid() {
            ItemID iid = ItemID.fromString("test-item");
            Item item = new Item(iid);
            assertThat(item.iid()).isEqualTo(iid);
        }

        @Test
        @DisplayName("Item permits null iid (anonymous — no identity)")
        void permitsNullIid() {
            Item item = new Item((ItemID) null);
            assertThat(item.iid()).isNull();
            assertThat(item.toString()).contains("anonymous");
        }

        @Test
        @DisplayName("Anonymous items are equal only to themselves (no shared identity)")
        void anonymousEqualityIsByReference() {
            Item a = new Item((ItemID) null);
            Item b = new Item((ItemID) null);
            assertThat(a).isEqualTo(a);
            assertThat(a).isNotEqualTo(b);
            // Distinct anonymous items must not collide in identity-keyed maps.
            assertThat(a.hashCode()).isNotEqualTo(b.hashCode());
        }

        @Test
        @DisplayName("Equality based on iid")
        void equalsByIid() {
            ItemID iid = ItemID.fromString("same-iid");
            Item a = new Item(iid);
            Item b = new Item(iid);
            assertThat(a).isEqualTo(b);
            assertThat(a.hashCode()).isEqualTo(b.hashCode());
        }

        @Test
        @DisplayName("Different iids produce unequal items")
        void differentIids() {
            Item a = new Item(ItemID.fromString("a"));
            Item b = new Item(ItemID.fromString("b"));
            assertThat(a).isNotEqualTo(b);
        }

        @Test
        @DisplayName("Concrete instantiation works")
        void canInstantiate() {
            Item item = new Item(ItemID.random());
            assertThat(item).isNotNull();
            assertThat(item.iid()).isNotNull();
        }
    }

    @Nested
    @DisplayName("Librarian binding")
    class LibrarianBinding {

        @Test
        @DisplayName("Single-arg constructor leaves librarian null")
        void noLibrarianByDefault() {
            Item item = new Item(ItemID.random());
            assertThat(item.librarian()).isNull();
        }

        @Test
        @DisplayName("Two-arg constructor accepts a librarian")
        void constructorWithLibrarian() {
            Librarian lib = Librarian.inMemory();
            Item item = new Item(ItemID.random(), lib);
            assertThat(item.librarian()).isSameAs(lib);
        }

        @Test
        @DisplayName("Two-arg constructor accepts null librarian")
        void constructorWithNullLibrarian() {
            Item item = new Item(ItemID.random(), null);
            assertThat(item.librarian()).isNull();
        }

        @Test
        @DisplayName("bindLibrarian rebinds")
        void bindLibrarian() {
            Item item = new Item(ItemID.random());
            assertThat(item.librarian()).isNull();

            Librarian lib = Librarian.inMemory();
            item.bindLibrarian(lib);
            assertThat(item.librarian()).isSameAs(lib);
        }

        @Test
        @DisplayName("bindLibrarian can replace an existing binding")
        void rebind() {
            Librarian first = Librarian.inMemory();
            Item item = new Item(ItemID.random(), first);

            Librarian second = Librarian.inMemory();
            item.bindLibrarian(second);
            assertThat(item.librarian()).isSameAs(second);
        }
    }

    @Nested
    @DisplayName("Canonical key")
    class CanonicalKey {

        @Test
        @DisplayName("KEY constant matches the documented canonical key")
        void keyMatches() {
            assertThat(Item.KEY).isEqualTo("cg.sememe:item");
        }
    }

    @Nested
    @DisplayName("Manifest state")
    class ManifestState {

        @Test
        @DisplayName("New item has no current manifest")
        void noCurrentByDefault() {
            Item item = new Item(ItemID.random());
            assertThat(item.current()).isNull();
            assertThat(item.versionId()).isEmpty();
            assertThat(item.parents()).isEmpty();
            assertThat(item.endorses()).isEmpty();
        }

        @Test
        @DisplayName("bindManifest attaches a manifest")
        void bindManifest() {
            ItemID iid = ItemID.fromString("test-item");
            Item item = new Item(iid);

            Body manifestBody = Body.of(
                    ItemRef.of(ItemID.fromString("cg.archetype:document")),
                    List.of(Binding.ref(Manifest.ITEM_ID, iid))
            );
            Manifest manifest = Manifest.of(manifestBody);

            item.bindManifest(manifest);
            assertThat(item.current()).isSameAs(manifest);
            assertThat(item.versionId()).contains(manifest.versionId());
        }

        @Test
        @DisplayName("Replacing manifest with bindManifest works (git-checkout style)")
        void replaceManifest() {
            ItemID iid = ItemID.fromString("test-item");
            Item item = new Item(iid);

            Body bodyV1 = Body.of(
                    ItemRef.of(ItemID.fromString("cg.archetype:document")),
                    List.of(Binding.ref(Manifest.ITEM_ID, iid))
            );
            Manifest v1 = Manifest.of(bodyV1);

            Body bodyV2 = Body.of(
                    ItemRef.of(ItemID.fromString("cg.archetype:document")),
                    List.of(
                            Binding.ref(Manifest.ITEM_ID, iid),
                            Binding.ref(Manifest.FOLLOWS, ItemID.fromString("v1-as-iid"))
                    )
            );
            Manifest v2 = Manifest.of(bodyV2);

            item.bindManifest(v1);
            assertThat(item.current()).isSameAs(v1);

            item.bindManifest(v2);
            assertThat(item.current()).isSameAs(v2);
            assertThat(item.parents()).hasSize(1);
        }

        @Test
        @DisplayName("Convenience accessors delegate to current manifest")
        void delegatedAccessors() {
            ItemID iid = ItemID.fromString("doc-item");
            Body body = Body.of(
                    ItemRef.of(ItemID.fromString("cg.archetype:document")),
                    List.of(
                            Binding.ref(Manifest.ITEM_ID, iid),
                            Binding.ref(Manifest.ENDORSES, ItemID.fromString("frame-1")),
                            Binding.ref(Manifest.ENDORSES, ItemID.fromString("frame-2"))
                    )
            );
            Manifest manifest = Manifest.of(body);

            Item item = new Item(iid);
            item.bindManifest(manifest);

            assertThat(item.versionId()).contains(manifest.versionId());
            assertThat(item.endorses()).hasSize(2);
        }

        @Test
        @DisplayName("Unbinding manifest by passing null clears state")
        void unbind() {
            ItemID iid = ItemID.fromString("test");
            Body body = Body.of(
                    ItemRef.of(ItemID.fromString("cg.archetype:document")),
                    List.of(Binding.ref(Manifest.ITEM_ID, iid))
            );
            Item item = new Item(iid);
            item.bindManifest(Manifest.of(body));
            assertThat(item.current()).isNotNull();

            item.bindManifest(null);
            assertThat(item.current()).isNull();
            assertThat(item.versionId()).isEmpty();
        }
    }

    @Nested
    @DisplayName("Endorsed frames")
    class EndorsedFrames {

        @Test
        @DisplayName("No manifest → empty stream")
        void noManifest() {
            Item item = new Item(ItemID.random(), Librarian.inMemory());
            assertThat(item.endorsedFrames()).isEmpty();
        }

        @Test
        @DisplayName("No librarian → empty stream")
        void noLibrarian() {
            ItemID iid = ItemID.fromString("orphan");
            Body manifestBody = Body.of(
                    ItemRef.of(ItemID.fromString("cg.archetype:document")),
                    List.of(
                            Binding.ref(Manifest.ITEM_ID, iid),
                            Binding.ref(Manifest.ENDORSES, ItemID.fromString("frame-1"))
                    )
            );
            Item item = new Item(iid);
            item.bindManifest(Manifest.of(manifestBody));
            assertThat(item.endorsedFrames()).isEmpty();
        }

        @Test
        @DisplayName("Materializes endorsed bodies via the librarian; missing CIDs are silently dropped")
        void materializesEndorsements() {
            Librarian lib = Librarian.inMemory();

            Body presentFrame = Body.of(
                    ItemRef.of(ItemID.fromString("cg.predicate:authored")),
                    List.of(Binding.ref(
                            ItemID.fromString("cg.role:theme"),
                            ItemID.fromString("hobbit")))
            );
            DatumID presentCid = lib.persist(presentFrame);

            DatumID missingCid = DatumID.of("never-stored".getBytes());

            ItemID iid = ItemID.fromString("doc");
            Body manifestBody = Body.of(
                    ItemRef.of(ItemID.fromString("cg.archetype:document")),
                    List.of(
                            Binding.ref(Manifest.ITEM_ID, iid),
                            new Binding(Manifest.ENDORSES, BindingTarget.ref(presentCid)),
                            new Binding(Manifest.ENDORSES, BindingTarget.ref(missingCid))
                    )
            );
            Item item = new Item(iid, lib);
            item.bindManifest(Manifest.of(manifestBody));

            List<Frame> resolved = item.endorsedFrames().toList();
            assertThat(resolved).hasSize(1);
            assertThat(resolved.get(0).body()).isEqualTo(presentFrame);
        }
    }

    @Nested
    @DisplayName("Archetype")
    class Archetype {

        @Test
        @DisplayName("bare Item returns Item.IID")
        void bareItemArchetype() {
            Item item = new Item(ItemID.random());
            assertThat(item.archetype()).isEqualTo(Item.IID);
        }

        @Test
        @DisplayName("Signer subclass returns Signer.ARCHETYPE")
        void signerArchetype() {
            Signer s = new Signer(ItemID.random());
            assertThat(s.archetype()).isEqualTo(Signer.ARCHETYPE);
            assertThat(s.archetype()).isNotEqualTo(Item.IID);
        }

        @Test
        @DisplayName("Librarian subclass returns Librarian.IID")
        void librarianArchetype() {
            Librarian lib = Librarian.inMemory();
            assertThat(lib.archetype()).isEqualTo(Librarian.IID);
            assertThat(lib.archetype()).isNotEqualTo(Signer.ARCHETYPE);
        }
    }

    @Nested
    @DisplayName("Commit")
    class Commit {

        @Test
        @DisplayName("commit with no librarian throws")
        void commitWithoutLibrarianThrows() {
            Item item = new Item(ItemID.random());
            assertThatThrownBy(() -> item.commit(List.of()))
                    .isInstanceOf(IllegalStateException.class);
        }

        @Test
        @DisplayName("inception commit produces a manifest with ITEM_ID and no FOLLOWS")
        void inceptionCommit() {
            Librarian lib = Librarian.inMemory();
            ItemID iid = ItemID.fromString("doc-1");
            Item item = new Item(iid, lib);
            assertThat(item.versionId()).isEmpty();

            Manifest manifest = item.commit(List.of());

            assertThat(manifest.itemId()).isEqualTo(iid);
            assertThat(manifest.parents()).isEmpty();
            assertThat(item.current()).isSameAs(manifest);
            assertThat(item.versionId()).contains(manifest.versionId());
        }

        @Test
        @DisplayName("sequential commit FOLLOWS the previous version")
        void sequentialCommitLinksVersions() {
            Librarian lib = Librarian.inMemory();
            Item item = new Item(ItemID.fromString("doc-1"), lib);

            Manifest v1 = item.commit(List.of());
            DatumID v1Cid = v1.versionId();

            Manifest v2 = item.commit(List.of());

            assertThat(v2.parents()).containsExactly(v1Cid);
            assertThat(item.current()).isSameAs(v2);
        }

        @Test
        @DisplayName("commit body uses the item's archetype as the head")
        void commitBodyHasArchetypeHead() {
            Librarian lib = Librarian.inMemory();
            Item item = new Item(ItemID.fromString("doc-1"), lib);

            Manifest manifest = item.commit(List.of());

            // Body's head is the item's archetype (Item.IID for bare Item).
            assertThat(((ItemRef) manifest.body().head()).iid())
                    .isEqualTo(Item.IID);
        }

        @Test
        @DisplayName("Signer subclass commits with Signer's archetype as head")
        void signerCommitUsesSignerArchetype() {
            Librarian lib = Librarian.inMemory();
            // Use Signer-typed item bound to the librarian for storage.
            Signer s = new Signer(ItemID.fromString("alice")) {};
            // Need a librarian on the Signer; bindLibrarian:
            s.bindLibrarian(lib);

            Manifest manifest = s.commit(lib, List.of());

            assertThat(((ItemRef) manifest.body().head()).iid())
                    .isEqualTo(Signer.ARCHETYPE);
        }

        @Test
        @DisplayName("commit's record signature verifies with the signer's public key")
        void commitRecordSignatureVerifies() {
            Librarian lib = Librarian.inMemory();
            Item item = new Item(ItemID.fromString("doc-1"), lib);

            Manifest manifest = item.commit(List.of());

            // The new manifest carries the record produced by the commit.
            assertThat(manifest.records()).hasSize(1);
            VarSig sig = manifest.records().get(0).varsig();

            byte[] signedBytes = HashTree.signingPayload(manifest.body());
            assertThat(Librarian.verify(lib.signingPublicKey().orElseThrow(), signedBytes, sig))
                    .isTrue();
        }

        @Test
        @DisplayName("committed body and record are persisted to the librarian")
        void committedBytesArePersisted() {
            Librarian lib = Librarian.inMemory();
            Item item = new Item(ItemID.fromString("doc-1"), lib);

            Manifest manifest = item.commit(List.of());
            DatumID bodyCid = manifest.versionId();
            ContentID recordCid = ContentID.of(
                    manifest.records().get(0).encodeBinary(Canonical.Scope.BODY));

            assertThat(lib.has(bodyCid)).isTrue();
            assertThat(lib.has(recordCid)).isTrue();
        }

        @Test
        @DisplayName("after commit, librarian.fetchItem returns the item with the same current manifest")
        void fetchItemAfterCommit() {
            Librarian lib = Librarian.inMemory();
            ItemID iid = ItemID.fromString("doc-1");
            Item item = new Item(iid, lib);
            Manifest committed = item.commit(List.of());

            Item refetched = lib.fetchItem(iid).orElseThrow();
            assertThat(refetched.iid()).isEqualTo(iid);
            assertThat(refetched.versionId()).contains(committed.versionId());
        }

        @Test
        @DisplayName("commit-supplied bindings appear in the manifest body alongside ITEM_ID")
        void additionalBindingsPropagate() {
            Librarian lib = Librarian.inMemory();
            ItemID iid = ItemID.fromString("doc-1");
            Item item = new Item(iid, lib);

            // Persist a frame body so we can endorse it.
            Body endorsed = Body.of(
                    ItemRef.of(ItemID.fromString("cg.predicate:authored")),
                    List.of()
            );
            DatumID endorsedCid = lib.persist(endorsed);

            Binding endorsement = new Binding(Manifest.ENDORSES, BindingTarget.ref(endorsedCid));
            Manifest manifest = item.commit(List.of(endorsement));

            assertThat(manifest.endorses()).hasSize(1);
            assertThat(item.endorsedFrames().toList()).hasSize(1);
        }

        @Test
        @DisplayName("commit can use a different signer than the librarian")
        void commitWithExplicitSigner() {
            Librarian lib = Librarian.inMemory();
            Signer alice = Signer.inMemory();
            Item item = new Item(ItemID.fromString("doc-1"), lib);

            // Storage goes through `lib`; signing is done by `alice`.
            Manifest manifest = item.commit(alice, List.of());

            VarSig sig = manifest.records().get(0).varsig();
            byte[] signedBytes = HashTree.signingPayload(manifest.body());
            // Alice's public key verifies — not the librarian's.
            assertThat(Signer.verify(alice.signingPublicKey().orElseThrow(), signedBytes, sig)).isTrue();
            assertThat(Librarian.verify(lib.signingPublicKey().orElseThrow(), signedBytes, sig)).isFalse();
        }
    }
}
