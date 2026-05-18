package dev.everydaythings.graph.datum;

import com.upokecenter.cbor.CBORObject;
import dev.everydaythings.graph.encoding.CgCbor;
import dev.everydaythings.graph.id.CompoundKey;
import dev.everydaythings.graph.id.ContentRef;
import dev.everydaythings.graph.id.DatumRef;
import dev.everydaythings.graph.id.HashID;
import dev.everydaythings.graph.id.ItemRef;
import dev.everydaythings.graph.id.SchemaRef;
import dev.everydaythings.graph.id.TypeRef;
import dev.everydaythings.graph.value.Color;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Binding roles can now be any of the three IID-family reference variants:
 * {@link ItemRef} (literal — the common case), {@link TypeRef} (query
 * pattern), {@link SchemaRef} (schema/expectation).  ContentRefs and
 * DatumRefs are rejected at CompoundKey construction.
 *
 * <p>The schema-roled binding (`!R = ...`) is the structural form of an
 * EXPECTS declaration on a manifest.  The query-roled binding (`?R = ...`)
 * is a query position in a body.
 */
class BindingRoleVariantTest {

    static final String R_KEY = "cg.color:r";

    @Nested
    @DisplayName("Literal role (@R)")
    class LiteralRole {

        @Test
        @DisplayName("CompoundKey.head() returns the ItemRef")
        void head() {
            CompoundKey key = CompoundKey.of(ItemRef.iid(R_KEY));
            assertThat(key.head()).isInstanceOf(ItemRef.class);
            assertThat(key.isLiteralHead()).isTrue();
            assertThat(key.isQueryHead()).isFalse();
            assertThat(key.isSchemaHead()).isFalse();
        }

        @Test
        @DisplayName("headIid() returns ItemRef cleanly")
        void headIidWorks() {
            CompoundKey key = CompoundKey.of(ItemRef.iid(R_KEY));
            assertThat(key.headIid()).isEqualTo(ItemRef.iid(R_KEY));
        }
    }

    @Nested
    @DisplayName("Schema role (!R) — the EXPECTS form")
    class SchemaRole {

        @Test
        @DisplayName("CompoundKey accepts a SchemaRef head")
        void constructsWithSchemaRef() {
            SchemaRef role = SchemaRef.iid(R_KEY);
            CompoundKey key = CompoundKey.of(role);
            assertThat(key.head()).isEqualTo(role);
            assertThat(key.isSchemaHead()).isTrue();
        }

        @Test
        @DisplayName("Binding with SchemaRef role round-trips via CBOR, preserving the variant")
        void cborRoundTrip() {
            SchemaRef role = SchemaRef.iid(R_KEY);
            Binding original = new Binding(role, 255L);
            CBORObject cbor = CgCbor.toCbor(original);
            Binding decoded = CgCbor.decodeBinding(cbor);
            assertThat(decoded.role()).isEqualTo(role);
            assertThat(decoded.role()).isInstanceOf(SchemaRef.class);
            assertThat(decoded).isEqualTo(original);
        }

        @Test
        @DisplayName("headIid() throws for a SchemaRef-headed key")
        void headIidThrows() {
            CompoundKey key = CompoundKey.of(SchemaRef.iid(R_KEY));
            assertThatThrownBy(key::headIid)
                    .isInstanceOf(ClassCastException.class)
                    .hasMessageContaining("SCHEMA");
        }
    }

    @Nested
    @DisplayName("Query role (?R) — the pattern form")
    class QueryRole {

        @Test
        @DisplayName("CompoundKey accepts a TypeRef head")
        void constructsWithTypeRef() {
            TypeRef role = TypeRef.iid(R_KEY);
            CompoundKey key = CompoundKey.of(role);
            assertThat(key.head()).isEqualTo(role);
            assertThat(key.isQueryHead()).isTrue();
        }

        @Test
        @DisplayName("Binding with TypeRef role round-trips via CBOR")
        void cborRoundTrip() {
            TypeRef role = TypeRef.iid(R_KEY);
            Binding original = new Binding(role, 255L);
            CBORObject cbor = CgCbor.toCbor(original);
            Binding decoded = CgCbor.decodeBinding(cbor);
            assertThat(decoded.role()).isEqualTo(role);
            assertThat(decoded.role()).isInstanceOf(TypeRef.class);
        }
    }

    @Nested
    @DisplayName("Cross-variant equality")
    class CrossVariant {

        @Test
        @DisplayName("Bindings with literal/query/schema roles at the same IID are NOT equal")
        void distinctAtSameIid() {
            Binding bLit = new Binding(ItemRef.iid(R_KEY), 1L);
            Binding bQry = new Binding(TypeRef.iid(R_KEY), 1L);
            Binding bSch = new Binding(SchemaRef.iid(R_KEY), 1L);
            assertThat(bLit).isNotEqualTo(bQry);
            assertThat(bLit).isNotEqualTo(bSch);
            assertThat(bQry).isNotEqualTo(bSch);
        }
    }

    @Nested
    @DisplayName("Rejected heads")
    class Rejections {

        @Test
        @DisplayName("ContentRef head is rejected at CompoundKey construction")
        void rejectsContentRef() {
            ContentRef bogus = ContentRef.of("data".getBytes());
            assertThatThrownBy(() -> CompoundKey.of(bogus))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("IID family");
        }

        @Test
        @DisplayName("DatumRef head is rejected at CompoundKey construction")
        void rejectsDatumRef() {
            byte[] digest = HashID.randomID(HashID.KEY_LENGTH);
            DatumRef bogus = new DatumRef(digest, io.ipfs.multihash.Multihash.Type.sha2_256);
            assertThatThrownBy(() -> CompoundKey.of(bogus))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("IID family");
        }
    }

    @Nested
    @DisplayName("Body with schema-roled bindings (the EXPECTS shape)")
    class ExpectsShape {

        @Test
        @DisplayName("Color archetype manifest can carry !R, !G, !B bindings declaring shape")
        void colorExpectsShape() {
            ItemRef colorHead = ItemRef.iid(Color.KEY);
            Body manifest = Body.of(colorHead, List.of(
                    new Binding(SchemaRef.iid(Color.R.KEY), 0L),
                    new Binding(SchemaRef.iid(Color.G.KEY), 0L),
                    new Binding(SchemaRef.iid(Color.B.KEY), 0L)));

            // Body itself is literal; its bindings are schema-roled.
            assertThat(manifest.isLiteralBody()).isTrue();
            assertThat(manifest.bindings()).hasSize(3);
            for (Binding b : manifest.bindings()) {
                assertThat(b.role()).isInstanceOf(SchemaRef.class);
            }

            // CBOR round-trip preserves all of this.
            CBORObject cbor = CgCbor.toCbor(manifest);
            Body decoded = CgCbor.decodeBody(cbor);
            assertThat(decoded).isEqualTo(manifest);
            for (Binding b : decoded.bindings()) {
                assertThat(b.role()).isInstanceOf(SchemaRef.class);
            }
        }
    }
}
