package dev.everydaythings.graph.encoding;

import com.upokecenter.cbor.CBORObject;
import dev.everydaythings.graph.datum.Body;
import dev.everydaythings.graph.datum.Record;
import dev.everydaythings.graph.id.HashID;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Objects;

/**
 * Streaming CG-CBOR parser — drives an {@link EventSink} from incrementally
 * arriving bytes.
 *
 * <p>Each call to {@link #feed(byte[])} appends to an internal buffer and
 * then tries to drain whole top-level CBOR values from it, dispatching each
 * to the sink. Partial values stay buffered until further bytes arrive.
 *
 * <h2>Type dispatch</h2>
 *
 * <p>The parser reuses {@link CgCbor#fromCbor(CBORObject)} for type
 * dispatch — once the CBOR library has reassembled a complete {@code CBORObject}
 * tree, the existing decode logic produces the typed Java value. We then
 * switch on the result type and fire the matching sink callback:
 *
 * <ul>
 *   <li>{@link HashID} → {@link EventSink#onRef}</li>
 *   <li>{@link Body} → {@link EventSink#onBody}</li>
 *   <li>{@link Record} → {@link EventSink#onRecord}</li>
 *   <li>{@link String} → {@link EventSink#onText}</li>
 *   <li>{@link Boolean} → {@link EventSink#onBool}</li>
 *   <li>{@link Number} → {@link EventSink#onNumber}</li>
 *   <li>{@link CgCbor#TAG_ENCRYPTED} (special-cased before decode) → {@link EventSink#onEncrypted}</li>
 * </ul>
 *
 * <p>Encrypted envelopes are intercepted before the type dispatch because
 * {@link CgCbor#fromCbor} would otherwise reject the unrecognized tag — the
 * encrypted payload is opaque to the codec and is handed to the protocol
 * layer as raw bytes.
 *
 * <h2>EOF vs malformed</h2>
 *
 * <p>The CBOR library throws on both "ran out of bytes mid-value" and "byte
 * doesn't match any valid CBOR shape." We distinguish by checking the
 * underlying stream's {@code available()} after the throw:
 *
 * <ul>
 *   <li>{@code available() == 0} — consumed every buffered byte trying to
 *       complete the value, so it's <b>incomplete</b>. Rewind the buffer to
 *       the start of this value and wait for more bytes.</li>
 *   <li>{@code available() > 0} — bytes remained after the failure point, so
 *       the bytes were <b>malformed</b>. Fire {@link EventSink#onMalformed}
 *       and discard the rest of the buffer.</li>
 * </ul>
 */
final class CgCborStreamParser implements StreamParser {

    private final EventSink sink;
    private final ByteArrayOutputStream buffer = new ByteArrayOutputStream();
    private boolean closed = false;

    CgCborStreamParser(EventSink sink) {
        this.sink = Objects.requireNonNull(sink, "sink");
    }

    @Override
    public void feed(byte[] bytes) {
        if (closed) throw new IllegalStateException("parser is closed");
        if (bytes == null || bytes.length == 0) return;
        buffer.write(bytes, 0, bytes.length);
        drain();
    }

    @Override
    public void close() {
        closed = true;
        buffer.reset();
    }

    /**
     * Try to read as many complete CBOR values as possible from the buffer,
     * dispatch each to the sink, then compact the buffer to retain any
     * unconsumed bytes for the next {@link #feed} call.
     */
    private void drain() {
        if (buffer.size() == 0) return;
        byte[] data = buffer.toByteArray();
        ByteArrayInputStream in = new ByteArrayInputStream(data);
        int valueStart = 0;          // start of the current (yet-to-complete) value
        while (in.available() > 0) {
            try {
                CBORObject obj = CBORObject.Read(in);
                int consumed = data.length - in.available();
                valueStart = consumed;  // advance past the just-consumed value
                dispatch(obj);
            } catch (Exception e) {
                if (in.available() == 0) {
                    // Hit EOF mid-value — wait for more bytes.
                    break;
                }
                // Malformed bytes — surface to sink and bail; we have no
                // robust recovery point inside an arbitrary CBOR stream.
                sink.onMalformed(e);
                valueStart = data.length;
                break;
            }
        }
        // Compact: discard consumed prefix, keep any partial-value tail.
        buffer.reset();
        if (valueStart < data.length) {
            buffer.write(data, valueStart, data.length - valueStart);
        }
    }

    private void dispatch(CBORObject obj) {
        if (obj == null || obj.isNull()) return;
        try {
            // Encrypted envelopes are opaque to the codec — intercept before
            // fromCbor (which doesn't recognize TAG_ENCRYPTED) and hand the
            // payload bytes to the sink as-is.
            if (obj.isTagged() && obj.HasMostOuterTag(CgCbor.TAG_ENCRYPTED)) {
                sink.onEncrypted(obj.UntagOne().GetByteString());
                return;
            }
            Object value = CgCbor.fromCbor(obj);
            switch (value) {
                case null            -> { /* swallow */ }
                case HashID ref      -> sink.onRef(ref);
                case Body body       -> sink.onBody(body);
                case Record record   -> sink.onRecord(record);
                case String text     -> sink.onText(text);
                case Boolean bool    -> sink.onBool(bool);
                case Number number   -> sink.onNumber(number);
                default -> sink.onMalformed(new IllegalStateException(
                        "Unexpected top-level value type: " + value.getClass().getName()));
            }
        } catch (Exception e) {
            sink.onMalformed(e);
        }
    }
}
