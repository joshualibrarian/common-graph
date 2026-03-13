package dev.everydaythings.graph.network;

import com.upokecenter.cbor.CBORObject;
import dev.everydaythings.graph.Canonical.CgTag;

/**
 * ACK — simple acknowledgment.
 *
 * <p>Shared by both peer and session protocols.
 * Replaces {@code SessionMessage.OkResponse}.
 */
public record Ack(long requestId) implements ProtocolMessage {

    @Override
    public int tag() {
        return CgTag.ACK;
    }

    @Override
    public CBORObject toCbor() {
        CBORObject obj = CBORObject.NewMap();
        obj.set("requestId", CBORObject.FromInt64(requestId));
        return obj;
    }

    public static Ack fromCbor(CBORObject obj) {
        return new Ack(obj.get("requestId").AsInt64Value());
    }
}
