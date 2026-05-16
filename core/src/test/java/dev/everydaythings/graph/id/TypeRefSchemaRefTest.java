package dev.everydaythings.graph.id;

import com.upokecenter.cbor.CBORObject;
import dev.everydaythings.graph.encoding.CgCbor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TypeRefSchemaRefTest {

    static final String KEY = "cg.archetype:color";

    @Nested
    @DisplayName("TypeRef")
    class TypeRefSpec {

        @Test
        @DisplayName("fromString(key) and iid(key) produce identical refs")
        void fromStringAliasing() {
            TypeRef a = TypeRef.fromString(KEY);
            TypeRef b = TypeRef.iid(KEY);
            assertThat(a).isEqualTo(b);
        }

        @Test
        @DisplayName("shares the same underlying IID multihash as ItemRef.fromString")
        void sharesIidWithItemRef() {
            TypeRef tr = TypeRef.fromString(KEY);
            ItemRef ir = ItemRef.fromString(KEY);
            assertThat(tr.multihash()).containsExactly(ir.multihash());
        }

        @Test
        @DisplayName("prefix byte is '?' (0x3F)")
        void prefixByte() {
            TypeRef tr = TypeRef.fromString(KEY);
            assertThat(tr.prefixByte()).isEqualTo((byte) 0x3F);
            assertThat(tr.toRefBytes()[0]).isEqualTo((byte) 0x3F);
        }

        @Test
        @DisplayName("variant marker is TYPE")
        void variantMarker() {
            assertThat(TypeRef.fromString(KEY).variant()).isEqualTo(HashID.Variant.TYPE);
        }

        @Test
        @DisplayName("text form starts with '?'")
        void textForm() {
            assertThat(TypeRef.fromString(KEY).encodeText()).startsWith("?");
        }

        @Test
        @DisplayName("binary round-trip via fromRefBytes")
        void binaryRoundTrip() {
            TypeRef original = TypeRef.fromString(KEY);
            byte[] wire = original.toRefBytes();
            HashID decoded = HashID.fromRefBytes(wire);
            assertThat(decoded)
                    .isInstanceOf(TypeRef.class)
                    .isEqualTo(original);
        }

        @Test
        @DisplayName("text round-trip via HashID.parse")
        void textRoundTrip() {
            TypeRef original = TypeRef.fromString(KEY);
            String text = original.encodeText();
            HashID decoded = HashID.parse(text);
            assertThat(decoded)
                    .isInstanceOf(TypeRef.class)
                    .isEqualTo(original);
        }

        @Test
        @DisplayName("CBOR Tag-6 round-trip via HashID.fromCborTree")
        void cborRoundTrip() {
            TypeRef original = TypeRef.fromString(KEY);
            CBORObject node = CBORObject.FromByteArray(original.toRefBytes())
                    .WithTag(CgCbor.TAG_REF);
            HashID decoded = HashID.fromCborTree(node);
            assertThat(decoded)
                    .isInstanceOf(TypeRef.class)
                    .isEqualTo(original);
        }

        @Test
        @DisplayName("TypeRef.iid() returns an unpinned ItemRef pointing at the same IID")
        void iidAccessor() {
            TypeRef tr = TypeRef.fromString(KEY);
            ItemRef ir = tr.iid();
            assertThat(ir.multihash()).containsExactly(tr.multihash());
            assertThat(ir.isPinned()).isFalse();
        }

        @Test
        @DisplayName("from(ItemRef) wraps an existing item ref's IID")
        void wrapsItemRef() {
            ItemRef ir = ItemRef.fromString(KEY);
            TypeRef tr = TypeRef.of(ir);
            assertThat(tr.multihash()).containsExactly(ir.multihash());
        }

        @Test
        @DisplayName("parseText rejects text without '?' prefix")
        void parseRejectsWrongPrefix() {
            String itemRefText = ItemRef.fromString(KEY).encodeText();
            assertThatThrownBy(() -> TypeRef.parseText(itemRefText))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("SchemaRef")
    class SchemaRefSpec {

        @Test
        @DisplayName("fromString(key) and iid(key) produce identical refs")
        void fromStringAliasing() {
            SchemaRef a = SchemaRef.fromString(KEY);
            SchemaRef b = SchemaRef.iid(KEY);
            assertThat(a).isEqualTo(b);
        }

        @Test
        @DisplayName("shares the same underlying IID multihash as ItemRef.fromString")
        void sharesIidWithItemRef() {
            SchemaRef sr = SchemaRef.fromString(KEY);
            ItemRef ir = ItemRef.fromString(KEY);
            assertThat(sr.multihash()).containsExactly(ir.multihash());
        }

        @Test
        @DisplayName("prefix byte is '!' (0x21)")
        void prefixByte() {
            SchemaRef sr = SchemaRef.fromString(KEY);
            assertThat(sr.prefixByte()).isEqualTo((byte) 0x21);
            assertThat(sr.toRefBytes()[0]).isEqualTo((byte) 0x21);
        }

        @Test
        @DisplayName("variant marker is SCHEMA")
        void variantMarker() {
            assertThat(SchemaRef.fromString(KEY).variant()).isEqualTo(HashID.Variant.SCHEMA);
        }

        @Test
        @DisplayName("text form starts with '!'")
        void textForm() {
            assertThat(SchemaRef.fromString(KEY).encodeText()).startsWith("!");
        }

        @Test
        @DisplayName("binary round-trip via fromRefBytes")
        void binaryRoundTrip() {
            SchemaRef original = SchemaRef.fromString(KEY);
            byte[] wire = original.toRefBytes();
            HashID decoded = HashID.fromRefBytes(wire);
            assertThat(decoded)
                    .isInstanceOf(SchemaRef.class)
                    .isEqualTo(original);
        }

        @Test
        @DisplayName("text round-trip via HashID.parse")
        void textRoundTrip() {
            SchemaRef original = SchemaRef.fromString(KEY);
            String text = original.encodeText();
            HashID decoded = HashID.parse(text);
            assertThat(decoded)
                    .isInstanceOf(SchemaRef.class)
                    .isEqualTo(original);
        }

        @Test
        @DisplayName("CBOR Tag-6 round-trip via HashID.fromCborTree")
        void cborRoundTrip() {
            SchemaRef original = SchemaRef.fromString(KEY);
            CBORObject node = CBORObject.FromByteArray(original.toRefBytes())
                    .WithTag(CgCbor.TAG_REF);
            HashID decoded = HashID.fromCborTree(node);
            assertThat(decoded)
                    .isInstanceOf(SchemaRef.class)
                    .isEqualTo(original);
        }
    }

    @Nested
    @DisplayName("Cross-variant distinctions")
    class CrossVariant {

        @Test
        @DisplayName("TypeRef, SchemaRef, ItemRef with same IID are NOT equal")
        void distinctVariants() {
            ItemRef ir = ItemRef.fromString(KEY);
            TypeRef tr = TypeRef.fromString(KEY);
            SchemaRef sr = SchemaRef.fromString(KEY);
            assertThat(tr).isNotEqualTo(ir);
            assertThat(sr).isNotEqualTo(ir);
            assertThat(tr).isNotEqualTo(sr);
        }

        @Test
        @DisplayName("the three variants have distinct first wire bytes")
        void distinctPrefixBytes() {
            byte[] ir = ItemRef.fromString(KEY).toRefBytes();
            byte[] tr = TypeRef.fromString(KEY).toRefBytes();
            byte[] sr = SchemaRef.fromString(KEY).toRefBytes();
            assertThat(ir[0]).isNotEqualTo(tr[0]);
            assertThat(ir[0]).isNotEqualTo(sr[0]);
            assertThat(tr[0]).isNotEqualTo(sr[0]);
        }

        @Test
        @DisplayName("HashID.parse routes by leading prefix character")
        void parseDispatch() {
            String key = "cg.archetype:book";
            assertThat(HashID.parse("@" + tail(ItemRef.fromString(key).encodeText())))
                    .isInstanceOf(ItemRef.class);
            assertThat(HashID.parse(TypeRef.fromString(key).encodeText()))
                    .isInstanceOf(TypeRef.class);
            assertThat(HashID.parse(SchemaRef.fromString(key).encodeText()))
                    .isInstanceOf(SchemaRef.class);
        }

        private static String tail(String s) {
            return s.substring(1);
        }
    }
}
