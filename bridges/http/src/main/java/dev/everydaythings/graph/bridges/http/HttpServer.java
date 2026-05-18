package dev.everydaythings.graph.bridges.http;

import dev.everydaythings.graph.bridges.tcp.TcpTransport;
import dev.everydaythings.graph.bridges.tls.TlsTunnel;
import dev.everydaythings.graph.network.transport.Transport;
import dev.everydaythings.graph.network.tunnel.Tunnel;
import dev.everydaythings.graph.value.TcpEndpoint;
import io.netty.handler.ssl.SslContext;
import lombok.extern.log4j.Log4j2;

import java.util.Objects;

/**
 * Convenience HTTP/1.1 server.  Owns a {@link TcpTransport} listener and
 * (optionally) an {@link SslContext} for HTTPS, and composes them with
 * {@link TlsTunnel} and {@link HttpExchange#serve(Tunnel, HttpRouter, int)}
 * to serve requests through a {@link HttpRouter}.
 *
 * <p>For callers who want to manage their own composition — for example a
 * service that wants HTTP on top of a non-TCP transport — call
 * {@link HttpExchange#serve(Tunnel, HttpRouter)} directly on whatever
 * Tunnel they have.
 *
 * <h2>HTTPS</h2>
 *
 * <p>Pass an {@link SslContext} via {@link Builder#tls(SslContext)} to
 * make the server speak HTTPS.  Internally each accepted TCP tunnel is
 * wrapped with {@link TlsTunnel#server(Tunnel, SslContext)} before being
 * handed to {@link HttpExchange}.  The HTTP layer has no knowledge that
 * TLS is in play.
 *
 * <h2>Lifecycle</h2>
 *
 * <p>{@link #start} binds the listener and returns the address actually
 * bound (useful for {@code :0} ephemeral-port binds).  {@link #close}
 * stops accepting new connections and shuts the transport down.
 */
@Log4j2
public final class HttpServer implements AutoCloseable {

    public static final int DEFAULT_MAX_CONTENT_LENGTH = HttpExchange.DEFAULT_MAX_CONTENT_LENGTH;

    private final HttpRouter router;
    private final TcpEndpoint bindEndpoint;
    private final SslContext sslContext;
    private final int maxContentLength;
    private final TcpTransport transport;
    private final boolean ownsTransport;

    private Transport.Listener listener;
    private TcpEndpoint actualEndpoint;

    private HttpServer(Builder b) {
        this.router = b.router;
        this.bindEndpoint = b.bindEndpoint;
        this.sslContext = b.sslContext;
        this.maxContentLength = b.maxContentLength;
        this.transport = b.transport != null ? b.transport : new TcpTransport();
        this.ownsTransport = b.transport == null;
    }

    /** Bind the server, return the address actually bound. */
    public TcpEndpoint start() {
        listener = transport.listen(bindEndpoint, this::accept);
        actualEndpoint = (TcpEndpoint) listener.actualEndpoint();
        return actualEndpoint;
    }

    /** The endpoint actually bound — relevant for ephemeral-port (port 0) binds. */
    public TcpEndpoint actualEndpoint() {
        if (actualEndpoint == null) {
            throw new IllegalStateException("HttpServer is not started");
        }
        return actualEndpoint;
    }

    private void accept(Tunnel tcpTunnel) {
        Tunnel tunnel = sslContext == null
                ? tcpTunnel
                : TlsTunnel.server(tcpTunnel, sslContext);
        try {
            HttpExchange.serve(tunnel, router, maxContentLength);
        } catch (RuntimeException e) {
            logger.warn("Failed to start HTTP serving on accepted tunnel", e);
            tunnel.close();
        }
    }

    @Override
    public void close() {
        if (listener != null) listener.close();
        if (ownsTransport) transport.close();
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private HttpRouter router;
        private TcpEndpoint bindEndpoint;
        private SslContext sslContext;
        private int maxContentLength = DEFAULT_MAX_CONTENT_LENGTH;
        private TcpTransport transport;

        public Builder router(HttpRouter router) {
            this.router = router;
            return this;
        }

        public Builder bind(TcpEndpoint endpoint) {
            this.bindEndpoint = endpoint;
            return this;
        }

        /** Enable HTTPS with the given SslContext.  Must be a server-mode context. */
        public Builder tls(SslContext sslContext) {
            this.sslContext = sslContext;
            return this;
        }

        public Builder maxContentLength(int bytes) {
            if (bytes < 1) throw new IllegalArgumentException("maxContentLength must be >= 1");
            this.maxContentLength = bytes;
            return this;
        }

        /** Use a caller-provided TcpTransport.  Caller owns its lifecycle. */
        public Builder transport(TcpTransport transport) {
            this.transport = transport;
            return this;
        }

        public HttpServer build() {
            Objects.requireNonNull(router, "router");
            Objects.requireNonNull(bindEndpoint, "bindEndpoint");
            return new HttpServer(this);
        }
    }
}
