package dev.everydaythings.graph.bridges.http;

import dev.everydaythings.graph.network.TcpEndpoint;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * End-to-end tests for {@link HttpServer} + {@link HttpClient}, both
 * Netty-backed, looping over real TCP sockets on {@code 127.0.0.1:0}.
 */
class HttpServerClientTest {

    private HttpClient client;
    private HttpServer server;

    @BeforeEach
    void setUp() {
        client = new HttpClient();
    }

    @AfterEach
    void tearDown() {
        if (server != null) server.close();
        client.close();
    }

    @Nested
    @DisplayName("Round-trip")
    class RoundTrip {

        @Test
        @DisplayName("GET / returns 200 with text body the handler produced")
        void getReturnsHandlerResponse() throws Exception {
            HttpRouter router = new HttpRouter()
                    .onSync(HttpMethod.GET, "/", req ->
                            HttpResponse.of(200, "hello, world".getBytes(StandardCharsets.UTF_8),
                                    "text/plain; charset=utf-8"));
            server = HttpServer.builder()
                    .router(router)
                    .bind(TcpEndpoint.of("127.0.0.1", 0))
                    .build();
            TcpEndpoint addr = server.start();

            URI target = URI.create("http://127.0.0.1:" + addr.port() + "/");
            HttpResponse resp = client.get(target).get(5, TimeUnit.SECONDS);

            assertThat(resp.status()).isEqualTo(200);
            assertThat(resp.isSuccess()).isTrue();
            assertThat(new String(resp.body(), StandardCharsets.UTF_8))
                    .isEqualTo("hello, world");
            assertThat(resp.headers().first("Content-Type"))
                    .contains("text/plain; charset=utf-8");
        }

        @Test
        @DisplayName("POST round-trips body bytes intact, preserves Content-Type")
        void postBodyRoundTrip() throws Exception {
            AtomicReference<byte[]> seenBody = new AtomicReference<>();
            AtomicReference<String> seenContentType = new AtomicReference<>();

            HttpRouter router = new HttpRouter()
                    .onSync(HttpMethod.POST, "/echo", req -> {
                        seenBody.set(req.body());
                        seenContentType.set(req.headers().first("Content-Type").orElse(""));
                        return HttpResponse.of(200, req.body(),
                                req.headers().first("Content-Type").orElse("application/octet-stream"));
                    });
            server = HttpServer.builder()
                    .router(router)
                    .bind(TcpEndpoint.of("127.0.0.1", 0))
                    .build();
            TcpEndpoint addr = server.start();

            byte[] payload = new byte[]{1, 2, 3, 4, 5, 0, (byte) 0xFF};
            URI target = URI.create("http://127.0.0.1:" + addr.port() + "/echo");
            HttpResponse resp = client.post(target, payload, "application/cbor")
                    .get(5, TimeUnit.SECONDS);

            assertThat(seenBody.get()).containsExactly(payload);
            assertThat(seenContentType.get()).isEqualTo("application/cbor");
            assertThat(resp.status()).isEqualTo(200);
            assertThat(resp.body()).containsExactly(payload);
        }

        @Test
        @DisplayName("unmatched path returns 404 from default fallback")
        void unmatchedPathReturns404() throws Exception {
            HttpRouter router = new HttpRouter()
                    .onSync(HttpMethod.GET, "/known", req -> HttpResponse.of(200));
            server = HttpServer.builder()
                    .router(router)
                    .bind(TcpEndpoint.of("127.0.0.1", 0))
                    .build();
            TcpEndpoint addr = server.start();

            URI target = URI.create("http://127.0.0.1:" + addr.port() + "/missing");
            HttpResponse resp = client.get(target).get(5, TimeUnit.SECONDS);

            assertThat(resp.status()).isEqualTo(404);
        }

        @Test
        @DisplayName("handler that throws yields 500")
        void handlerThrowsYields500() throws Exception {
            HttpRouter router = new HttpRouter()
                    .onSync(HttpMethod.GET, "/boom", req -> {
                        throw new RuntimeException("intentional");
                    });
            server = HttpServer.builder()
                    .router(router)
                    .bind(TcpEndpoint.of("127.0.0.1", 0))
                    .build();
            TcpEndpoint addr = server.start();

            URI target = URI.create("http://127.0.0.1:" + addr.port() + "/boom");
            HttpResponse resp = client.get(target).get(5, TimeUnit.SECONDS);

            assertThat(resp.status()).isEqualTo(500);
        }

        @Test
        @DisplayName("path with query string is routed by path; query reaches handler")
        void pathAndQuery() throws Exception {
            AtomicReference<String> seenQuery = new AtomicReference<>();
            HttpRouter router = new HttpRouter()
                    .onSync(HttpMethod.GET, "/q", req -> {
                        seenQuery.set(req.uri().getRawQuery());
                        return HttpResponse.of(200);
                    });
            server = HttpServer.builder()
                    .router(router)
                    .bind(TcpEndpoint.of("127.0.0.1", 0))
                    .build();
            TcpEndpoint addr = server.start();

            URI target = URI.create("http://127.0.0.1:" + addr.port() + "/q?name=keri&surface=kel");
            HttpResponse resp = client.get(target).get(5, TimeUnit.SECONDS);

            assertThat(resp.status()).isEqualTo(200);
            assertThat(seenQuery.get()).isEqualTo("name=keri&surface=kel");
        }
    }

    @Nested
    @DisplayName("Client failure modes")
    class ClientFailures {

        @Test
        @DisplayName("https:// without an SslContext fails the future with IllegalStateException")
        void httpsRequiresSslContext() {
            CompletableFuture<HttpResponse> future = client.get(URI.create("https://example.com/"));
            assertThatThrownBy(() -> future.get(1, TimeUnit.SECONDS))
                    .isInstanceOf(ExecutionException.class)
                    .hasCauseInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("SslContext");
        }

        @Test
        @DisplayName("unknown scheme is rejected with IllegalArgumentException")
        void unknownSchemeRejected() {
            CompletableFuture<HttpResponse> future = client.get(URI.create("ftp://example.com/"));
            assertThatThrownBy(() -> future.get(1, TimeUnit.SECONDS))
                    .isInstanceOf(ExecutionException.class)
                    .hasCauseInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("http://");
        }

        @Test
        @DisplayName("URI without a host is rejected")
        void missingHostRejected() {
            CompletableFuture<HttpResponse> future = client.get(URI.create("http:///path"));
            assertThatThrownBy(() -> future.get(1, TimeUnit.SECONDS))
                    .isInstanceOf(ExecutionException.class)
                    .hasCauseInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("host");
        }

        @Test
        @DisplayName("connect to a dead port fails the future")
        void connectToDeadPort() {
            // Use a port likely to be closed.  Connect timeout kicks in if RST is slow.
            CompletableFuture<HttpResponse> future =
                    client.get(URI.create("http://127.0.0.1:1/"));
            assertThatThrownBy(() -> future.get(15, TimeUnit.SECONDS))
                    .isInstanceOf(ExecutionException.class);
        }
    }

    @Nested
    @DisplayName("Server lifecycle")
    class ServerLifecycle {

        @Test
        @DisplayName("actualEndpoint() reports the kernel-assigned port; throws before start()")
        void actualEndpointGuards() {
            server = HttpServer.builder()
                    .router(new HttpRouter())
                    .bind(TcpEndpoint.of("127.0.0.1", 0))
                    .build();
            assertThatThrownBy(server::actualEndpoint)
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("not started");

            TcpEndpoint addr = server.start();
            assertThat(addr.port()).isPositive();
            assertThat(server.actualEndpoint()).isEqualTo(addr);
        }
    }
}
