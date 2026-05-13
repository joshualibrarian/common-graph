package dev.everydaythings.graph.datum;

import com.upokecenter.cbor.CBORObject;
import dev.everydaythings.graph.encoding.Canonical;
import dev.everydaythings.graph.encoding.HashTree;
import dev.everydaythings.graph.identity.Algorithm;
import dev.everydaythings.graph.identity.VarSig;
import dev.everydaythings.graph.item.Manifest;
import dev.everydaythings.graph.item.id.CompoundKey;
import dev.everydaythings.graph.item.id.ContentID;
import dev.everydaythings.graph.item.id.DatumID;
import dev.everydaythings.graph.item.id.FrameRef;
import dev.everydaythings.graph.item.id.ItemID;
import dev.everydaythings.graph.item.id.ItemRef;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DatumTest {

    static final ItemID PRED   = ItemID.fromString("cg.predicate:authored");
    static final ItemID DOC    = ItemID.fromString("cg.archetype:document");
    static final ItemID THEME  = ItemID.fromString("cg.role:theme");
    static final ItemID AGENT  = ItemID.fromString("cg.role:agent");
    static final ItemID TOLKIEN = ItemID.fromString("person.tolkien");
    static final ItemID HOBBIT = ItemID.fromString("book.hobbit");

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
            assertThat(ContentID.of(body.encodeBinary(Canonical.Scope.BODY))).isNotNull();
        }

        @Test
        @DisplayName("CBOR round-trip preserves content")
        void cborRoundTrip() {
            Body original = Body.of(ItemRef.of(PRED), List.of(
                    Binding.ref(AGENT, TOLKIEN),
                    Binding.ref(THEME, HOBBIT)
            ));

            CBORObject cbor = original.toCborTree(Canonical.Scope.BODY);
            Body decoded = Body.fromCborTree(cbor);

            assertThat(decoded).isEqualTo(original);
            assertThat(ContentID.of(decoded.encodeBinary(Canonical.Scope.BODY)))
                    .isEqualTo(ContentID.of(original.encodeBinary(Canonical.Scope.BODY)));
        }

        @Test
        @DisplayName("CBOR encoding is a 2-element array")
        void cborTwoElement() {
            Body body = Body.of(ItemRef.of(PRED), List.of(Binding.ref(THEME, HOBBIT)));
            CBORObject cbor = body.toCborTree(Canonical.Scope.BODY);
            assertThat(cbor.getType()).isEqualTo(com.upokecenter.cbor.CBORType.Array);
            assertThat(cbor.size()).isEqualTo(2);
        }

        @Test
        @DisplayName("decode rejects 3-element array")
        void rejectsRecordShape() {
            CBORObject record = CBORObject.NewArray();
            record.Add(ItemRef.of(PRED).toCborTree(Canonical.Scope.BODY));
            record.Add(CBORObject.NewArray());
            record.Add(CBORObject.FromByteArray(new byte[]{0x01}));

            assertThatThrownBy(() -> Body.fromCborTree(record))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("decode rejects non-ItemRef head")
        void rejectsNonItemRefHead() {
            CBORObject body = CBORObject.NewArray();
            body.Add(FrameRef.of(DatumID.of("x".getBytes())).toCborTree(Canonical.Scope.BODY));
            body.Add(CBORObject.NewArray());

            assertThatThrownBy(() -> Body.fromCborTree(body))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("equal bodies produce equal CIDs")
        void equalBodiesEqualCids() {
            Body a = Body.of(ItemRef.of(PRED), List.of(Binding.ref(THEME, HOBBIT)));
            Body b = Body.of(ItemRef.of(PRED), List.of(Binding.ref(THEME, HOBBIT)));
            assertThat(ContentID.of(a.encodeBinary(Canonical.Scope.BODY)))
                    .isEqualTo(ContentID.of(b.encodeBinary(Canonical.Scope.BODY)));
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

            // DatumID (Merkle structural hash) is computed differently from
            // ContentID (canonical bytes hash); they should not coincide.
            ContentID aContentId = ContentID.of(a.encodeBinary(Canonical.Scope.BODY));
            assertThat(a.datumId().encodeBinary()).isNotEqualTo(aContentId.encodeBinary());
        }

        @Test
        @DisplayName("RedactedTarget preserves the binding's merkle contribution")
        void redactionPreservesMerkleRoot() {
            // Original body with an IID target.
            BindingTarget original = BindingTarget.iid(HOBBIT);

            // Compute what the original target contributes to the Merkle hash.
            byte[] originalContribution = dev.everydaythings.graph.encoding.HashTree.hashOf(
                    original, dev.everydaythings.graph.encoding.HashTree.DEFAULT_DIGEST);

            // A RedactedTarget wrapping that same hash should contribute identically.
            BindingTarget redacted = new BindingTarget.RedactedTarget(originalContribution);
            assertThat(dev.everydaythings.graph.encoding.HashTree.hashOf(
                    redacted, dev.everydaythings.graph.encoding.HashTree.DEFAULT_DIGEST))
                    .isEqualTo(originalContribution);

            // Construct a body with the original target, and another with the
            // redaction marker in its place. Their DatumIDs should match —
            // the structural Merkle hash is unchanged because the redacted
            // target's merkleHash() short-circuits to the same contribution.
            Body full = Body.of(ItemRef.of(PRED), List.of(new Binding(THEME, original)));
            Body redactedBody = Body.of(ItemRef.of(PRED), List.of(new Binding(THEME, redacted)));

            assertThat(full.datumId()).isEqualTo(redactedBody.datumId());

            // But their canonical bytes differ — the wire forms are different.
            assertThat(ContentID.of(full.encodeBinary(Canonical.Scope.BODY)))
                    .isNotEqualTo(ContentID.of(redactedBody.encodeBinary(Canonical.Scope.BODY)));
        }

        @Test
        @DisplayName("permissive — accepts any bindings")
        void permissive() {
            ItemID arbitraryRole = ItemID.fromString("any.role");
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
            assertThat(body.binding(CompoundKey.of(ItemID.fromString("nonexistent")))).isEmpty();
        }
    }

    @Nested
    @DisplayName("Record")
    class RecordTest {

        @Test
        @DisplayName("simple record construction")
        void simpleRecord() {
            Body body = Body.of(ItemRef.of(PRED), List.of(Binding.ref(THEME, HOBBIT)));
            DatumID bodyId = body.datumId();

            byte[] sig = new byte[64];
            for (int i = 0; i < 64; i++) sig[i] = (byte) i;
            VarSig varsig = VarSig.of(Algorithm.Sign.ED25519, sig);

            Record record = Record.of(FrameRef.of(bodyId), List.of(), varsig);

            assertThat(record.head()).isEqualTo(FrameRef.of(bodyId));
            assertThat(record.varsig().algorithm()).isEqualTo(Algorithm.Sign.ED25519);
        }

        @Test
        @DisplayName("CBOR round-trip preserves content")
        void cborRoundTrip() {
            DatumID bodyId = DatumID.of("body".getBytes());
            byte[] sig = new byte[64];
            VarSig varsig = VarSig.of(Algorithm.Sign.ED25519, sig);

            Record original = Record.of(FrameRef.of(bodyId), List.of(), varsig);
            CBORObject cbor = original.toCborTree(Canonical.Scope.BODY);
            Record decoded = Record.fromCborTree(cbor);

            assertThat(decoded).isEqualTo(original);
        }

        @Test
        @DisplayName("CBOR encoding is a 3-element array")
        void cborThreeElement() {
            DatumID bodyId = DatumID.of("body".getBytes());
            byte[] sig = new byte[64];
            Record record = Record.of(FrameRef.of(bodyId), List.of(),
                    VarSig.of(Algorithm.Sign.ED25519, sig));

            CBORObject cbor = record.toCborTree(Canonical.Scope.BODY);
            assertThat(cbor.getType()).isEqualTo(com.upokecenter.cbor.CBORType.Array);
            assertThat(cbor.size()).isEqualTo(3);
        }

        @Test
        @DisplayName("decode rejects 2-element array")
        void rejectsBodyShape() {
            CBORObject body = CBORObject.NewArray();
            body.Add(FrameRef.of(DatumID.of("x".getBytes())).toCborTree(Canonical.Scope.BODY));
            body.Add(CBORObject.NewArray());

            assertThatThrownBy(() -> Record.fromCborTree(body))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("decode rejects non-FrameRef head")
        void rejectsNonFrameRefHead() {
            CBORObject record = CBORObject.NewArray();
            record.Add(ItemRef.of(PRED).toCborTree(Canonical.Scope.BODY));
            record.Add(CBORObject.NewArray());
            record.Add(CBORObject.FromByteArray(new byte[]{0x01}));

            assertThatThrownBy(() -> Record.fromCborTree(record))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("signingPayload is the structural Merkle root (independent of signature)")
        void signingPayloadIsMerkleRoot() {
            DatumID bodyId = DatumID.of("body".getBytes());
            byte[] sig = new byte[64];
            Record record = Record.of(FrameRef.of(bodyId), List.of(),
                    VarSig.of(Algorithm.Sign.ED25519, sig));

            // signingPayload returns the Merkle root — not the encoded body.
            // Same record with a different signature should have the same payload.
            byte[] payload = HashTree.signingPayload(record);
            byte[] differentSig = new byte[64];
            differentSig[0] = 1;
            Record other = Record.of(FrameRef.of(bodyId), List.of(),
                    VarSig.of(Algorithm.Sign.ED25519, differentSig));
            assertThat(HashTree.signingPayload(other)).isEqualTo(payload);
        }

        @Test
        @DisplayName("rejects empty signature")
        void rejectsEmptySig() {
            DatumID bodyId = DatumID.of("body".getBytes());
            assertThatThrownBy(() -> Record.of(FrameRef.of(bodyId), List.of(), new byte[0]))
                    .isInstanceOf(IllegalArgumentException.class);
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
            assertThat(frame.bodyCID()).isEqualTo(ContentID.of(body.encodeBinary(Canonical.Scope.BODY)));
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
            ItemID iid = ItemID.fromString("specific-item");
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
            ItemID iid = ItemID.fromString("specific-item");
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
            ItemID iid = ItemID.fromString("specific-item");
            ItemID parentVid1 = ItemID.fromString("v1-as-iid");
            ItemID parentVid2 = ItemID.fromString("v2-as-iid");
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
            ItemID iid = ItemID.fromString("specific-item");
            Body body = Body.of(ItemRef.of(DOC), List.of(
                    Binding.ref(Manifest.ITEM_ID, iid),
                    Binding.ref(Manifest.ENDORSES, ItemID.fromString("frame-1")),
                    Binding.ref(Manifest.ENDORSES, ItemID.fromString("frame-2"))
            ));
            Manifest manifest = Manifest.of(body);

            assertThat(manifest.endorses()).hasSize(2);
        }

        @Test
        @DisplayName("Manifest implementation is Optional")
        void implementationOptional() {
            ItemID iid = ItemID.fromString("specific-item");
            Body bodyNoImpl = Body.of(ItemRef.of(DOC), List.of(
                    Binding.ref(Manifest.ITEM_ID, iid)
            ));
            assertThat(Manifest.of(bodyNoImpl).implementation()).isEmpty();

            Body bodyWithImpl = Body.of(ItemRef.of(DOC), List.of(
                    Binding.ref(Manifest.ITEM_ID, iid),
                    Binding.ref(Manifest.IMPLEMENTATION, ItemID.fromString("impl-1"))
            ));
            assertThat(Manifest.of(bodyWithImpl).implementation()).isPresent();
        }
    }
}
