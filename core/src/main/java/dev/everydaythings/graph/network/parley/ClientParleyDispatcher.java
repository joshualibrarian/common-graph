package dev.everydaythings.graph.network.parley;

import dev.everydaythings.graph.datum.Body;
import dev.everydaythings.graph.datum.Record;
import dev.everydaythings.graph.ref.HashID;
import dev.everydaythings.graph.ref.ItemRef;
import lombok.extern.log4j.Log4j2;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Client-side concrete {@link ParleyDispatcher}: incoming bodies are either
 * responses to outgoing requests (routed by RESPONSE_TO to awaiting futures)
 * or pushes from the server (routed to the registered push handler).
 *
 * <p>Used by {@code RemoteLibrarian} after a successful Parley codec
 * handshake.  Multiplexing of concurrent in-flight requests is keyed by the
 * client's own outgoing SEQUENCE; the server echoes that sequence as
 * RESPONSE_TO on its responses, and the client looks up the awaiting future
 * by that key.
 *
 * <h2>What clients receive</h2>
 *
 * <ul>
 *   <li><b>Responses</b> — bodies preceded by a record whose RESPONSE_TO
 *       binding names our request's SEQUENCE.  Routed to a future registered
 *       via {@link #registerAwaiting}.</li>
 *   <li><b>Pushes</b> — bodies with no preceding record (or with records
 *       lacking RESPONSE_TO).  Routed to the push handler.  Scene graph
 *       updates flow this way.</li>
 *   <li><b>HELLO</b> — handled by the abstract base; client typically sends
 *       its HELLO first (initiator) and just observes the server's reply.</li>
 *   <li><b>Bare ref / text</b> — these arrive as server responses to bare-
 *       shorthand requests we made.  No standard correlation mechanism is
 *       wired yet; treated as pushes for now.</li>
 * </ul>
 *
 * <p>"Async streaming" responses (multi-response operations like LOOKUP) are
 * deferred until a use case forces the design — see
 * [[project_parley_multiplexing_2026_06_02]].  For now, single-response
 * patterns work via {@link #registerAwaiting}.
 */
@Log4j2
public final class ClientParleyDispatcher extends ParleyDispatcher {

    private final ItemRef sessionAgent;
    private final Consumer<Body> pushHandler;

    /**
     * Outgoing-request futures keyed by our SEQUENCE.  When the server
     * replies, its record carries RESPONSE_TO matching our sequence; we look
     * up the future, complete it with the response body, remove it.
     */
    private final Map<Long, CompletableFuture<Body>> awaiting = new ConcurrentHashMap<>();

    /**
     * Construct a client dispatcher.
     *
     * @param sessionAgent  the session's iid (used as AGENT on our HELLO).
     * @param connection    the Parley connection (already past codec handshake).
     * @param pushHandler   callback for unsolicited server bodies (scene graphs etc.);
     *                      may be {@code null} for headless / eval-mode clients.
     */
    public ClientParleyDispatcher(ItemRef sessionAgent,
                                  RemoteConnection connection,
                                  Consumer<Body> pushHandler) {
        super(connection);
        this.sessionAgent = Objects.requireNonNull(sessionAgent, "sessionAgent");
        this.pushHandler = pushHandler;
    }

    @Override
    protected ItemRef ourAgent() {
        return sessionAgent;
    }

    @Override
    protected void onPeerAdmitted(Body helloBody, ItemRef peerIid) {
        // Client doesn't persist peer HELLOs — the server owns the relationship
        // history; we just remember peerAgent in the base class for the
        // duration of this connection.
        logger.debug("Client received server HELLO from peerAgent={}", peerIid);
    }

    // ==================================================================================
    // Body routing — responses vs pushes
    // ==================================================================================

    @Override
    protected void onBody(Body body, List<Record> pairedRecords) {
        Long responseTo = extractResponseTo(pairedRecords);
        if (responseTo != null) {
            CompletableFuture<Body> awaiter = awaiting.remove(responseTo);
            if (awaiter != null) {
                awaiter.complete(body);
                return;
            }
            logger.debug("Client received body with RESPONSE_TO={} but no awaiting future; treating as push",
                    responseTo);
        }
        deliverPush(body);
    }

    @Override
    protected void onRefValue(HashID ref) {
        // Bare-ref arrivals at the client are responses to fetch shorthands
        // we issued; no standard correlation yet (bare-ref requests carry no
        // CORRELATION).  Until that lands, log and drop.
        logger.debug("Client received bare ref (no correlation mechanism wired): {}", ref);
    }

    @Override
    protected void onTextValue(String text) {
        // Same shape as bare-ref: server's response to a lookup-shorthand,
        // no correlation mechanism wired yet.
        logger.debug("Client received bare text (no correlation mechanism wired): {}", text);
    }

    // ==================================================================================
    // Future registration — outgoing call sites use this before sending
    // ==================================================================================

    /**
     * Register a future to be completed when a response body with
     * {@code RESPONSE_TO → mySequence} arrives.  Call BEFORE sending the
     * request to avoid a race where the response arrives before the future
     * is registered.
     *
     * @param mySequence  the SEQUENCE this client placed on the outgoing
     *                    request record; the server will echo it as
     *                    RESPONSE_TO on responses.
     * @return a future that completes with the first response body bearing
     *         the matching RESPONSE_TO.  Subsequent responses (if any) for
     *         the same sequence go to the push handler.
     */
    public CompletableFuture<Body> registerAwaiting(long mySequence) {
        CompletableFuture<Body> future = new CompletableFuture<>();
        awaiting.put(mySequence, future);
        return future;
    }

    /**
     * Cancel and remove an awaiting future for the given sequence.  Used
     * when an outgoing call times out or the caller no longer wants the
     * response.
     */
    public void cancelAwaiting(long mySequence) {
        CompletableFuture<Body> future = awaiting.remove(mySequence);
        if (future != null) {
            future.cancel(false);
        }
    }

    // ==================================================================================
    // Push delivery
    // ==================================================================================

    private void deliverPush(Body body) {
        if (pushHandler == null) {
            logger.debug("Client received push but no handler registered: head={}", body.headRef());
            return;
        }
        try {
            pushHandler.accept(body);
        } catch (Exception e) {
            logger.warn("Push handler threw for body {}: {}", body.headRef(), e.toString());
        }
    }
}
