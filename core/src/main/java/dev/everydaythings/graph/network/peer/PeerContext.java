package dev.everydaythings.graph.network.peer;

import dev.everydaythings.graph.encoding.Canonical;
import dev.everydaythings.graph.item.ItemOld;
import dev.everydaythings.graph.item.Literal;
import dev.everydaythings.graph.item.ManifestOld;
import dev.everydaythings.graph.datum.BindingTarget;
import dev.everydaythings.graph.frame.FrameBodyOld;
import dev.everydaythings.graph.frame.FrameRecordOld;
import dev.everydaythings.graph.item.id.ContentID;
import dev.everydaythings.graph.item.id.ItemID;
import dev.everydaythings.graph.language.ThematicRole;
import dev.everydaythings.graph.network.RoutingVocabulary;
import dev.everydaythings.graph.runtime.LibrarianOld;
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
public class PeerContext {

    private static final Logger log = LogManager.getLogger(PeerContext.class);

    @Getter
    private final LibrarianOld librarian;

    public PeerContext(LibrarianOld librarian) {
        this.librarian = librarian;
    }

    /**
     * Get the local Librarian's manifest (for handshake).
     */
    public ManifestOld localManifest() {
        return librarian.current();
    }

    /**
     * Look up an item by IID.
     */
    public Optional<ItemOld> getItem(ItemID iid) {
        return librarian.get(iid, ItemOld.class);
    }

    /**
     * Look up an item's manifest.
     */
    public Optional<ManifestOld> getManifest(ItemID iid) {
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
    public List<FrameBodyOld> queryFrameBodies(ItemID item, ItemID predicate) {
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
    public void storeManifest(ManifestOld manifest) {
        // Librarian's storeManifest takes bytes
        byte[] encoded = manifest.encodeBinary(Canonical.Scope.RECORD);
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
    public void storeFrameBodies(List<FrameBodyOld> frames) {
        for (FrameBodyOld body : frames) {
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
    public void onPeerIdentified(ManifestOld remoteManifest, InetSocketAddress remoteAddress) {
        ItemID localId = librarian.iid();
        ItemID remoteId = remoteManifest.iid();

        // Create peers-with relation: local --peers-with--> remote
        FrameBodyOld peersWithBody = FrameBodyOld.of(
                RoutingVocabulary.PeersWith.IID,
                localId,
                Map.of(ThematicRole.Goal.IID, BindingTarget.iid(remoteId)));
        FrameRecordOld peersWithRecord = FrameRecordOld.create(peersWithBody, librarian);
        librarian.library().storeFrame(peersWithBody, peersWithRecord);
        log.info("Created peers-with frame: {} -> {}", localId.encodeText(), remoteId.encodeText());

        // Create reachable-at relation: remote --reachable-at--> Endpoint
        Endpoint endpoint = Endpoint.cg(
                IpAddress.fromInetAddress(remoteAddress.getAddress()),
                remoteAddress.getPort()
        );
        FrameBodyOld reachableAtBody = FrameBodyOld.of(
                RoutingVocabulary.ReachableAt.IID,
                remoteId,
                Map.of(ThematicRole.Goal.IID, Literal.ofBytes(
                        endpoint.encodeBinary(Canonical.Scope.RECORD))));
        FrameRecordOld reachableAtRecord = FrameRecordOld.create(reachableAtBody, librarian);
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

        FrameBodyOld relayBody = FrameBodyOld.of(
                RoutingVocabulary.AcknowledgesRelay.IID,
                librarian.iid(),
                Map.of(ThematicRole.Goal.IID, BindingTarget.iid(fromPeer)));
        FrameRecordOld relayRecord = FrameRecordOld.create(relayBody, librarian);
        librarian.library().storeFrame(relayBody, relayRecord);
        log.info("Relay forwarded: {} -> {}", fromPeer.encodeText(), toPeer.encodeText());
    }

    public void onDeliveryReceived(ItemID remoteLibrarianIid, long requestId) {
        ItemID localId = librarian.iid();

        FrameBodyOld ackBody = FrameBodyOld.of(
                RoutingVocabulary.AcknowledgesDelivery.IID,
                localId,
                Map.of(
                        ThematicRole.Goal.IID, BindingTarget.iid(remoteLibrarianIid),
                        RoutingVocabulary.RequestId.IID, Literal.ofInteger(requestId)));
        FrameRecordOld ackRecord = FrameRecordOld.create(ackBody, librarian);
        librarian.library().storeFrame(ackBody, ackRecord);
        log.info("Acknowledged delivery from {} (request {})",
                remoteLibrarianIid.encodeText(), requestId);
    }
}
