package dev.everydaythings.graph.crypt;

import dev.everydaythings.graph.Hash;
import dev.everydaythings.graph.trust.EncryptionPublicKey;
import dev.everydaythings.graph.trust.KeyLog;
import dev.everydaythings.graph.trust.Purpose;
import dev.everydaythings.graph.trust.Signing;
import dev.everydaythings.graph.trust.SigningPublicKey;
import dev.everydaythings.graph.vault.InMemoryVault;
import dev.everydaythings.graph.vault.Vault;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for X3DH key agreement: pre-key generation, protocol round-trip,
 * SPK verification, OPK consumption, and edge cases.
 */
class X3DHTest {

    private static Vault createVault() {
        InMemoryVault vault = InMemoryVault.create();
        vault.generateEncryptionKey();
        return vault;
    }

    private static EncryptionPublicKey encryptionKey(Vault vault) {
        return EncryptionPublicKey.builder()
                .jcaPublicKey(vault.publicKey(Vault.ENCRYPTION_KEY_ALIAS).orElseThrow())
                .build();
    }

    private static SigningPublicKey signingKey(Vault vault) {
        return SigningPublicKey.builder()
                .jcaPublicKey(vault.signingPublicKey().orElseThrow())
                .build();
    }

    // ==================================================================================
    // PreKeyBundle Generation
    // ==================================================================================

    @Nested
    class PreKeyBundleGeneration {

        @Test
        void generateBundleWithOPKs() {
            Vault vault = createVault();
            PreKeyBundle bundle = PreKeyBundle.generate(vault, null, 10);

            assertThat(bundle.signedPreKey()).isNotNull();
            assertThat(bundle.signedPreKeySignature()).isNotNull();
            assertThat(bundle.signedPreKeySignature().length).isGreaterThan(0);
            assertThat(bundle.signedPreKeyTimestamp()).isGreaterThan(0);
            assertThat(bundle.oneTimePreKeys()).hasSize(10);
            assertThat(bundle.hasOneTimePreKeys()).isTrue();
            assertThat(bundle.oneTimePreKeyCount()).isEqualTo(10);
        }

        @Test
        void generateBundleWithZeroOPKs() {
            Vault vault = createVault();
            PreKeyBundle bundle = PreKeyBundle.generate(vault, null, 0);

            assertThat(bundle.signedPreKey()).isNotNull();
            assertThat(bundle.hasOneTimePreKeys()).isFalse();
            assertThat(bundle.oneTimePreKeyCount()).isEqualTo(0);
        }

        @Test
        void spkStoredInVault() {
            Vault vault = createVault();
            PreKeyBundle.generate(vault, null, 0);

            assertThat(vault.containsKey(PreKeyBundle.SPK_ALIAS)).isTrue();
        }

        @Test
        void opksStoredInVault() {
            Vault vault = createVault();
            PreKeyBundle.generate(vault, null, 5);

            for (int i = 0; i < 5; i++) {
                assertThat(vault.containsKey(PreKeyBundle.OPK_PREFIX + i)).isTrue();
            }
        }

        @Test
        void allOPKsHaveUniqueKeyIds() {
            Vault vault = createVault();
            PreKeyBundle bundle = PreKeyBundle.generate(vault, null, 20);

            long distinctCount = bundle.oneTimePreKeys().stream()
                    .map(EncryptionPublicKey::keyId)
                    .distinct()
                    .count();
            assertThat(distinctCount).isEqualTo(20);
        }

        @Test
        void removeOneTimePreKey() {
            Vault vault = createVault();
            PreKeyBundle bundle = PreKeyBundle.generate(vault, null, 5);

            byte[] keyId = bundle.oneTimePreKeys().getFirst().keyId();
            assertThat(bundle.removeOneTimePreKey(keyId)).isTrue();
            assertThat(bundle.oneTimePreKeyCount()).isEqualTo(4);
        }

        @Test
        void removeNonexistentOPKReturnsFalse() {
            Vault vault = createVault();
            PreKeyBundle bundle = PreKeyBundle.generate(vault, null, 3);

            assertThat(bundle.removeOneTimePreKey(new byte[32])).isFalse();
            assertThat(bundle.oneTimePreKeyCount()).isEqualTo(3);
        }
    }

    // ==================================================================================
    // SPK Signature Verification
    // ==================================================================================

    @Nested
    class SPKVerification {

        @Test
        void validSignatureVerifies() {
            Vault vault = createVault();
            PreKeyBundle bundle = PreKeyBundle.generate(vault, null, 0);
            SigningPublicKey sk = signingKey(vault);

            assertThat(bundle.verifySignedPreKey(sk)).isTrue();
        }

        @Test
        void wrongSigningKeyFails() {
            Vault vault1 = createVault();
            Vault vault2 = createVault();

            PreKeyBundle bundle = PreKeyBundle.generate(vault1, null, 0);
            SigningPublicKey wrongKey = signingKey(vault2);

            assertThat(bundle.verifySignedPreKey(wrongKey)).isFalse();
        }

        @Test
        void tamperedSignatureFails() {
            Vault vault = createVault();
            PreKeyBundle bundle = PreKeyBundle.generate(vault, null, 0);

            byte[] tampered = bundle.signedPreKeySignature().clone();
            tampered[0] ^= 0x01;

            PreKeyBundle bad = new PreKeyBundle(
                    bundle.signedPreKey(), tampered,
                    bundle.signedPreKeyTimestamp(), bundle.oneTimePreKeys());

            assertThat(bad.verifySignedPreKey(signingKey(vault))).isFalse();
        }
    }

    // ==================================================================================
    // X3DH Protocol Round-Trip
    // ==================================================================================

    @Nested
    class ProtocolRoundTrip {

        private Vault aliceVault;
        private Vault bobVault;
        private EncryptionPublicKey aliceIK;
        private EncryptionPublicKey bobIK;
        private SigningPublicKey bobSigningKey;
        private PreKeyBundle bobBundle;

        @BeforeEach
        void setup() {
            aliceVault = createVault();
            bobVault = createVault();
            aliceIK = encryptionKey(aliceVault);
            bobIK = encryptionKey(bobVault);
            bobSigningKey = signingKey(bobVault);
            bobBundle = PreKeyBundle.generate(bobVault, null, 10);
        }

        @Test
        void fullRoundTripWithOPK() {
            X3DH.InitiatorResult initiator = X3DH.initiate(
                    aliceVault, aliceIK, bobBundle, bobIK, bobSigningKey);

            X3DH.Result responder = X3DH.respond(
                    bobVault, bobIK, initiator.message());

            // Both derive the same 32-byte shared secret
            assertThat(initiator.result().sharedSecret())
                    .hasSize(32)
                    .isEqualTo(responder.sharedSecret());

            // Both have the same associated data
            assertThat(initiator.result().associatedData())
                    .isEqualTo(responder.associatedData());

            // Init message references the SPK and OPK used
            assertThat(initiator.message().signedPreKeyId()).isNotNull();
            assertThat(initiator.message().oneTimePreKeyId()).isNotNull();
        }

        @Test
        void roundTripWithoutOPK() {
            bobBundle = PreKeyBundle.generate(bobVault, null, 0);

            X3DH.InitiatorResult initiator = X3DH.initiate(
                    aliceVault, aliceIK, bobBundle, bobIK, bobSigningKey);

            X3DH.Result responder = X3DH.respond(
                    bobVault, bobIK, initiator.message());

            assertThat(initiator.result().sharedSecret())
                    .isEqualTo(responder.sharedSecret());

            // No OPK used
            assertThat(initiator.message().oneTimePreKeyId()).isNull();
        }

        @Test
        void opkConsumedAfterUse() {
            X3DH.InitiatorResult initiator = X3DH.initiate(
                    aliceVault, aliceIK, bobBundle, bobIK, bobSigningKey);

            byte[] usedOpkKeyId = initiator.message().oneTimePreKeyId();
            assertThat(usedOpkKeyId).isNotNull();

            // Bob responds (consumes OPK)
            X3DH.respond(bobVault, bobIK, initiator.message());

            // The OPK alias is deleted from vault
            String alias = X3DH.findOpkAlias(bobVault, usedOpkKeyId);
            assertThat(alias).isNull();
        }

        @Test
        void differentSessionsProduceDifferentSecrets() {
            X3DH.InitiatorResult session1 = X3DH.initiate(
                    aliceVault, aliceIK, bobBundle, bobIK, bobSigningKey);

            X3DH.InitiatorResult session2 = X3DH.initiate(
                    aliceVault, aliceIK, bobBundle, bobIK, bobSigningKey);

            // Different ephemeral keys → different shared secrets
            assertThat(session1.result().sharedSecret())
                    .isNotEqualTo(session2.result().sharedSecret());
        }

        @Test
        void invalidSpkSignatureRejected() {
            byte[] tampered = bobBundle.signedPreKeySignature().clone();
            tampered[0] ^= 0x01;
            PreKeyBundle badBundle = new PreKeyBundle(
                    bobBundle.signedPreKey(), tampered,
                    bobBundle.signedPreKeyTimestamp(), bobBundle.oneTimePreKeys());

            assertThatThrownBy(() ->
                    X3DH.initiate(aliceVault, aliceIK, badBundle, bobIK, bobSigningKey))
                    .isInstanceOf(SecurityException.class)
                    .hasMessageContaining("SPK signature");
        }

        @Test
        void missingOPKInVaultThrows() {
            X3DH.InitiatorResult initiator = X3DH.initiate(
                    aliceVault, aliceIK, bobBundle, bobIK, bobSigningKey);

            // Delete all OPKs from Bob's vault
            for (String alias : new ArrayList<>(bobVault.aliases())) {
                if (alias.startsWith(PreKeyBundle.OPK_PREFIX)) {
                    bobVault.deleteKey(alias);
                }
            }

            assertThatThrownBy(() ->
                    X3DH.respond(bobVault, bobIK, initiator.message()))
                    .isInstanceOf(SecurityException.class)
                    .hasMessageContaining("OPK not found");
        }

        @Test
        void destroyZeroizesSecret() {
            X3DH.InitiatorResult result = X3DH.initiate(
                    aliceVault, aliceIK, bobBundle, bobIK, bobSigningKey);

            byte[] copy = result.result().sharedSecret().clone();
            assertThat(copy).isNotEqualTo(new byte[32]);

            result.result().destroy();
            assertThat(result.result().sharedSecret()).isEqualTo(new byte[32]);
        }

        @Test
        void associatedDataBindsBothIdentities() {
            X3DH.InitiatorResult aliceResult = X3DH.initiate(
                    aliceVault, aliceIK, bobBundle, bobIK, bobSigningKey);

            // Carol initiates with the same Bob bundle
            Vault carolVault = createVault();
            EncryptionPublicKey carolIK = encryptionKey(carolVault);
            X3DH.InitiatorResult carolResult = X3DH.initiate(
                    carolVault, carolIK, bobBundle, bobIK, bobSigningKey);

            // Different initiator identity → different associated data
            assertThat(aliceResult.result().associatedData())
                    .isNotEqualTo(carolResult.result().associatedData());
        }

        @Test
        void multipleSessionsConsumeDistinctOPKs() {
            bobBundle = PreKeyBundle.generate(bobVault, null, 3);

            // Three sessions, each uses a different OPK
            for (int i = 0; i < 3; i++) {
                X3DH.InitiatorResult initiator = X3DH.initiate(
                        aliceVault, aliceIK, bobBundle, bobIK, bobSigningKey);

                X3DH.Result responder = X3DH.respond(
                        bobVault, bobIK, initiator.message());

                assertThat(initiator.result().sharedSecret())
                        .isEqualTo(responder.sharedSecret());

                // Remove used OPK from the bundle (as a real Signer would)
                bobBundle.removeOneTimePreKey(initiator.message().oneTimePreKeyId());
            }

            // All OPKs consumed
            assertThat(bobBundle.hasOneTimePreKeys()).isFalse();
        }
    }

    // ==================================================================================
    // KeyLog Integration
    // ==================================================================================

    @Nested
    class KeyLogIntegration {

        private Signing.Signer testSigner;
        private Signing.Hasher testHasher;
        private long seq;

        @BeforeEach
        void setupSigning() {
            Vault signerVault = InMemoryVault.create();
            byte[] keyRef = signingKey(signerVault).keyId();
            testSigner = new Signing.Signer() {
                @Override public byte[] keyRef() { return keyRef; }
                @Override public byte[] signRaw(byte[] data) { return signerVault.sign(data); }
            };
            testHasher = Signing.defaultHasher();
            seq = 0;
        }

        private void addKeyToLog(KeyLog keyLog, EncryptionPublicKey key, Purpose purpose) {
            keyLog.append(new KeyLog.AddKey(key), seq++, testSigner, testHasher);
            byte[] keyCid = KeyLog.keyCidBytes(key);
            keyLog.append(new KeyLog.SetCurrent(keyCid, purpose, true), seq++, testSigner, testHasher);
        }

        @Test
        void purposeValuesExist() {
            assertThat(Purpose.PRE_KEY.bit()).isEqualTo(8);
            assertThat(Purpose.ONE_TIME_PRE_KEY.bit()).isEqualTo(16);
            assertThat(Purpose.PRE_KEY.inMask(Purpose.mask(Purpose.PRE_KEY))).isTrue();
            assertThat(Purpose.ONE_TIME_PRE_KEY.inMask(Purpose.mask(Purpose.ONE_TIME_PRE_KEY))).isTrue();
        }

        @Test
        void keyLogTracksPreKey() {
            Vault vault = createVault();
            PreKeyBundle bundle = PreKeyBundle.generate(vault, null, 0);

            KeyLog keyLog = KeyLog.create();
            addKeyToLog(keyLog, bundle.signedPreKey(), Purpose.PRE_KEY);

            assertThat(keyLog.currentPreKey()).isPresent();
            assertThat(keyLog.currentPreKey().get().keyId())
                    .isEqualTo(bundle.signedPreKey().keyId());
        }

        @Test
        void keyLogTracksOneTimePreKeys() {
            Vault vault = createVault();
            PreKeyBundle bundle = PreKeyBundle.generate(vault, null, 5);

            KeyLog keyLog = KeyLog.create();
            for (EncryptionPublicKey opk : bundle.oneTimePreKeys()) {
                addKeyToLog(keyLog, opk, Purpose.ONE_TIME_PRE_KEY);
            }

            List<EncryptionPublicKey> available = keyLog.availableOneTimePreKeys();
            assertThat(available).hasSize(5);
        }

        @Test
        void tombstonedOPKNotAvailable() {
            Vault vault = createVault();
            PreKeyBundle bundle = PreKeyBundle.generate(vault, null, 3);

            KeyLog keyLog = KeyLog.create();
            for (EncryptionPublicKey opk : bundle.oneTimePreKeys()) {
                addKeyToLog(keyLog, opk, Purpose.ONE_TIME_PRE_KEY);
            }
            assertThat(keyLog.availableOneTimePreKeys()).hasSize(3);

            // Tombstone one OPK (consumed)
            byte[] consumedCid = KeyLog.keyCidBytes(bundle.oneTimePreKeys().getFirst());
            keyLog.append(new KeyLog.TombstoneKey(consumedCid, KeyLog.REASON_CONSUMED),
                    seq++, testSigner, testHasher);

            assertThat(keyLog.availableOneTimePreKeys()).hasSize(2);
        }

        @Test
        void fromKeyLogExtractsBundle() {
            Vault vault = createVault();
            PreKeyBundle generated = PreKeyBundle.generate(vault, null, 5);

            // Record in KeyLog
            KeyLog keyLog = KeyLog.create();
            addKeyToLog(keyLog, generated.signedPreKey(), Purpose.PRE_KEY);
            for (EncryptionPublicKey opk : generated.oneTimePreKeys()) {
                addKeyToLog(keyLog, opk, Purpose.ONE_TIME_PRE_KEY);
            }

            // Extract bundle from KeyLog
            PreKeyBundle extracted = PreKeyBundle.fromKeyLog(
                    keyLog, generated.signedPreKeySignature(), generated.signedPreKeyTimestamp());

            assertThat(extracted).isNotNull();
            assertThat(extracted.signedPreKey().keyId()).isEqualTo(generated.signedPreKey().keyId());
            assertThat(extracted.oneTimePreKeyCount()).isEqualTo(5);
            assertThat(extracted.verifySignedPreKey(signingKey(vault))).isTrue();
        }

        @Test
        void fromKeyLogReturnsNullWithoutPreKey() {
            KeyLog keyLog = KeyLog.create();
            assertThat(PreKeyBundle.fromKeyLog(keyLog, new byte[64], 0)).isNull();
        }

        @Test
        void x3dhRoundTripWithKeyLogBundle() {
            // Alice side
            Vault aliceVault = createVault();
            EncryptionPublicKey aliceIK = encryptionKey(aliceVault);

            // Bob generates pre-keys and records in KeyLog
            Vault bobVault = createVault();
            EncryptionPublicKey bobIK = encryptionKey(bobVault);
            SigningPublicKey bobSigningKey = signingKey(bobVault);
            PreKeyBundle generated = PreKeyBundle.generate(bobVault, null, 5);

            KeyLog bobKeyLog = KeyLog.create();
            addKeyToLog(bobKeyLog, generated.signedPreKey(), Purpose.PRE_KEY);
            for (EncryptionPublicKey opk : generated.oneTimePreKeys()) {
                addKeyToLog(bobKeyLog, opk, Purpose.ONE_TIME_PRE_KEY);
            }

            // Alice extracts bundle from Bob's KeyLog (as synced to her)
            PreKeyBundle bobBundle = PreKeyBundle.fromKeyLog(
                    bobKeyLog, generated.signedPreKeySignature(), generated.signedPreKeyTimestamp());

            // X3DH round-trip works with KeyLog-derived bundle
            X3DH.InitiatorResult initiator = X3DH.initiate(
                    aliceVault, aliceIK, bobBundle, bobIK, bobSigningKey);
            X3DH.Result responder = X3DH.respond(
                    bobVault, bobIK, initiator.message());

            assertThat(initiator.result().sharedSecret())
                    .hasSize(32)
                    .isEqualTo(responder.sharedSecret());
        }
    }
}
