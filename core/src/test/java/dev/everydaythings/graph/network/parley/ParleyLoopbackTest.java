package dev.everydaythings.graph.network.parley;

import dev.everydaythings.graph.encoding.Encoding;
import dev.everydaythings.graph.ref.ItemRef;
import dev.everydaythings.graph.network.transport.LoopbackTransport;
import dev.everydaythings.graph.network.tunnel.Tunnel;
import dev.everydaythings.graph.runtime.librarian.Librarian;
import dev.everydaythings.graph.value.LoopbackEndpoint;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end Parley over the in-VM loopback transport — proves the
 * full stack composes: {@link LoopbackTransport} hands paired tunnels
 * to two {@link Parley} instances backed by their own
 * {@link Librarian}s, the codec point-and-grunt completes in both
 * directions, and the resulting {@link RemoteConnection}s are live.
 *
 * <p>The test wires the layers exactly as a real bridge would: a
 * single shared transport instance, two Parleys (server + client),
 * two librarians.  Loopback is the substitute for the network; the
 * code under test (transport SPI ↔ tunnel ↔ codec ↔ parser ↔
 * librarian dispatch) is the production path.
 */
class ParleyLoopbackTest {

    private static final ItemRef CG_CBOR = ItemRef.iid(Encoding.CgCborV1.KEY);

    @Test
    @DisplayName("Parley handshake completes end-to-end through a LoopbackTransport")
    void handshakeCompletes() throws Exception {
        LoopbackTransport transport = new LoopbackTransport();

        Librarian serverLib = Librarian.inMemory();
        Librarian clientLib = Librarian.inMemory();
        Parley serverParley = new Parley(serverLib);
        Parley clientParley = new Parley(clientLib);

        // Server: install a listener that hands incoming tunnels to Parley.accept.
        // Capture the server-side RemoteConnection so the test can assert on it.
        AtomicReference<RemoteConnection> serverConnRef = new AtomicReference<>();
        CountDownLatch serverReady = new CountDownLatch(1);
        LoopbackEndpoint serverEp = LoopbackEndpoint.of("server");
        transport.listen(serverEp, tunnel ->
                serverParley.accept(tunnel, CG_CBOR, Set.of(CG_CBOR))
                        .whenComplete((conn, err) -> {
                            if (conn != null) serverConnRef.set(conn);
                            serverReady.countDown();
                        }));

        // Client: connect through the transport, hand the tunnel to Parley.connect.
        Tunnel clientTunnel = transport.connect(serverEp).get(1, TimeUnit.SECONDS);
        CompletableFuture<RemoteConnection> clientFuture =
                clientParley.connect(clientTunnel, CG_CBOR, Set.of(CG_CBOR));

        RemoteConnection clientConn = clientFuture.get(2, TimeUnit.SECONDS);
        assertThat(serverReady.await(2, TimeUnit.SECONDS))
                .as("server-side Parley.accept future completed")
                .isTrue();

        assertThat(clientConn).isNotNull();
        assertThat(clientConn.agreedCodec()).isEqualTo(CG_CBOR);
        assertThat(clientConn.isOpen()).isTrue();

        RemoteConnection serverConn = serverConnRef.get();
        assertThat(serverConn).as("server-side connection present").isNotNull();
        assertThat(serverConn.agreedCodec()).isEqualTo(CG_CBOR);
        assertThat(serverConn.isOpen()).isTrue();

        // Closing one side cascades through the underlying LoopbackTunnel.
        clientConn.close();
        assertThat(clientConn.isOpen()).isFalse();
        assertThat(serverConn.isOpen()).isFalse();
    }
}
