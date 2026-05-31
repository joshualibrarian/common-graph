package dev.everydaythings.graph.bridges.http;

import dev.everydaythings.graph.network.TcpEndpoint;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import io.netty.handler.ssl.util.SelfSignedCertificate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end HTTPS tests.  The key thing being validated here isn't HTTPS
 * itself (that's well-trodden Netty territory) but the architectural
 * claim: HTTPS is pure composition.  The HTTP layer never sees TLS; the
 * TLS layer never sees HTTP; both meet at the Tunnel SPI.
 *
 * <pre>
 *   KERI / app                    ← caller
 *      │
 *   HttpClient.send(URI)          ← convenience
 *      │
 *   HttpExchange.exchange         ← primitive
 *      │
 *   TlsTunnel                     ← TLS as Tunnel wrapper
 *      │
 *   TcpTunnel                     ← base byte channel
 * </pre>
 */
class HttpsTest {

    private SelfSignedCertificate cert;
    private SslContext serverContext;
    private SslContext clientContext;

    private HttpServer server;
    private HttpClient client;

    @BeforeEach
    void setUp() throws Exception {
        cert = new SelfSignedCertificate();
        serverContext = SslContextBuilder.forServer(cert.certificate(), cert.privateKey())
                .build();
        clientContext = SslContextBuilder.forClient()
                .trustManager(InsecureTrustManagerFactory.INSTANCE)
                .build();
        client = HttpClient.builder().sslContext(clientContext).build();
    }

    @AfterEach
    void tearDown() {
        if (server != null) server.close();
        if (client != null) client.close();
        if (cert != null) cert.delete();
    }

    @Test
    @DisplayName("HTTPS GET round-trips a text body, server sees the decoded request")
    void httpsGetRoundTrip() throws Exception {
        AtomicReference<String> seenPath = new AtomicReference<>();
        HttpRouter router = new HttpRouter()
                .onSync(HttpMethod.GET, "/secure", req -> {
                    seenPath.set(req.uri().getRawPath());
                    return HttpResponse.of(200,
                            "encrypted hello".getBytes(StandardCharsets.UTF_8),
                            "text/plain; charset=utf-8");
                });

        server = HttpServer.builder()
                .router(router)
                .bind(TcpEndpoint.of("127.0.0.1", 0))
                .tls(serverContext)
                .build();
        TcpEndpoint addr = server.start();

        URI target = URI.create("https://127.0.0.1:" + addr.port() + "/secure");
        HttpResponse resp = client.get(target).get(10, TimeUnit.SECONDS);

        assertThat(resp.status()).isEqualTo(200);
        assertThat(new String(resp.body(), StandardCharsets.UTF_8))
                .isEqualTo("encrypted hello");
        assertThat(seenPath.get())
                .as("server-side router saw the request path after TLS decryption")
                .isEqualTo("/secure");
    }

    @Test
    @DisplayName("HTTPS POST round-trips arbitrary body bytes intact through TLS")
    void httpsPostBodyRoundTrip() throws Exception {
        AtomicReference<byte[]> seenBody = new AtomicReference<>();
        HttpRouter router = new HttpRouter()
                .onSync(HttpMethod.POST, "/upload", req -> {
                    seenBody.set(req.body());
                    return HttpResponse.of(200, req.body(), "application/cbor");
                });

        server = HttpServer.builder()
                .router(router)
                .bind(TcpEndpoint.of("127.0.0.1", 0))
                .tls(serverContext)
                .build();
        TcpEndpoint addr = server.start();

        byte[] payload = new byte[]{0, 1, 2, 3, 4, (byte) 0xFF, (byte) 0xAA, (byte) 0x55};
        URI target = URI.create("https://127.0.0.1:" + addr.port() + "/upload");
        HttpResponse resp = client.post(target, payload, "application/cbor")
                .get(10, TimeUnit.SECONDS);

        assertThat(resp.status()).isEqualTo(200);
        assertThat(seenBody.get()).containsExactly(payload);
        assertThat(resp.body()).containsExactly(payload);
    }

    @Test
    @DisplayName("Plain http:// client cannot reach an https:// server (TLS expects a handshake)")
    void httpClientFailsAgainstHttpsServer() throws Exception {
        HttpRouter router = new HttpRouter()
                .onSync(HttpMethod.GET, "/", req -> HttpResponse.of(200));

        server = HttpServer.builder()
                .router(router)
                .bind(TcpEndpoint.of("127.0.0.1", 0))
                .tls(serverContext)
                .build();
        TcpEndpoint addr = server.start();

        // Use a plain client (no SslContext) — the server will receive raw HTTP
        // bytes where it expects ClientHello.  The exchange should fail rather
        // than succeed.
        HttpClient plainClient = new HttpClient();
        try {
            URI target = URI.create("http://127.0.0.1:" + addr.port() + "/");
            // Either the request future fails (server rejects the malformed
            // handshake), or the future eventually times out.  Either is a
            // legitimate signal of "this composition is wrong"; assert it
            // doesn't return 200.
            try {
                HttpResponse resp = plainClient.get(target).get(3, TimeUnit.SECONDS);
                assertThat(resp.status())
                        .as("a plain HTTP client must not appear to succeed against an HTTPS server")
                        .isNotEqualTo(200);
            } catch (Exception expected) {
                // success: connection closed / timed out / errored
            }
        } finally {
            plainClient.close();
        }
    }
}
