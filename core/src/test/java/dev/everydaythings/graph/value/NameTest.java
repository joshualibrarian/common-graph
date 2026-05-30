package dev.everydaythings.graph.value;

import com.upokecenter.cbor.CBORObject;
import dev.everydaythings.graph.datum.Body;
import dev.everydaythings.graph.encoding.CgCbor;
import dev.everydaythings.graph.ref.ItemRef;
import dev.everydaythings.graph.value.identifier.Alias;
import dev.everydaythings.graph.value.identifier.FamilyName;
import dev.everydaythings.graph.value.identifier.FullName;
import dev.everydaythings.graph.value.identifier.GivenName;
import dev.everydaythings.graph.value.identifier.Handle;
import dev.everydaythings.graph.value.identifier.Honorific;
import dev.everydaythings.graph.value.identifier.Maternal;
import dev.everydaythings.graph.value.identifier.MiddleName;
import dev.everydaythings.graph.value.identifier.Name;
import dev.everydaythings.graph.value.identifier.Nickname;
import dev.everydaythings.graph.value.identifier.Patronymic;
import dev.everydaythings.graph.value.identifier.Pseudonym;
import dev.everydaythings.graph.value.identifier.Suffix;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for the Name hierarchy: atomic Name subtypes (GivenName,
 * FamilyName, Nickname, Alias, Pseudonym, Handle, Honorific, Suffix,
 * Patronymic, Maternal) and the compound {@link FullName}.
 */
class NameTest {

    @Nested
    @DisplayName("Atomic Name subtypes")
    class Atomic {

        @Test
        @DisplayName("GivenName carries its text in atomic-body content")
        void givenName() {
            GivenName g = GivenName.of("Joshua");
            assertThat(g.encodeText()).isEqualTo("Joshua");
            assertThat(g.isAtomic()).isTrue();
            assertThat(g.head()).isEqualTo(ItemRef.iid(GivenName.KEY));
        }

        @Test
        @DisplayName("trims whitespace on construction")
        void trims() {
            assertThat(GivenName.of("  Joshua  ").encodeText()).isEqualTo("Joshua");
        }

        @Test
        @DisplayName("rejects empty and whitespace-only input")
        void rejectsEmpty() {
            assertThatThrownBy(() -> GivenName.of(""))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("empty");
            assertThatThrownBy(() -> GivenName.of("   "))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("empty");
        }

        @Test
        @DisplayName("rejects control characters")
        void rejectsControl() {
            assertThatThrownBy(() -> GivenName.of("Joshua"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("control");
        }

        @Test
        @DisplayName("rejects oversize input")
        void rejectsOversize() {
            String huge = "x".repeat(Name.MAX_LENGTH + 1);
            assertThatThrownBy(() -> GivenName.of(huge))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("length");
        }

        @Test
        @DisplayName("each subtype has its own archetype IID — same text in different subtypes hash differently")
        void typedIdentity() {
            GivenName g = GivenName.of("Madonna");
            Nickname n = Nickname.of("Madonna");
            assertThat(g.datumId()).isNotEqualTo(n.datumId());
            assertThat(g.head()).isNotEqualTo(n.head());
        }

        @Test
        @DisplayName("two instances with identical text share a CID")
        void dedup() {
            GivenName a = GivenName.of("Joshua");
            GivenName b = GivenName.of("Joshua");
            assertThat(a.datumId()).isEqualTo(b.datumId());
        }

        @Test
        @DisplayName("All ten atomic Name subtypes construct and encode")
        void allSubtypes() {
            assertThat(GivenName.of("Joshua").encodeText()).isEqualTo("Joshua");
            assertThat(FamilyName.of("Chambers").encodeText()).isEqualTo("Chambers");
            assertThat(MiddleName.of("Brian").encodeText()).isEqualTo("Brian");
            assertThat(Nickname.of("Josh").encodeText()).isEqualTo("Josh");
            assertThat(Alias.of("Bob Dylan").encodeText()).isEqualTo("Bob Dylan");
            assertThat(Pseudonym.of("Mark Twain").encodeText()).isEqualTo("Mark Twain");
            assertThat(Honorific.of("Dr.").encodeText()).isEqualTo("Dr.");
            assertThat(Suffix.of("Jr.").encodeText()).isEqualTo("Jr.");
            assertThat(Patronymic.of("Petrovich").encodeText()).isEqualTo("Petrovich");
            assertThat(Maternal.of("Pérez").encodeText()).isEqualTo("Pérez");
        }
    }

    @Nested
    @DisplayName("Handle")
    class HandleTests {

        @Test
        @DisplayName("Handle stores plain text without prefix")
        void plainText() {
            Handle h = Handle.of("joshua-c");
            assertThat(h.encodeText()).isEqualTo("joshua-c");
        }

        @Test
        @DisplayName("leading @ is preserved (treated as part of the handle text)")
        void leadingAtPreserved() {
            // We don't strip; the @ is platform-display convention, not data.
            // If the caller passes "@handle" it's preserved verbatim.
            Handle h = Handle.of("@joshua-c");
            assertThat(h.encodeText()).isEqualTo("@joshua-c");
        }
    }

    @Nested
    @DisplayName("FullName compound")
    class Compound {

        @Test
        @DisplayName("Western: honorific + given + middle + family + suffix")
        void western() {
            FullName n = FullName.builder()
                    .honorific("Dr.")
                    .given("Joshua")
                    .middle("Brian")
                    .family("Chambers")
                    .suffix("Jr.")
                    .build();
            assertThat(n.encodeText()).isEqualTo("Dr. Joshua Brian Chambers Jr.");
            assertThat(n.bindings()).hasSize(5);
            assertThat(n.isAtomic()).isFalse();
        }

        @Test
        @DisplayName("East Asian: family + given (render order picks order)")
        void eastAsian() {
            FullName n = FullName.builder()
                    .family("Wang")
                    .given("Wei")
                    .build();
            assertThat(n.encodeText()).isEqualTo("Wang Wei");
        }

        @Test
        @DisplayName("Spanish: given + paternal family + maternal")
        void spanish() {
            FullName n = FullName.builder()
                    .given("María")
                    .family("González")
                    .maternal("Pérez")
                    .build();
            assertThat(n.encodeText()).isEqualTo("María González Pérez");
        }

        @Test
        @DisplayName("Mononymous: a single given name")
        void mononymous() {
            FullName n = FullName.builder().given("Madonna").build();
            assertThat(n.encodeText()).isEqualTo("Madonna");
            assertThat(n.bindings()).hasSize(1);
        }

        @Test
        @DisplayName("with(Name) accepts any pre-built Name subtype")
        void withTypedPart() {
            FullName n = FullName.builder()
                    .with(GivenName.of("Joshua"))
                    .with(FamilyName.of("Chambers"))
                    .build();
            assertThat(n.encodeText()).isEqualTo("Joshua Chambers");
        }

        @Test
        @DisplayName("FullName head is the FullName archetype")
        void headIsArchetype() {
            FullName n = FullName.builder().given("Joshua").family("Chambers").build();
            assertThat(n.head()).isEqualTo(ItemRef.iid(FullName.KEY));
        }

        @Test
        @DisplayName("empty FullName is rejected")
        void emptyRejected() {
            assertThatThrownBy(() -> FullName.builder().build())
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("at least one");
        }
    }

    @Nested
    @DisplayName("CID determinism")
    class Determinism {

        @Test
        @DisplayName("FullName with same parts in same order has same CID")
        void sameOrderSameCid() {
            FullName a = FullName.builder().given("Joshua").family("Chambers").build();
            FullName b = FullName.builder().given("Joshua").family("Chambers").build();
            assertThat(a.datumId()).isEqualTo(b.datumId());
        }

        @Test
        @DisplayName("FullName with parts in different order has different CID (order is structural)")
        void differentOrderDifferentCid() {
            FullName western = FullName.builder().given("Wei").family("Wang").build();
            FullName eastern = FullName.builder().family("Wang").given("Wei").build();
            assertThat(western.datumId()).isNotEqualTo(eastern.datumId());
        }

        @Test
        @DisplayName("Atomic part bodies dedup independently of which FullName contains them")
        void partsDedup() {
            FullName a = FullName.builder().given("Joshua").family("Chambers").build();
            FullName b = FullName.builder().given("Joshua").family("Smith").build();
            // Both have a GivenName("Joshua") part — same body.  Bindings are
            // canonical-sorted; find the GivenName target by type.
            GivenName partA = findFirst(a, GivenName.class);
            GivenName partB = findFirst(b, GivenName.class);
            assertThat(partA.datumId()).isEqualTo(partB.datumId());
        }

        private static <T extends Name> T findFirst(FullName fn, Class<T> type) {
            return fn.bindings().stream()
                    .map(b -> b.target())
                    .filter(type::isInstance)
                    .map(type::cast)
                    .findFirst()
                    .orElseThrow();
        }
    }

    @Nested
    @DisplayName("Wire round-trip")
    class WireRoundTrip {

        @Test
        @DisplayName("Atomic GivenName round-trips through CBOR")
        void atomicRoundTrip() {
            GivenName original = GivenName.of("Joshua");
            byte[] bytes = CgCbor.codec().encode(original);
            Body decoded = CgCbor.decodeBody(CBORObject.DecodeFromBytes(bytes));
            assertThat(decoded.head()).isEqualTo(ItemRef.iid(GivenName.KEY));
            assertThat(decoded.datumId()).isEqualTo(original.datumId());
            assertThat(decoded.atomicContent()).contains("Joshua");
        }

        @Test
        @DisplayName("FullName compound round-trips through CBOR")
        void compoundRoundTrip() {
            FullName original = FullName.builder()
                    .given("Joshua")
                    .middle("Brian")
                    .family("Chambers")
                    .build();
            byte[] bytes = CgCbor.codec().encode(original);
            Body decoded = CgCbor.decodeBody(CBORObject.DecodeFromBytes(bytes));
            assertThat(decoded.head()).isEqualTo(ItemRef.iid(FullName.KEY));
            assertThat(decoded.bindings()).hasSize(3);
            assertThat(decoded.datumId()).isEqualTo(original.datumId());
        }
    }
}
