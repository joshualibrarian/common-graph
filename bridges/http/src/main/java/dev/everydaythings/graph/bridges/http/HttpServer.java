package dev.everydaythings.graph.bridges.http;

import dev.everydaythings.graph.network.IpAddress;
import dev.everydaythings.graph.value.TcpEndpoint;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.http.HttpUtil;
import io.netty.handler.codec.http.HttpVersion;
import lombok.extern.log4j.Log4j2;

import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Netty-backed HTTP/1.1 server.  Binds to a {@link TcpEndpoint}, routes
 * incoming requests through an {@link HttpRouter}, sends responses back.
 *
 * <h2>Threading</h2>
 *
 * <p>One boss group (single-threaded accept loop) and one worker group
 * (default Netty sizing).  Handlers run on the worker thread that
 * delivered the request; if a handler returns a not-yet-complete
 * {@link CompletableFuture}, the response write is scheduled onto the
 * channel's event loop when the future completes.
 *
 * <h2>Body limits</h2>
 *
 * <p>{@link HttpObjectAggregator} caps a single request body at 16 MiB.
 * Bridges with larger payload needs can raise the cap via
 * {@link Builder#maxContentLength}.
 *
 * <h2>Lifecycle</h2>
 *
 * <p>Construct via {@link Builder}, then call {@link #start} (synchronous
 * bind) which returns the address actually bound — relevant when the
 * caller passed port 0.  {@link #close} shuts the server down gracefully,
 * draining in-flight requests up to a short deadline.
 */
@Log4j2
public final class HttpServer implements AutoCloseable {

    public static final int DEFAULT_MAX_CONTENT_LENGTH = 16 * 1024 * 1024;

    private final HttpRouter router;
    private final TcpEndpoint bindEndpoint;
    private final int maxContentLength;

    private final EventLoopGroup bossGroup;
    private final EventLoopGroup workerGroup;

    private Channel serverChannel;
    private TcpEndpoint actualEndpoint;

    private HttpServer(Builder b) {
        this.router = b.router;
        this.bindEndpoint = b.bindEndpoint;
        this.maxContentLength = b.maxContentLength;
        this.bossGroup = new NioEventLoopGroup(1);
        this.workerGroup = new NioEventLoopGroup();
    }

    /** Bind the server, return the address actually bound. */
    public TcpEndpoint start() {
        ServerBootstrap bootstrap = new ServerBootstrap()
                .group(bossGroup, workerGroup)
                .channel(NioServerSocketChannel.class)
                .childOption(ChannelOption.TCP_NODELAY, true)
                .childHandler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) {
                        ch.pipeline().addLast(new HttpServerCodec());
                        ch.pipeline().addLast(new HttpObjectAggregator(maxContentLength));
                        ch.pipeline().addLast(new RequestHandler(router));
                    }
                });
        try {
            serverChannel = bootstrap.bind(
                    new InetSocketAddress(
                            bindEndpoint.host().toInetAddress(),
                            bindEndpoint.port()))
                    .sync().channel();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("HttpServer.start interrupted", e);
        }
        InetSocketAddress local = (InetSocketAddress) serverChannel.localAddress();
        this.actualEndpoint = TcpEndpoint.of(
                IpAddress.fromInetAddress(local.getAddress()),
                local.getPort());
        return actualEndpoint;
    }

    /** The endpoint actually bound — relevant for ephemeral-port (port 0) binds. */
    public TcpEndpoint actualEndpoint() {
        if (actualEndpoint == null) {
            throw new IllegalStateException("HttpServer is not started");
        }
        return actualEndpoint;
    }

    @Override
    public void close() {
        try {
            if (serverChannel != null) serverChannel.close().sync();
            bossGroup.shutdownGracefully(0, 200, TimeUnit.MILLISECONDS).sync();
            workerGroup.shutdownGracefully(0, 200, TimeUnit.MILLISECONDS).sync();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private HttpRouter router;
        private TcpEndpoint bindEndpoint;
        private int maxContentLength = DEFAULT_MAX_CONTENT_LENGTH;

        public Builder router(HttpRouter router) {
            this.router = router;
            return this;
        }

        public Builder bind(TcpEndpoint endpoint) {
            this.bindEndpoint = endpoint;
            return this;
        }

        public Builder maxContentLength(int bytes) {
            if (bytes < 1) throw new IllegalArgumentException("maxContentLength must be >= 1");
            this.maxContentLength = bytes;
            return this;
        }

        public HttpServer build() {
            Objects.requireNonNull(router, "router");
            Objects.requireNonNull(bindEndpoint, "bindEndpoint");
            return new HttpServer(this);
        }
    }

    // ==================================================================================
    // Netty handler — translates FullHttpRequest → our HttpRequest, dispatches to
    // the router, writes the resulting HttpResponse back as FullHttpResponse.
    // ==================================================================================

    private static final class RequestHandler extends SimpleChannelInboundHandler<FullHttpRequest> {

        private final HttpRouter router;

        RequestHandler(HttpRouter router) {
            this.router = router;
        }

        @Override
        protected void channelRead0(ChannelHandlerContext ctx, FullHttpRequest nettyReq) {
            HttpRequest request = toHttpRequest(nettyReq);
            boolean keepAlive = HttpUtil.isKeepAlive(nettyReq);

            CompletableFuture<HttpResponse> futureResp;
            try {
                futureResp = router.dispatch(request);
            } catch (RuntimeException e) {
                futureResp = CompletableFuture.failedFuture(e);
            }

            futureResp.whenComplete((resp, err) -> {
                FullHttpResponse nettyResp;
                if (err != null) {
                    logger.warn("HTTP handler failed for {} {}: {}",
                            request.method(), request.uri().getRawPath(), err.toString());
                    nettyResp = buildErrorResponse();
                } else if (resp == null) {
                    nettyResp = buildErrorResponse();
                } else {
                    nettyResp = toNettyResponse(resp);
                }
                if (keepAlive && nettyResp.status().code() < 500) {
                    nettyResp.headers().set(HttpHeaderNames.CONNECTION, "keep-alive");
                    ctx.writeAndFlush(nettyResp);
                } else {
                    nettyResp.headers().set(HttpHeaderNames.CONNECTION, "close");
                    ctx.writeAndFlush(nettyResp).addListener(ChannelFutureListener.CLOSE);
                }
            });
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            logger.warn("HTTP pipeline exception on {}", ctx.channel(), cause);
            ctx.close();
        }

        private static HttpRequest toHttpRequest(FullHttpRequest nettyReq) {
            HttpMethod method = mapMethod(nettyReq.method().name());
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

        private static FullHttpResponse toNettyResponse(HttpResponse resp) {
            ByteBuf buf = Unpooled.wrappedBuffer(resp.bodyRaw());
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

        private static FullHttpResponse buildErrorResponse() {
            return new DefaultFullHttpResponse(
                    HttpVersion.HTTP_1_1,
                    HttpResponseStatus.INTERNAL_SERVER_ERROR,
                    Unpooled.EMPTY_BUFFER);
        }

        private static HttpMethod mapMethod(String name) {
            return switch (name) {
                case "GET" -> HttpMethod.GET;
                case "POST" -> HttpMethod.POST;
                case "PUT" -> HttpMethod.PUT;
                case "DELETE" -> HttpMethod.DELETE;
                default -> throw new IllegalArgumentException("Unsupported HTTP method: " + name);
            };
        }
    }
}
