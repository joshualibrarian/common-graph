package dev.everydaythings.graph.network.peer;

import dev.everydaythings.graph.network.ProtocolMessage;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.util.AttributeKey;
import lombok.Getter;
import lombok.experimental.Accessors;

import java.net.InetSocketAddress;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * Represents a connection to a remote peer.
 *
 * <p>Wraps a Netty channel and provides a simple API for sending messages.
 * Messages are automatically length-prefixed by the pipeline.
 *
 * <p>Per-connection state (like {@link RemotePeer}) is stored as Netty
 * channel attributes, automatically cleaned up on channel close.
 */
@Accessors(fluent = true)
public class PeerConnection implements AutoCloseable {

    private static final AttributeKey<RemotePeer> REMOTE_PEER_KEY =
            AttributeKey.valueOf("remotePeer");

    @Getter
    private final Channel channel;

    public PeerConnection(Channel channel) {
        this.channel = channel;
    }

    // =========================================================================
    // Sending
    // =========================================================================

    /**
     * Send a typed protocol message to the peer.
     *
     * <p>The message will be encoded by the ProtocolCodec and
     * length-prefixed automatically by the pipeline.
     *
     * @param message The protocol message to send
     * @return Future that completes when the write is flushed
     */
    public CompletableFuture<Void> send(ProtocolMessage message) {
        CompletableFuture<Void> future = new CompletableFuture<>();
        ChannelFuture cf = channel.writeAndFlush(message);
        cf.addListener(f -> {
            if (f.isSuccess()) {
                future.complete(null);
            } else {
                future.completeExceptionally(f.cause());
            }
        });
        return future;
    }

    /**
     * Send raw bytes to the peer.
     *
     * <p>The message will be length-prefixed automatically by the pipeline.
     *
     * @param data The message bytes (will be CBOR in practice)
     * @return Future that completes when the write is flushed
     */
    public CompletableFuture<Void> send(byte[] data) {
        CompletableFuture<Void> future = new CompletableFuture<>();
        ChannelFuture cf = channel.writeAndFlush(Unpooled.wrappedBuffer(data));
        cf.addListener(f -> {
            if (f.isSuccess()) {
                future.complete(null);
            } else {
                future.completeExceptionally(f.cause());
            }
        });
        return future;
    }

    // =========================================================================
    // Per-Connection State (Channel Attributes)
    // =========================================================================

    /**
     * Get the remote peer info attached to this connection.
     */
    public Optional<RemotePeer> remotePeer() {
        return Optional.ofNullable(channel.attr(REMOTE_PEER_KEY).get());
    }

    /**
     * Attach remote peer info to this connection.
     */
    public void setRemotePeer(RemotePeer peer) {
        channel.attr(REMOTE_PEER_KEY).set(peer);
    }

    // =========================================================================
    // Connection Info
    // =========================================================================

    /**
     * Get the remote address of this connection.
     */
    public InetSocketAddress remoteAddress() {
        return (InetSocketAddress) channel.remoteAddress();
    }

    /**
     * Get the local address of this connection.
     */
    public InetSocketAddress localAddress() {
        return (InetSocketAddress) channel.localAddress();
    }

    /**
     * Check if the connection is still open.
     */
    public boolean isOpen() {
        return channel.isOpen();
    }

    /**
     * Close the connection.
     */
    @Override
    public void close() {
        channel.close();
    }

    @Override
    public String toString() {
        return "PeerConnection[" + remoteAddress() + "]";
    }
}
