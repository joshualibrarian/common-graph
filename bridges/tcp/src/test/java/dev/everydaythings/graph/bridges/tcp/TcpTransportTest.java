package dev.everydaythings.graph.bridges.tcp;

import dev.everydaythings.graph.network.IpAddress;
import dev.everydaythings.graph.network.transport.Transport;
import dev.everydaythings.graph.network.tunnel.Tunnel;
import dev.everydaythings.graph.value.LoopbackEndpoint;
import dev.everydaythings.graph.value.TcpEndpoint;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link TcpTransport}.  Each test binds to {@code 127.0.0.1:0}
 * (kernel-assigned ephemeral port) so they don't collide and don't need
 * specific privileges.
 */
class TcpTransportTest {

    private TcpTransport transport;

    @BeforeEach
    void setUp() {
        transport = new TcpTransport();
    }

    @AfterEach
    void tearDown() {
        transport.close();
    }

    @Nested
    @DisplayName("Connect ↔ listen happy paths")
    class HappyPath {

        @Test
        @DisplayName("listen on :0 reports the actually-bound port; connect produces a paired tunnel; bytes round-trip both ways")
        void bytesRoundTripBothWays() throws Exception {
            AtomicReference<Tunnel> serverSide = new AtomicReference<>();
            CompletableFuture<byte[]> serverGot = new CompletableFuture<>();

            Transport.Listener listener = transport.listen(
                    TcpEndpoint.of("127.0.0.1", 0),
                    tunnel -> {
                        serverSide.set(tunnel);
                        tunnel.onReceive(serverGot::complete);
                    });

            TcpEndpoint actual = (TcpEndpoint) listener.actualEndpoint();
            assertThat(actual.port()).as("kernel assigned a real ephemeral port").isPositive();
            assertThat(actual.host().toHostString()).isEqualTo("127.0.0.1");

            Tunnel clientSide = transport.connect(actual).get(2, TimeUnit.SECONDS);
            CompletableFuture<byte[]> clientGot = new CompletableFuture<>();
            clientSide.onReceive(clientGot::complete);

            clientSide.send(new byte[]{1, 2, 3}).get(2, TimeUnit.SECONDS);
            assertThat(serverGot.get(2, TimeUnit.SECONDS)).containsExactly(1, 2, 3);

            // Wait until the accept handler has wired the server-side tunnel
            // (it does so synchronously inside initChannel, but the
            // serverSide.set happens on the boss thread).
            for (int i = 0; i < 100 && serverSide.get() == null; i++) Thread.sleep(5);
            assertThat(serverSide.get()).as("server-side tunnel registered").isNotNull();

            serverSide.get().send(new byte[]{9, 8, 7}).get(2, TimeUnit.SECONDS);
            assertThat(clientGot.get(2, TimeUnit.SECONDS)).containsExactly(9, 8, 7);

            clientSide.close();
            listener.close();
        }

        @Test
        @DisplayName("bytes sent before the receive consumer is wired are buffered and drained")
        void earlyBytesAreBuffered() throws Exception {
            // The server intentionally delays wiring its receive consumer so that
            // any bytes sent immediately after connect arrive at the channel
            // before the consumer is registered.  They must not be lost.
            CompletableFuture<byte[]> serverGot = new CompletableFuture<>();
            AtomicReference<Tunnel> serverSide = new AtomicReference<>();

            Transport.Listener listener = transport.listen(
                    TcpEndpoint.of("127.0.0.1", 0),
                    serverSide::set);
            TcpEndpoint actual = (TcpEndpoint) listener.actualEndpoint();

            Tunnel clientSide = transport.connect(actual).get(2, TimeUnit.SECONDS);
            clientSide.send(new byte[]{42, 43, 44}).get(2, TimeUnit.SECONDS);

            // Wait until the server-side tunnel exists, then wire the receiver
            // — by which point the bytes have already arrived.
            for (int i = 0; i < 200 && serverSide.get() == null; i++) Thread.sleep(5);
            assertThat(serverSide.get()).isNotNull();
            // Give the kernel + Netty pipeline a moment to deliver bytes.
            Thread.sleep(50);
            serverSide.get().onReceive(serverGot::complete);

            assertThat(serverGot.get(2, TimeUnit.SECONDS))
                    .as("bytes sent before onReceive was registered must still arrive")
                    .containsExactly(42, 43, 44);

            clientSide.close();
            listener.close();
        }
    }

    @Nested
    @DisplayName("Failure modes")
    class Failures {

        @Test
        @DisplayName("connect with non-TCP endpoint fails the future")
        void connectWrongEndpointType() {
            CompletableFuture<Tunnel> future = transport.connect(LoopbackEndpoint.of());
            assertThatThrownBy(() -> future.get(1, TimeUnit.SECONDS))
                    .isInstanceOf(ExecutionException.class)
                    .hasCauseInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("TcpEndpoint");
        }

        @Test
        @DisplayName("listen with non-TCP endpoint throws")
        void listenWrongEndpointType() {
            assertThatThrownBy(() -> transport.listen(LoopbackEndpoint.of(), t -> {}))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("TcpEndpoint");
        }

        @Test
        @DisplayName("connect to a port nothing is listening on fails the future")
        void connectToDeadPort() {
            // Bind, capture the port, immediately close — now nothing's listening
            // there.  This races with port reuse but in practice the immediately-
            // following connect almost always sees ECONNREFUSED.
            Transport.Listener listener = transport.listen(
                    TcpEndpoint.of("127.0.0.1", 0), t -> {});
            TcpEndpoint deadEndpoint = (TcpEndpoint) listener.actualEndpoint();
            listener.close();

            CompletableFuture<Tunnel> future = transport.connect(deadEndpoint);
            assertThatThrownBy(() -> future.get(2, TimeUnit.SECONDS))
                    .as("connect should fail when nothing is listening")
                    .isInstanceOf(ExecutionException.class);
        }
    }

    @Nested
    @DisplayName("Identity")
    class Identity {

        @Test
        @DisplayName("transport() returns the TCP transport-sememe IID")
        void transportSememe() {
            assertThat(transport.transport())
                    .isEqualTo(dev.everydaythings.graph.ref.ItemRef.iid(
                            dev.everydaythings.graph.network.NetworkVocabulary.Tcp.KEY));
        }

        @Test
        @DisplayName("listener.actualEndpoint() preserves bound host")
        void actualEndpointReports() {
            Transport.Listener listener = transport.listen(
                    TcpEndpoint.of(IpAddress.parse("127.0.0.1"), 0), t -> {});
            try {
                TcpEndpoint actual = (TcpEndpoint) listener.actualEndpoint();
                assertThat(actual.host().toHostString()).isEqualTo("127.0.0.1");
                assertThat(actual.port()).isPositive();
            } finally {
                listener.close();
            }
        }
    }
}
