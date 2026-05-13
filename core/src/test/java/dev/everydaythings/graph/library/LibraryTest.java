package dev.everydaythings.graph.library;

import dev.everydaythings.graph.encoding.Canonical;
import dev.everydaythings.graph.datum.Binding;
import dev.everydaythings.graph.datum.BindingTarget;
import dev.everydaythings.graph.datum.Body;
import dev.everydaythings.graph.datum.Record;
import dev.everydaythings.graph.item.Literal;
import dev.everydaythings.graph.item.Manifest;
import dev.everydaythings.graph.item.id.CompoundKey;
import dev.everydaythings.graph.item.id.ContentID;
import dev.everydaythings.graph.item.id.DatumID;
import dev.everydaythings.graph.item.id.FrameRef;
import dev.everydaythings.graph.item.id.ItemID;
import dev.everydaythings.graph.item.id.ItemRef;
import dev.everydaythings.graph.language.ThematicRole;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class LibraryTest {

    // Library is concrete and exposes both domain-object queries and the
    // byte-store-specific contentIdsForDatum (which throws if the data store
    // isn't a DataByteStore — but Library.inMemory() always is).
    private Library library;

    @BeforeEach
    void setUp() {
        library = Library.inMemory();
    }

    @AfterEach
    void tearDown() {
        library.close();
    }

    @Nested
    @DisplayName("Datum persistence")
    class DatumPersistence {

        @Test
        @DisplayName("put returns the Datum's semantic identity (DatumID)")
        void putReturnsDatumId() {
            Body body = Body.of(
                    ItemRef.of(ItemID.fromString("cg.predicate:authored")),
                    List.of(Binding.ref(
                            ItemID.fromString("cg.role:theme"),
                            ItemID.fromString("book")))
            );

            DatumID id = library.put(body);
            assertThat(id).isEqualTo(body.datumId());
        }

        @Test
        @DisplayName("fetchDatum retrieves the Datum after put")
        void fetchDatumAfterPut() {
            Body body = Body.of(
                    ItemRef.of(ItemID.fromString("cg.predicate:authored")),
                    List.of()
            );

            DatumID id = library.put(body);
            assertThat(library.fetchDatum(id)).isPresent();
        }

        @Test
        @DisplayName("fetchDatum returns empty for unknown DatumID")
        void fetchDatumEmptyForUnknown() {
            DatumID unknown = new Body(
                    ItemRef.of(ItemID.fromString("cg.predicate:never-stored")),
                    List.of()).datumId();
            assertThat(library.fetchDatum(unknown)).isEmpty();
        }

        @Test
        @DisplayName("has returns true after put")
        void hasReturnsTrueAfterPut() {
            Body body = Body.of(
                    ItemRef.of(ItemID.fromString("cg.predicate:test")),
                    List.of()
            );
            DatumID id = library.put(body);
            assertThat(library.has(id)).isTrue();
        }

        @Test
        @DisplayName("has returns false for unknown CID")
        void hasFalseForUnknown() {
            ContentID unknown = ContentID.of("not-here".getBytes());
            assertThat(library.hasContent(unknown)).isFalse();
        }

        @Test
        @DisplayName("idempotent: putting the same Datum twice yields the same CID")
        void idempotentPut() {
            Body body = Body.of(
                    ItemRef.of(ItemID.fromString("cg.predicate:test")),
                    List.of()
            );

            DatumID first = library.put(body);
            DatumID second = library.put(body);
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
            assertThat(library.hasContent(cid)).isTrue();

            Optional<byte[]> retrieved = library.getContent(cid);
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

    @Nested
    @DisplayName("RECORDS_BY_BODY indexing")
    class RecordsByBodyIndexing {

        @Test
        @DisplayName("recordCidsForBody returns empty for a body with no attestations")
        void noAttestations() {
            Body body = Body.of(
                    ItemRef.of(ItemID.fromString("cg.predicate:test")),
                    List.of()
            );
            DatumID bodyCid = library.put(body);
            assertThat(library.recordCidsForBody(bodyCid)).isEmpty();
        }

        @Test
        @DisplayName("Records are indexed by body CID on put")
        void singleRecordIndexed() {
            Body body = Body.of(
                    ItemRef.of(ItemID.fromString("cg.predicate:test")),
                    List.of()
            );
            DatumID bodyCid = library.put(body);

            Record record = new Record(FrameRef.of(bodyCid), List.of(), new byte[]{1, 2, 3});
            DatumID recordCid = library.put(record);

            assertThat(library.recordCidsForBody(bodyCid)).containsExactly(recordCid);
        }

        @Test
        @DisplayName("Multiple records against the same body all surface")
        void multipleRecordsForSameBody() {
            Body body = Body.of(
                    ItemRef.of(ItemID.fromString("cg.predicate:test")),
                    List.of()
            );
            DatumID bodyCid = library.put(body);

            Record r1 = new Record(FrameRef.of(bodyCid), List.of(), new byte[]{1});
            Record r2 = new Record(FrameRef.of(bodyCid), List.of(), new byte[]{2});
            DatumID c1 = library.put(r1);
            DatumID c2 = library.put(r2);

            assertThat(library.recordCidsForBody(bodyCid))
                    .containsExactlyInAnyOrder(c1, c2);
        }

        @Test
        @DisplayName("Records against different bodies don't cross-pollinate")
        void recordsScopedToTheirBody() {
            Body bodyA = Body.of(
                    ItemRef.of(ItemID.fromString("cg.predicate:a")),
                    List.of()
            );
            Body bodyB = Body.of(
                    ItemRef.of(ItemID.fromString("cg.predicate:b")),
                    List.of()
            );
            DatumID cidA = library.put(bodyA);
            DatumID cidB = library.put(bodyB);

            Record recordA = new Record(FrameRef.of(cidA), List.of(), new byte[]{1});
            DatumID recordCidA = library.put(recordA);

            assertThat(library.recordCidsForBody(cidA)).containsExactly(recordCidA);
            assertThat(library.recordCidsForBody(cidB)).isEmpty();
        }
    }

    @Nested
    @DisplayName("TYPE_INDEX indexing")
    class TypeIndexIndexing {

        @Test
        @DisplayName("manifestCidsForType returns empty for an unindexed type")
        void noManifests() {
            ItemID typeIid = ItemID.fromString("cg.archetype:document");
            assertThat(library.manifestCidsForType(typeIid)).isEmpty();
        }

        @Test
        @DisplayName("Archetypal bodies (with ITEM_ID binding) are indexed by type on put")
        void archetypalBodyIndexed() {
            ItemID typeIid = ItemID.fromString("cg.archetype:document");
            ItemID itemIid = ItemID.fromString("doc-1");
            Body manifestBody = Body.of(
                    ItemRef.of(typeIid),
                    List.of(Binding.ref(Manifest.ITEM_ID, itemIid))
            );
            DatumID bodyCid = library.put(manifestBody);

            assertThat(library.manifestCidsForType(typeIid)).containsExactly(bodyCid);
        }

        @Test
        @DisplayName("Frame bodies (no ITEM_ID binding) are NOT in TYPE_INDEX")
        void frameBodyNotIndexed() {
            ItemID predicateIid = ItemID.fromString("cg.predicate:authored");
            Body frameBody = Body.of(
                    ItemRef.of(predicateIid),
                    List.of(Binding.ref(
                            ItemID.fromString("cg.role:theme"),
                            ItemID.fromString("hobbit")))
            );
            library.put(frameBody);

            assertThat(library.manifestCidsForType(predicateIid)).isEmpty();
        }

        @Test
        @DisplayName("Multiple manifests of the same type all surface")
        void multipleManifestsOfSameType() {
            ItemID typeIid = ItemID.fromString("cg.archetype:document");
            Body m1 = Body.of(
                    ItemRef.of(typeIid),
                    List.of(Binding.ref(Manifest.ITEM_ID, ItemID.fromString("doc-1")))
            );
            Body m2 = Body.of(
                    ItemRef.of(typeIid),
                    List.of(Binding.ref(Manifest.ITEM_ID, ItemID.fromString("doc-2")))
            );
            DatumID c1 = library.put(m1);
            DatumID c2 = library.put(m2);

            assertThat(library.manifestCidsForType(typeIid))
                    .containsExactlyInAnyOrder(c1, c2);
        }

        @Test
        @DisplayName("Manifests of different types don't cross-pollinate")
        void typesScoped() {
            ItemID typeA = ItemID.fromString("cg.archetype:a");
            ItemID typeB = ItemID.fromString("cg.archetype:b");
            Body manifestA = Body.of(
                    ItemRef.of(typeA),
                    List.of(Binding.ref(Manifest.ITEM_ID, ItemID.fromString("a-1")))
            );
            DatumID cidA = library.put(manifestA);

            assertThat(library.manifestCidsForType(typeA)).containsExactly(cidA);
            assertThat(library.manifestCidsForType(typeB)).isEmpty();
        }

        @Test
        @DisplayName("Version-pinned archetype heads still surface in unpinned type queries")
        void versionPinnedHeadStillFound() {
            ItemID typeIid = ItemID.fromString("cg.archetype:document");
            ContentID typeVid = ContentID.of("v1".getBytes());
            Body manifestBody = Body.of(
                    ItemRef.of(typeIid, typeVid),
                    List.of(Binding.ref(Manifest.ITEM_ID, ItemID.fromString("doc-1")))
            );
            DatumID bodyCid = library.put(manifestBody);

            assertThat(library.manifestCidsForType(typeIid)).containsExactly(bodyCid);
        }
    }

    @Nested
    @DisplayName("FORWARD_BINDINGS indexing")
    class ForwardBindingsIndexing {

        @Test
        @DisplayName("manifestCidsForItem returns empty for an unindexed item")
        void noManifestsForUnknownItem() {
            ItemID iid = ItemID.fromString("nobody-here");
            assertThat(library.manifestCidsForItem(iid)).isEmpty();
        }

        @Test
        @DisplayName("Manifest with ITEM_ID binding becomes findable via manifestCidsForItem")
        void manifestFindableByItemId() {
            ItemID iid = ItemID.fromString("doc-1");
            Body manifestBody = Body.of(
                    ItemRef.of(ItemID.fromString("cg.archetype:document")),
                    List.of(Binding.ref(Manifest.ITEM_ID, iid))
            );
            DatumID bodyCid = library.put(manifestBody);

            assertThat(library.manifestCidsForItem(iid)).containsExactly(bodyCid);
        }

        @Test
        @DisplayName("Frame bodies (no ITEM_ID binding) don't pollute item lookup")
        void frameBodyNotInItemLookup() {
            ItemID iid = ItemID.fromString("hobbit");
            Body frameBody = Body.of(
                    ItemRef.of(ItemID.fromString("cg.predicate:authored")),
                    List.of(Binding.ref(ItemID.fromString("cg.role:theme"), iid))
            );
            library.put(frameBody);

            // The frame's THEME→hobbit binding IS forward-indexed, but under the THEME role,
            // not the ITEM_ID role; item lookup should not surface it.
            assertThat(library.manifestCidsForItem(iid)).isEmpty();
        }

        @Test
        @DisplayName("Multiple manifest versions for the same item all surface")
        void multipleVersionsForItem() {
            ItemID iid = ItemID.fromString("doc-1");
            Body v1 = Body.of(
                    ItemRef.of(ItemID.fromString("cg.archetype:document")),
                    List.of(Binding.ref(Manifest.ITEM_ID, iid))
            );
            Body v2 = Body.of(
                    ItemRef.of(ItemID.fromString("cg.archetype:document")),
                    List.of(
                            Binding.ref(Manifest.ITEM_ID, iid),
                            Binding.ref(Manifest.FOLLOWS, ItemID.fromString("v1-vid"))
                    )
            );
            DatumID c1 = library.put(v1);
            DatumID c2 = library.put(v2);

            assertThat(library.manifestCidsForItem(iid)).containsExactlyInAnyOrder(c1, c2);
        }

        @Test
        @DisplayName("Different items don't cross-pollinate")
        void itemsScoped() {
            ItemID iidA = ItemID.fromString("doc-A");
            ItemID iidB = ItemID.fromString("doc-B");
            Body manifestA = Body.of(
                    ItemRef.of(ItemID.fromString("cg.archetype:document")),
                    List.of(Binding.ref(Manifest.ITEM_ID, iidA))
            );
            DatumID cidA = library.put(manifestA);

            assertThat(library.manifestCidsForItem(iidA)).containsExactly(cidA);
            assertThat(library.manifestCidsForItem(iidB)).isEmpty();
        }

        @Test
        @DisplayName("Bodies with literal-target bindings are not corrupted by indexing")
        void literalTargetsSkippedNotCrashed() {
            ItemID iid = ItemID.fromString("doc-with-literal");
            Body manifestBody = Body.of(
                    ItemRef.of(ItemID.fromString("cg.archetype:document")),
                    List.of(
                            Binding.ref(Manifest.ITEM_ID, iid),
                            Binding.literal(ThematicRole.Topic.IID, Literal.ofText("just a label"))
                    )
            );
            DatumID bodyCid = library.put(manifestBody);

            // The literal binding is silently skipped by the indexer; the ITEM_ID binding
            // still indexes correctly.
            assertThat(library.manifestCidsForItem(iid)).containsExactly(bodyCid);
        }

        @Test
        @DisplayName("Qualified-key bindings (with qualifiers) coexist with simple-key indexing")
        void qualifiedKeysCoexist() {
            ItemID iid = ItemID.fromString("doc-qualified");
            ItemID qualifierIid = ItemID.fromString("cg.qualifier:test");
            Body manifestBody = Body.of(
                    ItemRef.of(ItemID.fromString("cg.archetype:document")),
                    List.of(
                            Binding.ref(Manifest.ITEM_ID, iid),
                            Binding.qualified(
                                    ThematicRole.Topic.IID,
                                    List.of(new CompoundKey.Sememe(qualifierIid)),
                                    BindingTarget.iid(ItemID.fromString("some-target")))
                    )
            );
            DatumID bodyCid = library.put(manifestBody);

            // The qualified-key binding is indexed (its key includes the qualifier CBOR);
            // we don't query by it in Phase 1, but it must not break the simple-key lookup.
            assertThat(library.manifestCidsForItem(iid)).containsExactly(bodyCid);
        }
    }

    @Nested
    @DisplayName("DATUM_INDEX (semantic identity → wire-form realizations)")
    class RefIndexing {

        @Test
        @DisplayName("contentIdsForDatum returns empty for an unindexed DatumID")
        void contentIdsForUnindexedDatum() {
            Body body = Body.of(
                    ItemRef.of(ItemID.fromString("cg.predicate:authored")),
                    List.of(Binding.ref(ThematicRole.Theme.IID, ItemID.fromString("book")))
            );
            // Compute the DatumID without persisting.
            assertThat(library.contentIdsForDatum(body.datumId())).isEmpty();
        }

        @Test
        @DisplayName("Persisted Datums are indexed by DatumID → ContentID")
        void datumIndexedOnPut() {
            Body body = Body.of(
                    ItemRef.of(ItemID.fromString("cg.predicate:authored")),
                    List.of(Binding.ref(ThematicRole.Theme.IID, ItemID.fromString("book")))
            );

            DatumID datumId = library.put(body);
            assertThat(datumId).isEqualTo(body.datumId());

            // The DatumID resolves to exactly one realization: the ContentID of the stored bytes.
            ContentID expectedContentId = ContentID.of(body.encodeBinary(Canonical.Scope.BODY));
            assertThat(library.contentIdsForDatum(body.datumId())).containsExactly(expectedContentId);
        }

        @Test
        @DisplayName("fetchDatum returns the Datum after put")
        void fetchDatumAfterPut() {
            Body body = Body.of(
                    ItemRef.of(ItemID.fromString("cg.predicate:authored")),
                    List.of(Binding.ref(ThematicRole.Theme.IID, ItemID.fromString("book")))
            );
            library.put(body);

            // Library returns domain objects, not bytes. The Datum round-trips
            // through wire-form storage internally; we just see it as an object.
            Optional<dev.everydaythings.graph.datum.Datum> fetched =
                    library.fetchDatum(body.datumId());
            assertThat(fetched).isPresent();
            assertThat(fetched.get().datumId()).isEqualTo(body.datumId());
            // The byte-store realization is still discoverable for tests that
            // care about the wire form.
            assertThat(library.contentIdsForDatum(body.datumId()))
                    .containsExactly(ContentID.of(body.encodeBinary(Canonical.Scope.BODY)));
        }

        @Test
        @DisplayName("Multiple Datums with different DatumIDs don't cross-pollinate")
        void differentDatumIdsAreIsolated() {
            Body bodyA = Body.of(
                    ItemRef.of(ItemID.fromString("cg.predicate:authored")),
                    List.of(Binding.ref(ThematicRole.Theme.IID, ItemID.fromString("bookA")))
            );
            Body bodyB = Body.of(
                    ItemRef.of(ItemID.fromString("cg.predicate:authored")),
                    List.of(Binding.ref(ThematicRole.Theme.IID, ItemID.fromString("bookB")))
            );

            library.put(bodyA);
            library.put(bodyB);

            ContentID expectedA = ContentID.of(bodyA.encodeBinary(Canonical.Scope.BODY));
            ContentID expectedB = ContentID.of(bodyB.encodeBinary(Canonical.Scope.BODY));
            assertThat(library.contentIdsForDatum(bodyA.datumId())).containsExactly(expectedA);
            assertThat(library.contentIdsForDatum(bodyB.datumId())).containsExactly(expectedB);
            assertThat(bodyA.datumId()).isNotEqualTo(bodyB.datumId());
        }

        @Test
        @DisplayName("Records are indexed by DatumID too (Records are Datums)")
        void recordsIndexedByDatumId() {
            Body body = Body.of(
                    ItemRef.of(ItemID.fromString("cg.predicate:status")),
                    List.of()
            );
            DatumID bodyCid = library.put(body);

            // Construct and persist a Record over the body (any byte signature suffices for indexing).
            Record record = Record.of(FrameRef.of(bodyCid), List.of(), new byte[]{1, 2, 3});
            library.put(record);

            ContentID expectedRecordCid = ContentID.of(record.encodeBinary(Canonical.Scope.BODY));
            assertThat(library.contentIdsForDatum(record.datumId())).containsExactly(expectedRecordCid);
        }

        @Test
        @DisplayName("delete removes the DATUM_INDEX entry")
        void deleteRemovesRefIndex() {
            Body body = Body.of(
                    ItemRef.of(ItemID.fromString("cg.predicate:authored")),
                    List.of(Binding.ref(ThematicRole.Theme.IID, ItemID.fromString("book")))
            );
            library.put(body);

            ContentID expectedCid = ContentID.of(body.encodeBinary(Canonical.Scope.BODY));
            assertThat(library.contentIdsForDatum(body.datumId())).containsExactly(expectedCid);

            library.deleteContent(expectedCid);

            assertThat(library.contentIdsForDatum(body.datumId())).isEmpty();
        }
    }
}
