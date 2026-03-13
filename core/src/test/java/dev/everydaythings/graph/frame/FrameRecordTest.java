package dev.everydaythings.graph.frame;

import dev.everydaythings.graph.Canonical;
import dev.everydaythings.graph.item.id.ContentID;
import dev.everydaythings.graph.item.id.ItemID;
import dev.everydaythings.graph.runtime.Librarian;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Disabled("Slow — multi-librarian attestation tests")
@DisplayName("FrameRecord")
class FrameRecordTest {

    static final ItemID TITLE = ItemID.fromString("cg:pred/title");
    static final ItemID AUTHOR = ItemID.fromString("cg:pred/author");
    static final ItemID THE_HOBBIT = ItemID.fromString("cg:book/the-hobbit");
    static final ItemID TOLKIEN = ItemID.fromString("cg:person/tolkien");
    static final ItemID TARGET_ROLE = ItemID.fromString("cg.role:target");

    static Librarian signer;

    @BeforeAll
    static void setup() {
        signer = Librarian.createInMemory();
    }

    @Nested
    @DisplayName("Construction")
    class Construction {

        @Test
        @DisplayName("create signed record")
        void createSigned() {
            FrameBody body = FrameBody.of(TITLE, THE_HOBBIT);
            FrameRecord record = FrameRecord.create(body, signer);

            assertThat(record.bodyHash()).isEqualTo(body.hash());
            assertThat(record.signer()).isNotNull();
            assertThat(record.timestamp()).isNotNull();
            assertThat(record.isSigned()).isTrue();
            assertThat(record.signing()).isNotNull();
        }

        @Test
        @DisplayName("create unsigned record")
        void createUnsigned() {
            FrameBody body = FrameBody.of(TITLE, THE_HOBBIT);
            FrameRecord record = FrameRecord.unsigned(body, signer.publicKey());

            assertThat(record.bodyHash()).isEqualTo(body.hash());
            assertThat(record.signer()).isNotNull();
            assertThat(record.isSigned()).isFalse();
        }

        @Test
        @DisplayName("null body hash rejected")
        void nullBodyHash() {
            assertThatThrownBy(() -> new FrameRecord(null, signer.publicKey(), Instant.now()))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("null signer rejected")
        void nullSigner() {
            FrameBody body = FrameBody.of(TITLE, THE_HOBBIT);
            assertThatThrownBy(() -> new FrameRecord(body.hash(), null, Instant.now()))
                    .isInstanceOf(NullPointerException.class);
        }
    }

    @Nested
    @DisplayName("Identity")
    class Identity {

        @Test
        @DisplayName("record CID is deterministic")
        void deterministicCid() {
            FrameBody body = FrameBody.of(TITLE, THE_HOBBIT);
            Instant now = Instant.now();

            FrameRecord a = new FrameRecord(body.hash(), signer.publicKey(), now);
            FrameRecord b = new FrameRecord(body.hash(), signer.publicKey(), now);

            assertThat(a.recordCid()).isEqualTo(b.recordCid());
        }

        @Test
        @DisplayName("same body, different signers = different record CIDs")
        void differentSigners() {
            FrameBody body = FrameBody.of(TITLE, THE_HOBBIT);
            Librarian signer2 = Librarian.createInMemory();
            Instant now = Instant.now();

            FrameRecord a = new FrameRecord(body.hash(), signer.publicKey(), now);
            FrameRecord b = new FrameRecord(body.hash(), signer2.publicKey(), now);

            // Same body hash but different signers → different record CIDs
            assertThat(a.bodyHash()).isEqualTo(b.bodyHash());
            assertThat(a.recordCid()).isNotEqualTo(b.recordCid());
        }

        @Test
        @DisplayName("record CID is a ContentID")
        void cidIsContentID() {
            FrameBody body = FrameBody.of(TITLE, THE_HOBBIT);
            FrameRecord record = FrameRecord.create(body, signer);
            ContentID cid = record.recordCid();
            assertThat(cid).isNotNull();
            assertThat(cid.encodeBinary()).isNotEmpty();
        }
    }

    @Nested
    @DisplayName("Signing")
    class SigningTests {

        @Test
        @DisplayName("sign populates signing field")
        void signPopulatesSigning() {
            FrameBody body = FrameBody.of(TITLE, THE_HOBBIT);
            FrameRecord record = FrameRecord.unsigned(body, signer.publicKey());
            assertThat(record.isSigned()).isFalse();

            record.sign(signer);
            assertThat(record.isSigned()).isTrue();
            assertThat(record.signing()).isNotNull();
        }

        @Test
        @DisplayName("signed record has valid target")
        void signedRecordTarget() {
            FrameBody body = FrameBody.of(TITLE, THE_HOBBIT);
            FrameRecord record = FrameRecord.create(body, signer);

            assertThat(record.targetId()).isEqualTo(record.recordCid());
            assertThat(record.bodyToSign()).isNotEmpty();
        }
    }

    @Nested
    @DisplayName("Multi-attestation")
    class MultiAttestation {

        @Test
        @DisplayName("same assertion from different signers shares body hash")
        void sharedBodyHash() {
            Librarian alice = Librarian.createInMemory();
            Librarian bob = Librarian.createInMemory();

            // Both assert the same fact
            FrameBody body = FrameBody.of(AUTHOR, THE_HOBBIT,
                    Map.of(TARGET_ROLE, BindingTarget.iid(TOLKIEN)));

            FrameRecord aliceRecord = FrameRecord.create(body, alice);
            FrameRecord bobRecord = FrameRecord.create(body, bob);

            // Same body hash
            assertThat(aliceRecord.bodyHash()).isEqualTo(bobRecord.bodyHash());

            // Different records (different signers)
            assertThat(aliceRecord.recordCid()).isNotEqualTo(bobRecord.recordCid());

            // Both signed
            assertThat(aliceRecord.isSigned()).isTrue();
            assertThat(bobRecord.isSigned()).isTrue();
        }
    }

    @Nested
    @DisplayName("FrameEntry Integration")
    class FrameEntryIntegration {

        @Test
        @DisplayName("body hash stored on FrameEntry")
        void bodyHashOnEntry() {
            FrameBody body = FrameBody.of(TITLE, THE_HOBBIT);
            ContentID hash = body.hash();

            FrameEntry entry = FrameEntry.builder()
                    .frameKey(dev.everydaythings.graph.item.id.FrameKey.literal("title"))
                    .type(ItemID.fromString("cg:type/text"))
                    .identity(true)
                    .bodyHash(hash)
                    .build();

            assertThat(entry.bodyHash()).isEqualTo(hash);
        }

        @Test
        @DisplayName("body hash null for legacy entries")
        void bodyHashNullForLegacy() {
            FrameEntry entry = FrameEntry.builder()
                    .frameKey(dev.everydaythings.graph.item.id.FrameKey.literal("vault"))
                    .type(ItemID.fromString("cg:type/vault"))
                    .identity(false)
                    .build();

            assertThat(entry.bodyHash()).isNull();
        }
    }
}
