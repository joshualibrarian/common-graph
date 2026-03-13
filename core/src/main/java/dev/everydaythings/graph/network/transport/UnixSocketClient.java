package dev.everydaythings.graph.network.transport;

import dev.everydaythings.graph.network.transport.UnixPeerConnection;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.net.StandardProtocolFamily;
import java.net.UnixDomainSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Unix domain socket client for connecting to local librarians.
 *
 * <p>Uses Java 16+ native Unix domain socket support via {@link UnixDomainSocketAddress}.
 */
public class UnixSocketClient implements AutoCloseable {

    private static final Logger log = LogManager.getLogger(UnixSocketClient.class);

    private static final int BUFFER_SIZE = 1024 * 1024; // 1MB max message
    private static final int LENGTH_PREFIX_SIZE = 4;

    private ExecutorService readerThread;
    private final AtomicBoolean running = new AtomicBoolean(false);

    /**
     * Connect to a Unix socket.
     *
     * @param socketPath Path to the socket file
     * @param handler    Handler for connection events
     * @return Future that completes with the connection
     */
    public CompletableFuture<UnixPeerConnection> connect(Path socketPath, ConnectionHandler handler) {
        CompletableFuture<UnixPeerConnection> future = new CompletableFuture<>();

        try {
            log.debug("Connecting to Unix socket: {}", socketPath);

            SocketChannel channel = SocketChannel.open(StandardProtocolFamily.UNIX);
            channel.configureBlocking(false);

            UnixDomainSocketAddress address = UnixDomainSocketAddress.of(socketPath);

            if (channel.connect(address)) {
                // Connected immediately
                completeConnection(channel, handler, future);
            } else {
                // Connection in progress - wait for it
                Selector selector = Selector.open();
                channel.register(selector, SelectionKey.OP_CONNECT);

                // Wait for connection in background
                CompletableFuture.runAsync(() -> {
                    try {
                        while (!channel.finishConnect()) {
                            selector.select(100);
                        }
                        selector.close();
                        completeConnection(channel, handler, future);
                    } catch (IOException e) {
                        log.error("Failed to connect to Unix socket: {}", socketPath, e);
                        future.completeExceptionally(e);
                        try { channel.close(); } catch (IOException ignore) {}
                    }
                });
            }

        } catch (IOException e) {
            log.error("Failed to connect to Unix socket: {}", socketPath, e);
            future.completeExceptionally(e);
        }

        return future;
    }

    private void completeConnection(SocketChannel channel, ConnectionHandler handler,
                                    CompletableFuture<UnixPeerConnection> future) {
        UnixPeerConnection connection = new UnixPeerConnection(channel);
        log.debug("Connected to Unix socket");
        future.complete(connection);

        // Start reader thread for this connection
        running.set(true);
        readerThread = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "unix-socket-reader");
            t.setDaemon(true);
            return t;
        });
        readerThread.submit(() -> readLoop(channel, connection, handler));
    }

    private void readLoop(SocketChannel channel, UnixPeerConnection connection, ConnectionHandler handler) {
        ByteBuffer lengthBuffer = ByteBuffer.allocate(LENGTH_PREFIX_SIZE);
        ByteBuffer messageBuffer = ByteBuffer.allocate(BUFFER_SIZE);

        try {
            Selector selector = Selector.open();
            channel.register(selector, SelectionKey.OP_READ);

            while (running.get() && channel.isOpen()) {
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

                    if (key.isReadable()) {
                        // Read length prefix
                        lengthBuffer.clear();
                        int bytesRead = channel.read(lengthBuffer);

                        if (bytesRead == -1) {
                            // Server disconnected
                            log.debug("Disconnected from Unix socket");
                            handler.onDisconnect(connection);
                            return;
                        }

                        if (bytesRead < LENGTH_PREFIX_SIZE) {
                            continue; // Incomplete read
                        }

                        lengthBuffer.flip();
                        int messageLength = lengthBuffer.getInt();

                        if (messageLength <= 0 || messageLength > BUFFER_SIZE) {
                            log.warn("Invalid message length: {}", messageLength);
                            continue;
                        }

                        // Read message body
                        messageBuffer.clear();
                        messageBuffer.limit(messageLength);

                        int totalRead = 0;
                        while (totalRead < messageLength) {
                            int read = channel.read(messageBuffer);
                            if (read == -1) {
                                handler.onDisconnect(connection);
                                return;
                            }
                            totalRead += read;
                        }

                        messageBuffer.flip();
                        byte[] data = new byte[messageLength];
                        messageBuffer.get(data);

                        handler.onMessage(connection, data);
                    }
                }
            }

            selector.close();
        } catch (IOException e) {
            if (running.get()) {
                log.debug("Error reading from Unix socket", e);
            }
            handler.onDisconnect(connection);
        }
    }

    @Override
    public void close() {
        running.set(false);
        if (readerThread != null) {
            readerThread.shutdownNow();
            readerThread = null;
        }
    }

    /**
     * Handler for client-side connection events.
     */
    public interface ConnectionHandler {
        void onMessage(UnixPeerConnection connection, byte[] data);
        void onDisconnect(UnixPeerConnection connection);
    }

    /**
     * Check if Unix domain sockets are available on this platform.
     */
    public static boolean isAvailable() {
        return UnixSocketServer.isAvailable();
    }
}
