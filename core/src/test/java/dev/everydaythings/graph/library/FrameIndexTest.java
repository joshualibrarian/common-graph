package dev.everydaythings.graph.library;

import dev.everydaythings.graph.frame.BindingTarget;
import dev.everydaythings.graph.frame.FrameBody;
import dev.everydaythings.graph.frame.FrameEntry;
import dev.everydaythings.graph.item.id.ContentID;
import dev.everydaythings.graph.item.id.FrameKey;
import dev.everydaythings.graph.item.id.ItemID;
import dev.everydaythings.graph.library.skiplist.SkipListLibraryIndex;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@Disabled("Store tests — refactoring later")
@DisplayName("Frame Index")
class FrameIndexTest {

    static final ItemID AUTHOR = ItemID.fromString("cg:pred/author");
    static final ItemID TITLE = ItemID.fromString("cg:pred/title");
    static final ItemID LIKES = ItemID.fromString("cg:pred/likes");
    static final ItemID THE_HOBBIT = ItemID.fromString("cg:book/the-hobbit");
    static final ItemID TOLKIEN = ItemID.fromString("cg:person/tolkien");
    static final ItemID ALICE = ItemID.fromString("cg:user/alice");
    static final ItemID BOB = ItemID.fromString("cg:user/bob");
    static final ItemID TARGET_ROLE = ItemID.fromString("cg.role:target");
    static final ItemID THEME_ROLE = ItemID.fromString("cg.role:theme");

    SkipListLibraryIndex index;

    @BeforeEach
    void setup() {
        index = SkipListLibraryIndex.create();
    }

    @Nested
    @DisplayName("indexFrame")
    class IndexFrame {

        @Test
        @DisplayName("index by predicate")
        void indexByPredicate() {
            FrameBody body = FrameBody.of(AUTHOR, THE_HOBBIT,
                    Map.of(TARGET_ROLE, BindingTarget.iid(TOLKIEN)));
            ContentID bodyHash = body.hash();
            ContentID storageCid = ContentID.of(new byte[]{1, 2, 3});

            index.runInWriteTransaction(tx ->
                    index.indexFrame(AUTHOR, body.bindings(), bodyHash, storageCid, tx));

            List<LibraryIndex.FrameRef> refs = index.framesByPredicate(AUTHOR).toList();
            assertThat(refs).hasSize(1);
            assertThat(refs.getFirst().bodyHash()).isEqualTo(bodyHash);
            assertThat(refs.getFirst().storageCid()).isEqualTo(storageCid);
        }

        @Test
        @DisplayName("index by participating item")
        void indexByItem() {
            FrameBody body = FrameBody.of(AUTHOR, THE_HOBBIT,
                    Map.of(TARGET_ROLE, BindingTarget.iid(TOLKIEN)));
            ContentID bodyHash = body.hash();
            ContentID storageCid = ContentID.of(new byte[]{1, 2, 3});

            index.runInWriteTransaction(tx ->
                    index.indexFrame(AUTHOR, body.bindings(), bodyHash, storageCid, tx));

            // Should be findable via Tolkien (TARGET binding)
            List<LibraryIndex.FrameRef> refs = index.framesByItem(TOLKIEN).toList();
            assertThat(refs).hasSize(1);
            assertThat(refs.getFirst().bodyHash()).isEqualTo(bodyHash);
        }

        @Test
        @DisplayName("index by item and predicate")
        void indexByItemPredicate() {
            FrameBody body = FrameBody.of(AUTHOR, THE_HOBBIT,
                    Map.of(TARGET_ROLE, BindingTarget.iid(TOLKIEN)));
            ContentID bodyHash = body.hash();
            ContentID storageCid = ContentID.of(new byte[]{1, 2, 3});

            index.runInWriteTransaction(tx ->
                    index.indexFrame(AUTHOR, body.bindings(), bodyHash, storageCid, tx));

            List<LibraryIndex.FrameRef> found = index.framesByItemPredicate(TOLKIEN, AUTHOR).toList();
            assertThat(found).hasSize(1);

            // Different predicate should return empty
            List<LibraryIndex.FrameRef> notFound = index.framesByItemPredicate(TOLKIEN, TITLE).toList();
            assertThat(notFound).isEmpty();
        }

        @Test
        @DisplayName("multiple frames indexed")
        void multipleFrames() {
            FrameBody body1 = FrameBody.of(AUTHOR, THE_HOBBIT,
                    Map.of(TARGET_ROLE, BindingTarget.iid(TOLKIEN)));
            FrameBody body2 = FrameBody.of(LIKES, THE_HOBBIT,
                    Map.of(TARGET_ROLE, BindingTarget.iid(ALICE)));
            ContentID cid1 = ContentID.of(new byte[]{1});
            ContentID cid2 = ContentID.of(new byte[]{2});

            index.runInWriteTransaction(tx -> {
                index.indexFrame(AUTHOR, body1.bindings(), body1.hash(), cid1, tx);
                index.indexFrame(LIKES, body2.bindings(), body2.hash(), cid2, tx);
            });

            // Both involve THE_HOBBIT (via bindings), but through different roles
            // Let's verify by predicate
            assertThat(index.framesByPredicate(AUTHOR).toList()).hasSize(1);
            assertThat(index.framesByPredicate(LIKES).toList()).hasSize(1);
        }
    }

    @Nested
    @DisplayName("indexFrameBody")
    class IndexFrameBody {

        @Test
        @DisplayName("frame body indexed via frame index")
        void frameBodyIndexed() {
            FrameBody body = FrameBody.of(AUTHOR, THE_HOBBIT,
                    Map.of(TARGET_ROLE, BindingTarget.iid(TOLKIEN)));
            ContentID bodyHash = body.hash();
            ContentID storageCid = ContentID.of(body.bodyBytes());

            index.runInWriteTransaction(tx ->
                    index.indexFrame(AUTHOR, body.bindings(), bodyHash, storageCid, tx));

            // Findable by item bindings and predicate
            assertThat(index.framesByItem(TOLKIEN).toList()).hasSize(1);
            assertThat(index.framesByPredicate(AUTHOR).toList()).hasSize(1);
        }

        @Test
        @DisplayName("framesByItem finds indexed frame body")
        void framesByItemFindsBody() {
            FrameBody body = FrameBody.of(AUTHOR, THE_HOBBIT,
                    Map.of(TARGET_ROLE, BindingTarget.iid(TOLKIEN)));
            ContentID bodyHash = body.hash();
            ContentID storageCid = ContentID.of(body.bodyBytes());

            index.runInWriteTransaction(tx ->
                    index.indexFrame(AUTHOR, body.bindings(), bodyHash, storageCid, tx));

            List<LibraryIndex.FrameRef> refs = index.framesByItem(TOLKIEN).toList();
            assertThat(refs).hasSize(1);
            assertThat(refs.getFirst().storageCid()).isEqualTo(storageCid);
        }
    }

    @Nested
    @DisplayName("RECORD_BY_BODY")
    class RecordByBody {

        @Test
        @DisplayName("index and query records by body hash")
        void indexAndQuery() {
            FrameBody body = FrameBody.of(AUTHOR, THE_HOBBIT,
                    Map.of(TARGET_ROLE, BindingTarget.iid(TOLKIEN)));
            ContentID bodyHash = body.hash();

            ContentID aliceKeyId = ContentID.of(new byte[]{1, 1, 1});
            ContentID bobKeyId = ContentID.of(new byte[]{2, 2, 2});
            ContentID aliceCid = ContentID.of(new byte[]{10});
            ContentID bobCid = ContentID.of(new byte[]{20});

            index.runInWriteTransaction(tx -> {
                index.indexRecord(bodyHash, aliceKeyId, aliceCid, tx);
                index.indexRecord(bodyHash, bobKeyId, bobCid, tx);
            });

            List<LibraryIndex.RecordRef> records = index.recordsByBody(bodyHash).toList();
            assertThat(records).hasSize(2);
        }

        @Test
        @DisplayName("attestation count")
        void attestationCount() {
            FrameBody body = FrameBody.of(LIKES, THE_HOBBIT,
                    Map.of(TARGET_ROLE, BindingTarget.iid(ALICE)));
            ContentID bodyHash = body.hash();

            ContentID key1 = ContentID.of(new byte[]{1, 1});
            ContentID key2 = ContentID.of(new byte[]{2, 2});
            ContentID key3 = ContentID.of(new byte[]{3, 3});

            index.runInWriteTransaction(tx -> {
                index.indexRecord(bodyHash, key1, ContentID.of(new byte[]{1}), tx);
                index.indexRecord(bodyHash, key2, ContentID.of(new byte[]{2}), tx);
                index.indexRecord(bodyHash, key3, ContentID.of(new byte[]{3}), tx);
            });

            assertThat(index.attestationCount(bodyHash)).isEqualTo(3);
        }

        @Test
        @DisplayName("different bodies have independent records")
        void independentBodies() {
            FrameBody body1 = FrameBody.of(LIKES, THE_HOBBIT,
                    Map.of(TARGET_ROLE, BindingTarget.iid(ALICE)));
            FrameBody body2 = FrameBody.of(LIKES, THE_HOBBIT,
                    Map.of(TARGET_ROLE, BindingTarget.iid(BOB)));

            ContentID signerKey = ContentID.of(new byte[]{1, 1, 1});

            index.runInWriteTransaction(tx -> {
                index.indexRecord(body1.hash(), signerKey, ContentID.of(new byte[]{1}), tx);
                index.indexRecord(body2.hash(), signerKey, ContentID.of(new byte[]{2}), tx);
            });

            assertThat(index.attestationCount(body1.hash())).isEqualTo(1);
            assertThat(index.attestationCount(body2.hash())).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("indexEndorsedFrame")
    class IndexEndorsedFrame {

        @Test
        @DisplayName("endorsed frame with semantic key is indexed")
        void semanticKeyIndexed() {
            ContentID snapshotCid = ContentID.of(new byte[]{42});
            ContentID bodyHash = ContentID.of(new byte[]{99});

            FrameEntry entry = FrameEntry.builder()
                    .frameKey(FrameKey.literal("title"))
                    .type(ItemID.fromString("cg:type/text"))
                    .identity(true)
                    .bodyHash(bodyHash)
                    .payload(FrameEntry.EntryPayload.builder().snapshotCid(snapshotCid).build())
                    .frameKey(FrameKey.of(TITLE))
                    .build();

            index.runInWriteTransaction(tx ->
                    index.indexEndorsedFrame(THE_HOBBIT, entry, tx));

            // Findable by predicate
            List<LibraryIndex.FrameRef> refs = index.framesByPredicate(TITLE).toList();
            assertThat(refs).hasSize(1);
            assertThat(refs.getFirst().bodyHash()).isEqualTo(bodyHash);
            assertThat(refs.getFirst().storageCid()).isEqualTo(snapshotCid);

            // Owner is indexed as participant
            assertThat(index.framesByItem(THE_HOBBIT).toList()).hasSize(1);
        }

        @Test
        @DisplayName("endorsed frame with literal key is not indexed")
        void literalKeyNotIndexed() {
            ContentID snapshotCid = ContentID.of(new byte[]{42});

            FrameEntry entry = FrameEntry.builder()
                    .frameKey(FrameKey.literal("vault"))
                    .type(ItemID.fromString("cg:type/vault"))
                    .identity(false)
                    .payload(FrameEntry.EntryPayload.builder().snapshotCid(snapshotCid).build())
                    .build();

            index.runInWriteTransaction(tx ->
                    index.indexEndorsedFrame(THE_HOBBIT, entry, tx));

            // Literal-keyed frames are not indexed (item-internal)
            assertThat(index.framesByItem(THE_HOBBIT).toList()).isEmpty();
        }

        @Test
        @DisplayName("endorsed frame with no content is not indexed")
        void noContentNotIndexed() {
            FrameEntry entry = FrameEntry.builder()
                    .frameKey(FrameKey.literal("vault"))
                    .type(ItemID.fromString("cg:type/vault"))
                    .identity(false)
                    .build();

            index.runInWriteTransaction(tx ->
                    index.indexEndorsedFrame(THE_HOBBIT, entry, tx));

            assertThat(index.framesByItem(THE_HOBBIT).toList()).isEmpty();
        }

        @Test
        @DisplayName("reference frame indexes target item")
        void referenceIndexed() {
            ContentID bodyHash = ContentID.of(new byte[]{77});

            FrameEntry entry = FrameEntry.builder()
                    .frameKey(FrameKey.literal("author"))
                    .type(ItemID.fromString("cg:type/person"))
                    .identity(false)
                    .bodyHash(bodyHash)
                    .payload(FrameEntry.EntryPayload.builder().referenceTarget(TOLKIEN).build())
                    .frameKey(FrameKey.of(AUTHOR))
                    .build();

            index.runInWriteTransaction(tx ->
                    index.indexEndorsedFrame(THE_HOBBIT, entry, tx));

            // Both owner and reference target are indexed
            assertThat(index.framesByItem(THE_HOBBIT).toList()).hasSize(1);
            assertThat(index.framesByItem(TOLKIEN).toList()).hasSize(1);
        }
    }
}
