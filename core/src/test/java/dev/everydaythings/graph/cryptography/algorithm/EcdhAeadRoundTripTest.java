package dev.everydaythings.graph.cryptography.algorithm;

import dev.everydaythings.graph.cryptography.vault.InMemoryVault;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.PublicKey;
import java.security.SecureRandom;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * End-to-end validation of the building blocks of hybrid encryption:
 * X25519 ECDH for shared secret, HKDF-SHA-256 for content-key derivation,
 * AES-GCM-256 for AEAD encryption.  Proves the primitives compose before
 * the full Noise XX wiring lands.
 */
@DisplayName("ECDH-AEAD round trip")
class EcdhAeadRoundTripTest {

    @Test
    @DisplayName("Alice encrypts to Bob; Bob decrypts; plaintext recovered")
    void roundTrip() {
        InMemoryVault alice = InMemoryVault.generate();
        InMemoryVault bob = InMemoryVault.generate();

        KeyAgreement x25519 = KeyAgreement.X25519.builtin();
        Kdf.HkdfSha256 hkdf = Kdf.HkdfSha256.builtin();
        Aead.AesGcm256 aead = Aead.AesGcm256.builtin();

        // Alice derives a shared secret using Bob's public key
        PublicKey bobPub = x25519.decodePublicKey(bob.keyAgreementPublicKey().orElseThrow().rawKey());
        byte[] aliceShared = alice.agree(bobPub);

        // Bob derives the same shared secret using Alice's public key
        PublicKey alicePub = x25519.decodePublicKey(alice.keyAgreementPublicKey().orElseThrow().rawKey());
        byte[] bobShared = bob.agree(alicePub);
        assertThat(aliceShared).isEqualTo(bobShared);

        // Both sides HKDF-expand into a 32-byte AES-256 key
        byte[] salt = "cg-ecdh-aead-test".getBytes(StandardCharsets.UTF_8);
        byte[] info = "v1".getBytes(StandardCharsets.UTF_8);
        byte[] aliceKey = hkdf.derive(aliceShared, salt, info, 32);
        byte[] bobKey = hkdf.derive(bobShared, salt, info, 32);
        assertThat(aliceKey).isEqualTo(bobKey).hasSize(32);

        // Alice encrypts a message
        byte[] nonce = new byte[12];
        new SecureRandom().nextBytes(nonce);
        byte[] aad = "header".getBytes(StandardCharsets.UTF_8);
        byte[] plaintext = "the eagle has landed".getBytes(StandardCharsets.UTF_8);
        byte[] ciphertext = aead.encrypt(aliceKey, nonce, aad, plaintext);

        // Bob decrypts and recovers the original
        byte[] recovered = aead.decrypt(bobKey, nonce, aad, ciphertext);
        assertThat(new String(recovered, StandardCharsets.UTF_8)).isEqualTo("the eagle has landed");
    }

    @Test
    @DisplayName("tampered ciphertext fails authentication")
    void tamperedCiphertextRejected() {
        InMemoryVault alice = InMemoryVault.generate();
        InMemoryVault bob = InMemoryVault.generate();

        KeyAgreement x25519 = KeyAgreement.X25519.builtin();
        Aead.AesGcm256 aead = Aead.AesGcm256.builtin();
        Kdf.HkdfSha256 hkdf = Kdf.HkdfSha256.builtin();

        PublicKey bobPub = x25519.decodePublicKey(bob.keyAgreementPublicKey().orElseThrow().rawKey());
        byte[] shared = alice.agree(bobPub);
        byte[] key = hkdf.derive(shared, new byte[0], new byte[0], 32);

        byte[] nonce = new byte[12];
        byte[] plaintext = "secret".getBytes(StandardCharsets.UTF_8);
        byte[] ciphertext = aead.encrypt(key, nonce, null, plaintext);

        // Flip a bit in the ciphertext
        ciphertext[0] ^= 0x01;

        assertThatThrownBy(() -> aead.decrypt(key, nonce, null, ciphertext))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("AEAD decrypt failed");
    }

    @Test
    @DisplayName("AAD mismatch fails authentication")
    void aadMismatchRejected() {
        Aead.AesGcm256 aead = Aead.AesGcm256.builtin();
        byte[] key = new byte[32];
        byte[] nonce = new byte[12];
        byte[] plaintext = "payload".getBytes(StandardCharsets.UTF_8);
        byte[] ciphertext = aead.encrypt(key, nonce, "context-A".getBytes(StandardCharsets.UTF_8), plaintext);

        assertThatThrownBy(() -> aead.decrypt(key, nonce, "context-B".getBytes(StandardCharsets.UTF_8), ciphertext))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("AEAD decrypt failed");
    }

    @Test
    @DisplayName("HKDF matches RFC 5869 test vector 1 (basic, SHA-256)")
    void hkdfRfcTestVector() {
        // RFC 5869 Appendix A.1
        byte[] ikm = hex("0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b");
        byte[] salt = hex("000102030405060708090a0b0c");
        byte[] info = hex("f0f1f2f3f4f5f6f7f8f9");
        int length = 42;
        byte[] expected = hex(
                "3cb25f25faacd57a90434f64d0362f2a" +
                        "2d2d0a90cf1a5a4c5db02d56ecc4c5bf" +
                        "34007208d5b887185865");

        byte[] okm = Kdf.HkdfSha256.builtin().derive(ikm, salt, info, length);
        assertThat(okm).isEqualTo(expected);
    }

    @Test
    @DisplayName("ChaCha20-Poly1305 round trip")
    void chachaRoundTrip() {
        Aead.ChaCha20Poly1305 aead = Aead.ChaCha20Poly1305.builtin();
        byte[] key = new byte[32];
        new SecureRandom().nextBytes(key);
        byte[] nonce = new byte[12];
        new SecureRandom().nextBytes(nonce);
        byte[] plaintext = "chacha works too".getBytes(StandardCharsets.UTF_8);
        byte[] ciphertext = aead.encrypt(key, nonce, null, plaintext);
        byte[] recovered = aead.decrypt(key, nonce, null, ciphertext);
        assertThat(new String(recovered, StandardCharsets.UTF_8)).isEqualTo("chacha works too");
    }

    private static byte[] hex(String s) {
        s = s.replaceAll("\\s+", "");
        byte[] out = new byte[s.length() / 2];
        for (int i = 0; i < out.length; i++) {
            out[i] = (byte) Integer.parseInt(s.substring(i * 2, i * 2 + 2), 16);
        }
        return out;
    }
}
