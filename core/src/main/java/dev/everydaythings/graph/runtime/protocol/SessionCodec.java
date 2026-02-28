package dev.everydaythings.graph.runtime.protocol;

import com.upokecenter.cbor.CBORObject;
import dev.everydaythings.graph.item.id.ItemID;
import dev.everydaythings.graph.language.Posting;
import dev.everydaythings.graph.ui.scene.View;
import lombok.extern.log4j.Log4j2;
import java.io.*;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

/**
 * Encodes and decodes {@link SessionMessage}s for wire transmission.
 *
 * <p>Wire format:
 * <pre>
 * ┌─────────┬───────────┬───────────────────────────────────┐
 * │ Length  │ Type      │ CBOR Payload                      │
 * │ (4 byte)│ (1 byte)  │ (variable)                        │
 * └─────────┴───────────┴───────────────────────────────────┘
 * </pre>
 */
@Log4j2
public class SessionCodec {

    /**
     * Encode a message to bytes.
     */
    public byte[] encode(SessionMessage message) {
        CBORObject cbor = toCbor(message);
        byte[] payload = cbor.EncodeToBytes();

        // Frame: 4-byte length + 1-byte type + payload
        ByteBuffer buffer = ByteBuffer.allocate(4 + 1 + payload.length);
        buffer.putInt(1 + payload.length);  // Length includes type byte
        buffer.put((byte) message.type().code());
        buffer.put(payload);
        return buffer.array();
    }

    /**
     * Write a message to an output stream.
     */
    public void write(SessionMessage message, OutputStream out) throws IOException {
        byte[] encoded = encode(message);
        out.write(encoded);
        out.flush();
    }

    /**
     * Read a message from an input stream.
     */
    public SessionMessage read(InputStream in) throws IOException {
        DataInputStream din = new DataInputStream(in);

        // Read length
        int length = din.readInt();
        if (length < 1 || length > 10_000_000) {
            throw new IOException("Invalid message length: " + length);
        }

        // Read type
        int typeCode = din.readUnsignedByte();
        SessionMessage.Type type = SessionMessage.Type.fromCode(typeCode);

        // Read payload
        byte[] payload = new byte[length - 1];
        din.readFully(payload);

        CBORObject cbor = CBORObject.DecodeFromBytes(payload);
        return fromCbor(type, cbor);
    }

    // =========================================================================
    // CBOR Serialization
    // =========================================================================

    private CBORObject toCbor(SessionMessage message) {
        CBORObject obj = CBORObject.NewMap();

        switch (message) {
            case SessionMessage.AuthChallenge m -> {
                obj.set("nonce", CBORObject.FromByteArray(m.nonce()));
                obj.set("methods", toCborStringList(m.acceptedMethods()));
            }
            case SessionMessage.AuthToken m -> {
                obj.set("token", CBORObject.FromString(m.token()));
                if (m.principalId() != null) obj.set("principalId", CBORObject.FromString(m.principalId().encodeText()));
            }
            case SessionMessage.AuthPrincipal m -> {
                obj.set("principalId", CBORObject.FromString(m.principalId().encodeText()));
                obj.set("signature", CBORObject.FromByteArray(m.signature()));
            }
            case SessionMessage.AuthEngage m -> {
                obj.set("inviteCode", CBORObject.FromString(m.inviteCode()));
                if (m.name() != null) obj.set("name", CBORObject.FromString(m.name()));
            }
            case SessionMessage.AuthResponse m -> {
                obj.set("success", m.success() ? CBORObject.True : CBORObject.False);
                if (m.sessionId() != null) obj.set("sessionId", CBORObject.FromString(m.sessionId()));
                if (m.persistentToken() != null) obj.set("persistentToken", CBORObject.FromString(m.persistentToken()));
                if (m.principalId() != null) obj.set("principalId", CBORObject.FromString(m.principalId().encodeText()));
                if (m.error() != null) obj.set("error", CBORObject.FromString(m.error()));
            }
            case SessionMessage.ContextRequest m -> {
                if (m.itemId() != null) obj.set("itemId", CBORObject.FromString(m.itemId().encodeText()));
                obj.set("resolve", m.resolve() ? CBORObject.True : CBORObject.False);
            }
            case SessionMessage.ContextResponse m -> {
                if (m.itemId() != null) obj.set("itemId", CBORObject.FromString(m.itemId().encodeText()));
                if (m.label() != null) obj.set("label", CBORObject.FromString(m.label()));
                if (m.error() != null) obj.set("error", CBORObject.FromString(m.error()));
            }
            case SessionMessage.DispatchRequest m -> {
                obj.set("action", CBORObject.FromString(m.action()));
                obj.set("args", toCborStringList(m.args()));
                obj.set("requestId", CBORObject.FromInt64(m.requestId()));
                if (m.caller() != null) obj.set("caller", CBORObject.FromString(m.caller().encodeText()));
            }
            case SessionMessage.DispatchResponse m -> {
                obj.set("requestId", CBORObject.FromInt64(m.requestId()));
                obj.set("success", m.success() ? CBORObject.True : CBORObject.False);
                if (m.view() != null) obj.set("view", viewToCbor(m.view()));
                if (m.error() != null) obj.set("error", CBORObject.FromString(m.error()));
            }
            case SessionMessage.LookupRequest m -> {
                obj.set("query", CBORObject.FromString(m.query()));
                obj.set("limit", CBORObject.FromInt32(m.limit()));
                obj.set("requestId", CBORObject.FromInt64(m.requestId()));
            }
            case SessionMessage.LookupResponse m -> {
                obj.set("requestId", CBORObject.FromInt64(m.requestId()));
                obj.set("postings", postingsToCbor(m.postings()));
            }
            case SessionMessage.SubscribeRequest m -> {
                obj.set("itemId", CBORObject.FromString(m.itemId().encodeText()));
                obj.set("subscribe", m.subscribe() ? CBORObject.True : CBORObject.False);
            }
            case SessionMessage.EventMessage m -> {
                obj.set("itemId", CBORObject.FromString(m.itemId().encodeText()));
                obj.set("eventType", CBORObject.FromString(m.eventType()));
                // payload is event-specific; for now just store as string if it's a string
                if (m.payload() != null) {
                    obj.set("payload", CBORObject.FromString(m.payload().toString()));
                }
            }
            case SessionMessage.StreamChunk m -> {
                obj.set("requestId", CBORObject.FromInt64(m.requestId()));
                obj.set("data", CBORObject.FromByteArray(m.data()));
                obj.set("complete", m.complete() ? CBORObject.True : CBORObject.False);
            }
            case SessionMessage.OkResponse m -> {
                obj.set("requestId", CBORObject.FromInt64(m.requestId()));
            }
            case SessionMessage.ErrorResponse m -> {
                obj.set("requestId", CBORObject.FromInt64(m.requestId()));
                obj.set("code", CBORObject.FromString(m.code()));
                obj.set("message", CBORObject.FromString(m.message()));
            }
        }

        return obj;
    }

    private SessionMessage fromCbor(SessionMessage.Type type, CBORObject obj) {
        return switch (type) {
            case AUTH -> {
                // Determine which auth message type based on fields present
                if (obj.ContainsKey("success")) {
                    // AuthResponse
                    yield new SessionMessage.AuthResponse(
                            obj.get("success").AsBoolean(),
                            obj.ContainsKey("sessionId") ? obj.get("sessionId").AsString() : null,
                            obj.ContainsKey("persistentToken") ? obj.get("persistentToken").AsString() : null,
                            obj.ContainsKey("principalId") ? ItemID.fromString(obj.get("principalId").AsString()) : null,
                            obj.ContainsKey("error") ? obj.get("error").AsString() : null
                    );
                } else if (obj.ContainsKey("methods")) {
                    // AuthChallenge
                    yield new SessionMessage.AuthChallenge(
                            obj.get("nonce").GetByteString(),
                            fromCborStringList(obj.get("methods"))
                    );
                } else if (obj.ContainsKey("token")) {
                    // AuthToken
                    yield new SessionMessage.AuthToken(
                            obj.get("token").AsString(),
                            obj.ContainsKey("principalId") ? ItemID.fromString(obj.get("principalId").AsString()) : null
                    );
                } else if (obj.ContainsKey("inviteCode")) {
                    // AuthEngage
                    yield new SessionMessage.AuthEngage(
                            obj.get("inviteCode").AsString(),
                            obj.ContainsKey("name") ? obj.get("name").AsString() : null
                    );
                } else if (obj.ContainsKey("principalId")) {
                    // AuthPrincipal
                    yield new SessionMessage.AuthPrincipal(
                            ItemID.fromString(obj.get("principalId").AsString()),
                            obj.get("signature").GetByteString()
                    );
                } else {
                    throw new IllegalArgumentException("Unknown AUTH message format");
                }
            }
            case CONTEXT -> {
                if (obj.ContainsKey("label") || (obj.ContainsKey("error") && !obj.ContainsKey("resolve"))) {
                    yield new SessionMessage.ContextResponse(
                            obj.ContainsKey("itemId") ? ItemID.fromString(obj.get("itemId").AsString()) : null,
                            obj.ContainsKey("label") ? obj.get("label").AsString() : null,
                            obj.ContainsKey("error") ? obj.get("error").AsString() : null
                    );
                } else {
                    yield new SessionMessage.ContextRequest(
                            obj.ContainsKey("itemId") ? ItemID.fromString(obj.get("itemId").AsString()) : null,
                            obj.ContainsKey("resolve") && obj.get("resolve").AsBoolean()
                    );
                }
            }
            case DISPATCH -> {
                if (obj.ContainsKey("action")) {
                    yield new SessionMessage.DispatchRequest(
                            obj.get("action").AsString(),
                            fromCborStringList(obj.get("args")),
                            obj.get("requestId").AsInt64Value(),
                            obj.ContainsKey("caller") ? ItemID.fromString(obj.get("caller").AsString()) : null
                    );
                } else {
                    yield new SessionMessage.DispatchResponse(
                            obj.get("requestId").AsInt64Value(),
                            obj.get("success").AsBoolean(),
                            obj.ContainsKey("view") ? viewFromCbor(obj.get("view")) : null,
                            obj.ContainsKey("error") ? obj.get("error").AsString() : null
                    );
                }
            }
            case LOOKUP -> {
                if (obj.ContainsKey("query")) {
                    yield new SessionMessage.LookupRequest(
                            obj.get("query").AsString(),
                            obj.get("limit").AsInt32(),
                            obj.get("requestId").AsInt64Value()
                    );
                } else {
                    yield new SessionMessage.LookupResponse(
                            obj.get("requestId").AsInt64Value(),
                            postingsFromCbor(obj.get("postings"))
                    );
                }
            }
            case SUBSCRIBE -> new SessionMessage.SubscribeRequest(
                    ItemID.fromString(obj.get("itemId").AsString()),
                    obj.get("subscribe").AsBoolean()
            );
            case EVENT -> new SessionMessage.EventMessage(
                    ItemID.fromString(obj.get("itemId").AsString()),
                    obj.get("eventType").AsString(),
                    obj.ContainsKey("payload") ? obj.get("payload").AsString() : null
            );
            case STREAM -> new SessionMessage.StreamChunk(
                    obj.get("requestId").AsInt64Value(),
                    obj.get("data").GetByteString(),
                    obj.get("complete").AsBoolean()
            );
            case OK -> new SessionMessage.OkResponse(obj.get("requestId").AsInt64Value());
            case ERROR -> new SessionMessage.ErrorResponse(
                    obj.get("requestId").AsInt64Value(),
                    obj.get("code").AsString(),
                    obj.get("message").AsString()
            );
        };
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private CBORObject toCborStringList(List<String> list) {
        CBORObject arr = CBORObject.NewArray();
        for (String s : list) {
            arr.Add(CBORObject.FromString(s));
        }
        return arr;
    }

    private List<String> fromCborStringList(CBORObject arr) {
        List<String> list = new ArrayList<>();
        for (int i = 0; i < arr.size(); i++) {
            list.add(arr.get(i).AsString());
        }
        return list;
    }

    private CBORObject viewToCbor(View view) {
        // Simplified - Views will need proper serialization
        CBORObject obj = CBORObject.NewMap();
        obj.set("type", CBORObject.FromString(view.getClass().getSimpleName()));
        // TODO: Full View serialization
        return obj;
    }

    private View viewFromCbor(CBORObject obj) {
        // Simplified - Views will need proper deserialization
        // TODO: Full View deserialization
        return View.empty();
    }

    private CBORObject postingsToCbor(List<Posting> postings) {
        CBORObject arr = CBORObject.NewArray();
        for (Posting p : postings) {
            CBORObject pObj = CBORObject.NewMap();
            pObj.set("token", CBORObject.FromString(p.token()));
            pObj.set("target", CBORObject.FromString(p.target().encodeText()));
            arr.Add(pObj);
        }
        return arr;
    }

    private List<Posting> postingsFromCbor(CBORObject arr) {
        List<Posting> postings = new ArrayList<>();
        for (int i = 0; i < arr.size(); i++) {
            CBORObject pObj = arr.get(i);
            ItemID target = ItemID.fromString(pObj.get("target").AsString());
            postings.add(new Posting(
                    pObj.get("token").AsString(),
                    null,  // scope
                    target,
                    1.0f   // default weight
            ));
        }
        return postings;
    }
}
