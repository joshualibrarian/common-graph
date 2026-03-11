package dev.everydaythings.graph.crypt;

import dev.everydaythings.graph.trust.EncryptionPublicKey;
import dev.everydaythings.graph.vault.InMemoryVault;
import dev.everydaythings.graph.vault.Vault;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for Sender Key group encryption: key creation, distribution,
 * encrypt/decrypt, key rotation, out-of-order handling, and multi-member
 * scenarios.
 */
class SenderKeyTest {

    private static Vault createVault() {
        InMemoryVault vault = InMemoryVault.create();
        vault.generateEncryptionKey();
        return vault;
    }

    private static EncryptionPublicKey encryptionKey(Vault vault) {
        return EncryptionPublicKey.builder()
                .jcaPublicKey(vault.publicKey(Vault.ENCRYPTION_KEY_ALIAS).orElseThrow())
                .build();
    }

    // ==================================================================================
    // Basic Sender Key Operations
    // ==================================================================================

    @Nested
    class BasicOperations {

        @Test
        void createSenderKey() {
            SenderKey.SenderState state = SenderKey.createSenderKey();

            assertThat(state.keyId()).hasSize(16);
            assertThat(state.iteration()).isEqualTo(0);
        }

        @Test
        void distributeAndReceive() {
            SenderKey.SenderState sender = SenderKey.createSenderKey();
            SenderKey.DistributionMessage dist = SenderKey.distribute(sender);

            assertThat(dist.keyId()).isEqualTo(sender.keyId());
            assertThat(dist.iteration()).isEqualTo(0);

            SenderKey.ReceiverState receiver = SenderKey.receive(dist);
            assertThat(receiver.keyId()).isEqualTo(sender.keyId());
            assertThat(receiver.iteration()).isEqualTo(0);
        }

        @Test
        void singleMessageRoundTrip() {
            SenderKey.SenderState sender = SenderKey.createSenderKey();
            SenderKey.ReceiverState receiver = SenderKey.receive(SenderKey.distribute(sender));

            byte[] ad = "group-123".getBytes();
            SenderKey.GroupMessage msg = SenderKey.encrypt(sender, "hello group".getBytes(), ad);

            byte[] plaintext = SenderKey.decrypt(receiver, msg, ad);
            assertThat(new String(plaintext)).isEqualTo("hello group");
        }

        @Test
        void multipleMessages() {
            SenderKey.SenderState sender = SenderKey.createSenderKey();
            SenderKey.ReceiverState receiver = SenderKey.receive(SenderKey.distribute(sender));
            byte[] ad = "group".getBytes();

            for (int i = 0; i < 20; i++) {
                byte[] data = ("msg-" + i).getBytes();
                SenderKey.GroupMessage msg = SenderKey.encrypt(sender, data, ad);
                assertThat(SenderKey.decrypt(receiver, msg, ad)).isEqualTo(data);
            }

            assertThat(sender.iteration()).isEqualTo(20);
            assertThat(receiver.iteration()).isEqualTo(20);
        }

        @Test
        void differentCiphertextForSamePlaintext() {
            SenderKey.SenderState sender = SenderKey.createSenderKey();
            byte[] ad = "group".getBytes();

            SenderKey.GroupMessage m1 = SenderKey.encrypt(sender, "same".getBytes(), ad);
            SenderKey.GroupMessage m2 = SenderKey.encrypt(sender, "same".getBytes(), ad);

            // Different message keys + different nonces → different ciphertext
            assertThat(m1.ciphertext()).isNotEqualTo(m2.ciphertext());
            assertThat(m1.iteration()).isNotEqualTo(m2.iteration());
        }

        @Test
        void emptyPlaintext() {
            SenderKey.SenderState sender = SenderKey.createSenderKey();
            SenderKey.ReceiverState receiver = SenderKey.receive(SenderKey.distribute(sender));
            byte[] ad = "group".getBytes();

            SenderKey.GroupMessage msg = SenderKey.encrypt(sender, new byte[0], ad);
            assertThat(SenderKey.decrypt(receiver, msg, ad)).isEmpty();
        }

        @Test
        void nullAd() {
            SenderKey.SenderState sender = SenderKey.createSenderKey();
            SenderKey.ReceiverState receiver = SenderKey.receive(SenderKey.distribute(sender));

            SenderKey.GroupMessage msg = SenderKey.encrypt(sender, "hello".getBytes(), null);
            assertThat(new String(SenderKey.decrypt(receiver, msg, null))).isEqualTo("hello");
        }
    }

    // ==================================================================================
    // Security Properties
    // ==================================================================================

    @Nested
    class Security {

        @Test
        void wrongAdFailsDecrypt() {
            SenderKey.SenderState sender = SenderKey.createSenderKey();
            SenderKey.ReceiverState receiver = SenderKey.receive(SenderKey.distribute(sender));

            SenderKey.GroupMessage msg = SenderKey.encrypt(sender, "hello".getBytes(), "correct".getBytes());

            assertThatThrownBy(() -> SenderKey.decrypt(receiver, msg, "wrong".getBytes()))
                    .isInstanceOf(SecurityException.class);
        }

        @Test
        void tamperedCiphertextFails() {
            SenderKey.SenderState sender = SenderKey.createSenderKey();
            SenderKey.ReceiverState receiver = SenderKey.receive(SenderKey.distribute(sender));

            SenderKey.GroupMessage msg = SenderKey.encrypt(sender, "hello".getBytes(), null);
            byte[] tampered = msg.ciphertext().clone();
            tampered[tampered.length - 1] ^= 0x01;

            SenderKey.GroupMessage bad = new SenderKey.GroupMessage(msg.keyId(), msg.iteration(), tampered);
            assertThatThrownBy(() -> SenderKey.decrypt(receiver, bad, null))
                    .isInstanceOf(SecurityException.class);
        }

        @Test
        void wrongKeyIdFails() {
            SenderKey.SenderState sender = SenderKey.createSenderKey();
            SenderKey.ReceiverState receiver = SenderKey.receive(SenderKey.distribute(sender));

            SenderKey.GroupMessage msg = SenderKey.encrypt(sender, "hello".getBytes(), null);
            SenderKey.GroupMessage wrong = new SenderKey.GroupMessage(
                    new byte[16], msg.iteration(), msg.ciphertext());

            assertThatThrownBy(() -> SenderKey.decrypt(receiver, wrong, null))
                    .isInstanceOf(SecurityException.class)
                    .hasMessageContaining("Key ID mismatch");
        }

        @Test
        void pastMessageCannotBeDecrypted() {
            SenderKey.SenderState sender = SenderKey.createSenderKey();
            SenderKey.ReceiverState receiver = SenderKey.receive(SenderKey.distribute(sender));

            // Send and receive two messages
            SenderKey.GroupMessage m0 = SenderKey.encrypt(sender, "m0".getBytes(), null);
            SenderKey.GroupMessage m1 = SenderKey.encrypt(sender, "m1".getBytes(), null);
            SenderKey.decrypt(receiver, m0, null);
            SenderKey.decrypt(receiver, m1, null);

            // Try to re-decrypt m0 — chain has moved past it
            assertThatThrownBy(() -> SenderKey.decrypt(receiver, m0, null))
                    .isInstanceOf(SecurityException.class)
                    .hasMessageContaining("Cannot decrypt past message");
        }

        @Test
        void destroyPreventsEncrypt() {
            SenderKey.SenderState sender = SenderKey.createSenderKey();
            sender.destroy();

            assertThatThrownBy(() -> SenderKey.encrypt(sender, "hello".getBytes(), null))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("destroyed");
        }

        @Test
        void destroyPreventsDecrypt() {
            SenderKey.SenderState sender = SenderKey.createSenderKey();
            SenderKey.ReceiverState receiver = SenderKey.receive(SenderKey.distribute(sender));

            SenderKey.GroupMessage msg = SenderKey.encrypt(sender, "hello".getBytes(), null);
            receiver.destroy();

            assertThatThrownBy(() -> SenderKey.decrypt(receiver, msg, null))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("destroyed");
        }
    }

    // ==================================================================================
    // Skipped Messages (out of order within sender's chain)
    // ==================================================================================

    @Nested
    class SkippedMessages {

        @Test
        void skipAndDecryptLater() {
            SenderKey.SenderState sender = SenderKey.createSenderKey();
            SenderKey.ReceiverState receiver = SenderKey.receive(SenderKey.distribute(sender));

            // Sender sends 3 messages
            SenderKey.GroupMessage m0 = SenderKey.encrypt(sender, "m0".getBytes(), null);
            SenderKey.GroupMessage m1 = SenderKey.encrypt(sender, "m1".getBytes(), null);
            SenderKey.GroupMessage m2 = SenderKey.encrypt(sender, "m2".getBytes(), null);

            // Receiver gets m2 first (skips m0, m1)
            assertThat(new String(SenderKey.decrypt(receiver, m2, null))).isEqualTo("m2");
            assertThat(receiver.iteration()).isEqualTo(3);

            // m0 and m1 cannot be recovered — Sender Keys don't cache skipped keys
            // (This is the tradeoff vs Double Ratchet)
            assertThatThrownBy(() -> SenderKey.decrypt(receiver, m0, null))
                    .isInstanceOf(SecurityException.class);
        }

        @Test
        void moderateSkipSucceeds() {
            SenderKey.SenderState sender = SenderKey.createSenderKey();
            SenderKey.ReceiverState receiver = SenderKey.receive(SenderKey.distribute(sender));

            // Send 10 messages
            List<SenderKey.GroupMessage> msgs = new ArrayList<>();
            for (int i = 0; i < 10; i++) {
                msgs.add(SenderKey.encrypt(sender, ("msg-" + i).getBytes(), null));
            }

            // Receive only the last one (skips 0-8, advances chain to 9)
            assertThat(new String(SenderKey.decrypt(receiver, msgs.getLast(), null)))
                    .isEqualTo("msg-9");
        }
    }

    // ==================================================================================
    // Multi-Member Group
    // ==================================================================================

    @Nested
    class MultiMemberGroup {

        @Test
        void threeMembers() {
            byte[] ad = "group-chat".getBytes();

            // Each member creates their sender key
            SenderKey.SenderState aliceSender = SenderKey.createSenderKey();
            SenderKey.SenderState bobSender = SenderKey.createSenderKey();
            SenderKey.SenderState carolSender = SenderKey.createSenderKey();

            // Distribute: each member receives the other two sender keys
            SenderKey.ReceiverState bobHasAlice = SenderKey.receive(SenderKey.distribute(aliceSender));
            SenderKey.ReceiverState carolHasAlice = SenderKey.receive(SenderKey.distribute(aliceSender));

            SenderKey.ReceiverState aliceHasBob = SenderKey.receive(SenderKey.distribute(bobSender));
            SenderKey.ReceiverState carolHasBob = SenderKey.receive(SenderKey.distribute(bobSender));

            SenderKey.ReceiverState aliceHasCarol = SenderKey.receive(SenderKey.distribute(carolSender));
            SenderKey.ReceiverState bobHasCarol = SenderKey.receive(SenderKey.distribute(carolSender));

            // Alice sends to group → Bob and Carol can decrypt
            SenderKey.GroupMessage aliceMsg = SenderKey.encrypt(aliceSender, "hi everyone!".getBytes(), ad);
            assertThat(new String(SenderKey.decrypt(bobHasAlice, aliceMsg, ad))).isEqualTo("hi everyone!");
            assertThat(new String(SenderKey.decrypt(carolHasAlice, aliceMsg, ad))).isEqualTo("hi everyone!");

            // Bob sends to group → Alice and Carol can decrypt
            SenderKey.GroupMessage bobMsg = SenderKey.encrypt(bobSender, "hey!".getBytes(), ad);
            assertThat(new String(SenderKey.decrypt(aliceHasBob, bobMsg, ad))).isEqualTo("hey!");
            assertThat(new String(SenderKey.decrypt(carolHasBob, bobMsg, ad))).isEqualTo("hey!");

            // Carol sends to group → Alice and Bob can decrypt
            SenderKey.GroupMessage carolMsg = SenderKey.encrypt(carolSender, "hello!".getBytes(), ad);
            assertThat(new String(SenderKey.decrypt(aliceHasCarol, carolMsg, ad))).isEqualTo("hello!");
            assertThat(new String(SenderKey.decrypt(bobHasCarol, carolMsg, ad))).isEqualTo("hello!");
        }

        @Test
        void oneReceiverCannotDecryptAnotherSendersMessages() {
            SenderKey.SenderState aliceSender = SenderKey.createSenderKey();
            SenderKey.SenderState bobSender = SenderKey.createSenderKey();

            // Carol only has Alice's key
            SenderKey.ReceiverState carolHasAlice = SenderKey.receive(SenderKey.distribute(aliceSender));

            // Bob sends a message
            SenderKey.GroupMessage bobMsg = SenderKey.encrypt(bobSender, "from bob".getBytes(), null);

            // Carol cannot decrypt Bob's message with Alice's key
            assertThatThrownBy(() -> SenderKey.decrypt(carolHasAlice, bobMsg, null))
                    .isInstanceOf(SecurityException.class);
        }
    }

    // ==================================================================================
    // Key Registry
    // ==================================================================================

    @Nested
    class KeyRegistryTest {

        @Test
        void registerAndLookup() {
            SenderKey.KeyRegistry registry = new SenderKey.KeyRegistry();

            SenderKey.SenderState alice = SenderKey.createSenderKey();
            SenderKey.SenderState bob = SenderKey.createSenderKey();

            registry.register(SenderKey.receive(SenderKey.distribute(alice)));
            registry.register(SenderKey.receive(SenderKey.distribute(bob)));

            assertThat(registry.size()).isEqualTo(2);
            assertThat(registry.lookup(alice.keyId())).isNotNull();
            assertThat(registry.lookup(bob.keyId())).isNotNull();
            assertThat(registry.lookup(new byte[16])).isNull();
        }

        @Test
        void removeKey() {
            SenderKey.KeyRegistry registry = new SenderKey.KeyRegistry();

            SenderKey.SenderState sender = SenderKey.createSenderKey();
            registry.register(SenderKey.receive(SenderKey.distribute(sender)));
            assertThat(registry.size()).isEqualTo(1);

            registry.remove(sender.keyId());
            assertThat(registry.size()).isEqualTo(0);
            assertThat(registry.lookup(sender.keyId())).isNull();
        }

        @Test
        void decryptViaRegistry() {
            SenderKey.KeyRegistry registry = new SenderKey.KeyRegistry();
            byte[] ad = "group".getBytes();

            SenderKey.SenderState alice = SenderKey.createSenderKey();
            SenderKey.SenderState bob = SenderKey.createSenderKey();

            registry.register(SenderKey.receive(SenderKey.distribute(alice)));
            registry.register(SenderKey.receive(SenderKey.distribute(bob)));

            // Alice sends
            SenderKey.GroupMessage aliceMsg = SenderKey.encrypt(alice, "from alice".getBytes(), ad);
            SenderKey.ReceiverState aliceState = registry.lookup(aliceMsg.keyId());
            assertThat(new String(SenderKey.decrypt(aliceState, aliceMsg, ad))).isEqualTo("from alice");

            // Bob sends
            SenderKey.GroupMessage bobMsg = SenderKey.encrypt(bob, "from bob".getBytes(), ad);
            SenderKey.ReceiverState bobState = registry.lookup(bobMsg.keyId());
            assertThat(new String(SenderKey.decrypt(bobState, bobMsg, ad))).isEqualTo("from bob");
        }
    }

    // ==================================================================================
    // Key Rotation (member removal)
    // ==================================================================================

    @Nested
    class KeyRotation {

        @Test
        void rotationAfterMemberRemoval() {
            byte[] ad = "group".getBytes();

            // Original: Alice, Bob, Carol
            SenderKey.SenderState aliceOld = SenderKey.createSenderKey();
            SenderKey.SenderState bobOld = SenderKey.createSenderKey();

            SenderKey.ReceiverState carolHasAliceOld = SenderKey.receive(SenderKey.distribute(aliceOld));
            SenderKey.ReceiverState carolHasBobOld = SenderKey.receive(SenderKey.distribute(bobOld));

            // Messages work
            SenderKey.GroupMessage msg = SenderKey.encrypt(aliceOld, "before removal".getBytes(), ad);
            assertThat(new String(SenderKey.decrypt(carolHasAliceOld, msg, ad)))
                    .isEqualTo("before removal");

            // Carol is removed → Alice and Bob MUST rotate their sender keys
            SenderKey.SenderState aliceNew = SenderKey.createSenderKey();
            SenderKey.SenderState bobNew = SenderKey.createSenderKey();

            // Re-distribute only to remaining members (not Carol!)
            SenderKey.ReceiverState bobHasAliceNew = SenderKey.receive(SenderKey.distribute(aliceNew));

            // Alice sends with new key → Bob can decrypt
            SenderKey.GroupMessage newMsg = SenderKey.encrypt(aliceNew, "after removal".getBytes(), ad);
            assertThat(new String(SenderKey.decrypt(bobHasAliceNew, newMsg, ad)))
                    .isEqualTo("after removal");

            // Carol's old receiver state can't decrypt new messages (different key ID)
            assertThatThrownBy(() -> SenderKey.decrypt(carolHasAliceOld, newMsg, ad))
                    .isInstanceOf(SecurityException.class);
        }

        @Test
        void newMemberReceivesExistingKeys() {
            byte[] ad = "group".getBytes();

            // Alice and Bob are existing members
            SenderKey.SenderState aliceSender = SenderKey.createSenderKey();
            SenderKey.SenderState bobSender = SenderKey.createSenderKey();

            // Alice sends some messages before Carol joins
            SenderKey.encrypt(aliceSender, "msg1".getBytes(), ad);
            SenderKey.encrypt(aliceSender, "msg2".getBytes(), ad);

            // Carol joins — receives current sender keys (at current iteration)
            SenderKey.DistributionMessage aliceDist = SenderKey.distribute(aliceSender);
            assertThat(aliceDist.iteration()).isEqualTo(2); // Alice has sent 2 messages

            SenderKey.ReceiverState carolHasAlice = SenderKey.receive(aliceDist);
            assertThat(carolHasAlice.iteration()).isEqualTo(2);

            // Carol can decrypt new messages from Alice
            SenderKey.GroupMessage newMsg = SenderKey.encrypt(aliceSender, "msg3".getBytes(), ad);
            assertThat(new String(SenderKey.decrypt(carolHasAlice, newMsg, ad))).isEqualTo("msg3");
        }
    }

    // ==================================================================================
    // Key Distribution via EnvelopeOps (encrypted wrapping)
    // ==================================================================================

    @Nested
    class EncryptedDistribution {

        @Test
        void wrapAndUnwrapDistribution() {
            // Sender creates key
            SenderKey.SenderState sender = SenderKey.createSenderKey();
            SenderKey.DistributionMessage dist = SenderKey.distribute(sender);

            // Recipient's vault
            Vault recipientVault = createVault();
            EncryptionPublicKey recipientKey = encryptionKey(recipientVault);

            // Wrap for recipient (encrypted delivery)
            byte[] wrapped = SenderKey.wrapForRecipient(dist, recipientKey);

            // Unwrap (recipient side)
            SenderKey.DistributionMessage unwrapped = SenderKey.unwrapDistribution(
                    wrapped, recipientVault, recipientKey);

            assertThat(unwrapped.keyId()).isEqualTo(dist.keyId());
            assertThat(unwrapped.chainKey()).isEqualTo(dist.chainKey());
            assertThat(unwrapped.iteration()).isEqualTo(dist.iteration());
        }

        @Test
        void wrappedKeyWorksForDecryption() {
            SenderKey.SenderState sender = SenderKey.createSenderKey();
            SenderKey.DistributionMessage dist = SenderKey.distribute(sender);

            Vault recipientVault = createVault();
            EncryptionPublicKey recipientKey = encryptionKey(recipientVault);

            // Wrap and unwrap
            byte[] wrapped = SenderKey.wrapForRecipient(dist, recipientKey);
            SenderKey.DistributionMessage unwrapped = SenderKey.unwrapDistribution(
                    wrapped, recipientVault, recipientKey);

            // Create receiver state from unwrapped distribution
            SenderKey.ReceiverState receiver = SenderKey.receive(unwrapped);

            // Can decrypt messages
            byte[] ad = "group".getBytes();
            SenderKey.GroupMessage msg = SenderKey.encrypt(sender, "encrypted delivery".getBytes(), ad);
            assertThat(new String(SenderKey.decrypt(receiver, msg, ad))).isEqualTo("encrypted delivery");
        }

        @Test
        void wrongVaultCannotUnwrap() {
            SenderKey.SenderState sender = SenderKey.createSenderKey();
            SenderKey.DistributionMessage dist = SenderKey.distribute(sender);

            Vault correctVault = createVault();
            Vault wrongVault = createVault();
            EncryptionPublicKey correctKey = encryptionKey(correctVault);
            EncryptionPublicKey wrongKey = encryptionKey(wrongVault);

            byte[] wrapped = SenderKey.wrapForRecipient(dist, correctKey);

            assertThatThrownBy(() -> SenderKey.unwrapDistribution(wrapped, wrongVault, wrongKey))
                    .isInstanceOf(SecurityException.class);
        }
    }
}
