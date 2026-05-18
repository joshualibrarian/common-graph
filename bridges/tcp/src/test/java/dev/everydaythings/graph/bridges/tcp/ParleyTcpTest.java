package dev.everydaythings.graph.bridges.tcp;

import dev.everydaythings.graph.encoding.Encoding;
import dev.everydaythings.graph.id.ItemRef;
import dev.everydaythings.graph.bridges.parley.Parley;
import dev.everydaythings.graph.bridges.parley.RemoteConnection;
import dev.everydaythings.graph.network.transport.Transport;
import dev.everydaythings.graph.network.tunnel.Tunnel;
import dev.everydaythings.graph.runtime.librarian.Librarian;
import dev.everydaythings.graph.value.TcpEndpoint;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end Parley over TCP — proves the full network stack composes
 * exactly as a deployed app would wire it: a real Netty-backed transport
 * with an OS-level socket, two librarians, two Parleys, codec
 * point-and-grunt across the wire, a live {@link RemoteConnection} on
 * each side.
 *
 * <p>Mirrors {@code ParleyLoopbackTest} so any difference between
 * in-VM and real-network behavior is immediately visible.
 */
class ParleyTcpTest {

    private static final ItemRef CG_CBOR = ItemRef.iid(Encoding.CgCborV1.KEY);

    private TcpTransport serverTransport;
    private TcpTransport clientTransport;

    @BeforeEach
    void setUp() {
        // Two separate transport instances — exactly how two real hosts
        // would each instantiate their own Netty stack.
        serverTransport = new TcpTransport();
        clientTransport = new TcpTransport();
    }

    @AfterEach
    void tearDown() {
        serverTransport.close();
        clientTransport.close();
    }

    @Test
    @DisplayName("Parley handshake completes end-to-end through a real TCP socket")
    void handshakeCompletes() throws Exception {
        Librarian serverLib = Librarian.inMemory();
        Librarian clientLib = Librarian.inMemory();
        Parley serverParley = new Parley(serverLib);
        Parley clientParley = new Parley(clientLib);

        AtomicReference<RemoteConnection> serverConnRef = new AtomicReference<>();
        CountDownLatch serverReady = new CountDownLatch(1);

        Transport.Listener listener = serverTransport.listen(
                TcpEndpoint.of("127.0.0.1", 0),
                tunnel -> serverParley.accept(tunnel, CG_CBOR, Set.of(CG_CBOR))
                        .whenComplete((conn, err) -> {
                            if (conn != null) serverConnRef.set(conn);
                            serverReady.countDown();
                        }));

        TcpEndpoint serverAddr = (TcpEndpoint) listener.actualEndpoint();

        Tunnel clientTunnel = clientTransport.connect(serverAddr).get(2, TimeUnit.SECONDS);
        CompletableFuture<RemoteConnection> clientFuture =
                clientParley.connect(clientTunnel, CG_CBOR, Set.of(CG_CBOR));

        RemoteConnection clientConn = clientFuture.get(5, TimeUnit.SECONDS);
        assertThat(serverReady.await(5, TimeUnit.SECONDS))
                .as("server-side Parley.accept future completed")
                .isTrue();

        assertThat(clientConn.agreedCodec()).isEqualTo(CG_CBOR);
        assertThat(clientConn.isOpen()).isTrue();

        RemoteConnection serverConn = serverConnRef.get();
        assertThat(serverConn).as("server-side connection present").isNotNull();
        assertThat(serverConn.agreedCodec()).isEqualTo(CG_CBOR);
        assertThat(serverConn.isOpen()).isTrue();

        clientConn.close();
        // Give Netty a beat to propagate the FIN.
        for (int i = 0; i < 100 && serverConn.isOpen(); i++) Thread.sleep(10);
        assertThat(serverConn.isOpen()).isFalse();

        listener.close();
    }
}
