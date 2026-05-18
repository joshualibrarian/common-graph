package dev.everydaythings.graph.bridges.tls;

import dev.everydaythings.graph.network.tunnel.LoopbackTunnel;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import io.netty.handler.ssl.util.SelfSignedCertificate;
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
 * End-to-end tests for {@link TlsTunnel} composed on top of a
 * {@link LoopbackTunnel} pair.  Validates: handshake completion, bidirectional
 * cleartext round-trip through real TLS encrypt/decrypt, deferred-send
 * semantics before handshake, security-property declarations.
 */
class TlsTunnelTest {

    private SelfSignedCertificate cert;
    private SslContext serverContext;
    private SslContext clientContext;

    @BeforeEach
    void setUp() throws Exception {
        cert = new SelfSignedCertificate();
        serverContext = SslContextBuilder.forServer(cert.certificate(), cert.privateKey())
                .build();
        // Test-only trust-all client — fine because the server cert is generated
        // fresh per test and never exposed beyond the JVM.
        clientContext = SslContextBuilder.forClient()
                .trustManager(InsecureTrustManagerFactory.INSTANCE)
                .build();
    }

    @AfterEach
    void tearDown() {
        if (cert != null) cert.delete();
    }

    @Nested
    @DisplayName("Round-trip")
    class RoundTrip {

        @Test
        @DisplayName("handshake completes on both sides; bytes round-trip cleartext after handshake")
        void bytesRoundTripCleartext() throws Exception {
            LoopbackTunnel.Pair pair = LoopbackTunnel.pair();
            TlsTunnel serverTls = TlsTunnel.server(pair.a(), serverContext);
            TlsTunnel clientTls = TlsTunnel.client(pair.b(), clientContext);

            clientTls.handshakeFuture().get(5, TimeUnit.SECONDS);
            serverTls.handshakeFuture().get(5, TimeUnit.SECONDS);

            CompletableFuture<byte[]> serverGot = new CompletableFuture<>();
            CompletableFuture<byte[]> clientGot = new CompletableFuture<>();
            serverTls.onReceive(serverGot::complete);
            clientTls.onReceive(clientGot::complete);

            clientTls.send("hello from client".getBytes()).get(2, TimeUnit.SECONDS);
            assertThat(new String(serverGot.get(2, TimeUnit.SECONDS)))
                    .isEqualTo("hello from client");

            serverTls.send("hello from server".getBytes()).get(2, TimeUnit.SECONDS);
            assertThat(new String(clientGot.get(2, TimeUnit.SECONDS)))
                    .isEqualTo("hello from server");
        }

        @Test
        @DisplayName("send before handshake completes is deferred and delivered after handshake")
        void sendDeferredUntilHandshake() throws Exception {
            LoopbackTunnel.Pair pair = LoopbackTunnel.pair();
            TlsTunnel serverTls = TlsTunnel.server(pair.a(), serverContext);
            TlsTunnel clientTls = TlsTunnel.client(pair.b(), clientContext);

            // Don't wait for handshake — fire a send immediately.  The future
            // shouldn't complete until handshake is done and the bytes are flushed.
            CompletableFuture<Void> sendFuture = clientTls.send("early bytes".getBytes());

            CompletableFuture<byte[]> serverGot = new CompletableFuture<>();
            serverTls.onReceive(serverGot::complete);

            sendFuture.get(5, TimeUnit.SECONDS);
            assertThat(new String(serverGot.get(2, TimeUnit.SECONDS))).isEqualTo("early bytes");
            assertThat(clientTls.handshakeFuture().isDone()).isTrue();
        }

        @Test
        @DisplayName("bytes arriving before onReceive is registered are buffered and drained on registration")
        void earlyPlaintextBuffered() throws Exception {
            LoopbackTunnel.Pair pair = LoopbackTunnel.pair();
            TlsTunnel serverTls = TlsTunnel.server(pair.a(), serverContext);
            TlsTunnel clientTls = TlsTunnel.client(pair.b(), clientContext);

            clientTls.handshakeFuture().get(5, TimeUnit.SECONDS);
            serverTls.handshakeFuture().get(5, TimeUnit.SECONDS);

            // Server side never wires onReceive before client sends.
            clientTls.send("buffered cleartext".getBytes()).get(2, TimeUnit.SECONDS);

            CompletableFuture<byte[]> serverGot = new CompletableFuture<>();
            serverTls.onReceive(serverGot::complete);

            assertThat(new String(serverGot.get(2, TimeUnit.SECONDS)))
                    .isEqualTo("buffered cleartext");
        }
    }

    @Nested
    @DisplayName("Handshake failure modes")
    class HandshakeFailures {

        @Test
        @DisplayName("client refusing to trust an unknown cert fails handshake")
        void clientTrustRejection() throws Exception {
            // Strict client context: default trust managers, no insecure trust-all.
            SslContext strictClient = SslContextBuilder.forClient().build();

            LoopbackTunnel.Pair pair = LoopbackTunnel.pair();
            TlsTunnel.server(pair.a(), serverContext);
            TlsTunnel clientTls = TlsTunnel.client(pair.b(), strictClient);

            assertThatThrownBy(() -> clientTls.handshakeFuture().get(5, TimeUnit.SECONDS))
                    .as("client should reject the self-signed server cert")
                    .isInstanceOf(ExecutionException.class);
        }
    }

    @Nested
    @DisplayName("Security properties")
    class SecurityProperties {

        @Test
        @DisplayName("isConfidential is true unconditionally")
        void confidentialAlways() {
            LoopbackTunnel.Pair pair = LoopbackTunnel.pair();
            TlsTunnel clientTls = TlsTunnel.client(pair.b(), clientContext);
            assertThat(clientTls.isConfidential()).isTrue();
        }

        @Test
        @DisplayName("counterparty is empty pending the cert-as-record + trust resolution work")
        void counterpartyProvisional() throws Exception {
            LoopbackTunnel.Pair pair = LoopbackTunnel.pair();
            TlsTunnel serverTls = TlsTunnel.server(pair.a(), serverContext);
            TlsTunnel clientTls = TlsTunnel.client(pair.b(), clientContext);

            clientTls.handshakeFuture().get(5, TimeUnit.SECONDS);

            // Provisional: pending step 5 (cert sememe + trust resolution),
            // counterparty() returns empty even after a successful handshake.
            assertThat(clientTls.counterparty()).isEmpty();
            assertThat(serverTls.counterparty()).isEmpty();
            assertThat(clientTls.isAuthenticated()).isFalse();
        }
    }

    @Nested
    @DisplayName("Input validation")
    class InputValidation {

        @Test
        @DisplayName("passing a server SslContext to client() throws")
        void wrongSideClient() {
            LoopbackTunnel.Pair pair = LoopbackTunnel.pair();
            assertThatThrownBy(() -> TlsTunnel.client(pair.a(), serverContext))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("server mode");
        }

        @Test
        @DisplayName("passing a client SslContext to server() throws")
        void wrongSideServer() {
            LoopbackTunnel.Pair pair = LoopbackTunnel.pair();
            assertThatThrownBy(() -> TlsTunnel.server(pair.a(), clientContext))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("client mode");
        }
    }

    @Nested
    @DisplayName("Lifecycle")
    class Lifecycle {

        @Test
        @DisplayName("close on one side cascades through the underlying tunnel")
        void closeCascades() throws Exception {
            LoopbackTunnel.Pair pair = LoopbackTunnel.pair();
            TlsTunnel serverTls = TlsTunnel.server(pair.a(), serverContext);
            TlsTunnel clientTls = TlsTunnel.client(pair.b(), clientContext);

            clientTls.handshakeFuture().get(5, TimeUnit.SECONDS);

            clientTls.close();
            assertThat(clientTls.isOpen()).isFalse();
            // The cascade goes through LoopbackTunnel: closing the client end
            // closes pair.b(), which closes pair.a(), so serverTls's underlying
            // is also closed.
            assertThat(serverTls.isOpen()).isFalse();
        }

        @Test
        @DisplayName("close is idempotent")
        void closeIdempotent() {
            LoopbackTunnel.Pair pair = LoopbackTunnel.pair();
            TlsTunnel clientTls = TlsTunnel.client(pair.b(), clientContext);
            clientTls.close();
            clientTls.close();   // must not throw
        }
    }
}
