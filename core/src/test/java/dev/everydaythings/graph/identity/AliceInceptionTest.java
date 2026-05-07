package dev.everydaythings.graph.identity;

import dev.everydaythings.graph.crypt.MultiKey;
import dev.everydaythings.graph.crypt.VarSig;
import dev.everydaythings.graph.frame.Frame;
import dev.everydaythings.graph.item.user.Signer;
import dev.everydaythings.graph.runtime.Librarian;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests exercising a non-Librarian Signer ("alice") publishing her
 * own INCEPTION via a librarian, then having the librarian's KEL-aware
 * verification recognize her signatures.
 *
 * <p>The Librarian-self-INCEPTION case is covered by {@link LibrarianInceptionTest};
 * this exercises the more general path: any Signer with signing capability,
 * bound to a librarian, can publish its own INCEPTION and become a recognized
 * identity to that librarian.
 */
class AliceInceptionTest {

    @Test
    @DisplayName("alice publishes her own INCEPTION via lib; lib recognizes her keys")
    void aliceIncepts() {
        Librarian lib = Librarian.inMemory();
        lib.bootstrap();

        Signer alice = Signer.inMemory();
        alice.bindLibrarian(lib);

        Optional<Frame> inception = alice.publishSelfInception();
        assertThat(inception).isPresent();

        // lib's KEL-derived view of alice now includes her current signing key
        List<MultiKey> aliceKeys = lib.signingKeysForIdentity(alice.iid());
        assertThat(aliceKeys).containsExactly(alice.signingPublicKey().orElseThrow());
    }

    @Test
    @DisplayName("alice's signatures verify via lib.verifySignedAsIdentity(alice.iid)")
    void aliceSignaturesVerify() {
        Librarian lib = Librarian.inMemory();
        lib.bootstrap();

        Signer alice = Signer.inMemory();
        alice.bindLibrarian(lib);
        alice.publishSelfInception();

        byte[] message = "hello from alice".getBytes();
        VarSig sig = alice.sign(message);

        assertThat(lib.verifySignedAsIdentity(alice.iid(), message, sig)).isTrue();
    }

    @Test
    @DisplayName("bob's signature does not verify under alice's identity")
    void bobsSignatureFailsUnderAlicesIdentity() {
        Librarian lib = Librarian.inMemory();
        lib.bootstrap();

        Signer alice = Signer.inMemory();
        alice.bindLibrarian(lib);
        alice.publishSelfInception();

        Signer bob = Signer.inMemory();
        bob.bindLibrarian(lib);
        bob.publishSelfInception();

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

        Signer alice = Signer.inMemory();
        alice.bindLibrarian(lib);

        Frame inception = alice.publishSelfInception().orElseThrow();
        assertThat(Inception.isSelfAttested(inception)).isTrue();
    }

    @Test
    @DisplayName("identity-only Signer (no vault) cannot publishSelfInception")
    void identityOnlyCannotIncept() {
        Librarian lib = Librarian.inMemory();
        lib.bootstrap();

        Signer ghost = new Signer(dev.everydaythings.graph.item.id.ItemID.fromString("ghost"));
        ghost.bindLibrarian(lib);

        assertThat(ghost.publishSelfInception()).isEmpty();
        assertThat(lib.signingKeysForIdentity(ghost.iid())).isEmpty();
    }

    @Test
    @DisplayName("Signer without librarian binding cannot publishSelfInception")
    void unboundCannotIncept() {
        Signer floater = Signer.inMemory();
        // No bindLibrarian call.

        assertThat(floater.publishSelfInception()).isEmpty();
    }
}
