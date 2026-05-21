package dev.everydaythings.graph.datum;

import com.upokecenter.cbor.CBORObject;
import dev.everydaythings.graph.encoding.CgCbor;
import dev.everydaythings.graph.ref.ContentRef;
import dev.everydaythings.graph.ref.DatumRef;
import dev.everydaythings.graph.ref.HashID;
import dev.everydaythings.graph.ref.ItemRef;
import dev.everydaythings.graph.ref.SchemaRef;
import dev.everydaythings.graph.ref.TypeRef;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Body heads can now be any of the three IID-family reference variants:
 * {@link ItemRef} (literal), {@link TypeRef} (query), {@link SchemaRef}
 * (schema).  ContentRefs and DatumRefs are rejected at construction.
 */
class BodyHeadVariantTest {

    static final String COLOR_KEY = "cg.archetype:color";
    static final ItemRef R = ItemRef.fromString("cg.color:r");

    @Nested
    @DisplayName("Literal head (ItemRef)")
    class LiteralHead {

        @Test
        @DisplayName("isLiteralBody is true, isQueryBody and isSchemaBody false")
        void predicates() {
            Body b = Body.of(ItemRef.fromString(COLOR_KEY), List.of());
            assertThat(b.isLiteralBody()).isTrue();
            assertThat(b.isQueryBody()).isFalse();
            assertThat(b.isSchemaBody()).isFalse();
        }

        @Test
        @DisplayName("headRef() returns the ItemRef")
        void headRefAccessor() {
            ItemRef head = ItemRef.fromString(COLOR_KEY);
            Body b = Body.of(head, List.of());
            assertThat(b.headRef()).isEqualTo(head);
        }
    }

    @Nested
    @DisplayName("Query head (TypeRef)")
    class QueryHead {

        @Test
        @DisplayName("Body constructible with TypeRef head")
        void constructible() {
            TypeRef head = TypeRef.fromString(COLOR_KEY);
            Body b = Body.of(head, List.of());
            assertThat(b.head()).isEqualTo(head);
        }

        @Test
        @DisplayName("isQueryBody is true")
        void predicates() {
            Body b = Body.of(TypeRef.fromString(COLOR_KEY), List.of());
            assertThat(b.isQueryBody()).isTrue();
            assertThat(b.isLiteralBody()).isFalse();
            assertThat(b.isSchemaBody()).isFalse();
        }

        @Test
        @DisplayName("headRef() throws since head is not an ItemRef")
        void headRefThrows() {
            Body b = Body.of(TypeRef.fromString(COLOR_KEY), List.of());
            assertThatThrownBy(b::headRef)
                    .isInstanceOf(ClassCastException.class)
                    .hasMessageContaining("TYPE");
        }

        @Test
        @DisplayName("CBOR round-trip preserves the TypeRef head")
        void cborRoundTrip() {
            TypeRef head = TypeRef.fromString(COLOR_KEY);
            Body original = Body.of(head, List.of(
                    Binding.literal(R, 200L)));
            CBORObject cbor = CgCbor.toCbor(original);
            Body decoded = CgCbor.decodeBody(cbor);
            assertThat(decoded.head()).isEqualTo(head);
            assertThat(decoded.isQueryBody()).isTrue();
            assertThat(decoded).isEqualTo(original);
        }
    }

    @Nested
    @DisplayName("Schema head (SchemaRef)")
    class SchemaHead {

        @Test
        @DisplayName("Body constructible with SchemaRef head")
        void constructible() {
            SchemaRef head = SchemaRef.fromString(COLOR_KEY);
            Body b = Body.of(head, List.of());
            assertThat(b.head()).isEqualTo(head);
        }

        @Test
        @DisplayName("isSchemaBody is true")
        void predicates() {
            Body b = Body.of(SchemaRef.fromString(COLOR_KEY), List.of());
            assertThat(b.isSchemaBody()).isTrue();
            assertThat(b.isLiteralBody()).isFalse();
            assertThat(b.isQueryBody()).isFalse();
        }

        @Test
        @DisplayName("CBOR round-trip preserves the SchemaRef head")
        void cborRoundTrip() {
            SchemaRef head = SchemaRef.fromString(COLOR_KEY);
            Body original = Body.of(head, List.of(
                    Binding.literal(R, 255L)));
            CBORObject cbor = CgCbor.toCbor(original);
            Body decoded = CgCbor.decodeBody(cbor);
            assertThat(decoded.head()).isEqualTo(head);
            assertThat(decoded.isSchemaBody()).isTrue();
            assertThat(decoded).isEqualTo(original);
        }
    }

    @Nested
    @DisplayName("Cross-variant equality")
    class CrossVariant {

        @Test
        @DisplayName("Literal, query, and schema bodies with the same underlying IID are NOT equal")
        void distinctEvenWhenIidMatches() {
            HashID literal = ItemRef.fromString(COLOR_KEY);
            HashID query = TypeRef.fromString(COLOR_KEY);
            HashID schema = SchemaRef.fromString(COLOR_KEY);
            Body bLit = Body.of(literal, List.of());
            Body bQry = Body.of(query, List.of());
            Body bSch = Body.of(schema, List.of());
            assertThat(bLit).isNotEqualTo(bQry);
            assertThat(bLit).isNotEqualTo(bSch);
            assertThat(bQry).isNotEqualTo(bSch);
        }
    }

    @Nested
    @DisplayName("Rejected head variants")
    class Rejections {

        @Test
        @DisplayName("ContentRef head is rejected at construction")
        void rejectsContentRef() {
            ContentRef bogus = ContentRef.of("data".getBytes());
            assertThatThrownBy(() -> Body.of(bogus, List.of()))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("IID family");
        }

        @Test
        @DisplayName("DatumRef head is rejected at construction")
        void rejectsDatumRef() {
            byte[] digest = HashID.randomID(HashID.KEY_LENGTH);
            DatumRef bogus = new DatumRef(digest, io.ipfs.multihash.Multihash.Type.sha2_256);
            assertThatThrownBy(() -> Body.of(bogus, List.of()))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("IID family");
        }
    }
}
