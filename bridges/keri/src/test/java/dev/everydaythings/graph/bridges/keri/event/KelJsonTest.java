package dev.everydaythings.graph.bridges.keri.event;

import dev.everydaythings.graph.bridges.keri.Cesr;
import dev.everydaythings.graph.bridges.keri.MatterCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * KEL JSON codec — version-string patching + SAID computation + round-trip.
 */
@DisplayName("KEL JSON codec")
class KelJsonTest {

    private final SecureRandom rng = new SecureRandom();

    @Test
    @DisplayName("inception round-trips and preserves all fields")
    void inceptionRoundTrip() {
        String signingKey = Cesr.encodePrimitive(MatterCode.ED25519, randomBytes(32));
        String nextDigest = Cesr.encodePrimitive(MatterCode.SHA2_256, randomBytes(32));

        Map<String, Object> event = KelEvents.inception(
                signingKey, List.of(signingKey), List.of(nextDigest), "1", "1");

        byte[] wire = KelJson.encode(event);

        Map<String, Object> parsed = KelJson.decode(wire);
        assertThat(parsed.get("t")).isEqualTo("icp");
        assertThat(parsed.get("i")).isEqualTo(signingKey);
        assertThat(parsed.get("s")).isEqualTo("0");
        assertThat(parsed.get("k")).isEqualTo(List.of(signingKey));
        assertThat(parsed.get("n")).isEqualTo(List.of(nextDigest));
        assertThat(parsed.get("kt")).isEqualTo("1");
        assertThat(parsed.get("nt")).isEqualTo("1");
    }

    @Test
    @DisplayName("version string declares the actual byte size")
    void versionStringSizeMatches() {
        String signingKey = Cesr.encodePrimitive(MatterCode.ED25519, randomBytes(32));
        String nextDigest = Cesr.encodePrimitive(MatterCode.SHA2_256, randomBytes(32));

        byte[] wire = KelJson.encode(KelEvents.inception(
                signingKey, List.of(signingKey), List.of(nextDigest), "1", "1"));

        String json = new String(wire, StandardCharsets.UTF_8);
        assertThat(json).startsWith("{\"v\":\"KERI10JSON");
        Map<String, Object> parsed = KelJson.decode(wire);
        String version = (String) parsed.get("v");
        int declared = Integer.parseInt(version.substring(10, 16), 16);
        assertThat(declared).isEqualTo(wire.length);
    }

    @Test
    @DisplayName("default SAID is a valid Blake3-256 qb64 primitive")
    void saidIsValidPrimitive() {
        String signingKey = Cesr.encodePrimitive(MatterCode.ED25519, randomBytes(32));
        String nextDigest = Cesr.encodePrimitive(MatterCode.SHA2_256, randomBytes(32));

        Map<String, Object> parsed = KelJson.decode(KelJson.encode(KelEvents.inception(
                signingKey, List.of(signingKey), List.of(nextDigest), "1", "1")));

        String said = (String) parsed.get("d");
        assertThat(said).startsWith("E").hasSize(KelJson.SAID_LENGTH);
        Cesr.Primitive p = Cesr.decodePrimitive(said);
        assertThat(p.code()).isEqualTo(MatterCode.BLAKE3_256);
    }

    @Test
    @DisplayName("SHA2-256 SAID via parameterized encode round-trips")
    void sha2SaidOverride() {
        String signingKey = Cesr.encodePrimitive(MatterCode.ED25519, randomBytes(32));
        String nextDigest = Cesr.encodePrimitive(MatterCode.SHA2_256, randomBytes(32));

        byte[] wire = KelJson.encode(KelEvents.inception(
                signingKey, List.of(signingKey), List.of(nextDigest), "1", "1"),
                MatterCode.SHA2_256);

        Map<String, Object> parsed = KelJson.decode(wire);
        String said = (String) parsed.get("d");
        assertThat(said).startsWith("I").hasSize(KelJson.SAID_LENGTH);
        assertThat(Cesr.decodePrimitive(said).code()).isEqualTo(MatterCode.SHA2_256);
    }

    @Test
    @DisplayName("SHA3-256 SAID via parameterized encode round-trips")
    void sha3SaidOverride() {
        String signingKey = Cesr.encodePrimitive(MatterCode.ED25519, randomBytes(32));
        String nextDigest = Cesr.encodePrimitive(MatterCode.SHA2_256, randomBytes(32));

        byte[] wire = KelJson.encode(KelEvents.inception(
                signingKey, List.of(signingKey), List.of(nextDigest), "1", "1"),
                MatterCode.SHA3_256);

        Map<String, Object> parsed = KelJson.decode(wire);
        String said = (String) parsed.get("d");
        assertThat(said).startsWith("H").hasSize(KelJson.SAID_LENGTH);
        assertThat(Cesr.decodePrimitive(said).code()).isEqualTo(MatterCode.SHA3_256);
    }

    @Test
    @DisplayName("tampering with bytes after encode is detected via SAID")
    void tamperingDetected() {
        String signingKey = Cesr.encodePrimitive(MatterCode.ED25519, randomBytes(32));
        String nextDigest = Cesr.encodePrimitive(MatterCode.SHA2_256, randomBytes(32));

        byte[] wire = KelJson.encode(KelEvents.inception(
                signingKey, List.of(signingKey), List.of(nextDigest), "1", "1"));

        // Flip a byte in the kt field value (it's "1" -> change to "2" preserves length).
        String json = new String(wire, StandardCharsets.UTF_8);
        int ktPos = json.indexOf("\"kt\":\"1\"");
        wire[ktPos + 6] = '2';

        assertThatThrownBy(() -> KelJson.decode(wire))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("SAID mismatch");
    }

    @Test
    @DisplayName("rotation event round-trips")
    void rotationRoundTrip() {
        String aid = Cesr.encodePrimitive(MatterCode.ED25519, randomBytes(32));
        String priorD = Cesr.encodePrimitive(MatterCode.SHA2_256, randomBytes(32));
        String newKey = Cesr.encodePrimitive(MatterCode.ED25519, randomBytes(32));
        String nextDigest = Cesr.encodePrimitive(MatterCode.SHA2_256, randomBytes(32));

        Map<String, Object> event = KelEvents.rotation(
                aid, 1, priorD, List.of(newKey), List.of(nextDigest), "1", "1");

        Map<String, Object> parsed = KelJson.decode(KelJson.encode(event));
        assertThat(parsed.get("t")).isEqualTo("rot");
        assertThat(parsed.get("s")).isEqualTo("1");
        assertThat(parsed.get("p")).isEqualTo(priorD);
        assertThat(parsed.get("k")).isEqualTo(List.of(newKey));
    }

    @Test
    @DisplayName("interaction event round-trips with anchors")
    void interactionRoundTrip() {
        String aid = Cesr.encodePrimitive(MatterCode.ED25519, randomBytes(32));
        String priorD = Cesr.encodePrimitive(MatterCode.SHA2_256, randomBytes(32));
        Map<String, Object> seal = Map.of(
                "i", aid,
                "s", "1",
                "d", priorD);

        Map<String, Object> parsed = KelJson.decode(KelJson.encode(
                KelEvents.interaction(aid, 2, priorD, List.of(seal))));

        assertThat(parsed.get("t")).isEqualTo("ixn");
        assertThat(parsed.get("s")).isEqualTo("2");
        assertThat(parsed.get("a")).isEqualTo(List.of(seal));
    }

    @Test
    @DisplayName("delegated inception includes delegator field")
    void delegatedInception() {
        String delegator = Cesr.encodePrimitive(MatterCode.ED25519, randomBytes(32));
        String childKey = Cesr.encodePrimitive(MatterCode.ED25519, randomBytes(32));
        String nextDigest = Cesr.encodePrimitive(MatterCode.SHA2_256, randomBytes(32));

        Map<String, Object> parsed = KelJson.decode(KelJson.encode(
                KelEvents.delegatedInception(childKey, delegator,
                        List.of(childKey), List.of(nextDigest), "1", "1")));

        assertThat(parsed.get("t")).isEqualTo("dip");
        assertThat(parsed.get("di")).isEqualTo(delegator);
    }

    @Test
    @DisplayName("size mismatch is rejected")
    void sizeMismatch() {
        String signingKey = Cesr.encodePrimitive(MatterCode.ED25519, randomBytes(32));
        String nextDigest = Cesr.encodePrimitive(MatterCode.SHA2_256, randomBytes(32));
        byte[] wire = KelJson.encode(KelEvents.inception(
                signingKey, List.of(signingKey), List.of(nextDigest), "1", "1"));
        // Overwrite the 6-hex-char size in the version string with a wrong value.
        // The leading "{\"v\":\"KERI10JSON" is 16 bytes; size hex follows immediately.
        byte[] tampered = wire.clone();
        byte[] wrongSize = "000001".getBytes(StandardCharsets.US_ASCII);
        System.arraycopy(wrongSize, 0, tampered, 16, 6);

        assertThatThrownBy(() -> KelJson.decode(tampered))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("size mismatch");
    }

    private byte[] randomBytes(int n) {
        byte[] b = new byte[n];
        rng.nextBytes(b);
        return b;
    }
}
