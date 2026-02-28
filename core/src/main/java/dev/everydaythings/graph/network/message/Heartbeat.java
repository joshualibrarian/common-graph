package dev.everydaythings.graph.network.message;

import com.upokecenter.cbor.CBORObject;

/**
 * HEARTBEAT - "I'm still here"
 *
 * <p>Minimal keep-alive message sent when a connection is idle.
 * Used by {@link dev.everydaythings.graph.network.transport.HeartbeatHandler}
 * to detect dead peers.
 */
public record Heartbeat() implements ProtocolMessage {

    static final String TYPE = "heartbeat";

    @Override
    public byte[] encode() {
        CBORObject envelope = CBORObject.NewMap();
        envelope.set("type", CBORObject.FromObject(TYPE));
        return envelope.EncodeToBytes();
    }
}
