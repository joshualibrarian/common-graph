package dev.everydaythings.graph.network.peer;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.util.concurrent.CompletableFuture;

/**
 * A connection to a peer over a Unix domain socket.
 *
 * <p>Unix socket connections are always local, so there's no remote address
 * in the traditional sense. The peer is identified by the connection itself.
 *
 * <p>Uses Java 16+ native Unix domain socket support.
 */
public class UnixPeerConnection {

    private static final int LENGTH_PREFIX_SIZE = 4;

    private final SocketChannel channel;
    private final long connectedAt;

    public UnixPeerConnection(SocketChannel channel) {
        this.channel = channel;
        this.connectedAt = System.currentTimeMillis();
    }

    /**
     * Send data to the peer.
     *
     * <p>Messages are framed with a 4-byte length prefix.
     */
    public CompletableFuture<Void> send(byte[] data) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                // Create buffer with length prefix + data
                ByteBuffer buffer = ByteBuffer.allocate(LENGTH_PREFIX_SIZE + data.length);
                buffer.putInt(data.length);
                buffer.put(data);
                buffer.flip();

                // Write entire buffer
                while (buffer.hasRemaining()) {
                    channel.write(buffer);
                }
                return null;
            } catch (IOException e) {
                throw new RuntimeException("Failed to send data", e);
            }
        });
    }

    /**
     * Close the connection.
     */
    public CompletableFuture<Void> close() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                channel.close();
                return null;
            } catch (IOException e) {
                throw new RuntimeException("Failed to close connection", e);
            }
        });
    }

    /**
     * Check if the connection is active.
     */
    public boolean isActive() {
        return channel.isOpen() && channel.isConnected();
    }

    /**
     * Get the underlying NIO channel.
     */
    public SocketChannel channel() {
        return channel;
    }

    /**
     * Get when this connection was established.
     */
    public long connectedAt() {
        return connectedAt;
    }

    @Override
    public String toString() {
        return "UnixPeerConnection[" + channel.hashCode() + "]";
    }
}
