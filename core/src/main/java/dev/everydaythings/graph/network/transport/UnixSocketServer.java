package dev.everydaythings.graph.network.transport;

import dev.everydaythings.graph.network.peer.UnixPeerConnection;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.net.StandardProtocolFamily;
import java.net.UnixDomainSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Unix domain socket server for local IPC.
 *
 * <p>Uses Java 16+ native Unix domain socket support via {@link UnixDomainSocketAddress}.
 * The socket file is created at the specified path and removed on shutdown.
 *
 * <p>Advantages over TCP for local communication:
 * <ul>
 *   <li>No network stack overhead</li>
 *   <li>Filesystem-based access control</li>
 *   <li>No port conflicts</li>
 *   <li>Slightly lower latency</li>
 * </ul>
 */
public class UnixSocketServer implements AutoCloseable {

    private static final Logger log = LogManager.getLogger(UnixSocketServer.class);

    private static final int BUFFER_SIZE = 1024 * 1024; // 1MB max message
    private static final int LENGTH_PREFIX_SIZE = 4;

    private final Path socketPath;
    private final IncomingHandler handler;

    private ServerSocketChannel serverChannel;
    private Selector selector;
    private ExecutorService acceptorThread;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final Map<SocketChannel, UnixPeerConnection> connections = new ConcurrentHashMap<>();

    public UnixSocketServer(Path socketPath, IncomingHandler handler) {
        this.socketPath = socketPath;
        this.handler = handler;
    }

    /**
     * Start the server and return when it's bound and ready.
     */
    public CompletableFuture<Path> start() {
        CompletableFuture<Path> future = new CompletableFuture<>();

        try {
            // Clean up any stale socket file
            Files.deleteIfExists(socketPath);

            // Ensure parent directory exists
            Path parent = socketPath.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }

            // Create and bind the server socket
            serverChannel = ServerSocketChannel.open(StandardProtocolFamily.UNIX);
            serverChannel.configureBlocking(false);
            serverChannel.bind(UnixDomainSocketAddress.of(socketPath));

            // Create selector for non-blocking I/O
            selector = Selector.open();
            serverChannel.register(selector, SelectionKey.OP_ACCEPT);

            running.set(true);

            // Start acceptor thread
            acceptorThread = Executors.newSingleThreadExecutor(r -> {
                Thread t = new Thread(r, "unix-socket-acceptor");
                t.setDaemon(true);
                return t;
            });
            acceptorThread.submit(this::acceptLoop);

            log.info("Unix socket server listening on {}", socketPath);
            future.complete(socketPath);

        } catch (IOException e) {
            log.error("Failed to start Unix socket server: {}", socketPath, e);
            future.completeExceptionally(e);
            cleanup();
        }

        return future;
    }

    private void acceptLoop() {
        ByteBuffer lengthBuffer = ByteBuffer.allocate(LENGTH_PREFIX_SIZE);
        ByteBuffer messageBuffer = ByteBuffer.allocate(BUFFER_SIZE);

        while (running.get()) {
            try {
                if (selector.select(100) == 0) {
                    continue;
                }

                Iterator<SelectionKey> keys = selector.selectedKeys().iterator();
                while (keys.hasNext()) {
                    SelectionKey key = keys.next();
                    keys.remove();

                    if (!key.isValid()) {
                        continue;
                    }

                    if (key.isAcceptable()) {
                        acceptConnection();
                    } else if (key.isReadable()) {
                        readFromClient(key, lengthBuffer, messageBuffer);
                    }
                }
            } catch (IOException e) {
                if (running.get()) {
                    log.error("Error in Unix socket accept loop", e);
                }
            }
        }
    }

    private void acceptConnection() throws IOException {
        SocketChannel clientChannel = serverChannel.accept();
        if (clientChannel != null) {
            clientChannel.configureBlocking(false);
            clientChannel.register(selector, SelectionKey.OP_READ);

            UnixPeerConnection connection = new UnixPeerConnection(clientChannel);
            connections.put(clientChannel, connection);
            handler.onConnect(connection);
            log.debug("Unix socket client connected");
        }
    }

    private void readFromClient(SelectionKey key, ByteBuffer lengthBuffer, ByteBuffer messageBuffer) {
        SocketChannel clientChannel = (SocketChannel) key.channel();
        UnixPeerConnection connection = connections.get(clientChannel);

        try {
            // Read length prefix
            lengthBuffer.clear();
            int bytesRead = clientChannel.read(lengthBuffer);

            if (bytesRead == -1) {
                // Client disconnected
                closeConnection(key, clientChannel, connection);
                return;
            }

            if (bytesRead < LENGTH_PREFIX_SIZE) {
                // Incomplete read - in production, buffer partial reads
                return;
            }

            lengthBuffer.flip();
            int messageLength = lengthBuffer.getInt();

            if (messageLength <= 0 || messageLength > BUFFER_SIZE) {
                log.warn("Invalid message length: {}", messageLength);
                return;
            }

            // Read message body
            messageBuffer.clear();
            messageBuffer.limit(messageLength);

            int totalRead = 0;
            while (totalRead < messageLength) {
                int read = clientChannel.read(messageBuffer);
                if (read == -1) {
                    closeConnection(key, clientChannel, connection);
                    return;
                }
                totalRead += read;
            }

            messageBuffer.flip();
            byte[] data = new byte[messageLength];
            messageBuffer.get(data);

            handler.onMessage(connection, data);

        } catch (IOException e) {
            log.debug("Error reading from Unix socket client", e);
            closeConnection(key, clientChannel, connection);
        }
    }

    private void closeConnection(SelectionKey key, SocketChannel channel, UnixPeerConnection connection) {
        key.cancel();
        connections.remove(channel);
        try {
            channel.close();
        } catch (IOException ignore) {}
        if (connection != null) {
            handler.onDisconnect(connection);
        }
        log.debug("Unix socket client disconnected");
    }

    @Override
    public void close() {
        cleanup();
    }

    private void cleanup() {
        running.set(false);

        if (selector != null) {
            selector.wakeup();
        }

        if (acceptorThread != null) {
            acceptorThread.shutdownNow();
            acceptorThread = null;
        }

        // Close all client connections
        for (Map.Entry<SocketChannel, UnixPeerConnection> entry : connections.entrySet()) {
            try {
                entry.getKey().close();
            } catch (IOException ignore) {}
        }
        connections.clear();

        if (serverChannel != null) {
            try {
                serverChannel.close();
            } catch (IOException ignore) {}
            serverChannel = null;
        }

        if (selector != null) {
            try {
                selector.close();
            } catch (IOException ignore) {}
            selector = null;
        }

        // Clean up socket file
        try {
            Files.deleteIfExists(socketPath);
        } catch (IOException e) {
            log.warn("Failed to delete socket file: {}", socketPath, e);
        }
    }

    /**
     * Handler interface for incoming Unix socket connections.
     */
    public interface IncomingHandler {
        void onConnect(UnixPeerConnection connection);
        void onMessage(UnixPeerConnection connection, byte[] data);
        void onDisconnect(UnixPeerConnection connection);
    }

    /**
     * Check if Unix domain sockets are available on this platform.
     *
     * <p>Requires Java 16+ and a Unix-like OS (Linux, macOS, BSD).
     *
     * @return true if Unix domain sockets can be used
     */
    public static boolean isAvailable() {
        // Unix domain sockets are available in Java 16+ on Unix-like systems
        String os = System.getProperty("os.name", "").toLowerCase();
        return os.contains("nix") || os.contains("nux") || os.contains("mac") || os.contains("bsd");
    }
}
