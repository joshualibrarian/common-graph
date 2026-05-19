package dev.everydaythings.graph.bridges.tls;

import dev.everydaythings.graph.network.tunnel.LoopbackTunnel;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import io.netty.handler.ssl.util.SelfSignedCertificate;
import org.bouncycastle.cert.X509v3CertificateBuilder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import javax.security.auth.x500.X500Principal;
import java.math.BigInteger;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.cert.X509Certificate;
import java.util.Date;
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
        @DisplayName("counterparty empty for one-sided TLS with an RSA self-signed cert (RSA not yet wired through MultiKey)")
        void counterpartyEmptyForRsa() throws Exception {
            // Netty's SelfSignedCertificate defaults to RSA, and MultiKey.fromJcaPublicKey
            // only handles Ed25519 today.  So this exercise produces an empty
            // counterparty on both sides:
            //   - client side: server's RSA cert can't convert to MultiKey
            //   - server side: client presented no cert (one-sided TLS)
            LoopbackTunnel.Pair pair = LoopbackTunnel.pair();
            TlsTunnel serverTls = TlsTunnel.server(pair.a(), serverContext);
            TlsTunnel clientTls = TlsTunnel.client(pair.b(), clientContext);

            clientTls.handshakeFuture().get(5, TimeUnit.SECONDS);

            assertThat(clientTls.counterparty()).isEmpty();
            assertThat(serverTls.counterparty()).isEmpty();
            assertThat(clientTls.isAuthenticated()).isFalse();
            assertThat(serverTls.isAuthenticated()).isFalse();
        }

        @Test
        @DisplayName("counterparty surfaces the peer's Ed25519 multikey after handshake with Ed25519 cert")
        void counterpartyForEd25519() throws Exception {
            // Mint an Ed25519 keypair + self-signed cert via BouncyCastle
            // (Netty's SelfSignedCertificate doesn't support EdDSA).
            KeyPair edKeyPair = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
            X509Certificate edCert = mintEd25519SelfSignedCert(edKeyPair, "CN=test.cg");

            SslContext edServerContext = SslContextBuilder
                    .forServer(edKeyPair.getPrivate(), edCert).build();
            SslContext edClientContext = SslContextBuilder.forClient()
                    .trustManager(InsecureTrustManagerFactory.INSTANCE).build();

            LoopbackTunnel.Pair pair = LoopbackTunnel.pair();
            TlsTunnel serverTls = TlsTunnel.server(pair.a(), edServerContext);
            TlsTunnel clientTls = TlsTunnel.client(pair.b(), edClientContext);

            clientTls.handshakeFuture().get(5, TimeUnit.SECONDS);

            // Client side: the server's cert pubkey is now visible as a MultiKey.
            assertThat(clientTls.counterparty()).isPresent();
            assertThat(clientTls.counterparty().orElseThrow().code())
                    .as("Ed25519 multikey code 0xed")
                    .isEqualTo(0xed);
            assertThat(clientTls.isAuthenticated()).isTrue();

            // Server side: client didn't present a cert (one-sided TLS).
            assertThat(serverTls.counterparty()).isEmpty();
        }

        private static X509Certificate mintEd25519SelfSignedCert(KeyPair keyPair, String subjectDn) throws Exception {
            X500Principal subject = new X500Principal(subjectDn);
            Date notBefore = new Date();
            Date notAfter = new Date(notBefore.getTime() + 365L * 24 * 60 * 60 * 1000);
            X509v3CertificateBuilder builder = new JcaX509v3CertificateBuilder(
                    subject, BigInteger.ONE, notBefore, notAfter, subject, keyPair.getPublic());
            ContentSigner signer = new JcaContentSignerBuilder("Ed25519")
                    .build(keyPair.getPrivate());
            return new JcaX509CertificateConverter()
                    .getCertificate(builder.build(signer));
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
