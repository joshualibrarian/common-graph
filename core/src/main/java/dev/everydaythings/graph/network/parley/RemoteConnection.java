package dev.everydaythings.graph.network.parley;

import dev.everydaythings.graph.datum.Datum;
import dev.everydaythings.graph.encoding.Encoding;
import dev.everydaythings.graph.id.HashID;
import dev.everydaythings.graph.id.ItemRef;
import dev.everydaythings.graph.network.tunnel.Tunnel;
import lombok.extern.log4j.Log4j2;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;

/**
 * A live Parley conversation across a {@link Tunnel} — the result of a
 * successful {@link Parley#connect} or {@link Parley#accept}.
 *
 * <p>Holds the tunnel, the codec agreed during point-and-grunt, and the
 * {@link Encoding} used to serialize outbound values. Incoming bytes are
 * already wired to a streaming parser whose events flow into the Librarian;
 * RemoteConnection is the <i>send</i> side of the conversation.
 *
 * <h2>Layering (bottom-up)</h2>
 * <ol>
 *   <li>Raw byte transport (TCP, Unix domain socket, Reticulum, ...)</li>
 *   <li>Optional security wrapper (Noise, TLS) — produces a {@link Tunnel}
 *       whose {@code isConfidential()}/{@code isAuthenticated()} declare its
 *       guarantees</li>
 *   <li>Codec point-and-grunt (Parley phase 1)</li>
 *   <li>Stream of self-describing values (Parley phase 2) — this object</li>
 * </ol>
 */
@Log4j2
public final class RemoteConnection implements AutoCloseable {

    private final Tunnel tunnel;
    private final ItemRef agreedCodec;
    private final Encoding encoder;

    public RemoteConnection(Tunnel tunnel, ItemRef agreedCodec, Encoding encoder) {
        this.tunnel = Objects.requireNonNull(tunnel, "tunnel");
        this.agreedCodec = Objects.requireNonNull(agreedCodec, "agreedCodec");
        this.encoder = Objects.requireNonNull(encoder, "encoder");
    }

    /** The underlying byte tunnel. */
    public Tunnel tunnel() {
        return tunnel;
    }

    /** The codec agreed during point-and-grunt. */
    public ItemRef agreedCodec() {
        return agreedCodec;
    }

    // ==================================================================================
    // Sending
    // ==================================================================================

    /** Send a {@link Datum} (Body, Record, or Frame) to the counterparty. */
    public CompletableFuture<Void> send(Datum datum) {
        return tunnel.send(encoder.encode(datum));
    }

    /**
     * Send a bare reference — the "point-and-grunt" shorthand for
     * "send me this." The peer's sink receives an {@code onRef} event.
     */
    public CompletableFuture<Void> send(HashID ref) {
        return tunnel.send(encoder.encode(ref));
    }

    /**
     * Send a bare text string — the lightweight shorthand for "look this up
     * in your token dictionary." The peer's sink receives an {@code onText}
     * event.
     */
    public CompletableFuture<Void> sendText(String text) {
        return tunnel.send(encoder.encode(text));
    }

    // ==================================================================================
    // Lifecycle
    // ==================================================================================

    /** True if the underlying tunnel is alive. */
    public boolean isOpen() {
        return tunnel.isOpen();
    }

    @Override
    public void close() {
        tunnel.close();
    }
}
