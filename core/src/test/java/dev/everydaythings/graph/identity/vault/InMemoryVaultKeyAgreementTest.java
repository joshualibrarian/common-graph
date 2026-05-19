package dev.everydaythings.graph.identity.vault;

import dev.everydaythings.graph.id.ItemRef;
import dev.everydaythings.graph.identity.IdentityVocabulary;
import dev.everydaythings.graph.identity.MultiKey;
import dev.everydaythings.graph.identity.algorithm.KeyAgreement;
import dev.everydaythings.graph.identity.algorithm.Signing;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.security.PublicKey;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("InMemoryVault key-agreement track")
class InMemoryVaultKeyAgreementTest {

    @Test
    @DisplayName("generate() mints an X25519 keypair on the key-agreement purpose")
    void keyAgreementTrackPresent() {
        InMemoryVault vault = InMemoryVault.generate();
        assertThat(vault.canKeyAgree()).isTrue();
        assertThat(vault.keyAgreementAlgorithm())
                .contains(ItemRef.iid(KeyAgreement.X25519.KEY));
        assertThat(vault.keyAgreementPublicKey()).isPresent();
        assertThat(vault.keyAgreementNextKeyDigest()).isPresent();
    }

    @Test
    @DisplayName("key-agreement public key is a self-describing X25519 MultiKey")
    void publicKeyIsX25519MultiKey() {
        InMemoryVault vault = InMemoryVault.generate();
        MultiKey mk = vault.keyAgreementPublicKey().orElseThrow();
        assertThat(mk.code()).isEqualTo((int) KeyAgreement.X25519.MULTIKEY_CODE);
        assertThat(mk.rawKey()).hasSize(32);
    }

    @Test
    @DisplayName("signing and key-agreement tracks are independent (different keys)")
    void tracksIndependent() {
        InMemoryVault vault = InMemoryVault.generate();
        MultiKey signing = vault.signingPublicKey().orElseThrow();
        MultiKey keyAgreement = vault.keyAgreementPublicKey().orElseThrow();
        assertThat(signing.code()).isEqualTo((int) Signing.Ed25519.MULTIKEY_CODE);
        assertThat(keyAgreement.code()).isEqualTo((int) KeyAgreement.X25519.MULTIKEY_CODE);
        assertThat(signing.encoded()).isNotEqualTo(keyAgreement.encoded());
    }

    @Nested
    @DisplayName("ECDH agreement")
    class Agree {

        @Test
        @DisplayName("two vaults derive the same shared secret from each other's public keys")
        void mutualAgreement() {
            InMemoryVault alice = InMemoryVault.generate();
            InMemoryVault bob = InMemoryVault.generate();

            KeyAgreement x25519 = KeyAgreement.X25519.builtin();
            PublicKey alicePub = x25519.decodePublicKey(
                    alice.keyAgreementPublicKey().orElseThrow().rawKey());
            PublicKey bobPub = x25519.decodePublicKey(
                    bob.keyAgreementPublicKey().orElseThrow().rawKey());

            byte[] aliceShared = alice.agree(bobPub);
            byte[] bobShared = bob.agree(alicePub);

            assertThat(aliceShared).hasSize(32);
            assertThat(aliceShared).isEqualTo(bobShared);
        }
    }

    @Nested
    @DisplayName("KEL events on the key-agreement track")
    class KelEvents {

        @Test
        @DisplayName("can incept the key-agreement track")
        void inceptKa() {
            InMemoryVault vault = InMemoryVault.generate();
            ItemRef ka = ItemRef.iid(IdentityVocabulary.KeyAgreement.KEY);

            assertThat(vault.chainHead(ka)).isEmpty();
            assertThat(vault.sequence(ka)).isZero();

            vault.incept(ka);

            assertThat(vault.chainHead(ka)).isPresent();
            assertThat(vault.sequence(ka)).isEqualTo(1L);
        }

        @Test
        @DisplayName("can rotate the key-agreement track")
        void rotateKa() {
            InMemoryVault vault = InMemoryVault.generate();
            ItemRef ka = ItemRef.iid(IdentityVocabulary.KeyAgreement.KEY);

            MultiKey beforeRotation = vault.keyAgreementPublicKey().orElseThrow();
            vault.incept(ka);
            vault.rotate(ka);

            assertThat(vault.sequence(ka)).isEqualTo(2L);
            MultiKey afterRotation = vault.keyAgreementPublicKey().orElseThrow();
            assertThat(afterRotation.encoded()).isNotEqualTo(beforeRotation.encoded());
        }
    }
}
