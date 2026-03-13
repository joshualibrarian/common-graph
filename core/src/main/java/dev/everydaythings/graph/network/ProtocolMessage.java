package dev.everydaythings.graph.network;

import com.upokecenter.cbor.CBORObject;
import dev.everydaythings.graph.Canonical.CgTag;
import dev.everydaythings.graph.network.peer.Delivery;
import dev.everydaythings.graph.network.peer.Request;
import dev.everydaythings.graph.network.session.SessionMessage;

/**
 * Base interface for all CG protocol messages (peer and session).
 *
 * <p>Wire format: {@code [4-byte length][CBOR Tag(N, map)]}.
 * The CBOR tag discriminates the message type (1 byte, native CBOR).
 *
 * <p>Tags 11-22 are allocated for protocol messages (see {@link CgTag}).
 */
public interface ProtocolMessage {

    /** The CG-CBOR tag for this message type. */
    int tag();

    /** Encode the message body as a CBOR map (without the tag wrapper). */
    CBORObject toCbor();

    /** Encode the complete tagged message to bytes. */
    default byte[] encode() {
        return CBORObject.FromCBORObjectAndTag(toCbor(), tag()).EncodeToBytes();
    }

    /**
     * Decode a tagged CBOR message from bytes.
     *
     * @param data CBOR bytes with outermost tag indicating message type
     * @return The decoded protocol message
     */
    static ProtocolMessage decode(byte[] data) {
        CBORObject tagged = CBORObject.DecodeFromBytes(data);
        int tag = tagged.getMostOuterTag().ToInt32Checked();
        CBORObject map = tagged.UntagOne();
        return switch (tag) {
            case CgTag.REQUEST   -> Request.fromCbor(map);
            case CgTag.DELIVERY  -> Delivery.fromCbor(map);
            case CgTag.AUTH      -> SessionMessage.decodeAuth(map);
            case CgTag.CONTEXT   -> SessionMessage.decodeContext(map);
            case CgTag.DISPATCH  -> SessionMessage.decodeDispatch(map);
            case CgTag.LOOKUP    -> SessionMessage.decodeLookup(map);
            case CgTag.SUBSCRIBE -> SessionMessage.decodeSubscribe(map);
            case CgTag.EVENT     -> SessionMessage.decodeEvent(map);
            case CgTag.STREAM    -> SessionMessage.decodeStream(map);
            case CgTag.HEARTBEAT -> Heartbeat.INSTANCE;
            case CgTag.ACK       -> Ack.fromCbor(map);
            case CgTag.ERROR     -> ProtocolError.fromCbor(map);
            default -> throw new IllegalArgumentException("Unknown protocol tag: " + tag);
        };
    }
}
