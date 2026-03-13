package dev.everydaythings.graph.network.transport;

import dev.everydaythings.graph.crypt.Vault;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.security.PublicKey;
import java.util.ArrayDeque;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;

/**
 * Netty handler that performs a Noise XX handshake on channel activation
 * and then encrypts/decrypts all subsequent messages with session keys.
 *
 * <p>Pipeline position: between length-field framing and PeerCodec.
 * During handshake, this handler intercepts raw frames. After handshake,
 * it transparently encrypts outbound and decrypts inbound ByteBufs.
 *
 * <pre>
 * Pipeline:
 *   ┌───────────────────┐
 *   │  Frame Decoder     │  ← length-prefixed framing
 *   │  Frame Encoder     │
 *   ├───────────────────┤
 *   │  TransportEncrypt  │  ← THIS HANDLER (handshake + AEAD)
 *   ├───────────────────┤
 *   │  PeerCodec   │  ← CBOR ↔ PeerMessage
 *   │  IdleState          │
 *   │  Heartbeat          │
 *   │  Application        │
 *   └───────────────────┘
 * </pre>
 *
 * <p>During handshake, application writes are buffered. Once the handshake
 * completes, buffered messages are flushed encrypted.
 */
public class TransportEncryptionHandler extends ChannelDuplexHandler {

    private static final Logger log = LogManager.getLogger(TransportEncryptionHandler.class);

    private final TransportCrypto.HandshakeState handshake;
    private TransportCrypto.SessionCipherPair session;

    // Buffer application writes during handshake
    private final Queue<PendingWrite> pendingWrites = new ArrayDeque<>();
    private boolean handshakeComplete = false;

    // Future completed when handshake finishes (for connection establishment)
    private final CompletableFuture<PublicKey> handshakeFuture = new CompletableFuture<>();

    /**
     * Create a transport encryption handler.
     *
     * @param role  Whether we're the initiator or responder
     * @param vault Vault containing our X25519 encryption key
     */
    public TransportEncryptionHandler(TransportCrypto.Role role, Vault vault) {
        this.handshake = new TransportCrypto.HandshakeState(role, vault);
    }

    /**
     * Future that completes with the authenticated remote static key
     * when the handshake finishes.
     */
    public CompletableFuture<PublicKey> handshakeFuture() {
        return handshakeFuture;
    }

    // ==================================================================================
    // Handshake lifecycle
    // ==================================================================================

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        if (handshake.role() == TransportCrypto.Role.INITIATOR) {
            // Initiator sends the first handshake message
            byte[] msg1 = handshake.advance(null);
            sendHandshake(ctx, msg1);
            log.debug("Transport handshake started (initiator) to {}", ctx.channel().remoteAddress());
        } else {
            log.debug("Transport handshake waiting (responder) from {}", ctx.channel().remoteAddress());
        }
        // Don't fire channelActive upstream yet — wait until handshake completes
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (!(msg instanceof ByteBuf buf)) {
            ctx.fireChannelRead(msg);
            return;
        }

        try {
            byte[] data = new byte[buf.readableBytes()];
            buf.readBytes(data);

            if (!handshakeComplete) {
                handleHandshakeMessage(ctx, data);
            } else {
                // Decrypt session message
                handleSessionMessage(ctx, data);
            }
        } finally {
            buf.release();
        }
    }

    private void handleHandshakeMessage(ChannelHandlerContext ctx, byte[] data) {
        // Strip the message type byte
        if (data.length < 1 || data[0] != TransportCrypto.MSG_HANDSHAKE) {
            log.warn("Expected handshake message, got type: {}",
                    data.length > 0 ? data[0] : "empty");
            ctx.close();
            return;
        }
        byte[] payload = new byte[data.length - 1];
        System.arraycopy(data, 1, payload, 0, payload.length);

        try {
            byte[] response = handshake.advance(payload);

            if (response != null) {
                sendHandshake(ctx, response);
            }

            if (handshake.isComplete()) {
                completeHandshake(ctx);
            }
        } catch (Exception e) {
            log.warn("Handshake failed with {}: {}", ctx.channel().remoteAddress(), e.getMessage());
            handshakeFuture.completeExceptionally(e);
            ctx.close();
        }
    }

    private void completeHandshake(ChannelHandlerContext ctx) {
        session = handshake.split();
        handshakeComplete = true;

        PublicKey remoteKey = handshake.remoteStaticKey();
        log.debug("Transport handshake complete with {} (role: {})",
                ctx.channel().remoteAddress(), handshake.role());

        handshakeFuture.complete(remoteKey);

        // Fire channelActive upstream now that encryption is established
        ctx.fireChannelActive();

        // Flush any buffered application writes
        flushPendingWrites(ctx);
    }

    private void handleSessionMessage(ChannelHandlerContext ctx, byte[] data) {
        if (data.length < 1 || data[0] != TransportCrypto.MSG_TRANSPORT) {
            log.warn("Expected transport message, got type: {}",
                    data.length > 0 ? data[0] : "empty");
            return;
        }
        byte[] payload = new byte[data.length - 1];
        System.arraycopy(data, 1, payload, 0, payload.length);

        try {
            byte[] plaintext = session.recv().decrypt(payload);
            ctx.fireChannelRead(Unpooled.wrappedBuffer(plaintext));
        } catch (SecurityException e) {
            log.warn("Transport decryption failed from {}: {}", ctx.channel().remoteAddress(), e.getMessage());
            ctx.close();
        }
    }

    // ==================================================================================
    // Outbound: encrypt application messages
    // ==================================================================================

    @Override
    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
        if (!handshakeComplete) {
            // Buffer the write until handshake completes
            if (msg instanceof ByteBuf buf) {
                byte[] data = new byte[buf.readableBytes()];
                buf.readBytes(data);
                buf.release();
                pendingWrites.add(new PendingWrite(data, promise));
            } else {
                // Non-ByteBuf during handshake — shouldn't happen, but pass through
                ctx.write(msg, promise);
            }
            return;
        }

        if (msg instanceof ByteBuf buf) {
            try {
                byte[] data = new byte[buf.readableBytes()];
                buf.readBytes(data);
                byte[] encrypted = session.send().encrypt(data);

                // Prepend transport message type
                byte[] framed = new byte[1 + encrypted.length];
                framed[0] = TransportCrypto.MSG_TRANSPORT;
                System.arraycopy(encrypted, 0, framed, 1, encrypted.length);

                ctx.write(Unpooled.wrappedBuffer(framed), promise);
            } finally {
                buf.release();
            }
        } else {
            ctx.write(msg, promise);
        }
    }

    private void flushPendingWrites(ChannelHandlerContext ctx) {
        PendingWrite pw;
        while ((pw = pendingWrites.poll()) != null) {
            byte[] encrypted = session.send().encrypt(pw.data);
            byte[] framed = new byte[1 + encrypted.length];
            framed[0] = TransportCrypto.MSG_TRANSPORT;
            System.arraycopy(encrypted, 0, framed, 1, encrypted.length);
            ctx.write(Unpooled.wrappedBuffer(framed), pw.promise);
        }
        ctx.flush();
    }

    // ==================================================================================
    // Cleanup
    // ==================================================================================

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        if (session != null) {
            session.destroy();
            session = null;
        }
        // Fail any pending writes
        PendingWrite pw;
        while ((pw = pendingWrites.poll()) != null) {
            pw.promise.setFailure(new java.io.IOException("Channel closed during handshake"));
        }
        if (!handshakeFuture.isDone()) {
            handshakeFuture.completeExceptionally(
                    new java.io.IOException("Channel closed during handshake"));
        }
        ctx.fireChannelInactive();
    }

    // ==================================================================================
    // Helpers
    // ==================================================================================

    private void sendHandshake(ChannelHandlerContext ctx, byte[] payload) {
        byte[] framed = new byte[1 + payload.length];
        framed[0] = TransportCrypto.MSG_HANDSHAKE;
        System.arraycopy(payload, 0, framed, 1, payload.length);
        ctx.writeAndFlush(Unpooled.wrappedBuffer(framed));
    }

    /**
     * Check if transport encryption is active (handshake complete).
     */
    public boolean isEncrypted() {
        return handshakeComplete;
    }

    private record PendingWrite(byte[] data, ChannelPromise promise) {}
}
