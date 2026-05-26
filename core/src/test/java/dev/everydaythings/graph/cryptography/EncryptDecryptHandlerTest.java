package dev.everydaythings.graph.cryptography;

import dev.everydaythings.graph.datum.Binding;
import dev.everydaythings.graph.datum.Body;
import dev.everydaythings.graph.datum.Frame;
import dev.everydaythings.graph.datum.Opaque;
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

/**
 * End-to-end test of the Encrypt and Decrypt command verbs via the
 * {@link Signer} {@code @Seed.Handler}-annotated methods.
 *
 * <p>Bob publishes a SignedPreKey frame.  Alice's Signer handles an Encrypt
 * request producing an encrypted Frame whose record signs the request body.
 * The Frame is persisted to the shared graph.  Bob's Signer handles a
 * Decrypt request referencing that Frame and recovers the plaintext via
 * auto-bootstrap.
 */
@DisplayName("Encrypt and Decrypt command handlers on Signer")
class EncryptDecryptHandlerTest {

    @Test
    @DisplayName("Alice encrypts to Bob; Bob decrypts via command frames")
    void roundTrip() {
        Librarian lib = Librarian.inMemory();
        lib.bootstrap();

        // Two Signers in the same graph (shared librarian).  Each has its own
        // vault.  Auto-incepted on the signing track.
        Signer alice = new Signer(lib);
        Signer bob = new Signer(lib);

        // Each Signer also incepts its key-agreement track (so peers can
        // resolve key-agreement pubkeys via INCEPTION lookup), and publishes
        // a SignedPreKey frame (so peers can run X3DH against them).
        alice.inception(ItemRef.iid(IdentityVocabulary.KeyAgreement.KEY));
        bob.inception(ItemRef.iid(IdentityVocabulary.KeyAgreement.KEY));
        alice.publishSignedPreKey();
        bob.publishSignedPreKey();

        // Sanity check: graph-fetched keys match the originals.
        MultiKey bobKaDirect = bob.vault().orElseThrow().keyAgreementPublicKey().orElseThrow();
        MultiKey bobKaFetched = alice.fetchPeerKeyAgreementKey(bob.iid()).orElseThrow();
        assertThat(bobKaFetched.rawKey()).isEqualTo(bobKaDirect.rawKey());

        MultiKey bobSpkDirect = bob.vault().orElseThrow().signedPreKeyPublicKey().orElseThrow();
        MultiKey bobSpkFetched = alice.fetchPeerSignedPreKey(bob.iid()).orElseThrow();
        assertThat(bobSpkFetched.rawKey()).isEqualTo(bobSpkDirect.rawKey());

        // Alice constructs an Encrypt command body.
        byte[] plaintext = "hello, bob — from a command frame".getBytes(StandardCharsets.UTF_8);
        Body encryptRequest = Body.of(
                ItemRef.of(ItemRef.iid(Encrypt.KEY)),
                List.of(
                        Binding.ref(ItemRef.iid(ThematicRole.Agent.KEY), alice.iid()),
                        new Binding(ItemRef.iid(ThematicRole.Theme.KEY), plaintext),
                        Binding.ref(ItemRef.iid(ThematicRole.Beneficiary.KEY), bob.iid()),
                        Binding.ref(ItemRef.iid(ThematicRole.Instrument.KEY),
                                ItemRef.iid(DoubleRatchetV1.KEY)),
                        new Binding(ItemRef.iid(ThematicRole.Time.KEY), Instant.now())));

        // Alice's handler executes the command, producing the encrypted frame.
        Frame result = alice.handleEncrypt(Frame.of(encryptRequest, List.of()));

        // Result body has a THEME binding holding the Opaque.Encrypted.
        assertThat(result.body().head()).isInstanceOfSatisfying(
                ItemRef.class,
                ir -> assertThat(ir.iid()).isEqualTo(ItemRef.iid(Encrypt.KEY)));
        Object themeTarget = result.body().binding(
                dev.everydaythings.graph.ref.CompoundKey.of(ItemRef.iid(ThematicRole.Theme.KEY)))
                .orElseThrow().target();
        assertThat(themeTarget).isInstanceOf(Opaque.Encrypted.class);

        // The result record signs the original Encrypt request body.
        assertThat(result.records()).hasSize(1);
        Record metadataRecord = result.records().get(0);
        assertThat(metadataRecord.head()).isEqualTo(DatumRef.of(encryptRequest.datumId()));

        // Publish the request body + result frame + record into the shared graph
        // so Bob's handler can find them by ref.
        lib.persist(encryptRequest);
        lib.persist(result.body());
        for (Record r : result.records()) lib.persist(r);

        // Need a fresh Bob who hasn't already auto-bootstrapped (closeSession to reset).
        bob.vault().orElseThrow().closeSession(alice.iid());

        // Bob constructs a Decrypt command body pointing at the result frame's body.
        Body decryptRequest = Body.of(
                ItemRef.of(ItemRef.iid(Decrypt.KEY)),
                List.of(
                        Binding.ref(ItemRef.iid(ThematicRole.Agent.KEY), bob.iid()),
                        new Binding(ItemRef.iid(ThematicRole.Theme.KEY), DatumRef.of(result.body().datumId())),
                        new Binding(ItemRef.iid(ThematicRole.Time.KEY), Instant.now())));

        byte[] recovered = bob.handleDecrypt(Frame.of(decryptRequest, List.of()));

        assertThat(recovered).isEqualTo(plaintext);
    }
}
