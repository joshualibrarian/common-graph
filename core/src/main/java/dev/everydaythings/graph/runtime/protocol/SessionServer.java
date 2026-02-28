package dev.everydaythings.graph.runtime.protocol;

import dev.everydaythings.graph.item.Item;
import dev.everydaythings.graph.item.action.ActionResult;
import dev.everydaythings.graph.item.id.ItemID;
import dev.everydaythings.graph.language.Posting;
import dev.everydaythings.graph.runtime.Librarian;
import dev.everydaythings.graph.ui.scene.surface.primitive.TextSurface;
import dev.everydaythings.graph.ui.scene.View;
import lombok.extern.log4j.Log4j2;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.StandardProtocolFamily;
import java.net.UnixDomainSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Server for the Session Protocol.
 *
 * <p>Accepts connections from TextSession (and embedded terminals) via:
 * <ul>
 *   <li>Unix domain socket (local, fast, secure)</li>
 *   <li>TCP (localhost or remote with auth)</li>
 * </ul>
 *
 * <p>Each connection becomes a session with:
 * <ul>
 *   <li>Optional authentication (principal)</li>
 *   <li>Context item (defaults to librarian)</li>
 *   <li>Dispatch capability</li>
 *   <li>Lookup/completion</li>
 *   <li>Subscriptions</li>
 * </ul>
 */
@Log4j2
public class SessionServer implements AutoCloseable {

    private final Librarian librarian;
    private final SessionCodec codec = new SessionCodec();

    // Server channels
    private ServerSocketChannel unixServer;
    private ServerSocketChannel tcpServer;
    private Selector selector;

    // Active sessions
    private final Map<SocketChannel, ClientSession> sessions = new ConcurrentHashMap<>();

    // Auth tokens (simple token store for now)
    private final Map<String, TokenInfo> validTokens = new ConcurrentHashMap<>();
    private String localAutoToken;  // Auto-generated token for Unix socket connections

    // Lifecycle
    private final AtomicBoolean running = new AtomicBoolean(false);
    private Thread acceptThread;
    private ExecutorService workerPool;

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
        if (unixServer != null) {
            throw new IllegalStateException("Unix server already running");
        }

        // Delete existing socket file
        Files.deleteIfExists(socketPath);

        unixServer = ServerSocketChannel.open(StandardProtocolFamily.UNIX);
        unixServer.bind(UnixDomainSocketAddress.of(socketPath));
        unixServer.configureBlocking(false);

        logger.info("Session server listening on Unix socket: {}", socketPath);
        ensureRunning();
    }

    /**
     * Start listening on TCP.
     */
    public void listenTcp(String host, int port) throws IOException {
        if (tcpServer != null) {
            throw new IllegalStateException("TCP server already running");
        }

        tcpServer = ServerSocketChannel.open();
        tcpServer.bind(new InetSocketAddress(host, port));
        tcpServer.configureBlocking(false);

        logger.info("Session server listening on TCP {}:{}", host, port);
        ensureRunning();
    }

    private void ensureRunning() throws IOException {
        if (running.compareAndSet(false, true)) {
            // Generate local auto-token for Unix socket auth
            generateLocalAutoToken();

            selector = Selector.open();
            workerPool = Executors.newCachedThreadPool(r -> {
                Thread t = new Thread(r, "session-worker");
                t.setDaemon(true);
                return t;
            });

            // Register server channels
            if (unixServer != null) {
                unixServer.register(selector, SelectionKey.OP_ACCEPT);
            }
            if (tcpServer != null) {
                tcpServer.register(selector, SelectionKey.OP_ACCEPT);
            }

            // Start accept loop
            acceptThread = new Thread(this::acceptLoop, "session-server");
            acceptThread.setDaemon(true);
            acceptThread.start();
        }
    }

    // =========================================================================
    // Accept Loop
    // =========================================================================

    private void acceptLoop() {
        logger.debug("Session server accept loop started");

        while (running.get()) {
            try {
                int ready = selector.select(1000);
                if (ready == 0) continue;

                Iterator<SelectionKey> keys = selector.selectedKeys().iterator();
                while (keys.hasNext()) {
                    SelectionKey key = keys.next();
                    keys.remove();

                    if (!key.isValid()) continue;

                    if (key.isAcceptable()) {
                        accept(key);
                    } else if (key.isReadable()) {
                        read(key);
                    }
                }
            } catch (ClosedSelectorException e) {
                break;
            } catch (Exception e) {
                logger.warn("Error in accept loop", e);
            }
        }

        logger.debug("Session server accept loop stopped");
    }

    private void accept(SelectionKey key) throws IOException {
        ServerSocketChannel server = (ServerSocketChannel) key.channel();
        SocketChannel client = server.accept();
        if (client == null) return;

        client.configureBlocking(false);
        client.register(selector, SelectionKey.OP_READ);

        // Create session
        boolean isUnix = server == unixServer;
        ClientSession session = new ClientSession(client, isUnix);
        sessions.put(client, session);

        logger.info("New connection: {} ({})", session.sessionId, isUnix ? "unix" : "tcp");

        // Send auth challenge
        List<String> methods = isUnix ?
            List.of("token") :  // Unix socket: token only (auto-token or persistent)
            List.of("token", "pairing");  // TCP: token or pairing

        logger.debug("Sending AUTH_CHALLENGE to session {}", session.sessionId);
        sendResponse(session, new SessionMessage.AuthChallenge(session.authNonce, methods));
        session.challengeSent = true;
        logger.debug("AUTH_CHALLENGE sent to session {}", session.sessionId);
    }

    private void read(SelectionKey key) {
        SocketChannel client = (SocketChannel) key.channel();
        ClientSession session = sessions.get(client);
        if (session == null) {
            key.cancel();
            return;
        }

        try {
            // Read length prefix (4 bytes)
            ByteBuffer lengthBuf = ByteBuffer.allocate(4);
            int read = client.read(lengthBuf);
            if (read == -1) {
                closeSession(client);
                return;
            }
            if (read < 4) {
                // Partial read - would need buffering in production
                return;
            }

            lengthBuf.flip();
            int length = lengthBuf.getInt();
            if (length < 1 || length > 10_000_000) {
                logger.warn("Invalid message length from session {}: {}", session.sessionId, length);
                closeSession(client);
                return;
            }

            // Read message
            ByteBuffer dataBuf = ByteBuffer.allocate(length);
            while (dataBuf.hasRemaining()) {
                if (client.read(dataBuf) == -1) {
                    closeSession(client);
                    return;
                }
            }
            dataBuf.flip();

            // Decode message type
            byte typeCode = dataBuf.get();
            SessionMessage.Type type = SessionMessage.Type.fromCode(typeCode);

            // Decode CBOR payload
            byte[] payload = new byte[length - 1];
            dataBuf.get(payload);

            // Handle in worker thread
            final SessionMessage.Type finalType = type;
            final byte[] finalPayload = payload;
            workerPool.submit(() -> handleMessage(session, finalType, finalPayload));

        } catch (IOException e) {
            logger.debug("Read error from session {}: {}", session.sessionId, e.getMessage());
            closeSession(client);
        }
    }

    // =========================================================================
    // Message Handling
    // =========================================================================

    private void handleMessage(ClientSession session, SessionMessage.Type type, byte[] payload) {
        try {
            com.upokecenter.cbor.CBORObject cbor = com.upokecenter.cbor.CBORObject.DecodeFromBytes(payload);

            switch (type) {
                case AUTH -> handleAuth(session, cbor);
                case CONTEXT -> handleContext(session, cbor);
                case DISPATCH -> handleDispatch(session, cbor);
                case LOOKUP -> handleLookup(session, cbor);
                case SUBSCRIBE -> handleSubscribe(session, cbor);
                default -> logger.warn("Unexpected message type from session {}: {}", session.sessionId, type);
            }
        } catch (Exception e) {
            logger.error("Error handling {} from session {}", type, session.sessionId, e);
            sendError(session, 0, "INTERNAL_ERROR", e.getMessage());
        }
    }

    private void handleAuth(ClientSession session, com.upokecenter.cbor.CBORObject cbor) {
        if (!session.challengeSent) {
            sendResponse(session, SessionMessage.AuthResponse.failure("No challenge sent"));
            return;
        }

        // Determine auth method from CBOR content
        if (cbor.ContainsKey("token")) {
            // Token auth
            String token = cbor.get("token").AsString();
            TokenInfo info = verifyToken(token);

            if (info == null) {
                logger.warn("Session {} failed token auth", session.sessionId);
                sendResponse(session, SessionMessage.AuthResponse.failure("Invalid token"));
                return;
            }

            session.authenticated = true;

            // Resolve principal identity:
            // 1. Explicit principalId in message (--as flag)
            // 2. Auto-token → bind to Librarian's principal (you ARE the owner)
            // 3. Persistent token → use whatever principal was set during engage
            if (cbor.ContainsKey("principalId")) {
                session.principalId = ItemID.fromString(cbor.get("principalId").AsString());
                logger.info("Session {} principal (explicit): {}", session.sessionId, session.principalId.encodeText());
            } else if (token.equals(localAutoToken)) {
                // Auto-token = the principal's token
                librarian.principal().ifPresent(p -> {
                    session.principalId = p.iid();
                    logger.info("Session {} principal (auto): {}", session.sessionId, p.displayToken());
                });
            }

            librarian.registerSession(Librarian.SessionInfo.unix("Session " + session.sessionId));
            if (session.principalId != null) {
                sendResponse(session, SessionMessage.AuthResponse.successWithPrincipal(session.sessionId, session.principalId));
            } else {
                sendResponse(session, SessionMessage.AuthResponse.success(session.sessionId));
            }
            logger.info("Session {} authenticated via token ({})", session.sessionId, info.description());

        } else if (cbor.ContainsKey("inviteCode")) {
            // Engage auth — register a new user via invite code
            String code = cbor.get("inviteCode").AsString();
            String name = cbor.ContainsKey("name") ? cbor.get("name").AsString() : null;

            if (name == null || name.isBlank()) {
                sendResponse(session, SessionMessage.AuthResponse.failure("Name required with invite code (--name)"));
                return;
            }

            TokenInfo info = verifyToken(code);
            if (info == null) {
                logger.warn("Session {} failed engage auth with invalid invite code", session.sessionId);
                sendResponse(session, SessionMessage.AuthResponse.failure("Invalid or expired invite code"));
                return;
            }

            // Create user on this librarian
            try {
                var user = dev.everydaythings.graph.item.user.User.create(librarian, name);
                session.authenticated = true;
                session.principalId = user.iid();

                // Issue persistent token for future connections
                String persistentToken = createPersistentToken("User: " + name + " (" + user.iid().encodeText() + ")");

                librarian.registerSession(Librarian.SessionInfo.unix("Session " + session.sessionId + " (" + name + ")"));
                sendResponse(session, SessionMessage.AuthResponse.engaged(session.sessionId, persistentToken, user.iid()));
                logger.info("Session {} engaged as '{}' ({}), issued persistent token",
                        session.sessionId, name, user.iid().encodeText());
            } catch (Exception e) {
                logger.error("Failed to create user '{}' during engage", name, e);
                sendResponse(session, SessionMessage.AuthResponse.failure("Failed to create user: " + e.getMessage()));
            }

        } else if (cbor.ContainsKey("principalId")) {
            // Principal signature auth (future — challenge-response with private key)
            sendResponse(session, SessionMessage.AuthResponse.failure("Principal auth not yet implemented"));

        } else {
            sendResponse(session, SessionMessage.AuthResponse.failure("Unknown auth method"));
        }
    }

    private void handleContext(ClientSession session, com.upokecenter.cbor.CBORObject cbor) {
        boolean resolve = cbor.ContainsKey("resolve") && cbor.get("resolve").AsBoolean();

        if (!cbor.ContainsKey("itemId") || cbor.get("itemId").isNull()) {
            // Get current context
            Item ctx = session.context != null ? session.context : librarian;
            sendResponse(session, new SessionMessage.ContextResponse(
                    ctx.iid(),
                    ctx.displayToken(),
                    null
            ));
            return;
        }

        String itemIdStr = cbor.get("itemId").AsString();

        if (resolve && itemIdStr.startsWith("token:")) {
            // Resolve token to item
            String token = itemIdStr.substring(6);
            var tokenDict = librarian.tokenIndex();
            if (tokenDict == null) {
                sendResponse(session, new SessionMessage.ContextResponse(null, null, "Token dictionary not available"));
                return;
            }
            List<Posting> postings = tokenDict.lookup(token).limit(1).toList();
            if (postings.isEmpty()) {
                sendResponse(session, new SessionMessage.ContextResponse(null, null, "Not found: " + token));
                return;
            }
            ItemID iid = postings.get(0).target();
            session.context = librarian.get(iid, Item.class).orElse(null);
        } else {
            ItemID iid = ItemID.fromString(itemIdStr);
            session.context = librarian.get(iid, Item.class).orElse(null);
        }

        if (session.context == null) {
            sendResponse(session, new SessionMessage.ContextResponse(null, null, "Item not found"));
        } else {
            sendResponse(session, new SessionMessage.ContextResponse(
                    session.context.iid(),
                    session.context.displayToken(),
                    null
            ));
        }
    }

    private void handleDispatch(ClientSession session, com.upokecenter.cbor.CBORObject cbor) {
        String action = cbor.get("action").AsString();
        long requestId = cbor.get("requestId").AsInt64Value();

        // Parse caller identity from message
        ItemID caller = cbor.ContainsKey("caller") ?
                ItemID.fromString(cbor.get("caller").AsString()) : null;

        // Validate caller matches authenticated principal (if set)
        if (session.principalId != null && caller != null && !caller.equals(session.principalId)) {
            sendResponse(session, SessionMessage.DispatchResponse.failure(requestId,
                    "Caller " + caller.encodeText() + " does not match authenticated principal"));
            return;
        }

        // Use session's principal if caller not in message
        if (caller == null) {
            caller = session.principalId;
        }

        // Parse args
        List<String> args = new ArrayList<>();
        com.upokecenter.cbor.CBORObject argsArr = cbor.get("args");
        for (int i = 0; i < argsArr.size(); i++) {
            args.add(argsArr.get(i).AsString());
        }

        // Get target (context or librarian)
        Item target = session.context != null ? session.context : librarian;

        // Dispatch with caller identity
        ActionResult result;
        if (caller != null) {
            result = target.dispatch(caller, action, args);
        } else {
            result = target.dispatch(action, args);
        }

        if (result.success()) {
            Object value = result.value();
            View view = value instanceof View v ? v :
                    value != null ? View.of(TextSurface.of(value.toString())) : View.empty();
            sendResponse(session, SessionMessage.DispatchResponse.success(requestId, view));

            // Notify subscribers of the changed item
            notifySubscribers(target.iid(), target);
        } else {
            sendResponse(session, SessionMessage.DispatchResponse.failure(requestId,
                    result.error() != null ? result.error().getMessage() : "Unknown error"));
        }
    }

    private void handleLookup(ClientSession session, com.upokecenter.cbor.CBORObject cbor) {
        String query = cbor.get("query").AsString();
        int limit = cbor.get("limit").AsInt32();
        long requestId = cbor.get("requestId").AsInt64Value();

        var tokenDict = librarian.tokenIndex();
        List<Posting> postings = tokenDict != null ?
                tokenDict.lookup(query).limit(limit).toList() :
                List.of();
        sendResponse(session, new SessionMessage.LookupResponse(requestId, postings));
    }

    private void handleSubscribe(ClientSession session, com.upokecenter.cbor.CBORObject cbor) {
        ItemID itemId = ItemID.fromString(cbor.get("itemId").AsString());
        boolean subscribe = cbor.get("subscribe").AsBoolean();

        if (subscribe) {
            session.subscriptions.add(itemId);
            logger.info("Session {} subscribed to {}", session.sessionId, itemId.encodeText());
        } else {
            session.subscriptions.remove(itemId);
            logger.info("Session {} unsubscribed from {}", session.sessionId, itemId.encodeText());
        }

        sendResponse(session, new SessionMessage.OkResponse(0));
    }

    /**
     * Notify all sessions subscribed to an item that it has been updated.
     *
     * <p>Sends an EVENT message with the updated View to each subscriber.
     * The dispatching session is excluded (they already got the dispatch response).
     */
    private void notifySubscribers(ItemID itemId, Item item) {
        for (var entry : sessions.entrySet()) {
            ClientSession cs = entry.getValue();
            if (cs.subscriptions.contains(itemId)) {
                try {
                    sendResponse(cs, new SessionMessage.EventMessage(
                            itemId, "updated", null));
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

    private void sendResponse(ClientSession session, SessionMessage message) {
        try {
            byte[] encoded = codec.encode(message);
            logger.debug("Sending {} to session {} ({} bytes)",
                    message.getClass().getSimpleName(), session.sessionId, encoded.length);
            ByteBuffer buffer = ByteBuffer.wrap(encoded);

            synchronized (session.channel) {
                int totalWritten = 0;
                while (buffer.hasRemaining()) {
                    int written = session.channel.write(buffer);
                    totalWritten += written;
                }
                logger.debug("Wrote {} bytes to session {}", totalWritten, session.sessionId);
            }
        } catch (IOException e) {
            logger.warn("Failed to send response to session {}", session.sessionId, e);
            closeSession(session.channel);
        }
    }

    private void sendError(ClientSession session, long requestId, String code, String message) {
        sendResponse(session, new SessionMessage.ErrorResponse(requestId, code, message));
    }

    // =========================================================================
    // Session Management
    // =========================================================================

    private void closeSession(SocketChannel channel) {
        ClientSession session = sessions.remove(channel);
        if (session != null) {
            librarian.unregisterSession(Librarian.SessionInfo.unix("Session " + session.sessionId));
            logger.info("Session closed: {}", session.sessionId);
        }

        try {
            channel.close();
        } catch (IOException e) {
            // ignore
        }
    }

    // =========================================================================
    // Lifecycle
    // =========================================================================

    @Override
    public void close() {
        running.set(false);

        if (selector != null) {
            try {
                selector.close();
            } catch (IOException e) {
                // ignore
            }
        }

        // Close all sessions
        for (SocketChannel channel : sessions.keySet()) {
            closeSession(channel);
        }

        // Close servers
        if (unixServer != null) {
            try {
                unixServer.close();
            } catch (IOException e) {
                // ignore
            }
        }
        if (tcpServer != null) {
            try {
                tcpServer.close();
            } catch (IOException e) {
                // ignore
            }
        }

        if (workerPool != null) {
            workerPool.shutdownNow();
        }

        logger.info("Session server stopped");
    }

    // =========================================================================
    // Client Session State
    // =========================================================================

    private static class ClientSession {
        final SocketChannel channel;
        final String sessionId = UUID.randomUUID().toString().substring(0, 8);
        final byte[] authNonce = new byte[32];
        final boolean isUnixSocket;
        boolean authenticated = false;
        boolean challengeSent = false;
        Item context = null;
        ItemID principalId = null;  // Set during AUTH or SET_PRINCIPAL
        final java.util.Set<ItemID> subscriptions = ConcurrentHashMap.newKeySet();

        ClientSession(SocketChannel channel, boolean isUnixSocket) {
            this.channel = channel;
            this.isUnixSocket = isUnixSocket;
            // Generate random nonce for auth challenge
            new java.security.SecureRandom().nextBytes(authNonce);
        }
    }

    // =========================================================================
    // Token Management
    // =========================================================================

    /**
     * Information about an auth token.
     */
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

    /**
     * Generate the local auto-token (for Unix socket auth).
     * This token is regenerated each time the server starts.
     */
    private void generateLocalAutoToken() {
        localAutoToken = UUID.randomUUID().toString();
        logger.debug("Generated local auto-token for Unix socket auth");
    }

    /**
     * Get the local auto-token (for local clients to use).
     */
    public String localAutoToken() {
        return localAutoToken;
    }

    /**
     * Create a persistent token for a trusted client.
     */
    public String createPersistentToken(String description) {
        String token = UUID.randomUUID().toString();
        validTokens.put(token, new TokenInfo(
            token,
            description,
            Instant.now(),
            null,  // No expiry
            false  // Not one-time
        ));
        logger.info("Created persistent token: {} - {}", token.substring(0, 8) + "...", description);
        return token;
    }

    /**
     * Create a one-time invite code.
     *
     * <p>Invite codes are 6-digit, one-time use, 5-minute expiry.
     * Used by the {@code invite} verb. The recipient engages with this code
     * to register a new user identity.
     */
    public String createInviteCode() {
        // 6-digit code for easy entry
        String code = String.format("%06d", new java.security.SecureRandom().nextInt(1000000));
        validTokens.put(code, new TokenInfo(
            code,
            "Invite code",
            Instant.now(),
            Instant.now().plusSeconds(300),  // 5 minute expiry
            true  // One-time use
        ));
        logger.info("Created invite code: {}", code);
        return code;
    }

    /**
     * Revoke a token.
     */
    public void revokeToken(String token) {
        TokenInfo removed = validTokens.remove(token);
        if (removed != null) {
            logger.info("Revoked token: {}", token.substring(0, Math.min(8, token.length())) + "...");
        }
    }

    /**
     * Get the TCP port that the server is listening on.
     *
     * <p>This is useful when the server was started with port 0 (auto-assign)
     * to discover the actual port that was assigned.
     *
     * @return The TCP port, or -1 if TCP server is not running
     */
    public int getTcpPort() {
        if (tcpServer == null) {
            return -1;
        }
        try {
            InetSocketAddress addr = (InetSocketAddress) tcpServer.getLocalAddress();
            return addr != null ? addr.getPort() : -1;
        } catch (Exception e) {
            logger.warn("Failed to get TCP port: {}", e.getMessage());
            return -1;
        }
    }

    /**
     * Check if the server is listening on a Unix socket.
     */
    public boolean hasUnixSocket() {
        return unixServer != null;
    }

    /**
     * Check if the server is listening on TCP.
     */
    public boolean hasTcpServer() {
        return tcpServer != null;
    }

    /**
     * Verify a token.
     */
    private TokenInfo verifyToken(String token) {
        // Check local auto-token first
        if (token.equals(localAutoToken)) {
            return new TokenInfo(token, "Local auto-token", Instant.now(), null, false);
        }

        TokenInfo info = validTokens.get(token);
        if (info == null) {
            return null;
        }

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
