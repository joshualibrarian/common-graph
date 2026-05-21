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

/**
 * EmailAddress — the first concrete atomic Identifier.  Storage shape is
 * head + canonical text, no fake VALUE binding.  Parse / round-trip / CID
 * dedup all work without ceremony.
 */
class EmailAddressTest {

    @Nested
    @DisplayName("Parsing and canonicalization")
    class Parsing {

        @Test
        @DisplayName("simple address parses and canonicalizes")
        void simple() {
            EmailAddress e = EmailAddress.fromText("alice@example.com");
            assertThat(e.encodeText()).isEqualTo("alice@example.com");
            assertThat(e.local()).isEqualTo("alice");
            assertThat(e.domain()).isEqualTo("example.com");
        }

        @Test
        @DisplayName("domain is lowercased")
        void domainLowercased() {
            EmailAddress e = EmailAddress.fromText("Alice@Example.COM");
            assertThat(e.encodeText()).isEqualTo("Alice@example.com");
            assertThat(e.domain()).isEqualTo("example.com");
            assertThat(e.local()).isEqualTo("Alice");
        }

        @Test
        @DisplayName("surrounding whitespace stripped")
        void whitespaceStripped() {
            EmailAddress e = EmailAddress.fromText("  alice@example.com  ");
            assertThat(e.encodeText()).isEqualTo("alice@example.com");
        }

        @Test
        @DisplayName("accepts plus-addressed local parts")
        void plusAddressing() {
            EmailAddress e = EmailAddress.fromText("alice+tag@example.com");
            assertThat(e.local()).isEqualTo("alice+tag");
            assertThat(e.domain()).isEqualTo("example.com");
        }

        @Test
        @DisplayName("accepts dotted local parts")
        void dottedLocal() {
            EmailAddress e = EmailAddress.fromText("alice.smith@example.co.uk");
            assertThat(e.local()).isEqualTo("alice.smith");
            assertThat(e.domain()).isEqualTo("example.co.uk");
        }
    }

    @Nested
    @DisplayName("Validation")
    class Validation {

        @Test
        @DisplayName("rejects missing @")
        void missingAt() {
            assertThatThrownBy(() -> EmailAddress.fromText("notanemail"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Not a valid email");
        }

        @Test
        @DisplayName("rejects empty domain")
        void emptyDomain() {
            assertThatThrownBy(() -> EmailAddress.fromText("alice@"))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("rejects empty local part")
        void emptyLocal() {
            assertThatThrownBy(() -> EmailAddress.fromText("@example.com"))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("rejects domain without TLD")
        void noTld() {
            assertThatThrownBy(() -> EmailAddress.fromText("alice@example"))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("rejects null input")
        void rejectsNull() {
            assertThatThrownBy(() -> EmailAddress.fromText(null))
                    .isInstanceOf(NullPointerException.class);
        }
    }

    @Nested
    @DisplayName("Storage shape")
    class Storage {

        @Test
        @DisplayName("EmailAddress is an atomic Body")
        void isAtomic() {
            EmailAddress e = EmailAddress.fromText("alice@example.com");
            assertThat(e.isAtomic()).isTrue();
            assertThat(e.atomicContent()).contains("alice@example.com");
            assertThat(e.bindings()).isEmpty();
            assertThat(e.entries()).isEmpty();
        }

        @Test
        @DisplayName("head is the EmailAddress archetype IID")
        void headIsArchetype() {
            EmailAddress e = EmailAddress.fromText("alice@example.com");
            assertThat(e.head()).isEqualTo(ItemRef.iid(EmailAddress.KEY));
        }
    }

    @Nested
    @DisplayName("CID determinism")
    class CidDeterminism {

        @Test
        @DisplayName("two equal emails produce identical CIDs")
        void deterministic() {
            EmailAddress a = EmailAddress.fromText("alice@example.com");
            EmailAddress b = EmailAddress.fromText("alice@example.com");
            assertThat(a.datumId()).isEqualTo(b.datumId());
        }

        @Test
        @DisplayName("case-different domains produce same CID after canonicalization")
        void caseDifferenceDedup() {
            EmailAddress a = EmailAddress.fromText("alice@example.com");
            EmailAddress b = EmailAddress.fromText("alice@EXAMPLE.COM");
            assertThat(a.datumId()).isEqualTo(b.datumId());
        }

        @Test
        @DisplayName("different addresses produce different CIDs")
        void differentAddressesDifferentCids() {
            EmailAddress a = EmailAddress.fromText("alice@example.com");
            EmailAddress b = EmailAddress.fromText("bob@example.com");
            assertThat(a.datumId()).isNotEqualTo(b.datumId());
        }
    }

    @Nested
    @DisplayName("Wire round-trip")
    class WireRoundTrip {

        @Test
        @DisplayName("encode + decode round-trips through CBOR")
        void roundTrip() {
            EmailAddress original = EmailAddress.fromText("alice@example.com");
            byte[] bytes = CgCbor.codec().encode(original);
            Body decoded = CgCbor.decodeBody(CBORObject.DecodeFromBytes(bytes));
            assertThat(decoded.isAtomic()).isTrue();
            assertThat(decoded.atomicContent()).contains("alice@example.com");
            assertThat(decoded.head()).isEqualTo(ItemRef.iid(EmailAddress.KEY));
            assertThat(decoded.datumId()).isEqualTo(original.datumId());
        }
    }
}
