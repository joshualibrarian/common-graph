package dev.everydaythings.graph.network;

import com.upokecenter.cbor.CBORObject;
import dev.everydaythings.graph.Canonical.CgTag;

/**
 * ERROR — error response.
 *
 * <p>Shared by both peer and session protocols.
 * Replaces {@code SessionMessage.ErrorResponse}.
 */
public record ProtocolError(long requestId, String code, String message) implements ProtocolMessage {

    @Override
    public int tag() {
        return CgTag.ERROR;
    }

    @Override
    public CBORObject toCbor() {
        CBORObject obj = CBORObject.NewMap();
        obj.set("requestId", CBORObject.FromInt64(requestId));
        obj.set("code", CBORObject.FromString(code));
        obj.set("message", CBORObject.FromString(message));
        return obj;
    }

    public static ProtocolError fromCbor(CBORObject obj) {
        return new ProtocolError(
                obj.get("requestId").AsInt64Value(),
                obj.get("code").AsString(),
                obj.get("message").AsString()
        );
    }
}
