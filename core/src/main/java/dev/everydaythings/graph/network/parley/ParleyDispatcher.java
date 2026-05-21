package dev.everydaythings.graph.network.parley;

import dev.everydaythings.graph.datum.Body;
import dev.everydaythings.graph.datum.Frame;
import dev.everydaythings.graph.datum.Opaque;
import dev.everydaythings.graph.datum.Record;
import dev.everydaythings.graph.ref.HashID;
import dev.everydaythings.graph.runtime.SubmitResult;
import dev.everydaythings.graph.runtime.librarian.Librarian;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import lombok.extern.log4j.Log4j2;

/**
 * Netty inbound handler that dispatches decoded CG-CBOR values arriving
 * from {@link ParleyFramer}.
 *
 * <p>Holds a reference to the local {@link Librarian} and the
 * {@link RemoteConnection} representing this Parley conversation.  For
 * each top-level value:
 *
 * <ul>
 *   <li>{@link Body} → submit as a frame to the librarian; any response
 *       frames flow back through the connection.</li>
 *   <li>{@link Record}, {@link HashID}, {@link String}, {@link Opaque} —
 *       reserved for the conversational primitives.  Each one is a TODO
 *       in the current cut.</li>
 *   <li>{@link Boolean}, {@link Number} — reserved primitive payloads.</li>
 * </ul>
 *
 * <p>This is the same dispatch shape that used to live in
 * {@code Parley.handleValue}; the only thing that's changed is that it's
 * now a Netty handler at the end of the pipeline instead of a private
 * method invoked from a streaming-parser callback.
 */
@Log4j2
public final class ParleyDispatcher extends SimpleChannelInboundHandler<Object> {

    private final Librarian librarian;
    private final RemoteConnection connection;

    public ParleyDispatcher(Librarian librarian, RemoteConnection connection) {
        this.librarian = librarian;
        this.connection = connection;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, Object value) {
        switch (value) {
            case Body body      -> submitFrame(body);
            case Record record  -> handleRecord(record);
            case HashID ref     -> handleFetchRequest(ref);
            case String text    -> handleTokenLookup(text);
            case Opaque op      -> handleOpaque(op);
            case Boolean b      -> logger.debug("Parley onBool (reserved): {}", b);
            case Number n       -> logger.debug("Parley onNumber (reserved): {}", n);
            default             -> logger.warn("Parley unexpected top-level value: {}",
                    value == null ? "null" : value.getClass().getName());
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        logger.warn("Parley pipeline error; closing connection", cause);
        connection.close();
    }

    // ==================================================================================
    // Value-type conventions — unchanged from the pre-Netty Parley shape.
    // ==================================================================================

    private void submitFrame(Body body) {
        logger.debug("Parley onBody: head={}", body.headRef());
        try {
            SubmitResult result = librarian.submit(Frame.of(body));
            for (Frame response : result.responses()) {
                connection.send(response.body());
            }
        } catch (Exception e) {
            logger.warn("Submit failed for body {}: {}", body.headRef(), e.toString());
        }
    }

    private void handleRecord(Record record) {
        // TODO: resolve the referenced body (locally if present, point-and-grunt
        // if not), then submit Frame(body, [record]).
        logger.debug("Parley onRecord (not yet dispatched): {}", record);
    }

    private void handleFetchRequest(HashID ref) {
        // TODO: point-and-grunt — librarian.fetch(ref) then connection.send(result).
        logger.debug("Parley onRef (not yet dispatched): {}", ref);
    }

    private void handleTokenLookup(String text) {
        // TODO: token-dictionary lookup → connection.send(resolvedRef).
        logger.debug("Parley onText (not yet dispatched): {}", text);
    }

    private void handleOpaque(Opaque opaque) {
        // TODO: dispatch by variant.  Encrypted → decrypt via local vault and
        // re-feed cleartext through this connection's pipeline.  Compressed →
        // decompress and reinject.  Redacted at top-of-stream is just a hash;
        // save the recordRefs for later.
        logger.debug("Parley Opaque (not yet dispatched): {}", opaque);
    }
}
