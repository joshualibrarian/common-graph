package dev.everydaythings.graph.value.location;

import com.upokecenter.cbor.CBORObject;
import dev.everydaythings.graph.datum.Body;
import dev.everydaythings.graph.encoding.CgCbor;
import dev.everydaythings.graph.ref.ItemRef;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PostalAddressTest {

    @Nested
    @DisplayName("Construction")
    class Construction {

        @Test
        @DisplayName("US-style address with all common parts")
        void usAddress() {
            PostalAddress a = PostalAddress.builder()
                    .street("123 Main St")
                    .locality("Springfield")
                    .region("IL")
                    .postalCode("62701")
                    .country("US")
                    .build();
            assertThat(a.street()).contains("123 Main St");
            assertThat(a.locality()).contains("Springfield");
            assertThat(a.region()).contains("IL");
            assertThat(a.postalCode()).contains("62701");
            assertThat(a.country()).contains("US");
        }

        @Test
        @DisplayName("apartment via ExtendedAddress")
        void apartment() {
            PostalAddress a = PostalAddress.builder()
                    .street("456 Oak Ave")
                    .extendedAddress("Apt 3B")
                    .locality("Boston")
                    .region("MA")
                    .postalCode("02101")
                    .country("US")
                    .build();
            assertThat(a.extendedAddress()).contains("Apt 3B");
        }

        @Test
        @DisplayName("PO Box only")
        void poBox() {
            PostalAddress a = PostalAddress.builder()
                    .poBox("PO Box 1234")
                    .locality("Reston")
                    .region("VA")
                    .postalCode("20190")
                    .country("US")
                    .build();
            assertThat(a.poBox()).contains("PO Box 1234");
            assertThat(a.street()).isEmpty();
        }

        @Test
        @DisplayName("minimal address (locality + country)")
        void minimal() {
            PostalAddress a = PostalAddress.builder()
                    .locality("Paris")
                    .country("FR")
                    .build();
            assertThat(a.locality()).contains("Paris");
            assertThat(a.country()).contains("FR");
            assertThat(a.street()).isEmpty();
        }

        @Test
        @DisplayName("null and empty parts are silently dropped")
        void emptyDropped() {
            PostalAddress a = PostalAddress.builder()
                    .street("123 Main")
                    .extendedAddress(null)
                    .locality("")
                    .country("US")
                    .build();
            assertThat(a.street()).contains("123 Main");
            assertThat(a.extendedAddress()).isEmpty();
            assertThat(a.locality()).isEmpty();
            assertThat(a.country()).contains("US");
            assertThat(a.bindings()).hasSize(2);
        }

        @Test
        @DisplayName("custom part via part(roleKey, text)")
        void customPart() {
            String customRole = "cg.quality:postal-sub-locality";
            PostalAddress a = PostalAddress.builder()
                    .street("4-5-6 Akasaka")
                    .locality("Minato-ku")
                    .part(customRole, "Tokyo")
                    .country("JP")
                    .build();
            assertThat(a.bindings()).hasSize(4);
        }
    }

    @Nested
    @DisplayName("Storage")
    class Storage {

        @Test
        @DisplayName("PostalAddress is a structured (compound) Body")
        void structured() {
            PostalAddress a = PostalAddress.builder().street("X").build();
            assertThat(a.isAtomic()).isFalse();
        }

        @Test
        @DisplayName("head is the PostalAddress archetype IID")
        void headIsArchetype() {
            PostalAddress a = PostalAddress.builder().street("X").build();
            assertThat(a.head()).isEqualTo(ItemRef.iid(PostalAddress.KEY));
        }
    }

    @Nested
    @DisplayName("CID determinism")
    class CidDeterminism {

        @Test
        @DisplayName("two equal addresses produce identical CIDs")
        void deterministic() {
            PostalAddress a = PostalAddress.builder()
                    .street("123 Main St").locality("Springfield").country("US").build();
            PostalAddress b = PostalAddress.builder()
                    .street("123 Main St").locality("Springfield").country("US").build();
            assertThat(a.datumId()).isEqualTo(b.datumId());
        }

        @Test
        @DisplayName("builder call order doesn't affect CID")
        void orderIndependent() {
            PostalAddress a = PostalAddress.builder()
                    .country("US").street("123 Main St").locality("Springfield").build();
            PostalAddress b = PostalAddress.builder()
                    .street("123 Main St").locality("Springfield").country("US").build();
            assertThat(a.datumId()).isEqualTo(b.datumId());
        }

        @Test
        @DisplayName("different parts produce different CIDs")
        void differentPartsDifferentCids() {
            PostalAddress a = PostalAddress.builder().street("123 Main St").build();
            PostalAddress b = PostalAddress.builder().street("456 Oak Ave").build();
            assertThat(a.datumId()).isNotEqualTo(b.datumId());
        }
    }

    @Nested
    @DisplayName("Wire round-trip")
    class WireRoundTrip {

        @Test
        @DisplayName("encode + decode preserves all parts")
        void roundTrip() {
            PostalAddress original = PostalAddress.builder()
                    .street("123 Main St")
                    .extendedAddress("Apt 3B")
                    .locality("Springfield")
                    .region("IL")
                    .postalCode("62701")
                    .country("US")
                    .build();
            byte[] bytes = CgCbor.codec().encode(original);
            Body decoded = CgCbor.decodeBody(CBORObject.DecodeFromBytes(bytes));
            assertThat(decoded.head()).isEqualTo(ItemRef.iid(PostalAddress.KEY));
            assertThat(decoded.bindings()).hasSize(6);
            assertThat(decoded.datumId()).isEqualTo(original.datumId());

            PostalAddress recovered = PostalAddress.from(decoded);
            assertThat(recovered.street()).contains("123 Main St");
            assertThat(recovered.extendedAddress()).contains("Apt 3B");
            assertThat(recovered.locality()).contains("Springfield");
            assertThat(recovered.region()).contains("IL");
            assertThat(recovered.postalCode()).contains("62701");
            assertThat(recovered.country()).contains("US");
        }
    }
}
