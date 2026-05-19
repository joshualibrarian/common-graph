package dev.everydaythings.graph.identity;

import dev.everydaythings.graph.datum.Binding;
import dev.everydaythings.graph.datum.Body;
import dev.everydaythings.graph.datum.Frame;
import dev.everydaythings.graph.datum.Opaque;
import dev.everydaythings.graph.datum.Record;
import dev.everydaythings.graph.id.DatumRef;
import dev.everydaythings.graph.id.ItemRef;
import dev.everydaythings.graph.identity.EncryptionVocabulary.ConsumedPreKey;
import dev.everydaythings.graph.identity.EncryptionVocabulary.Decrypt;
import dev.everydaythings.graph.identity.EncryptionVocabulary.DoubleRatchetV1;
import dev.everydaythings.graph.identity.EncryptionVocabulary.Encrypt;
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
 * End-to-end test of one-time pre-keys (OTPK) in X3DH:
 *
 * <ul>
 *   <li>Bob publishes both a SignedPreKey and a OneTimePreKey frame.</li>
 *   <li>Alice's Encrypt handler fetches the OTPK and consumes it in X3DH-4DH.</li>
 *   <li>The first encrypted frame's record carries a CONSUMED_PRE_KEY binding.</li>
 *   <li>Bob's Decrypt handler consumes the OTPK from his vault (destroying
 *       the private side) and decrypts.</li>
 *   <li>A second decryption attempt referencing the same OTPK fails: the
 *       private side is gone.</li>
 * </ul>
 */
@DisplayName("One-time pre-keys (OTPK) in X3DH")
class OneTimePreKeyTest {

    @Test
    @DisplayName("Alice consumes Bob's OTPK; Bob decrypts and destroys it; replay fails")
    void otpkConsumedAndDestroyed() {
        Librarian lib = Librarian.inMemory();
        lib.bootstrap();

        Signer alice = new Signer(lib);
        Signer bob = new Signer(lib);

        alice.inception(ItemRef.iid(IdentityVocabulary.KeyAgreement.KEY));
        bob.inception(ItemRef.iid(IdentityVocabulary.KeyAgreement.KEY));
        alice.publishSignedPreKey();
        bob.publishSignedPreKey();

        // Bob publishes his one-time pre-key.
        Frame otpkFrame = bob.publishOneTimePreKey().orElseThrow();
        assertThat(otpkFrame.body().head()).isInstanceOfSatisfying(
                ItemRef.class,
                ir -> assertThat(ir.iid()).isEqualTo(ItemRef.iid(IdentityVocabulary.OneTimePreKey.KEY)));

        // Sanity: Alice can fetch it from the shared graph.
        MultiKey bobOtpkBeforeFetch = bob.vault().orElseThrow().oneTimePreKeyPublicKey().orElseThrow();
        MultiKey bobOtpkFetched = alice.fetchPeerOneTimePreKey(bob.iid()).orElseThrow();
        assertThat(bobOtpkFetched.rawKey()).isEqualTo(bobOtpkBeforeFetch.rawKey());

        // Alice encrypts a message.  Handler picks up Bob's OTPK and uses it.
        byte[] plaintext = "with extra forward secrecy".getBytes(StandardCharsets.UTF_8);
        Body encryptRequest = Body.of(
                ItemRef.of(ItemRef.iid(Encrypt.KEY)),
                List.of(
                        Binding.ref(ItemRef.iid(ThematicRole.Agent.KEY), alice.iid()),
                        new Binding(
                                ItemRef.iid(ThematicRole.Theme.KEY),
                                List.of(),
                                plaintext),
                        Binding.ref(ItemRef.iid(ThematicRole.Beneficiary.KEY), bob.iid()),
                        Binding.ref(ItemRef.iid(ThematicRole.Instrument.KEY),
                                ItemRef.iid(DoubleRatchetV1.KEY)),
                        new Binding(
                                ItemRef.iid(ThematicRole.Time.KEY),
                                List.of(),
                                Instant.now())));
        Frame result = alice.handleEncrypt(Frame.of(encryptRequest, List.of()));

        // The result record carries a CONSUMED_PRE_KEY binding naming Bob's OTPK.
        Record metadataRecord = result.records().get(0);
        boolean hasConsumed = metadataRecord.bindings().stream()
                .anyMatch(b -> ItemRef.iid(ConsumedPreKey.KEY).equals(b.role()));
        assertThat(hasConsumed).as("CONSUMED_PRE_KEY binding").isTrue();

        // Publish and decrypt — first time succeeds.
        lib.persist(encryptRequest);
        lib.persist(result.body());
        for (Record r : result.records()) lib.persist(r);
        bob.vault().orElseThrow().closeSession(alice.iid());  // ensure fresh bootstrap

        Body decryptRequest = Body.of(
                ItemRef.of(ItemRef.iid(Decrypt.KEY)),
                List.of(
                        Binding.ref(ItemRef.iid(ThematicRole.Agent.KEY), bob.iid()),
                        new Binding(
                                ItemRef.iid(ThematicRole.Theme.KEY),
                                List.of(),
                                DatumRef.of(result.body().datumId())),
                        new Binding(
                                ItemRef.iid(ThematicRole.Time.KEY),
                                List.of(),
                                Instant.now())));
        byte[] recovered = bob.handleDecrypt(Frame.of(decryptRequest, List.of()));
        assertThat(recovered).isEqualTo(plaintext);

        // Bob's vault should no longer have this OTPK — single-use destruction.
        assertThat(bob.vault().orElseThrow().oneTimePreKeyPublicKey())
                .as("OTPK was consumed and destroyed").isEmpty();

        // Replay attempt: another vault tries to decrypt the same frame.  Bob's
        // vault no longer has the OTPK private key, so bootstrap fails.
        bob.vault().orElseThrow().closeSession(alice.iid());
        assertThatThrownBy(() -> bob.handleDecrypt(Frame.of(decryptRequest, List.of())))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("OTPK");
    }
}
