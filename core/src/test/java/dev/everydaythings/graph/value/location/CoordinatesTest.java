package dev.everydaythings.graph.value.location;

import com.upokecenter.cbor.CBORObject;
import dev.everydaythings.graph.datum.Body;
import dev.everydaythings.graph.encoding.CgCbor;
import dev.everydaythings.graph.ref.ItemRef;
import dev.everydaythings.graph.value.Rational;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CoordinatesTest {

    @Nested
    @DisplayName("Construction")
    class Construction {

        @Test
        @DisplayName("lat + long via double convenience factory")
        void doubleFactory() {
            Coordinates c = Coordinates.of(39.7817, -89.6501);
            assertThat(c.latitude()).isPresent();
            assertThat(c.longitude()).isPresent();
            assertThat(c.altitude()).isEmpty();
        }

        @Test
        @DisplayName("lat + long + altitude")
        void withAltitude() {
            Coordinates c = Coordinates.of(39.7817, -89.6501, 180.0);
            assertThat(c.latitude()).isPresent();
            assertThat(c.longitude()).isPresent();
            assertThat(c.altitude()).isPresent();
        }

        @Test
        @DisplayName("via builder + Rational")
        void builderRational() {
            Coordinates c = Coordinates.builder()
                    .latitude(Rational.of(BigInteger.valueOf(397817), BigInteger.valueOf(10000)))
                    .longitude(Rational.of(BigInteger.valueOf(-896501), BigInteger.valueOf(10000)))
                    .build();
            assertThat(c.latitude()).isPresent();
            assertThat(c.longitude()).isPresent();
        }

        @Test
        @DisplayName("rejects latitude out of range")
        void rejectsLatOutOfRange() {
            assertThatThrownBy(() -> Coordinates.of(91.0, 0.0))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Latitude");
            assertThatThrownBy(() -> Coordinates.of(-91.0, 0.0))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("rejects longitude out of range")
        void rejectsLngOutOfRange() {
            assertThatThrownBy(() -> Coordinates.of(0.0, 181.0))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Longitude");
            assertThatThrownBy(() -> Coordinates.of(0.0, -181.0))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("accepts boundary values (±90, ±180)")
        void boundaries() {
            assertThat(Coordinates.of(90.0, 180.0).latitude()).isPresent();
            assertThat(Coordinates.of(-90.0, -180.0).longitude()).isPresent();
        }
    }

    @Nested
    @DisplayName("Storage")
    class Storage {

        @Test
        @DisplayName("Coordinates is a structured (compound) Body")
        void structured() {
            Coordinates c = Coordinates.of(0.0, 0.0);
            assertThat(c.isAtomic()).isFalse();
            assertThat(c.bindings()).hasSize(2);
        }

        @Test
        @DisplayName("head is the Coordinates archetype IID")
        void headIsArchetype() {
            Coordinates c = Coordinates.of(0.0, 0.0);
            assertThat(c.head()).isEqualTo(ItemRef.iid(Coordinates.KEY));
        }
    }

    @Nested
    @DisplayName("CID determinism")
    class CidDeterminism {

        @Test
        @DisplayName("two equal coordinates produce identical CIDs")
        void deterministic() {
            Coordinates a = Coordinates.of(39.7817, -89.6501);
            Coordinates b = Coordinates.of(39.7817, -89.6501);
            assertThat(a.datumId()).isEqualTo(b.datumId());
        }

        @Test
        @DisplayName("different points produce different CIDs")
        void differentPointsDifferentCids() {
            Coordinates a = Coordinates.of(39.7817, -89.6501);
            Coordinates b = Coordinates.of(40.0000, -89.6501);
            assertThat(a.datumId()).isNotEqualTo(b.datumId());
        }

        @Test
        @DisplayName("altitude affects CID")
        void altitudeAffectsCid() {
            Coordinates a = Coordinates.of(39.7817, -89.6501);
            Coordinates b = Coordinates.of(39.7817, -89.6501, 100.0);
            assertThat(a.datumId()).isNotEqualTo(b.datumId());
        }
    }

    @Nested
    @DisplayName("Wire round-trip")
    class WireRoundTrip {

        @Test
        @DisplayName("encode + decode preserves all components")
        void roundTrip() {
            Coordinates original = Coordinates.of(39.7817, -89.6501, 180.5);
            byte[] bytes = CgCbor.codec().encode(original);
            Body decoded = CgCbor.decodeBody(CBORObject.DecodeFromBytes(bytes));
            assertThat(decoded.head()).isEqualTo(ItemRef.iid(Coordinates.KEY));
            assertThat(decoded.bindings()).hasSize(3);
            assertThat(decoded.datumId()).isEqualTo(original.datumId());

            Coordinates recovered = Coordinates.from(decoded);
            assertThat(recovered.latitude()).isPresent();
            assertThat(recovered.longitude()).isPresent();
            assertThat(recovered.altitude()).isPresent();
        }
    }
}
