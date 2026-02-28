package dev.everydaythings.graph.network.message;

import com.upokecenter.cbor.CBORObject;

/**
 * CG Protocol has exactly two message types:
 *
 * <ul>
 *   <li>{@link Request} - "I want something"</li>
 *   <li>{@link Delivery} - "Here's something"</li>
 * </ul>
 *
 * <p>Wire format:
 * <pre>
 * {
 *   "type": "request" | "delivery",
 *   "payload": { ... }
 * }
 * </pre>
 */
public sealed interface ProtocolMessage permits Request, Delivery, Heartbeat {

    /**
     * Encode this message to CBOR bytes for transmission.
     */
    byte[] encode();

    /**
     * Decode a message from CBOR bytes.
     */
    static ProtocolMessage decode(byte[] data) {
        CBORObject envelope = CBORObject.DecodeFromBytes(data);
        String type = envelope.get("type").AsString();
        CBORObject payload = envelope.get("payload");

        return switch (type) {
            case Request.TYPE -> Request.fromCbor(payload);
            case Delivery.TYPE -> Delivery.fromCbor(payload);
            case Heartbeat.TYPE -> new Heartbeat();
            default -> throw new IllegalArgumentException("Unknown message type: " + type);
        };
    }
}
