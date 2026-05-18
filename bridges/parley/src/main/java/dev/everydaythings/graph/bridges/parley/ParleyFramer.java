package dev.everydaythings.graph.bridges.parley;

import dev.everydaythings.graph.encoding.Encoding;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;
import lombok.extern.log4j.Log4j2;

import java.io.ByteArrayInputStream;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Netty inbound decoder that reads one top-level value per call through a
 * codec-agnostic {@link Encoding}.
 *
 * <p>Bytes accumulate in the pipeline's cumulative buffer (handled by
 * {@link ByteToMessageDecoder} — no manual buffer math needed here).  Each
 * call delegates to {@link Encoding#decodeOne(java.io.InputStream)}, which
 * is the single SPI hook between Parley and whatever codec point-and-grunt
 * agreed upon.  ParleyFramer never references a specific encoding type;
 * CG-CBOR, a hypothetical CG-JSON, anything else plug in by implementing
 * the SPI method.
 *
 * <p>Outcome dispatch (see the SPI contract):
 *
 * <ul>
 *   <li><b>Complete value</b>: encoding consumed N bytes, returns the
 *       value.  Framer advances Netty's reader index by N and emits the
 *       value downstream.</li>
 *   <li><b>Short read</b>: encoding consumed 0 bytes, returns empty.
 *       Framer leaves the reader index where it was; Netty will retry when
 *       more bytes arrive.</li>
 *   <li><b>Decoded null</b>: encoding consumed N bytes, returns empty.
 *       Framer advances by N (so the bytes don't replay) but emits
 *       nothing.</li>
 *   <li><b>Malformed</b>: encoding throws.  Framer wraps in
 *       {@link ParleyDecodeException} and propagates; the pipeline's
 *       {@link ParleyDispatcher} sees it as {@code exceptionCaught} and
 *       closes the connection.</li>
 * </ul>
 */
@Log4j2
public final class ParleyFramer extends ByteToMessageDecoder {

    private final Encoding codec;

    public ParleyFramer(Encoding codec) {
        this.codec = Objects.requireNonNull(codec, "codec");
    }

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) {
        int available = in.readableBytes();
        if (available == 0) return;

        // Snapshot the cumulative buffer to a byte[] backed input stream.  The
        // codec's decodeOne contract uses InputStream position to report
        // "bytes consumed" back to us.
        byte[] snapshot = new byte[available];
        in.markReaderIndex();
        in.readBytes(snapshot);
        ByteArrayInputStream bin = new ByteArrayInputStream(snapshot);

        Optional<Object> result;
        try {
            result = codec.decodeOne(bin);
        } catch (Exception e) {
            in.resetReaderIndex();
            throw new ParleyDecodeException("Malformed payload from peer", e);
        }

        int consumed = available - bin.available();
        // Reset Netty's reader, then advance by exactly what the codec consumed.
        // On short read: consumed == 0, reader stays put, Netty retries later.
        // On null / value: consumed > 0, reader advances; nothing or the value
        // gets emitted respectively.
        in.resetReaderIndex();
        if (consumed > 0) {
            in.skipBytes(consumed);
        }
        result.ifPresent(out::add);
    }

    /** Wraps any codec decode error for the pipeline to see as a single class. */
    public static final class ParleyDecodeException extends RuntimeException {
        public ParleyDecodeException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
