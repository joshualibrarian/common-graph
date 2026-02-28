package dev.everydaythings.graph.runtime.protocol;

import com.upokecenter.cbor.CBORObject;
import dev.everydaythings.graph.item.id.ItemID;
import dev.everydaythings.graph.language.Posting;
import dev.everydaythings.graph.ui.scene.View;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.UnixDomainSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/**
 * Client for the Session Protocol.
 *
 * <p>SessionClient handles the low-level protocol communication with a
 * Session Server (typically hosted by a Librarian). It manages:
 * <ul>
 *   <li>Connection establishment (Unix socket or TCP)</li>
 *   <li>Authentication handshake</li>
 *   <li>Message encoding/decoding</li>
 *   <li>Request/response correlation</li>
 * </ul>
 *
 * <p>This is the base class for remote access. Subclasses add domain-specific
 * functionality (e.g., {@code RemoteLibrarian} adds library operations).
 *
 * <p>Connection types:
 * <ul>
 *   <li>Unix socket - preferred for local connections (fast, secure)</li>
 *   <li>TCP - for remote connections</li>
 * </ul>
 *
 * <p>Authentication methods:
 * <ul>
 *   <li>Token-based - simple token for local/trusted connections</li>
 *   <li>Principal signature - for remote connections (future)</li>
 *   <li>Pairing code - for new device setup (future)</li>
 * </ul>
 */
public class SessionClient implements AutoCloseable {

    private static final Logger log = LogManager.getLogger(SessionClient.class);

    // Connection target - either "host:port" for TCP or path for Unix socket
    private final String connectionTarget;

    // Connection state
    private SocketChannel channel;
    private final SessionCodec codec = new SessionCodec();
    private final AtomicLong requestIdGenerator = new AtomicLong(1);

    // Session state (set after authentication)
    private String sessionId;

    // Principal identity for this session (set via --as flag)
    protected ItemID principalId;

    private boolean closed = false;

    // Async event reading
    private Thread readerThread;
    private volatile CompletableFuture<SessionMessage> pendingResponse;
    private volatile Consumer<SessionMessage.EventMessage> eventListener;
    private volatile boolean asyncMode = false;

    // ==================================================================================
    // Constructors
    // ==================================================================================

    /**
     * Create a SessionClient for TCP connection.
     */
    public SessionClient(String host, int port) {
        this.connectionTarget = host + ":" + port;
    }

    /**
     * Create a SessionClient for Unix socket connection.
     */
    public SessionClient(Path socketPath) {
        this.connectionTarget = socketPath.toString();
    }

    /**
     * Create a SessionClient with explicit connection target.
     *
     * @param target Either "host:port" for TCP or a path for Unix socket
     */
    public SessionClient(String target) {
        this.connectionTarget = target;
    }

    // ==================================================================================
    // Connection
    // ==================================================================================

    /**
     * Connect to the server.
     *
     * <p>Called automatically on first operation if not already connected.
     */
    public void connect() throws IOException {
        if (channel != null && channel.isOpen()) {
            return;  // Already connected
        }

        if (connectionTarget.contains(":") && !connectionTarget.startsWith("/")) {
            // TCP connection (host:port)
            String[] parts = connectionTarget.split(":", 2);
            String host = parts[0];
            int port = Integer.parseInt(parts[1]);
            channel = SocketChannel.open(new InetSocketAddress(host, port));
            log.info("Connected to server at TCP {}:{}", host, port);
        } else {
            // Unix socket
            Path socketPath = Path.of(connectionTarget);
            channel = SocketChannel.open(UnixDomainSocketAddress.of(socketPath));
            log.info("Connected to server at Unix socket {}", socketPath);
        }

        channel.configureBlocking(true);
    }

    /**
     * Authenticate with the server using token authentication.
     *
     * <p>For local connections, the token can be read from a file written
     * by the server daemon.
     *
     * @param token The authentication token, or null to use auto-detection
     */
    public void authenticate(String token) throws IOException {
        ensureConnected();

        // Wait for auth challenge
        log.debug("Waiting for AUTH_CHALLENGE from server...");
        SessionMessage challenge = receiveMessage();

        if (!(challenge instanceof SessionMessage.AuthChallenge authChallenge)) {
            throw new IOException("Expected AUTH_CHALLENGE, got: " + challenge.getClass().getSimpleName());
        }

        log.debug("Received auth challenge, methods: {}", authChallenge.acceptedMethods());

        // Determine which token to use
        if (token == null || token.isBlank() || "auto".equalsIgnoreCase(token)) {
            token = readLocalAutoToken();
        }

        if (token == null) {
            throw new IOException("No auth token available. Provide a token or ensure server is running locally.");
        }

        // Send token auth (include principal if set)
        sendMessage(new SessionMessage.AuthToken(token, principalId));

        // Wait for response
        SessionMessage response = receiveMessage();
        if (response instanceof SessionMessage.AuthResponse authResponse) {
            if (authResponse.success()) {
                this.sessionId = authResponse.sessionId();
                // Server tells us who we are (auto-token → principal, or explicit --as)
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

    /**
     * Connect and authenticate in one call.
     *
     * @param token The authentication token, or null for auto-detection
     */
    public void connectAndAuthenticate(String token) throws IOException {
        connect();
        authenticate(token);
    }

    /**
     * Connect and engage — register a new user identity via an invite code.
     *
     * <p>The invite code is obtained out-of-band from the Librarian's principal
     * (e.g., via the {@code invite} verb). On success, the server creates a new
     * User with the given name, binds this session to it, and issues a persistent
     * token for future connections.
     *
     * @param inviteCode The invite code from the Librarian's principal
     * @param name The desired username for the new identity
     * @throws IOException if connection or engagement fails
     */
    public void connectAndEngage(String inviteCode, String name) throws IOException {
        connect();

        // Wait for auth challenge
        log.debug("Waiting for AUTH_CHALLENGE from server...");
        SessionMessage challenge = receiveMessage();

        if (!(challenge instanceof SessionMessage.AuthChallenge authChallenge)) {
            throw new IOException("Expected AUTH_CHALLENGE, got: " + challenge.getClass().getSimpleName());
        }

        log.debug("Received auth challenge, methods: {}", authChallenge.acceptedMethods());

        // Send engage auth
        sendMessage(new SessionMessage.AuthEngage(inviteCode, name));

        // Wait for response
        SessionMessage response = receiveMessage();
        if (response instanceof SessionMessage.AuthResponse authResponse) {
            if (authResponse.success()) {
                this.sessionId = authResponse.sessionId();
                // Server created our user and tells us the IID
                if (authResponse.principalId() != null) {
                    this.principalId = authResponse.principalId();
                }
                log.info("Engaged as '{}', session: {}, principal: {}",
                        name, sessionId, principalId != null ? principalId.encodeText() : "(none)");

                if (authResponse.persistentToken() != null) {
                    log.info("Received persistent token for future connections");
                    // TODO: Save persistent token for future use
                }
            } else {
                throw new IOException("Engage failed: " + authResponse.error());
            }
        } else {
            throw new IOException("Expected AUTH response, got: " + response.getClass().getSimpleName());
        }
    }

    /**
     * Try to read the local auto-token from environment or file.
     */
    protected String readLocalAutoToken() {
        // Check environment variable first (set by parent process for embedded sessions)
        String envToken = System.getenv("GRAPH_SESSION_TOKEN");
        if (envToken != null && !envToken.isBlank()) {
            log.debug("Using token from GRAPH_SESSION_TOKEN environment variable");
            return envToken;
        }

        try {
            // The auto-token is written to a file next to the socket
            Path tokenPath;
            if (connectionTarget.endsWith(".sock")) {
                tokenPath = Path.of(connectionTarget).resolveSibling("session.token");
            } else if (!connectionTarget.contains(":")) {
                // Unix socket without .sock extension
                tokenPath = Path.of(connectionTarget).resolveSibling("session.token");
            } else {
                // TCP connection - try default location
                tokenPath = Path.of(System.getProperty("user.home"), ".librarian", "session.token");
            }

            if (Files.exists(tokenPath)) {
                String token = Files.readString(tokenPath).trim();
                log.debug("Read auto-token from {}", tokenPath);
                return token;
            }
        } catch (Exception e) {
            log.debug("Could not read local auto-token: {}", e.getMessage());
        }
        return null;
    }

    /**
     * Ensure connection and authentication are established.
     */
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

    /**
     * Set the context item.
     *
     * @param itemId The item to set as context
     * @return The context response
     */
    public SessionMessage.ContextResponse setContext(ItemID itemId) throws IOException {
        ensureConnected();

        SessionMessage.ContextRequest request = SessionMessage.ContextRequest.set(itemId);
        sendMessage(request);

        SessionMessage response = receiveMessage();
        if (response instanceof SessionMessage.ContextResponse cr) {
            return cr;
        }
        throw new IOException("Expected CONTEXT response, got: " + response.getClass().getSimpleName());
    }

    /**
     * Get the current context item.
     *
     * @return The context response
     */
    public SessionMessage.ContextResponse getContext() throws IOException {
        ensureConnected();

        sendMessage(SessionMessage.ContextRequest.get());

        SessionMessage response = receiveMessage();
        if (response instanceof SessionMessage.ContextResponse cr) {
            return cr;
        }
        throw new IOException("Expected CONTEXT response, got: " + response.getClass().getSimpleName());
    }

    /**
     * Send a dispatch request to the current context.
     *
     * @param action The action to dispatch
     * @param args Arguments for the action
     * @return The dispatch response
     */
    public SessionMessage.DispatchResponse sendDispatch(String action, List<String> args) throws IOException {
        return sendDispatch(action, args, principalId);
    }

    /**
     * Send a dispatch request with explicit caller identity.
     *
     * @param action The action to dispatch
     * @param args Arguments for the action
     * @param caller The principal performing the action
     * @return The dispatch response
     */
    public SessionMessage.DispatchResponse sendDispatch(String action, List<String> args, ItemID caller) throws IOException {
        ensureConnected();

        long requestId = requestIdGenerator.getAndIncrement();
        SessionMessage.DispatchRequest request = new SessionMessage.DispatchRequest(action, args, requestId, caller);

        SessionMessage response = sendAndReceive(request);
        if (response instanceof SessionMessage.DispatchResponse dr) {
            return dr;
        } else if (response instanceof SessionMessage.ErrorResponse er) {
            return SessionMessage.DispatchResponse.failure(requestId, er.code() + ": " + er.message());
        }
        throw new IOException("Expected DISPATCH response, got: " + response.getClass().getSimpleName());
    }

    /**
     * Lookup tokens for completion/search.
     *
     * @param query The query string
     * @param limit Maximum number of results
     * @return List of matching postings
     */
    public List<Posting> lookup(String query, int limit) throws IOException {
        ensureConnected();

        long requestId = requestIdGenerator.getAndIncrement();
        SessionMessage.LookupRequest request = new SessionMessage.LookupRequest(query, limit, requestId);
        sendMessage(request);

        SessionMessage response = receiveMessage();
        if (response instanceof SessionMessage.LookupResponse lr) {
            return lr.postings();
        }

        log.warn("Unexpected lookup response: {}", response.getClass().getSimpleName());
        return List.of();
    }

    // ==================================================================================
    // Accessors
    // ==================================================================================

    /**
     * Get the connection target string.
     */
    public String connectionTarget() {
        return connectionTarget;
    }

    /**
     * Get the session ID (null if not authenticated).
     */
    public String sessionId() {
        return sessionId;
    }

    /**
     * Check if the client is connected and authenticated.
     */
    public boolean isAuthenticated() {
        return sessionId != null && channel != null && channel.isOpen();
    }

    /**
     * Check if the client is open (not closed).
     */
    public boolean isOpen() {
        return !closed && (channel == null || channel.isOpen());
    }

    // ==================================================================================
    // Async Event Reading
    // ==================================================================================

    /**
     * Start an async reader thread that routes incoming messages.
     *
     * <p>After calling this, incoming EVENT messages are delivered to the
     * event listener, and request/response methods use CompletableFuture
     * internally.
     */
    public void startAsyncReader() {
        if (asyncMode) return;
        asyncMode = true;
        readerThread = new Thread(this::readLoop, "session-reader");
        readerThread.setDaemon(true);
        readerThread.start();
    }

    /**
     * Set the event listener for incoming EVENT messages.
     */
    public void onEvent(Consumer<SessionMessage.EventMessage> listener) {
        this.eventListener = listener;
    }

    /**
     * Send a subscribe request for an item.
     */
    public void sendSubscribe(ItemID itemId) throws IOException {
        sendMessage(new SessionMessage.SubscribeRequest(itemId, true));
        if (!asyncMode) {
            // Consume the OK response synchronously
            receiveMessage();
        }
    }

    private void readLoop() {
        log.debug("Async reader started");
        while (!closed) {
            try {
                SessionMessage msg = receiveMessageInternal();
                if (msg instanceof SessionMessage.EventMessage event) {
                    Consumer<SessionMessage.EventMessage> listener = eventListener;
                    if (listener != null) {
                        try {
                            listener.accept(event);
                        } catch (Exception e) {
                            log.warn("Event listener error", e);
                        }
                    }
                } else {
                    CompletableFuture<SessionMessage> future = pendingResponse;
                    if (future != null) {
                        pendingResponse = null;
                        future.complete(msg);
                    } else {
                        log.warn("Received message with no pending request: {}", msg.getClass().getSimpleName());
                    }
                }
            } catch (IOException e) {
                if (!closed) {
                    log.debug("Reader thread error: {}", e.getMessage());
                }
                break;
            }
        }
        log.debug("Async reader stopped");
    }

    /**
     * Send a message and wait for the response (async-aware).
     */
    protected SessionMessage sendAndReceive(SessionMessage request) throws IOException {
        if (asyncMode) {
            CompletableFuture<SessionMessage> future = new CompletableFuture<>();
            pendingResponse = future;
            sendMessage(request);
            try {
                return future.get(30, TimeUnit.SECONDS);
            } catch (Exception e) {
                throw new IOException("Timeout or error waiting for response", e);
            }
        } else {
            sendMessage(request);
            return receiveMessage();
        }
    }

    /**
     * Internal receive — reads one message from the channel.
     * Same as receiveMessage() but named distinctly for the reader thread.
     */
    private SessionMessage receiveMessageInternal() throws IOException {
        return receiveMessage();
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
                    channel.close();
                } catch (IOException e) {
                    log.warn("Error closing channel", e);
                }
                channel = null;
            }
            log.debug("SessionClient closed");
        }
    }

    /**
     * Check that the client is open, throwing if closed.
     */
    protected void checkOpen() {
        if (closed) {
            throw new IllegalStateException("SessionClient is closed");
        }
    }

    // ==================================================================================
    // Wire Protocol
    // ==================================================================================

    /**
     * Send a message to the server.
     */
    protected void sendMessage(SessionMessage message) throws IOException {
        byte[] encoded = codec.encode(message);
        ByteBuffer buffer = ByteBuffer.wrap(encoded);
        while (buffer.hasRemaining()) {
            channel.write(buffer);
        }
    }

    /**
     * Receive a message from the server.
     */
    protected SessionMessage receiveMessage() throws IOException {
        // Read length (4 bytes)
        ByteBuffer lengthBuf = ByteBuffer.allocate(4);
        while (lengthBuf.hasRemaining()) {
            if (channel.read(lengthBuf) == -1) {
                throw new IOException("Connection closed");
            }
        }
        lengthBuf.flip();
        int length = lengthBuf.getInt();

        if (length < 1 || length > 10_000_000) {
            throw new IOException("Invalid message length: " + length);
        }

        // Read message
        ByteBuffer dataBuf = ByteBuffer.allocate(length);
        while (dataBuf.hasRemaining()) {
            if (channel.read(dataBuf) == -1) {
                throw new IOException("Connection closed");
            }
        }
        dataBuf.flip();

        // Decode type
        byte typeCode = dataBuf.get();
        SessionMessage.Type type = SessionMessage.Type.fromCode(typeCode);

        // Decode CBOR payload
        byte[] payload = new byte[length - 1];
        dataBuf.get(payload);

        CBORObject cbor = CBORObject.DecodeFromBytes(payload);
        return decodeMessage(type, cbor);
    }

    /**
     * Decode a message from CBOR.
     */
    private SessionMessage decodeMessage(SessionMessage.Type type, CBORObject cbor) {
        return switch (type) {
            case AUTH -> {
                if (cbor.ContainsKey("success")) {
                    yield new SessionMessage.AuthResponse(
                            cbor.get("success").AsBoolean(),
                            cbor.ContainsKey("sessionId") ? cbor.get("sessionId").AsString() : null,
                            cbor.ContainsKey("persistentToken") ? cbor.get("persistentToken").AsString() : null,
                            cbor.ContainsKey("principalId") ? ItemID.fromString(cbor.get("principalId").AsString()) : null,
                            cbor.ContainsKey("error") ? cbor.get("error").AsString() : null
                    );
                } else if (cbor.ContainsKey("methods")) {
                    List<String> methods = new ArrayList<>();
                    var arr = cbor.get("methods");
                    for (int i = 0; i < arr.size(); i++) {
                        methods.add(arr.get(i).AsString());
                    }
                    yield new SessionMessage.AuthChallenge(
                            cbor.get("nonce").GetByteString(),
                            methods
                    );
                } else {
                    throw new IllegalArgumentException("Unknown AUTH message");
                }
            }
            case CONTEXT -> {
                if (cbor.ContainsKey("label") || cbor.ContainsKey("error")) {
                    yield new SessionMessage.ContextResponse(
                            cbor.ContainsKey("itemId") ? ItemID.fromString(cbor.get("itemId").AsString()) : null,
                            cbor.ContainsKey("label") ? cbor.get("label").AsString() : null,
                            cbor.ContainsKey("error") ? cbor.get("error").AsString() : null
                    );
                } else {
                    yield new SessionMessage.ContextRequest(
                            cbor.ContainsKey("itemId") ? ItemID.fromString(cbor.get("itemId").AsString()) : null,
                            cbor.ContainsKey("resolve") && cbor.get("resolve").AsBoolean()
                    );
                }
            }
            case DISPATCH -> {
                if (cbor.ContainsKey("action")) {
                    List<String> args = new ArrayList<>();
                    var argsArr = cbor.get("args");
                    for (int i = 0; i < argsArr.size(); i++) {
                        args.add(argsArr.get(i).AsString());
                    }
                    yield new SessionMessage.DispatchRequest(
                            cbor.get("action").AsString(),
                            args,
                            cbor.get("requestId").AsInt64Value(),
                            cbor.ContainsKey("caller") ? ItemID.fromString(cbor.get("caller").AsString()) : null
                    );
                } else {
                    yield new SessionMessage.DispatchResponse(
                            cbor.get("requestId").AsInt64Value(),
                            cbor.get("success").AsBoolean(),
                            cbor.ContainsKey("view") ? View.empty() : null,  // TODO: Full View deserialization
                            cbor.ContainsKey("error") ? cbor.get("error").AsString() : null
                    );
                }
            }
            case LOOKUP -> {
                if (cbor.ContainsKey("query")) {
                    yield new SessionMessage.LookupRequest(
                            cbor.get("query").AsString(),
                            cbor.get("limit").AsInt32(),
                            cbor.get("requestId").AsInt64Value()
                    );
                } else {
                    List<Posting> postings = new ArrayList<>();
                    var arr = cbor.get("postings");
                    for (int i = 0; i < arr.size(); i++) {
                        var pObj = arr.get(i);
                        ItemID target = ItemID.fromString(pObj.get("target").AsString());
                        postings.add(new Posting(
                                pObj.get("token").AsString(),
                                null,  // scope
                                target,
                                1.0f   // default weight
                        ));
                    }
                    yield new SessionMessage.LookupResponse(
                            cbor.get("requestId").AsInt64Value(),
                            postings
                    );
                }
            }
            case ERROR -> new SessionMessage.ErrorResponse(
                    cbor.get("requestId").AsInt64Value(),
                    cbor.get("code").AsString(),
                    cbor.get("message").AsString()
            );
            case OK -> new SessionMessage.OkResponse(cbor.get("requestId").AsInt64Value());
            default -> throw new IllegalArgumentException("Unexpected message type: " + type);
        };
    }

    @Override
    public String toString() {
        return "SessionClient[" + connectionTarget + (sessionId != null ? ", session=" + sessionId : "") + "]";
    }
}
