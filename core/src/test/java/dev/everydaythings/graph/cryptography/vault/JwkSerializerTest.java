package dev.everydaythings.graph.cryptography.vault;

import dev.everydaythings.graph.cryptography.algorithm.KeyAgreement;
import dev.everydaythings.graph.cryptography.algorithm.Signing;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.security.KeyPair;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("JwkSerializer — KeyPair ↔ JWK round-trip")
class JwkSerializerTest {

    private final Signing.Ed25519 ed25519 = Signing.Ed25519.builtin();
    private final KeyAgreement.X25519 x25519 = KeyAgreement.X25519.builtin();

    @Nested
    @DisplayName("Ed25519")
    class Ed25519 {

        @Test
        @DisplayName("encode produces OKP / Ed25519 JWK with d + x fields")
        void encodeShape() {
            KeyPair kp = ed25519.generateKeyPair();
            Map<String, Object> jwk = JwkSerializer.encodeEd25519(kp);

            assertThat(jwk).containsEntry("kty", "OKP");
            assertThat(jwk).containsEntry("crv", "Ed25519");
            assertThat(jwk.get("d")).isInstanceOf(String.class);
            assertThat(jwk.get("x")).isInstanceOf(String.class);
        }

        @Test
        @DisplayName("encode then decode yields keypair with same raw bytes")
        void roundTrip() {
            KeyPair original = ed25519.generateKeyPair();
            Map<String, Object> jwk = JwkSerializer.encodeEd25519(original);
            KeyPair recovered = JwkSerializer.decode(jwk);

            assertThat(ed25519.publicKeyToRaw(recovered.getPublic()))
                    .isEqualTo(ed25519.publicKeyToRaw(original.getPublic()));
        }

        @Test
        @DisplayName("recovered private key can sign a message that verifies under the original public key")
        void privateKeyStillSigns() {
            KeyPair original = ed25519.generateKeyPair();
            Map<String, Object> jwk = JwkSerializer.encodeEd25519(original);
            KeyPair recovered = JwkSerializer.decode(jwk);

            byte[] message = "round-trip test".getBytes();
            byte[] signature = ed25519.sign(message, recovered.getPrivate());
            assertThat(ed25519.verify(message, signature, original.getPublic())).isTrue();
        }
    }

    @Nested
    @DisplayName("X25519")
    class X25519 {

        @Test
        @DisplayName("encode produces OKP / X25519 JWK with d + x fields")
        void encodeShape() {
            KeyPair kp = x25519.generateKeyPair();
            Map<String, Object> jwk = JwkSerializer.encodeX25519(kp);

            assertThat(jwk).containsEntry("kty", "OKP");
            assertThat(jwk).containsEntry("crv", "X25519");
            assertThat(jwk.get("d")).isInstanceOf(String.class);
            assertThat(jwk.get("x")).isInstanceOf(String.class);
        }

        @Test
        @DisplayName("encode then decode yields keypair with same raw bytes")
        void roundTrip() {
            KeyPair original = x25519.generateKeyPair();
            Map<String, Object> jwk = JwkSerializer.encodeX25519(original);
            KeyPair recovered = JwkSerializer.decode(jwk);

            assertThat(x25519.publicKeyToRaw(recovered.getPublic()))
                    .isEqualTo(x25519.publicKeyToRaw(original.getPublic()));
        }

        @Test
        @DisplayName("recovered private key derives the same shared secret as original")
        void privateKeyStillAgrees() throws Exception {
            KeyPair alice = x25519.generateKeyPair();
            KeyPair bob   = x25519.generateKeyPair();

            byte[] aliceSecretWithBob = x25519.agree(alice.getPrivate(), bob.getPublic());

            Map<String, Object> aliceJwk = JwkSerializer.encodeX25519(alice);
            KeyPair aliceRecovered = JwkSerializer.decode(aliceJwk);

            byte[] recoveredSecretWithBob =
                    x25519.agree(aliceRecovered.getPrivate(), bob.getPublic());

            assertThat(recoveredSecretWithBob).isEqualTo(aliceSecretWithBob);
        }
    }

    @Nested
    @DisplayName("Validation")
    class Validation {

        @Test
        @DisplayName("unknown kty is rejected")
        void rejectsUnknownKty() {
            Map<String, Object> jwk = Map.of("kty", "EC", "crv", "P-256", "d", "x", "x", "y");
            assertThatThrownBy(() -> JwkSerializer.decode(jwk))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("OKP");
        }

        @Test
        @DisplayName("unknown crv is rejected")
        void rejectsUnknownCrv() {
            Map<String, Object> jwk = Map.of("kty", "OKP", "crv", "Ed448", "d", "x", "x", "y");
            assertThatThrownBy(() -> JwkSerializer.decode(jwk))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Ed25519");
        }

        @Test
        @DisplayName("missing required field is rejected")
        void rejectsMissingField() {
            Map<String, Object> jwk = Map.of("kty", "OKP", "crv", "Ed25519", "x", "y");
            assertThatThrownBy(() -> JwkSerializer.decode(jwk))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("'d'");
        }
    }
}
