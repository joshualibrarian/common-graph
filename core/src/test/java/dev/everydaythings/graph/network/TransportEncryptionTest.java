package dev.everydaythings.graph.network;

import dev.everydaythings.graph.item.id.ItemID;
import dev.everydaythings.graph.network.ProtocolMessage;
import dev.everydaythings.graph.network.peer.Request;
import dev.everydaythings.graph.network.peer.Delivery;
import dev.everydaythings.graph.network.peer.PeerConnection;
import dev.everydaythings.graph.network.transport.TransportCrypto;
import dev.everydaythings.graph.value.Endpoint;
import dev.everydaythings.graph.value.IpAddress;
import dev.everydaythings.graph.crypt.InMemoryVault;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.net.InetAddress;
import java.security.PublicKey;
import java.security.cert.X509Certificate;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for CG transport encryption: Noise XX handshake and AEAD session encryption.
 */
@Disabled("Slow — transport encryption integration tests")
class TransportEncryptionTest {

    // ==================================================================================
    // Unit tests: TransportCrypto handshake and session cipher
    // ==================================================================================

    @Nested
    class HandshakeAndSessionCipher {

        @Test
        void handshakeProducesSessionKeys() {
            InMemoryVault initiatorVault = createVaultWithEncryptionKey();
            InMemoryVault responderVault = createVaultWithEncryptionKey();

            var initiator = new TransportCrypto.HandshakeState(
                    TransportCrypto.Role.INITIATOR, initiatorVault);
            var responder = new TransportCrypto.HandshakeState(
                    TransportCrypto.Role.RESPONDER, responderVault);

            // Message 1: Initiator → Responder (ephemeral key)
            byte[] msg1 = initiator.advance(null);
            assertThat(msg1).isNotNull();
            assertThat(initiator.isComplete()).isFalse();

            // Message 2: Responder → Initiator (ephemeral + static + DH)
            byte[] msg2 = responder.advance(msg1);
            assertThat(msg2).isNotNull();
            assertThat(responder.isComplete()).isFalse();

            // Message 3: Initiator → Responder (static + DH)
            byte[] msg3 = initiator.advance(msg2);
            assertThat(msg3).isNotNull();
            assertThat(initiator.isComplete()).isTrue();

            // Responder processes message 3
            byte[] msg4 = responder.advance(msg3);
            assertThat(msg4).isNull(); // No outgoing message from responder
            assertThat(responder.isComplete()).isTrue();

            // Both can split into session ciphers
            var iSession = initiator.split();
            var rSession = responder.split();
            assertThat(iSession).isNotNull();
            assertThat(rSession).isNotNull();

            iSession.destroy();
            rSession.destroy();
        }

        @Test
        void handshakeAuthenticatesStaticKeys() {
            InMemoryVault initiatorVault = createVaultWithEncryptionKey();
            InMemoryVault responderVault = createVaultWithEncryptionKey();

            PublicKey initiatorPub = initiatorVault.encryptionPublicKey().orElseThrow();
            PublicKey responderPub = responderVault.encryptionPublicKey().orElseThrow();

            var initiator = new TransportCrypto.HandshakeState(
                    TransportCrypto.Role.INITIATOR, initiatorVault);
            var responder = new TransportCrypto.HandshakeState(
                    TransportCrypto.Role.RESPONDER, responderVault);

            // Run handshake
            byte[] msg1 = initiator.advance(null);
            byte[] msg2 = responder.advance(msg1);
            byte[] msg3 = initiator.advance(msg2);
            responder.advance(msg3);

            // Both should know each other's static key
            assertThat(initiator.remoteStaticKey().getEncoded())
                    .isEqualTo(responderPub.getEncoded());
            assertThat(responder.remoteStaticKey().getEncoded())
                    .isEqualTo(initiatorPub.getEncoded());
        }

        @Test
        void sessionCipherEncryptDecryptRoundTrip() {
            InMemoryVault v1 = createVaultWithEncryptionKey();
            InMemoryVault v2 = createVaultWithEncryptionKey();

            var initiator = new TransportCrypto.HandshakeState(TransportCrypto.Role.INITIATOR, v1);
            var responder = new TransportCrypto.HandshakeState(TransportCrypto.Role.RESPONDER, v2);

            byte[] msg1 = initiator.advance(null);
            byte[] msg2 = responder.advance(msg1);
            byte[] msg3 = initiator.advance(msg2);
            responder.advance(msg3);

            var iSession = initiator.split();
            var rSession = responder.split();

            // Initiator sends → Responder receives
            byte[] plaintext = "Hello from initiator".getBytes();
            byte[] encrypted = iSession.send().encrypt(plaintext);
            byte[] decrypted = rSession.recv().decrypt(encrypted);
            assertThat(decrypted).isEqualTo(plaintext);

            // Responder sends → Initiator receives
            byte[] reply = "Hello from responder".getBytes();
            byte[] encReply = rSession.send().encrypt(reply);
            byte[] decReply = iSession.recv().decrypt(encReply);
            assertThat(decReply).isEqualTo(reply);

            iSession.destroy();
            rSession.destroy();
        }

        @Test
        void sessionCipherHandlesMultipleMessages() {
            InMemoryVault v1 = createVaultWithEncryptionKey();
            InMemoryVault v2 = createVaultWithEncryptionKey();

            var initiator = new TransportCrypto.HandshakeState(TransportCrypto.Role.INITIATOR, v1);
            var responder = new TransportCrypto.HandshakeState(TransportCrypto.Role.RESPONDER, v2);

            byte[] msg1 = initiator.advance(null);
            byte[] msg2 = responder.advance(msg1);
            byte[] msg3 = initiator.advance(msg2);
            responder.advance(msg3);

            var iSession = initiator.split();
            var rSession = responder.split();

            // Send 100 messages each way with incrementing nonces
            for (int i = 0; i < 100; i++) {
                byte[] data = ("message-" + i).getBytes();
                byte[] enc = iSession.send().encrypt(data);
                byte[] dec = rSession.recv().decrypt(enc);
                assertThat(dec).isEqualTo(data);
            }

            iSession.destroy();
            rSession.destroy();
        }

        @Test
        void wrongKeyCannotDecrypt() {
            InMemoryVault v1 = createVaultWithEncryptionKey();
            InMemoryVault v2 = createVaultWithEncryptionKey();
            InMemoryVault v3 = createVaultWithEncryptionKey(); // attacker

            // Legitimate handshake
            var initiator = new TransportCrypto.HandshakeState(TransportCrypto.Role.INITIATOR, v1);
            var responder = new TransportCrypto.HandshakeState(TransportCrypto.Role.RESPONDER, v2);
            byte[] m1 = initiator.advance(null);
            byte[] m2 = responder.advance(m1);
            byte[] m3 = initiator.advance(m2);
            responder.advance(m3);
            var iSession = initiator.split();
            var rSession = responder.split();

            // Separate handshake with attacker
            var attacker = new TransportCrypto.HandshakeState(TransportCrypto.Role.INITIATOR, v3);
            var attackerResp = new TransportCrypto.HandshakeState(TransportCrypto.Role.RESPONDER, v2);
            byte[] a1 = attacker.advance(null);
            byte[] a2 = attackerResp.advance(a1);
            byte[] a3 = attacker.advance(a2);
            attackerResp.advance(a3);
            var aSession = attacker.split();

            // Initiator encrypts with legitimate session
            byte[] encrypted = iSession.send().encrypt("secret".getBytes());

            // Attacker cannot decrypt with their session keys
            assertThatThrownBy(() -> aSession.recv().decrypt(encrypted))
                    .isInstanceOf(SecurityException.class);

            iSession.destroy();
            rSession.destroy();
            aSession.destroy();
        }

        @Test
        void handshakeCannotAdvanceAfterCompletion() {
            InMemoryVault v1 = createVaultWithEncryptionKey();
            InMemoryVault v2 = createVaultWithEncryptionKey();

            var initiator = new TransportCrypto.HandshakeState(TransportCrypto.Role.INITIATOR, v1);
            var responder = new TransportCrypto.HandshakeState(TransportCrypto.Role.RESPONDER, v2);
            byte[] m1 = initiator.advance(null);
            byte[] m2 = responder.advance(m1);
            initiator.advance(m2);

            assertThat(initiator.isComplete()).isTrue();
            assertThatThrownBy(() -> initiator.advance(new byte[0]))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("already complete");
        }
    }

    // ==================================================================================
    // Integration tests: TransportEncryptionHandler + NetworkManager
    // ==================================================================================

    @Nested
    class NetworkIntegration {

        private NetworkManager server;
        private NetworkManager client;

        @AfterEach
        void tearDown() {
            if (client != null) client.close();
            if (server != null) server.close();
        }

        @Test
        void transportEncryptedConnectionSendsAndReceives() throws Exception {
            InMemoryVault serverVault = createVaultWithEncryptionKey();
            InMemoryVault clientVault = createVaultWithEncryptionKey();

            AtomicReference<ProtocolMessage> receivedMessage = new AtomicReference<>();
            CountDownLatch messageReceived = new CountDownLatch(1);
            CountDownLatch serverConnected = new CountDownLatch(1);

            server = new NetworkManager(0, new TestDispatcher() {
                @Override
                public void onPeerConnected(PeerConnection connection, X509Certificate peerCert) {
                    serverConnected.countDown();
                }

                @Override
                public void onMessage(PeerConnection connection, ProtocolMessage message) {
                    receivedMessage.set(message);
                    messageReceived.countDown();
                }
            }, serverVault);
            server.start().get(5, TimeUnit.SECONDS);
            assertThat(server.isTransportEncrypted()).isTrue();

            client = new NetworkManager(0, new TestDispatcher(), clientVault);
            client.start().get(5, TimeUnit.SECONDS);
            assertThat(client.isTransportEncrypted()).isTrue();

            // Connect and send
            Endpoint serverEndpoint = Endpoint.cg(
                    IpAddress.fromInetAddress(InetAddress.getLoopbackAddress()),
                    server.boundAddress().getPort()
            );
            PeerConnection conn = client.connect(serverEndpoint).get(5, TimeUnit.SECONDS);
            assertThat(serverConnected.await(5, TimeUnit.SECONDS)).isTrue();

            ItemID testIid = ItemID.random();
            Request request = Request.item(42, testIid);
            conn.send(request).get(5, TimeUnit.SECONDS);

            // Message arrives intact through encrypted transport
            assertThat(messageReceived.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(receivedMessage.get()).isInstanceOf(Request.class);
            Request received = (Request) receivedMessage.get();
            assertThat(received.requestId()).isEqualTo(42);
        }

        @Test
        void transportEncryptedBidirectionalMessaging() throws Exception {
            InMemoryVault serverVault = createVaultWithEncryptionKey();
            InMemoryVault clientVault = createVaultWithEncryptionKey();
            ItemID testIid = ItemID.random();

            AtomicReference<ProtocolMessage> clientReceivedReply = new AtomicReference<>();
            CountDownLatch replyReceived = new CountDownLatch(1);

            server = new NetworkManager(0, new TestDispatcher() {
                @Override
                public void onMessage(PeerConnection connection, ProtocolMessage message) {
                    if (message instanceof Request request) {
                        Delivery reply = new Delivery(request.requestId(),
                                List.of(new Delivery.Payload.NotFound(testIid)));
                        connection.send(reply);
                    }
                }
            }, serverVault);
            server.start().get(5, TimeUnit.SECONDS);

            client = new NetworkManager(0, new TestDispatcher() {
                @Override
                public void onMessage(PeerConnection connection, ProtocolMessage message) {
                    clientReceivedReply.set(message);
                    replyReceived.countDown();
                }
            }, clientVault);
            client.start().get(5, TimeUnit.SECONDS);

            Endpoint serverEndpoint = Endpoint.cg(
                    IpAddress.fromInetAddress(InetAddress.getLoopbackAddress()),
                    server.boundAddress().getPort()
            );
            PeerConnection conn = client.connect(serverEndpoint).get(5, TimeUnit.SECONDS);

            conn.send(Request.item(99, testIid)).get(5, TimeUnit.SECONDS);

            assertThat(replyReceived.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(clientReceivedReply.get()).isInstanceOf(Delivery.class);
            Delivery delivery = (Delivery) clientReceivedReply.get();
            assertThat(delivery.requestId()).isEqualTo(99);
        }

        @Test
        void multipleMessagesOverEncryptedTransport() throws Exception {
            InMemoryVault serverVault = createVaultWithEncryptionKey();
            InMemoryVault clientVault = createVaultWithEncryptionKey();
            int messageCount = 10;

            CountDownLatch allReceived = new CountDownLatch(messageCount);

            server = new NetworkManager(0, new TestDispatcher() {
                @Override
                public void onMessage(PeerConnection connection, ProtocolMessage message) {
                    allReceived.countDown();
                }
            }, serverVault);
            server.start().get(5, TimeUnit.SECONDS);

            client = new NetworkManager(0, new TestDispatcher(), clientVault);
            client.start().get(5, TimeUnit.SECONDS);

            Endpoint serverEndpoint = Endpoint.cg(
                    IpAddress.fromInetAddress(InetAddress.getLoopbackAddress()),
                    server.boundAddress().getPort()
            );
            PeerConnection conn = client.connect(serverEndpoint).get(5, TimeUnit.SECONDS);

            // Send multiple messages
            for (int i = 0; i < messageCount; i++) {
                conn.send(Request.item(i, ItemID.random())).get(5, TimeUnit.SECONDS);
            }

            assertThat(allReceived.await(10, TimeUnit.SECONDS)).isTrue();
        }
    }

    // ==================================================================================
    // Helpers
    // ==================================================================================

    private static InMemoryVault createVaultWithEncryptionKey() {
        InMemoryVault vault = InMemoryVault.create(); // creates signing key
        vault.generateEncryptionKey(); // add X25519 encryption key
        return vault;
    }

    private static class TestDispatcher implements NetworkManager.MessageDispatcher {
        @Override
        public void onPeerConnected(PeerConnection connection, X509Certificate peerCert) {}

        @Override
        public void onMessage(PeerConnection connection, ProtocolMessage message) {}

        @Override
        public void onPeerDisconnected(PeerConnection connection) {}
    }
}
