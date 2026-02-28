package dev.everydaythings.graph.network;

import dev.everydaythings.graph.item.Manifest;
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
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests single-hop relay forwarding between three librarians.
 *
 * <p>Topology: lib1 <---> lib2 (relay) <---> lib3
 * lib1 and lib3 are NOT directly connected.
 */
class RelayForwardingTest {

    private Librarian lib1;
    private Librarian lib2;
    private Librarian lib3;

    @AfterEach
    void tearDown() {
        if (lib3 != null) lib3.close();
        if (lib2 != null) lib2.close();
        if (lib1 != null) lib1.close();
    }

    @Test
    void relayForwardsRequestAndDelivery() throws Exception {
        // Given: three librarians, lib1 <-> lib2 <-> lib3
        setupThreeLibrarians();

        CgProtocol proto1 = lib1.cgProtocol().orElseThrow();
        CgProtocol proto2 = lib2.cgProtocol().orElseThrow();

        // Find lib1's connection to lib2
        PeerConnection conn12 = proto1.connectionFor(lib2.iid()).orElseThrow();

        // When: lib1 requests lib3's manifest through lib2
        CountDownLatch deliveryReceived = new CountDownLatch(1);
        AtomicReference<Manifest> receivedManifest = new AtomicReference<>();
        proto1.requestItemVia(conn12, lib3.iid(), lib3.iid(), manifest -> {
            receivedManifest.set(manifest);
            deliveryReceived.countDown();
        });

        // Then: lib1 should receive lib3's manifest through the relay
        assertThat(deliveryReceived.await(10, TimeUnit.SECONDS))
                .as("lib1 should receive relayed delivery from lib3").isTrue();

        assertThat(receivedManifest.get()).isNotNull();
        assertThat(receivedManifest.get().iid()).isEqualTo(lib3.iid());
    }

    @Test
    void relayToUnknownPeerDropsMessage() throws Exception {
        // Given: three librarians, lib1 <-> lib2 <-> lib3
        setupThreeLibrarians();

        CgProtocol proto1 = lib1.cgProtocol().orElseThrow();
        PeerConnection conn12 = proto1.connectionFor(lib2.iid()).orElseThrow();

        // When: lib1 requests an item from a non-existent peer through lib2
        ItemID fakePeer = ItemID.random();
        CountDownLatch deliveryReceived = new CountDownLatch(1);
        proto1.requestItemVia(conn12, fakePeer, lib3.iid(), manifest -> {
            deliveryReceived.countDown();
        });

        // Then: the request should time out (lib2 can't forward to unknown peer)
        assertThat(deliveryReceived.await(3, TimeUnit.SECONDS))
                .as("request to unknown peer should time out").isFalse();
    }

    @Test
    void relayCreatesAcknowledgement() throws Exception {
        // Given: three librarians, lib1 <-> lib2 <-> lib3
        setupThreeLibrarians();

        CgProtocol proto1 = lib1.cgProtocol().orElseThrow();
        PeerConnection conn12 = proto1.connectionFor(lib2.iid()).orElseThrow();

        // Verify no relay acknowledgements exist yet
        List<Relation> preRelays = lib2.library()
                .byPredicate(Host.ACKNOWLEDGES_RELAY.iid()).toList();
        assertThat(preRelays).isEmpty();

        // When: lib1 requests lib3's manifest through lib2
        CountDownLatch deliveryReceived = new CountDownLatch(1);
        proto1.requestItemVia(conn12, lib3.iid(), lib3.iid(), manifest ->
                deliveryReceived.countDown());

        assertThat(deliveryReceived.await(10, TimeUnit.SECONDS))
                .as("lib1 should receive relayed delivery").isTrue();

        // Allow time for relay acknowledgement to be created
        Thread.sleep(200);

        // Then: lib2 should have an acknowledges-relay relation
        List<Relation> relays = lib2.library()
                .byPredicate(Host.ACKNOWLEDGES_RELAY.iid()).toList();
        assertThat(relays).isNotEmpty();
        assertThat(relays).anyMatch(r ->
                r.subject().equals(lib2.iid()) &&
                r.object() instanceof Relation.IidTarget target &&
                target.iid().equals(lib1.iid())
        );
    }

    // =========================================================================
    // Setup Helpers
    // =========================================================================

    /**
     * Set up three librarians: lib1 <-> lib2 <-> lib3.
     * lib1 and lib3 are NOT directly connected.
     */
    private void setupThreeLibrarians() throws Exception {
        lib1 = Librarian.createInMemory();
        lib2 = Librarian.createInMemory();
        lib3 = Librarian.createInMemory();

        lib1.startNetwork(0).get(5, TimeUnit.SECONDS);
        lib2.startNetwork(0).get(5, TimeUnit.SECONDS);
        lib3.startNetwork(0).get(5, TimeUnit.SECONDS);

        // Connect lib1 -> lib2
        int lib2Port = lib2.network().orElseThrow().boundAddress().getPort();
        Endpoint lib2Endpoint = Endpoint.cg(
                IpAddress.fromInetAddress(InetAddress.getLoopbackAddress()),
                lib2Port
        );
        PeerConnection conn12 = lib1.connect(lib2Endpoint).get(5, TimeUnit.SECONDS);

        // Connect lib2 -> lib3
        int lib3Port = lib3.network().orElseThrow().boundAddress().getPort();
        Endpoint lib3Endpoint = Endpoint.cg(
                IpAddress.fromInetAddress(InetAddress.getLoopbackAddress()),
                lib3Port
        );
        PeerConnection conn23 = lib2.connect(lib3Endpoint).get(5, TimeUnit.SECONDS);

        // Wait for both handshakes to complete
        CgProtocol proto1 = lib1.cgProtocol().orElseThrow();
        CgProtocol proto2 = lib2.cgProtocol().orElseThrow();

        assertThat(pollUntil(() ->
                proto1.connectionFor(lib2.iid()).isPresent(), 5000
        )).as("lib1 should identify lib2").isTrue();

        assertThat(pollUntil(() ->
                proto2.connectionFor(lib3.iid()).isPresent(), 5000
        )).as("lib2 should identify lib3").isTrue();
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
