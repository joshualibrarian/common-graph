package dev.everydaythings.graph.bridges.http;

import dev.everydaythings.graph.bridges.tcp.TcpTransport;
import dev.everydaythings.graph.bridges.tls.TlsTunnel;
import dev.everydaythings.graph.network.tunnel.Tunnel;
import dev.everydaythings.graph.value.TcpEndpoint;
import io.netty.handler.ssl.SslContext;
import lombok.extern.log4j.Log4j2;

import java.net.URI;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

/**
 * Convenience HTTP/1.1 client.  Owns a {@link TcpTransport} and (optionally)
 * an {@link SslContext} for HTTPS, and composes them with {@link TlsTunnel}
 * and {@link HttpExchange} to fulfil URI-based requests.
 *
 * <p>For callers who want to manage their own composition — for example a
 * KERI bridge that wires {@code TcpTransport → TlsTunnel → HttpExchange}
 * itself — use {@link HttpExchange#exchange(Tunnel, HttpRequest)} directly.
 *
 * <h2>Behaviour</h2>
 *
 * <p>One TCP connection per request.  No connection pooling; the tunnel is
 * closed after the response arrives.  Both {@code http://} and {@code
 * https://} are supported; HTTPS requires an {@link SslContext} configured
 * via the builder.
 *
 * <h2>Lifecycle</h2>
 *
 * <p>{@link #close} shuts the underlying TcpTransport down.  Construct one
 * HttpClient per app and share it.
 */
@Log4j2
public final class HttpClient implements AutoCloseable {

    public static final int DEFAULT_MAX_CONTENT_LENGTH = HttpExchange.DEFAULT_MAX_CONTENT_LENGTH;

    private final TcpTransport transport;
    private final boolean ownsTransport;
    private final SslContext sslContext;
    private final int maxContentLength;

    private HttpClient(Builder b) {
        this.transport = b.transport != null ? b.transport : new TcpTransport();
        this.ownsTransport = b.transport == null;
        this.sslContext = b.sslContext;
        this.maxContentLength = b.maxContentLength;
    }

    /** Default client: owns its own TcpTransport, no HTTPS. */
    public HttpClient() {
        this(builder());
    }

    // ==================================================================================
    // Public API
    // ==================================================================================

    /** Send a request, returning a future for the response. */
    public CompletableFuture<HttpResponse> send(HttpRequest request) {
        Objects.requireNonNull(request, "request");

        URI uri = request.uri();
        if (uri.getHost() == null) {
            return CompletableFuture.failedFuture(new IllegalArgumentException(
                    "HttpClient.send requires an absolute URI with a host, got " + uri));
        }
        String scheme = uri.getScheme();
        boolean https;
        if ("http".equalsIgnoreCase(scheme)) {
            https = false;
        } else if ("https".equalsIgnoreCase(scheme)) {
            https = true;
            if (sslContext == null) {
                return CompletableFuture.failedFuture(new IllegalStateException(
                        "https:// request requires an SslContext; configure via HttpClient.builder().sslContext(...)"));
            }
        } else {
            return CompletableFuture.failedFuture(new IllegalArgumentException(
                    "HttpClient supports http:// and https:// only, got scheme '" + scheme + "'"));
        }
        int port = uri.getPort() == -1 ? (https ? 443 : 80) : uri.getPort();
        String host = uri.getHost();

        TcpEndpoint endpoint = TcpEndpoint.of(host, port);
        return transport.connect(endpoint).thenCompose(tcpTunnel -> {
            Tunnel tunnel = https
                    ? TlsTunnel.client(tcpTunnel, sslContext, host, port)
                    : tcpTunnel;
            CompletableFuture<HttpResponse> exchange =
                    HttpExchange.exchange(tunnel, request, maxContentLength);
            return exchange.whenComplete((resp, err) -> tunnel.close());
        });
    }

    /** Convenience for GET. */
    public CompletableFuture<HttpResponse> get(URI uri) {
        return send(HttpRequest.get(uri));
    }

    /** Convenience for POST with body + content-type. */
    public CompletableFuture<HttpResponse> post(URI uri, byte[] body, String contentType) {
        return send(HttpRequest.post(uri, body, contentType));
    }

    // ==================================================================================
    // Lifecycle
    // ==================================================================================

    @Override
    public void close() {
        if (ownsTransport) transport.close();
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private TcpTransport transport;
        private SslContext sslContext;
        private int maxContentLength = DEFAULT_MAX_CONTENT_LENGTH;

        /** Use a caller-provided TcpTransport.  Caller owns its lifecycle. */
        public Builder transport(TcpTransport transport) {
            this.transport = transport;
            return this;
        }

        /** Enable HTTPS with the given SslContext.  Must be a client-mode context. */
        public Builder sslContext(SslContext sslContext) {
            this.sslContext = sslContext;
            return this;
        }

        public Builder maxContentLength(int bytes) {
            if (bytes < 1) throw new IllegalArgumentException("maxContentLength must be >= 1");
            this.maxContentLength = bytes;
            return this;
        }

        public HttpClient build() {
            return new HttpClient(this);
        }
    }
}
