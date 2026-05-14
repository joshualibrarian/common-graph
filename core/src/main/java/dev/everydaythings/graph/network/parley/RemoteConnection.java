package dev.everydaythings.graph.network.parley;

import dev.everydaythings.graph.datum.Datum;
import dev.everydaythings.graph.id.ContentID;
import lombok.extern.log4j.Log4j2;

import java.util.concurrent.CompletableFuture;

/**
 * A Parley connection over a network transport — one wire-level conversation
 * with a remote party, wrapping a {@link Tunnel}.
 *
 * <p>RemoteConnection is the runtime tracking of one Parley conversation: the
 * codec agreed during point-and-grunt, the tunnel carrying bytes, and any
 * other per-connection state. {@link Parley} owns the set of active
 * RemoteConnections.
 *
 * <h2>Layering (bottom-up)</h2>
 * <ol>
 *   <li>Raw socket (TCP, Unix domain socket, etc.)</li>
 *   <li>Length-prefixed framing</li>
 *   <li>{@link Tunnel} — Noise XX or TLS — provides confidentiality + auth</li>
 *   <li>Codec point-and-grunt (Parley phase 1) — over plaintext-inside-the-tunnel</li>
 *   <li>Stream of self-describing chunks (Parley phase 2) — Datums + blobs</li>
 * </ol>
 *
 * <p>The tunnel is established <em>before</em> the codec handshake.
 * Point-and-grunt speaks plaintext across an already-secure channel.
 *
 * <p>There is no LocalConnection counterpart: in-VM clients hold a direct
 * {@code Librarian} reference and don't speak Parley at all.
 *
 * <p>STUB — the actual Netty pipeline, byte plumbing, and stream wiring will
 * be adapted from the existing {@code network/transport/} code (PeerServer,
 * PeerClient, TransportEncryptionHandler, TransportCrypto) once we wire this
 * in.
 */
@Log4j2
public final class RemoteConnection implements AutoCloseable {

    private final Tunnel tunnel;

    public RemoteConnection(Tunnel tunnel) {
        this.tunnel = tunnel;
    }

    public Tunnel tunnel() {
        return tunnel;
    }

    /** Send a Datum (Body, Record, or Frame) to the counterparty. */
    public CompletableFuture<Void> send(Datum datum) {
        // TODO: encode datum via librarian.encoder() and push through tunnel
        return CompletableFuture.completedFuture(null);
    }

    /** Send a raw content blob to the counterparty. */
    public CompletableFuture<Void> send(ContentID cid, byte[] content) {
        // TODO: push raw content through tunnel (codec-prefixed if needed)
        return CompletableFuture.completedFuture(null);
    }

    /** True if the underlying tunnel is alive. */
    public boolean isOpen() {
        return tunnel != null && tunnel.isOpen();
    }

    @Override
    public void close() {
        if (tunnel != null) {
            tunnel.close();
        }
    }
}
