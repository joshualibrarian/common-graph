package dev.everydaythings.graph.network;

import com.upokecenter.cbor.CBORObject;
import dev.everydaythings.graph.Canonical.CgTag;

/**
 * HEARTBEAT — "I'm still here".
 *
 * <p>Minimal keep-alive message sent when a connection is idle.
 * Shared by both peer and session protocols.
 *
 * @see dev.everydaythings.graph.network.transport.HeartbeatHandler
 */
public record Heartbeat() implements ProtocolMessage {

    public static final Heartbeat INSTANCE = new Heartbeat();

    @Override
    public int tag() {
        return CgTag.HEARTBEAT;
    }

    @Override
    public CBORObject toCbor() {
        return CBORObject.NewMap();
    }
}
