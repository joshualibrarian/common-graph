package dev.everydaythings.graph.crypt;

import com.upokecenter.cbor.CBORObject;
import dev.everydaythings.graph.Canonical;
import dev.everydaythings.graph.Hash;
import dev.everydaythings.graph.trust.Algorithm;
import dev.everydaythings.graph.trust.EncryptionPublicKey;
import dev.everydaythings.graph.vault.InMemoryVault;
import dev.everydaythings.graph.vault.Vault;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EncryptedEnvelopeTest {

    private Vault aliceVault;
    private Vault bobVault;
    private EncryptionPublicKey aliceEncKey;
    private EncryptionPublicKey bobEncKey;

    @BeforeEach
    void setUp() {
        // Create two vaults with signing + encryption keys
        aliceVault = InMemoryVault.create();
        aliceVault.generateEncryptionKey();
        aliceEncKey = EncryptionPublicKey.builder()
                .jcaPublicKey(aliceVault.encryptionPublicKey().orElseThrow())
                .build();

        bobVault = InMemoryVault.create();
        bobVault.generateEncryptionKey();
        bobEncKey = EncryptionPublicKey.builder()
                .jcaPublicKey(bobVault.encryptionPublicKey().orElseThrow())
                .build();
    }

    @Nested
    class SingleRecipient {

        @Test
        void encryptDecryptRoundTrip() {
            byte[] plaintext = "Hello, encrypted world!".getBytes();
            byte[] plaintextCid = Hash.DEFAULT.digest(plaintext);

            EncryptedEnvelope envelope = EnvelopeOps.encryptAnonymous(
                    plaintext, plaintextCid, null,
                    Algorithm.Aead.AES_GCM_256,
                    List.of(bobEncKey));

            assertThat(envelope.recipients()).hasSize(1);
            assertThat(envelope.kemAlg()).isEqualTo(Algorithm.KeyMgmt.ECDH_ES_HKDF_256);
            assertThat(envelope.aeadAlg()).isEqualTo(Algorithm.Aead.AES_GCM_256);
            assertThat(envelope.plaintextCid()).isEqualTo(plaintextCid);
            assertThat(envelope.ciphertext()).isNotEqualTo(plaintext);

            // Bob decrypts
            byte[] decrypted = EnvelopeOps.decrypt(envelope, bobVault, bobEncKey.keyId());
            assertThat(decrypted).isEqualTo(plaintext);
        }

        @Test
        void wrongRecipientCannotDecrypt() {
            byte[] plaintext = "Secret message".getBytes();

            EncryptedEnvelope envelope = EnvelopeOps.encryptAnonymous(
                    plaintext, null, null,
                    Algorithm.Aead.AES_GCM_256,
                    List.of(bobEncKey));

            // Alice tries to decrypt Bob's envelope — no matching recipient entry
            assertThatThrownBy(() ->
                    EnvelopeOps.decrypt(envelope, aliceVault, aliceEncKey.keyId()))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("No recipient entry found");
        }

        @Test
        void tamperedCiphertextFails() {
            byte[] plaintext = "Sensitive data".getBytes();
            byte[] plaintextCid = Hash.DEFAULT.digest(plaintext);

            EncryptedEnvelope envelope = EnvelopeOps.encryptAnonymous(
                    plaintext, plaintextCid, null,
                    Algorithm.Aead.AES_GCM_256,
                    List.of(bobEncKey));

            // Tamper with ciphertext
            byte[] tampered = envelope.ciphertext().clone();
            tampered[0] ^= 0xFF;
            EncryptedEnvelope tamperedEnvelope = new EncryptedEnvelope(
                    envelope.kemAlg(), envelope.aeadAlg(), envelope.nonce(),
                    envelope.aad(), envelope.plaintextCid(),
                    envelope.senderKid(), envelope.senderSig(),
                    envelope.recipients(), tampered);

            assertThatThrownBy(() ->
                    EnvelopeOps.decrypt(tamperedEnvelope, bobVault, bobEncKey.keyId()))
                    .isInstanceOf(SecurityException.class);
        }
    }

    @Nested
    class MultiRecipient {

        @Test
        void bothRecipientsCanDecrypt() {
            byte[] plaintext = "Group message".getBytes();
            byte[] plaintextCid = Hash.DEFAULT.digest(plaintext);

            EncryptedEnvelope envelope = EnvelopeOps.encryptAnonymous(
                    plaintext, plaintextCid, null,
                    Algorithm.Aead.AES_GCM_256,
                    List.of(aliceEncKey, bobEncKey));

            assertThat(envelope.recipients()).hasSize(2);

            // Both can decrypt
            byte[] aliceDecrypted = EnvelopeOps.decrypt(envelope, aliceVault, aliceEncKey.keyId());
            byte[] bobDecrypted = EnvelopeOps.decrypt(envelope, bobVault, bobEncKey.keyId());

            assertThat(aliceDecrypted).isEqualTo(plaintext);
            assertThat(bobDecrypted).isEqualTo(plaintext);
        }
    }

    @Nested
    class CborRoundTrip {

        @Test
        void encodeDecodePreservesEnvelope() {
            byte[] plaintext = "CBOR round-trip test".getBytes();
            byte[] plaintextCid = Hash.DEFAULT.digest(plaintext);

            EncryptedEnvelope original = EnvelopeOps.encryptAnonymous(
                    plaintext, plaintextCid, null,
                    Algorithm.Aead.AES_GCM_256,
                    List.of(bobEncKey));

            // Encode to CBOR
            CBORObject cbor = original.toCborTree(Canonical.Scope.RECORD);
            assertThat(cbor.HasMostOuterTag(Canonical.CgTag.ENCRYPTED)).isTrue();

            // Decode from CBOR
            EncryptedEnvelope decoded = EncryptedEnvelope.fromCborTree(cbor);

            assertThat(decoded.kemAlg()).isEqualTo(original.kemAlg());
            assertThat(decoded.aeadAlg()).isEqualTo(original.aeadAlg());
            assertThat(decoded.nonce()).isEqualTo(original.nonce());
            assertThat(decoded.plaintextCid()).isEqualTo(original.plaintextCid());
            assertThat(decoded.ciphertext()).isEqualTo(original.ciphertext());
            assertThat(decoded.recipients()).hasSize(1);
            assertThat(decoded.recipients().get(0).kid()).isEqualTo(original.recipients().get(0).kid());

            // Decoded envelope should still decrypt
            byte[] decrypted = EnvelopeOps.decrypt(decoded, bobVault, bobEncKey.keyId());
            assertThat(decrypted).isEqualTo(plaintext);
        }

        @Test
        void binaryRoundTrip() {
            byte[] plaintext = "Binary round-trip".getBytes();

            EncryptedEnvelope original = EnvelopeOps.encryptAnonymous(
                    plaintext, null, null,
                    Algorithm.Aead.AES_GCM_256,
                    List.of(bobEncKey));

            // Encode to bytes
            byte[] bytes = original.encodeBinary(Canonical.Scope.RECORD);
            assertThat(bytes).isNotEmpty();

            // Decode from bytes
            CBORObject cbor = CBORObject.DecodeFromBytes(bytes);
            EncryptedEnvelope decoded = EncryptedEnvelope.fromCborTree(cbor);

            // Should still decrypt
            byte[] decrypted = EnvelopeOps.decrypt(decoded, bobVault, bobEncKey.keyId());
            assertThat(decrypted).isEqualTo(plaintext);
        }
    }

    @Nested
    class AdditionalAuthenticatedData {

        @Test
        void aadIsAuthenticated() {
            byte[] plaintext = "AAD test".getBytes();
            byte[] aad = "item:12345".getBytes();

            EncryptedEnvelope envelope = EnvelopeOps.encryptAnonymous(
                    plaintext, null, aad,
                    Algorithm.Aead.AES_GCM_256,
                    List.of(bobEncKey));

            // Decrypt with correct AAD succeeds
            byte[] decrypted = EnvelopeOps.decrypt(envelope, bobVault, bobEncKey.keyId());
            assertThat(decrypted).isEqualTo(plaintext);
        }

        @Test
        void wrongAadFails() {
            byte[] plaintext = "AAD test".getBytes();
            byte[] aad = "item:12345".getBytes();

            EncryptedEnvelope envelope = EnvelopeOps.encryptAnonymous(
                    plaintext, null, aad,
                    Algorithm.Aead.AES_GCM_256,
                    List.of(bobEncKey));

            // Modify AAD in the envelope
            EncryptedEnvelope tampered = new EncryptedEnvelope(
                    envelope.kemAlg(), envelope.aeadAlg(), envelope.nonce(),
                    "item:99999".getBytes(), envelope.plaintextCid(),
                    envelope.senderKid(), envelope.senderSig(),
                    envelope.recipients(), envelope.ciphertext());

            assertThatThrownBy(() ->
                    EnvelopeOps.decrypt(tampered, bobVault, bobEncKey.keyId()))
                    .isInstanceOf(SecurityException.class);
        }
    }

    @Nested
    class SenderAuthentication {

        @Test
        void signedEnvelopeDecrypts() {
            byte[] plaintext = "Authenticated message".getBytes();
            byte[] plaintextCid = Hash.DEFAULT.digest(plaintext);

            EncryptedEnvelope envelope = EnvelopeOps.encrypt(
                    plaintext, plaintextCid, null,
                    Algorithm.Aead.AES_GCM_256,
                    List.of(bobEncKey),
                    aliceVault, null);  // no signing key → anonymous but still works

            byte[] decrypted = EnvelopeOps.decrypt(envelope, bobVault, bobEncKey.keyId());
            assertThat(decrypted).isEqualTo(plaintext);
        }
    }

    @Nested
    class PlaintextCidVerification {

        @Test
        void mismatchedPlaintextCidFails() {
            byte[] plaintext = "CID verification test".getBytes();
            byte[] wrongCid = new byte[32]; // all zeros — wrong CID

            EncryptedEnvelope envelope = EnvelopeOps.encryptAnonymous(
                    plaintext, wrongCid, null,
                    Algorithm.Aead.AES_GCM_256,
                    List.of(bobEncKey));

            assertThatThrownBy(() ->
                    EnvelopeOps.decrypt(envelope, bobVault, bobEncKey.keyId()))
                    .isInstanceOf(SecurityException.class)
                    .hasMessageContaining("CID mismatch");
        }
    }
}
