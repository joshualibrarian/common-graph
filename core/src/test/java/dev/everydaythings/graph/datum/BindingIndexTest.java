package dev.everydaythings.graph.datum;

import com.upokecenter.cbor.CBORObject;
import dev.everydaythings.graph.canonical.HashTree;
import dev.everydaythings.graph.encoding.CgCbor;
import dev.everydaythings.graph.id.ItemRef;
import dev.everydaythings.graph.language.ThematicRole;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for {@link Binding}'s {@code index} field — the structural ordinal slot
 * that lets bindings with the same compound key carry author-intended order.
 */
class BindingIndexTest {

    static final ItemRef CONTAINER = ItemRef.fromString("cg.scene:container-node");
    static final ItemRef CHILD     = ItemRef.fromString("cg.scene:child");
    static final ItemRef NODE_A    = ItemRef.fromString("test.node:a");
    static final ItemRef NODE_B    = ItemRef.fromString("test.node:b");
    static final ItemRef NODE_C    = ItemRef.fromString("test.node:c");

    @Nested
    @DisplayName("Construction and accessors")
    class Construction {

        @Test
        @DisplayName("binding without index has hasIndex()=false and index()=null")
        void noIndex() {
            Binding b = new Binding(CHILD, NODE_A);
            assertThat(b.hasIndex()).isFalse();
            assertThat(b.index()).isNull();
        }

        @Test
        @DisplayName("binding with index has hasIndex()=true and exposes the value")
        void withIndex() {
            Binding b = new Binding(CHILD, List.of(), NODE_A, 3L);
            assertThat(b.hasIndex()).isTrue();
            assertThat(b.index()).isEqualTo(3L);
        }
    }

    @Nested
    @DisplayName("CBOR round-trip")
    class CborRoundTrip {

        @Test
        @DisplayName("binding with null index encodes as 2-element array")
        void nullIndexEncodes2Elements() {
            Binding b = new Binding(CHILD, NODE_A);
            CBORObject cbor = CgCbor.toCbor(b);
            assertThat(cbor.size()).isEqualTo(2);
        }

        @Test
        @DisplayName("binding with non-null index encodes as 3-element array")
        void indexEncodes3Elements() {
            Binding b = new Binding(CHILD, List.of(), NODE_A, 7L);
            CBORObject cbor = CgCbor.toCbor(b);
            assertThat(cbor.size()).isEqualTo(3);
            assertThat(cbor.get(2).AsInt64Value()).isEqualTo(7L);
        }

        @Test
        @DisplayName("null-index binding round-trips through CBOR")
        void nullIndexRoundTrip() {
            Binding original = new Binding(CHILD, NODE_A);
            CBORObject cbor = CgCbor.toCbor(original);
            Binding decoded = CgCbor.decodeBinding(cbor);
            assertThat(decoded).isEqualTo(original);
            assertThat(decoded.index()).isNull();
        }

        @Test
        @DisplayName("indexed binding round-trips through CBOR")
        void indexedRoundTrip() {
            Binding original = new Binding(CHILD, List.of(), NODE_A, 5L);
            CBORObject cbor = CgCbor.toCbor(original);
            Binding decoded = CgCbor.decodeBinding(cbor);
            assertThat(decoded).isEqualTo(original);
            assertThat(decoded.index()).isEqualTo(5L);
        }
    }

    @Nested
    @DisplayName("Canonical determinism")
    class Canonical {

        @Test
        @DisplayName("two bindings with different index values hash differently")
        void differentIndicesDifferentHash() {
            Binding b1 = new Binding(CHILD, List.of(), NODE_A, 0L);
            Binding b2 = new Binding(CHILD, List.of(), NODE_A, 1L);
            byte[] h1 = HashTree.hashOf(b1, HashTree.DEFAULT_DIGEST);
            byte[] h2 = HashTree.hashOf(b2, HashTree.DEFAULT_DIGEST);
            assertThat(h1).isNotEqualTo(h2);
        }

        @Test
        @DisplayName("null-index binding has same hash as a binding with no index field would")
        void nullIndexBackwardCompatibleHash() {
            // The point: adding the index field doesn't change canonical bytes
            // for bindings that don't use it. Two constructors that arrive at
            // null index by different paths produce the same canonical bytes.
            Binding b1 = new Binding(CHILD, NODE_A);
            Binding b2 = new Binding(CHILD, List.of(), NODE_A, null);
            byte[] h1 = HashTree.hashOf(b1, HashTree.DEFAULT_DIGEST);
            byte[] h2 = HashTree.hashOf(b2, HashTree.DEFAULT_DIGEST);
            assertThat(h1).isEqualTo(h2);
        }

        @Test
        @DisplayName("body containing indexed children encodes deterministically")
        void bodyWithIndexedChildren() {
            Body body = Body.of(CONTAINER, List.of(
                    new Binding(CHILD, List.of(), NODE_A, 0L),
                    new Binding(CHILD, List.of(), NODE_B, 1L),
                    new Binding(CHILD, List.of(), NODE_C, 2L)
            ));
            byte[] firstHash = HashTree.hashOf(body, HashTree.DEFAULT_DIGEST);

            // Same data, constructed in different binding order, produces same hash
            Body bodySameDataReordered = Body.of(CONTAINER, List.of(
                    new Binding(CHILD, List.of(), NODE_C, 2L),
                    new Binding(CHILD, List.of(), NODE_A, 0L),
                    new Binding(CHILD, List.of(), NODE_B, 1L)
            ));
            byte[] secondHash = HashTree.hashOf(bodySameDataReordered, HashTree.DEFAULT_DIGEST);
            assertThat(firstHash).isEqualTo(secondHash);
        }
    }

    @Nested
    @DisplayName("Duplicate-index rejection in DatumBuilder")
    class DuplicateRejection {

        @Test
        @DisplayName("two bindings with same compound key and same non-null index are rejected")
        void rejectsDuplicateIndex() {
            assertThatThrownBy(() -> Frame.compose(ItemRef.fromString("cg.predicate:test"))
                    .with(new Binding(CHILD, List.of(), NODE_A, 0L))
                    .with(new Binding(CHILD, List.of(), NODE_B, 0L))
                    .build())
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Duplicate");
        }

        @Test
        @DisplayName("two bindings with same compound key and different indices are allowed")
        void allowsDifferentIndices() {
            Frame f = Frame.compose(ItemRef.fromString("cg.predicate:test"))
                    .with(new Binding(CHILD, List.of(), NODE_A, 0L))
                    .with(new Binding(CHILD, List.of(), NODE_B, 1L))
                    .build();
            assertThat(f.body().bindings()).hasSize(2);
        }

        @Test
        @DisplayName("two bindings with same compound key and both null indices are allowed")
        void allowsBothNullIndices() {
            Frame f = Frame.compose(ItemRef.fromString("cg.predicate:test"))
                    .with(new Binding(CHILD, NODE_A))
                    .with(new Binding(CHILD, NODE_B))
                    .build();
            assertThat(f.body().bindings()).hasSize(2);
        }

        @Test
        @DisplayName("BindingBuilder.index() flows the value into the materialized binding")
        void indexViaBuilder() {
            Frame f = (Frame) Frame.compose(ItemRef.fromString("cg.predicate:test"))
                    .binding(CHILD).index(42).target(NODE_A)
                    .build();
            Binding b = f.body().bindings().get(0);
            assertThat(b.index()).isEqualTo(42L);
        }
    }

    @Nested
    @DisplayName("Null targets — role-only assertions")
    class NullTargets {

        @Test
        @DisplayName("a binding may carry a null target")
        void nullTargetConstructible() {
            Binding b = new Binding(CHILD, null);
            assertThat(b.target()).isNull();
            assertThat(b.role()).isEqualTo(CHILD);
        }

        @Test
        @DisplayName("null-target binding round-trips through CBOR as a 1-element array")
        void nullTargetRoundTrip() {
            Binding original = new Binding(CHILD, null);
            CBORObject cbor = CgCbor.toCbor(original);
            // Trailing-null trim collapses [key, null, null] to [key].
            assertThat(cbor.size()).isEqualTo(1);
            Binding decoded = CgCbor.decodeBinding(cbor);
            assertThat(decoded).isEqualTo(original);
            assertThat(decoded.target()).isNull();
            assertThat(decoded.index()).isNull();
        }

        @Test
        @DisplayName("null-target binding hashes deterministically")
        void nullTargetDeterministicHash() {
            Binding b1 = new Binding(CHILD, null);
            Binding b2 = new Binding(CHILD, null);
            byte[] h1 = HashTree.hashOf(b1, HashTree.DEFAULT_DIGEST);
            byte[] h2 = HashTree.hashOf(b2, HashTree.DEFAULT_DIGEST);
            assertThat(h1).isEqualTo(h2);
        }

        @Test
        @DisplayName("null-target binding hashes differently from a binding with a target")
        void nullTargetDistinctHash() {
            Binding nullTarget = new Binding(CHILD, null);
            Binding withTarget = new Binding(CHILD, NODE_A);
            byte[] hNull = HashTree.hashOf(nullTarget, HashTree.DEFAULT_DIGEST);
            byte[] hWith = HashTree.hashOf(withTarget, HashTree.DEFAULT_DIGEST);
            assertThat(hNull).isNotEqualTo(hWith);
        }

        @Test
        @DisplayName("a body carrying a null-target binding round-trips and re-hashes identically")
        void bodyWithNullTargetBinding() {
            Body body = Body.of(CONTAINER, List.of(new Binding(CHILD, null)));
            byte[] originalHash = HashTree.hashOf(body, HashTree.DEFAULT_DIGEST);

            CBORObject cbor = CgCbor.toCbor(body);
            Body decoded = CgCbor.decodeBody(cbor);
            assertThat(decoded.bindings()).hasSize(1);
            assertThat(decoded.bindings().get(0).target()).isNull();
            byte[] decodedHash = HashTree.hashOf(decoded, HashTree.DEFAULT_DIGEST);
            assertThat(decodedHash).isEqualTo(originalHash);
        }
    }
}
