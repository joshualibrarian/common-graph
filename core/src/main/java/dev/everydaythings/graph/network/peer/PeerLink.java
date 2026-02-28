package dev.everydaythings.graph.network.peer;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Flow;

/**
 * A protocol-level link to a remote peer (Librarian).
 *
 * <p>PeerLink provides request/response communication and event streaming
 * with another node in the network. This is distinct from Session, which
 * is for frontend-to-backend (UI → Librarian) communication.
 */
public interface PeerLink extends AutoCloseable {
    RemotePeer peer();
    <Req, Resp> CompletableFuture<Resp> request(Req req);
    Flow.Publisher<PeerEvent> events();
    @Override void close();
}