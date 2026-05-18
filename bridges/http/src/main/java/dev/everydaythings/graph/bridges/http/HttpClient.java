package dev.everydaythings.graph.bridges.http;

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpClientCodec;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpVersion;
import lombok.extern.log4j.Log4j2;

import java.net.URI;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Netty-backed async HTTP/1.1 client.  One-shot per request — opens a TCP
 * connection, sends the request, reads the response, closes.  No
 * connection pooling and no TLS yet; bridges that need TLS will get it
 * once the cert / Noise story is settled.
 *
 * <p>Construct one client per app and share it; threads and event loops
 * are pooled inside.  {@link #close} shuts the event loop down.
 *
 * <h2>Body limits</h2>
 *
 * <p>{@link HttpObjectAggregator} caps responses at 16 MiB by default.
 * Override via {@link Builder#maxContentLength} for bridges that legitimately
 * fetch larger payloads.
 *
 * <h2>Failure modes</h2>
 *
 * <ul>
 *   <li>Connect failures → returned future fails with the underlying
 *       cause.</li>
 *   <li>Non-HTTP responses, oversize bodies → returned future fails with
 *       the codec's exception.</li>
 *   <li>Non-2xx HTTP status → returned future completes <i>successfully</i>
 *       with the {@link HttpResponse}; callers inspect
 *       {@link HttpResponse#status()} themselves.</li>
 * </ul>
 */
@Log4j2
public final class HttpClient implements AutoCloseable {

    public static final int DEFAULT_MAX_CONTENT_LENGTH = 16 * 1024 * 1024;
    private static final int DEFAULT_CONNECT_TIMEOUT_MS = 10_000;

    private final EventLoopGroup eventLoop;
    private final int maxContentLength;
    private final int connectTimeoutMs;

    public HttpClient() {
        this(builder());
    }

    private HttpClient(Builder b) {
        this.eventLoop = new NioEventLoopGroup();
        this.maxContentLength = b.maxContentLength;
        this.connectTimeoutMs = b.connectTimeoutMs;
    }

    /** Send a request, returning a future for the response. */
    public CompletableFuture<HttpResponse> send(HttpRequest request) {
        Objects.requireNonNull(request, "request");

        URI uri = request.uri();
        if (uri.getHost() == null) {
            return CompletableFuture.failedFuture(new IllegalArgumentException(
                    "HttpClient.send requires an absolute URI with a host, got " + uri));
        }
        if (uri.getScheme() == null || !uri.getScheme().equalsIgnoreCase("http")) {
            return CompletableFuture.failedFuture(new IllegalArgumentException(
                    "HttpClient currently supports http:// only, got scheme '" + uri.getScheme() + "'"));
        }
        int port = uri.getPort() == -1 ? 80 : uri.getPort();
        String host = uri.getHost();

        CompletableFuture<HttpResponse> future = new CompletableFuture<>();
        Bootstrap bootstrap = new Bootstrap()
                .group(eventLoop)
                .channel(NioSocketChannel.class)
                .option(ChannelOption.TCP_NODELAY, true)
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, connectTimeoutMs)
                .handler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) {
                        ch.pipeline().addLast(new HttpClientCodec());
                        ch.pipeline().addLast(new HttpObjectAggregator(maxContentLength));
                        ch.pipeline().addLast(new ResponseHandler(future));
                    }
                });

        bootstrap.connect(host, port).addListener((ChannelFutureListener) cf -> {
            if (!cf.isSuccess()) {
                future.completeExceptionally(cf.cause());
                return;
            }
            FullHttpRequest nettyReq = toNettyRequest(request, host, port);
            cf.channel().writeAndFlush(nettyReq).addListener((ChannelFutureListener) wf -> {
                if (!wf.isSuccess()) {
                    future.completeExceptionally(wf.cause());
                    wf.channel().close();
                }
            });
        });
        return future;
    }

    /** Convenience for GET. */
    public CompletableFuture<HttpResponse> get(URI uri) {
        return send(HttpRequest.get(uri));
    }

    /** Convenience for POST with body + content-type. */
    public CompletableFuture<HttpResponse> post(URI uri, byte[] body, String contentType) {
        return send(HttpRequest.post(uri, body, contentType));
    }

    @Override
    public void close() {
        try {
            eventLoop.shutdownGracefully(0, 200, TimeUnit.MILLISECONDS).sync();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private int maxContentLength = DEFAULT_MAX_CONTENT_LENGTH;
        private int connectTimeoutMs = DEFAULT_CONNECT_TIMEOUT_MS;

        public Builder maxContentLength(int bytes) {
            if (bytes < 1) throw new IllegalArgumentException("maxContentLength must be >= 1");
            this.maxContentLength = bytes;
            return this;
        }

        public Builder connectTimeoutMs(int ms) {
            if (ms < 1) throw new IllegalArgumentException("connectTimeoutMs must be >= 1");
            this.connectTimeoutMs = ms;
            return this;
        }

        public HttpClient build() {
            return new HttpClient(this);
        }
    }

    // ==================================================================================
    // Request encoding + response handling.
    // ==================================================================================

    private static FullHttpRequest toNettyRequest(HttpRequest req, String host, int port) {
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
        // Host + Content-Length + Connection are required for HTTP/1.1 compliance.
        String hostHeader = (port == 80) ? host : host + ":" + port;
        nettyReq.headers().set(HttpHeaderNames.HOST, hostHeader);
        nettyReq.headers().setInt(HttpHeaderNames.CONTENT_LENGTH, req.bodyLength());
        nettyReq.headers().set(HttpHeaderNames.CONNECTION, "close");
        return nettyReq;
    }

    private static io.netty.handler.codec.http.HttpMethod mapMethod(HttpMethod m) {
        return switch (m) {
            case GET    -> io.netty.handler.codec.http.HttpMethod.GET;
            case POST   -> io.netty.handler.codec.http.HttpMethod.POST;
            case PUT    -> io.netty.handler.codec.http.HttpMethod.PUT;
            case DELETE -> io.netty.handler.codec.http.HttpMethod.DELETE;
        };
    }

    private static final class ResponseHandler extends SimpleChannelInboundHandler<FullHttpResponse> {

        private final CompletableFuture<HttpResponse> future;

        ResponseHandler(CompletableFuture<HttpResponse> future) {
            this.future = future;
        }

        @Override
        protected void channelRead0(ChannelHandlerContext ctx, FullHttpResponse nettyResp) {
            HttpHeaders headers = new HttpHeaders();
            for (Map.Entry<String, String> h : nettyResp.headers()) {
                headers.add(h.getKey(), h.getValue());
            }
            ByteBuf content = nettyResp.content();
            byte[] body = new byte[content.readableBytes()];
            content.readBytes(body);

            HttpResponse out = new HttpResponse(nettyResp.status().code(), headers, body);
            future.complete(out);
            ctx.close();
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            if (!future.isDone()) future.completeExceptionally(cause);
            ctx.close();
        }

        @Override
        public void channelInactive(ChannelHandlerContext ctx) {
            if (!future.isDone()) {
                future.completeExceptionally(new java.io.IOException(
                        "Connection closed before response was received"));
            }
        }
    }
}
