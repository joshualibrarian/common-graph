package dev.everydaythings.graph.identity.jca;

import dev.everydaythings.graph.id.ItemRef;
import dev.everydaythings.graph.identity.IdentityVocabulary;
import dev.everydaythings.graph.identity.MultiKey;
import dev.everydaythings.graph.identity.vault.InMemoryVault;
import dev.everydaythings.graph.identity.vault.Vault;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.security.InvalidKeyException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PublicKey;
import java.security.Security;
import java.security.Signature;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * End-to-end tests for the Vault JCA Provider.
 *
 * <p>The headline test is "sign through Vault via JCA Signature API → verify
 * with JDK against Vault's public key → it works."  That's the cryptographic
 * contract: this provider is just a delegation shim.
 */
class VaultProviderTest {

    @BeforeAll
    static void installProvider() {
        VaultProvider.install();
    }

    @AfterAll
    static void uninstallProvider() {
        VaultProvider.uninstall();
    }

    @Nested
    @DisplayName("Provider registration")
    class Registration {

        @Test
        @DisplayName("install() registers under the CG-Vault name")
        void installRegisters() {
            assertThat(Security.getProvider(VaultProvider.NAME)).isNotNull();
        }

        @Test
        @DisplayName("install() is idempotent")
        void installIdempotent() {
            VaultProvider.install();
            VaultProvider.install();
            assertThat(Security.getProvider(VaultProvider.NAME)).isNotNull();
        }

        @Test
        @DisplayName("Signature.getInstance with our name returns a working SPI")
        void signatureLookup() throws Exception {
            Signature sig = Signature.getInstance("Ed25519", VaultProvider.NAME);
            assertThat(sig).isNotNull();
            assertThat(sig.getProvider().getName()).isEqualTo(VaultProvider.NAME);
        }
    }

    @Nested
    @DisplayName("Sign via Vault, verify via JDK")
    class CrossProviderRoundTrip {

        @Test
        @DisplayName("end-to-end: bytes signed via Vault verify against the Vault's pubkey")
        void signVerifyRoundTrip() throws Exception {
            Vault vault = InMemoryVault.generate();
            byte[] message = "hello from Vault-backed JCA".getBytes();

            // Sign via the Vault provider
            Signature signer = Signature.getInstance("Ed25519", VaultProvider.NAME);
            signer.initSign(VaultPrivateKey.signing(vault));
            signer.update(message);
            byte[] signature = signer.sign();

            // Verify via the default JDK provider, using the Vault's pubkey
            MultiKey vaultPubkey = vault.signingPublicKey().orElseThrow();
            PublicKey jdkPubkey = vaultPubkey.publicKey();
            Signature verifier = Signature.getInstance("Ed25519");
            verifier.initVerify(jdkPubkey);
            verifier.update(message);
            assertThat(verifier.verify(signature))
                    .as("signature produced via Vault must verify against the Vault's pubkey")
                    .isTrue();
        }

        @Test
        @DisplayName("update via multiple chunks accumulates correctly")
        void multipleUpdateChunks() throws Exception {
            Vault vault = InMemoryVault.generate();
            byte[] part1 = "the quick brown fox ".getBytes();
            byte[] part2 = "jumps over the lazy dog".getBytes();
            byte[] whole = new byte[part1.length + part2.length];
            System.arraycopy(part1, 0, whole, 0, part1.length);
            System.arraycopy(part2, 0, whole, part1.length, part2.length);

            Signature signer = Signature.getInstance("Ed25519", VaultProvider.NAME);
            signer.initSign(VaultPrivateKey.signing(vault));
            signer.update(part1);
            signer.update(part2);
            byte[] sig = signer.sign();

            PublicKey pubkey = vault.signingPublicKey().orElseThrow().publicKey();
            Signature verifier = Signature.getInstance("Ed25519");
            verifier.initVerify(pubkey);
            verifier.update(whole);
            assertThat(verifier.verify(sig)).isTrue();
        }

        @Test
        @DisplayName("two independent signing sessions don't bleed into each other")
        void signatureSessionsIsolated() throws Exception {
            Vault vault = InMemoryVault.generate();

            Signature first = Signature.getInstance("Ed25519", VaultProvider.NAME);
            first.initSign(VaultPrivateKey.signing(vault));
            first.update("first".getBytes());
            byte[] firstSig = first.sign();

            // Now sign different bytes through a different Signature instance.
            Signature second = Signature.getInstance("Ed25519", VaultProvider.NAME);
            second.initSign(VaultPrivateKey.signing(vault));
            second.update("second".getBytes());
            byte[] secondSig = second.sign();

            // Each verifies against its own message, not the other's.
            PublicKey pubkey = vault.signingPublicKey().orElseThrow().publicKey();
            Signature verifier = Signature.getInstance("Ed25519");
            verifier.initVerify(pubkey);
            verifier.update("first".getBytes());
            assertThat(verifier.verify(firstSig)).isTrue();

            verifier.initVerify(pubkey);
            verifier.update("second".getBytes());
            assertThat(verifier.verify(secondSig)).isTrue();
        }
    }

    @Nested
    @DisplayName("Verify path delegates to JDK")
    class VerifyDelegation {

        @Test
        @DisplayName("can verify a JDK-signed message via our provider's Signature object")
        void verifyJdkSignedViaOurProvider() throws Exception {
            // Generate a JDK-side keypair, sign a message with JDK, verify via our provider.
            KeyPairGenerator gen = KeyPairGenerator.getInstance("Ed25519");
            KeyPair jdkPair = gen.generateKeyPair();
            byte[] message = "from jdk".getBytes();

            Signature jdkSigner = Signature.getInstance("Ed25519");
            jdkSigner.initSign(jdkPair.getPrivate());
            jdkSigner.update(message);
            byte[] signature = jdkSigner.sign();

            Signature ourVerifier = Signature.getInstance("Ed25519", VaultProvider.NAME);
            ourVerifier.initVerify(jdkPair.getPublic());
            ourVerifier.update(message);
            assertThat(ourVerifier.verify(signature)).isTrue();
        }
    }

    @Nested
    @DisplayName("Input validation")
    class InputValidation {

        @Test
        @DisplayName("initSign rejects non-VaultPrivateKey")
        void initSignRejectsForeignKey() throws Exception {
            KeyPairGenerator gen = KeyPairGenerator.getInstance("Ed25519");
            KeyPair jdkPair = gen.generateKeyPair();

            Signature sig = Signature.getInstance("Ed25519", VaultProvider.NAME);
            assertThatThrownBy(() -> sig.initSign(jdkPair.getPrivate()))
                    .isInstanceOf(InvalidKeyException.class)
                    .hasMessageContaining("VaultPrivateKey");
        }

        @Test
        @DisplayName("VaultPrivateKey reports null format / null encoded (opaque-handle convention)")
        void opaqueHandleSemantics() {
            Vault vault = InMemoryVault.generate();
            VaultPrivateKey key = VaultPrivateKey.signing(vault);

            assertThat(key.getAlgorithm()).isEqualTo("Ed25519");
            assertThat(key.getFormat()).isNull();
            assertThat(key.getEncoded()).isNull();
            assertThat(key.purpose())
                    .isEqualTo(ItemRef.iid(IdentityVocabulary.Signing.KEY));
        }
    }

}
