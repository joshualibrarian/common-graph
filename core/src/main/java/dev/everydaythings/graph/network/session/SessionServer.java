package dev.everydaythings.graph.network.session;

import dev.everydaythings.graph.item.Item;
import dev.everydaythings.graph.dispatch.ActionResult;
import dev.everydaythings.graph.item.id.ItemID;
import dev.everydaythings.graph.language.Posting;
import dev.everydaythings.graph.network.Ack;
import dev.everydaythings.graph.network.Heartbeat;
import dev.everydaythings.graph.network.ProtocolCodec;
import dev.everydaythings.graph.network.ProtocolError;
import dev.everydaythings.graph.network.ProtocolMessage;
import dev.everydaythings.graph.network.transport.HeartbeatHandler;
import dev.everydaythings.graph.network.transport.TransportDetector;
import dev.everydaythings.graph.runtime.Librarian;
import dev.everydaythings.graph.ui.scene.surface.primitive.TextSurface;
import dev.everydaythings.graph.ui.scene.View;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import io.netty.handler.codec.LengthFieldPrepender;
import io.netty.handler.timeout.IdleStateHandler;
import lombok.extern.log4j.Log4j2;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Server for the Session Protocol, built on Netty.
 *
 * <p>Accepts connections from TextSession (and embedded terminals) via:
 * <ul>
 *   <li>TCP (localhost or remote with auth)</li>
 *   <li>Unix domain socket support via Netty native transports when available</li>
 * </ul>
 *
 * <p>Uses the same Netty pipeline as the peer transport:
 * length-prefixed framing + ProtocolCodec + HeartbeatHandler.
 */
@Log4j2
public class SessionServer implements AutoCloseable {

    private final Librarian librarian;

    // Netty infrastructure
    private EventLoopGroup bossGroup;
    private EventLoopGroup workerGroup;
    private Channel tcpChannel;
    private Channel unixChannel;

    // Active sessions
    private final Map<Channel, ClientSession> sessions = new ConcurrentHashMap<>();

    // Auth tokens (simple token store for now)
    private final Map<String, TokenInfo> validTokens = new ConcurrentHashMap<>();
    private String localAutoToken;

    // Lifecycle
    private final AtomicBoolean running = new AtomicBoolean(false);

    public SessionServer(Librarian librarian) {
        this.librarian = librarian;
    }

    // =========================================================================
    // Startup
    // =========================================================================

    /**
     * Start listening on a Unix socket.
     */
    public void listenUnix(Path socketPath) throws IOException {
        if (unixChannel != null) {
            throw new IllegalStateException("Unix server already running");
        }

        Files.deleteIfExists(socketPath);
        ensureNettyStarted();

        try {
            ServerBootstrap bootstrap = new ServerBootstrap();
            bootstrap.group(bossGroup, workerGroup)
                    .channel(TransportDetector.serverChannelClass())
                    .childHandler(createChannelInitializer(true))
                    .option(ChannelOption.SO_BACKLOG, 128);

            // Bind to TCP localhost as fallback for Unix socket
            // (Netty domain sockets need native transport; use TCP on localhost for now)
            unixChannel = bootstrap.bind(new InetSocketAddress("127.0.0.1", 0))
                    .syncUninterruptibly().channel();

            logger.info("Session server listening (Unix socket path registered: {})", socketPath);
        } catch (Exception e) {
            throw new IOException("Failed to start Unix session server", e);
        }
    }

    /**
     * Start listening on TCP.
     */
    public void listenTcp(String host, int port) throws IOException {
        if (tcpChannel != null) {
            throw new IllegalStateException("TCP server already running");
        }

        ensureNettyStarted();

        try {
            ServerBootstrap bootstrap = new ServerBootstrap();
            bootstrap.group(bossGroup, workerGroup)
                    .channel(TransportDetector.serverChannelClass())
                    .childHandler(createChannelInitializer(false))
                    .option(ChannelOption.SO_BACKLOG, 128)
                    .childOption(ChannelOption.SO_KEEPALIVE, true);

            tcpChannel = bootstrap.bind(host, port).syncUninterruptibly().channel();
            InetSocketAddress addr = (InetSocketAddress) tcpChannel.localAddress();
            logger.info("Session server listening on TCP {}:{}", addr.getHostString(), addr.getPort());
        } catch (Exception e) {
            throw new IOException("Failed to start TCP session server", e);
        }
    }

    private void ensureNettyStarted() {
        if (running.compareAndSet(false, true)) {
            generateLocalAutoToken();
            bossGroup = TransportDetector.newEventLoopGroup(1);
            workerGroup = TransportDetector.newEventLoopGroup();
        }
    }

    private ChannelInitializer<SocketChannel> createChannelInitializer(boolean isUnix) {
        return new ChannelInitializer<>() {
            @Override
            protected void initChannel(SocketChannel ch) {
                ChannelPipeline p = ch.pipeline();

                // Length-prefixed framing: 4-byte length header, max 16MB
                p.addLast("frameDecoder", new LengthFieldBasedFrameDecoder(16 * 1024 * 1024, 0, 4, 0, 4));
                p.addLast("frameEncoder", new LengthFieldPrepender(4));

                // Protocol codec (ByteBuf ↔ ProtocolMessage)
                p.addLast("codec", new ProtocolCodec());

                // Idle detection: reader=60s, writer=30s
                p.addLast("idle", new IdleStateHandler(60, 30, 0, TimeUnit.SECONDS));

                // Heartbeat handler
                p.addLast("heartbeat", new HeartbeatHandler());

                // Session handler
                p.addLast("session", new SessionChannelHandler(isUnix));
            }
        };
    }

    // =========================================================================
    // Netty Channel Handler
    // =========================================================================

    private class SessionChannelHandler extends ChannelInboundHandlerAdapter {
        private final boolean isUnixSocket;
        private ClientSession session;

        SessionChannelHandler(boolean isUnixSocket) {
            this.isUnixSocket = isUnixSocket;
        }

        @Override
        public void channelActive(ChannelHandlerContext ctx) {
            session = new ClientSession(ctx.channel(), isUnixSocket);
            sessions.put(ctx.channel(), session);

            logger.info("New session connection: {} ({})", session.sessionId, isUnixSocket ? "unix" : "tcp");

            // Send auth challenge
            List<String> methods = isUnixSocket ?
                    List.of("token") :
                    List.of("token", "pairing");

            sendMessage(ctx, new SessionMessage.AuthChallenge(session.authNonce, methods));
            session.challengeSent = true;
        }

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) {
            if (msg instanceof SessionMessage sm) {
                handleSessionMessage(ctx, session, sm);
            } else if (msg instanceof Ack || msg instanceof ProtocolError || msg instanceof Heartbeat) {
                // Handled by pipeline or ignored
            }
        }

        @Override
        public void channelInactive(ChannelHandlerContext ctx) {
            ClientSession removed = sessions.remove(ctx.channel());
            if (removed != null) {
                librarian.unregisterSession(Librarian.SessionInfo.unix("Session " + removed.sessionId));
                logger.info("Session closed: {}", removed.sessionId);
            }
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            logger.warn("Session error from {}: {}", ctx.channel().remoteAddress(), cause.getMessage());
            ctx.close();
        }
    }

    // =========================================================================
    // Message Handling
    // =========================================================================

    private void handleSessionMessage(ChannelHandlerContext ctx, ClientSession session, SessionMessage message) {
        try {
            switch (message) {
                case SessionMessage.AuthToken m         -> handleAuthToken(ctx, session, m);
                case SessionMessage.AuthPrincipal m     -> handleAuthPrincipal(ctx, session, m);
                case SessionMessage.AuthEngage m        -> handleAuthEngage(ctx, session, m);
                case SessionMessage.ContextRequest m    -> handleContext(ctx, session, m);
                case SessionMessage.DispatchRequest m   -> handleDispatch(ctx, session, m);
                case SessionMessage.LookupRequest m     -> handleLookup(ctx, session, m);
                case SessionMessage.SubscribeRequest m  -> handleSubscribe(ctx, session, m);
                default -> logger.warn("Unexpected session message from {}: {}",
                        session.sessionId, message.getClass().getSimpleName());
            }
        } catch (Exception e) {
            logger.error("Error handling {} from session {}", message.getClass().getSimpleName(), session.sessionId, e);
            sendMessage(ctx, new ProtocolError(0, "INTERNAL_ERROR", e.getMessage()));
        }
    }

    private void handleAuthToken(ChannelHandlerContext ctx, ClientSession session, SessionMessage.AuthToken m) {
        if (!session.challengeSent) {
            sendMessage(ctx, SessionMessage.AuthResponse.failure("No challenge sent"));
            return;
        }

        String token = m.token();
        TokenInfo info = verifyToken(token);

        if (info == null) {
            logger.warn("Session {} failed token auth", session.sessionId);
            sendMessage(ctx, SessionMessage.AuthResponse.failure("Invalid token"));
            return;
        }

        session.authenticated = true;

        if (m.principalId() != null) {
            session.principalId = m.principalId();
            logger.info("Session {} principal (explicit): {}", session.sessionId, session.principalId.encodeText());
        } else if (token.equals(localAutoToken)) {
            librarian.principal().ifPresent(p -> {
                session.principalId = p.iid();
                logger.info("Session {} principal (auto): {}", session.sessionId, p.displayToken());
            });
        }

        librarian.registerSession(Librarian.SessionInfo.unix("Session " + session.sessionId));
        if (session.principalId != null) {
            sendMessage(ctx, SessionMessage.AuthResponse.successWithPrincipal(session.sessionId, session.principalId));
        } else {
            sendMessage(ctx, SessionMessage.AuthResponse.success(session.sessionId));
        }
        logger.info("Session {} authenticated via token ({})", session.sessionId, info.description());
    }

    private void handleAuthPrincipal(ChannelHandlerContext ctx, ClientSession session, SessionMessage.AuthPrincipal m) {
        sendMessage(ctx, SessionMessage.AuthResponse.failure("Principal auth not yet implemented"));
    }

    private void handleAuthEngage(ChannelHandlerContext ctx, ClientSession session, SessionMessage.AuthEngage m) {
        if (!session.challengeSent) {
            sendMessage(ctx, SessionMessage.AuthResponse.failure("No challenge sent"));
            return;
        }

        String code = m.inviteCode();
        String name = m.name();

        if (name == null || name.isBlank()) {
            sendMessage(ctx, SessionMessage.AuthResponse.failure("Name required with invite code (--name)"));
            return;
        }

        TokenInfo info = verifyToken(code);
        if (info == null) {
            logger.warn("Session {} failed engage auth with invalid invite code", session.sessionId);
            sendMessage(ctx, SessionMessage.AuthResponse.failure("Invalid or expired invite code"));
            return;
        }

        try {
            var user = dev.everydaythings.graph.item.user.User.create(librarian, name);
            session.authenticated = true;
            session.principalId = user.iid();

            String persistentToken = createPersistentToken("User: " + name + " (" + user.iid().encodeText() + ")");

            librarian.registerSession(Librarian.SessionInfo.unix("Session " + session.sessionId + " (" + name + ")"));
            sendMessage(ctx, SessionMessage.AuthResponse.engaged(session.sessionId, persistentToken, user.iid()));
            logger.info("Session {} engaged as '{}' ({}), issued persistent token",
                    session.sessionId, name, user.iid().encodeText());
        } catch (Exception e) {
            logger.error("Failed to create user '{}' during engage", name, e);
            sendMessage(ctx, SessionMessage.AuthResponse.failure("Failed to create user: " + e.getMessage()));
        }
    }

    private void handleContext(ChannelHandlerContext ctx, ClientSession session, SessionMessage.ContextRequest m) {
        boolean resolve = m.resolve();

        if (m.itemId() == null) {
            Item ctxItem = session.context != null ? session.context : librarian;
            sendMessage(ctx, new SessionMessage.ContextResponse(ctxItem.iid(), ctxItem.displayToken(), null));
            return;
        }

        String itemIdStr = m.itemId().encodeText();

        if (resolve && itemIdStr.startsWith("token:")) {
            String token = itemIdStr.substring(6);
            var tokenDict = librarian.tokenIndex();
            if (tokenDict == null) {
                sendMessage(ctx, new SessionMessage.ContextResponse(null, null, "Token dictionary not available"));
                return;
            }
            List<Posting> postings = tokenDict.lookup(token).limit(1).toList();
            if (postings.isEmpty()) {
                sendMessage(ctx, new SessionMessage.ContextResponse(null, null, "Not found: " + token));
                return;
            }
            ItemID iid = postings.get(0).target();
            session.context = librarian.get(iid, Item.class).orElse(null);
        } else {
            session.context = librarian.get(m.itemId(), Item.class).orElse(null);
        }

        if (session.context == null) {
            sendMessage(ctx, new SessionMessage.ContextResponse(null, null, "Item not found"));
        } else {
            sendMessage(ctx, new SessionMessage.ContextResponse(
                    session.context.iid(), session.context.displayToken(), null));
        }
    }

    private void handleDispatch(ChannelHandlerContext ctx, ClientSession session, SessionMessage.DispatchRequest m) {
        String action = m.action();
        long requestId = m.requestId();
        ItemID caller = m.caller();

        if (session.principalId != null && caller != null && !caller.equals(session.principalId)) {
            sendMessage(ctx, SessionMessage.DispatchResponse.failure(requestId,
                    "Caller " + caller.encodeText() + " does not match authenticated principal"));
            return;
        }

        if (caller == null) {
            caller = session.principalId;
        }

        Item target = session.context != null ? session.context : librarian;

        ActionResult result;
        if (caller != null) {
            result = target.dispatch(caller, action, m.args());
        } else {
            result = target.dispatch(action, m.args());
        }

        if (result.success()) {
            Object value = result.value();
            View view = value instanceof View v ? v :
                    value != null ? View.of(TextSurface.of(value.toString())) : View.empty();
            sendMessage(ctx, SessionMessage.DispatchResponse.success(requestId, view));
            notifySubscribers(target.iid(), target);
        } else {
            sendMessage(ctx, SessionMessage.DispatchResponse.failure(requestId,
                    result.error() != null ? result.error().getMessage() : "Unknown error"));
        }
    }

    private void handleLookup(ChannelHandlerContext ctx, ClientSession session, SessionMessage.LookupRequest m) {
        var tokenDict = librarian.tokenIndex();
        List<Posting> postings = tokenDict != null ?
                tokenDict.lookup(m.query()).limit(m.limit()).toList() :
                List.of();
        sendMessage(ctx, new SessionMessage.LookupResponse(m.requestId(), postings));
    }

    private void handleSubscribe(ChannelHandlerContext ctx, ClientSession session, SessionMessage.SubscribeRequest m) {
        if (m.subscribe()) {
            session.subscriptions.add(m.itemId());
            logger.info("Session {} subscribed to {}", session.sessionId, m.itemId().encodeText());
        } else {
            session.subscriptions.remove(m.itemId());
            logger.info("Session {} unsubscribed from {}", session.sessionId, m.itemId().encodeText());
        }

        sendMessage(ctx, new Ack(0));
    }

    // =========================================================================
    // Subscriber Notification
    // =========================================================================

    private void notifySubscribers(ItemID itemId, Item item) {
        for (var entry : sessions.entrySet()) {
            ClientSession cs = entry.getValue();
            if (cs.subscriptions.contains(itemId)) {
                try {
                    Channel ch = entry.getKey();
                    if (ch.isOpen()) {
                        ch.writeAndFlush(new SessionMessage.EventMessage(itemId, "updated", null));
                    }
                } catch (Exception e) {
                    logger.warn("Failed to notify session {} of update to {}",
                            cs.sessionId, itemId.encodeText(), e);
                }
            }
        }
    }

    // =========================================================================
    // Response Sending
    // =========================================================================

    private void sendMessage(ChannelHandlerContext ctx, ProtocolMessage message) {
        ctx.writeAndFlush(message);
    }

    // =========================================================================
    // Lifecycle
    // =========================================================================

    @Override
    public void close() {
        running.set(false);

        // Close all sessions
        for (Channel channel : sessions.keySet()) {
            try {
                channel.close();
            } catch (Exception e) {
                // ignore
            }
        }
        sessions.clear();

        // Close server channels
        if (tcpChannel != null) {
            tcpChannel.close().syncUninterruptibly();
        }
        if (unixChannel != null) {
            unixChannel.close().syncUninterruptibly();
        }

        // Shut down Netty
        if (workerGroup != null) {
            workerGroup.shutdownGracefully();
        }
        if (bossGroup != null) {
            bossGroup.shutdownGracefully();
        }

        logger.info("Session server stopped");
    }

    // =========================================================================
    // Client Session State
    // =========================================================================

    private static class ClientSession {
        final Channel channel;
        final String sessionId = UUID.randomUUID().toString().substring(0, 8);
        final byte[] authNonce = new byte[32];
        final boolean isUnixSocket;
        boolean authenticated = false;
        boolean challengeSent = false;
        Item context = null;
        ItemID principalId = null;
        final java.util.Set<ItemID> subscriptions = ConcurrentHashMap.newKeySet();

        ClientSession(Channel channel, boolean isUnixSocket) {
            this.channel = channel;
            this.isUnixSocket = isUnixSocket;
            new java.security.SecureRandom().nextBytes(authNonce);
        }
    }

    // =========================================================================
    // Token Management
    // =========================================================================

    public record TokenInfo(
        String token,
        String description,
        Instant createdAt,
        Instant expiresAt,
        boolean oneTime
    ) {
        public boolean isExpired() {
            return expiresAt != null && Instant.now().isAfter(expiresAt);
        }
    }

    private void generateLocalAutoToken() {
        localAutoToken = UUID.randomUUID().toString();
        logger.debug("Generated local auto-token for Unix socket auth");
    }

    public String localAutoToken() {
        return localAutoToken;
    }

    public String createPersistentToken(String description) {
        String token = UUID.randomUUID().toString();
        validTokens.put(token, new TokenInfo(
            token, description, Instant.now(), null, false
        ));
        logger.info("Created persistent token: {} - {}", token.substring(0, 8) + "...", description);
        return token;
    }

    public String createInviteCode() {
        String code = String.format("%06d", new java.security.SecureRandom().nextInt(1000000));
        validTokens.put(code, new TokenInfo(
            code, "Invite code", Instant.now(), Instant.now().plusSeconds(300), true
        ));
        logger.info("Created invite code: {}", code);
        return code;
    }

    public void revokeToken(String token) {
        TokenInfo removed = validTokens.remove(token);
        if (removed != null) {
            logger.info("Revoked token: {}", token.substring(0, Math.min(8, token.length())) + "...");
        }
    }

    public int getTcpPort() {
        if (tcpChannel == null) return -1;
        try {
            InetSocketAddress addr = (InetSocketAddress) tcpChannel.localAddress();
            return addr != null ? addr.getPort() : -1;
        } catch (Exception e) {
            logger.warn("Failed to get TCP port: {}", e.getMessage());
            return -1;
        }
    }

    public boolean hasUnixSocket() {
        return unixChannel != null;
    }

    public boolean hasTcpServer() {
        return tcpChannel != null;
    }

    private TokenInfo verifyToken(String token) {
        if (token.equals(localAutoToken)) {
            return new TokenInfo(token, "Local auto-token", Instant.now(), null, false);
        }

        TokenInfo info = validTokens.get(token);
        if (info == null) return null;
        if (info.isExpired()) {
            validTokens.remove(token);
            return null;
        }
        if (info.oneTime()) {
            validTokens.remove(token);
        }
        return info;
    }
}
