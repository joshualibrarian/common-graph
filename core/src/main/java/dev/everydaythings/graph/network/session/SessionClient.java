package dev.everydaythings.graph.network.session;

import dev.everydaythings.graph.item.id.ItemID;
import dev.everydaythings.graph.language.Posting;
import dev.everydaythings.graph.network.Ack;
import dev.everydaythings.graph.network.Heartbeat;
import dev.everydaythings.graph.network.ProtocolCodec;
import dev.everydaythings.graph.network.ProtocolError;
import dev.everydaythings.graph.network.ProtocolMessage;
import dev.everydaythings.graph.network.transport.HeartbeatHandler;
import dev.everydaythings.graph.network.transport.TransportDetector;
import dev.everydaythings.graph.ui.scene.View;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import io.netty.handler.codec.LengthFieldPrepender;
import io.netty.handler.timeout.IdleStateHandler;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/**
 * Client for the Session Protocol, built on Netty.
 *
 * <p>SessionClient handles the low-level protocol communication with a
 * Session Server (typically hosted by a Librarian). It manages:
 * <ul>
 *   <li>Connection establishment (TCP)</li>
 *   <li>Authentication handshake</li>
 *   <li>Message encoding/decoding (via ProtocolCodec)</li>
 *   <li>Request/response correlation</li>
 * </ul>
 *
 * <p>This is the base class for remote access. Subclasses add domain-specific
 * functionality (e.g., {@code RemoteLibrarian} adds library operations).
 */
public class SessionClient implements AutoCloseable {

    private static final Logger log = LogManager.getLogger(SessionClient.class);

    private final String connectionTarget;

    // Netty infrastructure
    private EventLoopGroup group;
    private Channel channel;
    private final AtomicLong requestIdGenerator = new AtomicLong(1);

    // Session state
    private String sessionId;
    protected ItemID principalId;
    private boolean closed = false;

    // Async response handling
    private volatile CompletableFuture<ProtocolMessage> pendingResponse;
    private volatile Consumer<SessionMessage.EventMessage> eventListener;

    // ==================================================================================
    // Constructors
    // ==================================================================================

    public SessionClient(String host, int port) {
        this.connectionTarget = host + ":" + port;
    }

    public SessionClient(Path socketPath) {
        this.connectionTarget = socketPath.toString();
    }

    public SessionClient(String target) {
        this.connectionTarget = target;
    }

    // ==================================================================================
    // Connection
    // ==================================================================================

    public void connect() throws IOException {
        if (channel != null && channel.isOpen()) {
            return;
        }

        group = TransportDetector.newEventLoopGroup();

        String host;
        int port;
        if (connectionTarget.contains(":") && !connectionTarget.startsWith("/")) {
            String[] parts = connectionTarget.split(":", 2);
            host = parts[0];
            port = Integer.parseInt(parts[1]);
        } else {
            // Unix socket path — connect via TCP to localhost (fallback)
            host = "127.0.0.1";
            port = 0; // Will need to resolve from socket path
            // For true Unix socket support, Netty native transport is needed.
            // For now, read the TCP port from the socket path's sibling file.
            throw new IOException("Direct Unix socket not supported in Netty client; use TCP endpoint");
        }

        CompletableFuture<Void> connectFuture = new CompletableFuture<>();

        Bootstrap bootstrap = new Bootstrap();
        bootstrap.group(group)
                .channel(TransportDetector.socketChannelClass())
                .handler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) {
                        ChannelPipeline p = ch.pipeline();
                        p.addLast("frameDecoder", new LengthFieldBasedFrameDecoder(16 * 1024 * 1024, 0, 4, 0, 4));
                        p.addLast("frameEncoder", new LengthFieldPrepender(4));
                        p.addLast("codec", new ProtocolCodec());
                        p.addLast("idle", new IdleStateHandler(60, 30, 0, TimeUnit.SECONDS));
                        p.addLast("heartbeat", new HeartbeatHandler());
                        p.addLast("handler", new ClientHandler());
                    }
                })
                .option(ChannelOption.SO_KEEPALIVE, true)
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 10000);

        bootstrap.connect(host, port).addListener((ChannelFuture f) -> {
            if (f.isSuccess()) {
                channel = f.channel();
                log.info("Connected to server at TCP {}:{}", host, port);
                connectFuture.complete(null);
            } else {
                connectFuture.completeExceptionally(f.cause());
            }
        });

        try {
            connectFuture.get(10, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new IOException("Failed to connect to " + connectionTarget, e);
        }
    }

    /**
     * Netty handler for incoming messages.
     */
    private class ClientHandler extends ChannelInboundHandlerAdapter {
        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) {
            if (msg instanceof SessionMessage.EventMessage event) {
                Consumer<SessionMessage.EventMessage> listener = eventListener;
                if (listener != null) {
                    try {
                        listener.accept(event);
                    } catch (Exception e) {
                        log.warn("Event listener error", e);
                    }
                }
            } else if (msg instanceof ProtocolMessage pm) {
                CompletableFuture<ProtocolMessage> future = pendingResponse;
                if (future != null) {
                    pendingResponse = null;
                    future.complete(pm);
                } else if (!(msg instanceof Heartbeat)) {
                    log.warn("Received message with no pending request: {}", msg.getClass().getSimpleName());
                }
            }
        }

        @Override
        public void channelInactive(ChannelHandlerContext ctx) {
            log.debug("Connection closed");
            CompletableFuture<ProtocolMessage> future = pendingResponse;
            if (future != null) {
                pendingResponse = null;
                future.completeExceptionally(new IOException("Connection closed"));
            }
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            log.warn("Connection error: {}", cause.getMessage());
            CompletableFuture<ProtocolMessage> future = pendingResponse;
            if (future != null) {
                pendingResponse = null;
                future.completeExceptionally(cause);
            }
            ctx.close();
        }
    }

    // ==================================================================================
    // Authentication
    // ==================================================================================

    public void authenticate(String token) throws IOException {
        ensureConnected();

        log.debug("Waiting for AUTH_CHALLENGE from server...");
        ProtocolMessage challenge = receiveMessage();

        if (!(challenge instanceof SessionMessage.AuthChallenge authChallenge)) {
            throw new IOException("Expected AUTH_CHALLENGE, got: " + challenge.getClass().getSimpleName());
        }

        log.debug("Received auth challenge, methods: {}", authChallenge.acceptedMethods());

        if (token == null || token.isBlank() || "auto".equalsIgnoreCase(token)) {
            token = readLocalAutoToken();
        }

        if (token == null) {
            throw new IOException("No auth token available. Provide a token or ensure server is running locally.");
        }

        sendMessage(new SessionMessage.AuthToken(token, principalId));

        ProtocolMessage response = receiveMessage();
        if (response instanceof SessionMessage.AuthResponse authResponse) {
            if (authResponse.success()) {
                this.sessionId = authResponse.sessionId();
                if (authResponse.principalId() != null) {
                    this.principalId = authResponse.principalId();
                }
                log.info("Authenticated successfully, session: {}, principal: {}",
                        sessionId, principalId != null ? principalId.encodeText() : "(none)");
            } else {
                throw new IOException("Authentication failed: " + authResponse.error());
            }
        } else {
            throw new IOException("Expected AUTH response, got: " + response.getClass().getSimpleName());
        }
    }

    public void connectAndAuthenticate(String token) throws IOException {
        connect();
        authenticate(token);
    }

    public void connectAndEngage(String inviteCode, String name) throws IOException {
        connect();

        log.debug("Waiting for AUTH_CHALLENGE from server...");
        ProtocolMessage challenge = receiveMessage();

        if (!(challenge instanceof SessionMessage.AuthChallenge authChallenge)) {
            throw new IOException("Expected AUTH_CHALLENGE, got: " + challenge.getClass().getSimpleName());
        }

        log.debug("Received auth challenge, methods: {}", authChallenge.acceptedMethods());

        sendMessage(new SessionMessage.AuthEngage(inviteCode, name));

        ProtocolMessage response = receiveMessage();
        if (response instanceof SessionMessage.AuthResponse authResponse) {
            if (authResponse.success()) {
                this.sessionId = authResponse.sessionId();
                if (authResponse.principalId() != null) {
                    this.principalId = authResponse.principalId();
                }
                log.info("Engaged as '{}', session: {}, principal: {}",
                        name, sessionId, principalId != null ? principalId.encodeText() : "(none)");
            } else {
                throw new IOException("Engage failed: " + authResponse.error());
            }
        } else {
            throw new IOException("Expected AUTH response, got: " + response.getClass().getSimpleName());
        }
    }

    protected String readLocalAutoToken() {
        String envToken = System.getenv("GRAPH_SESSION_TOKEN");
        if (envToken != null && !envToken.isBlank()) {
            log.debug("Using token from GRAPH_SESSION_TOKEN environment variable");
            return envToken;
        }

        try {
            Path tokenPath;
            if (connectionTarget.endsWith(".sock")) {
                tokenPath = Path.of(connectionTarget).resolveSibling("session.token");
            } else if (!connectionTarget.contains(":")) {
                tokenPath = Path.of(connectionTarget).resolveSibling("session.token");
            } else {
                tokenPath = Path.of(System.getProperty("user.home"), ".librarian", "session.token");
            }

            if (Files.exists(tokenPath)) {
                String tokenVal = Files.readString(tokenPath).trim();
                log.debug("Read auto-token from {}", tokenPath);
                return tokenVal;
            }
        } catch (Exception e) {
            log.debug("Could not read local auto-token: {}", e.getMessage());
        }
        return null;
    }

    protected void ensureConnected() throws IOException {
        if (channel == null || !channel.isOpen()) {
            connect();
        }
        if (sessionId == null) {
            authenticate(null);
        }
    }

    // ==================================================================================
    // Session Operations
    // ==================================================================================

    public SessionMessage.ContextResponse setContext(ItemID itemId) throws IOException {
        ensureConnected();
        sendMessage(SessionMessage.ContextRequest.set(itemId));
        ProtocolMessage response = receiveMessage();
        if (response instanceof SessionMessage.ContextResponse cr) return cr;
        throw new IOException("Expected CONTEXT response, got: " + response.getClass().getSimpleName());
    }

    public SessionMessage.ContextResponse getContext() throws IOException {
        ensureConnected();
        sendMessage(SessionMessage.ContextRequest.get());
        ProtocolMessage response = receiveMessage();
        if (response instanceof SessionMessage.ContextResponse cr) return cr;
        throw new IOException("Expected CONTEXT response, got: " + response.getClass().getSimpleName());
    }

    public SessionMessage.DispatchResponse sendDispatch(String action, List<String> args) throws IOException {
        return sendDispatch(action, args, principalId);
    }

    public SessionMessage.DispatchResponse sendDispatch(String action, List<String> args, ItemID caller) throws IOException {
        ensureConnected();
        long requestId = requestIdGenerator.getAndIncrement();
        SessionMessage.DispatchRequest request = new SessionMessage.DispatchRequest(action, args, requestId, caller);
        ProtocolMessage response = sendAndReceive(request);
        if (response instanceof SessionMessage.DispatchResponse dr) return dr;
        if (response instanceof ProtocolError er) {
            return SessionMessage.DispatchResponse.failure(requestId, er.code() + ": " + er.message());
        }
        throw new IOException("Expected DISPATCH response, got: " + response.getClass().getSimpleName());
    }

    public List<Posting> lookup(String query, int limit) throws IOException {
        ensureConnected();
        long requestId = requestIdGenerator.getAndIncrement();
        sendMessage(new SessionMessage.LookupRequest(query, limit, requestId));
        ProtocolMessage response = receiveMessage();
        if (response instanceof SessionMessage.LookupResponse lr) return lr.postings();
        log.warn("Unexpected lookup response: {}", response.getClass().getSimpleName());
        return List.of();
    }

    // ==================================================================================
    // Accessors
    // ==================================================================================

    public String connectionTarget() { return connectionTarget; }
    public String sessionId() { return sessionId; }

    public boolean isAuthenticated() {
        return sessionId != null && channel != null && channel.isOpen();
    }

    public boolean isOpen() {
        return !closed && (channel == null || channel.isOpen());
    }

    // ==================================================================================
    // Async Events
    // ==================================================================================

    /**
     * Start an async reader thread that routes incoming messages.
     *
     * <p>With the Netty-based client, async reading is always active via the
     * pipeline. This method is a no-op but kept for API compatibility.
     * Incoming EVENT messages are always delivered to the event listener.
     */
    public void startAsyncReader() {
        // No-op: Netty pipeline handles async reads automatically.
        // Event messages are routed to the listener in ClientHandler.channelRead.
    }

    public void onEvent(Consumer<SessionMessage.EventMessage> listener) {
        this.eventListener = listener;
    }

    public void sendSubscribe(ItemID itemId) throws IOException {
        sendMessage(new SessionMessage.SubscribeRequest(itemId, true));
        // Consume the ACK
        receiveMessage();
    }

    // ==================================================================================
    // Wire Protocol
    // ==================================================================================

    protected void sendMessage(ProtocolMessage message) throws IOException {
        if (channel == null || !channel.isOpen()) {
            throw new IOException("Not connected");
        }
        channel.writeAndFlush(message).syncUninterruptibly();
    }

    protected ProtocolMessage receiveMessage() throws IOException {
        CompletableFuture<ProtocolMessage> future = new CompletableFuture<>();
        pendingResponse = future;
        try {
            return future.get(30, TimeUnit.SECONDS);
        } catch (Exception e) {
            pendingResponse = null;
            throw new IOException("Timeout or error waiting for response", e);
        }
    }

    protected ProtocolMessage sendAndReceive(ProtocolMessage request) throws IOException {
        CompletableFuture<ProtocolMessage> future = new CompletableFuture<>();
        pendingResponse = future;
        sendMessage(request);
        try {
            return future.get(30, TimeUnit.SECONDS);
        } catch (Exception e) {
            pendingResponse = null;
            throw new IOException("Timeout or error waiting for response", e);
        }
    }

    // ==================================================================================
    // Lifecycle
    // ==================================================================================

    @Override
    public void close() {
        if (!closed) {
            closed = true;
            if (channel != null) {
                try {
                    channel.close().syncUninterruptibly();
                } catch (Exception e) {
                    log.warn("Error closing channel", e);
                }
                channel = null;
            }
            if (group != null) {
                group.shutdownGracefully();
                group = null;
            }
            log.debug("SessionClient closed");
        }
    }

    protected void checkOpen() {
        if (closed) {
            throw new IllegalStateException("SessionClient is closed");
        }
    }

    @Override
    public String toString() {
        return "SessionClient[" + connectionTarget + (sessionId != null ? ", session=" + sessionId : "") + "]";
    }
}
