package dev.everydaythings.graph.network;

import dev.everydaythings.graph.item.Item;
import dev.everydaythings.graph.item.Literal;
import dev.everydaythings.graph.item.Manifest;
import dev.everydaythings.graph.item.component.BindingTarget;
import dev.everydaythings.graph.item.component.FrameBody;
import dev.everydaythings.graph.item.component.FrameRecord;
import dev.everydaythings.graph.item.id.ContentID;
import dev.everydaythings.graph.item.id.ItemID;
import dev.everydaythings.graph.language.ThematicRole;
import dev.everydaythings.graph.runtime.Librarian;
import dev.everydaythings.graph.value.Endpoint;
import dev.everydaythings.graph.value.IpAddress;
import lombok.Getter;
import lombok.experimental.Accessors;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.net.InetSocketAddress;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Context provided to protocol handlers for accessing local resources.
 *
 * <p>Protocol handlers use this to:
 * <ul>
 *   <li>Look up items, content, and relations to fulfill requests</li>
 *   <li>Store received items and content</li>
 *   <li>Access the local Librarian's identity for handshake</li>
 * </ul>
 */
@Accessors(fluent = true)
public class ProtocolContext {

    private static final Logger log = LogManager.getLogger(ProtocolContext.class);

    @Getter
    private final Librarian librarian;

    public ProtocolContext(Librarian librarian) {
        this.librarian = librarian;
    }

    /**
     * Get the local Librarian's manifest (for handshake).
     */
    public Manifest localManifest() {
        return librarian.current();
    }

    /**
     * Look up an item by IID.
     */
    public Optional<Item> getItem(ItemID iid) {
        return librarian.get(iid, Item.class);
    }

    /**
     * Look up an item's manifest.
     */
    public Optional<Manifest> getManifest(ItemID iid) {
        return librarian.manifest(iid);
    }

    /**
     * Look up content by CID.
     */
    public Optional<byte[]> getContent(ContentID cid) {
        return librarian.content(cid);
    }

    /**
     * Query frame bodies (item, predicate - either can be null for wildcard).
     * Frame-based: queries by participating item and/or predicate.
     */
    public List<FrameBody> queryFrameBodies(ItemID item, ItemID predicate) {
        // Library.byPredicate returns Stream<FrameBody> directly
        if (predicate != null) {
            return librarian.library().byPredicate(predicate).toList();
        }
        // TODO: Full item-based query support (search bindings for matching IidTarget)
        return List.of();
    }

    /**
     * Store a received manifest.
     */
    public void storeManifest(Manifest manifest) {
        // Librarian's storeManifest takes bytes
        byte[] encoded = manifest.encodeBinary(dev.everydaythings.graph.Canonical.Scope.RECORD);
        librarian.storeManifest(encoded);
    }

    /**
     * Store received content.
     */
    public ContentID storeContent(byte[] data) {
        return librarian.storeContent(data);
    }

    /**
     * Store received frame bodies.
     */
    public void storeFrameBodies(List<FrameBody> frames) {
        for (FrameBody body : frames) {
            librarian.storeFrame(body);
        }
    }

    /**
     * Called when a peer is identified during handshake.
     *
     * <p>Creates two signed relations:
     * <ul>
     *   <li>{@code (localLibrarian) --peers-with--> (remoteLibrarian)} — peer adjacency</li>
     *   <li>{@code (remoteLibrarian) --reachable-at--> Endpoint} — how to reach the remote peer</li>
     * </ul>
     *
     * @param remoteManifest The remote librarian's manifest
     * @param remoteAddress  The network address of the remote peer
     */
    public void onPeerIdentified(Manifest remoteManifest, InetSocketAddress remoteAddress) {
        ItemID localId = librarian.iid();
        ItemID remoteId = remoteManifest.iid();

        // Create peers-with relation: local --peers-with--> remote
        FrameBody peersWithBody = FrameBody.of(
                RoutingVocabulary.PeersWith.SEED.iid(),
                localId,
                Map.of(ThematicRole.Target.SEED.iid(), BindingTarget.iid(remoteId)));
        FrameRecord peersWithRecord = FrameRecord.create(peersWithBody, librarian);
        librarian.library().storeFrame(peersWithBody, peersWithRecord);
        log.info("Created peers-with frame: {} -> {}", localId.encodeText(), remoteId.encodeText());

        // Create reachable-at relation: remote --reachable-at--> Endpoint
        Endpoint endpoint = Endpoint.cg(
                IpAddress.fromInetAddress(remoteAddress.getAddress()),
                remoteAddress.getPort()
        );
        FrameBody reachableAtBody = FrameBody.of(
                RoutingVocabulary.ReachableAt.SEED.iid(),
                remoteId,
                Map.of(ThematicRole.Target.SEED.iid(), Literal.of(endpoint)));
        FrameRecord reachableAtRecord = FrameRecord.create(reachableAtBody, librarian);
        librarian.library().storeFrame(reachableAtBody, reachableAtRecord);
        log.info("Created reachable-at frame: {} -> {}", remoteId.encodeText(), endpoint);
    }

    /**
     * Called when a solicited delivery with useful content is received from a peer.
     *
     * <p>Creates a signed acknowledgement relation:
     * {@code (localLibrarian) --acknowledges-delivery--> (remoteLibrarian)}
     * with a request-id qualifier.
     *
     * <p>These gossipable attestations accumulate to build a peer's credibility.
     *
     * @param remoteLibrarianIid The IID of the peer who fulfilled the request
     * @param requestId          The request ID that was fulfilled
     */
    /**
     * Called when this librarian successfully forwards an envelope.
     *
     * <p>Creates a signed relay acknowledgement relation:
     * {@code (localLibrarian) --acknowledges-relay--> (fromPeer)}
     *
     * @param fromPeer The peer who asked us to relay
     * @param toPeer   The peer we forwarded to
     */
    public void onRelayForwarded(ItemID fromPeer, ItemID toPeer) {
        if (fromPeer == null || toPeer == null) return;

        FrameBody relayBody = FrameBody.of(
                RoutingVocabulary.AcknowledgesRelay.SEED.iid(),
                librarian.iid(),
                Map.of(ThematicRole.Target.SEED.iid(), BindingTarget.iid(fromPeer)));
        FrameRecord relayRecord = FrameRecord.create(relayBody, librarian);
        librarian.library().storeFrame(relayBody, relayRecord);
        log.info("Relay forwarded: {} -> {}", fromPeer.encodeText(), toPeer.encodeText());
    }

    public void onDeliveryReceived(ItemID remoteLibrarianIid, long requestId) {
        ItemID localId = librarian.iid();

        FrameBody ackBody = FrameBody.of(
                RoutingVocabulary.AcknowledgesDelivery.SEED.iid(),
                localId,
                Map.of(
                        ThematicRole.Target.SEED.iid(), BindingTarget.iid(remoteLibrarianIid),
                        RoutingVocabulary.RequestId.SEED.iid(), Literal.ofInteger(requestId)));
        FrameRecord ackRecord = FrameRecord.create(ackBody, librarian);
        librarian.library().storeFrame(ackBody, ackRecord);
        log.info("Acknowledged delivery from {} (request {})",
                remoteLibrarianIid.encodeText(), requestId);
    }
}
