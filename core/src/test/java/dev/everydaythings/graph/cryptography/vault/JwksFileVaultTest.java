package dev.everydaythings.graph.cryptography.vault;

import dev.everydaythings.graph.cryptography.MultiKey;
import dev.everydaythings.graph.cryptography.VarSig;
import dev.everydaythings.graph.cryptography.algorithm.Signing;
import dev.everydaythings.graph.ref.ItemRef;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Round-trip tests for {@link JwksFileVault} — create, persist to disk,
 * load back, verify keys + identity + operations match.
 */
class JwksFileVaultTest {

    @Test
    @DisplayName("create() generates fresh keypairs and writes keys.jwks")
    void createWritesFile(@TempDir Path dir) {
        JwksFileVault vault = JwksFileVault.create(dir);

        assertThat(Files.isRegularFile(dir.resolve("keys.jwks"))).isTrue();
        assertThat(vault.identity()).isNotNull();
        assertThat(vault.canSign()).isTrue();
        assertThat(vault.canKeyAgree()).isTrue();
        assertThat(vault.signingPublicKey()).isPresent();
        assertThat(vault.keyAgreementPublicKey()).isPresent();
    }

    @Test
    @DisplayName("create() refuses to clobber existing vault file")
    void createRefusesExisting(@TempDir Path dir) {
        JwksFileVault.create(dir);
        assertThatThrownBy(() -> JwksFileVault.create(dir))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Vault already exists");
    }

    @Test
    @DisplayName("load() reconstructs vault with same identity as create()")
    void loadPreservesIdentity(@TempDir Path dir) {
        JwksFileVault first  = JwksFileVault.create(dir);
        ItemRef firstIid     = first.identity();
        MultiKey firstSigningPub = first.signingPublicKey().orElseThrow();

        JwksFileVault second = JwksFileVault.load(dir);

        assertThat(second.identity()).isEqualTo(firstIid);
        assertThat(second.signingPublicKey().orElseThrow().encoded())
                .isEqualTo(firstSigningPub.encoded());
    }

    @Test
    @DisplayName("loaded vault can sign and the signature verifies")
    void loadedVaultCanSign(@TempDir Path dir) {
        JwksFileVault first  = JwksFileVault.create(dir);
        JwksFileVault loaded = JwksFileVault.load(dir);

        byte[] msg = "persisted across restart".getBytes();
        VarSig sig = loaded.sign(msg);

        Signing alg = (Signing) loaded.rootIdentity().currentSigning.algorithm();
        assertThat(alg.verify(msg, sig.rawSig(), loaded.rootIdentity().currentSigning.publicKey()))
                .isTrue();

        // And the signature verifies under the ORIGINAL vault's public key too —
        // proving the keypair persisted.
        assertThat(alg.verify(msg, sig.rawSig(), first.rootIdentity().currentSigning.publicKey()))
                .isTrue();
    }

    @Test
    @DisplayName("loaded vault produces same key-agreement secret as the original")
    void loadedVaultKeyAgreementMatches(@TempDir Path dir) {
        JwksFileVault first  = JwksFileVault.create(dir);

        // Use a separate peer vault.
        InMemoryVault peer = InMemoryVault.generate();
        java.security.PublicKey peerPub = peer.rootIdentity().currentKeyAgreement.publicKey();

        byte[] firstSecret = first.agree(peerPub);

        JwksFileVault loaded = JwksFileVault.load(dir);
        byte[] loadedSecret = loaded.agree(peerPub);

        assertThat(loadedSecret).isEqualTo(firstSecret);
    }

    @Test
    @DisplayName("load() throws when no vault file exists")
    void loadMissingFile(@TempDir Path dir) {
        assertThatThrownBy(() -> JwksFileVault.load(dir))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("No vault file");
    }

    @Test
    @DisplayName("rootIdentity() works on a loaded vault (handles route correctly)")
    void loadedRootIdentityWorks(@TempDir Path dir) {
        JwksFileVault.create(dir);
        JwksFileVault loaded = JwksFileVault.load(dir);

        Identity identity = loaded.rootIdentity();
        assertThat(identity.currentSigning).isNotNull();
        assertThat(identity.nextSigning).isNotNull();
        assertThat(identity.currentKeyAgreement).isNotNull();
        assertThat(identity.currentSignedPreKey).isNotNull();
    }
}
