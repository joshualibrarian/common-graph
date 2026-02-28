package dev.everydaythings.graph.network.peer;

import dev.everydaythings.graph.item.id.ItemID;
import lombok.Getter;
import lombok.experimental.Accessors;

import java.net.InetSocketAddress;
import java.time.Instant;

/**
 * Represents a remote peer (Librarian) in the network.
 *
 * <p>A RemotePeer is identified by the ItemID of their Librarian item,
 * which is exchanged during the handshake. Before handshake completes,
 * the peer is identified only by their network address.
 */
@Accessors(fluent = true)
public class RemotePeer {

    @Getter
    private final InetSocketAddress address;

    @Getter
    private final Instant connectedAt;

    @Getter
    private final boolean inbound;  // true = they connected to us

    // Set after handshake
    @Getter
    private ItemID librarianId;

    @Getter
    private String displayName;

    public RemotePeer(InetSocketAddress address, boolean inbound) {
        this.address = address;
        this.connectedAt = Instant.now();
        this.inbound = inbound;
    }

    /**
     * Complete the handshake by recording the peer's identity.
     */
    public void identified(ItemID librarianId, String displayName) {
        this.librarianId = librarianId;
        this.displayName = displayName;
    }

    /**
     * Check if the peer has completed the handshake.
     */
    public boolean isIdentified() {
        return librarianId != null;
    }

    @Override
    public String toString() {
        if (librarianId != null) {
            return displayName != null ? displayName : librarianId.encodeText().substring(0, 12) + "...";
        }
        return address.toString();
    }
}
