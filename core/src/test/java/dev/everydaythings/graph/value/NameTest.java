package dev.everydaythings.graph.value;

import com.upokecenter.cbor.CBORObject;
import dev.everydaythings.graph.datum.Body;
import dev.everydaythings.graph.encoding.CgCbor;
import dev.everydaythings.graph.ref.ItemRef;
import dev.everydaythings.graph.value.identifier.Name;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class NameTest {

    @Nested
    @DisplayName("Construction")
    class Construction {

        @Test
        @DisplayName("Western full name (given + family)")
        void westernFullName() {
            Name n = Name.of("Joshua", "Chambers");
            assertThat(n.given()).contains("Joshua");
            assertThat(n.family()).contains("Chambers");
            assertThat(n.middle()).isEmpty();
            assertThat(n.nickname()).isEmpty();
        }

        @Test
        @DisplayName("name with middle, nickname, suffix")
        void multiplePartsViaBuilder() {
            Name n = Name.builder()
                    .given("Joshua")
                    .middle("Brian")
                    .family("Chambers")
                    .nickname("josh")
                    .suffix("Jr.")
                    .build();
            assertThat(n.given()).contains("Joshua");
            assertThat(n.middle()).contains("Brian");
            assertThat(n.family()).contains("Chambers");
            assertThat(n.nickname()).contains("josh");
            assertThat(n.suffix()).contains("Jr.");
            assertThat(n.honorific()).isEmpty();
        }

        @Test
        @DisplayName("mononymous (just given)")
        void mononymous() {
            Name n = Name.builder().given("Madonna").build();
            assertThat(n.given()).contains("Madonna");
            assertThat(n.family()).isEmpty();
        }

        @Test
        @DisplayName("Spanish dual-surname (given + family + maternal)")
        void spanishDualSurname() {
            Name n = Name.builder()
                    .given("Gabriel")
                    .family("García")
                    .maternal("Márquez")
                    .build();
            assertThat(n.given()).contains("Gabriel");
            assertThat(n.family()).contains("García");
            assertThat(n.maternal()).contains("Márquez");
        }

        @Test
        @DisplayName("Slavic name (given + patronymic + family)")
        void slavicName() {
            Name n = Name.builder()
                    .given("Ivan")
                    .patronymic("Petrovich")
                    .family("Sidorov")
                    .build();
            assertThat(n.given()).contains("Ivan");
            assertThat(n.patronymic()).contains("Petrovich");
            assertThat(n.family()).contains("Sidorov");
        }

        @Test
        @DisplayName("honorific + given + family + suffix")
        void honorificAndSuffix() {
            Name n = Name.builder()
                    .honorific("Dr.")
                    .given("Jane")
                    .family("Smith")
                    .suffix("PhD")
                    .build();
            assertThat(n.honorific()).contains("Dr.");
            assertThat(n.suffix()).contains("PhD");
        }

        @Test
        @DisplayName("null and empty parts are silently dropped")
        void emptyPartsDropped() {
            Name n = Name.builder()
                    .given("Joshua")
                    .middle(null)
                    .family("")
                    .build();
            assertThat(n.given()).contains("Joshua");
            assertThat(n.middle()).isEmpty();
            assertThat(n.family()).isEmpty();
            assertThat(n.bindings()).hasSize(1);
        }

        @Test
        @DisplayName("custom name-part role via part(roleKey, text)")
        void customNamePart() {
            String customRole = "cg.quality:name-clan";
            Name n = Name.builder()
                    .given("Jin")
                    .family("Park")
                    .part(customRole, "Bak-shi")
                    .build();
            assertThat(n.given()).contains("Jin");
            assertThat(n.bindings()).hasSize(3);
        }
    }

    @Nested
    @DisplayName("Storage shape")
    class Storage {

        @Test
        @DisplayName("Name is a structured (non-atomic) Body")
        void structured() {
            Name n = Name.of("Joshua", "Chambers");
            assertThat(n.isAtomic()).isFalse();
            assertThat(n.bindings()).hasSize(2);
        }

        @Test
        @DisplayName("head is the Name archetype IID")
        void headIsArchetype() {
            Name n = Name.of("Joshua", "Chambers");
            assertThat(n.head()).isEqualTo(ItemRef.iid(Name.KEY));
        }
    }

    @Nested
    @DisplayName("CID determinism")
    class CidDeterminism {

        @Test
        @DisplayName("two equal Names produce identical CIDs")
        void deterministic() {
            Name a = Name.of("Joshua", "Chambers");
            Name b = Name.of("Joshua", "Chambers");
            assertThat(a.datumId()).isEqualTo(b.datumId());
        }

        @Test
        @DisplayName("different parts produce different CIDs")
        void differentPartsDifferentCids() {
            Name a = Name.of("Joshua", "Chambers");
            Name b = Name.of("Bob",    "Chambers");
            assertThat(a.datumId()).isNotEqualTo(b.datumId());
        }

        @Test
        @DisplayName("ordering of builder calls doesn't affect CID")
        void orderIndependent() {
            Name a = Name.builder().given("Joshua").family("Chambers").build();
            Name b = Name.builder().family("Chambers").given("Joshua").build();
            assertThat(a.datumId()).isEqualTo(b.datumId());
        }

        @Test
        @DisplayName("partial Names that share parts produce same CID")
        void sharedPartsSameCid() {
            Name a = Name.builder().given("Madonna").build();
            Name b = Name.builder().given("Madonna").build();
            assertThat(a.datumId()).isEqualTo(b.datumId());
        }
    }

    @Nested
    @DisplayName("Wire round-trip")
    class WireRoundTrip {

        @Test
        @DisplayName("encode + decode preserves all parts")
        void roundTrip() {
            Name original = Name.builder()
                    .given("Joshua")
                    .middle("Brian")
                    .family("Chambers")
                    .nickname("josh")
                    .build();
            byte[] bytes = CgCbor.codec().encode(original);
            Body decoded = CgCbor.decodeBody(CBORObject.DecodeFromBytes(bytes));
            assertThat(decoded.head()).isEqualTo(ItemRef.iid(Name.KEY));
            assertThat(decoded.bindings()).hasSize(4);
            assertThat(decoded.datumId()).isEqualTo(original.datumId());

            // Project back to a Name view and verify accessors
            Name recovered = Name.from(decoded);
            assertThat(recovered.given()).contains("Joshua");
            assertThat(recovered.middle()).contains("Brian");
            assertThat(recovered.family()).contains("Chambers");
            assertThat(recovered.nickname()).contains("josh");
        }
    }
}
