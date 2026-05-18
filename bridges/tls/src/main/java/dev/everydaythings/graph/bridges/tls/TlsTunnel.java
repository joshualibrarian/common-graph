package dev.everydaythings.graph.bridges.tls;

import dev.everydaythings.graph.identity.MultiKey;
import dev.everydaythings.graph.network.tunnel.Tunnel;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslHandler;
import lombok.extern.log4j.Log4j2;

import java.util.ArrayDeque;
import java.util.Objects;
import java.util.Optional;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * {@link Tunnel} wrapper that adds TLS confidentiality and counterparty
 * authentication to any underlying Tunnel.
 *
 * <h2>How it works</h2>
 *
 * <p>Netty's {@link SslHandler} does all the actual TLS work.  We host it in
 * an {@link EmbeddedChannel} — a pipeline-without-a-socket — so the same
 * battle-tested code that powers HTTPS in Netty's normal mode runs here
 * without needing a Netty {@link io.netty.channel.Channel Channel} to ride
 * on.  Bytes flow:
 *
 * <pre>
 *   wire (encrypted)              application (cleartext)
 *
 *   underlying.onReceive  ─►  embedded.writeInbound()
 *                                   │
 *                                   ▼
 *                              SslHandler decrypts
 *                                   │
 *                                   ▼
 *                              plaintext handler  ─►  caller's onReceive()
 *
 *   caller.send()  ─►  embedded.writeOutbound()
 *                            │
 *                            ▼
 *                       SslHandler encrypts
 *                            │
 *                            ▼
 *                       drain embedded.readOutbound()  ─►  underlying.send()
 * </pre>
 *
 * <p>The TLS handshake runs as soon as the wrapper is constructed: SslHandler
 * produces the initial handshake bytes (ClientHello for clients; waits for
 * the peer for servers), they drain to the underlying Tunnel, the response
 * comes back through {@code onReceive}, and so on until handshake completes.
 *
 * <h2>Lifecycle</h2>
 *
 * <p>{@link #handshakeFuture} completes successfully once TLS is fully
 * established (records can flow either direction).  Calling {@link #send}
 * before handshake completes defers the write until handshake is done; if
 * handshake fails, the future fails and pending writes complete
 * exceptionally.
 *
 * <p>Calling {@link #close} closes both the embedded TLS pipeline and the
 * underlying Tunnel.  Idempotent.
 *
 * <h2>Security properties</h2>
 *
 * <ul>
 *   <li>{@link #isConfidential()}: always {@code true} once TLS records flow.</li>
 *   <li>{@link #isAuthenticated()}: {@code true} when the peer's cert was
 *       verified and is available via {@link #counterparty()}.  In stock
 *       configurations this means the cert chain validated against the
 *       trust store the caller's {@link SslContext} was built with.</li>
 *   <li>{@link #counterparty()}: provisional implementation returns empty
 *       pending the cert sememe / trust resolution work (see
 *       {@code docs/network-stack.md} migration step 5).  When that lands,
 *       this will return a {@link MultiKey} derived from the verified peer
 *       cert's public key.</li>
 * </ul>
 *
 * <h2>Threading</h2>
 *
 * <p>EmbeddedChannel is not inherently thread-safe.  This wrapper
 * synchronizes all access on a single lock — the simplest correctness story
 * and entirely fine for the use cases on the roadmap (Parley conversations,
 * KERI HTTP, etc., none of which need per-tunnel megabit throughput).
 * Optimize when a workload demands it.
 */
@Log4j2
public final class TlsTunnel implements Tunnel {

    private final Tunnel underlying;
    private final SslHandler sslHandler;
    private final EmbeddedChannel embedded;
    private final CompletableFuture<Void> handshakeFuture = new CompletableFuture<>();
    private final Queue<byte[]> pendingPlaintext = new ArrayDeque<>();
    private final Object lock = new Object();
    private volatile Consumer<byte[]> receiver;
    private volatile boolean closed = false;

    // ==================================================================================
    // Factories
    // ==================================================================================

    /**
     * Wrap {@code underlying} with TLS in client mode.  No SNI / hostname
     * verification.  Suitable for tests, IP-only connections, and any
     * deployment where the peer is identified by raw key rather than by
     * hostname (e.g., AID-as-cert-key authentication).
     */
    public static TlsTunnel client(Tunnel underlying, SslContext sslContext) {
        Objects.requireNonNull(sslContext, "sslContext");
        if (!sslContext.isClient()) {
            throw new IllegalArgumentException("sslContext is configured for server mode");
        }
        SslHandler handler = sslContext.newHandler(ByteBufAllocator.DEFAULT);
        return new TlsTunnel(underlying, handler);
    }

    /**
     * Wrap {@code underlying} with TLS in client mode, with SNI + hostname
     * verification against {@code peerHost}.  Standard PKI client mode.
     */
    public static TlsTunnel client(Tunnel underlying, SslContext sslContext,
                                   String peerHost, int peerPort) {
        Objects.requireNonNull(sslContext, "sslContext");
        Objects.requireNonNull(peerHost, "peerHost");
        if (!sslContext.isClient()) {
            throw new IllegalArgumentException("sslContext is configured for server mode");
        }
        SslHandler handler = sslContext.newHandler(ByteBufAllocator.DEFAULT, peerHost, peerPort);
        return new TlsTunnel(underlying, handler);
    }

    /**
     * Wrap {@code underlying} with TLS in server mode.  The SslContext must
     * carry the server's cert + private key.
     */
    public static TlsTunnel server(Tunnel underlying, SslContext sslContext) {
        Objects.requireNonNull(sslContext, "sslContext");
        if (!sslContext.isServer()) {
            throw new IllegalArgumentException("sslContext is configured for client mode");
        }
        SslHandler handler = sslContext.newHandler(ByteBufAllocator.DEFAULT);
        return new TlsTunnel(underlying, handler);
    }

    // ==================================================================================
    // Construction
    // ==================================================================================

    private TlsTunnel(Tunnel underlying, SslHandler sslHandler) {
        this.underlying = Objects.requireNonNull(underlying, "underlying");
        this.sslHandler = Objects.requireNonNull(sslHandler, "sslHandler");

        ChannelInboundHandlerAdapter plaintextHandler = new ChannelInboundHandlerAdapter() {
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
                deliverPlaintext(arr);
            }

            @Override
            public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
                logger.warn("TLS pipeline exception", cause);
                if (!handshakeFuture.isDone()) {
                    handshakeFuture.completeExceptionally(cause);
                }
                close();
            }
        };

        this.embedded = new EmbeddedChannel(sslHandler, plaintextHandler);

        // Mirror SslHandler's handshake completion into our future.
        sslHandler.handshakeFuture().addListener(f -> {
            if (f.isSuccess()) {
                handshakeFuture.complete(null);
            } else {
                handshakeFuture.completeExceptionally(f.cause());
                close();
            }
        });

        // Underlying inbound bytes feed into the embedded TLS pipeline.
        underlying.onReceive(this::handleEncryptedInbound);

        // Handshake bytes the SslHandler emitted during construction (ClientHello
        // for a client; nothing yet for a server) need to leave now.
        drainOutboundToUnderlying();
    }

    // ==================================================================================
    // Byte I/O
    // ==================================================================================

    @Override
    public CompletableFuture<Void> send(byte[] bytes) {
        Objects.requireNonNull(bytes, "bytes");
        if (closed) {
            throw new IllegalStateException("TlsTunnel is closed");
        }
        // Defer writes until the handshake completes — Parley codec point-and-grunt
        // and any other application traffic should never race the handshake.
        return handshakeFuture.thenCompose(v -> {
            synchronized (lock) {
                if (closed) {
                    return CompletableFuture.failedFuture(
                            new IllegalStateException("TlsTunnel was closed before send completed"));
                }
                embedded.writeOutbound(Unpooled.wrappedBuffer(bytes));
                drainOutboundToUnderlying();
            }
            return CompletableFuture.completedFuture(null);
        });
    }

    @Override
    public void onReceive(Consumer<byte[]> consumer) {
        synchronized (lock) {
            this.receiver = consumer;
            if (consumer == null) return;
            byte[] chunk;
            while ((chunk = pendingPlaintext.poll()) != null) {
                consumer.accept(chunk);
            }
        }
    }

    /** Future that completes when the TLS handshake succeeds, or fails on handshake failure. */
    public CompletableFuture<Void> handshakeFuture() {
        return handshakeFuture;
    }

    private void handleEncryptedInbound(byte[] encrypted) {
        synchronized (lock) {
            if (closed) return;
            embedded.writeInbound(Unpooled.wrappedBuffer(encrypted));
            drainOutboundToUnderlying();
        }
    }

    private void drainOutboundToUnderlying() {
        // Called under `lock`.
        ByteBuf out;
        while ((out = embedded.readOutbound()) != null) {
            byte[] arr;
            try {
                arr = new byte[out.readableBytes()];
                out.readBytes(arr);
            } finally {
                out.release();
            }
            if (closed || !underlying.isOpen()) {
                // SslHandler emits a close_notify alert after a handshake or
                // record-level failure; if we've already torn the underlying
                // down (typically because we're handling that same failure),
                // those alert bytes have nowhere to go.  Safe to drop.
                continue;
            }
            try {
                underlying.send(arr);
            } catch (RuntimeException e) {
                // Underlying refused the write between our open-check and the
                // send.  Same scenario; drop and continue draining.
                logger.debug("Dropped {} outbound bytes after underlying closed: {}",
                        arr.length, e.toString());
            }
        }
    }

    private void deliverPlaintext(byte[] plaintext) {
        // Called from inside embedded.writeInbound, already under `lock`.
        Consumer<byte[]> r = receiver;
        if (r != null) {
            r.accept(plaintext);
        } else {
            pendingPlaintext.offer(plaintext);
        }
    }

    // ==================================================================================
    // Security properties
    // ==================================================================================

    /**
     * Provisional: always returns empty.  Wiring up the conversion from the
     * SSLSession's peer cert public key to a {@link MultiKey} lands together
     * with the cert sememe + trust resolution work
     * (see {@code docs/network-stack.md} migration step 5).
     */
    @Override
    public Optional<MultiKey> counterparty() {
        return Optional.empty();
    }

    @Override
    public boolean isConfidential() {
        return true;
    }

    @Override
    public boolean isAuthenticated() {
        return counterparty().isPresent();
    }

    // ==================================================================================
    // Lifecycle
    // ==================================================================================

    @Override
    public boolean isOpen() {
        return !closed && underlying.isOpen() && embedded.isOpen();
    }

    @Override
    public void close() {
        synchronized (lock) {
            if (closed) return;
            closed = true;
            try {
                embedded.close();
            } catch (Exception ignored) {
                // best-effort
            }
            underlying.close();
        }
    }
}
