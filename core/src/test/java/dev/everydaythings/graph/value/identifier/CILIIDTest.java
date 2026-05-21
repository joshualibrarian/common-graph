package dev.everydaythings.graph.value.identifier;

import com.upokecenter.cbor.CBORObject;
import dev.everydaythings.graph.datum.Body;
import dev.everydaythings.graph.encoding.CgCbor;
import dev.everydaythings.graph.ref.ItemRef;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CILIIDTest {

    @Nested
    @DisplayName("Parsing")
    class Parsing {

        @Test
        @DisplayName("simple CILI id parses")
        void simple() {
            CILIID c = CILIID.fromText("i69788");
            assertThat(c.encodeText()).isEqualTo("i69788");
            assertThat(c.number()).isEqualTo(69788);
        }

        @Test
        @DisplayName("whitespace stripped")
        void whitespaceStripped() {
            CILIID c = CILIID.fromText("  i12345  ");
            assertThat(c.encodeText()).isEqualTo("i12345");
        }

        @Test
        @DisplayName("rejects missing 'i' prefix")
        void rejectsNoPrefix() {
            assertThatThrownBy(() -> CILIID.fromText("69788"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Not a valid CILI");
        }

        @Test
        @DisplayName("rejects non-digit content")
        void rejectsNonDigit() {
            assertThatThrownBy(() -> CILIID.fromText("iABC"))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("rejects empty number portion")
        void rejectsEmptyNumber() {
            assertThatThrownBy(() -> CILIID.fromText("i"))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("Storage")
    class Storage {

        @Test
        @DisplayName("CILIID is an atomic Body")
        void isAtomic() {
            CILIID c = CILIID.fromText("i69788");
            assertThat(c.isAtomic()).isTrue();
            assertThat(c.atomicContent()).contains("i69788");
            assertThat(c.bindings()).isEmpty();
        }

        @Test
        @DisplayName("head is the CILIID archetype IID")
        void headIsArchetype() {
            CILIID c = CILIID.fromText("i69788");
            assertThat(c.head()).isEqualTo(ItemRef.iid(CILIID.KEY));
        }
    }

    @Nested
    @DisplayName("CID determinism")
    class CidDeterminism {

        @Test
        @DisplayName("two equal CILIIDs produce identical CIDs")
        void deterministic() {
            CILIID a = CILIID.fromText("i69788");
            CILIID b = CILIID.fromText("i69788");
            assertThat(a.datumId()).isEqualTo(b.datumId());
        }

        @Test
        @DisplayName("different ids produce different CIDs")
        void differentIdsDifferentCids() {
            CILIID a = CILIID.fromText("i69788");
            CILIID b = CILIID.fromText("i69761");
            assertThat(a.datumId()).isNotEqualTo(b.datumId());
        }
    }

    @Nested
    @DisplayName("Wire round-trip")
    class WireRoundTrip {

        @Test
        @DisplayName("encode + decode round-trips through CBOR")
        void roundTrip() {
            CILIID original = CILIID.fromText("i69788");
            byte[] bytes = CgCbor.codec().encode(original);
            Body decoded = CgCbor.decodeBody(CBORObject.DecodeFromBytes(bytes));
            assertThat(decoded.isAtomic()).isTrue();
            assertThat(decoded.atomicContent()).contains("i69788");
            assertThat(decoded.head()).isEqualTo(ItemRef.iid(CILIID.KEY));
            assertThat(decoded.datumId()).isEqualTo(original.datumId());
        }
    }
}
