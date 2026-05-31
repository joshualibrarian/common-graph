package dev.everydaythings.graph.network.transport;

import dev.everydaythings.graph.network.tunnel.Tunnel;
import dev.everydaythings.graph.network.LoopbackEndpoint;
import dev.everydaythings.graph.network.TcpEndpoint;
import dev.everydaythings.graph.value.IpAddress;
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
 * Unit tests for the in-VM loopback transport: addressing by name,
 * listener lifecycle, mismatched-endpoint failure modes.
 */
class LoopbackTransportTest {

    @Nested
    @DisplayName("Connect ↔ listen happy paths")
    class HappyPath {

        @Test
        @DisplayName("named endpoint: connect reaches the registered listener; bytes round-trip both ways")
        void namedConnectReachesListener() throws Exception {
            LoopbackTransport transport = new LoopbackTransport();
            LoopbackEndpoint endpoint = LoopbackEndpoint.of("server");

            AtomicReference<Tunnel> serverSide = new AtomicReference<>();
            transport.listen(endpoint, serverSide::set);

            Tunnel clientSide = transport.connect(endpoint).get(1, TimeUnit.SECONDS);

            assertThat(serverSide.get()).as("listener was handed a tunnel").isNotNull();
            assertThat(clientSide).as("caller got a tunnel back").isNotNull();
            assertThat(clientSide).as("server and client get different ends").isNotSameAs(serverSide.get());

            // Wire receivers and exchange a byte each way to prove the pair is connected.
            AtomicReference<byte[]> seenByServer = new AtomicReference<>();
            AtomicReference<byte[]> seenByClient = new AtomicReference<>();
            serverSide.get().onReceive(seenByServer::set);
            clientSide.onReceive(seenByClient::set);

            clientSide.send(new byte[]{1, 2, 3});
            serverSide.get().send(new byte[]{4, 5, 6});

            assertThat(seenByServer.get()).containsExactly(1, 2, 3);
            assertThat(seenByClient.get()).containsExactly(4, 5, 6);
        }

        @Test
        @DisplayName("unnamed endpoint also works (single anonymous slot)")
        void unnamedConnectReachesListener() throws Exception {
            LoopbackTransport transport = new LoopbackTransport();
            LoopbackEndpoint endpoint = LoopbackEndpoint.of();

            AtomicReference<Tunnel> serverSide = new AtomicReference<>();
            transport.listen(endpoint, serverSide::set);

            Tunnel clientSide = transport.connect(endpoint).get(1, TimeUnit.SECONDS);

            assertThat(serverSide.get()).isNotNull();
            assertThat(clientSide).isNotNull();
        }

        @Test
        @DisplayName("multiple named endpoints coexist on one transport")
        void multipleNamedEndpoints() throws Exception {
            LoopbackTransport transport = new LoopbackTransport();
            LoopbackEndpoint alpha = LoopbackEndpoint.of("alpha");
            LoopbackEndpoint beta = LoopbackEndpoint.of("beta");

            AtomicReference<Tunnel> alphaSide = new AtomicReference<>();
            AtomicReference<Tunnel> betaSide = new AtomicReference<>();
            transport.listen(alpha, alphaSide::set);
            transport.listen(beta, betaSide::set);

            transport.connect(alpha).get(1, TimeUnit.SECONDS);
            assertThat(alphaSide.get()).as("alpha listener fired").isNotNull();
            assertThat(betaSide.get()).as("beta listener did NOT fire").isNull();

            transport.connect(beta).get(1, TimeUnit.SECONDS);
            assertThat(betaSide.get()).as("beta listener fired on its connect").isNotNull();

            assertThat(transport.activeNames()).containsExactlyInAnyOrder("alpha", "beta");
        }
    }

    @Nested
    @DisplayName("Lifecycle")
    class Lifecycle {

        @Test
        @DisplayName("closing the Listener unregisters; subsequent connect fails")
        void closeListenerUnregisters() {
            LoopbackTransport transport = new LoopbackTransport();
            LoopbackEndpoint endpoint = LoopbackEndpoint.of("ephemeral");

            Transport.Listener listener = transport.listen(endpoint, t -> {});
            assertThat(transport.activeNames()).contains("ephemeral");

            listener.close();

            assertThat(transport.activeNames()).doesNotContain("ephemeral");
            assertThatThrownBy(() -> transport.connect(endpoint).get(1, TimeUnit.SECONDS))
                    .isInstanceOf(ExecutionException.class)
                    .hasCauseInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("No loopback listener");
        }

        @Test
        @DisplayName("listener.actualEndpoint() returns the endpoint actually bound")
        void actualEndpointReturned() {
            LoopbackTransport transport = new LoopbackTransport();
            LoopbackEndpoint endpoint = LoopbackEndpoint.of("foo");

            Transport.Listener listener = transport.listen(endpoint, t -> {});

            assertThat(listener.actualEndpoint()).isSameAs(endpoint);
        }

        @Test
        @DisplayName("Listener.close() is idempotent")
        void doubleCloseIsHarmless() {
            LoopbackTransport transport = new LoopbackTransport();
            Transport.Listener listener = transport.listen(LoopbackEndpoint.of("x"), t -> {});
            listener.close();
            listener.close();   // must not throw
        }
    }

    @Nested
    @DisplayName("Failure modes")
    class Failures {

        @Test
        @DisplayName("connect with no registered listener fails the future")
        void connectWithoutListenerFails() {
            LoopbackTransport transport = new LoopbackTransport();
            CompletableFuture<Tunnel> future =
                    transport.connect(LoopbackEndpoint.of("ghost"));
            assertThatThrownBy(() -> future.get(1, TimeUnit.SECONDS))
                    .isInstanceOf(ExecutionException.class)
                    .hasCauseInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("No loopback listener registered");
        }

        @Test
        @DisplayName("double listen on same name throws — one listener per name")
        void doubleListenSameNameThrows() {
            LoopbackTransport transport = new LoopbackTransport();
            transport.listen(LoopbackEndpoint.of("dup"), t -> {});
            assertThatThrownBy(() ->
                    transport.listen(LoopbackEndpoint.of("dup"), t -> {}))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("already registered");
        }

        @Test
        @DisplayName("connect with non-loopback endpoint fails the future")
        void connectWithWrongEndpointType() {
            LoopbackTransport transport = new LoopbackTransport();
            CompletableFuture<Tunnel> future = transport.connect(
                    TcpEndpoint.of(IpAddress.parse("127.0.0.1"), 0));
            assertThatThrownBy(() -> future.get(1, TimeUnit.SECONDS))
                    .isInstanceOf(ExecutionException.class)
                    .hasCauseInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("LoopbackEndpoint");
        }

        @Test
        @DisplayName("listen with non-loopback endpoint throws")
        void listenWithWrongEndpointType() {
            LoopbackTransport transport = new LoopbackTransport();
            assertThatThrownBy(() ->
                    transport.listen(TcpEndpoint.of(IpAddress.parse("127.0.0.1"), 0),
                            t -> {}))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("LoopbackEndpoint");
        }
    }

    @Nested
    @DisplayName("Identity")
    class Identity {

        @Test
        @DisplayName("transport() returns the Loopback transport-sememe IID")
        void transportSememe() {
            LoopbackTransport transport = new LoopbackTransport();
            assertThat(transport.transport())
                    .isEqualTo(dev.everydaythings.graph.ref.ItemRef.iid(
                            dev.everydaythings.graph.network.NetworkVocabulary.Loopback.KEY));
        }
    }
}
