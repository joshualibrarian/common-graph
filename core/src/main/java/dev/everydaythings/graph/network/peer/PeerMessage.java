package dev.everydaythings.graph.network.peer;

import dev.everydaythings.graph.network.ProtocolMessage;

/**
 * CG peer protocol messages.
 *
 * <p>Two message types:
 * <ul>
 *   <li>{@link Request} - "I want something" [Tag 11]</li>
 *   <li>{@link Delivery} - "Here's something" [Tag 12]</li>
 * </ul>
 *
 * <p>Wire format: {@code [4-byte length][CBOR Tag(N, map)]}
 */
public sealed interface PeerMessage extends ProtocolMessage permits Request, Delivery {
}
