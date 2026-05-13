package dev.everydaythings.graph.item.id;

import com.upokecenter.cbor.CBORObject;
import dev.everydaythings.graph.encoding.Canonical;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ReferenceTest {

    static final ItemID IID = ItemID.fromString("cg:item/test-1");
    static final ItemID OTHER_IID = ItemID.fromString("cg:item/test-2");
    static final ContentID VID = ContentID.of("version-1".getBytes());
    static final ContentID CID = ContentID.of("content-bytes".getBytes());
    static final DatumID BODY_ID = DatumID.of("body-bytes".getBytes());

    static final ItemID THEME = ItemID.fromString("cg:role/theme");
    static final ItemID ENG = ItemID.fromString("cg:language/eng");

    @Nested
    @DisplayName("ItemRef")
    class ItemOldRefTest {

        @Test
        @DisplayName("unpinned construction")
        void unpinned() {
            ItemRef ref = ItemRef.of(IID);
            assertThat(ref.iid()).isEqualTo(IID);
            assertThat(ref.version()).isEmpty();
            assertThat(ref.isPinned()).isFalse();
            assertThat(ref.variant()).isEqualTo(Reference.Variant.ITEM);
        }

        @Test
        @DisplayName("version-pinned construction")
        void pinned() {
            ItemRef ref = ItemRef.of(IID, VID);
            assertThat(ref.iid()).isEqualTo(IID);
            assertThat(ref.version()).contains(VID);
            assertThat(ref.isPinned()).isTrue();
        }

        @Test
        @DisplayName("text round-trip unpinned")
        void textRoundTripUnpinned() {
            ItemRef original = ItemRef.of(IID);
            String text = original.encodeText();
            assertThat(text).startsWith("@");
            assertThat(text).doesNotContain("\\");
            ItemRef decoded = ItemRef.parseText(text);
            assertThat(decoded).isEqualTo(original);
        }

        @Test
        @DisplayName("text round-trip pinned")
        void textRoundTripPinned() {
            ItemRef original = ItemRef.of(IID, VID);
            String text = original.encodeText();
            assertThat(text).startsWith("@");
            assertThat(text).contains("\\");
            ItemRef decoded = ItemRef.parseText(text);
            assertThat(decoded).isEqualTo(original);
        }

        @Test
        @DisplayName("binary round-trip unpinned")
        void binaryRoundTripUnpinned() {
            ItemRef original = ItemRef.of(IID);
            byte[] bytes = original.toRefBytes();
            assertThat(bytes[0]).isEqualTo(Reference.PREFIX_ITEM);
            Reference decoded = Reference.fromRefBytes(bytes);
            assertThat(decoded).isEqualTo(original);
        }

        @Test
        @DisplayName("binary round-trip pinned")
        void binaryRoundTripPinned() {
            ItemRef original = ItemRef.of(IID, VID);
            byte[] bytes = original.toRefBytes();
            Reference decoded = Reference.fromRefBytes(bytes);
            assertThat(decoded).isEqualTo(original);
        }

        @Test
        @DisplayName("CBOR Tag-6 round-trip")
        void cborRoundTrip() {
            ItemRef original = ItemRef.of(IID, VID);
            CBORObject cbor = original.toCborTree(Canonical.Scope.BODY);
            assertThat(cbor.isTagged()).isTrue();
            assertThat(cbor.HasMostOuterTag(6)).isTrue();
            Reference decoded = Reference.fromCborTree(cbor);
            assertThat(decoded).isEqualTo(original);
        }

        @Test
        @DisplayName("rejects null iid")
        void rejectsNull() {
            assertThatThrownBy(() -> ItemRef.of(null))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("different iids produce different refs")
        void differentIids() {
            assertThat(ItemRef.of(IID)).isNotEqualTo(ItemRef.of(OTHER_IID));
        }
    }

    @Nested
    @DisplayName("ContentRef")
    class ContentRefTest {

        @Test
        @DisplayName("construction")
        void construct() {
            ContentRef ref = ContentRef.of(CID);
            assertThat(ref.cid()).isEqualTo(CID);
            assertThat(ref.variant()).isEqualTo(Reference.Variant.CONTENT);
        }

        @Test
        @DisplayName("text round-trip")
        void textRoundTrip() {
            ContentRef original = ContentRef.of(CID);
            String text = original.encodeText();
            assertThat(text).startsWith("~");
            assertThat(text).doesNotContain("\\");
            ContentRef decoded = ContentRef.parseText(text);
            assertThat(decoded).isEqualTo(original);
        }

        @Test
        @DisplayName("binary round-trip")
        void binaryRoundTrip() {
            ContentRef original = ContentRef.of(CID);
            byte[] bytes = original.toRefBytes();
            assertThat(bytes[0]).isEqualTo(Reference.PREFIX_CONTENT);
            Reference decoded = Reference.fromRefBytes(bytes);
            assertThat(decoded).isEqualTo(original);
        }

        @Test
        @DisplayName("CBOR Tag-6 round-trip")
        void cborRoundTrip() {
            ContentRef original = ContentRef.of(CID);
            CBORObject cbor = original.toCborTree(Canonical.Scope.BODY);
            Reference decoded = Reference.fromCborTree(cbor);
            assertThat(decoded).isEqualTo(original);
        }

        @Test
        @DisplayName("rejects null cid")
        void rejectsNull() {
            assertThatThrownBy(() -> ContentRef.of(null))
                    .isInstanceOf(NullPointerException.class);
        }
    }

    @Nested
    @DisplayName("FrameRef")
    class FrameOldRefTest {

        @Test
        @DisplayName("whole-frame construction")
        void wholeFrame() {
            FrameRef ref = FrameRef.of(BODY_ID);
            assertThat(ref.bodyId()).isEqualTo(BODY_ID);
            assertThat(ref.key()).isEmpty();
            assertThat(ref.portion()).isEmpty();
            assertThat(ref.variant()).isEqualTo(Reference.Variant.FRAME);
        }

        @Test
        @DisplayName("with key construction")
        void withKey() {
            CompoundKey key = CompoundKey.of(THEME, ENG);
            FrameRef ref = FrameRef.of(BODY_ID, key);
            assertThat(ref.key()).contains(key);
            assertThat(ref.portion()).isEmpty();
        }

        @Test
        @DisplayName("with key and portion construction")
        void withKeyAndPortion() {
            CompoundKey key = CompoundKey.of(THEME);
            Selector portion = Selector.byteRange(10, 20);
            FrameRef ref = FrameRef.of(BODY_ID, key, portion);
            assertThat(ref.key()).contains(key);
            assertThat(ref.portion()).contains(portion);
        }

        @Test
        @DisplayName("rejects portion without key")
        void rejectsPortionWithoutKey() {
            Selector portion = Selector.byteRange(0, 10);
            assertThatThrownBy(() -> new FrameRef(BODY_ID, Optional.empty(), Optional.of(portion)))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("text round-trip whole frame")
        void textRoundTripWhole() {
            FrameRef original = FrameRef.of(BODY_ID);
            String text = original.encodeText();
            assertThat(text).startsWith("#");
            assertThat(text).doesNotContain("\\");
            FrameRef decoded = FrameRef.parseText(text);
            assertThat(decoded).isEqualTo(original);
        }

        @Test
        @DisplayName("text round-trip with key")
        void textRoundTripWithKey() {
            CompoundKey key = CompoundKey.of(THEME);
            FrameRef original = FrameRef.of(BODY_ID, key);
            String text = original.encodeText();
            assertThat(text).startsWith("#");
            assertThat(text).contains("\\");
            FrameRef decoded = FrameRef.parseText(text);
            assertThat(decoded.bodyId()).isEqualTo(original.bodyId());
            assertThat(decoded.key()).isPresent();
            assertThat(decoded.key().get().head()).isEqualTo(THEME);
        }

        @Test
        @DisplayName("binary round-trip whole frame")
        void binaryRoundTripWhole() {
            FrameRef original = FrameRef.of(BODY_ID);
            byte[] bytes = original.toRefBytes();
            assertThat(bytes[0]).isEqualTo(Reference.PREFIX_FRAME);
            Reference decoded = Reference.fromRefBytes(bytes);
            assertThat(decoded).isEqualTo(original);
        }

        @Test
        @DisplayName("binary round-trip with key")
        void binaryRoundTripWithKey() {
            CompoundKey key = CompoundKey.of(THEME, ENG);
            FrameRef original = FrameRef.of(BODY_ID, key);
            byte[] bytes = original.toRefBytes();
            Reference decoded = Reference.fromRefBytes(bytes);
            assertThat(decoded).isEqualTo(original);
        }

        @Test
        @DisplayName("CBOR Tag-6 round-trip whole frame")
        void cborRoundTripWhole() {
            FrameRef original = FrameRef.of(BODY_ID);
            CBORObject cbor = original.toCborTree(Canonical.Scope.BODY);
            Reference decoded = Reference.fromCborTree(cbor);
            assertThat(decoded).isEqualTo(original);
        }
    }

    @Nested
    @DisplayName("Reference.parse dispatch")
    class ParseDispatch {

        @Test
        @DisplayName("@ → ItemRef")
        void parseItemRef() {
            ItemRef original = ItemRef.of(IID);
            Reference parsed = Reference.parse(original.encodeText());
            assertThat(parsed).isInstanceOf(ItemRef.class);
            assertThat(parsed).isEqualTo(original);
        }

        @Test
        @DisplayName("~ → ContentRef")
        void parseContentRef() {
            ContentRef original = ContentRef.of(CID);
            Reference parsed = Reference.parse(original.encodeText());
            assertThat(parsed).isInstanceOf(ContentRef.class);
            assertThat(parsed).isEqualTo(original);
        }

        @Test
        @DisplayName("# → FrameRef")
        void parseFrameRef() {
            FrameRef original = FrameRef.of(BODY_ID);
            Reference parsed = Reference.parse(original.encodeText());
            assertThat(parsed).isInstanceOf(FrameRef.class);
            assertThat(parsed).isEqualTo(original);
        }

        @Test
        @DisplayName("rejects unknown prefix")
        void rejectsUnknownPrefix() {
            assertThatThrownBy(() -> Reference.parse("?something"))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("rejects empty text")
        void rejectsEmpty() {
            assertThatThrownBy(() -> Reference.parse(""))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> Reference.parse(null))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("Reference.fromRefBytes dispatch")
    class FromRefBytesDispatch {

        @Test
        @DisplayName("0x40 → ItemRef")
        void itemRefBytes() {
            ItemRef original = ItemRef.of(IID, VID);
            byte[] bytes = original.toRefBytes();
            Reference decoded = Reference.fromRefBytes(bytes);
            assertThat(decoded).isInstanceOf(ItemRef.class);
            assertThat(decoded).isEqualTo(original);
        }

        @Test
        @DisplayName("0x7E → ContentRef")
        void contentRefBytes() {
            ContentRef original = ContentRef.of(CID);
            byte[] bytes = original.toRefBytes();
            Reference decoded = Reference.fromRefBytes(bytes);
            assertThat(decoded).isInstanceOf(ContentRef.class);
        }

        @Test
        @DisplayName("0x23 → FrameRef")
        void frameRefBytes() {
            FrameRef original = FrameRef.of(BODY_ID);
            byte[] bytes = original.toRefBytes();
            Reference decoded = Reference.fromRefBytes(bytes);
            assertThat(decoded).isInstanceOf(FrameRef.class);
        }

        @Test
        @DisplayName("rejects unknown prefix byte")
        void rejectsUnknownPrefix() {
            byte[] bad = new byte[]{0x55, 0x01, 0x02};
            assertThatThrownBy(() -> Reference.fromRefBytes(bad))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("rejects empty payload")
        void rejectsEmpty() {
            assertThatThrownBy(() -> Reference.fromRefBytes(new byte[0]))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }
}
