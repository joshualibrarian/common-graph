package dev.everydaythings.graph.frame;

import dev.everydaythings.graph.item.id.ContentID;
import dev.everydaythings.graph.item.id.CompoundKey;
import dev.everydaythings.graph.item.id.ItemID;
import dev.everydaythings.graph.runtime.LibrarianOld;
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
class FrameOldRecordOldTest {

    static final ItemID TITLE = ItemID.fromString("cg:pred/title");
    static final ItemID AUTHOR = ItemID.fromString("cg:pred/author");
    static final ItemID THE_HOBBIT = ItemID.fromString("cg:book/the-hobbit");
    static final ItemID TOLKIEN = ItemID.fromString("cg:person/tolkien");
    static final ItemID GOAL_ROLE = ItemID.fromString("cg.role:goal");

    static LibrarianOld signer;
    static LibrarianOld signer2;

    @BeforeAll
    static void setup() {
        signer = LibrarianOld.createInMemory();
        signer2 = LibrarianOld.createInMemory();
    }

    @Nested
    @DisplayName("Construction")
    class Construction {

        @Test
        @DisplayName("create signed record")
        void createSigned() {
            FrameBodyOld body = FrameBodyOld.of(TITLE, THE_HOBBIT);
            FrameRecordOld record = FrameRecordOld.create(body, signer);

            assertThat(record.bodyHash()).isEqualTo(body.hash());
            assertThat(record.signer()).isNotNull();
            assertThat(record.timestamp()).isNotNull();
            assertThat(record.isSigned()).isTrue();
            assertThat(record.signing()).isNotNull();
        }

        @Test
        @DisplayName("create unsigned record")
        void createUnsigned() {
            FrameBodyOld body = FrameBodyOld.of(TITLE, THE_HOBBIT);
            FrameRecordOld record = FrameRecordOld.unsigned(body, signer.publicKey());

            assertThat(record.bodyHash()).isEqualTo(body.hash());
            assertThat(record.signer()).isNotNull();
            assertThat(record.isSigned()).isFalse();
        }

        @Test
        @DisplayName("null body hash rejected")
        void nullBodyHash() {
            assertThatThrownBy(() -> new FrameRecordOld(null, signer.publicKey(), Instant.now()))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("null signer rejected")
        void nullSigner() {
            FrameBodyOld body = FrameBodyOld.of(TITLE, THE_HOBBIT);
            assertThatThrownBy(() -> new FrameRecordOld(body.hash(), null, Instant.now()))
                    .isInstanceOf(NullPointerException.class);
        }
    }

    @Nested
    @DisplayName("Identity")
    class Identity {

        @Test
        @DisplayName("record CID is deterministic")
        void deterministicCid() {
            FrameBodyOld body = FrameBodyOld.of(TITLE, THE_HOBBIT);
            Instant now = Instant.now();

            FrameRecordOld a = new FrameRecordOld(body.hash(), signer.publicKey(), now);
            FrameRecordOld b = new FrameRecordOld(body.hash(), signer.publicKey(), now);

            assertThat(a.recordCid()).isEqualTo(b.recordCid());
        }

        @Test
        @DisplayName("same body, different signers = different record CIDs")
        void differentSigners() {
            FrameBodyOld body = FrameBodyOld.of(TITLE, THE_HOBBIT);
            Instant now = Instant.now();

            FrameRecordOld a = new FrameRecordOld(body.hash(), signer.publicKey(), now);
            FrameRecordOld b = new FrameRecordOld(body.hash(), signer2.publicKey(), now);

            // Same body hash but different signers → different record CIDs
            assertThat(a.bodyHash()).isEqualTo(b.bodyHash());
            assertThat(a.recordCid()).isNotEqualTo(b.recordCid());
        }

        @Test
        @DisplayName("record CID is a ContentID")
        void cidIsContentID() {
            FrameBodyOld body = FrameBodyOld.of(TITLE, THE_HOBBIT);
            FrameRecordOld record = FrameRecordOld.create(body, signer);
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
            FrameBodyOld body = FrameBodyOld.of(TITLE, THE_HOBBIT);
            FrameRecordOld record = FrameRecordOld.unsigned(body, signer.publicKey());
            assertThat(record.isSigned()).isFalse();

            record.sign(signer);
            assertThat(record.isSigned()).isTrue();
            assertThat(record.signing()).isNotNull();
        }

        @Test
        @DisplayName("signed record has valid target")
        void signedRecordTarget() {
            FrameBodyOld body = FrameBodyOld.of(TITLE, THE_HOBBIT);
            FrameRecordOld record = FrameRecordOld.create(body, signer);

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
            // Both signers assert the same fact
            FrameBodyOld body = FrameBodyOld.of(AUTHOR, THE_HOBBIT,
                    Map.of(GOAL_ROLE, BindingTarget.iid(TOLKIEN)));

            FrameRecordOld aliceRecord = FrameRecordOld.create(body, signer);
            FrameRecordOld bobRecord = FrameRecordOld.create(body, signer2);

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
    @DisplayName("Frame Integration")
    class FrameOldIntegration {

        @Test
        @DisplayName("body hash stored on Frame")
        void bodyHashOnFrame() {
            FrameBodyOld body = FrameBodyOld.of(TITLE, THE_HOBBIT);
            ContentID hash = body.hash();

            FrameOld frame = new FrameOld(
                    CompoundKey.of(ItemID.fromString("cg.test:title")),
                    ItemID.fromString("cg.sememe:text"),
                    body, hash, true);

            assertThat(frame.bodyHash()).isEqualTo(hash);
        }

        @Test
        @DisplayName("body hash null when not set")
        void bodyHashNull() {
            FrameOld frame = FrameOld.snapshot(
                    CompoundKey.of(ItemID.fromString("cg.test:vault")),
                    ItemID.fromString("cg.sememe:vault"), null, false);

            assertThat(frame.bodyHash()).isNull();
        }
    }
}
