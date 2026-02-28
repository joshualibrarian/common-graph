package dev.everydaythings.graph.runtime.protocol;

import dev.everydaythings.graph.item.id.ItemID;
import dev.everydaythings.graph.language.Posting;
import dev.everydaythings.graph.ui.scene.View;
import lombok.Value;

import java.util.List;

/**
 * Messages in the Session Protocol.
 *
 * <p>Wire format:
 * <pre>
 * ┌─────────┬───────────┬───────────────────────────────────┐
 * │ Length  │ Type      │ CBOR Payload                      │
 * │ (4 byte)│ (1 byte)  │ (variable)                        │
 * └─────────┴───────────┴───────────────────────────────────┘
 * </pre>
 *
 * <p>All payloads are CBOR-encoded. The Session translates between
 * CBOR objects and rendered output (View panels, ANSI text, etc).
 */
public sealed interface SessionMessage {

    /**
     * Message type codes for wire format.
     */
    enum Type {
        AUTH(0x01),
        CONTEXT(0x02),
        DISPATCH(0x03),
        LOOKUP(0x04),
        SUBSCRIBE(0x05),
        EVENT(0x06),
        STREAM(0x07),
        ERROR(0x08),
        OK(0x09);

        private final int code;

        Type(int code) {
            this.code = code;
        }

        public int code() {
            return code;
        }

        public static Type fromCode(int code) {
            for (Type t : values()) {
                if (t.code == code) return t;
            }
            throw new IllegalArgumentException("Unknown message type: " + code);
        }
    }

    Type type();

    // =========================================================================
    // Request Messages (Client → Librarian)
    // =========================================================================

    /**
     * Authentication challenge sent by server after connect.
     */
    @Value
    class AuthChallenge implements SessionMessage {
        byte[] nonce;
        List<String> acceptedMethods;  // "token", "principal", "pairing"

        @Override
        public Type type() {
            return Type.AUTH;
        }
    }

    /**
     * Token-based authentication (simple, for local connections).
     */
    @Value
    class AuthToken implements SessionMessage {
        String token;
        ItemID principalId;  // Optional: who the session acts as

        @Override
        public Type type() {
            return Type.AUTH;
        }
    }

    /**
     * Principal signature authentication (for remote, future).
     */
    @Value
    class AuthPrincipal implements SessionMessage {
        ItemID principalId;
        byte[] signature;  // Signs the challenge nonce

        @Override
        public Type type() {
            return Type.AUTH;
        }
    }

    /**
     * Engage authentication — register a new user via invite code.
     *
     * <p>The client presents an invite code (obtained out-of-band from the
     * librarian's principal) and a desired username. On success, the server
     * creates a new User, binds the session, and issues a persistent token.
     */
    @Value
    class AuthEngage implements SessionMessage {
        String inviteCode;
        String name;  // Desired username for the new identity

        @Override
        public Type type() {
            return Type.AUTH;
        }
    }

    /**
     * Set or get the context item.
     */
    @Value
    class ContextRequest implements SessionMessage {
        ItemID itemId;  // null = get current context
        boolean resolve;  // If true, itemId is a token to resolve

        @Override
        public Type type() {
            return Type.CONTEXT;
        }

        public static ContextRequest get() {
            return new ContextRequest(null, false);
        }

        public static ContextRequest set(ItemID iid) {
            return new ContextRequest(iid, false);
        }

        public static ContextRequest resolve(String token) {
            // Token stored as ItemID text for simplicity; could be separate field
            return new ContextRequest(ItemID.fromString("token:" + token), true);
        }
    }

    /**
     * Dispatch an action to the context item.
     */
    @Value
    class DispatchRequest implements SessionMessage {
        String action;
        List<String> args;
        long requestId;  // For correlating async responses
        ItemID caller;   // Who is dispatching (principal identity)

        @Override
        public Type type() {
            return Type.DISPATCH;
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

        @Override
        public Type type() {
            return Type.LOOKUP;
        }
    }

    /**
     * Subscribe to changes on an item.
     */
    @Value
    class SubscribeRequest implements SessionMessage {
        ItemID itemId;
        boolean subscribe;  // false = unsubscribe

        @Override
        public Type type() {
            return Type.SUBSCRIBE;
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
        String persistentToken;  // For engage - token to use for future connections
        ItemID principalId;      // The resolved principal identity (set during engage or auto-token)
        String error;

        @Override
        public Type type() {
            return Type.AUTH;
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
    }

    /**
     * Context response (current context item).
     */
    @Value
    class ContextResponse implements SessionMessage {
        ItemID itemId;
        String label;  // Human-readable label
        String error;

        @Override
        public Type type() {
            return Type.CONTEXT;
        }
    }

    /**
     * Dispatch result.
     */
    @Value
    class DispatchResponse implements SessionMessage {
        long requestId;
        boolean success;
        View view;      // The result as a View
        String error;

        @Override
        public Type type() {
            return Type.DISPATCH;
        }

        public static DispatchResponse success(long requestId, View view) {
            return new DispatchResponse(requestId, true, view, null);
        }

        public static DispatchResponse failure(long requestId, String error) {
            return new DispatchResponse(requestId, false, null, error);
        }
    }

    /**
     * Lookup result.
     */
    @Value
    class LookupResponse implements SessionMessage {
        long requestId;
        List<Posting> postings;

        @Override
        public Type type() {
            return Type.LOOKUP;
        }
    }

    /**
     * Pushed event (for subscriptions).
     */
    @Value
    class EventMessage implements SessionMessage {
        ItemID itemId;
        String eventType;  // "updated", "deleted", "relation_added", etc.
        Object payload;    // Event-specific data

        @Override
        public Type type() {
            return Type.EVENT;
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

        @Override
        public Type type() {
            return Type.STREAM;
        }
    }

    /**
     * Simple OK acknowledgment.
     */
    @Value
    class OkResponse implements SessionMessage {
        long requestId;

        @Override
        public Type type() {
            return Type.OK;
        }
    }

    /**
     * Error response.
     */
    @Value
    class ErrorResponse implements SessionMessage {
        long requestId;
        String code;
        String message;

        @Override
        public Type type() {
            return Type.ERROR;
        }
    }
}
