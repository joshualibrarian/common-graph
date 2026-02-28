package dev.everydaythings.graph.network;

import dev.everydaythings.graph.network.peer.PeerLink;

public interface Protocol {
    String protocolId();     // "cg/1", "smtp/1" (gateway), etc.
    void onLink(PeerLink link, ProtocolContext ctx);
}
