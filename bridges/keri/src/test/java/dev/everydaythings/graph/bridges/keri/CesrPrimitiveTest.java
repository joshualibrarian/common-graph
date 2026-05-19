package dev.everydaythings.graph.bridges.keri;

import dev.everydaythings.graph.encoding.Encoding;
import dev.everydaythings.graph.id.ItemRef;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.security.SecureRandom;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Verifies that the v1 CESR codec round-trips its primitive types and
 * computes the qb64-length / code-detection logic correctly.
 */
@DisplayName("CESR primitive codec")
class CesrPrimitiveTest {

    private final SecureRandom rng = new SecureRandom();

    @Test
    @DisplayName("Ed25519 (transferable) round-trips through qb64")
    void ed25519TransferableRoundTrip() {
        byte[] raw = randomBytes(32);
        String qb64 = Cesr.encodePrimitive(MatterCode.ED25519, raw);
        assertThat(qb64).startsWith("D").hasSize(44);

        Cesr.Primitive decoded = Cesr.decodePrimitive(qb64);
        assertThat(decoded.code()).isEqualTo(MatterCode.ED25519);
        assertThat(decoded.raw()).isEqualTo(raw);
    }

    @Test
    @DisplayName("Ed25519 signature (2-char code) round-trips through qb64")
    void ed25519SignatureRoundTrip() {
        byte[] raw = randomBytes(64);
        String qb64 = Cesr.encodePrimitive(MatterCode.ED25519_SIG, raw);
        assertThat(qb64).startsWith("0B").hasSize(88);

        Cesr.Primitive decoded = Cesr.decodePrimitive(qb64);
        assertThat(decoded.code()).isEqualTo(MatterCode.ED25519_SIG);
        assertThat(decoded.raw()).isEqualTo(raw);
    }

    @Test
    @DisplayName("Blake3-256 digest round-trips")
    void blake3RoundTrip() {
        byte[] raw = randomBytes(32);
        String qb64 = Cesr.encodePrimitive(MatterCode.BLAKE3_256, raw);
        assertThat(qb64).startsWith("E").hasSize(44);

        Cesr.Primitive decoded = Cesr.decodePrimitive(qb64);
        assertThat(decoded.code()).isEqualTo(MatterCode.BLAKE3_256);
        assertThat(decoded.raw()).isEqualTo(raw);
    }

    @Test
    @DisplayName("wrong raw length is rejected on encode")
    void rejectsWrongRawLength() {
        byte[] tooShort = new byte[16];
        assertThatThrownBy(() -> Cesr.encodePrimitive(MatterCode.ED25519, tooShort))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("32");
    }

    @Test
    @DisplayName("unknown code prefix is rejected on decode")
    void rejectsUnknownPrefix() {
        // 'Z' isn't in our v1 table.  Pad to 44 chars to look length-plausible.
        String fake = "Z" + "A".repeat(43);
        assertThatThrownBy(() -> Cesr.decodePrimitive(fake))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown");
    }

    @Test
    @DisplayName("malformed length is rejected on decode")
    void rejectsBadLength() {
        // ED25519 wants 44 chars total; provide 40.
        String tooShort = "D" + "A".repeat(39);
        assertThatThrownBy(() -> Cesr.decodePrimitive(tooShort))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("length");
    }

    @Test
    @DisplayName("Encoding identity reports CesrJson")
    void encodingIdentity() {
        assertThat(Cesr.INSTANCE.formatCode()).isEqualTo((byte) 0x20);
        assertThat(Cesr.INSTANCE.encoding()).isEqualTo(ItemRef.iid(Encoding.CesrJson.KEY));
    }

    private byte[] randomBytes(int n) {
        byte[] b = new byte[n];
        rng.nextBytes(b);
        return Arrays.copyOf(b, n);
    }
}
