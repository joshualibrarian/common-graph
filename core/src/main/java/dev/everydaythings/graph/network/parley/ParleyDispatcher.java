package dev.everydaythings.graph.network.parley;

import dev.everydaythings.graph.ThematicRole;
import dev.everydaythings.graph.datum.Binding;
import dev.everydaythings.graph.datum.Body;
import dev.everydaythings.graph.datum.DatumNode;
import dev.everydaythings.graph.datum.Frame;
import dev.everydaythings.graph.datum.Opaque;
import dev.everydaythings.graph.datum.Record;
import dev.everydaythings.graph.ref.CompoundKey;
import dev.everydaythings.graph.ref.DatumRef;
import dev.everydaythings.graph.ref.HashID;
import dev.everydaythings.graph.ref.ItemRef;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import lombok.extern.log4j.Log4j2;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Abstract base for Parley dispatchers — handles the medium-level concerns
 * common to both sides of any Parley conversation: codec-decoded value
 * routing, HELLO-phase admission, and the records-before-bodies pairing
 * convention.  Subclasses implement the role-specific semantic behavior:
 *
 * <ul>
 *   <li>{@link ServerParleyDispatcher} — incoming bodies become frames
 *       submitted to a local Librarian; responses are wrapped with
 *       SEQUENCE + RESPONSE_TO records and sent back.</li>
 *   <li>{@link ClientParleyDispatcher} — incoming bodies are responses
 *       (routed via RESPONSE_TO to awaiting futures) or pushes (routed to
 *       a push handler).  No local librarian dispatching.</li>
 * </ul>
 *
 * <p>Both sides share:
 *
 * <ul>
 *   <li>HELLO exchange logic (intercepts HELLO bodies as a Parley phase,
 *       not as a dispatched assertion — see [[feedback_frames_self_contained]]).</li>
 *   <li>Record buffering: incoming records are held by their {@link DatumRef}
 *       head until the matching body arrives; the body's {@link #onBody}
 *       call receives the paired records.</li>
 *   <li>Bare-value routing: {@link HashID} → {@link #onRefValue}, {@link String}
 *       → {@link #onTextValue}.  Subclasses interpret these by role
 *       (server: fetch/lookup requests; client: responses to our requests).</li>
 * </ul>
 *
 * <p>Why an abstract base instead of composition over a strategy interface?
 * The HELLO state and record buffer are non-trivial pieces of state that
 * really belong to "the dispatcher" as one object, not to a strategy
 * delegate.  Inheritance keeps the responsibilities co-located.
 */
@Log4j2
public abstract class ParleyDispatcher extends SimpleChannelInboundHandler<Object> {

    protected final RemoteConnection connection;

    /**
     * HELLO-phase state.  Parley conversations have three documented phases:
     * codec point-and-grunt, HELLO exchange, then a stream of typed values.
     * HELLO is the connection-bootstrap handshake — medium-specific and does
     * not flow through normal dispatch.
     *
     * <p>The exchange is symmetric: each side sends its own HELLO, each
     * receives the other's.  {@link #sentHello} tracks whether we have sent
     * ours; {@link #peerAgent} carries the AGENT from the HELLO we received.
     * The connection is "fully admitted" when both have happened.
     *
     * <p>Volatile because admission may be observed across threads.
     */
    private volatile boolean sentHello = false;
    private volatile ItemRef peerAgent = null;

    /**
     * Incoming records buffered until their body arrives.  Keyed by the
     * record's head (DatumRef pointing at the body).  When a body arrives
     * with matching datumId, drain the records here to pair with it.
     *
     * <p>Per [[project_wire_shorthand_2026_06_02]]: records-before-bodies is
     * the convention.  Bodies without buffered records have push semantics
     * (no envelope, no expected response shape).
     */
    private final Map<DatumRef, List<Record>> pendingRecords = new ConcurrentHashMap<>();

    protected ParleyDispatcher(RemoteConnection connection) {
        this.connection = connection;
    }

    // ==================================================================================
    // Public state
    // ==================================================================================

    /** Whether both sides have exchanged HELLOs. */
    public boolean isAdmitted() {
        return sentHello && peerAgent != null;
    }

    /** The peer's claimed AGENT iid, or empty until their HELLO arrives. */
    public Optional<ItemRef> peerAgent() {
        return Optional.ofNullable(peerAgent);
    }

    /**
     * Send a HELLO greeting from {@code senderAgent} on this connection.
     * Used by an initiator (typically a client RemoteLibrarian after the
     * codec handshake completes) to open the HELLO exchange.  The responder
     * side does NOT call this — its HELLO goes out automatically in response
     * to receiving the initiator's HELLO via {@link #handleHello}.
     */
    public void sendHello(ItemRef senderAgent) {
        if (sentHello) return;   // idempotent — initiator only sends once
        sentHello = true;
        try {
            Body hello = Frame.compose(ItemRef.iid(ParleyVocabulary.Hello.KEY))
                    .with(ItemRef.iid(ThematicRole.Agent.KEY), senderAgent)
                    .build()
                    .body();
            connection.send(hello);
        } catch (Exception e) {
            logger.warn("Sending HELLO from {} failed: {}", senderAgent, e.toString());
        }
    }

    // ==================================================================================
    // Netty inbound — dispatch by value type
    // ==================================================================================

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, Object value) {
        switch (value) {
            case Body body      -> handleIncomingBody(body);
            case Record record  -> handleIncomingRecord(record);
            case HashID ref     -> onRefValue(ref);
            case String text    -> onTextValue(text);
            case Opaque op      -> onOpaqueValue(op);
            case Boolean b      -> logger.debug("Parley onBool (reserved): {}", b);
            case Number n       -> logger.debug("Parley onNumber (reserved): {}", n);
            default             -> logger.warn("Parley unexpected top-level value: {}",
                    value == null ? "null" : value.getClass().getName());
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        logger.warn("Parley pipeline error; closing connection", cause);
        connection.close();
    }

    // ==================================================================================
    // Body + record routing — shared mechanics, subclass-specific semantics
    // ==================================================================================

    private void handleIncomingBody(Body body) {
        logger.debug("Parley onBody: head={}", body.headRef());
        // HELLO is the connection-bootstrap handshake (Parley phase 2), not a
        // normal dispatched body.  Intercept at this layer.
        if (isHello(body)) {
            handleHello(body);
            return;
        }
        List<Record> paired = drainPendingRecords(body);
        try {
            onBody(body, paired);
        } catch (Exception e) {
            logger.warn("onBody failed for body {}: {}", body.headRef(), e.toString());
        }
    }

    private void handleIncomingRecord(Record record) {
        if (!(record.head() instanceof DatumRef bodyRef)) {
            // ContentRef-headed records (MATERIALIZED) are not part of the
            // request/response convention; ignore for now.
            logger.debug("Parley onRecord with non-DatumRef head, ignoring: {}", record.head());
            return;
        }
        // Subclass hook for incoming sequence observation (replay / gap detection).
        if (peerAgent != null) {
            observeIncomingRecord(record, peerAgent);
        }
        // Buffer by body's hash so onBody can pair when the body arrives.
        pendingRecords.computeIfAbsent(bodyRef, k -> new ArrayList<>()).add(record);
        logger.debug("Parley onRecord buffered for body {}", bodyRef);
    }

    /**
     * Drain (and remove) any records buffered against this body's datumId.
     * Returns an empty list if none — the body is a push (no envelope).
     */
    private List<Record> drainPendingRecords(Body body) {
        DatumRef bodyId = DatumRef.of(body.datumId());
        List<Record> records = pendingRecords.remove(bodyId);
        return records == null ? List.of() : records;
    }

    // ==================================================================================
    // HELLO phase
    //
    // Symmetric handshake: each side sends its own HELLO body, each receives
    // the other's.  AGENT is required; identity-chain references (INCEPTION,
    // DELEGATION, KA-INCEPTION) are gossip the receiver fetches or trusts
    // based on prior state.  See [[project_hello_bootstrap_2026_06_02]].
    // ==================================================================================

    private void handleHello(Body helloBody) {
        if (peerAgent != null) {
            // Second HELLO mid-conversation is unusual; identity-update flows
            // through ROTATION frames, not re-HELLO.  Log and ignore.
            logger.debug("Parley HELLO arrived post-admission; ignoring");
            return;
        }
        Optional<ItemRef> agent = extractAgent(helloBody);
        if (agent.isEmpty()) {
            logger.warn("Parley HELLO missing AGENT binding; closing connection");
            connection.close();
            return;
        }
        onPeerAdmitted(helloBody, agent.get());
        peerAgent = agent.get();
        logger.info("Parley received HELLO: peerAgent={}", peerAgent);

        // Send our own HELLO back if we have not already (responder path).
        // sendHello is idempotent — initiators that already sent theirs no-op.
        if (!sentHello) {
            sendHello(ourAgent());
        }
    }

    // ==================================================================================
    // Static helpers
    // ==================================================================================

    /** Predicate test: is this body a HELLO greeting? */
    protected static boolean isHello(Body body) {
        ItemRef hello = ItemRef.iid(ParleyVocabulary.Hello.KEY);
        return hello.equals(body.headRef());
    }

    /** Pull AGENT off a HELLO body, if present and shaped as an ItemRef. */
    protected static Optional<ItemRef> extractAgent(Body body) {
        return body.binding(CompoundKey.of(ItemRef.iid(ThematicRole.Agent.KEY)))
                .map(Binding::target)
                .filter(t -> t instanceof ItemRef)
                .map(t -> (ItemRef) t);
    }

    /**
     * Pull the SEQUENCE binding value from the first record carrying one,
     * or null if none.  Used by server-side dispatchers to RESPONSE_TO the
     * caller, and by client-side dispatchers to read the server's outgoing
     * sequence on responses.
     */
    protected static Long extractBindingLong(List<Record> records, ItemRef role) {
        if (records.isEmpty()) return null;
        for (Record r : records) {
            for (DatumNode node : r.entries()) {
                if (node instanceof Binding b
                        && role.equals(b.role())
                        && b.target() instanceof Long value) {
                    return value;
                }
            }
        }
        return null;
    }

    /** Convenience: extract SEQUENCE from a list of paired records. */
    protected static Long extractSequence(List<Record> records) {
        return extractBindingLong(records, ItemRef.iid(ParleyVocabulary.Sequence.KEY));
    }

    /** Convenience: extract RESPONSE_TO from a list of paired records. */
    protected static Long extractResponseTo(List<Record> records) {
        return extractBindingLong(records, ItemRef.iid(ParleyVocabulary.ResponseTo.KEY));
    }

    // ==================================================================================
    // Subclass extension points
    // ==================================================================================

    /** Our agent iid — used as AGENT on outgoing HELLO. */
    protected abstract ItemRef ourAgent();

    /**
     * Process an incoming body (non-HELLO) with any records paired by datumId.
     * Server-side dispatches to the librarian and sends responses; client-side
     * routes by RESPONSE_TO or to a push handler.
     */
    protected abstract void onBody(Body body, List<Record> pairedRecords);

    /**
     * Bare {@link HashID} on the wire — fetch shorthand.  Server-side resolves
     * via librarian.fetch*; client-side treats as a fetch-response routing
     * concern (today: log).
     */
    protected abstract void onRefValue(HashID ref);

    /**
     * Bare {@link String} on the wire — lookup shorthand.  Server-side resolves
     * via librarian.lookup; client-side treats as a lookup-response.
     */
    protected abstract void onTextValue(String text);

    /**
     * Called when a peer's HELLO has been validated and is about to be
     * accepted.  Server-side typically persists the HELLO (it IS the
     * relationship history); client-side may just log.
     */
    protected abstract void onPeerAdmitted(Body helloBody, ItemRef peerIid);

    /**
     * Called when an incoming record (with DatumRef head) is buffered, after
     * peer admission.  Server-side uses this to observe the peer's outgoing
     * sequence for replay / gap detection.  Client-side may skip.
     */
    protected void observeIncomingRecord(Record record, ItemRef peerIid) {
        // default no-op
    }

    /** Opaque handling — default no-op; specialized handlers override. */
    protected void onOpaqueValue(Opaque op) {
        logger.debug("Parley Opaque (not yet dispatched): {}", op);
    }
}
