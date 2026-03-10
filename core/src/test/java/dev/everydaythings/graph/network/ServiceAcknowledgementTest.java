package dev.everydaythings.graph.network;

import dev.everydaythings.graph.item.Literal;
import dev.everydaythings.graph.item.id.ItemID;
import dev.everydaythings.graph.item.relation.Relation;
import dev.everydaythings.graph.network.peer.PeerConnection;
import dev.everydaythings.graph.runtime.Host;
import dev.everydaythings.graph.runtime.Librarian;
import dev.everydaythings.graph.value.Endpoint;
import dev.everydaythings.graph.value.IpAddress;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.net.InetAddress;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests that service acknowledgement relations are created after successful deliveries.
 */
class ServiceAcknowledgementTest {

    private Librarian lib1;
    private Librarian lib2;

    @AfterEach
    void tearDown() {
        if (lib2 != null) lib2.close();
        if (lib1 != null) lib1.close();
    }

    @Test
    void successfulDeliveryCreatesAcknowledgement() throws Exception {
        // Given: two in-memory librarians connected over TLS
        lib1 = Librarian.createInMemory();
        lib2 = Librarian.createInMemory();

        lib1.startNetwork(0).get(5, TimeUnit.SECONDS);
        lib2.startNetwork(0).get(5, TimeUnit.SECONDS);

        int lib1Port = lib1.network().orElseThrow().boundAddress().getPort();
        Endpoint lib1Endpoint = Endpoint.cg(
                IpAddress.fromInetAddress(InetAddress.getLoopbackAddress()),
                lib1Port
        );

        PeerConnection conn = lib2.connect(lib1Endpoint).get(5, TimeUnit.SECONDS);

        // Wait for handshake to complete
        CgProtocol proto2 = lib2.cgProtocol().orElseThrow();
        assertThat(pollUntil(() ->
                proto2.peer(conn).map(p -> p.isIdentified()).orElse(false), 5000
        )).as("lib2 should identify lib1").isTrue();

        // Verify no acknowledgement relations exist yet (handshake doesn't create them)
        List<Relation> preAcks = lib2.library()
                .byPredicate(Host.ACKNOWLEDGES_DELIVERY.iid()).toList();
        assertThat(preAcks).isEmpty();

        // When: lib2 requests lib1's manifest (which lib1 has)
        CountDownLatch deliveryReceived = new CountDownLatch(1);
        proto2.requestItem(conn, lib1.iid(), manifest -> deliveryReceived.countDown());

        assertThat(deliveryReceived.await(5, TimeUnit.SECONDS))
                .as("lib2 should receive delivery from lib1").isTrue();

        // Allow time for acknowledgement relation to be created
        Thread.sleep(200);

        // Then: lib2 should have an acknowledges-delivery relation
        List<Relation> acks = lib2.library()
                .byPredicate(Host.ACKNOWLEDGES_DELIVERY.iid()).toList();
        assertThat(acks).isNotEmpty();
        assertThat(acks).anyMatch(r ->
                r.subject().equals(lib2.iid()) &&
                r.object() instanceof Relation.IidTarget target &&
                target.iid().equals(lib1.iid())
        );

        // The acknowledgement should be signed by lib2
        Relation ack = acks.stream()
                .filter(r -> r.subject().equals(lib2.iid()))
                .findFirst().orElseThrow();
        assertThat(ack.authorKey()).isNotNull();

        // The acknowledgement should have a request-id binding
        assertThat(ack.bindings()).containsKey(Host.REQUEST_ID.iid());
        Literal requestIdLit = (Literal) ack.bindings().get(Host.REQUEST_ID.iid());
        assertThat(requestIdLit.asInteger()).isGreaterThan(0);
    }

    @Test
    void notFoundDeliveryDoesNotCreateAcknowledgement() throws Exception {
        // Given: two connected librarians
        lib1 = Librarian.createInMemory();
        lib2 = Librarian.createInMemory();

        lib1.startNetwork(0).get(5, TimeUnit.SECONDS);
        lib2.startNetwork(0).get(5, TimeUnit.SECONDS);

        int lib1Port = lib1.network().orElseThrow().boundAddress().getPort();
        Endpoint lib1Endpoint = Endpoint.cg(
                IpAddress.fromInetAddress(InetAddress.getLoopbackAddress()),
                lib1Port
        );

        PeerConnection conn = lib2.connect(lib1Endpoint).get(5, TimeUnit.SECONDS);

        CgProtocol proto2 = lib2.cgProtocol().orElseThrow();
        assertThat(pollUntil(() ->
                proto2.peer(conn).map(p -> p.isIdentified()).orElse(false), 5000
        )).as("lib2 should identify lib1").isTrue();

        // When: lib2 requests an item that doesn't exist on lib1
        ItemID nonExistent = ItemID.random();
        CountDownLatch deliveryReceived = new CountDownLatch(1);
        proto2.requestItem(conn, nonExistent, manifest -> deliveryReceived.countDown());

        assertThat(deliveryReceived.await(5, TimeUnit.SECONDS))
                .as("lib2 should receive not-found response").isTrue();

        // Allow time for any potential acknowledgement
        Thread.sleep(200);

        // Then: no acknowledgement relation should exist
        List<Relation> acks = lib2.library()
                .byPredicate(Host.ACKNOWLEDGES_DELIVERY.iid()).toList();
        assertThat(acks).isEmpty();
    }

    private static boolean pollUntil(java.util.function.BooleanSupplier condition, long timeoutMs)
            throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            if (condition.getAsBoolean()) return true;
            Thread.sleep(50);
        }
        return condition.getAsBoolean();
    }
}
