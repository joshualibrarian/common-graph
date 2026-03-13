package dev.everydaythings.graph.network.transport;

import dev.everydaythings.graph.network.Heartbeat;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Sends heartbeat messages on writer-idle and closes dead connections on reader-idle.
 *
 * <p>Must be placed after {@link io.netty.handler.timeout.IdleStateHandler}
 * and after {@link dev.everydaythings.graph.network.ProtocolCodec} in the pipeline
 * (so it can write {@link Heartbeat} objects that the codec encodes).
 */
public class HeartbeatHandler extends ChannelDuplexHandler {

    private static final Logger log = LogManager.getLogger(HeartbeatHandler.class);

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        if (evt instanceof IdleStateEvent idle) {
            if (idle.state() == IdleState.WRITER_IDLE) {
                log.trace("Writer idle — sending heartbeat to {}", ctx.channel().remoteAddress());
                ctx.writeAndFlush(Heartbeat.INSTANCE);
            } else if (idle.state() == IdleState.READER_IDLE) {
                log.info("Reader idle — closing dead connection to {}", ctx.channel().remoteAddress());
                ctx.close();
            }
        } else {
            super.userEventTriggered(ctx, evt);
        }
    }
}
