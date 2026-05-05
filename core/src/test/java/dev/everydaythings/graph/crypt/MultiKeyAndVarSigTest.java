package dev.everydaythings.graph.crypt;

import dev.everydaythings.graph.item.id.Varint;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MultiKeyAndVarSigTest {

    @Nested
    @DisplayName("Varint")
    class VarintTest {

        @Test
        @DisplayName("encodes 1-byte values")
        void encodesShortValues() {
            assertThat(Varint.encodeUnsignedVarint(0)).containsExactly(0x00);
            assertThat(Varint.encodeUnsignedVarint(1)).containsExactly(0x01);
            assertThat(Varint.encodeUnsignedVarint(127)).containsExactly(0x7F);
        }

        @Test
        @DisplayName("encodes 2-byte values")
        void encodesTwoByteValues() {
            assertThat(Varint.encodeUnsignedVarint(128)).containsExactly(0x80, 0x01);
            assertThat(Varint.encodeUnsignedVarint(0xed)).containsExactly(0xed, 0x01);
            byte[] e = Varint.encodeUnsignedVarint(0x1200);
            Varint.Read r = Varint.readUnsignedVarint(e, 0);
            assertThat(r.value()).isEqualTo(0x1200L);
            assertThat(r.next()).isEqualTo(e.length);
        }

        @Test
        @DisplayName("round-trip various values")
        void roundTrip() {
            long[] values = {0, 1, 127, 128, 255, 16383, 16384, 0xed, 0x1200, 0x1205, 1_000_000};
            for (long v : values) {
                byte[] bytes = Varint.encodeUnsignedVarint(v);
                Varint.Read r = Varint.readUnsignedVarint(bytes, 0);
                assertThat(r.value()).as("round-trip of %d", v).isEqualTo(v);
                assertThat(r.next()).isEqualTo(bytes.length);
            }
        }

        @Test
        @DisplayName("rejects negative")
        void rejectsNegative() {
            assertThatThrownBy(() -> Varint.encodeUnsignedVarint(-1))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("rejects truncated buffer")
        void rejectsTruncated() {
            byte[] truncated = {(byte) 0x80};
            assertThatThrownBy(() -> Varint.readUnsignedVarint(truncated, 0))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("Algorithm multikey/varsig metadata")
    class AlgorithmMetadata {

        @Test
        @DisplayName("Sign algorithms expose multikey codes")
        void signMultikeyCodes() {
            assertThat(Algorithm.Sign.ED25519.multikeyCode()).isEqualTo(0xed);
            assertThat(Algorithm.Sign.ES256.multikeyCode()).isEqualTo(0x1200);
            assertThat(Algorithm.Sign.ES256K.multikeyCode()).isEqualTo(0xe7);
            assertThat(Algorithm.Sign.PS256.multikeyCode()).isEqualTo(0x1205);
        }

        @Test
        @DisplayName("Sign algorithms expose varsig codes")
        void signVarsigCodes() {
            assertThat(Algorithm.Sign.ED25519.varsigCode()).isEqualTo(0xed);
            assertThat(Algorithm.Sign.ES256.varsigCode()).isEqualTo(0x1200);
        }

        @Test
        @DisplayName("KeyMgmt algorithms expose multikey codes")
        void keyMgmtMultikeyCodes() {
            assertThat(Algorithm.KeyMgmt.ECDH_ES_HKDF_256.multikeyCode()).isEqualTo(0xec);
            assertThat(Algorithm.KeyMgmt.RSA_OAEP_256.multikeyCode()).isEqualTo(0x1205);
        }

        @Test
        @DisplayName("Sign algorithms expose raw key/sig sizes")
        void signSizes() {
            assertThat(Algorithm.Sign.ED25519.rawKeyBytes()).isEqualTo(32);
            assertThat(Algorithm.Sign.ED25519.sigBytes()).isEqualTo(64);
            assertThat(Algorithm.Sign.PS256.sigBytes()).isEqualTo(0);  // variable
        }

        @Test
        @DisplayName("byMultikeyCode resolves correctly")
        void byMultikeyCode() {
            assertThat(Algorithm.Asymmetric.byMultikeyCode(0xed)).isEqualTo(Algorithm.Sign.ED25519);
            assertThat(Algorithm.Asymmetric.byMultikeyCode(0xec)).isEqualTo(Algorithm.KeyMgmt.ECDH_ES_HKDF_256);
        }

        @Test
        @DisplayName("byMultikeyCode rejects unknown")
        void byMultikeyCodeUnknown() {
            assertThatThrownBy(() -> Algorithm.Asymmetric.byMultikeyCode(0xffff))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("byVarsigCode resolves correctly")
        void byVarsigCode() {
            assertThat(Algorithm.Sign.byVarsigCode(0xed)).isEqualTo(Algorithm.Sign.ED25519);
            assertThat(Algorithm.Sign.byVarsigCode(0x1200)).isEqualTo(Algorithm.Sign.ES256);
        }

        @Test
        @DisplayName("RSA shared between PS256 and RSA_OAEP_256")
        void rsaShared() {
            assertThat(Algorithm.Sign.PS256.multikeyCode())
                    .isEqualTo(Algorithm.KeyMgmt.RSA_OAEP_256.multikeyCode());
        }
    }

    @Nested
    @DisplayName("MultiKey")
    class MultiKeyTest {

        @Test
        @DisplayName("Ed25519 round-trip via Algorithm")
        void ed25519RoundTripViaAlgorithm() {
            byte[] rawKey = new byte[32];
            for (int i = 0; i < 32; i++) rawKey[i] = (byte) (i + 1);
            MultiKey mk = MultiKey.of(Algorithm.Sign.ED25519, rawKey);

            assertThat(mk.code()).isEqualTo(0xed);
            assertThat(mk.algorithm()).isEqualTo(Algorithm.Sign.ED25519);
            assertThat(mk.rawKey()).containsExactly(rawKey);

            byte[] encoded = mk.encoded();
            assertThat(encoded.length).isEqualTo(2 + 32);  // varint(0xed) is 2 bytes

            MultiKey decoded = MultiKey.decode(encoded);
            assertThat(decoded).isEqualTo(mk);
            assertThat(decoded.algorithm()).isEqualTo(Algorithm.Sign.ED25519);
        }

        @Test
        @DisplayName("X25519 round-trip via KeyMgmt algorithm")
        void x25519RoundTrip() {
            byte[] rawKey = new byte[32];
            MultiKey mk = MultiKey.of(Algorithm.KeyMgmt.ECDH_ES_HKDF_256, rawKey);
            assertThat(mk.code()).isEqualTo(0xec);
            MultiKey decoded = MultiKey.decode(mk.encoded());
            assertThat(decoded).isEqualTo(mk);
        }

        @Test
        @DisplayName("rejects wrong key length when algorithm has fixed size")
        void rejectsWrongLength() {
            byte[] tooShort = new byte[16];
            assertThatThrownBy(() -> MultiKey.of(Algorithm.Sign.ED25519, tooShort))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("RSA accepts variable length")
        void rsaVariableLength() {
            byte[] keyBytes = new byte[295];
            MultiKey mk = MultiKey.of(Algorithm.Sign.PS256, keyBytes);
            assertThat(mk.code()).isEqualTo(0x1205);
            assertThat(mk.rawKey().length).isEqualTo(295);
        }

        @Test
        @DisplayName("unknown codec carried through without validation")
        void unknownCodec() {
            byte[] bytes = new byte[42];
            MultiKey mk = MultiKey.of(0xfffe, bytes);
            assertThat(mk.code()).isEqualTo(0xfffe);
            assertThat(mk.algorithm()).isNull();
            MultiKey decoded = MultiKey.decode(mk.encoded());
            assertThat(decoded.code()).isEqualTo(0xfffe);
        }

        @Test
        @DisplayName("equality based on encoded bytes")
        void equality() {
            byte[] rawA = new byte[32];
            byte[] rawB = new byte[32];
            rawB[0] = 1;
            assertThat(MultiKey.of(Algorithm.Sign.ED25519, rawA))
                    .isEqualTo(MultiKey.of(Algorithm.Sign.ED25519, rawA));
            assertThat(MultiKey.of(Algorithm.Sign.ED25519, rawA))
                    .isNotEqualTo(MultiKey.of(Algorithm.Sign.ED25519, rawB));
        }
    }

    @Nested
    @DisplayName("VarSig")
    class VarSigTest {

        @Test
        @DisplayName("Ed25519 round-trip via Algorithm")
        void ed25519RoundTripViaAlgorithm() {
            byte[] rawSig = new byte[64];
            for (int i = 0; i < 64; i++) rawSig[i] = (byte) i;
            VarSig vs = VarSig.of(Algorithm.Sign.ED25519, rawSig);

            assertThat(vs.code()).isEqualTo(0xed);
            assertThat(vs.algorithm()).isEqualTo(Algorithm.Sign.ED25519);
            assertThat(vs.rawSig()).containsExactly(rawSig);

            byte[] encoded = vs.encoded();
            assertThat(encoded.length).isEqualTo(2 + 64);

            VarSig decoded = VarSig.decode(encoded);
            assertThat(decoded).isEqualTo(vs);
            assertThat(decoded.algorithm()).isEqualTo(Algorithm.Sign.ED25519);
        }

        @Test
        @DisplayName("rejects wrong sig length when algorithm has fixed size")
        void rejectsWrongLength() {
            byte[] tooShort = new byte[32];
            assertThatThrownBy(() -> VarSig.of(Algorithm.Sign.ED25519, tooShort))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("PS256 accepts variable length")
        void ps256VariableLength() {
            byte[] sigBytes = new byte[256];
            VarSig vs = VarSig.of(Algorithm.Sign.PS256, sigBytes);
            assertThat(vs.code()).isEqualTo(0x1205);
            assertThat(vs.rawSig().length).isEqualTo(256);
        }

        @Test
        @DisplayName("ES256 round-trip")
        void es256RoundTrip() {
            byte[] rawSig = new byte[64];
            VarSig vs = VarSig.of(Algorithm.Sign.ES256, rawSig);
            VarSig decoded = VarSig.decode(vs.encoded());
            assertThat(decoded).isEqualTo(vs);
        }

        @Test
        @DisplayName("unknown codec carried through without validation")
        void unknownCodec() {
            byte[] bytes = new byte[42];
            VarSig vs = VarSig.of(0xfffe, bytes);
            assertThat(vs.code()).isEqualTo(0xfffe);
            assertThat(vs.algorithm()).isNull();
        }

        @Test
        @DisplayName("equality based on encoded bytes")
        void equality() {
            byte[] rawA = new byte[64];
            byte[] rawB = new byte[64];
            rawB[0] = 1;
            assertThat(VarSig.of(Algorithm.Sign.ED25519, rawA))
                    .isEqualTo(VarSig.of(Algorithm.Sign.ED25519, rawA));
            assertThat(VarSig.of(Algorithm.Sign.ED25519, rawA))
                    .isNotEqualTo(VarSig.of(Algorithm.Sign.ED25519, rawB));
        }
    }
}
