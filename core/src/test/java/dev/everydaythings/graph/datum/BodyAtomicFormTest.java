package dev.everydaythings.graph.datum;

import com.upokecenter.cbor.CBORObject;
import dev.everydaythings.graph.encoding.CgCbor;
import dev.everydaythings.graph.ref.ItemRef;
import dev.everydaythings.graph.value.Rational;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Atomic-form Body: head + single leaf value rather than head + bindings.
 * Slot 2 of the CBOR encoding holds the leaf directly (not wrapped in an
 * array).  CIDs are deterministic; round-trip is byte-stable.
 */
class BodyAtomicFormTest {

    private static final ItemRef EMAIL_HEAD = ItemRef.fromString("cg.archetype:email-address");
    private static final ItemRef ISBN_HEAD  = ItemRef.fromString("cg.archetype:isbn");

    @Nested
    @DisplayName("Construction")
    class Construction {

        @Test
        @DisplayName("atomic Body has isAtomic=true and atomicContent populated")
        void atomicConstructor() {
            Body b = Body.ofAtomic(EMAIL_HEAD, "alice@example.com");
            assertThat(b.isAtomic()).isTrue();
            assertThat(b.atomicContent()).contains("alice@example.com");
            assertThat(b.entries()).isEmpty();
            assertThat(b.bindings()).isEmpty();
        }

        @Test
        @DisplayName("structured Body has isAtomic=false and empty atomicContent")
        void structuredStillWorks() {
            Body b = Body.of(EMAIL_HEAD, List.of());
            assertThat(b.isAtomic()).isFalse();
            assertThat(b.atomicContent()).isEmpty();
        }

        @Test
        @DisplayName("rejects null content")
        void rejectsNull() {
            assertThatThrownBy(() -> Body.ofAtomic(EMAIL_HEAD, null))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("rejects unsupported atomic types")
        void rejectsUnsupportedType() {
            assertThatThrownBy(() -> Body.ofAtomic(EMAIL_HEAD, new Object()))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("leaf-typed");
        }
    }

    @Nested
    @DisplayName("Wire round-trip")
    class WireRoundTrip {

        @Test
        @DisplayName("String-atomic body round-trips through CBOR")
        void stringAtomic() {
            Body original = Body.ofAtomic(EMAIL_HEAD, "alice@example.com");
            byte[] bytes = CgCbor.codec().encode(original);
            Body decoded = CgCbor.decodeBody(CBORObject.DecodeFromBytes(bytes));
            assertThat(decoded.isAtomic()).isTrue();
            assertThat(decoded.atomicContent()).contains("alice@example.com");
            assertThat(decoded.head()).isEqualTo(EMAIL_HEAD);
        }

        @Test
        @DisplayName("Integer-atomic body round-trips through CBOR")
        void integerAtomic() {
            Body original = Body.ofAtomic(ISBN_HEAD, 9780547928227L);
            byte[] bytes = CgCbor.codec().encode(original);
            Body decoded = CgCbor.decodeBody(CBORObject.DecodeFromBytes(bytes));
            assertThat(decoded.isAtomic()).isTrue();
            assertThat(decoded.atomicContent()).contains(9780547928227L);
        }

        @Test
        @DisplayName("Instant-atomic body round-trips through CBOR")
        void instantAtomic() {
            Instant t = Instant.parse("2026-05-19T00:00:00Z");
            Body original = Body.ofAtomic(ISBN_HEAD, t);
            byte[] bytes = CgCbor.codec().encode(original);
            Body decoded = CgCbor.decodeBody(CBORObject.DecodeFromBytes(bytes));
            assertThat(decoded.isAtomic()).isTrue();
            assertThat(decoded.atomicContent()).contains(t);
        }

        @Test
        @DisplayName("byte[]-atomic body round-trips through CBOR")
        void bytesAtomic() {
            byte[] payload = new byte[]{1, 2, 3, 4, 5};
            Body original = Body.ofAtomic(ISBN_HEAD, payload);
            byte[] bytes = CgCbor.codec().encode(original);
            Body decoded = CgCbor.decodeBody(CBORObject.DecodeFromBytes(bytes));
            assertThat(decoded.isAtomic()).isTrue();
            assertThat((byte[]) decoded.atomicContent().orElseThrow()).isEqualTo(payload);
        }

        @Test
        @DisplayName("Boolean-atomic body round-trips through CBOR")
        void booleanAtomic() {
            Body original = Body.ofAtomic(ISBN_HEAD, Boolean.TRUE);
            byte[] bytes = CgCbor.codec().encode(original);
            Body decoded = CgCbor.decodeBody(CBORObject.DecodeFromBytes(bytes));
            assertThat(decoded.isAtomic()).isTrue();
            assertThat(decoded.atomicContent()).contains(true);
        }

        @Test
        @DisplayName("Rational-atomic body round-trips through CBOR")
        void rationalAtomic() {
            Rational r = Rational.of(BigInteger.valueOf(355), BigInteger.valueOf(113));
            Body original = Body.ofAtomic(ISBN_HEAD, r);
            byte[] bytes = CgCbor.codec().encode(original);
            Body decoded = CgCbor.decodeBody(CBORObject.DecodeFromBytes(bytes));
            assertThat(decoded.isAtomic()).isTrue();
            assertThat(decoded.atomicContent()).contains(r);
        }

        @Test
        @DisplayName("structured body round-trip still works (no regression)")
        void structuredStillRoundTrips() {
            Body original = Body.of(EMAIL_HEAD, List.of());
            byte[] bytes = CgCbor.codec().encode(original);
            Body decoded = CgCbor.decodeBody(CBORObject.DecodeFromBytes(bytes));
            assertThat(decoded.isAtomic()).isFalse();
            assertThat(decoded.entries()).isEmpty();
            assertThat(decoded.head()).isEqualTo(EMAIL_HEAD);
        }
    }

    @Nested
    @DisplayName("CID determinism")
    class CidDeterminism {

        @Test
        @DisplayName("two equal atomic bodies produce identical CIDs")
        void deterministicCids() {
            Body a = Body.ofAtomic(EMAIL_HEAD, "alice@example.com");
            Body b = Body.ofAtomic(EMAIL_HEAD, "alice@example.com");
            assertThat(a.datumId()).isEqualTo(b.datumId());
        }

        @Test
        @DisplayName("different content produces different CIDs")
        void differentContentDifferentCids() {
            Body a = Body.ofAtomic(EMAIL_HEAD, "alice@example.com");
            Body b = Body.ofAtomic(EMAIL_HEAD, "bob@example.com");
            assertThat(a.datumId()).isNotEqualTo(b.datumId());
        }

        @Test
        @DisplayName("different heads produce different CIDs (same content)")
        void differentHeadsDifferentCids() {
            Body a = Body.ofAtomic(EMAIL_HEAD, "shared-text");
            Body b = Body.ofAtomic(ISBN_HEAD,  "shared-text");
            assertThat(a.datumId()).isNotEqualTo(b.datumId());
        }

        @Test
        @DisplayName("atomic and structured forms with same head produce different CIDs")
        void atomicVsStructuredCidsDiffer() {
            Body atomic = Body.ofAtomic(EMAIL_HEAD, "alice@example.com");
            Body structured = Body.of(EMAIL_HEAD, List.of());
            assertThat(atomic.datumId()).isNotEqualTo(structured.datumId());
        }

        @Test
        @DisplayName("CID survives wire round-trip")
        void cidStableAcrossRoundTrip() {
            Body original = Body.ofAtomic(EMAIL_HEAD, "alice@example.com");
            byte[] bytes = CgCbor.codec().encode(original);
            Body decoded = CgCbor.decodeBody(CBORObject.DecodeFromBytes(bytes));
            assertThat(decoded.datumId()).isEqualTo(original.datumId());
        }
    }

    @Nested
    @DisplayName("Form discriminator")
    class FormDiscriminator {

        @Test
        @DisplayName("isAtomic reflects the constructor that built it")
        void discriminator() {
            assertThat(Body.ofAtomic(EMAIL_HEAD, "x").isAtomic()).isTrue();
            assertThat(Body.of(EMAIL_HEAD, List.of()).isAtomic()).isFalse();
        }

        @Test
        @DisplayName("atomicContent() returns empty Optional for structured bodies")
        void structuredHasNoAtomicContent() {
            Body b = Body.of(EMAIL_HEAD, List.of());
            assertThat(b.atomicContent()).isEqualTo(Optional.empty());
        }
    }
}
