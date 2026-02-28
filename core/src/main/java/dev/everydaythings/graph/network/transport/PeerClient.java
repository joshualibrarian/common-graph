package dev.everydaythings.graph.network.transport;

import dev.everydaythings.graph.network.message.ProtocolMessage;
import dev.everydaythings.graph.network.peer.PeerConnection;
import dev.everydaythings.graph.value.Endpoint;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import io.netty.handler.codec.LengthFieldPrepender;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslHandler;
import io.netty.handler.timeout.IdleStateHandler;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.net.InetAddress;
import java.security.cert.X509Certificate;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Network client for connecting to peer librarians.
 *
 * <p>Supports optional TLS for encrypted, authenticated connections.
 *
 * <p>Pipeline order:
 * <ol>
 *   <li>SSL (optional) — TLS encryption</li>
 *   <li>Frame decoder/encoder — length-prefixed framing</li>
 *   <li>CgProtocolCodec — CBOR ↔ ProtocolMessage conversion</li>
 *   <li>IdleStateHandler — idle detection</li>
 *   <li>HeartbeatHandler — heartbeat/dead-peer handling</li>
 *   <li>ClientChannelHandler — application message dispatch</li>
 * </ol>
 */
public class PeerClient implements AutoCloseable {

    private static final Logger log = LogManager.getLogger(PeerClient.class);

    private final EventLoopGroup group;
    private final MessageHandler handler;
    private final SslContext sslContext;

    /**
     * Create a client without TLS (for testing).
     */
    public PeerClient(MessageHandler handler) {
        this(handler, (SslContext) null);
    }

    /**
     * Create a client with TLS.
     */
    public PeerClient(MessageHandler handler, SslContext sslContext) {
        this.group = TransportDetector.newEventLoopGroup();
        this.handler = handler;
        this.sslContext = sslContext;
    }

    /**
     * Connect to a peer at the given endpoint.
     */
    public CompletableFuture<PeerConnection> connect(Endpoint endpoint) {
        return connect(endpoint.host().toInetAddress(), endpoint.port());
    }

    /**
     * Connect to a peer at the given address.
     */
    public CompletableFuture<PeerConnection> connect(InetAddress host, int port) {
        CompletableFuture<PeerConnection> future = new CompletableFuture<>();

        Bootstrap bootstrap = new Bootstrap();
        bootstrap.group(group)
                .channel(TransportDetector.socketChannelClass())
                .handler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) {
                        ChannelPipeline p = ch.pipeline();

                        // 1. TLS (if enabled)
                        if (sslContext != null) {
                            p.addLast("ssl", sslContext.newHandler(ch.alloc(), host.getHostAddress(), port));
                        }

                        // 2. Length-prefixed framing: 4-byte length header, max 16MB
                        p.addLast("frameDecoder", new LengthFieldBasedFrameDecoder(16 * 1024 * 1024, 0, 4, 0, 4));
                        p.addLast("frameEncoder", new LengthFieldPrepender(4));

                        // 3. CG Protocol codec (ByteBuf ↔ ProtocolMessage)
                        p.addLast("codec", new CgProtocolCodec());

                        // 4. Idle detection: reader=30s, writer=15s
                        p.addLast("idle", new IdleStateHandler(30, 15, 0, TimeUnit.SECONDS));

                        // 5. Heartbeat / dead-peer handler
                        p.addLast("heartbeat", new HeartbeatHandler());

                        // 6. Application handler
                        p.addLast("handler", new ClientChannelHandler(handler, future, sslContext != null));
                    }
                })
                .option(ChannelOption.SO_KEEPALIVE, true)
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 10000);

        bootstrap.connect(host, port).addListener((ChannelFuture f) -> {
            if (!f.isSuccess()) {
                log.warn("Failed to connect to {}:{}: {}", host, port, f.cause().getMessage());
                future.completeExceptionally(f.cause());
            }
            // Success case is handled in channelActive
        });

        return future;
    }

    /**
     * Check if TLS is enabled.
     */
    public boolean isTlsEnabled() {
        return sslContext != null;
    }

    /**
     * Shut down the client and release resources.
     */
    @Override
    public void close() {
        group.shutdownGracefully();
    }

    /**
     * Handler for messages from the server.
     */
    public interface MessageHandler {
        /**
         * Called when connected to a peer.
         *
         * @param connection The connection
         * @param peerCert   The peer's certificate (if TLS), or null
         */
        void onConnect(PeerConnection connection, X509Certificate peerCert);

        /**
         * Called when a typed protocol message is received from the server.
         */
        void onMessage(PeerConnection connection, ProtocolMessage message);

        /**
         * Called when the connection is closed.
         */
        void onDisconnect(PeerConnection connection);
    }

    /**
     * Netty handler that bridges to our MessageHandler.
     */
    private static class ClientChannelHandler extends ChannelInboundHandlerAdapter {
        private final MessageHandler handler;
        private final CompletableFuture<PeerConnection> connectFuture;
        private final boolean tlsEnabled;
        private PeerConnection connection;

        ClientChannelHandler(MessageHandler handler, CompletableFuture<PeerConnection> connectFuture, boolean tlsEnabled) {
            this.handler = handler;
            this.connectFuture = connectFuture;
            this.tlsEnabled = tlsEnabled;
        }

        @Override
        public void channelActive(ChannelHandlerContext ctx) {
            connection = new PeerConnection(ctx.channel());
            log.debug("Connected to peer: {} (TLS: {})", connection.remoteAddress(), tlsEnabled);

            if (tlsEnabled) {
                // Defer until TLS handshake completes — channelActive fires on TCP connect,
                // before TLS negotiation finishes. Certificate extraction would return null
                // and early writes would race with the handshake.
                SslHandler sslHandler = ctx.pipeline().get(SslHandler.class);
                sslHandler.handshakeFuture().addListener(future -> {
                    if (future.isSuccess()) {
                        X509Certificate peerCert = extractPeerCertificate(ctx);
                        connectFuture.complete(connection);
                        handler.onConnect(connection, peerCert);
                    } else {
                        log.warn("TLS handshake failed to {}: {}",
                                connection.remoteAddress(), future.cause().getMessage());
                        connectFuture.completeExceptionally(future.cause());
                        ctx.close();
                    }
                });
            } else {
                connectFuture.complete(connection);
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
            log.debug("Disconnected from peer: {}", connection.remoteAddress());
            handler.onDisconnect(connection);
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            log.warn("Connection error to {}: {}",
                    connection != null ? connection.remoteAddress() : "unknown",
                    cause.getMessage());
            if (!connectFuture.isDone()) {
                connectFuture.completeExceptionally(cause);
            }
            ctx.close();
        }
    }
}
