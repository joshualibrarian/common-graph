package dev.everydaythings.graph.web;

import dev.everydaythings.graph.item.ItemOld;
import dev.everydaythings.graph.item.id.ItemID;
import dev.everydaythings.graph.language.Posting;
import dev.everydaythings.graph.network.AckOld;
import dev.everydaythings.graph.network.HeartbeatOld;
import dev.everydaythings.graph.network.ProtocolError;
import dev.everydaythings.graph.network.ProtocolMessage;
import dev.everydaythings.graph.network.session.SessionMessage;
import dev.everydaythings.graph.runtime.LibrarianOld;
import dev.everydaythings.graph.ui.scene.SceneCompiler;
import dev.everydaythings.graph.ui.scene.SceneNode;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.websocketx.BinaryWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler;
import lombok.extern.log4j.Log4j2;

import java.security.SecureRandom;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Handles CG session protocol messages over WebSocket.
 *
 * <p>Each WebSocket connection is a session. Binary frames carry the same
 * CBOR-tagged protocol messages as the TCP session protocol — no translation
 * layer, no REST API, no custom JSON format.
 *
 * <p>Session handling logic mirrors {@code SessionServer} but operates over
 * WebSocket frames instead of length-prefixed TCP.
 */
@Log4j2
public class WebSocketSessionHandler extends SimpleChannelInboundHandler<BinaryWebSocketFrame> {

    private final LibrarianOld librarian;

    // Per-connection session state
    private String sessionId;
    private byte[] authNonce;
    private boolean challengeSent = false;
    private boolean authenticated = false;
    private ItemOld context = null;
    private ItemID principalId = null;
    private final Set<ItemID> subscriptions = ConcurrentHashMap.newKeySet();

    public WebSocketSessionHandler(LibrarianOld librarian) {
        this.librarian = librarian;
    }

    // =========================================================================
    // WebSocket lifecycle
    // =========================================================================

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        if (evt instanceof WebSocketServerProtocolHandler.HandshakeComplete) {
            // WebSocket connection established — start session
            sessionId = UUID.randomUUID().toString().substring(0, 8);
            authNonce = new byte[32];
            new SecureRandom().nextBytes(authNonce);

            logger.info("WebSocket session connected: {}", sessionId);

            // Send auth challenge
            List<String> methods = List.of("token", "pairing");
            sendMessage(ctx, new SessionMessage.AuthChallenge(authNonce, methods));
            challengeSent = true;
        }
        super.userEventTriggered(ctx, evt);
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        if (sessionId != null) {
            librarian.unregisterSession(LibrarianOld.SessionInfo.unix("WebSession " + sessionId));
            logger.info("WebSocket session closed: {}", sessionId);
        }
        super.channelInactive(ctx);
    }

    // =========================================================================
    // Message handling
    // =========================================================================

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, BinaryWebSocketFrame frame) {
        byte[] data = new byte[frame.content().readableBytes()];
        frame.content().readBytes(data);

        try {
            ProtocolMessage message = ProtocolMessage.decode(data);

            if (message instanceof SessionMessage sm) {
                handleSessionMessage(ctx, sm);
            } else if (message instanceof HeartbeatOld) {
                // Respond with heartbeat
                sendMessage(ctx, HeartbeatOld.INSTANCE);
            }
            // Ignore Ack, ProtocolError from client
        } catch (Exception e) {
            logger.warn("Failed to decode WebSocket message from session {}: {}", sessionId, e.getMessage());
            sendMessage(ctx, new ProtocolError(0, "DECODE_ERROR", e.getMessage()));
        }
    }

    private void handleSessionMessage(ChannelHandlerContext ctx, SessionMessage message) {
        try {
            switch (message) {
                case SessionMessage.AuthToken m         -> handleAuthToken(ctx, m);
                case SessionMessage.AuthEngage m        -> handleAuthEngage(ctx, m);
                case SessionMessage.ContextRequest m    -> handleContext(ctx, m);
                case SessionMessage.DispatchRequest m   -> handleDispatch(ctx, m);
                case SessionMessage.LookupRequest m     -> handleLookup(ctx, m);
                case SessionMessage.SubscribeRequest m  -> handleSubscribe(ctx, m);
                default -> logger.warn("Unexpected session message from WebSocket {}: {}",
                        sessionId, message.getClass().getSimpleName());
            }
        } catch (Exception e) {
            logger.error("Error handling {} from WebSocket session {}",
                    message.getClass().getSimpleName(), sessionId, e);
            sendMessage(ctx, new ProtocolError(0, "INTERNAL_ERROR", e.getMessage()));
        }
    }

    // =========================================================================
    // Auth handlers
    // =========================================================================

    private void handleAuthToken(ChannelHandlerContext ctx, SessionMessage.AuthToken m) {
        if (!challengeSent) {
            sendMessage(ctx, SessionMessage.AuthResponse.failure("No challenge sent"));
            return;
        }

        // Verify token via the Librarian's public API
        String token = m.token();
        String localToken = librarian.sessionLocalToken();
        if (localToken == null || !token.equals(localToken)) {
            logger.warn("WebSocket session {} failed token auth", sessionId);
            sendMessage(ctx, SessionMessage.AuthResponse.failure("Invalid token"));
            return;
        }

        authenticated = true;

        if (m.principalId() != null) {
            principalId = m.principalId();
        } else {
            librarian.principal().ifPresent(p -> principalId = p.iid());
        }

        librarian.registerSession(LibrarianOld.SessionInfo.unix("WebSession " + sessionId));
        if (principalId != null) {
            sendMessage(ctx, SessionMessage.AuthResponse.successWithPrincipal(sessionId, principalId));
        } else {
            sendMessage(ctx, SessionMessage.AuthResponse.success(sessionId));
        }
        logger.info("WebSocket session {} authenticated via token", sessionId);
    }

    private void handleAuthEngage(ChannelHandlerContext ctx, SessionMessage.AuthEngage m) {
        if (!challengeSent) {
            sendMessage(ctx, SessionMessage.AuthResponse.failure("No challenge sent"));
            return;
        }

        String code = m.inviteCode();
        String name = m.name();

        if (name == null || name.isBlank()) {
            sendMessage(ctx, SessionMessage.AuthResponse.failure("Name required with invite code"));
            return;
        }

        // TODO: Engage flow (invite codes, user creation) requires shared token
        // management between SessionServer and WebGateway. For now, only token
        // auth is supported for web sessions.
        sendMessage(ctx, SessionMessage.AuthResponse.failure("Engage auth not yet supported for web sessions"));
    }

    // =========================================================================
    // Session handlers (mirror SessionServer logic)
    // =========================================================================

    private void handleContext(ChannelHandlerContext ctx, SessionMessage.ContextRequest m) {
        if (m.itemId() == null) {
            ItemOld ctxItem = context != null ? context : librarian;
            sendMessage(ctx, new SessionMessage.ContextResponse(ctxItem.iid(), ctxItem.displayToken(), null));
            return;
        }

        String itemIdStr = m.itemId().encodeText();

        if (m.resolve() && itemIdStr.startsWith("token:")) {
            String token = itemIdStr.substring(6);
            var tokenDict = librarian.tokenIndex();
            if (tokenDict == null) {
                sendMessage(ctx, new SessionMessage.ContextResponse(null, null, "Token dictionary not available"));
                return;
            }
            List<Posting> postings = tokenDict.lookup(token).limit(1).toList();
            if (postings.isEmpty()) {
                sendMessage(ctx, new SessionMessage.ContextResponse(null, null, "Not found: " + token));
                return;
            }
            ItemID iid = postings.get(0).target();
            context = librarian.get(iid, ItemOld.class).orElse(null);
        } else {
            context = librarian.get(m.itemId(), ItemOld.class).orElse(null);
        }

        if (context == null) {
            sendMessage(ctx, new SessionMessage.ContextResponse(null, null, "Item not found"));
        } else {
            sendMessage(ctx, new SessionMessage.ContextResponse(context.iid(), context.displayToken(), null));
        }
    }

    private void handleDispatch(ChannelHandlerContext ctx, SessionMessage.DispatchRequest m) {
        // Verb dispatch has been removed — all evaluation goes through FrameAssemblyPipeline
        sendMessage(ctx, SessionMessage.DispatchResponse.failure(m.requestId(),
                "Verb dispatch is no longer supported; use the frame assembly pipeline"));
    }

    private void handleLookup(ChannelHandlerContext ctx, SessionMessage.LookupRequest m) {
        var tokenDict = librarian.tokenIndex();
        List<Posting> postings = tokenDict != null ?
                tokenDict.lookup(m.query()).limit(m.limit()).toList() :
                List.of();
        sendMessage(ctx, new SessionMessage.LookupResponse(m.requestId(), postings));
    }

    private void handleSubscribe(ChannelHandlerContext ctx, SessionMessage.SubscribeRequest m) {
        if (m.subscribe()) {
            subscriptions.add(m.itemId());
            logger.info("WebSocket session {} subscribed to {}", sessionId, m.itemId().encodeText());
        } else {
            subscriptions.remove(m.itemId());
        }
        sendMessage(ctx, new AckOld(0));
    }

    // =========================================================================
    // Item View Compilation
    // =========================================================================

    /**
     * Compile an Item's scene annotations into a View.
     *
     * <p>This is the web equivalent of what the native session does via
     * ItemModel.toSurface() → SceneCompiler.compile(). Every item is
     * renderable through its @Scene annotations.
     */
    private SceneNode compileItemView(ItemOld item) {
        try {
            SceneNode result = SceneCompiler.compile(item);
            if (result != null) return result;
        } catch (Exception e) {
            logger.debug("Scene compilation failed for {}: {}", item.displayToken(), e.getMessage());
        }
        // Fallback
        return SceneNode.vertical()
                .add(SceneNode.ofText(item.displayToken()).classes("heading"))
                .add(SceneNode.ofText(item.iid().encodeText()).classes("muted"));
    }

    // =========================================================================
    // Wire format: ProtocolMessage ↔ BinaryWebSocketFrame
    // =========================================================================

    private void sendMessage(ChannelHandlerContext ctx, ProtocolMessage message) {
        byte[] encoded = message.encode();
        ByteBuf buf = Unpooled.wrappedBuffer(encoded);
        ctx.writeAndFlush(new BinaryWebSocketFrame(buf));
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        logger.warn("WebSocket error in session {}: {}", sessionId, cause.getMessage());
        ctx.close();
    }
}
