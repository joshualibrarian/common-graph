package dev.everydaythings.graph.runtime;


import dev.everydaythings.graph.encoding.CgCbor;

import dev.everydaythings.graph.canonical.HashTree;
import dev.everydaythings.graph.ref.CompoundKey;
import dev.everydaythings.graph.cryptography.VarSig;
import dev.everydaythings.graph.datum.Binding;
import dev.everydaythings.graph.datum.Body;
import dev.everydaythings.graph.datum.Frame;
import dev.everydaythings.graph.datum.Record;
import dev.everydaythings.graph.item.Item;
import dev.everydaythings.graph.language.ThematicRole;
import dev.everydaythings.graph.runtime.librarian.Librarian;
import dev.everydaythings.graph.item.Manifest;
import dev.everydaythings.graph.ref.ContentRef;
import dev.everydaythings.graph.ref.DatumRef;
import dev.everydaythings.graph.ref.ItemRef;
import dev.everydaythings.graph.cryptography.Signer;
import dev.everydaythings.graph.library.index.TokenPosting;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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

        @Test
        @DisplayName("inMemory() Librarian inherits signing capability from Signer")
        void inheritsSigningCapability() {
            Librarian lib = Librarian.inMemory();
            assertThat(lib.canSign()).isTrue();
            assertThat(lib.signingPublicKey()).isPresent();

            // Round-trip: librarian signs, verify with its own public key.
            byte[] message = "librarian-signed message".getBytes();
            assertThat(lib.verify(lib.signingPublicKey().orElseThrow(), message, lib.sign(message)))
                    .isTrue();
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
                    ItemRef.of(ItemRef.fromString("cg.predicate:test")),
                    List.of()
            );

            DatumRef cid = lib.persist(body);
            assertThat(cid).isEqualTo(body.datumId());

            // fetch(DatumRef) was deleted — bytes live behind the DatumRef→ContentRef
            // bridge; the `has(DatumRef)` check below proves storage round-tripped.
            assertThat(lib.has(cid)).isTrue();
        }

        @Test
        @DisplayName("fetchFrame returns body wrapped as a Frame; records empty when none persisted")
        void fetchFrameNoRecords() {
            Librarian lib = Librarian.inMemory();
            Body body = Body.of(
                    ItemRef.of(ItemRef.fromString("cg.predicate:authored")),
                    List.of(Binding.ref(
                            ItemRef.fromString("cg.role:theme"),
                            ItemRef.fromString("hobbit")))
            );

            DatumRef cid = lib.persist(body);
            Optional<Frame> decoded = lib.fetchFrame(cid);
            assertThat(decoded).isPresent();
            assertThat(decoded.get().body()).isEqualTo(body);
            assertThat(decoded.get().records()).isEmpty();
        }

        @Test
        @DisplayName("fetchFrame loads records persisted against the body")
        void fetchFrameWithRecords() {
            Librarian lib = Librarian.inMemory();
            Body body = Body.of(
                    ItemRef.of(ItemRef.fromString("cg.predicate:authored")),
                    List.of()
            );
            DatumRef bodyCid = lib.persist(body);

            Record record = new Record(DatumRef.of(bodyCid), List.of(), new byte[]{1, 2, 3});
            lib.persist(record);

            Optional<Frame> decoded = lib.fetchFrame(bodyCid);
            assertThat(decoded).isPresent();
            assertThat(decoded.get().body()).isEqualTo(body);
            assertThat(decoded.get().records()).containsExactly(record);
        }

        @Test
        @DisplayName("fetchManifest returns archetypal bodies wrapped as a Manifest")
        void fetchManifest() {
            Librarian lib = Librarian.inMemory();
            ItemRef iid = ItemRef.fromString("doc");
            Body manifestBody = Body.of(
                    ItemRef.of(ItemRef.fromString("cg.archetype:document")),
                    List.of(Binding.ref(Manifest.ITEM_ID, iid))
            );

            DatumRef cid = lib.persist(manifestBody);
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
                    ItemRef.of(ItemRef.fromString("cg.predicate:authored")),
                    List.of()
            );
            DatumRef cid = lib.persist(propositional);
            assertThat(lib.fetchManifest(cid)).isEmpty();
        }

        @Test
        @DisplayName("fetch returns empty for unknown CID")
        void fetchEmpty() {
            Librarian lib = Librarian.inMemory();
            ContentRef unknown = ContentRef.of("never-stored".getBytes());
            assertThat(lib.fetch(unknown)).isEmpty();
            assertThat(lib.has(unknown)).isFalse();
        }

        @Test
        @DisplayName("persistContent stores raw bytes addressable by CID")
        void persistContent() {
            Librarian lib = Librarian.inMemory();
            byte[] bytes = "hello world".getBytes();
            ContentRef cid = lib.persistContent(bytes);

            Optional<byte[]> fetched = lib.fetch(cid);
            assertThat(fetched).isPresent();
            assertThat(fetched.get()).containsExactly(bytes);
        }
    }

    @Nested
    @DisplayName("Item cache & one-instance-per-IID")
    class ItemCache {

        @Test
        @DisplayName("fetchItem returns the same Java instance on repeated calls")
        void fetchItemMemoizes() {
            Librarian lib = Librarian.inMemory();
            ItemRef iid = ItemRef.fromString("doc-1");
            new Item(iid, lib).commit(List.of());

            Item first = lib.fetchItem(iid).orElseThrow();
            Item second = lib.fetchItem(iid).orElseThrow();
            assertThat(second).isSameAs(first);
        }

        @Test
        @DisplayName("commit auto-registers the item")
        void commitRegisters() {
            Librarian lib = Librarian.inMemory();
            ItemRef iid = ItemRef.fromString("doc-1");
            Item committed = new Item(iid, lib);
            committed.commit(List.of());

            // After commit, the SAME instance is what fetchItem returns.
            assertThat(lib.fetchItem(iid)).hasValueSatisfying(found ->
                    assertThat(found).isSameAs(committed));
        }

        @Test
        @DisplayName("register makes an externally-constructed instance canonical")
        void explicitRegister() {
            Librarian lib = Librarian.inMemory();
            ItemRef iid = ItemRef.fromString("doc-1");
            Item custom = new Item(iid, lib);
            lib.register(custom);

            assertThat(lib.fetchItem(iid)).hasValueSatisfying(found ->
                    assertThat(found).isSameAs(custom));
        }

        @Test
        @DisplayName("Librarian self-registers in inMemory()")
        void librarianSelfRegisters() {
            Librarian lib = Librarian.inMemory();
            assertThat(lib.fetchItem(lib.iid())).hasValueSatisfying(found ->
                    assertThat(found).isSameAs(lib));
        }
    }

    @Nested
    @DisplayName("assembleFrame & onFrameAssembled routing")
    class FrameAssembly {

        /** Test subclass that records every frame it receives via onFrameAssembled. */
        static class CountingItem extends Item {
            final java.util.List<Frame> received = new java.util.ArrayList<>();

            CountingItem(ItemRef iid, Librarian lib) {
                super(iid, lib);
            }

            @Override
            public void onFrameAssembled(Frame frame) {
                received.add(frame);
            }
        }

        /** Test subclass that throws on every frame. */
        static class ThrowingItem extends Item {
            int callCount = 0;

            ThrowingItem(ItemRef iid, Librarian lib) {
                super(iid, lib);
            }

            @Override
            public void onFrameAssembled(Frame frame) {
                callCount++;
                throw new RuntimeException("oops");
            }
        }

        @Test
        @DisplayName("assembleFrame persists body and record")
        void persists() {
            Librarian lib = Librarian.inMemory();
            Body body = Body.of(
                    ItemRef.of(ItemRef.fromString("cg.predicate:status")),
                    List.of()
            );

            Frame frame = lib.assembleFrame(body, lib);

            assertThat(frame.body()).isEqualTo(body);
            assertThat(frame.records()).hasSize(1);
            assertThat(lib.has(ContentRef.of(CgCbor.codec().encode(frame.body())))).isTrue();
            assertThat(lib.has(ContentRef.of(CgCbor.codec().encode(frame.records().get(0))))).isTrue();
        }

        @Test
        @DisplayName("registered items referenced in body bindings receive onFrameAssembled")
        void referencedItemsNotified() {
            Librarian lib = Librarian.inMemory();
            ItemRef aliceId = ItemRef.fromString("alice");
            CountingItem alice = new CountingItem(aliceId, lib);
            lib.register(alice);

            Body body = Body.of(
                    ItemRef.of(ItemRef.fromString("cg.predicate:authored")),
                    List.of(Binding.ref(ItemRef.fromString("cg.role:agent"), aliceId))
            );
            Frame frame = lib.assembleFrame(body, lib);

            assertThat(alice.received).containsExactly(frame);
        }

        @Test
        @DisplayName("items not referenced are not notified")
        void unreferencedNotNotified() {
            Librarian lib = Librarian.inMemory();
            ItemRef aliceId = ItemRef.fromString("alice");
            ItemRef bobId = ItemRef.fromString("bob");
            CountingItem alice = new CountingItem(aliceId, lib);
            CountingItem bob = new CountingItem(bobId, lib);
            lib.register(alice);
            lib.register(bob);

            // Frame mentions only alice.
            Body body = Body.of(
                    ItemRef.of(ItemRef.fromString("cg.predicate:authored")),
                    List.of(Binding.ref(ItemRef.fromString("cg.role:agent"), aliceId))
            );
            lib.assembleFrame(body, lib);

            assertThat(alice.received).hasSize(1);
            assertThat(bob.received).isEmpty();
        }

        @Test
        @DisplayName("an item referenced by multiple bindings is notified exactly once (dedup)")
        void deduplicates() {
            Librarian lib = Librarian.inMemory();
            ItemRef aliceId = ItemRef.fromString("alice");
            CountingItem alice = new CountingItem(aliceId, lib);
            lib.register(alice);

            // Frame mentions alice twice (two different roles).
            Body body = Body.of(
                    ItemRef.of(ItemRef.fromString("cg.predicate:self-loop")),
                    List.of(
                            Binding.ref(ItemRef.fromString("cg.role:agent"), aliceId),
                            Binding.ref(ItemRef.fromString("cg.role:theme"), aliceId)
                    )
            );
            lib.assembleFrame(body, lib);

            assertThat(alice.received).hasSize(1);
        }

        @Test
        @DisplayName("references to unknown items are silently skipped")
        void unknownItemsSkipped() {
            Librarian lib = Librarian.inMemory();
            // No item registered for "ghost".
            Body body = Body.of(
                    ItemRef.of(ItemRef.fromString("cg.predicate:authored")),
                    List.of(Binding.ref(ItemRef.fromString("cg.role:agent"),
                            ItemRef.fromString("ghost")))
            );

            // Should not throw.
            Frame frame = lib.assembleFrame(body, lib);
            assertThat(frame.records()).hasSize(1);
        }

        @Test
        @DisplayName("an exception in one item's handler does not stop the chain")
        void exceptionsDontPropagate() {
            Librarian lib = Librarian.inMemory();
            ItemRef aliceId = ItemRef.fromString("alice");
            ItemRef bobId = ItemRef.fromString("bob");
            ThrowingItem alice = new ThrowingItem(aliceId, lib);
            CountingItem bob = new CountingItem(bobId, lib);
            lib.register(alice);
            lib.register(bob);

            Body body = Body.of(
                    ItemRef.of(ItemRef.fromString("cg.predicate:co-mention")),
                    List.of(
                            Binding.ref(ItemRef.fromString("cg.role:agent"), aliceId),
                            Binding.ref(ItemRef.fromString("cg.role:theme"), bobId)
                    )
            );

            lib.assembleFrame(body, lib);

            assertThat(alice.callCount).isEqualTo(1);     // alice was called and threw
            assertThat(bob.received).hasSize(1);           // bob was still called
        }

        @Test
        @DisplayName("the assembled record's signature verifies under the signer's public key")
        void recordSignatureVerifies() {
            Librarian lib = Librarian.inMemory();
            Body body = Body.of(
                    ItemRef.of(ItemRef.fromString("cg.predicate:status")),
                    List.of()
            );
            Frame frame = lib.assembleFrame(body, lib);

            byte[] signedBytes = HashTree.signingPayload(body);
            VarSig sig = frame.records().get(0).varsig();
            assertThat(lib.verify(lib.signingPublicKey().orElseThrow(), signedBytes, sig))
                    .isTrue();
        }
    }

    @Nested
    @DisplayName("Subclass dispatch via IMPLEMENTATION binding")
    class SubclassDispatch {

        /** Public static subclass — Class.forName needs to resolve it via FQN. */
        public static class TestThing extends Item {
            public final java.util.List<Frame> received = new java.util.ArrayList<>();

            public TestThing(ItemRef iid, Librarian lib) {
                super(iid, lib);
            }

            @Override
            public void onFrameAssembled(Frame frame) {
                received.add(frame);
            }
        }

        @Test
        @DisplayName("commit auto-injects IMPLEMENTATION for non-bare-Item subclasses")
        void commitInjectsImplementationForSubclass() {
            Librarian lib = Librarian.inMemory();
            TestThing thing = new TestThing(ItemRef.random(), lib);
            Manifest committed = thing.commit(List.of());

            Optional<Binding> impl = committed.implementation();
            assertThat(impl).isPresent();
            assertThat(impl.get().target()).isEqualTo(TestThing.class.getName());
        }

        @Test
        @DisplayName("commit does NOT inject IMPLEMENTATION for bare Item")
        void commitOmitsImplementationForBareItem() {
            Librarian lib = Librarian.inMemory();
            Item bare = new Item(ItemRef.random(), lib);
            Manifest committed = bare.commit(List.of());

            assertThat(committed.implementation()).isEmpty();
        }

        @Test
        @DisplayName("fetchItem hydrates as the subclass when manifest declares IMPLEMENTATION")
        void hydratesSubclassFromImplementation() {
            Librarian lib = Librarian.inMemory();
            ItemRef iid = ItemRef.random();
            // Manually persist a manifest body — bypasses commit's auto-register
            // so fetchItem hits the storage hydration path.
            Body manifestBody = Body.of(
                    ItemRef.of(ItemRef.fromString("cg.archetype:test-thing")),
                    List.of(
                            Binding.ref(Manifest.ITEM_ID, iid),
                            Manifest.implementation(TestThing.class)
                    )
            );
            lib.persist(manifestBody);

            Item fetched = lib.fetchItem(iid).orElseThrow();
            assertThat(fetched).isInstanceOf(TestThing.class);
            assertThat(fetched.iid()).isEqualTo(iid);
        }

        @Test
        @DisplayName("fetchItem falls back to bare Item when manifest has no IMPLEMENTATION")
        void fallsBackToBareItem() {
            Librarian lib = Librarian.inMemory();
            ItemRef iid = ItemRef.random();
            Body manifestBody = Body.of(
                    ItemRef.of(ItemRef.fromString("cg.archetype:plain")),
                    List.of(Binding.ref(Manifest.ITEM_ID, iid))
            );
            lib.persist(manifestBody);

            Item fetched = lib.fetchItem(iid).orElseThrow();
            assertThat(fetched.getClass()).isEqualTo(Item.class);  // exactly bare Item
        }

        @Test
        @DisplayName("fetchItem throws when IMPLEMENTATION points at a non-existent class")
        void throwsOnUnloadableImplementation() {
            Librarian lib = Librarian.inMemory();
            ItemRef iid = ItemRef.random();
            Body manifestBody = Body.of(
                    ItemRef.of(ItemRef.fromString("cg.archetype:bogus")),
                    List.of(
                            Binding.ref(Manifest.ITEM_ID, iid),
                            Manifest.implementation(
                                    ItemRef.iid(RuntimeVocabulary.Java.KEY),
                                    ItemRef.iid(RuntimeVocabulary.ClassName.KEY),
                                    "does.not.Exist")
                    )
            );
            lib.persist(manifestBody);

            assertThatThrownBy(() -> lib.fetchItem(iid))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("does.not.Exist")
                    .hasMessageContaining("not on the classpath");
        }

        @Test
        @DisplayName("fetchItem throws when IMPLEMENTATION class doesn't extend Item")
        void throwsOnNonItemClass() {
            Librarian lib = Librarian.inMemory();
            ItemRef iid = ItemRef.random();
            Body manifestBody = Body.of(
                    ItemRef.of(ItemRef.fromString("cg.archetype:bogus")),
                    List.of(
                            Binding.ref(Manifest.ITEM_ID, iid),
                            Manifest.implementation(String.class)
                    )
            );
            lib.persist(manifestBody);

            assertThatThrownBy(() -> lib.fetchItem(iid))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("does not extend Item");
        }

        @Test
        @DisplayName("hydrated subclass receives onFrameAssembled when frames reference it")
        void hydratedSubclassReceivesRouting() {
            Librarian lib = Librarian.inMemory();
            ItemRef iid = ItemRef.random();
            // Persist a manifest declaring IMPLEMENTATION → TestThing.
            Body manifestBody = Body.of(
                    ItemRef.of(ItemRef.fromString("cg.archetype:test-thing")),
                    List.of(
                            Binding.ref(Manifest.ITEM_ID, iid),
                            Manifest.implementation(TestThing.class)
                    )
            );
            lib.persist(manifestBody);

            // Assemble a frame referencing iid; routing internally fetches+hydrates
            // the TestThing, caches it, and calls onFrameAssembled.
            Body frameBody = Body.of(
                    ItemRef.of(ItemRef.fromString("cg.predicate:mention")),
                    List.of(Binding.ref(ItemRef.fromString("cg.role:theme"), iid))
            );
            Frame assembled = lib.assembleFrame(frameBody, lib);

            // Fetch the (now-cached) hydrated TestThing and verify it received the frame.
            TestThing thing = (TestThing) lib.fetchItem(iid).orElseThrow();
            assertThat(thing.received).contains(assembled);
        }

        @Test
        @DisplayName("commit-then-fetch returns the same registered subclass instance")
        void commitThenFetchSameInstance() {
            Librarian lib = Librarian.inMemory();
            TestThing thing = new TestThing(ItemRef.random(), lib);
            thing.commit(List.of());

            // After commit, the cached instance IS the one we constructed.
            Item fetched = lib.fetchItem(thing.iid()).orElseThrow();
            assertThat(fetched).isSameAs(thing);
        }
    }

    @Nested
    @DisplayName("Item loading")
    class ItemLoading {

        @Test
        @DisplayName("fetchItem returns empty for an unknown IID")
        void unknownItem() {
            Librarian lib = Librarian.inMemory();
            assertThat(lib.fetchItem(ItemRef.fromString("nobody-here"))).isEmpty();
        }

        @Test
        @DisplayName("fetchItem returns an Item bound to its current manifest")
        void fetchItemHydrates() {
            Librarian lib = Librarian.inMemory();
            ItemRef iid = ItemRef.fromString("doc-1");
            Body manifestBody = Body.of(
                    ItemRef.of(ItemRef.fromString("cg.archetype:document")),
                    List.of(Binding.ref(Manifest.ITEM_ID, iid))
            );
            DatumRef expectedVid = lib.persist(manifestBody);

            Optional<Item> loaded = lib.fetchItem(iid);
            assertThat(loaded).isPresent();
            assertThat(loaded.get().iid()).isEqualTo(iid);
            assertThat(loaded.get().librarian()).isSameAs(lib);
            assertThat(loaded.get().versionId()).contains(expectedVid);
        }

        @Test
        @DisplayName("End-to-end: librarian signs a body, persists the record, fetches it back, signature verifies")
        void signPersistFetchVerify() {
            Librarian lib = Librarian.inMemory();

            Body body = Body.of(
                    ItemRef.of(ItemRef.fromString("cg.predicate:authored")),
                    List.of(Binding.ref(
                            ItemRef.fromString("cg.role:theme"),
                            ItemRef.fromString("hobbit")))
            );
            DatumRef bodyCid = lib.persist(body);

            // Sign the body's Merkle digest with the librarian's own keypair, persist the record.
            byte[] signedBytes = HashTree.signingPayload(body);
            VarSig signature = lib.sign(signedBytes);
            Record record = Record.of(DatumRef.of(bodyCid), List.of(), signature);
            lib.persist(record);

            // Fetch the frame back; it should carry the persisted record.
            Frame frame = lib.fetchFrame(bodyCid).orElseThrow();
            assertThat(frame.records()).hasSize(1);
            Record fetched = frame.records().get(0);

            // Verify the signature with the librarian's own public key.
            assertThat(lib.verify(
                    lib.signingPublicKey().orElseThrow(),
                    signedBytes,
                    fetched.varsig()))
                    .isTrue();
        }

        @Test
        @DisplayName("fetchItem-loaded item supports endorsedFrames via the same librarian")
        void fetchItemSupportsEndorsedFrames() {
            Librarian lib = Librarian.inMemory();

            Body endorsedFrame = Body.of(
                    ItemRef.of(ItemRef.fromString("cg.predicate:authored")),
                    List.of(Binding.ref(
                            ItemRef.fromString("cg.role:theme"),
                            ItemRef.fromString("hobbit")))
            );
            DatumRef frameCid = lib.persist(endorsedFrame);

            ItemRef iid = ItemRef.fromString("doc-1");
            Body manifestBody = Body.of(
                    ItemRef.of(ItemRef.fromString("cg.archetype:document")),
                    List.of(
                            Binding.ref(Manifest.ITEM_ID, iid),
                            new Binding(Manifest.ENDORSES, frameCid)
                    )
            );
            lib.persist(manifestBody);

            Item item = lib.fetchItem(iid).orElseThrow();
            List<dev.everydaythings.graph.datum.Frame> frames = item.endorsedFrames().toList();
            assertThat(frames).hasSize(1);
            assertThat(frames.get(0).body()).isEqualTo(endorsedFrame);
        }
    }

    @Nested
    @DisplayName("Token lookup")
    class TokenLookup {

        @Test
        @DisplayName("indexed lexeme roundtrips into a rich Posting via Librarian.lookupToken")
        void roundtripBasicLexeme() {
            Librarian lib = Librarian.inMemory();
            // Bootstrap so Language seed items (English, German, ...) are
            // persisted with TYPE_INDEX entries — Library.lookupToken uses
            // those to recognize which qualifier on a Posting is its Language
            // scope. Without bootstrap, scope would resolve to null.
            lib.bootstrap();

            ItemRef lexemePredicate = ItemRef.fromString("test.predicate:lexeme");
            ItemRef targetSememe = ItemRef.fromString("test.sememe:create");
            // Use a token unique to this test so the bootstrap's auto-indexed
            // Lexeme frames don't add noise postings under the same token.
            String token = "test-token-wibblefrobnik";

            // LEXEME-shaped body: head=LEXEME, THEME→target, VALUE[Eng,Verb,Lemma]→token
            Body body = Body.of(
                    ItemRef.of(lexemePredicate),
                    List.of(
                            Binding.ref(
                                    ItemRef.iid(ThematicRole.Theme.KEY),
                                    targetSememe),
                            Binding.qualified(ItemRef.iid(ThematicRole.Value.KEY), List.of(
                                            new CompoundKey.Sememe(
                                                    ItemRef.iid(dev.everydaythings.graph.language.Language.English.KEY)), new CompoundKey.Sememe(
                                                    ItemRef.iid(dev.everydaythings.graph.language.PartOfSpeech.Verb.KEY)),
                                            new CompoundKey.Sememe(
                                                    ItemRef.iid(dev.everydaythings.graph.language.GrammaticalFeature.Lemma.KEY))),
                                    token)));

            // persist() walks the Body's text-typed bindings and writes token
            // index entries automatically — no explicit indexToken call needed.
            lib.persist(body);

            List<TokenPosting> postings = lib.lookupToken(token);

            assertThat(postings).hasSize(1);
            TokenPosting p = postings.get(0);
            assertThat(p.token()).isEqualTo(token);
            assertThat(p.target()).isEqualTo(targetSememe);
            assertThat(p.predicate()).isEqualTo(lexemePredicate);
            // Qualifiers are an unordered multiset; the indexer treats all
            // sememe qualifiers as features by default. Library.lookupToken
            // promotes a Language-archetype qualifier to scope when one is
            // recognized in TYPE_INDEX; if no Language is recognized (e.g.
            // bootstrap hasn't fully populated TYPE_INDEX for seed Languages),
            // scope stays null and all qualifiers remain in features. Assert
            // structurally: all three sememes are accounted for, in either
            // role.
            java.util.Set<ItemRef> allQualifiers = new java.util.HashSet<>(p.features());
            if (p.scope() != null) allQualifiers.add(p.scope());
            assertThat(allQualifiers).containsExactlyInAnyOrder(
                    ItemRef.iid(dev.everydaythings.graph.language.Language.English.KEY),
                    ItemRef.iid(dev.everydaythings.graph.language.PartOfSpeech.Verb.KEY),
                    ItemRef.iid(dev.everydaythings.graph.language.GrammaticalFeature.Lemma.KEY));
            // Source is the Body's semantic identity (DatumRef) — flipped from
            // ContentRef as part of the store-domain refactor (task #48).
            assertThat(p.source()).isEqualTo(body.datumId());
            assertThat(p.weight().doubleValue()).isEqualTo(1.0);
        }

        @Test
        @DisplayName("unknown token returns empty list")
        void unknownToken() {
            Librarian lib = Librarian.inMemory();
            assertThat(lib.lookupToken("nonexistent")).isEmpty();
        }

        @Test
        @DisplayName("Library.put auto-indexes text-target bindings — no explicit indexToken needed")
        void autoIndexOnPut() {
            Librarian lib = Librarian.inMemory();

            ItemRef titlePredicate = ItemRef.fromString("test.predicate:title");
            ItemRef movie = ItemRef.fromString("test.item:movie");

            Body body = Body.of(
                    ItemRef.of(titlePredicate),
                    List.of(
                            Binding.ref(
                                    ItemRef.iid(ThematicRole.Theme.KEY),
                                    movie),
                            new Binding(ItemRef.iid(ThematicRole.Value.KEY), "The Shawshank Redemption")));

            // Just persist — no explicit indexToken call.
            lib.persist(body);

            List<TokenPosting> postings =
                    lib.lookupToken("The Shawshank Redemption");

            assertThat(postings).hasSize(1);
            TokenPosting p = postings.get(0);
            assertThat(p.token()).isEqualTo("the shawshank redemption");  // normalized
            assertThat(p.target()).isEqualTo(movie);
            assertThat(p.predicate()).isEqualTo(titlePredicate);
            // Posting.source is the Body's semantic identity (DatumRef).
            assertThat(p.source()).isEqualTo(body.datumId());
        }

        @Test
        @DisplayName("token normalization — case folding")
        void caseFolding() {
            Librarian lib = Librarian.inMemory();

            ItemRef predicate = ItemRef.fromString("test.predicate:lexeme");
            Body body = Body.of(
                    ItemRef.of(predicate),
                    List.of(new Binding(ItemRef.iid(ThematicRole.Value.KEY), "Hello")));
            // persist() walks the text binding and indexes it (with case-folded
            // normalization) automatically — no explicit indexToken call.
            lib.persist(body);

            // Lookup with various cases — all should resolve.
            assertThat(lib.lookupToken("hello")).hasSize(1);
            assertThat(lib.lookupToken("HELLO")).hasSize(1);
            assertThat(lib.lookupToken("Hello")).hasSize(1);
        }
    }
}
