package dev.everydaythings.graph.network.transport;

import dev.everydaythings.graph.network.message.ProtocolMessage;
import dev.everydaythings.graph.network.peer.PeerConnection;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import io.netty.handler.codec.LengthFieldPrepender;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslHandler;
import io.netty.handler.timeout.IdleStateHandler;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.net.InetSocketAddress;
import java.security.cert.X509Certificate;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Network server for accepting peer connections.
 *
 * <p>Supports optional TLS for encrypted, authenticated connections.
 * When TLS is enabled, the server presents its certificate and can
 * optionally require client certificates (mutual TLS).
 *
 * <p>Pipeline order:
 * <ol>
 *   <li>SSL (optional) — TLS encryption</li>
 *   <li>Frame decoder/encoder — length-prefixed framing</li>
 *   <li>CgProtocolCodec — CBOR ↔ ProtocolMessage conversion</li>
 *   <li>IdleStateHandler — idle detection</li>
 *   <li>HeartbeatHandler — heartbeat/dead-peer handling</li>
 *   <li>ServerChannelHandler — application message dispatch</li>
 * </ol>
 */
public class PeerServer implements AutoCloseable {

    private static final Logger log = LogManager.getLogger(PeerServer.class);

    private final int port;
    private final IncomingHandler handler;
    private final SslContext sslContext;

    private EventLoopGroup bossGroup;
    private EventLoopGroup workerGroup;
    private Channel serverChannel;

    /**
     * Create a server without TLS (for testing).
     */
    public PeerServer(int port, IncomingHandler handler) {
        this(port, handler, null);
    }

    /**
     * Create a server with TLS.
     */
    public PeerServer(int port, IncomingHandler handler, SslContext sslContext) {
        this.port = port;
        this.handler = handler;
        this.sslContext = sslContext;
    }

    /**
     * Start the server and return when it's bound and ready.
     */
    public CompletableFuture<InetSocketAddress> start() {
        CompletableFuture<InetSocketAddress> future = new CompletableFuture<>();

        bossGroup = TransportDetector.newEventLoopGroup(1);
        workerGroup = TransportDetector.newEventLoopGroup();

        ServerBootstrap bootstrap = new ServerBootstrap();
        bootstrap.group(bossGroup, workerGroup)
                .channel(TransportDetector.serverChannelClass())
                .childHandler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) {
                        ChannelPipeline p = ch.pipeline();

                        // 1. TLS (if enabled)
                        if (sslContext != null) {
                            p.addLast("ssl", sslContext.newHandler(ch.alloc()));
                        }

                        // 2. Length-prefixed framing: 4-byte length header, max 16MB message
                        p.addLast("frameDecoder", new LengthFieldBasedFrameDecoder(16 * 1024 * 1024, 0, 4, 0, 4));
                        p.addLast("frameEncoder", new LengthFieldPrepender(4));

                        // 3. CG Protocol codec (ByteBuf ↔ ProtocolMessage)
                        p.addLast("codec", new CgProtocolCodec());

                        // 4. Idle detection: reader=30s, writer=15s
                        p.addLast("idle", new IdleStateHandler(30, 15, 0, TimeUnit.SECONDS));

                        // 5. Heartbeat / dead-peer handler
                        p.addLast("heartbeat", new HeartbeatHandler());

                        // 6. Application handler
                        p.addLast("handler", new ServerChannelHandler(handler, sslContext != null));
                    }
                })
                .option(ChannelOption.SO_BACKLOG, 128)
                .childOption(ChannelOption.SO_KEEPALIVE, true);

        bootstrap.bind(port).addListener((ChannelFuture f) -> {
            if (f.isSuccess()) {
                serverChannel = f.channel();
                InetSocketAddress addr = (InetSocketAddress) serverChannel.localAddress();
                log.info("PeerServer listening on {} (TLS: {}, transport: {})",
                        addr, sslContext != null, TransportDetector.detected());
                future.complete(addr);
            } else {
                log.error("Failed to bind PeerServer on port {}", port, f.cause());
                future.completeExceptionally(f.cause());
            }
        });

        return future;
    }

    /**
     * Stop the server gracefully.
     */
    @Override
    public void close() {
        log.debug("Shutting down PeerServer");
        if (serverChannel != null) {
            serverChannel.close().syncUninterruptibly();
        }
        if (workerGroup != null) {
            workerGroup.shutdownGracefully();
        }
        if (bossGroup != null) {
            bossGroup.shutdownGracefully();
        }
    }

    /**
     * Get the actual bound address (useful if port was 0).
     */
    public InetSocketAddress boundAddress() {
        return serverChannel != null ? (InetSocketAddress) serverChannel.localAddress() : null;
    }

    /**
     * Check if TLS is enabled.
     */
    public boolean isTlsEnabled() {
        return sslContext != null;
    }

    /**
     * Handler for incoming connections and messages.
     */
    public interface IncomingHandler {
        /**
         * Called when a new peer connects.
         *
         * @param connection The connection
         * @param peerCert   The peer's certificate (if TLS with client auth), or null
         */
        void onConnect(PeerConnection connection, X509Certificate peerCert);

        /**
         * Called when a typed protocol message is received from a peer.
         */
        void onMessage(PeerConnection connection, ProtocolMessage message);

        /**
         * Called when a peer disconnects.
         */
        void onDisconnect(PeerConnection connection);
    }

    /**
     * Netty handler that bridges to our IncomingHandler.
     */
    private static class ServerChannelHandler extends ChannelInboundHandlerAdapter {
        private final IncomingHandler handler;
        private final boolean tlsEnabled;
        private PeerConnection connection;

        ServerChannelHandler(IncomingHandler handler, boolean tlsEnabled) {
            this.handler = handler;
            this.tlsEnabled = tlsEnabled;
        }

        @Override
        public void channelActive(ChannelHandlerContext ctx) {
            connection = new PeerConnection(ctx.channel());
            log.debug("Peer connected: {} (TLS: {})", connection.remoteAddress(), tlsEnabled);

            if (tlsEnabled) {
                // Defer onConnect until the TLS handshake completes — channelActive fires
                // when the TCP connection is established, before TLS negotiation finishes.
                SslHandler sslHandler = ctx.pipeline().get(SslHandler.class);
                sslHandler.handshakeFuture().addListener(future -> {
                    if (future.isSuccess()) {
                        X509Certificate peerCert = extractPeerCertificate(ctx);
                        handler.onConnect(connection, peerCert);
                    } else {
                        log.warn("TLS handshake failed from {}: {}",
                                connection.remoteAddress(), future.cause().getMessage());
                        ctx.close();
                    }
                });
            } else {
                handler.onConnect(connection, null);
            }
        }

        private X509Certificate extractPeerCertificate(ChannelHandlerContext ctx) {
            try {
                SslHandler sslHandler = ctx.pipeline().get(SslHandler.class);
                if (sslHandler != null) {
                    javax.net.ssl.SSLSession session = sslHandler.engine().getSession();
                    java.security.cert.Certificate[] certs = session.getPeerCertificates();
                    if (certs != null && certs.length > 0 && certs[0] instanceof X509Certificate) {
                        return (X509Certificate) certs[0];
                    }
                }
            } catch (Exception e) {
                log.debug("Could not extract peer certificate: {}", e.getMessage());
            }
            return null;
        }

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) {
            if (msg instanceof ProtocolMessage message) {
                handler.onMessage(connection, message);
            }
        }

        @Override
        public void channelInactive(ChannelHandlerContext ctx) {
            log.debug("Peer disconnected: {}", connection.remoteAddress());
            handler.onDisconnect(connection);
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            log.warn("Connection error from {}: {}", connection.remoteAddress(), cause.getMessage());
            ctx.close();
        }
    }
}
