package dev.everydaythings.graph.network;

import java.util.Optional;

public interface ProtocolRegistry {
    Optional<Protocol> get(String protocolId);
}
