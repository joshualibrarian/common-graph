package dev.everydaythings.graph.network;

import dev.everydaythings.graph.network.peer.PeerConnection;
import dev.everydaythings.graph.network.transport.PeerClient;
import dev.everydaythings.graph.network.transport.PeerServer;
import dev.everydaythings.graph.value.Endpoint;
import dev.everydaythings.graph.crypt.Vault;
import io.netty.handler.ssl.SslContext;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.net.InetSocketAddress;
import java.security.cert.X509Certificate;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages network connectivity for a Librarian.
 *
 * <p>Owns the server (for incoming connections) and client (for outbound),
 * tracks active connections, and dispatches incoming messages.
 *
 * <p>Supports optional TLS for encrypted, authenticated connections.
 * Uses Netty's {@link SslContext} for server and client separately.
 */
public class NetworkManager implements AutoCloseable {

    private static final Logger log = LogManager.getLogger(NetworkManager.class);

    private final int port;
    private final MessageDispatcher dispatcher;
    private final SslContext serverSslContext;
    private final SslContext clientSslContext;
    private final Vault transportVault;

    private PeerServer server;
    private PeerClient client;

    private InetSocketAddress boundAddress;

    // Active connections by remote address
    private final Map<InetSocketAddress, PeerConnection> connections = new ConcurrentHashMap<>();

    /**
     * Create a NetworkManager without TLS or transport encryption (for testing).
     */
    public NetworkManager(int port, MessageDispatcher dispatcher) {
        this(port, dispatcher, null, null, null);
    }

    /**
     * Create a NetworkManager with optional TLS.
     *
     * @param port             The port to listen on (0 for auto-assign)
     * @param dispatcher       Handler for incoming messages
     * @param serverSslContext Netty SslContext for server (null to disable)
     * @param clientSslContext Netty SslContext for client (null to disable)
     */
    public NetworkManager(int port, MessageDispatcher dispatcher,
                          SslContext serverSslContext, SslContext clientSslContext) {
        this(port, dispatcher, serverSslContext, clientSslContext, null);
    }

    /**
     * Create a NetworkManager with CG transport encryption.
     *
     * @param port           The port to listen on (0 for auto-assign)
     * @param dispatcher     Handler for incoming messages
     * @param transportVault Vault with X25519 key for Noise XX handshake
     */
    public NetworkManager(int port, MessageDispatcher dispatcher, Vault transportVault) {
        this(port, dispatcher, null, null, transportVault);
    }

    /**
     * Create a NetworkManager with optional TLS and/or CG transport encryption.
     *
     * @param port             The port to listen on (0 for auto-assign)
     * @param dispatcher       Handler for incoming messages
     * @param serverSslContext Netty SslContext for server (null to disable)
     * @param clientSslContext Netty SslContext for client (null to disable)
     * @param transportVault   Vault for CG transport encryption (null to disable)
     */
    public NetworkManager(int port, MessageDispatcher dispatcher,
                          SslContext serverSslContext, SslContext clientSslContext,
                          Vault transportVault) {
        this.port = port;
        this.dispatcher = dispatcher;
        this.serverSslContext = serverSslContext;
        this.clientSslContext = clientSslContext;
        this.transportVault = transportVault;
    }

    /**
     * Start the network manager (server listening, client ready).
     *
     * @return Future that completes when the server is bound
     */
    public CompletableFuture<Void> start() {
        log.info("Starting NetworkManager on port {} (TLS: {}, transport-encrypted: {})",
                port, isTlsEnabled(), isTransportEncrypted());

        // Create and start server
        server = new PeerServer(port, new PeerServer.IncomingHandler() {

            @Override
            public void onConnect(PeerConnection connection, X509Certificate peerCert) {
                connections.put(connection.remoteAddress(), connection);
                dispatcher.onPeerConnected(connection, peerCert);
            }

            @Override
            public void onMessage(PeerConnection connection, ProtocolMessage message) {
                dispatcher.onMessage(connection, message);
            }

            @Override
            public void onDisconnect(PeerConnection connection) {
                connections.remove(connection.remoteAddress());
                dispatcher.onPeerDisconnected(connection);
            }
        }, serverSslContext, transportVault);

        // Create client for outbound connections
        client = new PeerClient(new PeerClient.MessageHandler() {
            @Override
            public void onConnect(PeerConnection connection, X509Certificate peerCert) {
                // Note: for client-initiated connections, we track in connect() not here
            }

            @Override
            public void onMessage(PeerConnection connection, ProtocolMessage message) {
                dispatcher.onMessage(connection, message);
            }

            @Override
            public void onDisconnect(PeerConnection connection) {
                connections.remove(connection.remoteAddress());
                dispatcher.onPeerDisconnected(connection);
            }
        }, clientSslContext, transportVault);

        return server.start().thenAccept(addr -> {
            this.boundAddress = addr;
            log.info("NetworkManager ready on {}", addr);
        });
    }

    /**
     * Connect to a remote peer.
     *
     * @param endpoint The endpoint to connect to
     * @return Future that completes with the connection
     */
    public CompletableFuture<PeerConnection> connect(Endpoint endpoint) {
        log.debug("Connecting to {}", endpoint);
        return client.connect(endpoint).thenApply(conn -> {
            connections.put(conn.remoteAddress(), conn);
            return conn;
        });
    }

    /**
     * Get an existing connection to a peer, if any.
     */
    public Optional<PeerConnection> connection(InetSocketAddress address) {
        return Optional.ofNullable(connections.get(address));
    }

    /**
     * Get all active connections.
     */
    public Iterable<PeerConnection> connections() {
        return connections.values();
    }

    /**
     * Number of active connections.
     */
    public int connectionCount() {
        return connections.size();
    }

    /**
     * Get the bound address.
     */
    public InetSocketAddress boundAddress() {
        return boundAddress;
    }

    /**
     * Check if the network is running.
     */
    public boolean isRunning() {
        return boundAddress != null;
    }

    /**
     * Check if TLS is enabled.
     */
    public boolean isTlsEnabled() {
        return serverSslContext != null || clientSslContext != null;
    }

    /**
     * Check if CG transport encryption is enabled.
     */
    public boolean isTransportEncrypted() {
        return transportVault != null;
    }

    /**
     * Shut down the network manager.
     */
    @Override
    public void close() {
        log.debug("Shutting down NetworkManager");

        // Close all connections
        for (PeerConnection conn : connections.values()) {
            try {
                conn.close();
            } catch (Exception e) {
                log.debug("Error closing connection: {}", e.getMessage());
            }
        }
        connections.clear();

        // Shut down client and server
        if (client != null) {
            client.close();
        }
        if (server != null) {
            server.close();
        }

        boundAddress = null;
    }

    /**
     * Handler for dispatching incoming messages.
     */
    public interface MessageDispatcher {
        /**
         * Called when a new peer connects.
         *
         * @param connection The connection
         * @param peerCert   The peer's X.509 certificate (if TLS with client auth), or null
         */
        void onPeerConnected(PeerConnection connection, X509Certificate peerCert);

        /**
         * Called when a typed protocol message is received.
         *
         * @param connection The connection the message came from
         * @param message    The decoded protocol message
         */
        void onMessage(PeerConnection connection, ProtocolMessage message);

        /**
         * Called when a peer disconnects.
         */
        void onPeerDisconnected(PeerConnection connection);
    }
}
