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

class PhoneNumberTest {

    @Nested
    @DisplayName("Parsing and canonicalization")
    class Parsing {

        @Test
        @DisplayName("E.164 number parses verbatim")
        void e164Verbatim() {
            PhoneNumber p = PhoneNumber.fromText("+15551234567");
            assertThat(p.encodeText()).isEqualTo("+15551234567");
        }

        @Test
        @DisplayName("decorative characters stripped (spaces, hyphens, parens, dots)")
        void stripsDecorative() {
            assertThat(PhoneNumber.fromText("+1 (555) 123-4567").encodeText())
                    .isEqualTo("+15551234567");
            assertThat(PhoneNumber.fromText("+1.555.123.4567").encodeText())
                    .isEqualTo("+15551234567");
            assertThat(PhoneNumber.fromText("+1-555-123-4567").encodeText())
                    .isEqualTo("+15551234567");
        }

        @Test
        @DisplayName("surrounding whitespace stripped")
        void whitespaceStripped() {
            assertThat(PhoneNumber.fromText("  +15551234567  ").encodeText())
                    .isEqualTo("+15551234567");
        }

        @Test
        @DisplayName("international numbers (UK, JP, AU) parse")
        void international() {
            assertThat(PhoneNumber.fromText("+442071234567").encodeText())
                    .isEqualTo("+442071234567");
            assertThat(PhoneNumber.fromText("+81 3 1234 5678").encodeText())
                    .isEqualTo("+81312345678");
            assertThat(PhoneNumber.fromText("+61-2-9876-5432").encodeText())
                    .isEqualTo("+61298765432");
        }

        @Test
        @DisplayName("country-code best-effort accessor returns up to 3 digits after +")
        void countryCodeBestEffort() {
            assertThat(PhoneNumber.fromText("+15551234567").countryCodeBestEffort()).isEqualTo("155");
            assertThat(PhoneNumber.fromText("+442071234567").countryCodeBestEffort()).isEqualTo("442");
        }
    }

    @Nested
    @DisplayName("Validation")
    class Validation {

        @Test
        @DisplayName("rejects missing + prefix")
        void missingPlus() {
            assertThatThrownBy(() -> PhoneNumber.fromText("15551234567"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("E.164");
        }

        @Test
        @DisplayName("rejects letters")
        void rejectsLetters() {
            assertThatThrownBy(() -> PhoneNumber.fromText("+1-CALL-NOW"))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("rejects too-short numbers")
        void rejectsTooShort() {
            assertThatThrownBy(() -> PhoneNumber.fromText("+12345"))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("rejects too-long numbers (>15 digits)")
        void rejectsTooLong() {
            assertThatThrownBy(() -> PhoneNumber.fromText("+12345678901234567"))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("rejects null")
        void rejectsNull() {
            assertThatThrownBy(() -> PhoneNumber.fromText(null))
                    .isInstanceOf(NullPointerException.class);
        }
    }

    @Nested
    @DisplayName("Storage")
    class Storage {

        @Test
        @DisplayName("PhoneNumber is an atomic Body")
        void isAtomic() {
            PhoneNumber p = PhoneNumber.fromText("+15551234567");
            assertThat(p.isAtomic()).isTrue();
            assertThat(p.atomicContent()).contains("+15551234567");
        }

        @Test
        @DisplayName("head is the PhoneNumber archetype IID")
        void headIsArchetype() {
            PhoneNumber p = PhoneNumber.fromText("+15551234567");
            assertThat(p.head()).isEqualTo(ItemRef.iid(PhoneNumber.KEY));
        }
    }

    @Nested
    @DisplayName("CID determinism")
    class CidDeterminism {

        @Test
        @DisplayName("two equal numbers produce identical CIDs")
        void deterministic() {
            PhoneNumber a = PhoneNumber.fromText("+15551234567");
            PhoneNumber b = PhoneNumber.fromText("+15551234567");
            assertThat(a.datumId()).isEqualTo(b.datumId());
        }

        @Test
        @DisplayName("differently-formatted inputs canonicalize to same CID")
        void canonicalizationDedup() {
            PhoneNumber a = PhoneNumber.fromText("+15551234567");
            PhoneNumber b = PhoneNumber.fromText("+1 (555) 123-4567");
            PhoneNumber c = PhoneNumber.fromText("+1.555.123.4567");
            assertThat(a.datumId()).isEqualTo(b.datumId());
            assertThat(b.datumId()).isEqualTo(c.datumId());
        }

        @Test
        @DisplayName("different numbers produce different CIDs")
        void differentNumbersDifferentCids() {
            PhoneNumber a = PhoneNumber.fromText("+15551234567");
            PhoneNumber b = PhoneNumber.fromText("+15559876543");
            assertThat(a.datumId()).isNotEqualTo(b.datumId());
        }
    }

    @Nested
    @DisplayName("Wire round-trip")
    class WireRoundTrip {

        @Test
        @DisplayName("encode + decode preserves canonical text")
        void roundTrip() {
            PhoneNumber original = PhoneNumber.fromText("+15551234567");
            byte[] bytes = CgCbor.codec().encode(original);
            Body decoded = CgCbor.decodeBody(CBORObject.DecodeFromBytes(bytes));
            assertThat(decoded.isAtomic()).isTrue();
            assertThat(decoded.atomicContent()).contains("+15551234567");
            assertThat(decoded.head()).isEqualTo(ItemRef.iid(PhoneNumber.KEY));
            assertThat(decoded.datumId()).isEqualTo(original.datumId());
        }
    }
}
