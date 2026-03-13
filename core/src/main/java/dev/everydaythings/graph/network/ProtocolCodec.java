package dev.everydaythings.graph.network;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToMessageCodec;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;

/**
 * Unified Netty codec for all CG protocol messages.
 *
 * <p>Converts between raw {@link ByteBuf} frames and typed
 * {@link ProtocolMessage} objects in the Netty pipeline.
 *
 * <p>Replaces the former PeerCodec. Used by both peer and session transports.
 *
 * <p>Pipeline position: after frame decoder/encoder, before application handlers.
 * Outbound: encodes {@link ProtocolMessage} → tagged CBOR bytes → {@link ByteBuf}.
 * Inbound: decodes {@link ByteBuf} → tagged CBOR bytes → {@link ProtocolMessage}.
 */
public class ProtocolCodec extends MessageToMessageCodec<ByteBuf, ProtocolMessage> {

    private static final Logger log = LogManager.getLogger(ProtocolCodec.class);

    @Override
    protected void encode(ChannelHandlerContext ctx, ProtocolMessage msg, List<Object> out) {
        out.add(Unpooled.wrappedBuffer(msg.encode()));
    }

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf msg, List<Object> out) {
        byte[] data = new byte[msg.readableBytes()];
        msg.readBytes(data);
        try {
            out.add(ProtocolMessage.decode(data));
        } catch (Exception e) {
            log.warn("Failed to decode protocol message from {}: {}",
                    ctx.channel().remoteAddress(), e.getMessage());
        }
    }
}
