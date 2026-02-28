package dev.everydaythings.graph.network;

import dev.everydaythings.graph.item.Manifest;
import dev.everydaythings.graph.item.id.ContentID;
import dev.everydaythings.graph.item.id.ItemID;
import dev.everydaythings.graph.item.relation.Relation;
import dev.everydaythings.graph.network.message.Delivery;
import dev.everydaythings.graph.network.message.Heartbeat;
import dev.everydaythings.graph.network.message.ProtocolMessage;
import dev.everydaythings.graph.network.message.Request;
import dev.everydaythings.graph.network.peer.PeerConnection;
import dev.everydaythings.graph.network.peer.PeerLink;
import dev.everydaythings.graph.network.peer.RemotePeer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/**
 * The Common Graph peer-to-peer protocol handler.
 *
 * <p>CG Protocol is beautifully simple - just two message types:
 * <ul>
 *   <li>{@link Request} - "I want something" (items, content, relations)</li>
 *   <li>{@link Delivery} - "Here's something" (manifests, bytes, relations, not-found)</li>
 * </ul>
 *
 * <p>Protocol flow:
 * <ol>
 *   <li>On connect: Both sides send unsolicited Delivery with their Librarian manifest (handshake)</li>
 *   <li>After handshake: Either side can send Request, peer responds with Delivery</li>
 *   <li>Subscriptions: Request with subscribe=true gets streaming Delivery updates</li>
 * </ol>
 *
 * <p>Per-connection state (RemotePeer) is stored as Netty channel attributes
 * on the PeerConnection, auto-cleaned on disconnect.
 */
public class CgProtocol implements Protocol {

    private static final Logger log = LogManager.getLogger(CgProtocol.class);

    public static final String PROTOCOL_ID = "cg/1";

    private static final int MAX_SUBSCRIPTIONS_PER_PEER = 100;
    private static final long REQUEST_TIMEOUT_SECONDS = 30;

    private final ProtocolContext context;

    // Monotonic request ID counter (avoids System.nanoTime() collisions)
    private final AtomicLong requestIdCounter = new AtomicLong(1);

    // Track pending requests (for correlating responses)
    private final Map<Long, PendingRequest> pendingRequests = new ConcurrentHashMap<>();

    // Subscription tracking (relation filters per connection)
    private final Map<PeerConnection, Set<Request.Target.Relations>> subscriptions = new ConcurrentHashMap<>();

    // Track active peer connections for enumeration
    private final Set<PeerConnection> activePeers = ConcurrentHashMap.newKeySet();

    public CgProtocol(ProtocolContext context) {
        this.context = context;
    }

    @Override
    public String protocolId() {
        return PROTOCOL_ID;
    }

    @Override
    public void onLink(PeerLink link, ProtocolContext ctx) {
        log.debug("CgProtocol.onLink called");
    }

    // =========================================================================
    // Connection Lifecycle
    // =========================================================================

    /**
     * Called when a peer connects.
     *
     * <p>Stores peer state as a channel attribute and sends our Librarian manifest as handshake.
     */
    public void onPeerConnected(PeerConnection connection, boolean inbound) {
        RemotePeer peer = new RemotePeer(connection.remoteAddress(), inbound);
        connection.setRemotePeer(peer);
        activePeers.add(connection);

        log.info("Peer connected: {} ({})", connection.remoteAddress(), inbound ? "inbound" : "outbound");

        // Send handshake - our Librarian manifest
        sendHandshake(connection);
    }

    /**
     * Called when a peer disconnects.
     */
    public void onPeerDisconnected(PeerConnection connection) {
        activePeers.remove(connection);
        subscriptions.remove(connection);

        connection.remotePeer().ifPresent(peer ->
                log.info("Peer disconnected: {}", peer));
    }

    // =========================================================================
    // Message Handling
    // =========================================================================

    /**
     * Handle an incoming typed protocol message from a peer.
     */
    public void handleMessage(PeerConnection connection, ProtocolMessage message) {
        handleMessage(connection, message, null);
    }

    /**
     * Handle an incoming message, optionally with relay context.
     *
     * @param connection The connection the message arrived on
     * @param message    The decoded protocol message
     * @param replyTo    If non-null, responses should be wrapped in an Envelope to this IID
     */
    private void handleMessage(PeerConnection connection, ProtocolMessage message, ItemID replyTo) {
        log.debug("Received {} from {}{}", message.getClass().getSimpleName(),
                connection.remoteAddress(), replyTo != null ? " (relayed, reply to " + replyTo + ")" : "");

        switch (message) {
            case Request request -> handleRequest(connection, request, replyTo);
            case Delivery delivery -> handleDelivery(connection, delivery);
            case Heartbeat ignored -> {} // handled at transport layer
        }
    }

    /**
     * Handle a REQUEST message - look up what they want and send a DELIVERY.
     *
     * @param replyTo If non-null, wrap response in an Envelope back to this IID
     */
    private void handleRequest(PeerConnection connection, Request request, ItemID replyTo) {
        log.debug("Handling request {} with {} targets", request.requestId(), request.targets().size());

        List<Delivery.Payload> payloads = new ArrayList<>();

        for (Request.Target target : request.targets()) {
            switch (target) {
                case Request.Target.Item itemTarget -> {
                    ItemID iid = itemTarget.iid();

                    Optional<Manifest> manifest = context.getManifest(iid);
                    if (manifest.isPresent()) {
                        payloads.add(new Delivery.Payload.Item(manifest.get()));
                        log.debug("Fulfilling request for item {}", iid);
                    } else {
                        payloads.add(new Delivery.Payload.NotFound(iid));
                        log.debug("Item not found: {}", iid);
                    }
                }

                case Request.Target.Content contentTarget -> {
                    ContentID cid = contentTarget.cid();

                    Optional<byte[]> data = context.getContent(cid);
                    if (data.isPresent()) {
                        payloads.add(new Delivery.Payload.Content(cid, data.get()));
                        log.debug("Fulfilling request for content {} ({} bytes)", cid, data.get().length);
                    } else {
                        log.debug("Content not found: {}", cid);
                    }
                }

                case Request.Target.Relations relTarget -> {
                    List<Relation> relations = context.queryRelations(
                            relTarget.subject(),
                            relTarget.predicate(),
                            relTarget.object()
                    );

                    if (!relations.isEmpty()) {
                        payloads.add(new Delivery.Payload.Relations(relations));
                        log.debug("Fulfilling request for {} relations", relations.size());
                    }

                    // Handle subscription (with dedup and limit)
                    if (relTarget.subscribe()) {
                        Set<Request.Target.Relations> peerSubs =
                                subscriptions.computeIfAbsent(connection, k -> ConcurrentHashMap.newKeySet());
                        if (peerSubs.size() < MAX_SUBSCRIPTIONS_PER_PEER) {
                            peerSubs.add(relTarget);
                            log.debug("Added subscription for relations (s={}, p={}, o={})",
                                    relTarget.subject(), relTarget.predicate(), relTarget.object());
                        } else {
                            log.warn("Subscription limit ({}) reached for {}",
                                    MAX_SUBSCRIPTIONS_PER_PEER, connection.remoteAddress());
                        }
                    }
                }
            }
        }

        // Send response
        if (!payloads.isEmpty()) {
            Delivery response = new Delivery(request.requestId(), payloads);
            if (replyTo != null) {
                // Wrap response in an Envelope back to the origin
                Delivery envelope = Delivery.envelope(replyTo, context.librarian().iid(), response);
                connection.send(envelope);
            } else {
                connection.send(response);
            }
        }
    }

    /**
     * Handle a DELIVERY message - store what they sent.
     */
    private void handleDelivery(PeerConnection connection, Delivery delivery) {
        Optional<RemotePeer> maybePeer = connection.remotePeer();
        if (maybePeer.isEmpty()) {
            log.warn("Received delivery from unknown connection {}", connection.remoteAddress());
            return;
        }

        RemotePeer peer = maybePeer.get();
        boolean isHandshake = delivery.requestId() == 0 && !peer.isIdentified();
        boolean hasUsefulPayload = false;

        for (Delivery.Payload payload : delivery.payloads()) {
            switch (payload) {
                case Delivery.Payload.Item itemPayload -> {
                    hasUsefulPayload = true;
                    Manifest manifest = itemPayload.manifest();

                    if (isHandshake) {
                        // This is the handshake - record peer identity
                        String displayName = manifest.iid().encodeText();
                        peer.identified(manifest.iid(), displayName);
                        log.info("Peer {} identified as {}", connection.remoteAddress(), peer);

                        // Create graph relations for this peer relationship
                        context.onPeerIdentified(manifest, connection.remoteAddress());
                    }

                    // Store the manifest (TODO: verify signature first)
                    context.storeManifest(manifest);
                    log.debug("Stored manifest for {}", manifest.iid());
                }

                case Delivery.Payload.Content contentPayload -> {
                    hasUsefulPayload = true;
                    ContentID cid = contentPayload.cid();
                    byte[] data = contentPayload.data();

                    context.storeContent(data);
                    log.debug("Stored content {} ({} bytes)", cid, data.length);

                    completePendingContentRequest(cid, data);
                }

                case Delivery.Payload.Relations relPayload -> {
                    hasUsefulPayload = true;
                    List<Relation> relations = relPayload.relations();

                    context.storeRelations(relations);
                    log.debug("Stored {} relations", relations.size());
                }

                case Delivery.Payload.NotFound notFound -> {
                    ItemID iid = notFound.iid();
                    log.debug("Peer doesn't have item {}", iid);

                    completePendingItemRequest(iid, null);
                }

                case Delivery.Payload.Envelope envelope -> {
                    ItemID nextHop = envelope.nextHop();

                    if (nextHop.equals(context.librarian().iid())) {
                        // We are the final destination — unwrap and handle
                        try {
                            ProtocolMessage inner = ProtocolMessage.decode(envelope.inner());
                            handleMessage(connection, inner, envelope.origin());
                        } catch (Exception e) {
                            log.warn("Failed to decode relayed message: {}", e.getMessage());
                        }
                    } else {
                        // Forward to next hop
                        connectionFor(nextHop).ifPresentOrElse(
                                nextConn -> {
                                    Delivery forwarded = new Delivery(0,
                                            List.of(new Delivery.Payload.Envelope(
                                                    nextHop, envelope.origin(), envelope.inner())));
                                    nextConn.send(forwarded);
                                    context.onRelayForwarded(
                                            connection.remotePeer()
                                                    .map(RemotePeer::librarianId).orElse(null),
                                            nextHop);
                                    log.debug("Forwarded envelope from {} to {}",
                                            envelope.origin(), nextHop);
                                },
                                () -> log.warn("Cannot forward envelope to {}: no connection",
                                        nextHop)
                        );
                    }
                }
            }
        }

        // Complete pending request if this was a response
        if (delivery.requestId() != 0) {
            PendingRequest pending = pendingRequests.remove(delivery.requestId());
            if (pending != null) {
                pending.complete(delivery);
            }

            // Acknowledge the delivery if it contained useful content
            if (hasUsefulPayload && peer.isIdentified()) {
                context.onDeliveryReceived(peer.librarianId(), delivery.requestId());
            }
        }
    }

    // =========================================================================
    // Outgoing Operations
    // =========================================================================

    /**
     * Send a handshake to a peer (our Librarian manifest).
     */
    private void sendHandshake(PeerConnection connection) {
        Manifest manifest = context.localManifest();
        if (manifest == null) {
            log.warn("Cannot send handshake: no local manifest");
            return;
        }

        Delivery handshake = Delivery.item(manifest);
        connection.send(handshake);
        log.debug("Sent handshake to {}", connection.remoteAddress());
    }

    /**
     * Request an item from a peer.
     */
    public void requestItem(PeerConnection connection, ItemID iid, Consumer<Manifest> callback) {
        long requestId = requestIdCounter.incrementAndGet();

        pendingRequests.put(requestId, new PendingRequest(requestId, callback));
        scheduleTimeout(connection, requestId);

        Request request = Request.item(requestId, iid);
        connection.send(request);
        log.debug("Sent request for item {} to {}", iid, connection.remoteAddress());
    }

    /**
     * Request an item via a relay peer.
     *
     * <p>Wraps the request in an Envelope addressed to the destination,
     * and sends it through the relay. The destination's response will be
     * relayed back through the same path.
     *
     * @param relay       The directly-connected peer to relay through
     * @param destination The librarian that has the item (not directly connected)
     * @param iid         The item to request
     * @param callback    Called with the manifest (or null on timeout/not-found)
     */
    public void requestItemVia(PeerConnection relay, ItemID destination,
                               ItemID iid, Consumer<Manifest> callback) {
        long requestId = requestIdCounter.incrementAndGet();

        pendingRequests.put(requestId, new PendingRequest(requestId, callback));
        scheduleTimeout(relay, requestId);

        Request inner = Request.item(requestId, iid);
        Delivery envelope = Delivery.envelope(destination, context.librarian().iid(), inner);
        relay.send(envelope);
        log.debug("Sent relayed request for item {} via {} to {}",
                iid, relay.remoteAddress(), destination);
    }

    /**
     * Request content from a peer.
     */
    public void requestContent(PeerConnection connection, ContentID cid, Consumer<byte[]> callback) {
        long requestId = requestIdCounter.incrementAndGet();

        pendingRequests.put(requestId, new PendingRequest(requestId, callback));
        scheduleTimeout(connection, requestId);

        Request request = Request.content(requestId, cid);
        connection.send(request);
        log.debug("Sent request for content {} to {}", cid, connection.remoteAddress());
    }

    /**
     * Schedule a timeout for a pending request.
     */
    private void scheduleTimeout(PeerConnection connection, long requestId) {
        connection.channel().eventLoop().schedule(() -> {
            PendingRequest expired = pendingRequests.remove(requestId);
            if (expired != null) {
                log.debug("Request {} timed out after {}s", requestId, REQUEST_TIMEOUT_SECONDS);
                expired.timeout();
            }
        }, REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS);
    }

    // =========================================================================
    // Pending Request Management
    // =========================================================================

    private void completePendingItemRequest(ItemID iid, Manifest manifest) {
        pendingRequests.values().removeIf(pending -> {
            if (pending.callback instanceof Consumer<?>) {
                @SuppressWarnings("unchecked")
                Consumer<Manifest> callback = (Consumer<Manifest>) pending.callback;
                callback.accept(manifest);
                return true;
            }
            return false;
        });
    }

    private void completePendingContentRequest(ContentID cid, byte[] data) {
        // In practice we'd use the requestId for proper correlation
    }

    // =========================================================================
    // Subscription Push
    // =========================================================================

    /**
     * Called when a relation is added locally - push to subscribers.
     */
    public void onRelationAdded(Relation relation) {
        for (var entry : subscriptions.entrySet()) {
            PeerConnection connection = entry.getKey();
            Set<Request.Target.Relations> filters = entry.getValue();

            for (Request.Target.Relations filter : filters) {
                if (matchesFilter(relation, filter)) {
                    Delivery push = Delivery.relations(0, List.of(relation));
                    connection.send(push);
                    log.debug("Pushed relation to subscriber {}", connection.remoteAddress());
                    break;  // Only send once per connection
                }
            }
        }
    }

    private boolean matchesFilter(Relation relation, Request.Target.Relations filter) {
        if (filter.subject() != null && !filter.subject().equals(relation.subject())) {
            return false;
        }
        if (filter.predicate() != null && !filter.predicate().equals(relation.predicate())) {
            return false;
        }
        if (filter.object() != null && relation.object() != null) {
            Relation.Target target = relation.object();
            if (target instanceof Relation.IidTarget iidTarget) {
                if (!filter.object().equals(iidTarget.iid())) {
                    return false;
                }
            } else {
                return false;
            }
        }
        return true;
    }

    // =========================================================================
    // Accessors
    // =========================================================================

    /**
     * Get the remote peer for a connection.
     */
    public Optional<RemotePeer> peer(PeerConnection connection) {
        return connection.remotePeer();
    }

    /**
     * Find the connection to a specific librarian by IID.
     */
    public Optional<PeerConnection> connectionFor(ItemID librarianIid) {
        for (PeerConnection conn : activePeers) {
            Optional<RemotePeer> peer = conn.remotePeer();
            if (peer.isPresent() && peer.get().isIdentified()
                    && librarianIid.equals(peer.get().librarianId())) {
                return Optional.of(conn);
            }
        }
        return Optional.empty();
    }

    /**
     * Get all connected peers.
     */
    public Iterable<RemotePeer> peers() {
        List<RemotePeer> result = new ArrayList<>();
        for (PeerConnection conn : activePeers) {
            conn.remotePeer().ifPresent(result::add);
        }
        return result;
    }

    // =========================================================================
    // Helper Types
    // =========================================================================

    /**
     * Tracks a pending request waiting for a response.
     */
    private record PendingRequest(
            long requestId,
            Object callback  // Consumer<T> for the expected response type
    ) {
        void complete(Delivery delivery) {
            if (callback instanceof Consumer<?>) {
                // Extract the first relevant payload for the callback
                for (Delivery.Payload payload : delivery.payloads()) {
                    switch (payload) {
                        case Delivery.Payload.Item itemPayload -> {
                            @SuppressWarnings("unchecked")
                            Consumer<Manifest> c = (Consumer<Manifest>) callback;
                            c.accept(itemPayload.manifest());
                            return;
                        }
                        case Delivery.Payload.Content contentPayload -> {
                            @SuppressWarnings("unchecked")
                            Consumer<byte[]> c = (Consumer<byte[]>) callback;
                            c.accept(contentPayload.data());
                            return;
                        }
                        case Delivery.Payload.NotFound notFound -> {
                            @SuppressWarnings("unchecked")
                            Consumer<Object> c = (Consumer<Object>) callback;
                            c.accept(null);
                            return;
                        }
                        default -> {}
                    }
                }
            }
        }

        void timeout() {
            // Invoke callback with null to signal timeout
            if (callback instanceof Consumer<?> consumer) {
                @SuppressWarnings("unchecked")
                Consumer<Object> c = (Consumer<Object>) consumer;
                c.accept(null);
            }
        }
    }
}
