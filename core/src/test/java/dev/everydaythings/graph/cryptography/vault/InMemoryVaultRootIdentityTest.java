package dev.everydaythings.graph.cryptography.vault;

import dev.everydaythings.graph.cryptography.algorithm.Signing;
import dev.everydaythings.graph.ref.ItemRef;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.security.PublicKey;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Smoke tests for {@link InMemoryVault#rootIdentity()} — the new entry-based
 * API surface synthesizing an {@link Identity} entry from the vault's
 * existing purpose state.
 */
class InMemoryVaultRootIdentityTest {

    @Test
    @DisplayName("rootIdentity() returns an Identity with current+next signing handles")
    void hasSigningHandles() {
        InMemoryVault vault = InMemoryVault.generate();
        Identity identity = vault.rootIdentity();

        assertThat(identity).isNotNull();
        assertThat(identity.currentSigning).isNotNull();
        assertThat(identity.nextSigning).isNotNull();
        assertThat(identity.currentSigning.algorithm()).isInstanceOf(Signing.class);
        assertThat(identity.currentSigning.publicKey()).isNotNull();
    }

    @Test
    @DisplayName("rootIdentity() returns key-agreement handles")
    void hasKeyAgreementHandles() {
        InMemoryVault vault = InMemoryVault.generate();
        Identity identity = vault.rootIdentity();

        assertThat(identity.currentKeyAgreement).isNotNull();
        assertThat(identity.nextKeyAgreement).isNotNull();
        assertThat(identity.currentSignedPreKey).isNotNull();
        assertThat(identity.oneTimePreKeys).isNotEmpty();
    }

    @Test
    @DisplayName("currentSigning.sign(msg) produces a verifiable signature")
    void signingHandleWorks() {
        InMemoryVault vault = InMemoryVault.generate();
        Identity identity = vault.rootIdentity();
        Signing alg = (Signing) identity.currentSigning.algorithm();
        PublicKey pub = identity.currentSigning.publicKey();

        byte[] msg = "hello from the entry-based vault".getBytes();
        byte[] sig = identity.currentSigning.sign(msg);

        assertThat(alg.verify(msg, sig, pub)).isTrue();
    }

    @Test
    @DisplayName("currentKeyAgreement.agree(peerPub) derives the same secret as direct vault.agree")
    void keyAgreementHandleWorks() {
        InMemoryVault vault = InMemoryVault.generate();
        InMemoryVault peer  = InMemoryVault.generate();

        Identity identity = vault.rootIdentity();
        PublicKey peerPub = peer.rootIdentity().currentKeyAgreement.publicKey();

        byte[] viaHandle = identity.currentKeyAgreement.agree(peerPub);
        byte[] viaLegacy = vault.agree(peerPub);

        assertThat(viaHandle).isEqualTo(viaLegacy);
    }

    @Test
    @DisplayName("rootIdentity() returns the same EntryId across calls")
    void entryIdIsStable() {
        InMemoryVault vault = InMemoryVault.generate();
        EntryId id1 = vault.rootIdentity().id();
        EntryId id2 = vault.rootIdentity().id();
        assertThat(id1).isEqualTo(id2);
    }

    @Test
    @DisplayName("rootIdentity().archetype() returns the Identity archetype IID")
    void archetypeIsIdentity() {
        InMemoryVault vault = InMemoryVault.generate();
        Identity identity = vault.rootIdentity();
        assertThat(identity.archetype()).isEqualTo(ItemRef.iid(Identity.KEY));
    }
}
