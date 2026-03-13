package dev.everydaythings.graph.network.session;

import com.upokecenter.cbor.CBORObject;
import dev.everydaythings.graph.Canonical.CgTag;
import dev.everydaythings.graph.item.id.ItemID;
import dev.everydaythings.graph.language.Posting;
import dev.everydaythings.graph.network.ProtocolMessage;
import dev.everydaythings.graph.ui.scene.View;
import lombok.Value;

import java.util.ArrayList;
import java.util.List;

/**
 * Messages in the Session Protocol.
 *
 * <p>Each message type has a CBOR tag for wire discrimination.
 * Subtypes within a tag are distinguished by field presence.
 *
 * <p>Wire format: {@code [4-byte length][CBOR Tag(N, map)]}
 */
public sealed interface SessionMessage extends ProtocolMessage {

    // =========================================================================
    // Request Messages (Client → Librarian)
    // =========================================================================

    /**
     * Authentication challenge sent by server after connect.
     */
    @Value
    class AuthChallenge implements SessionMessage {
        byte[] nonce;
        List<String> acceptedMethods;

        @Override public int tag() { return CgTag.AUTH; }

        @Override
        public CBORObject toCbor() {
            CBORObject obj = CBORObject.NewMap();
            obj.set("nonce", CBORObject.FromByteArray(nonce));
            obj.set("methods", toCborStringList(acceptedMethods));
            return obj;
        }

        static AuthChallenge fromCbor(CBORObject obj) {
            return new AuthChallenge(
                    obj.get("nonce").GetByteString(),
                    fromCborStringList(obj.get("methods"))
            );
        }
    }

    /**
     * Token-based authentication (simple, for local connections).
     */
    @Value
    class AuthToken implements SessionMessage {
        String token;
        ItemID principalId;

        @Override public int tag() { return CgTag.AUTH; }

        @Override
        public CBORObject toCbor() {
            CBORObject obj = CBORObject.NewMap();
            obj.set("token", CBORObject.FromString(token));
            if (principalId != null) obj.set("principalId", CBORObject.FromString(principalId.encodeText()));
            return obj;
        }

        static AuthToken fromCbor(CBORObject obj) {
            return new AuthToken(
                    obj.get("token").AsString(),
                    obj.ContainsKey("principalId") ? ItemID.fromString(obj.get("principalId").AsString()) : null
            );
        }
    }

    /**
     * Principal signature authentication (for remote, future).
     */
    @Value
    class AuthPrincipal implements SessionMessage {
        ItemID principalId;
        byte[] signature;

        @Override public int tag() { return CgTag.AUTH; }

        @Override
        public CBORObject toCbor() {
            CBORObject obj = CBORObject.NewMap();
            obj.set("principalId", CBORObject.FromString(principalId.encodeText()));
            obj.set("signature", CBORObject.FromByteArray(signature));
            return obj;
        }

        static AuthPrincipal fromCbor(CBORObject obj) {
            return new AuthPrincipal(
                    ItemID.fromString(obj.get("principalId").AsString()),
                    obj.get("signature").GetByteString()
            );
        }
    }

    /**
     * Engage authentication — register a new user via invite code.
     */
    @Value
    class AuthEngage implements SessionMessage {
        String inviteCode;
        String name;

        @Override public int tag() { return CgTag.AUTH; }

        @Override
        public CBORObject toCbor() {
            CBORObject obj = CBORObject.NewMap();
            obj.set("inviteCode", CBORObject.FromString(inviteCode));
            if (name != null) obj.set("name", CBORObject.FromString(name));
            return obj;
        }

        static AuthEngage fromCbor(CBORObject obj) {
            return new AuthEngage(
                    obj.get("inviteCode").AsString(),
                    obj.ContainsKey("name") ? obj.get("name").AsString() : null
            );
        }
    }

    /**
     * Set or get the context item.
     */
    @Value
    class ContextRequest implements SessionMessage {
        ItemID itemId;
        boolean resolve;

        @Override public int tag() { return CgTag.CONTEXT; }

        @Override
        public CBORObject toCbor() {
            CBORObject obj = CBORObject.NewMap();
            if (itemId != null) obj.set("itemId", CBORObject.FromString(itemId.encodeText()));
            obj.set("resolve", resolve ? CBORObject.True : CBORObject.False);
            return obj;
        }

        public static ContextRequest get() {
            return new ContextRequest(null, false);
        }

        public static ContextRequest set(ItemID iid) {
            return new ContextRequest(iid, false);
        }

        public static ContextRequest resolve(String token) {
            return new ContextRequest(ItemID.fromString("token:" + token), true);
        }

        static ContextRequest fromCbor(CBORObject obj) {
            return new ContextRequest(
                    obj.ContainsKey("itemId") ? ItemID.fromString(obj.get("itemId").AsString()) : null,
                    obj.ContainsKey("resolve") && obj.get("resolve").AsBoolean()
            );
        }
    }

    /**
     * Dispatch an action to the context item.
     */
    @Value
    class DispatchRequest implements SessionMessage {
        String action;
        List<String> args;
        long requestId;
        ItemID caller;

        @Override public int tag() { return CgTag.DISPATCH; }

        @Override
        public CBORObject toCbor() {
            CBORObject obj = CBORObject.NewMap();
            obj.set("action", CBORObject.FromString(action));
            obj.set("args", toCborStringList(args));
            obj.set("requestId", CBORObject.FromInt64(requestId));
            if (caller != null) obj.set("caller", CBORObject.FromString(caller.encodeText()));
            return obj;
        }

        static DispatchRequest fromCbor(CBORObject obj) {
            return new DispatchRequest(
                    obj.get("action").AsString(),
                    fromCborStringList(obj.get("args")),
                    obj.get("requestId").AsInt64Value(),
                    obj.ContainsKey("caller") ? ItemID.fromString(obj.get("caller").AsString()) : null
            );
        }
    }

    /**
     * Lookup tokens (for completion/search).
     */
    @Value
    class LookupRequest implements SessionMessage {
        String query;
        int limit;
        long requestId;

        @Override public int tag() { return CgTag.LOOKUP; }

        @Override
        public CBORObject toCbor() {
            CBORObject obj = CBORObject.NewMap();
            obj.set("query", CBORObject.FromString(query));
            obj.set("limit", CBORObject.FromInt32(limit));
            obj.set("requestId", CBORObject.FromInt64(requestId));
            return obj;
        }

        static LookupRequest fromCbor(CBORObject obj) {
            return new LookupRequest(
                    obj.get("query").AsString(),
                    obj.get("limit").AsInt32(),
                    obj.get("requestId").AsInt64Value()
            );
        }
    }

    /**
     * Subscribe to changes on an item.
     */
    @Value
    class SubscribeRequest implements SessionMessage {
        ItemID itemId;
        boolean subscribe;

        @Override public int tag() { return CgTag.SUBSCRIBE; }

        @Override
        public CBORObject toCbor() {
            CBORObject obj = CBORObject.NewMap();
            obj.set("itemId", CBORObject.FromString(itemId.encodeText()));
            obj.set("subscribe", subscribe ? CBORObject.True : CBORObject.False);
            return obj;
        }

        static SubscribeRequest fromCbor(CBORObject obj) {
            return new SubscribeRequest(
                    ItemID.fromString(obj.get("itemId").AsString()),
                    obj.get("subscribe").AsBoolean()
            );
        }
    }

    // =========================================================================
    // Response Messages (Librarian → Client)
    // =========================================================================

    /**
     * Authentication result.
     */
    @Value
    class AuthResponse implements SessionMessage {
        boolean success;
        String sessionId;
        String persistentToken;
        ItemID principalId;
        String error;

        @Override public int tag() { return CgTag.AUTH; }

        @Override
        public CBORObject toCbor() {
            CBORObject obj = CBORObject.NewMap();
            obj.set("success", success ? CBORObject.True : CBORObject.False);
            if (sessionId != null) obj.set("sessionId", CBORObject.FromString(sessionId));
            if (persistentToken != null) obj.set("persistentToken", CBORObject.FromString(persistentToken));
            if (principalId != null) obj.set("principalId", CBORObject.FromString(principalId.encodeText()));
            if (error != null) obj.set("error", CBORObject.FromString(error));
            return obj;
        }

        public static AuthResponse success(String sessionId) {
            return new AuthResponse(true, sessionId, null, null, null);
        }

        public static AuthResponse successWithPrincipal(String sessionId, ItemID principalId) {
            return new AuthResponse(true, sessionId, null, principalId, null);
        }

        public static AuthResponse successWithToken(String sessionId, String persistentToken) {
            return new AuthResponse(true, sessionId, persistentToken, null, null);
        }

        public static AuthResponse engaged(String sessionId, String persistentToken, ItemID principalId) {
            return new AuthResponse(true, sessionId, persistentToken, principalId, null);
        }

        public static AuthResponse failure(String error) {
            return new AuthResponse(false, null, null, null, error);
        }

        static AuthResponse fromCbor(CBORObject obj) {
            return new AuthResponse(
                    obj.get("success").AsBoolean(),
                    obj.ContainsKey("sessionId") ? obj.get("sessionId").AsString() : null,
                    obj.ContainsKey("persistentToken") ? obj.get("persistentToken").AsString() : null,
                    obj.ContainsKey("principalId") ? ItemID.fromString(obj.get("principalId").AsString()) : null,
                    obj.ContainsKey("error") ? obj.get("error").AsString() : null
            );
        }
    }

    /**
     * Context response (current context item).
     */
    @Value
    class ContextResponse implements SessionMessage {
        ItemID itemId;
        String label;
        String error;

        @Override public int tag() { return CgTag.CONTEXT; }

        @Override
        public CBORObject toCbor() {
            CBORObject obj = CBORObject.NewMap();
            if (itemId != null) obj.set("itemId", CBORObject.FromString(itemId.encodeText()));
            if (label != null) obj.set("label", CBORObject.FromString(label));
            if (error != null) obj.set("error", CBORObject.FromString(error));
            return obj;
        }

        static ContextResponse fromCbor(CBORObject obj) {
            return new ContextResponse(
                    obj.ContainsKey("itemId") ? ItemID.fromString(obj.get("itemId").AsString()) : null,
                    obj.ContainsKey("label") ? obj.get("label").AsString() : null,
                    obj.ContainsKey("error") ? obj.get("error").AsString() : null
            );
        }
    }

    /**
     * Dispatch result.
     */
    @Value
    class DispatchResponse implements SessionMessage {
        long requestId;
        boolean success;
        View view;
        String error;

        @Override public int tag() { return CgTag.DISPATCH; }

        @Override
        public CBORObject toCbor() {
            CBORObject obj = CBORObject.NewMap();
            obj.set("requestId", CBORObject.FromInt64(requestId));
            obj.set("success", success ? CBORObject.True : CBORObject.False);
            if (view != null) obj.set("view", viewToCbor(view));
            if (error != null) obj.set("error", CBORObject.FromString(error));
            return obj;
        }

        public static DispatchResponse success(long requestId, View view) {
            return new DispatchResponse(requestId, true, view, null);
        }

        public static DispatchResponse failure(long requestId, String error) {
            return new DispatchResponse(requestId, false, null, error);
        }

        static DispatchResponse fromCbor(CBORObject obj) {
            return new DispatchResponse(
                    obj.get("requestId").AsInt64Value(),
                    obj.get("success").AsBoolean(),
                    obj.ContainsKey("view") ? viewFromCbor(obj.get("view")) : null,
                    obj.ContainsKey("error") ? obj.get("error").AsString() : null
            );
        }
    }

    /**
     * Lookup result.
     */
    @Value
    class LookupResponse implements SessionMessage {
        long requestId;
        List<Posting> postings;

        @Override public int tag() { return CgTag.LOOKUP; }

        @Override
        public CBORObject toCbor() {
            CBORObject obj = CBORObject.NewMap();
            obj.set("requestId", CBORObject.FromInt64(requestId));
            obj.set("postings", postingsToCbor(postings));
            return obj;
        }

        static LookupResponse fromCbor(CBORObject obj) {
            return new LookupResponse(
                    obj.get("requestId").AsInt64Value(),
                    postingsFromCbor(obj.get("postings"))
            );
        }
    }

    /**
     * Pushed event (for subscriptions).
     */
    @Value
    class EventMessage implements SessionMessage {
        ItemID itemId;
        String eventType;
        Object payload;

        @Override public int tag() { return CgTag.EVENT; }

        @Override
        public CBORObject toCbor() {
            CBORObject obj = CBORObject.NewMap();
            obj.set("itemId", CBORObject.FromString(itemId.encodeText()));
            obj.set("eventType", CBORObject.FromString(eventType));
            if (payload != null) {
                obj.set("payload", CBORObject.FromString(payload.toString()));
            }
            return obj;
        }

        static EventMessage fromCbor(CBORObject obj) {
            return new EventMessage(
                    ItemID.fromString(obj.get("itemId").AsString()),
                    obj.get("eventType").AsString(),
                    obj.ContainsKey("payload") ? obj.get("payload").AsString() : null
            );
        }
    }

    /**
     * Streaming chunk (for long-running output).
     */
    @Value
    class StreamChunk implements SessionMessage {
        long requestId;
        byte[] data;
        boolean complete;

        @Override public int tag() { return CgTag.STREAM; }

        @Override
        public CBORObject toCbor() {
            CBORObject obj = CBORObject.NewMap();
            obj.set("requestId", CBORObject.FromInt64(requestId));
            obj.set("data", CBORObject.FromByteArray(data));
            obj.set("complete", complete ? CBORObject.True : CBORObject.False);
            return obj;
        }

        static StreamChunk fromCbor(CBORObject obj) {
            return new StreamChunk(
                    obj.get("requestId").AsInt64Value(),
                    obj.get("data").GetByteString(),
                    obj.get("complete").AsBoolean()
            );
        }
    }

    // =========================================================================
    // Tag-based decode methods (called from ProtocolMessage.decode)
    // =========================================================================

    static SessionMessage decodeAuth(CBORObject map) {
        if (map.ContainsKey("success"))    return AuthResponse.fromCbor(map);
        if (map.ContainsKey("methods"))    return AuthChallenge.fromCbor(map);
        if (map.ContainsKey("token"))      return AuthToken.fromCbor(map);
        if (map.ContainsKey("inviteCode")) return AuthEngage.fromCbor(map);
        if (map.ContainsKey("signature"))  return AuthPrincipal.fromCbor(map);
        throw new IllegalArgumentException("Unknown AUTH message format");
    }

    static SessionMessage decodeContext(CBORObject map) {
        if (map.ContainsKey("label") || (map.ContainsKey("error") && !map.ContainsKey("resolve"))) {
            return ContextResponse.fromCbor(map);
        }
        return ContextRequest.fromCbor(map);
    }

    static SessionMessage decodeDispatch(CBORObject map) {
        if (map.ContainsKey("action")) return DispatchRequest.fromCbor(map);
        return DispatchResponse.fromCbor(map);
    }

    static SessionMessage decodeLookup(CBORObject map) {
        if (map.ContainsKey("query")) return LookupRequest.fromCbor(map);
        return LookupResponse.fromCbor(map);
    }

    static SessionMessage decodeSubscribe(CBORObject map) {
        return SubscribeRequest.fromCbor(map);
    }

    static SessionMessage decodeEvent(CBORObject map) {
        return EventMessage.fromCbor(map);
    }

    static SessionMessage decodeStream(CBORObject map) {
        return StreamChunk.fromCbor(map);
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private static CBORObject toCborStringList(List<String> list) {
        CBORObject arr = CBORObject.NewArray();
        for (String s : list) {
            arr.Add(CBORObject.FromString(s));
        }
        return arr;
    }

    private static List<String> fromCborStringList(CBORObject arr) {
        List<String> list = new ArrayList<>();
        for (int i = 0; i < arr.size(); i++) {
            list.add(arr.get(i).AsString());
        }
        return list;
    }

    private static CBORObject viewToCbor(View view) {
        CBORObject obj = CBORObject.NewMap();
        obj.set("type", CBORObject.FromString(view.getClass().getSimpleName()));
        return obj;
    }

    private static View viewFromCbor(CBORObject obj) {
        return View.empty();
    }

    private static CBORObject postingsToCbor(List<Posting> postings) {
        CBORObject arr = CBORObject.NewArray();
        for (Posting p : postings) {
            CBORObject pObj = CBORObject.NewMap();
            pObj.set("token", CBORObject.FromString(p.token()));
            pObj.set("target", CBORObject.FromString(p.target().encodeText()));
            arr.Add(pObj);
        }
        return arr;
    }

    private static List<Posting> postingsFromCbor(CBORObject arr) {
        List<Posting> postings = new ArrayList<>();
        for (int i = 0; i < arr.size(); i++) {
            CBORObject pObj = arr.get(i);
            ItemID target = ItemID.fromString(pObj.get("target").AsString());
            postings.add(new Posting(
                    pObj.get("token").AsString(),
                    null,
                    target,
                    1.0f
            ));
        }
        return postings;
    }
}
