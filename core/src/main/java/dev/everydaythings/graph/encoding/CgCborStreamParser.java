package dev.everydaythings.graph.encoding;

import com.upokecenter.cbor.CBORObject;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Streaming CG-CBOR parser — drives a typed-value consumer from incrementally
 * arriving bytes.
 *
 * <p>Each call to {@link #feed(byte[])} appends to an internal buffer and
 * then tries to drain whole top-level CBOR values from it, handing each to
 * {@code onValue}.  Parse failures go to {@code onError}.  Partial values
 * stay buffered until further bytes arrive.
 *
 * <h2>What the consumer sees</h2>
 *
 * <p>The parser reuses {@link CgCbor#fromCbor(CBORObject)} for type
 * dispatch; once a complete {@code CBORObject} tree has been reassembled the
 * existing decode logic produces a typed Java value.  Top-level values that
 * may arrive: {@link dev.everydaythings.graph.id.HashID},
 * {@link dev.everydaythings.graph.datum.Body},
 * {@link dev.everydaythings.graph.datum.Record}, {@link String},
 * {@link Boolean}, {@link Number}, plus
 * {@link dev.everydaythings.graph.datum.Opaque} variants (Redacted,
 * Compressed, Encrypted) for tagged opaque wrappers.
 *
 * <h2>EOF vs malformed</h2>
 *
 * <p>The CBOR library throws on both "ran out of bytes mid-value" and "byte
 * doesn't match any valid CBOR shape." We distinguish by checking the
 * underlying stream's {@code available()} after the throw:
 *
 * <ul>
 *   <li>{@code available() == 0} — consumed every buffered byte trying to
 *       complete the value, so it's <b>incomplete</b>.  Rewind the buffer to
 *       the start of this value and wait for more bytes.</li>
 *   <li>{@code available() > 0} — bytes remained after the failure point, so
 *       the bytes were <b>malformed</b>.  Hand the exception to
 *       {@code onError} and discard the rest of the buffer.</li>
 * </ul>
 */
final class CgCborStreamParser implements StreamParser {

    private final Consumer<Object> onValue;
    private final Consumer<Throwable> onError;
    private final ByteArrayOutputStream buffer = new ByteArrayOutputStream();
    private boolean closed = false;

    CgCborStreamParser(Consumer<Object> onValue, Consumer<Throwable> onError) {
        this.onValue = Objects.requireNonNull(onValue, "onValue");
        this.onError = Objects.requireNonNull(onError, "onError");
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
     * dispatch each, then compact the buffer to retain any unconsumed bytes
     * for the next {@link #feed} call.
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
                // Malformed bytes — surface and bail; we have no robust
                // recovery point inside an arbitrary CBOR stream.
                onError.accept(e);
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
            Object value = CgCbor.fromCbor(obj);
            if (value == null) return;
            onValue.accept(value);
        } catch (Exception e) {
            onError.accept(e);
        }
    }
}
