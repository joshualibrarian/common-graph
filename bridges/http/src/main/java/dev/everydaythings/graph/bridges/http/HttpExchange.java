package dev.everydaythings.graph.bridges.http;

import dev.everydaythings.graph.network.tunnel.Tunnel;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpClientCodec;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.http.HttpVersion;
import lombok.extern.log4j.Log4j2;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Primitive layer for HTTP/1.1 over an arbitrary {@link Tunnel}.  The
 * convenience {@link HttpClient} and {@link HttpServer} compose this with
 * {@link dev.everydaythings.graph.bridges.tcp.TcpTransport TcpTransport}
 * and optional {@link dev.everydaythings.graph.bridges.tls.TlsTunnel
 * TlsTunnel} wrappers; both are sugar over the two static entry points
 * below.
 *
 * <h2>How it works</h2>
 *
 * <p>Netty's {@code HttpServerCodec} / {@code HttpClientCodec} are
 * pipeline-shaped: they decode bytes into HTTP message objects and encode
 * HTTP message objects back into bytes.  We host that pipeline in an
 * {@link EmbeddedChannel} so bytes flow {@code Tunnel ↔ EmbeddedChannel ↔
 * HTTP codec ↔ handler} without owning a real Netty
 * {@link io.netty.channel.Channel Channel}.
 *
 * <p>Same {@code EmbeddedChannel} adapter pattern as
 * {@link dev.everydaythings.graph.bridges.tls.TlsTunnel TlsTunnel}; the
 * codec changes but the byte plumbing is identical.
 *
 * <h2>What this layer does and doesn't do</h2>
 *
 * <ul>
 *   <li>Does: encode/decode HTTP/1.1 messages over a Tunnel.  Aggregates
 *       chunked transfer into whole {@link HttpRequest}/{@link HttpResponse}
 *       objects via Netty's {@link HttpObjectAggregator}.</li>
 *   <li>Doesn't: open or close the underlying tunnel.  The Tunnel's
 *       lifetime is the caller's concern.</li>
 *   <li>Doesn't: HTTPS.  Wrap the tunnel with TlsTunnel before handing it
 *       in.  HTTPS is composition, not a feature here.</li>
 *   <li>Doesn't: keep-alive connection reuse on the client side.  Each
 *       call to {@link #exchange} sends one request and reads one
 *       response.  Caller decides whether to reuse the tunnel.</li>
 * </ul>
 */
@Log4j2
public final class HttpExchange {

    public static final int DEFAULT_MAX_CONTENT_LENGTH = 16 * 1024 * 1024;

    private HttpExchange() {}

    // ==================================================================================
    // Client side
    // ==================================================================================

    /** {@link #exchange(Tunnel, HttpRequest, int)} with the default response cap. */
    public static CompletableFuture<HttpResponse> exchange(Tunnel tunnel, HttpRequest request) {
        return exchange(tunnel, request, DEFAULT_MAX_CONTENT_LENGTH);
    }

    /**
     * Send one request over the tunnel, await one response.  Does not
     * close the tunnel.  Caller owns the tunnel's lifecycle.
     *
     * @param maxResponseBytes upper bound on aggregated response body size
     */
    public static CompletableFuture<HttpResponse> exchange(
            Tunnel tunnel, HttpRequest request, int maxResponseBytes) {
        Objects.requireNonNull(tunnel, "tunnel");
        Objects.requireNonNull(request, "request");

        CompletableFuture<HttpResponse> future = new CompletableFuture<>();
        ClientHandler handler = new ClientHandler(future);
        EmbeddedChannel embedded = new EmbeddedChannel(
                new HttpClientCodec(),
                new HttpObjectAggregator(maxResponseBytes),
                handler);

        Object lock = new Object();
        AtomicReference<EmbeddedChannel> embeddedRef = new AtomicReference<>(embedded);

        tunnel.onReceive(bytes -> {
            EmbeddedChannel ec = embeddedRef.get();
            if (ec == null) return;
            synchronized (lock) {
                ec.writeInbound(Unpooled.wrappedBuffer(bytes));
                drainOutbound(ec, tunnel, lock);
            }
        });

        synchronized (lock) {
            FullHttpRequest nettyReq = toNettyRequest(request);
            embedded.writeOutbound(nettyReq);
            drainOutbound(embedded, tunnel, lock);
        }
        return future;
    }

    // ==================================================================================
    // Server side
    // ==================================================================================

    /** {@link #serve(Tunnel, HttpRouter, int)} with the default request cap. */
    public static void serve(Tunnel tunnel, HttpRouter router) {
        serve(tunnel, router, DEFAULT_MAX_CONTENT_LENGTH);
    }

    /**
     * Speak HTTP/1.1 on the tunnel for as long as the tunnel stays open.
     * Each request arriving on the tunnel is dispatched to {@code router};
     * the resulting response is written back.  Tunnel close ends the
     * serving session.
     *
     * @param maxRequestBytes upper bound on aggregated request body size
     */
    public static void serve(Tunnel tunnel, HttpRouter router, int maxRequestBytes) {
        Objects.requireNonNull(tunnel, "tunnel");
        Objects.requireNonNull(router, "router");

        Object lock = new Object();
        ServerHandler handler = new ServerHandler(router);
        EmbeddedChannel embedded = new EmbeddedChannel(
                new HttpServerCodec(),
                new HttpObjectAggregator(maxRequestBytes),
                handler);
        handler.setDrain(() -> {
            synchronized (lock) {
                drainOutbound(embedded, tunnel, lock);
            }
        });

        tunnel.onReceive(bytes -> {
            synchronized (lock) {
                if (!embedded.isOpen()) return;
                embedded.writeInbound(Unpooled.wrappedBuffer(bytes));
                drainOutbound(embedded, tunnel, lock);
            }
        });
    }

    // ==================================================================================
    // Byte plumbing
    // ==================================================================================

    private static void drainOutbound(EmbeddedChannel embedded, Tunnel tunnel, Object lock) {
        // Caller holds `lock`.
        ByteBuf out;
        while ((out = embedded.readOutbound()) != null) {
            byte[] arr;
            try {
                arr = new byte[out.readableBytes()];
                out.readBytes(arr);
            } finally {
                out.release();
            }
            if (!tunnel.isOpen()) {
                logger.debug("Dropping {} outbound HTTP bytes; tunnel closed", arr.length);
                continue;
            }
            try {
                tunnel.send(arr);
            } catch (RuntimeException e) {
                logger.debug("Tunnel send failed; dropping {} bytes: {}", arr.length, e.toString());
            }
        }
    }

    // ==================================================================================
    // Netty ↔ CG type conversion
    // ==================================================================================

    static FullHttpRequest toNettyRequest(HttpRequest req) {
        URI uri = req.uri();
        String rawPath = uri.getRawPath();
        if (rawPath == null || rawPath.isEmpty()) rawPath = "/";
        if (uri.getRawQuery() != null) rawPath = rawPath + "?" + uri.getRawQuery();

        ByteBuf body = req.bodyLength() == 0
                ? Unpooled.EMPTY_BUFFER
                : Unpooled.wrappedBuffer(req.bodyRaw());

        FullHttpRequest nettyReq = new DefaultFullHttpRequest(
                HttpVersion.HTTP_1_1,
                mapMethod(req.method()),
                rawPath,
                body);

        for (Map.Entry<String, java.util.List<String>> e : req.headers().asMap().entrySet()) {
            for (String value : e.getValue()) {
                nettyReq.headers().add(e.getKey(), value);
            }
        }
        // HTTP/1.1 compliance: Host + Content-Length must be present.
        String host = uri.getHost();
        if (host != null) {
            int port = uri.getPort();
            String hostHeader = (port == -1 || port == 80 || port == 443)
                    ? host
                    : host + ":" + port;
            nettyReq.headers().set(HttpHeaderNames.HOST, hostHeader);
        }
        nettyReq.headers().setInt(HttpHeaderNames.CONTENT_LENGTH, req.bodyLength());
        nettyReq.headers().set(HttpHeaderNames.CONNECTION, "close");
        return nettyReq;
    }

    static HttpRequest fromNettyRequest(FullHttpRequest nettyReq) {
        HttpMethod method = unmapMethod(nettyReq.method().name());
        URI uri;
        try {
            uri = new URI(nettyReq.uri());
        } catch (URISyntaxException e) {
            uri = URI.create("/");
        }
        HttpHeaders headers = new HttpHeaders();
        for (Map.Entry<String, String> h : nettyReq.headers()) {
            headers.add(h.getKey(), h.getValue());
        }
        ByteBuf content = nettyReq.content();
        byte[] body = new byte[content.readableBytes()];
        content.readBytes(body);
        return new HttpRequest(method, uri, headers, body);
    }

    static FullHttpResponse toNettyResponse(HttpResponse resp) {
        ByteBuf buf = resp.bodyLength() == 0
                ? Unpooled.EMPTY_BUFFER
                : Unpooled.wrappedBuffer(resp.bodyRaw());
        FullHttpResponse out = new DefaultFullHttpResponse(
                HttpVersion.HTTP_1_1,
                HttpResponseStatus.valueOf(resp.status()),
                buf);
        for (Map.Entry<String, java.util.List<String>> entry : resp.headers().asMap().entrySet()) {
            for (String value : entry.getValue()) {
                out.headers().add(entry.getKey(), value);
            }
        }
        out.headers().setInt(HttpHeaderNames.CONTENT_LENGTH, resp.bodyLength());
        return out;
    }

    static HttpResponse fromNettyResponse(FullHttpResponse nettyResp) {
        HttpHeaders headers = new HttpHeaders();
        for (Map.Entry<String, String> h : nettyResp.headers()) {
            headers.add(h.getKey(), h.getValue());
        }
        ByteBuf content = nettyResp.content();
        byte[] body = new byte[content.readableBytes()];
        content.readBytes(body);
        return new HttpResponse(nettyResp.status().code(), headers, body);
    }

    private static io.netty.handler.codec.http.HttpMethod mapMethod(HttpMethod m) {
        return switch (m) {
            case GET    -> io.netty.handler.codec.http.HttpMethod.GET;
            case POST   -> io.netty.handler.codec.http.HttpMethod.POST;
            case PUT    -> io.netty.handler.codec.http.HttpMethod.PUT;
            case DELETE -> io.netty.handler.codec.http.HttpMethod.DELETE;
        };
    }

    private static HttpMethod unmapMethod(String name) {
        return switch (name) {
            case "GET"    -> HttpMethod.GET;
            case "POST"   -> HttpMethod.POST;
            case "PUT"    -> HttpMethod.PUT;
            case "DELETE" -> HttpMethod.DELETE;
            default -> throw new IllegalArgumentException("Unsupported HTTP method: " + name);
        };
    }

    // ==================================================================================
    // Embedded-pipeline handlers
    // ==================================================================================

    private static final class ClientHandler extends SimpleChannelInboundHandler<FullHttpResponse> {
        private final CompletableFuture<HttpResponse> future;

        ClientHandler(CompletableFuture<HttpResponse> future) {
            this.future = future;
        }

        @Override
        protected void channelRead0(ChannelHandlerContext ctx, FullHttpResponse nettyResp) {
            if (!future.isDone()) {
                future.complete(fromNettyResponse(nettyResp));
            }
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            if (!future.isDone()) future.completeExceptionally(cause);
        }

        @Override
        public void channelInactive(ChannelHandlerContext ctx) {
            if (!future.isDone()) {
                future.completeExceptionally(new IOException(
                        "Channel closed before response was received"));
            }
        }
    }

    private static final class ServerHandler extends SimpleChannelInboundHandler<FullHttpRequest> {
        private final HttpRouter router;
        private Runnable drain = () -> {};

        ServerHandler(HttpRouter router) {
            this.router = router;
        }

        void setDrain(Runnable drain) {
            this.drain = drain;
        }

        @Override
        protected void channelRead0(ChannelHandlerContext ctx, FullHttpRequest nettyReq) {
            HttpRequest request = fromNettyRequest(nettyReq);
            CompletableFuture<HttpResponse> futureResp;
            try {
                futureResp = router.dispatch(request);
            } catch (RuntimeException e) {
                futureResp = CompletableFuture.failedFuture(e);
            }
            futureResp.whenComplete((resp, err) -> {
                FullHttpResponse out;
                if (err != null) {
                    logger.warn("HTTP handler failed for {} {}: {}",
                            request.method(), request.uri().getRawPath(), err.toString());
                    out = errorResponse();
                } else if (resp == null) {
                    out = errorResponse();
                } else {
                    out = toNettyResponse(resp);
                }
                ctx.writeAndFlush(out);
                drain.run();
            });
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            logger.warn("HTTP server pipeline exception", cause);
            ctx.close();
        }

        private static FullHttpResponse errorResponse() {
            FullHttpResponse out = new DefaultFullHttpResponse(
                    HttpVersion.HTTP_1_1,
                    HttpResponseStatus.INTERNAL_SERVER_ERROR,
                    Unpooled.EMPTY_BUFFER);
            // Without Content-Length the client can't know the response is
            // complete and waits for connection close that never comes.
            out.headers().setInt(HttpHeaderNames.CONTENT_LENGTH, 0);
            return out;
        }
    }
}
