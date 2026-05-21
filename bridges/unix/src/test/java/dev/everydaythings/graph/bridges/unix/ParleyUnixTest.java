package dev.everydaythings.graph.bridges.unix;

import dev.everydaythings.graph.encoding.Encoding;
import dev.everydaythings.graph.ref.ItemRef;
import dev.everydaythings.graph.network.parley.Parley;
import dev.everydaythings.graph.network.parley.RemoteConnection;
import dev.everydaythings.graph.network.transport.Transport;
import dev.everydaythings.graph.network.tunnel.Tunnel;
import dev.everydaythings.graph.runtime.librarian.Librarian;
import dev.everydaythings.graph.value.UnixEndpoint;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end Parley over Unix domain socket — proves the full local-IPC
 * stack composes exactly as a deployed librarian + session would wire it:
 * Parley.listen on the server (the librarian's parley.sock), Parley.connect
 * from the client (a Session connecting to it), codec point-and-grunt
 * across the socket, live RemoteConnection on each side.
 *
 * <p>Mirrors {@code ParleyTcpTest} in {@code :bridges:tcp} so any
 * difference between TCP and Unix-socket behavior is immediately visible.
 */
class ParleyUnixTest {

    private static final ItemRef CG_CBOR = ItemRef.iid(Encoding.CgCborV1.KEY);

    private UnixSocketTransport serverTransport;
    private UnixSocketTransport clientTransport;

    @BeforeEach
    void setUp() {
        serverTransport = new UnixSocketTransport();
        clientTransport = new UnixSocketTransport();
    }

    @AfterEach
    void tearDown() {
        serverTransport.close();
        clientTransport.close();
    }

    @Test
    @DisplayName("Parley handshake completes end-to-end through a real Unix socket")
    void handshakeCompletes(@TempDir Path tempDir) throws Exception {
        Path socketPath = tempDir.resolve("parley.sock");
        Librarian serverLib = Librarian.inMemory();
        Librarian clientLib = Librarian.inMemory();
        Parley serverParley = new Parley(serverLib);
        Parley clientParley = new Parley(clientLib);

        AtomicReference<RemoteConnection> serverConnRef = new AtomicReference<>();
        CountDownLatch serverReady = new CountDownLatch(1);

        // Use the new Parley.listen() helper — the high-level API a
        // librarian's parley-startup branch would call.  Wraps
        // transport.listen + accept into one call.
        Transport.Listener listener = serverTransport.listen(
                UnixEndpoint.of(socketPath.toString()),
                tunnel -> serverParley.accept(tunnel, CG_CBOR, Set.of(CG_CBOR))
                        .whenComplete((conn, err) -> {
                            if (conn != null) serverConnRef.set(conn);
                            serverReady.countDown();
                        }));

        Tunnel clientTunnel = clientTransport.connect(UnixEndpoint.of(socketPath.toString()))
                .get(2, TimeUnit.SECONDS);
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

    @Test
    @DisplayName("Parley.listen() helper composes transport.listen + accept correctly")
    void parleyListenHelper(@TempDir Path tempDir) throws Exception {
        Path socketPath = tempDir.resolve("helper.sock");
        Librarian serverLib = Librarian.inMemory();
        Librarian clientLib = Librarian.inMemory();
        Parley serverParley = new Parley(serverLib);
        Parley clientParley = new Parley(clientLib);

        // High-level API: one call binds + wires up accept on every incoming tunnel.
        Transport.Listener listener = serverParley.listen(
                serverTransport,
                UnixEndpoint.of(socketPath.toString()),
                CG_CBOR, Set.of(CG_CBOR));

        Tunnel clientTunnel = clientTransport.connect(UnixEndpoint.of(socketPath.toString()))
                .get(2, TimeUnit.SECONDS);
        RemoteConnection clientConn = clientParley.connect(clientTunnel, CG_CBOR, Set.of(CG_CBOR))
                .get(5, TimeUnit.SECONDS);

        assertThat(clientConn.agreedCodec()).isEqualTo(CG_CBOR);
        assertThat(clientConn.isOpen()).isTrue();

        clientConn.close();
        listener.close();
    }
}
