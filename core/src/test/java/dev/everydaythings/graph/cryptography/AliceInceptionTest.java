package dev.everydaythings.graph.cryptography;


import dev.everydaythings.graph.datum.Frame;
import dev.everydaythings.graph.ref.DatumRef;
import dev.everydaythings.graph.ref.ItemRef;
import dev.everydaythings.graph.runtime.librarian.Librarian;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests exercising a non-Librarian Signer ("alice") whose INCEPTION
 * is published automatically on construction via {@link Signer#inMemory(Librarian)},
 * then having the librarian's KEL-aware verification recognize her signatures.
 *
 * <p>The Librarian-self-INCEPTION case is covered by {@link LibrarianInceptionTest};
 * this exercises the more general path: any Signer constructed with a vault and a
 * librarian publishes its INCEPTION as part of construction and becomes a
 * recognized identity to that librarian.
 */
class AliceInceptionTest {

    @Test
    @DisplayName("alice's INCEPTION published on construction; lib recognizes her keys")
    void aliceIncepts() {
        Librarian lib = Librarian.inMemory();
        lib.bootstrap();

        Signer alice = Signer.inMemory(lib);

        // lib's KEL-derived view of alice now includes her current signing key
        List<MultiKey> aliceKeys = lib.currentKeys(alice.iid(), ItemRef.iid(IdentityVocabulary.Signing.KEY));
        assertThat(aliceKeys).containsExactly(alice.signingPublicKey().orElseThrow());
    }

    @Test
    @DisplayName("alice's signatures verify via lib.verifySignedAsIdentity(alice.iid)")
    void aliceSignaturesVerify() {
        Librarian lib = Librarian.inMemory();
        lib.bootstrap();

        Signer alice = Signer.inMemory(lib);

        byte[] message = "hello from alice".getBytes();
        VarSig sig = alice.sign(message);

        assertThat(lib.verifySignedAsIdentity(alice.iid(), message, sig)).isTrue();
    }

    @Test
    @DisplayName("bob's signature does not verify under alice's identity")
    void bobsSignatureFailsUnderAlicesIdentity() {
        Librarian lib = Librarian.inMemory();
        lib.bootstrap();

        Signer alice = Signer.inMemory(lib);
        Signer bob = Signer.inMemory(lib);

        byte[] message = "ostensibly from alice".getBytes();
        VarSig bobSig = bob.sign(message);

        // Bob signed it, but we're asking "is this from alice?" — should be false
        assertThat(lib.verifySignedAsIdentity(alice.iid(), message, bobSig)).isFalse();
        // Sanity: it does verify under bob's identity
        assertThat(lib.verifySignedAsIdentity(bob.iid(), message, bobSig)).isTrue();
    }

    @Test
    @DisplayName("alice's INCEPTION is self-attested (signed by her own committed key)")
    void aliceInceptionIsSelfAttested() {
        Librarian lib = Librarian.inMemory();
        lib.bootstrap();

        Signer alice = Signer.inMemory(lib);

        DatumRef inceptionId = alice.vault().orElseThrow()
                .chainHead(ItemRef.iid(IdentityVocabulary.Signing.KEY)).orElseThrow();
        Frame inception = lib.fetchFrame(inceptionId).orElseThrow();
        assertThat(Signer.isSelfAttested(inception)).isTrue();
    }

    @Test
    @DisplayName("identity-only Signer (no vault) has no KEL entry")
    void identityOnlyHasNoKel() {
        Librarian lib = Librarian.inMemory();
        lib.bootstrap();

        Signer ghost = new Signer(ItemRef.fromString("ghost"));
        ghost.bindLibrarian(lib);

        assertThat(ghost.canSign()).isFalse();
        assertThat(lib.currentKeys(ghost.iid(), ItemRef.iid(IdentityVocabulary.Signing.KEY))).isEmpty();
    }

    @Test
    @DisplayName("vault-only Signer (no librarian) does not auto-incept")
    void vaultOnlyDoesNotAutoIncept() {
        Signer floater = Signer.inMemory();

        // No librarian binding — can still sign in-place but nothing published
        assertThat(floater.canSign()).isTrue();
        assertThat(floater.vault().orElseThrow()
                .chainHead(ItemRef.iid(IdentityVocabulary.Signing.KEY))).isEmpty();
    }
}
