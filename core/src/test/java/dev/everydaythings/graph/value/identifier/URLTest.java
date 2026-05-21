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

class URLTest {

    @Nested
    @DisplayName("Parsing and canonicalization")
    class Parsing {

        @Test
        @DisplayName("https URL parses verbatim")
        void httpsVerbatim() {
            URL u = URL.fromText("https://example.com/path");
            assertThat(u.encodeText()).isEqualTo("https://example.com/path");
            assertThat(u.scheme()).isEqualTo("https");
            assertThat(u.host()).isEqualTo("example.com");
            assertThat(u.path()).isEqualTo("/path");
        }

        @Test
        @DisplayName("scheme lowercased")
        void schemeLowercased() {
            assertThat(URL.fromText("HTTPS://example.com/").scheme()).isEqualTo("https");
            assertThat(URL.fromText("HTTP://example.com/").scheme()).isEqualTo("http");
        }

        @Test
        @DisplayName("host lowercased")
        void hostLowercased() {
            assertThat(URL.fromText("https://Example.COM/path").host()).isEqualTo("example.com");
        }

        @Test
        @DisplayName("default ports stripped")
        void defaultPortsStripped() {
            assertThat(URL.fromText("http://example.com:80/").encodeText())
                    .isEqualTo("http://example.com/");
            assertThat(URL.fromText("https://example.com:443/").encodeText())
                    .isEqualTo("https://example.com/");
        }

        @Test
        @DisplayName("non-default ports preserved")
        void nonDefaultPortPreserved() {
            assertThat(URL.fromText("http://example.com:8080/").encodeText())
                    .isEqualTo("http://example.com:8080/");
        }

        @Test
        @DisplayName("path is case-preserved")
        void pathCasePreserved() {
            assertThat(URL.fromText("https://example.com/PathWithCase").path())
                    .isEqualTo("/PathWithCase");
        }

        @Test
        @DisplayName("query and fragment preserved")
        void queryAndFragmentPreserved() {
            URL u = URL.fromText("https://example.com/x?q=1&z=2#section");
            assertThat(u.encodeText()).isEqualTo("https://example.com/x?q=1&z=2#section");
        }

        @Test
        @DisplayName("opaque URI (mailto:) parses with lowercased scheme")
        void opaqueMailto() {
            URL u = URL.fromText("MAILTO:alice@example.com");
            assertThat(u.encodeText()).isEqualTo("mailto:alice@example.com");
            assertThat(u.scheme()).isEqualTo("mailto");
        }

        @Test
        @DisplayName("surrounding whitespace stripped")
        void whitespaceStripped() {
            assertThat(URL.fromText("  https://example.com/  ").encodeText())
                    .isEqualTo("https://example.com/");
        }
    }

    @Nested
    @DisplayName("Validation")
    class Validation {

        @Test
        @DisplayName("rejects empty string")
        void rejectsEmpty() {
            assertThatThrownBy(() -> URL.fromText(""))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("rejects relative URL (no scheme)")
        void rejectsRelative() {
            assertThatThrownBy(() -> URL.fromText("/path/only"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("scheme");
        }

        @Test
        @DisplayName("rejects bare host without scheme")
        void rejectsBareHost() {
            assertThatThrownBy(() -> URL.fromText("example.com"))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("rejects malformed input")
        void rejectsMalformed() {
            assertThatThrownBy(() -> URL.fromText("ht tp://broken url"))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("rejects null")
        void rejectsNull() {
            assertThatThrownBy(() -> URL.fromText(null))
                    .isInstanceOf(NullPointerException.class);
        }
    }

    @Nested
    @DisplayName("Storage")
    class Storage {

        @Test
        @DisplayName("URL is an atomic Body")
        void isAtomic() {
            URL u = URL.fromText("https://example.com/");
            assertThat(u.isAtomic()).isTrue();
            assertThat(u.atomicContent()).contains("https://example.com/");
        }

        @Test
        @DisplayName("head is the URL archetype IID")
        void headIsArchetype() {
            URL u = URL.fromText("https://example.com/");
            assertThat(u.head()).isEqualTo(ItemRef.iid(URL.KEY));
        }
    }

    @Nested
    @DisplayName("CID determinism")
    class CidDeterminism {

        @Test
        @DisplayName("two equal URLs produce identical CIDs")
        void deterministic() {
            URL a = URL.fromText("https://example.com/path");
            URL b = URL.fromText("https://example.com/path");
            assertThat(a.datumId()).isEqualTo(b.datumId());
        }

        @Test
        @DisplayName("canonicalization dedups case-different scheme/host")
        void canonicalizationDedup() {
            URL a = URL.fromText("https://example.com/path");
            URL b = URL.fromText("HTTPS://Example.COM/path");
            assertThat(a.datumId()).isEqualTo(b.datumId());
        }

        @Test
        @DisplayName("canonicalization dedups default-port-stripped forms")
        void defaultPortDedup() {
            URL a = URL.fromText("https://example.com/");
            URL b = URL.fromText("https://example.com:443/");
            assertThat(a.datumId()).isEqualTo(b.datumId());
        }

        @Test
        @DisplayName("different paths produce different CIDs")
        void differentPathsDifferentCids() {
            URL a = URL.fromText("https://example.com/a");
            URL b = URL.fromText("https://example.com/b");
            assertThat(a.datumId()).isNotEqualTo(b.datumId());
        }
    }

    @Nested
    @DisplayName("Wire round-trip")
    class WireRoundTrip {

        @Test
        @DisplayName("encode + decode preserves canonical text")
        void roundTrip() {
            URL original = URL.fromText("https://Example.COM:443/Path?q=1#frag");
            byte[] bytes = CgCbor.codec().encode(original);
            Body decoded = CgCbor.decodeBody(CBORObject.DecodeFromBytes(bytes));
            assertThat(decoded.isAtomic()).isTrue();
            assertThat(decoded.head()).isEqualTo(ItemRef.iid(URL.KEY));
            assertThat(decoded.atomicContent()).contains("https://example.com/Path?q=1#frag");
            assertThat(decoded.datumId()).isEqualTo(original.datumId());
        }
    }
}
