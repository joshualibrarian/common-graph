package dev.everydaythings.graph.item.mount;

import com.upokecenter.cbor.CBORObject;
import dev.everydaythings.graph.Canonical;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * Tests for SpatialMount with rotation quaternion support.
 */
@DisplayName("SpatialMount")
class SpatialMountTest {

    // ==================================================================================
    // Position-Only Construction
    // ==================================================================================

    @Nested
    @DisplayName("position-only constructor")
    class PositionOnly {

        @Test
        @DisplayName("creates identity rotation")
        void identityRotation() {
            var mount = new Mount.SpatialMount(1, 2, 3);

            assertThat(mount.x()).isEqualTo(1);
            assertThat(mount.y()).isEqualTo(2);
            assertThat(mount.z()).isEqualTo(3);
            assertThat(mount.qx()).isEqualTo(0);
            assertThat(mount.qy()).isEqualTo(0);
            assertThat(mount.qz()).isEqualTo(0);
            assertThat(mount.qw()).isEqualTo(1);
        }
    }

    // ==================================================================================
    // Euler Angle Construction
    // ==================================================================================

    @Nested
    @DisplayName("fromEuler")
    class FromEuler {

        @Test
        @DisplayName("zero euler angles produce identity rotation")
        void zeroEuler() {
            var mount = Mount.SpatialMount.fromEuler(1, 2, 3, 0, 0, 0);

            assertThat(mount.qx()).isCloseTo(0, within(1e-10));
            assertThat(mount.qy()).isCloseTo(0, within(1e-10));
            assertThat(mount.qz()).isCloseTo(0, within(1e-10));
            assertThat(mount.qw()).isCloseTo(1, within(1e-10));
        }

        @Test
        @DisplayName("90-degree yaw rotates around Y axis")
        void yaw90() {
            var mount = Mount.SpatialMount.fromEuler(0, 0, 0, 90, 0, 0);

            // 90-degree yaw: qy = sin(45°) ≈ 0.7071, qw = cos(45°) ≈ 0.7071
            assertThat(mount.qx()).isCloseTo(0, within(1e-10));
            assertThat(mount.qy()).isCloseTo(Math.sin(Math.PI / 4), within(1e-10));
            assertThat(mount.qz()).isCloseTo(0, within(1e-10));
            assertThat(mount.qw()).isCloseTo(Math.cos(Math.PI / 4), within(1e-10));
        }

        @Test
        @DisplayName("preserves position")
        void preservesPosition() {
            var mount = Mount.SpatialMount.fromEuler(5, 10, 15, 45, 30, 60);

            assertThat(mount.x()).isEqualTo(5);
            assertThat(mount.y()).isEqualTo(10);
            assertThat(mount.z()).isEqualTo(15);
        }

        @Test
        @DisplayName("produces unit quaternion")
        void unitQuaternion() {
            var mount = Mount.SpatialMount.fromEuler(0, 0, 0, 45, 30, 60);

            double len = Math.sqrt(mount.qx() * mount.qx() + mount.qy() * mount.qy()
                    + mount.qz() * mount.qz() + mount.qw() * mount.qw());
            assertThat(len).isCloseTo(1.0, within(1e-10));
        }
    }

    // ==================================================================================
    // Axis-Angle Construction
    // ==================================================================================

    @Nested
    @DisplayName("fromAxisAngle")
    class FromAxisAngle {

        @Test
        @DisplayName("zero angle produces identity rotation")
        void zeroAngle() {
            var mount = Mount.SpatialMount.fromAxisAngle(0, 0, 0, 0, 1, 0, 0);

            assertThat(mount.qx()).isCloseTo(0, within(1e-10));
            assertThat(mount.qy()).isCloseTo(0, within(1e-10));
            assertThat(mount.qz()).isCloseTo(0, within(1e-10));
            assertThat(mount.qw()).isCloseTo(1, within(1e-10));
        }

        @Test
        @DisplayName("90-degree rotation around Y axis")
        void yAxis90() {
            var mount = Mount.SpatialMount.fromAxisAngle(0, 0, 0, 0, 1, 0, 90);

            assertThat(mount.qx()).isCloseTo(0, within(1e-10));
            assertThat(mount.qy()).isCloseTo(Math.sin(Math.PI / 4), within(1e-10));
            assertThat(mount.qz()).isCloseTo(0, within(1e-10));
            assertThat(mount.qw()).isCloseTo(Math.cos(Math.PI / 4), within(1e-10));
        }

        @Test
        @DisplayName("degenerate zero-length axis produces identity")
        void degenerateAxis() {
            var mount = Mount.SpatialMount.fromAxisAngle(1, 2, 3, 0, 0, 0, 90);

            assertThat(mount.qx()).isEqualTo(0);
            assertThat(mount.qy()).isEqualTo(0);
            assertThat(mount.qz()).isEqualTo(0);
            assertThat(mount.qw()).isEqualTo(1);
        }

        @Test
        @DisplayName("normalizes non-unit axis")
        void normalizesAxis() {
            var mount = Mount.SpatialMount.fromAxisAngle(0, 0, 0, 0, 2, 0, 90);

            // Should produce same result as unit axis
            assertThat(mount.qx()).isCloseTo(0, within(1e-10));
            assertThat(mount.qy()).isCloseTo(Math.sin(Math.PI / 4), within(1e-10));
            assertThat(mount.qz()).isCloseTo(0, within(1e-10));
            assertThat(mount.qw()).isCloseTo(Math.cos(Math.PI / 4), within(1e-10));
        }

        @Test
        @DisplayName("produces unit quaternion")
        void unitQuaternion() {
            var mount = Mount.SpatialMount.fromAxisAngle(0, 0, 0, 1, 1, 1, 120);

            double len = Math.sqrt(mount.qx() * mount.qx() + mount.qy() * mount.qy()
                    + mount.qz() * mount.qz() + mount.qw() * mount.qw());
            assertThat(len).isCloseTo(1.0, within(1e-10));
        }
    }

    // ==================================================================================
    // CBOR Serialization
    // ==================================================================================

    @Nested
    @DisplayName("CBOR")
    class Cbor {

        @Test
        @DisplayName("encodes 8-element array with rotation")
        void encodes8Elements() {
            var mount = new Mount.SpatialMount(1, 2, 3, 0.1, 0.2, 0.3, 0.9);
            CBORObject cbor = mount.toCborTree(Canonical.Scope.RECORD);

            assertThat(cbor.size()).isEqualTo(8);
            assertThat(cbor.get(0).AsString()).isEqualTo("spatial");
            assertThat(cbor.get(1).AsDouble()).isEqualTo(1.0);
            assertThat(cbor.get(2).AsDouble()).isEqualTo(2.0);
            assertThat(cbor.get(3).AsDouble()).isEqualTo(3.0);
            assertThat(cbor.get(4).AsDouble()).isEqualTo(0.1);
            assertThat(cbor.get(5).AsDouble()).isEqualTo(0.2);
            assertThat(cbor.get(6).AsDouble()).isEqualTo(0.3);
            assertThat(cbor.get(7).AsDouble()).isEqualTo(0.9);
        }

        @Test
        @DisplayName("position-only encodes identity rotation")
        void positionOnlyEncodes() {
            var mount = new Mount.SpatialMount(1, 2, 3);
            CBORObject cbor = mount.toCborTree(Canonical.Scope.RECORD);

            assertThat(cbor.size()).isEqualTo(8);
            assertThat(cbor.get(4).AsDouble()).isEqualTo(0.0);
            assertThat(cbor.get(5).AsDouble()).isEqualTo(0.0);
            assertThat(cbor.get(6).AsDouble()).isEqualTo(0.0);
            assertThat(cbor.get(7).AsDouble()).isEqualTo(1.0);
        }

        @Test
        @DisplayName("decodes 8-element array with rotation")
        void decodes8Elements() {
            CBORObject cbor = CBORObject.NewArray();
            cbor.Add("spatial");
            cbor.Add(1.0); cbor.Add(2.0); cbor.Add(3.0);
            cbor.Add(0.1); cbor.Add(0.2); cbor.Add(0.3); cbor.Add(0.9);

            Mount mount = Mount.fromCborTree(cbor);
            assertThat(mount).isInstanceOf(Mount.SpatialMount.class);

            var spatial = (Mount.SpatialMount) mount;
            assertThat(spatial.x()).isEqualTo(1.0);
            assertThat(spatial.y()).isEqualTo(2.0);
            assertThat(spatial.z()).isEqualTo(3.0);
            assertThat(spatial.qx()).isEqualTo(0.1);
            assertThat(spatial.qy()).isEqualTo(0.2);
            assertThat(spatial.qz()).isEqualTo(0.3);
            assertThat(spatial.qw()).isEqualTo(0.9);
        }

        @Test
        @DisplayName("backward compat: decodes old 4-element array as identity rotation")
        void backwardCompat4Elements() {
            CBORObject cbor = CBORObject.NewArray();
            cbor.Add("spatial");
            cbor.Add(1.0); cbor.Add(2.0); cbor.Add(3.0);

            Mount mount = Mount.fromCborTree(cbor);
            assertThat(mount).isInstanceOf(Mount.SpatialMount.class);

            var spatial = (Mount.SpatialMount) mount;
            assertThat(spatial.x()).isEqualTo(1.0);
            assertThat(spatial.y()).isEqualTo(2.0);
            assertThat(spatial.z()).isEqualTo(3.0);
            assertThat(spatial.qx()).isEqualTo(0.0);
            assertThat(spatial.qy()).isEqualTo(0.0);
            assertThat(spatial.qz()).isEqualTo(0.0);
            assertThat(spatial.qw()).isEqualTo(1.0);
        }

        @Test
        @DisplayName("round-trip: encode then decode preserves values")
        void roundTrip() {
            var original = Mount.SpatialMount.fromEuler(1, 2, 3, 45, 30, 60);
            CBORObject cbor = original.toCborTree(Canonical.Scope.RECORD);

            Mount decoded = Mount.fromCborTree(cbor);
            assertThat(decoded).isInstanceOf(Mount.SpatialMount.class);

            var spatial = (Mount.SpatialMount) decoded;
            assertThat(spatial.x()).isEqualTo(original.x());
            assertThat(spatial.y()).isEqualTo(original.y());
            assertThat(spatial.z()).isEqualTo(original.z());
            assertThat(spatial.qx()).isCloseTo(original.qx(), within(1e-15));
            assertThat(spatial.qy()).isCloseTo(original.qy(), within(1e-15));
            assertThat(spatial.qz()).isCloseTo(original.qz(), within(1e-15));
            assertThat(spatial.qw()).isCloseTo(original.qw(), within(1e-15));
        }
    }
}
