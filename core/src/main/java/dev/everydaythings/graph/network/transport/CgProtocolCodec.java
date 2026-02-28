package dev.everydaythings.graph.network.transport;

import dev.everydaythings.graph.network.message.ProtocolMessage;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToMessageCodec;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;

/**
 * Codec that converts between raw {@link ByteBuf} frames and typed
 * {@link ProtocolMessage} objects in the Netty pipeline.
 *
 * <p>Pipeline position: after frame decoder/encoder, before application handlers.
 * Outbound: encodes {@link ProtocolMessage} → CBOR bytes → {@link ByteBuf}.
 * Inbound: decodes {@link ByteBuf} → CBOR bytes → {@link ProtocolMessage}.
 */
public class CgProtocolCodec extends MessageToMessageCodec<ByteBuf, ProtocolMessage> {

    private static final Logger log = LogManager.getLogger(CgProtocolCodec.class);

    @Override
    protected void encode(ChannelHandlerContext ctx, ProtocolMessage msg, List<Object> out) {
        byte[] encoded = msg.encode();
        out.add(Unpooled.wrappedBuffer(encoded));
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
