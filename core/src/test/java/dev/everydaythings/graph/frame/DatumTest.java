package dev.everydaythings.graph.frame;

import com.upokecenter.cbor.CBORObject;
import dev.everydaythings.graph.Canonical;
import dev.everydaythings.graph.crypt.Algorithm;
import dev.everydaythings.graph.crypt.VarSig;
import dev.everydaythings.graph.item.Manifest;
import dev.everydaythings.graph.item.id.CompoundKey;
import dev.everydaythings.graph.item.id.ContentID;
import dev.everydaythings.graph.item.id.FrameRef;
import dev.everydaythings.graph.item.id.ItemID;
import dev.everydaythings.graph.item.id.ItemRef;
import dev.everydaythings.graph.item.id.Reference;
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
            assertThat(body.cid()).isNotNull();
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
            assertThat(decoded.cid()).isEqualTo(original.cid());
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
            body.Add(FrameRef.of(ContentID.of("x".getBytes())).toCborTree(Canonical.Scope.BODY));
            body.Add(CBORObject.NewArray());

            assertThatThrownBy(() -> Body.fromCborTree(body))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("equal bodies produce equal CIDs")
        void equalBodiesEqualCids() {
            Body a = Body.of(ItemRef.of(PRED), List.of(Binding.ref(THEME, HOBBIT)));
            Body b = Body.of(ItemRef.of(PRED), List.of(Binding.ref(THEME, HOBBIT)));
            assertThat(a.cid()).isEqualTo(b.cid());
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
            ContentID bodyCid = body.cid();

            byte[] sig = new byte[64];
            for (int i = 0; i < 64; i++) sig[i] = (byte) i;
            VarSig varsig = VarSig.of(Algorithm.Sign.ED25519, sig);

            Record record = Record.of(FrameRef.of(bodyCid), List.of(), varsig);

            assertThat(record.head()).isEqualTo(FrameRef.of(bodyCid));
            assertThat(record.varsig().algorithm()).isEqualTo(Algorithm.Sign.ED25519);
        }

        @Test
        @DisplayName("CBOR round-trip preserves content")
        void cborRoundTrip() {
            ContentID bodyCid = ContentID.of("body".getBytes());
            byte[] sig = new byte[64];
            VarSig varsig = VarSig.of(Algorithm.Sign.ED25519, sig);

            Record original = Record.of(FrameRef.of(bodyCid), List.of(), varsig);
            CBORObject cbor = original.toCborTree(Canonical.Scope.BODY);
            Record decoded = Record.fromCborTree(cbor);

            assertThat(decoded).isEqualTo(original);
        }

        @Test
        @DisplayName("CBOR encoding is a 3-element array")
        void cborThreeElement() {
            ContentID bodyCid = ContentID.of("body".getBytes());
            byte[] sig = new byte[64];
            Record record = Record.of(FrameRef.of(bodyCid), List.of(),
                    VarSig.of(Algorithm.Sign.ED25519, sig));

            CBORObject cbor = record.toCborTree(Canonical.Scope.BODY);
            assertThat(cbor.getType()).isEqualTo(com.upokecenter.cbor.CBORType.Array);
            assertThat(cbor.size()).isEqualTo(3);
        }

        @Test
        @DisplayName("decode rejects 2-element array")
        void rejectsBodyShape() {
            CBORObject body = CBORObject.NewArray();
            body.Add(FrameRef.of(ContentID.of("x".getBytes())).toCborTree(Canonical.Scope.BODY));
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
        @DisplayName("encodeBodyForSigning excludes signature")
        void bodyForSigningExcludesSignature() {
            ContentID bodyCid = ContentID.of("body".getBytes());
            byte[] sig = new byte[64];
            Record record = Record.of(FrameRef.of(bodyCid), List.of(),
                    VarSig.of(Algorithm.Sign.ED25519, sig));

            byte[] toSign = record.encodeBodyForSigning(Canonical.Scope.BODY);
            byte[] full = record.toCborTree(Canonical.Scope.BODY).EncodeToBytes();
            assertThat(toSign.length).isLessThan(full.length);
        }

        @Test
        @DisplayName("rejects empty signature")
        void rejectsEmptySig() {
            ContentID bodyCid = ContentID.of("body".getBytes());
            assertThatThrownBy(() -> Record.of(FrameRef.of(bodyCid), List.of(), new byte[0]))
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
            assertThat(frame.bodyCID()).isEqualTo(body.cid());
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
            assertThat(manifest.versionId()).isEqualTo(body.cid());
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
