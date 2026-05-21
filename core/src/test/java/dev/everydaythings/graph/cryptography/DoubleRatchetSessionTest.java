package dev.everydaythings.graph.cryptography;

import dev.everydaythings.graph.cryptography.DoubleRatchet.EncryptedMessage;
import dev.everydaythings.graph.cryptography.vault.InMemoryVault;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * End-to-end test of Double-Ratchet sessions on the Vault: two vaults open
 * sessions to each other via X3DH and exchange a stream of encrypted
 * messages.  Tests forward secrecy of the ratchet, out-of-order handling,
 * and ciphertext indistinguishability for repeated plaintexts.
 */
@DisplayName("Vault Double-Ratchet sessions")
class DoubleRatchetSessionTest {

    @Test
    @DisplayName("Alice's first message bootstraps Bob's session asynchronously")
    void asyncFirstMessageBootstrap() {
        InMemoryVault alice = InMemoryVault.generate();
        InMemoryVault bob = InMemoryVault.generate();

        // Alice opens a session against Bob's published key material.  Bob is
        // not consulted and has no session yet.
        alice.openSessionTo(
                bob.identity(),
                bob.keyAgreementPublicKey().orElseThrow(),
                bob.signedPreKeyPublicKey().orElseThrow());
        assertThat(alice.hasSessionWith(bob.identity())).isTrue();
        assertThat(bob.hasSessionWith(alice.identity())).isFalse();

        // Alice's first encrypt carries the INITIATOR_IDENTITY_KEY and
        // INITIATOR_EPHEMERAL_KEY bootstrap bindings.
        byte[] plaintext = "hello, bob".getBytes(StandardCharsets.UTF_8);
        EncryptedMessage encrypted = alice.encryptInSession(bob.identity(), plaintext);
        assertThat(hasBinding(encrypted.recordBindings(),
                dev.everydaythings.graph.cryptography.EncryptionVocabulary.InitiatorIdentityKey.KEY)).isTrue();
        assertThat(hasBinding(encrypted.recordBindings(),
                dev.everydaythings.graph.cryptography.EncryptionVocabulary.InitiatorEphemeralKey.KEY)).isTrue();

        // Bob decrypts; the vault auto-bootstraps a responder session from the bindings.
        byte[] recovered = bob.decryptInSession(alice.identity(), encrypted);
        assertThat(recovered).isEqualTo(plaintext);
        assertThat(bob.hasSessionWith(alice.identity())).isTrue();
    }

    @Test
    @DisplayName("bootstrap bindings persist on Alice's outgoing messages until Bob replies")
    void bootstrapBindingsClearedAfterPeerHeardFrom() {
        InMemoryVault alice = InMemoryVault.generate();
        InMemoryVault bob = InMemoryVault.generate();
        alice.openSessionTo(
                bob.identity(),
                bob.keyAgreementPublicKey().orElseThrow(),
                bob.signedPreKeyPublicKey().orElseThrow());

        // Alice's first and second messages (before any reply) both carry bootstrap.
        EncryptedMessage m1 = alice.encryptInSession(bob.identity(), "first".getBytes(StandardCharsets.UTF_8));
        EncryptedMessage m2 = alice.encryptInSession(bob.identity(), "second".getBytes(StandardCharsets.UTF_8));
        assertThat(hasBinding(m1.recordBindings(),
                dev.everydaythings.graph.cryptography.EncryptionVocabulary.InitiatorIdentityKey.KEY)).isTrue();
        assertThat(hasBinding(m2.recordBindings(),
                dev.everydaythings.graph.cryptography.EncryptionVocabulary.InitiatorIdentityKey.KEY)).isTrue();

        // Bob receives, replies; Alice processes the reply.
        bob.decryptInSession(alice.identity(), m1);
        EncryptedMessage reply = bob.encryptInSession(alice.identity(), "hello back".getBytes(StandardCharsets.UTF_8));
        // Bob is the responder — his messages should never carry bootstrap bindings.
        assertThat(hasBinding(reply.recordBindings(),
                dev.everydaythings.graph.cryptography.EncryptionVocabulary.InitiatorIdentityKey.KEY)).isFalse();

        alice.decryptInSession(bob.identity(), reply);

        // Now Alice has heard from Bob.  Her subsequent message drops the bootstrap bindings.
        EncryptedMessage m3 = alice.encryptInSession(bob.identity(), "third".getBytes(StandardCharsets.UTF_8));
        assertThat(hasBinding(m3.recordBindings(),
                dev.everydaythings.graph.cryptography.EncryptionVocabulary.InitiatorIdentityKey.KEY)).isFalse();
    }

    @Test
    @DisplayName("decrypt with no session and no bootstrap bindings fails clearly")
    void noSessionAndNoBootstrap() {
        InMemoryVault alice = InMemoryVault.generate();
        InMemoryVault bob = InMemoryVault.generate();
        alice.openSessionTo(
                bob.identity(),
                bob.keyAgreementPublicKey().orElseThrow(),
                bob.signedPreKeyPublicKey().orElseThrow());

        // First message has bootstrap; bob accepts it.
        bob.decryptInSession(alice.identity(),
                alice.encryptInSession(bob.identity(), "open".getBytes(StandardCharsets.UTF_8)));
        // Bob replies, alice processes — now Alice's bootstrap bindings clear.
        EncryptedMessage reply = bob.encryptInSession(alice.identity(), "ack".getBytes(StandardCharsets.UTF_8));
        alice.decryptInSession(bob.identity(), reply);
        EncryptedMessage nonBootstrap = alice.encryptInSession(bob.identity(), "third".getBytes(StandardCharsets.UTF_8));

        // Simulate a fresh charlie who's never had a session with alice and
        // receives one of alice's post-bootstrap messages.  No bootstrap bindings
        // present in this message; auto-bootstrap should fail.
        InMemoryVault charlie = InMemoryVault.generate();
        assertThatThrownBy(() -> charlie.decryptInSession(alice.identity(), nonBootstrap))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("bootstrap bindings");
    }

    private static boolean hasBinding(List<dev.everydaythings.graph.datum.Binding> bindings, String roleKey) {
        dev.everydaythings.graph.ref.ItemRef role = dev.everydaythings.graph.ref.ItemRef.iid(roleKey);
        for (dev.everydaythings.graph.datum.Binding b : bindings) {
            if (role.equals(b.role())) return true;
        }
        return false;
    }

    @Test
    @DisplayName("bidirectional stream of messages with ratchet evolution")
    void bidirectionalStream() {
        Sessions s = openSessions();

        EncryptedMessage m1 = s.alice.encryptInSession(s.bob.identity(), "1 alice".getBytes(StandardCharsets.UTF_8));
        EncryptedMessage m2 = s.alice.encryptInSession(s.bob.identity(), "2 alice".getBytes(StandardCharsets.UTF_8));

        // Bob receives in order.
        assertThat(s.bob.decryptInSession(s.alice.identity(), m1))
                .isEqualTo("1 alice".getBytes(StandardCharsets.UTF_8));
        assertThat(s.bob.decryptInSession(s.alice.identity(), m2))
                .isEqualTo("2 alice".getBytes(StandardCharsets.UTF_8));

        // Bob can now reply.
        EncryptedMessage r1 = s.bob.encryptInSession(s.alice.identity(), "1 bob".getBytes(StandardCharsets.UTF_8));
        EncryptedMessage r2 = s.bob.encryptInSession(s.alice.identity(), "2 bob".getBytes(StandardCharsets.UTF_8));

        assertThat(s.alice.decryptInSession(s.bob.identity(), r1))
                .isEqualTo("1 bob".getBytes(StandardCharsets.UTF_8));
        assertThat(s.alice.decryptInSession(s.bob.identity(), r2))
                .isEqualTo("2 bob".getBytes(StandardCharsets.UTF_8));

        // And another round from Alice — the DH ratchet has now ticked.
        EncryptedMessage m3 = s.alice.encryptInSession(s.bob.identity(), "3 alice".getBytes(StandardCharsets.UTF_8));
        assertThat(s.bob.decryptInSession(s.alice.identity(), m3))
                .isEqualTo("3 alice".getBytes(StandardCharsets.UTF_8));
    }

    @Test
    @DisplayName("identical plaintexts produce different ciphertexts (ratchet forward secrecy)")
    void ciphertextsDiffer() {
        Sessions s = openSessions();
        byte[] plaintext = "same words".getBytes(StandardCharsets.UTF_8);

        List<EncryptedMessage> ciphertexts = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            ciphertexts.add(s.alice.encryptInSession(s.bob.identity(), plaintext));
        }
        for (EncryptedMessage ct : ciphertexts) {
            assertThat(s.bob.decryptInSession(s.alice.identity(), ct)).isEqualTo(plaintext);
        }
        // All ciphertexts mutually distinct.
        for (int i = 0; i < ciphertexts.size(); i++) {
            for (int j = i + 1; j < ciphertexts.size(); j++) {
                assertThat(ciphertexts.get(i).ciphertext())
                        .isNotEqualTo(ciphertexts.get(j).ciphertext());
            }
        }
    }

    @Test
    @DisplayName("out-of-order delivery within a single chain works via the skipped-keys cache")
    void outOfOrder() {
        Sessions s = openSessions();
        EncryptedMessage m1 = s.alice.encryptInSession(s.bob.identity(), "first".getBytes(StandardCharsets.UTF_8));
        EncryptedMessage m2 = s.alice.encryptInSession(s.bob.identity(), "second".getBytes(StandardCharsets.UTF_8));
        EncryptedMessage m3 = s.alice.encryptInSession(s.bob.identity(), "third".getBytes(StandardCharsets.UTF_8));

        // Bob receives m1, then m3 (out of order — m2 is delayed), then m2.
        assertThat(s.bob.decryptInSession(s.alice.identity(), m1))
                .isEqualTo("first".getBytes(StandardCharsets.UTF_8));
        assertThat(s.bob.decryptInSession(s.alice.identity(), m3))
                .isEqualTo("third".getBytes(StandardCharsets.UTF_8));
        // The delayed m2 still decrypts: skipped-keys cache holds its MK.
        assertThat(s.bob.decryptInSession(s.alice.identity(), m2))
                .isEqualTo("second".getBytes(StandardCharsets.UTF_8));
    }

    @Test
    @DisplayName("tampering with the ciphertext fails authentication")
    void tampering() {
        Sessions s = openSessions();
        byte[] plaintext = "valuable secret".getBytes(StandardCharsets.UTF_8);
        EncryptedMessage encrypted = s.alice.encryptInSession(s.bob.identity(), plaintext);
        byte[] tampered = encrypted.ciphertext().clone();
        tampered[tampered.length - 1] ^= 0x01;
        EncryptedMessage attacked = new EncryptedMessage(tampered, encrypted.recordBindings());
        assertThatThrownBy(() -> s.bob.decryptInSession(s.alice.identity(), attacked))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    @DisplayName("tampering with a record binding fails authentication via canonical-hash AD")
    void bindingTampering() {
        Sessions s = openSessions();
        byte[] plaintext = "binding-tamper-test".getBytes(StandardCharsets.UTF_8);
        EncryptedMessage encrypted = s.alice.encryptInSession(s.bob.identity(), plaintext);

        // Mutate one binding's target value.  Replace the message-number with a
        // wrong value to simulate a metadata-replay or tamper attempt.
        List<dev.everydaythings.graph.datum.Binding> mutated = new ArrayList<>(encrypted.recordBindings());
        for (int i = 0; i < mutated.size(); i++) {
            dev.everydaythings.graph.datum.Binding b = mutated.get(i);
            Object role = b.role();
            if (role instanceof dev.everydaythings.graph.ref.ItemRef ir
                    && ir.equals(dev.everydaythings.graph.ref.ItemRef.iid(
                            dev.everydaythings.graph.cryptography.EncryptionVocabulary.MessageNumber.KEY))) {
                mutated.set(i, new dev.everydaythings.graph.datum.Binding(ir, 9999L));
                break;
            }
        }
        EncryptedMessage attacked = new EncryptedMessage(encrypted.ciphertext(), mutated);
        assertThatThrownBy(() -> s.bob.decryptInSession(s.alice.identity(), attacked))
                .isInstanceOf(RuntimeException.class);
    }

    // ==================================================================================
    // Helpers
    // ==================================================================================

    private record Sessions(InMemoryVault alice, InMemoryVault bob) {}

    /**
     * Set up both sides ready to chat: Alice opens to Bob, sends one bootstrap
     * message that auto-establishes Bob's session, Bob replies once so Alice
     * can drop her bootstrap bindings.  Helpers use this for tests that want
     * to exercise post-bootstrap behavior.
     */
    private static Sessions openSessions() {
        InMemoryVault alice = InMemoryVault.generate();
        InMemoryVault bob = InMemoryVault.generate();
        alice.openSessionTo(
                bob.identity(),
                bob.keyAgreementPublicKey().orElseThrow(),
                bob.signedPreKeyPublicKey().orElseThrow());
        // Alice → Bob bootstraps Bob's responder session.
        EncryptedMessage hello = alice.encryptInSession(bob.identity(), "hello".getBytes(StandardCharsets.UTF_8));
        bob.decryptInSession(alice.identity(), hello);
        // Bob → Alice closes the loop so Alice's bootstrap bindings clear.
        EncryptedMessage ack = bob.encryptInSession(alice.identity(), "hi".getBytes(StandardCharsets.UTF_8));
        alice.decryptInSession(bob.identity(), ack);
        return new Sessions(alice, bob);
    }
}
