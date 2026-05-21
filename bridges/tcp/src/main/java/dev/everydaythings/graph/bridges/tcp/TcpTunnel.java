package dev.everydaythings.graph.bridges.tcp;

import dev.everydaythings.graph.cryptography.MultiKey;
import dev.everydaythings.graph.network.tunnel.Tunnel;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;

import java.util.ArrayDeque;
import java.util.Optional;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * TCP {@link Tunnel} — one logical bidirectional byte stream over a
 * TCP connection.  Plaintext, unauthenticated; security is added by
 * wrapping with a {@link dev.everydaythings.graph.network.noise.NoiseTunnel
 * NoiseTunnel} (or a future TLS wrapper).
 *
 * <p>This implementation is Netty-backed under the hood, but the type
 * is named for what it carries (TCP bytes), not the library that
 * happens to power it.
 *
 * <p>Bytes received before {@link #onReceive} has registered a consumer
 * are buffered and drained when the consumer is set, so the inevitable
 * race between the accept loop delivering early bytes and the codec
 * handshake being wired doesn't lose data.
 */
final class TcpTunnel implements Tunnel {

    private final Channel channel;
    private final InboundHandler handler;

    TcpTunnel(Channel channel) {
        this.channel = channel;
        this.handler = new InboundHandler();
        channel.pipeline().addLast(handler);
    }

    // ==================================================================================
    // Byte I/O
    // ==================================================================================

    @Override
    public CompletableFuture<Void> send(byte[] bytes) {
        if (!channel.isActive()) {
            throw new IllegalStateException("TcpTunnel is closed");
        }
        CompletableFuture<Void> future = new CompletableFuture<>();
        channel.writeAndFlush(Unpooled.wrappedBuffer(bytes))
                .addListener(f -> {
                    if (f.isSuccess()) future.complete(null);
                    else future.completeExceptionally(f.cause());
                });
        return future;
    }

    @Override
    public void onReceive(Consumer<byte[]> consumer) {
        handler.setReceiver(consumer);
    }

    // ==================================================================================
    // Security properties — plaintext, unauthenticated.  Wrappers add these.
    // ==================================================================================

    @Override public Optional<MultiKey> counterparty() { return Optional.empty(); }
    @Override public boolean isConfidential() { return false; }
    @Override public boolean isAuthenticated() { return false; }

    // ==================================================================================
    // Lifecycle
    // ==================================================================================

    @Override
    public boolean isOpen() {
        return channel.isActive();
    }

    @Override
    public void close() {
        channel.close();
    }

    // ==================================================================================
    // Pipeline handler — drains bytes into the registered consumer, or
    // buffers them until a consumer is registered.
    // ==================================================================================

    private static final class InboundHandler extends ChannelInboundHandlerAdapter {

        private final Queue<byte[]> pending = new ArrayDeque<>();
        private volatile Consumer<byte[]> receiver;

        synchronized void setReceiver(Consumer<byte[]> consumer) {
            this.receiver = consumer;
            if (consumer == null) return;
            byte[] chunk;
            while ((chunk = pending.poll()) != null) {
                consumer.accept(chunk);
            }
        }

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) {
            if (!(msg instanceof ByteBuf buf)) {
                ctx.fireChannelRead(msg);
                return;
            }
            byte[] arr;
            try {
                arr = new byte[buf.readableBytes()];
                buf.readBytes(arr);
            } finally {
                buf.release();
            }
            synchronized (this) {
                Consumer<byte[]> r = receiver;
                if (r != null) {
                    r.accept(arr);
                } else {
                    pending.offer(arr);
                }
            }
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            ctx.close();
        }
    }
}
