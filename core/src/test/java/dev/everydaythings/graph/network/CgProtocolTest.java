package dev.everydaythings.graph.network;

import dev.everydaythings.graph.item.Literal;
import dev.everydaythings.graph.item.component.BindingTarget;
import dev.everydaythings.graph.item.component.FrameBody;
import dev.everydaythings.graph.network.peer.PeerConnection;
import dev.everydaythings.graph.network.RoutingVocabulary;
import dev.everydaythings.graph.runtime.Librarian;
import dev.everydaythings.graph.value.Endpoint;
import dev.everydaythings.graph.value.IpAddress;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.net.InetAddress;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test for CgProtocol handshake over TLS.
 *
 * <p>Exercises the full path: {@code Librarian.startNetwork()} sets up TLS
 * using the vault's SSLContext, and {@code Librarian.connect()} initiates an
 * outbound TLS connection. Both sides complete the CG handshake and create
 * {@code peers-with} and {@code reachable-at} relations.
 */
@Disabled("Slow integration test — TLS handshake + full librarian bootstrap")
class CgProtocolTest {

    private Librarian lib1;
    private Librarian lib2;

    @AfterEach
    void tearDown() {
        if (lib2 != null) lib2.close();
        if (lib1 != null) lib1.close();
    }

    @Test
    void handshakeCreatesPeerRelationsOverTls() throws Exception {
        // Given: two in-memory librarians with TLS-enabled networking
        lib1 = Librarian.createInMemory();
        lib2 = Librarian.createInMemory();

        lib1.startNetwork(0).get(5, TimeUnit.SECONDS);
        lib2.startNetwork(0).get(5, TimeUnit.SECONDS);

        int lib1Port = lib1.network().orElseThrow().boundAddress().getPort();

        // Verify TLS is enabled
        assertThat(lib1.network().orElseThrow().isTlsEnabled()).isTrue();
        assertThat(lib2.network().orElseThrow().isTlsEnabled()).isTrue();

        // When: lib2 connects to lib1 over TLS
        Endpoint lib1Endpoint = Endpoint.cg(
                IpAddress.fromInetAddress(InetAddress.getLoopbackAddress()),
                lib1Port
        );
        PeerConnection conn = lib2.connect(lib1Endpoint).get(5, TimeUnit.SECONDS);
        assertThat(conn.isOpen()).isTrue();

        // Wait for handshake to complete on both sides
        CgProtocol proto1 = lib1.cgProtocol().orElseThrow();
        CgProtocol proto2 = lib2.cgProtocol().orElseThrow();

        assertThat(pollUntil(() ->
                proto2.peer(conn).map(p -> p.isIdentified()).orElse(false), 5000
        )).as("lib2 should identify lib1").isTrue();

        assertThat(pollUntil(() -> {
            for (var peer : proto1.peers()) {
                if (peer.isIdentified()) return true;
            }
            return false;
        }, 5000)).as("lib1 should identify lib2").isTrue();

        // Then: lib1 should have peers-with frame pointing at lib2
        List<FrameBody> lib1PeersWith = lib1.library().byPredicate(RoutingVocabulary.PeersWith.SEED.iid()).toList();
        assertThat(lib1PeersWith).isNotEmpty();
        assertThat(lib1PeersWith).anyMatch(r ->
                r.theme().equals(lib1.iid()) &&
                r.binding(dev.everydaythings.graph.language.ThematicRole.Target.SEED.iid()) instanceof BindingTarget.IidTarget target &&
                target.iid().equals(lib2.iid())
        );

        // lib1 should have reachable-at frame for lib2
        List<FrameBody> lib1Reachable = lib1.library().byPredicate(RoutingVocabulary.ReachableAt.SEED.iid())
                .filter(r -> r.theme().equals(lib2.iid()))
                .toList();
        assertThat(lib1Reachable).isNotEmpty();
        assertThat(lib1Reachable).anyMatch(r ->
                r.binding(dev.everydaythings.graph.language.ThematicRole.Target.SEED.iid()) instanceof Literal);

        // Then: lib2 should have peers-with frame pointing at lib1
        List<FrameBody> lib2PeersWith = lib2.library().byPredicate(RoutingVocabulary.PeersWith.SEED.iid()).toList();
        assertThat(lib2PeersWith).isNotEmpty();
        assertThat(lib2PeersWith).anyMatch(r ->
                r.theme().equals(lib2.iid()) &&
                r.binding(dev.everydaythings.graph.language.ThematicRole.Target.SEED.iid()) instanceof BindingTarget.IidTarget target &&
                target.iid().equals(lib1.iid())
        );

        // lib2 should have reachable-at frame for lib1
        List<FrameBody> lib2Reachable = lib2.library().byPredicate(RoutingVocabulary.ReachableAt.SEED.iid())
                .filter(r -> r.theme().equals(lib1.iid()))
                .toList();
        assertThat(lib2Reachable).isNotEmpty();
        assertThat(lib2Reachable).anyMatch(r ->
                r.binding(dev.everydaythings.graph.language.ThematicRole.Target.SEED.iid()) instanceof Literal);

        // Verify the reachable-at endpoint on lib2 points to lib1's address
        FrameBody lib2ReachableBody = lib2Reachable.stream()
                .filter(r -> r.binding(dev.everydaythings.graph.language.ThematicRole.Target.SEED.iid()) instanceof Literal)
                .findFirst().orElseThrow();
        Literal endpointLit = (Literal) lib2ReachableBody.binding(
                dev.everydaythings.graph.language.ThematicRole.Target.SEED.iid());
        Endpoint decoded = endpointLit.as(Endpoint.class);
        assertThat(decoded.port()).isEqualTo(lib1Port);
    }

    /**
     * Poll a condition until it returns true or timeout expires.
     */
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
