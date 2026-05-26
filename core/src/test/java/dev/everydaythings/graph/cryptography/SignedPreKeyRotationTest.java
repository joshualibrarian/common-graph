package dev.everydaythings.graph.cryptography;

import dev.everydaythings.graph.datum.Binding;
import dev.everydaythings.graph.datum.Body;
import dev.everydaythings.graph.datum.Frame;
import dev.everydaythings.graph.datum.Record;
import dev.everydaythings.graph.ref.DatumRef;
import dev.everydaythings.graph.ref.ItemRef;
import dev.everydaythings.graph.cryptography.EncryptionVocabulary.Decrypt;
import dev.everydaythings.graph.cryptography.EncryptionVocabulary.DoubleRatchetV1;
import dev.everydaythings.graph.cryptography.EncryptionVocabulary.Encrypt;
import dev.everydaythings.graph.language.ThematicRole;
import dev.everydaythings.graph.runtime.librarian.Librarian;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * End-to-end test of signed pre-key rotation.
 *
 * <ul>
 *   <li>Bob publishes an initial SPK.  Alice opens a session against it.</li>
 *   <li>Bob rotates.  His vault now holds two SPKs (old + new).  Senders
 *       fetching now get the NEW SPK frame (latest by TIME).</li>
 *   <li>Alice (fresh session) uses the new SPK; Bob decrypts.</li>
 *   <li>An in-flight session that used the OLD SPK still decrypts (private
 *       side retained).</li>
 *   <li>Bob destroys old SPKs.  Old-SPK bootstrap messages now fail.</li>
 * </ul>
 */
@DisplayName("Signed pre-key rotation")
class SignedPreKeyRotationTest {

    @Test
    @DisplayName("rotation: new SPK published, old retained until destroyed")
    void rotationRetainsOldUntilDestroyed() throws Exception {
        Librarian lib = Librarian.inMemory();
        lib.bootstrap();

        Signer alice = new Signer(lib);
        Signer bob = new Signer(lib);
        alice.inception(ItemRef.iid(IdentityVocabulary.KeyAgreement.KEY));
        bob.inception(ItemRef.iid(IdentityVocabulary.KeyAgreement.KEY));
        alice.publishSignedPreKey();
        bob.publishSignedPreKey();

        // Capture Bob's initial SPK pubkey for later comparison.
        MultiKey oldSpk = bob.vault().orElseThrow().signedPreKeyPublicKey().orElseThrow();

        // ALICE_1 — opens a session against Bob's OLD SPK and encrypts.
        Frame request1 = makeEncryptRequest(alice.iid(), bob.iid(),
                "before rotation".getBytes(StandardCharsets.UTF_8));
        Frame encrypt1 = alice.handleEncrypt(request1);
        lib.persist(request1.body());
        persistFrame(lib, encrypt1);

        // Bob rotates.  His vault now holds [new, old].  The NEW SPK becomes
        // current; old is retained for in-flight first-messages.
        // Sleep 2 ms to ensure the TIME binding on the new SPK frame is
        // strictly later than the old one (latest-by-TIME selection).
        Thread.sleep(2);
        Frame newSpkFrame = bob.rotateSignedPreKey().orElseThrow();
        MultiKey newSpk = bob.vault().orElseThrow().signedPreKeyPublicKey().orElseThrow();
        assertThat(newSpk.rawKey()).isNotEqualTo(oldSpk.rawKey());

        // Alice fetching Bob's SPK now sees the NEW one (latest by TIME).
        MultiKey fetched = alice.fetchPeerSignedPreKey(bob.iid()).orElseThrow();
        assertThat(fetched.rawKey()).isEqualTo(newSpk.rawKey());

        // ALICE_2 — opens a fresh session against Bob's NEW SPK and encrypts.
        // (Need to close the existing session so handleEncrypt re-opens.)
        alice.vault().orElseThrow().closeSession(bob.iid());
        Frame request2 = makeEncryptRequest(alice.iid(), bob.iid(),
                "after rotation".getBytes(StandardCharsets.UTF_8));
        Frame encrypt2 = alice.handleEncrypt(request2);
        lib.persist(request2.body());
        persistFrame(lib, encrypt2);

        // Bob receives encrypt2 (using NEW SPK).  Decrypts successfully.
        bob.vault().orElseThrow().closeSession(alice.iid());
        byte[] recovered2 = bob.handleDecrypt(makeDecryptRequest(
                bob.iid(), DatumRef.of(encrypt2.body().datumId())));
        assertThat(recovered2).isEqualTo("after rotation".getBytes(StandardCharsets.UTF_8));

        // Bob receives the IN-FLIGHT encrypt1 (which used the OLD SPK).  The
        // old SPK private side is still in the vault, so decryption works.
        bob.vault().orElseThrow().closeSession(alice.iid());
        byte[] recovered1 = bob.handleDecrypt(makeDecryptRequest(
                bob.iid(), DatumRef.of(encrypt1.body().datumId())));
        assertThat(recovered1).isEqualTo("before rotation".getBytes(StandardCharsets.UTF_8));

        // Bob destroys old SPKs.  Now the OLD SPK's private side is gone.
        // Replaying encrypt1 fails because Bob can't find the matching SPK.
        bob.vault().orElseThrow().destroyOldSignedPreKeys();
        bob.vault().orElseThrow().closeSession(alice.iid());
        assertThatThrownBy(() -> bob.handleDecrypt(makeDecryptRequest(
                bob.iid(), DatumRef.of(encrypt1.body().datumId()))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("SPK");

        // But the new SPK still works.
        bob.vault().orElseThrow().closeSession(alice.iid());
        byte[] recovered2Again = bob.handleDecrypt(makeDecryptRequest(
                bob.iid(), DatumRef.of(encrypt2.body().datumId())));
        assertThat(recovered2Again).isEqualTo("after rotation".getBytes(StandardCharsets.UTF_8));
    }

    // ==================================================================================
    // Helpers
    // ==================================================================================

    private static Frame makeEncryptRequest(ItemRef agentIid, ItemRef beneficiaryIid, byte[] plaintext) {
        Body body = Body.of(
                ItemRef.of(ItemRef.iid(Encrypt.KEY)),
                List.of(
                        Binding.ref(ItemRef.iid(ThematicRole.Agent.KEY), agentIid),
                        new Binding(ItemRef.iid(ThematicRole.Theme.KEY), plaintext),
                        Binding.ref(ItemRef.iid(ThematicRole.Beneficiary.KEY), beneficiaryIid),
                        Binding.ref(ItemRef.iid(ThematicRole.Instrument.KEY),
                                ItemRef.iid(DoubleRatchetV1.KEY)),
                        new Binding(ItemRef.iid(ThematicRole.Time.KEY), Instant.now())));
        return Frame.of(body, List.of());
    }

    private static Frame makeDecryptRequest(ItemRef agentIid, DatumRef encryptedBodyRef) {
        Body body = Body.of(
                ItemRef.of(ItemRef.iid(Decrypt.KEY)),
                List.of(
                        Binding.ref(ItemRef.iid(ThematicRole.Agent.KEY), agentIid),
                        new Binding(ItemRef.iid(ThematicRole.Theme.KEY), encryptedBodyRef),
                        new Binding(ItemRef.iid(ThematicRole.Time.KEY), Instant.now())));
        return Frame.of(body, List.of());
    }

    private static void persistFrame(Librarian lib, Frame frame) {
        lib.persist(frame.body());
        for (Record r : frame.records()) lib.persist(r);
    }
}
