package dev.everydaythings.graph.datum;

import dev.everydaythings.graph.encoding.CgCbor;

import com.upokecenter.cbor.CBORObject;
import dev.everydaythings.graph.canonical.HashTree;
import dev.everydaythings.graph.cryptography.algorithm.Signing;
import dev.everydaythings.graph.cryptography.VarSig;
import dev.everydaythings.graph.item.Manifest;
import dev.everydaythings.graph.runtime.RuntimeVocabulary;
import dev.everydaythings.graph.ref.CompoundKey;
import dev.everydaythings.graph.ref.ContentRef;
import dev.everydaythings.graph.ref.DatumRef;
import dev.everydaythings.graph.ref.ItemRef;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DatumTest {

    static final ItemRef PRED   = ItemRef.fromString("cg.predicate:authored");
    static final ItemRef DOC    = ItemRef.fromString("cg.archetype:document");
    static final ItemRef THEME  = ItemRef.fromString("cg.role:theme");
    static final ItemRef AGENT  = ItemRef.fromString("cg.role:agent");
    static final ItemRef TOLKIEN = ItemRef.fromString("person.tolkien");
    static final ItemRef HOBBIT = ItemRef.fromString("book.hobbit");

    @Nested
    @DisplayName("Body")
    class BodyTest {

        @Test
        @DisplayName("simple body construction and CID computation")
        void simpleBody() {
            Body body = Body.of(ItemRef.of(PRED), List.of(
                    Binding.ref(AGENT, TOLKIEN),
                    Binding.ref(THEME, HOBBIT)
            ));

            assertThat(body.head()).isEqualTo(ItemRef.of(PRED));
            assertThat(body.bindings()).hasSize(2);
            assertThat(ContentRef.of(CgCbor.codec().encode(body))).isNotNull();
        }

        @Test
        @DisplayName("CBOR round-trip preserves content")
        void cborRoundTrip() {
            Body original = Body.of(ItemRef.of(PRED), List.of(
                    Binding.ref(AGENT, TOLKIEN),
                    Binding.ref(THEME, HOBBIT)
            ));

            CBORObject cbor = CgCbor.toCbor(original);
            Body decoded = CgCbor.decodeBody(cbor);

            assertThat(decoded).isEqualTo(original);
            assertThat(ContentRef.of(CgCbor.codec().encode(decoded)))
                    .isEqualTo(ContentRef.of(CgCbor.codec().encode(original)));
        }

        @Test
        @DisplayName("CBOR encoding is a 2-element array")
        void cborTwoElement() {
            Body body = Body.of(ItemRef.of(PRED), List.of(Binding.ref(THEME, HOBBIT)));
            CBORObject cbor = CgCbor.toCbor(body);
            assertThat(cbor.getType()).isEqualTo(com.upokecenter.cbor.CBORType.Array);
            assertThat(cbor.size()).isEqualTo(2);
        }

        @Test
        @DisplayName("decode rejects 3-element array")
        void rejectsRecordShape() {
            CBORObject record = CBORObject.NewArray();
            record.Add(CgCbor.toCbor(ItemRef.of(PRED)));
            record.Add(CBORObject.NewArray());
            record.Add(CBORObject.FromByteArray(new byte[]{0x01}));

            assertThatThrownBy(() -> CgCbor.decodeBody(record))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("decode rejects non-ItemRef head")
        void rejectsNonItemRefHead() {
            CBORObject body = CBORObject.NewArray();
            body.Add(CgCbor.toCbor(DatumRef.of(DatumRef.of("x".getBytes()))));
            body.Add(CBORObject.NewArray());

            assertThatThrownBy(() -> CgCbor.decodeBody(body))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("equal bodies produce equal CIDs")
        void equalBodiesEqualCids() {
            Body a = Body.of(ItemRef.of(PRED), List.of(Binding.ref(THEME, HOBBIT)));
            Body b = Body.of(ItemRef.of(PRED), List.of(Binding.ref(THEME, HOBBIT)));
            assertThat(ContentRef.of(CgCbor.codec().encode(a)))
                    .isEqualTo(ContentRef.of(CgCbor.codec().encode(b)));
        }

        @Test
        @DisplayName("datumId is deterministic, non-null, and distinct from contentId")
        void datumIdBasics() {
            Body a = Body.of(ItemRef.of(PRED), List.of(Binding.ref(THEME, HOBBIT)));
            Body b = Body.of(ItemRef.of(PRED), List.of(Binding.ref(THEME, HOBBIT)));

            // Existence + determinism: equal bodies → equal DatumIDs
            assertThat(a.datumId()).isNotNull();
            assertThat(a.datumId()).isEqualTo(b.datumId());

            // Different bodies → different DatumIDs
            Body c = Body.of(ItemRef.of(PRED), List.of(Binding.ref(AGENT, TOLKIEN)));
            assertThat(a.datumId()).isNotEqualTo(c.datumId());

            // DatumRef (Merkle structural hash) is computed differently from
            // ContentRef (canonical bytes hash); they should not coincide.
            ContentRef aContentId = ContentRef.of(CgCbor.codec().encode(a));
            assertThat(a.datumId().encodeBinary()).isNotEqualTo(aContentId.encodeBinary());
        }

        @Test
        @DisplayName("Opaque.Redacted preserves the binding's merkle contribution")
        void redactionPreservesMerkleRoot() {
            // Original body with an IID target.
            Object original = HOBBIT;

            // Compute what the original target contributes to the Merkle hash.
            byte[] originalContribution = HashTree.hashOf(
                    original, HashTree.DEFAULT_DIGEST);

            // An Opaque.Redacted wrapping that same hash should contribute identically.
            Object redacted = new Opaque.Redacted(originalContribution);
            assertThat(HashTree.hashOf(
                    redacted, HashTree.DEFAULT_DIGEST))
                    .isEqualTo(originalContribution);

            // Construct a body with the original target, and another with the
            // redaction marker in its place. Their DatumIDs should match —
            // the structural Merkle hash is unchanged because the redacted
            // target's merkleHash() short-circuits to the same contribution.
            Body full = Body.of(ItemRef.of(PRED), List.of(new Binding(THEME, original)));
            Body redactedBody = Body.of(ItemRef.of(PRED), List.of(new Binding(THEME, redacted)));

            assertThat(full.datumId()).isEqualTo(redactedBody.datumId());

            // But their canonical bytes differ — the wire forms are different.
            assertThat(ContentRef.of(CgCbor.codec().encode(full)))
                    .isNotEqualTo(ContentRef.of(CgCbor.codec().encode(redactedBody)));
        }

        @Test
        @DisplayName("permissive — accepts any bindings")
        void permissive() {
            ItemRef arbitraryRole = ItemRef.fromString("any.role");
            Body body = Body.of(ItemRef.of(PRED), List.of(Binding.ref(arbitraryRole, HOBBIT)));
            assertThat(body.bindings()).hasSize(1);
        }

        @Test
        @DisplayName("binding lookup by CompoundKey")
        void bindingLookup() {
            Body body = Body.of(ItemRef.of(PRED), List.of(
                    Binding.ref(AGENT, TOLKIEN),
                    Binding.ref(THEME, HOBBIT)
            ));

            assertThat(body.binding(CompoundKey.of(AGENT))).isPresent();
            assertThat(body.binding(CompoundKey.of(THEME))).isPresent();
            assertThat(body.binding(CompoundKey.of(ItemRef.fromString("nonexistent")))).isEmpty();
        }
    }

    @Nested
    @DisplayName("Record")
    class RecordTest {

        @Test
        @DisplayName("simple record construction")
        void simpleRecord() {
            Body body = Body.of(ItemRef.of(PRED), List.of(Binding.ref(THEME, HOBBIT)));
            DatumRef bodyId = body.datumId();

            byte[] sig = new byte[64];
            for (int i = 0; i < 64; i++) sig[i] = (byte) i;
            VarSig varsig = VarSig.of((int) Signing.Ed25519.VARSIG_CODE, sig);

            Record record = Record.of(DatumRef.of(bodyId), List.of(), varsig);

            assertThat(record.head()).isEqualTo(DatumRef.of(bodyId));
            assertThat(record.varsig().code()).isEqualTo((int) Signing.Ed25519.VARSIG_CODE);
        }

        @Test
        @DisplayName("CBOR round-trip preserves content")
        void cborRoundTrip() {
            DatumRef bodyId = DatumRef.of("body".getBytes());
            byte[] sig = new byte[64];
            VarSig varsig = VarSig.of((int) Signing.Ed25519.VARSIG_CODE, sig);

            Record original = Record.of(DatumRef.of(bodyId), List.of(), varsig);
            CBORObject cbor = CgCbor.toCbor(original);
            Record decoded = CgCbor.decodeRecord(cbor);

            assertThat(decoded).isEqualTo(original);
        }

        @Test
        @DisplayName("CBOR encoding is a 3-element array")
        void cborThreeElement() {
            DatumRef bodyId = DatumRef.of("body".getBytes());
            byte[] sig = new byte[64];
            Record record = Record.of(DatumRef.of(bodyId), List.of(),
                    VarSig.of((int) Signing.Ed25519.VARSIG_CODE, sig));

            CBORObject cbor = CgCbor.toCbor(record);
            assertThat(cbor.getType()).isEqualTo(com.upokecenter.cbor.CBORType.Array);
            assertThat(cbor.size()).isEqualTo(3);
        }

        @Test
        @DisplayName("decode rejects 2-element array")
        void rejectsBodyShape() {
            CBORObject body = CBORObject.NewArray();
            body.Add(CgCbor.toCbor(DatumRef.of(DatumRef.of("x".getBytes()))));
            body.Add(CBORObject.NewArray());

            assertThatThrownBy(() -> CgCbor.decodeRecord(body))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("decode rejects non-FrameRef head")
        void rejectsNonFrameRefHead() {
            CBORObject record = CBORObject.NewArray();
            record.Add(CgCbor.toCbor(ItemRef.of(PRED)));
            record.Add(CBORObject.NewArray());
            record.Add(CBORObject.FromByteArray(new byte[]{0x01}));

            assertThatThrownBy(() -> CgCbor.decodeRecord(record))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("signingPayload is the structural Merkle root (independent of signature)")
        void signingPayloadIsMerkleRoot() {
            DatumRef bodyId = DatumRef.of("body".getBytes());
            byte[] sig = new byte[64];
            Record record = Record.of(DatumRef.of(bodyId), List.of(),
                    VarSig.of((int) Signing.Ed25519.VARSIG_CODE, sig));

            // signingPayload returns the Merkle root — not the encoded body.
            // Same record with a different signature should have the same payload.
            byte[] payload = HashTree.signingPayload(record);
            byte[] differentSig = new byte[64];
            differentSig[0] = 1;
            Record other = Record.of(DatumRef.of(bodyId), List.of(),
                    VarSig.of((int) Signing.Ed25519.VARSIG_CODE, differentSig));
            assertThat(HashTree.signingPayload(other)).isEqualTo(payload);
        }

        @Test
        @DisplayName("empty signature produces an unsigned record")
        void emptySignatureMeansUnsigned() {
            DatumRef bodyId = DatumRef.of("body".getBytes());
            Record record = Record.of(DatumRef.of(bodyId), List.of(), new byte[0]);
            assertThat(record.isSigned()).isFalse();
            assertThat(record.signature()).isEmpty();
        }

        @Test
        @DisplayName("Record.unsigned() builds the same shape as Record.of(.., new byte[0])")
        void unsignedFactoryMatchesEmptyByteForm() {
            DatumRef bodyId = DatumRef.of("body".getBytes());
            Record viaFactory = Record.unsigned(DatumRef.of(bodyId), List.of());
            Record viaEmptyBytes = Record.of(DatumRef.of(bodyId), List.of(), new byte[0]);
            assertThat(viaFactory).isEqualTo(viaEmptyBytes);
            assertThat(viaFactory.isSigned()).isFalse();
        }

        @Test
        @DisplayName("varsig() throws on an unsigned record")
        void varsigThrowsWhenUnsigned() {
            DatumRef bodyId = DatumRef.of("body".getBytes());
            Record unsigned = Record.unsigned(DatumRef.of(bodyId), List.of());
            assertThatThrownBy(unsigned::varsig)
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("unsigned");
        }
    }

    @Nested
    @DisplayName("Frame")
    class FrameTest {

        @Test
        @DisplayName("Frame wraps Body and Records")
        void wrap() {
            Body body = Body.of(ItemRef.of(PRED), List.of(Binding.ref(THEME, HOBBIT)));
            Frame frame = Frame.of(body);

            assertThat(frame.body()).isEqualTo(body);
            assertThat(frame.records()).isEmpty();
            // bodyCID assertion deleted with the method — ContentRef is encoder-specific
            // and now lives at the Library boundary, not on AttributedBody.
        }

        @Test
        @DisplayName("Frame.head returns body's head")
        void headDelegates() {
            Body body = Body.of(ItemRef.of(PRED), List.of());
            Frame frame = Frame.of(body);
            assertThat(frame.head()).isEqualTo(ItemRef.of(PRED));
        }

        @Test
        @DisplayName("Frame is not archetypal without ITEM_ID binding")
        void notArchetypal() {
            Body body = Body.of(ItemRef.of(PRED), List.of(Binding.ref(THEME, HOBBIT)));
            Frame frame = Frame.of(body);
            assertThat(frame.isArchetypal()).isFalse();
            assertThat(frame.asManifest()).isEmpty();
        }

        @Test
        @DisplayName("Frame binding lookup delegates to body")
        void bindingLookup() {
            Body body = Body.of(ItemRef.of(PRED), List.of(
                    Binding.ref(AGENT, TOLKIEN),
                    Binding.ref(THEME, HOBBIT)
            ));
            Frame frame = Frame.of(body);
            assertThat(frame.binding(CompoundKey.of(AGENT))).isPresent();
        }
    }

    @Nested
    @DisplayName("Manifest")
    class ManifestTest {

        @Test
        @DisplayName("Manifest construction requires ITEM_ID binding")
        void requiresItemId() {
            Body bodyWithoutItemId = Body.of(ItemRef.of(DOC), List.of());
            assertThatThrownBy(() -> Manifest.of(bodyWithoutItemId))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("Manifest with ITEM_ID binding succeeds")
        void withItemId() {
            ItemRef iid = ItemRef.fromString("specific-item");
            Body body = Body.of(ItemRef.of(DOC), List.of(
                    Binding.ref(Manifest.ITEM_ID, iid)
            ));
            Manifest manifest = Manifest.of(body);

            assertThat(manifest.itemId()).isEqualTo(iid);
            assertThat(manifest.versionId()).isEqualTo(body.datumId());
        }

        @Test
        @DisplayName("Manifest is archetypal")
        void isArchetypal() {
            ItemRef iid = ItemRef.fromString("specific-item");
            Body body = Body.of(ItemRef.of(DOC), List.of(
                    Binding.ref(Manifest.ITEM_ID, iid)
            ));
            Manifest manifest = Manifest.of(body);

            assertThat(manifest.isArchetypal()).isTrue();
            assertThat(manifest.asManifest()).contains(manifest);
        }

        @Test
        @DisplayName("Manifest parents read from FOLLOWS bindings")
        void parents() {
            ItemRef iid = ItemRef.fromString("specific-item");
            ItemRef parentVid1 = ItemRef.fromString("v1-as-iid");
            ItemRef parentVid2 = ItemRef.fromString("v2-as-iid");
            Body body = Body.of(ItemRef.of(DOC), List.of(
                    Binding.ref(Manifest.ITEM_ID, iid),
                    Binding.ref(Manifest.FOLLOWS, parentVid1),
                    Binding.ref(Manifest.FOLLOWS, parentVid2)
            ));
            Manifest manifest = Manifest.of(body);

            assertThat(manifest.parents()).hasSize(2);
        }

        @Test
        @DisplayName("Manifest endorses() returns endorsement bindings")
        void endorses() {
            ItemRef iid = ItemRef.fromString("specific-item");
            Body body = Body.of(ItemRef.of(DOC), List.of(
                    Binding.ref(Manifest.ITEM_ID, iid),
                    Binding.ref(Manifest.ENDORSES, ItemRef.fromString("frame-1")),
                    Binding.ref(Manifest.ENDORSES, ItemRef.fromString("frame-2"))
            ));
            Manifest manifest = Manifest.of(body);

            assertThat(manifest.endorses()).hasSize(2);
        }

        @Test
        @DisplayName("Manifest implementation is Optional")
        void implementationOptional() {
            ItemRef iid = ItemRef.fromString("specific-item");
            Body bodyNoImpl = Body.of(ItemRef.of(DOC), List.of(
                    Binding.ref(Manifest.ITEM_ID, iid)
            ));
            assertThat(Manifest.of(bodyNoImpl).implementation()).isEmpty();

            Body bodyWithImpl = Body.of(ItemRef.of(DOC), List.of(
                    Binding.ref(Manifest.ITEM_ID, iid),
                    Manifest.implementation(
                            ItemRef.iid(RuntimeVocabulary.Java.KEY),
                            ItemRef.iid(RuntimeVocabulary.ClassName.KEY),
                            "impl.Class")
            ));
            assertThat(Manifest.of(bodyWithImpl).implementation()).isPresent();
        }
    }
}
