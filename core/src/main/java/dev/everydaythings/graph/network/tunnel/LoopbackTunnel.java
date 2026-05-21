package dev.everydaythings.graph.network.tunnel;

import dev.everydaythings.graph.cryptography.MultiKey;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * In-memory bidirectional pipe implementing {@link Tunnel}. Test-only.
 *
 * <p>A loopback tunnel is a pair of endpoints. Each endpoint's {@link #send}
 * delivers bytes synchronously to the other endpoint's registered receiver.
 * No threading, no encryption, no identity verification — just a way to wire
 * up two Parley parties in one process for testing.
 *
 * <p>Construct a pair via {@link #pair}; the returned record holds two
 * Tunnels, one for each side of the conversation.
 *
 * <h2>Semantics</h2>
 * <ul>
 *   <li><b>Synchronous delivery.</b> {@link #send} invokes the counterparty's
 *       receiver before returning. The returned future is always already
 *       completed.</li>
 *   <li><b>No chunking.</b> Each {@code send} call delivers as one
 *       {@code onReceive} invocation. Real tunnels may split or coalesce;
 *       LoopbackTunnel doesn't.</li>
 *   <li><b>No buffering.</b> Bytes sent before a receiver is registered are
 *       dropped. Register receivers before sending.</li>
 *   <li><b>Symmetric close.</b> Closing one endpoint closes the other.</li>
 * </ul>
 *
 * <p>For most tests, set up both endpoints' receivers, then exchange bytes
 * either direction.
 */
public final class LoopbackTunnel implements Tunnel {

    /** Two ends of a loopback pipe. */
    public record Pair(LoopbackTunnel a, LoopbackTunnel b) {}

    /** Construct a new loopback pair. Each end is initially open with no receiver. */
    public static Pair pair() {
        LoopbackTunnel a = new LoopbackTunnel();
        LoopbackTunnel b = new LoopbackTunnel();
        a.peer = b;
        b.peer = a;
        return new Pair(a, b);
    }

    private LoopbackTunnel peer;
    private final AtomicReference<Consumer<byte[]>> receiver = new AtomicReference<>();
    private volatile boolean open = true;

    private LoopbackTunnel() {}

    // ==================================================================================
    // Byte I/O
    // ==================================================================================

    @Override
    public CompletableFuture<Void> send(byte[] bytes) {
        if (!open) {
            throw new IllegalStateException("LoopbackTunnel is closed");
        }
        Consumer<byte[]> peerReceiver = peer.receiver.get();
        if (peerReceiver != null) {
            peerReceiver.accept(bytes);
        }
        // If peer has no receiver, bytes are dropped silently. Tests should
        // register receivers before sending.
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public void onReceive(Consumer<byte[]> consumer) {
        receiver.set(consumer);
    }

    // ==================================================================================
    // Security properties — none. LoopbackTunnel is test-only.
    // ==================================================================================

    @Override
    public Optional<MultiKey> counterparty() {
        return Optional.empty();
    }

    @Override
    public boolean isConfidential() {
        return false;
    }

    @Override
    public boolean isAuthenticated() {
        return false;
    }

    // ==================================================================================
    // Lifecycle
    // ==================================================================================

    @Override
    public boolean isOpen() {
        return open;
    }

    /**
     * Close this endpoint. Also closes the peer. Idempotent.
     */
    @Override
    public void close() {
        if (!open) return;
        open = false;
        // Cascade close to the peer if it's still open.
        if (peer != null && peer.open) {
            peer.close();
        }
    }
}
