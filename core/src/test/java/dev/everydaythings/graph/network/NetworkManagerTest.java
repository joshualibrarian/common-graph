package dev.everydaythings.graph.network;

import dev.everydaythings.graph.network.message.Delivery;
import dev.everydaythings.graph.network.message.ProtocolMessage;
import dev.everydaythings.graph.network.message.Request;
import dev.everydaythings.graph.network.peer.PeerConnection;
import dev.everydaythings.graph.item.id.ItemID;
import dev.everydaythings.graph.value.Endpoint;
import dev.everydaythings.graph.value.IpAddress;
import dev.everydaythings.graph.vault.InMemoryVault;
import io.netty.handler.ssl.SslContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.net.InetAddress;
import java.security.cert.X509Certificate;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for basic network plumbing.
 */
class NetworkManagerTest {

    private NetworkManager server;
    private NetworkManager client;

    @AfterEach
    void tearDown() {
        if (client != null) client.close();
        if (server != null) server.close();
    }

    @Test
    void serverStartsAndBinds() throws Exception {
        // Given
        server = new NetworkManager(0, new TestDispatcher());

        // When
        server.start().get(5, TimeUnit.SECONDS);

        // Then
        assertThat(server.isRunning()).isTrue();
        assertThat(server.boundAddress()).isNotNull();
        assertThat(server.boundAddress().getPort()).isGreaterThan(0);
    }

    @Test
    void clientConnectsToServer() throws Exception {
        // Given
        CountDownLatch serverConnected = new CountDownLatch(1);

        server = new NetworkManager(0, new TestDispatcher() {
            @Override
            public void onPeerConnected(PeerConnection connection, X509Certificate peerCert) {
                serverConnected.countDown();
            }
        });
        server.start().get(5, TimeUnit.SECONDS);

        client = new NetworkManager(0, new TestDispatcher());
        client.start().get(5, TimeUnit.SECONDS);

        // When - connect client to server
        Endpoint serverEndpoint = Endpoint.cg(
                IpAddress.fromInetAddress(InetAddress.getLoopbackAddress()),
                server.boundAddress().getPort()
        );
        PeerConnection conn = client.connect(serverEndpoint).get(5, TimeUnit.SECONDS);

        // Then
        assertThat(serverConnected.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(conn).isNotNull();
        assertThat(conn.isOpen()).isTrue();
    }

    @Test
    void protocolMessagesAreSentAndReceived() throws Exception {
        // Given
        AtomicReference<ProtocolMessage> receivedMessage = new AtomicReference<>();
        CountDownLatch messageReceived = new CountDownLatch(1);

        server = new NetworkManager(0, new TestDispatcher() {
            @Override
            public void onMessage(PeerConnection connection, ProtocolMessage message) {
                receivedMessage.set(message);
                messageReceived.countDown();
            }
        });
        server.start().get(5, TimeUnit.SECONDS);

        client = new NetworkManager(0, new TestDispatcher());
        client.start().get(5, TimeUnit.SECONDS);

        Endpoint serverEndpoint = Endpoint.cg(
                IpAddress.fromInetAddress(InetAddress.getLoopbackAddress()),
                server.boundAddress().getPort()
        );
        PeerConnection conn = client.connect(serverEndpoint).get(5, TimeUnit.SECONDS);

        // When - send a Request message
        ItemID testIid = ItemID.random();
        Request request = Request.item(42, testIid);
        conn.send(request).get(5, TimeUnit.SECONDS);

        // Then
        assertThat(messageReceived.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(receivedMessage.get()).isInstanceOf(Request.class);
        Request received = (Request) receivedMessage.get();
        assertThat(received.requestId()).isEqualTo(42);
    }

    @Test
    void serverCanReplyWithDelivery() throws Exception {
        // Given
        AtomicReference<ProtocolMessage> receivedReply = new AtomicReference<>();
        CountDownLatch replyReceived = new CountDownLatch(1);
        ItemID testIid = ItemID.random();

        server = new NetworkManager(0, new TestDispatcher() {
            @Override
            public void onMessage(PeerConnection connection, ProtocolMessage message) {
                // Reply with not-found delivery
                if (message instanceof Request request) {
                    Delivery reply = new Delivery(request.requestId(),
                            List.of(new Delivery.Payload.NotFound(testIid)));
                    connection.send(reply);
                }
            }
        });
        server.start().get(5, TimeUnit.SECONDS);

        client = new NetworkManager(0, new TestDispatcher() {
            @Override
            public void onMessage(PeerConnection connection, ProtocolMessage message) {
                receivedReply.set(message);
                replyReceived.countDown();
            }
        });
        client.start().get(5, TimeUnit.SECONDS);

        Endpoint serverEndpoint = Endpoint.cg(
                IpAddress.fromInetAddress(InetAddress.getLoopbackAddress()),
                server.boundAddress().getPort()
        );
        PeerConnection conn = client.connect(serverEndpoint).get(5, TimeUnit.SECONDS);

        // When
        conn.send(Request.item(99, testIid)).get(5, TimeUnit.SECONDS);

        // Then
        assertThat(replyReceived.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(receivedReply.get()).isInstanceOf(Delivery.class);
        Delivery delivery = (Delivery) receivedReply.get();
        assertThat(delivery.requestId()).isEqualTo(99);
    }

    @Test
    void tlsConnectionSendsAndReceivesMessages() throws Exception {
        // Given: two NetworkManagers with TLS enabled via InMemoryVault
        InMemoryVault vault1 = InMemoryVault.create();
        InMemoryVault vault2 = InMemoryVault.create();

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
        }, vault1.serverSslContext(), vault1.clientSslContext());
        server.start().get(5, TimeUnit.SECONDS);
        assertThat(server.isTlsEnabled()).isTrue();

        client = new NetworkManager(0, new TestDispatcher(),
                vault2.serverSslContext(), vault2.clientSslContext());
        client.start().get(5, TimeUnit.SECONDS);
        assertThat(client.isTlsEnabled()).isTrue();

        // When: connect over TLS and send a message
        Endpoint serverEndpoint = Endpoint.cg(
                IpAddress.fromInetAddress(InetAddress.getLoopbackAddress()),
                server.boundAddress().getPort()
        );
        PeerConnection conn = client.connect(serverEndpoint).get(5, TimeUnit.SECONDS);
        assertThat(serverConnected.await(5, TimeUnit.SECONDS)).isTrue();

        ItemID testIid = ItemID.random();
        Request request = Request.item(1, testIid);
        conn.send(request).get(5, TimeUnit.SECONDS);

        // Then: message arrives intact over TLS
        assertThat(messageReceived.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(receivedMessage.get()).isInstanceOf(Request.class);
    }

    /**
     * Test dispatcher that does nothing by default.
     */
    private static class TestDispatcher implements NetworkManager.MessageDispatcher {
        @Override
        public void onPeerConnected(PeerConnection connection, X509Certificate peerCert) {}

        @Override
        public void onMessage(PeerConnection connection, ProtocolMessage message) {}

        @Override
        public void onPeerDisconnected(PeerConnection connection) {}
    }
}
