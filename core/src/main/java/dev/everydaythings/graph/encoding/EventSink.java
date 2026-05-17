package dev.everydaythings.graph.encoding;

import dev.everydaythings.graph.datum.Body;
import dev.everydaythings.graph.datum.Record;
import dev.everydaythings.graph.id.HashID;

/**
 * Codec-agnostic sink for typed top-level values parsed out of an encoded
 * stream.
 *
 * <p>An {@link Encoding} codec consumes bytes incrementally (see
 * {@link Encoding#parseStream}) and fires the appropriate callback below
 * whenever a whole top-level value lands. The sink is the boundary between
 * encoding-specific parsing and codec-agnostic protocol logic: Parley and
 * other protocol layers consume typed events without caring which codec
 * produced them.
 *
 * <p>Each callback corresponds to one of the protocol-level primitives we
 * agreed could appear at the top of a stream:
 *
 * <ul>
 *   <li><b>{@link #onRef(HashID)}</b> — a reference of any prefix variant
 *       ({@code @}, {@code ?}, {@code !}, {@code ~}, {@code #}). The
 *       point-and-grunt convention: a bare reference at the top of a stream
 *       means "send me this."</li>
 *   <li><b>{@link #onText(String)}</b> — a text string. The convention: a
 *       bare top-level string means "look this up in the token dictionary"
 *       (the lightweight equivalent of a LOOKUP frame).</li>
 *   <li><b>{@link #onNumber(Number)}</b> — a numeric value
 *       ({@link Long} / {@link java.math.BigInteger} /
 *       {@link dev.everydaythings.graph.value.Decimal} /
 *       {@link dev.everydaythings.graph.value.Rational}). Reserved for
 *       protocol-level numerics (status codes, sequence numbers, ...).</li>
 *   <li><b>{@link #onBool(boolean)}</b> — a boolean. Reserved for keepalive /
 *       ack-style signals.</li>
 *   <li><b>{@link #onBody(Body)}</b> — an unsigned datum (head + bindings),
 *       typically a frame to be dispatched.</li>
 *   <li><b>{@link #onRecord(Record)}</b> — a signed datum (external
 *       attestation envelope).</li>
 *   <li><b>{@link #onEncrypted(byte[])}</b> — an encrypted envelope. Opaque
 *       to the protocol; the recipient attempts to decrypt and may re-feed
 *       the cleartext bytes into the same parser.</li>
 *   <li><b>{@link #onMalformed(Throwable)}</b> — a codec-level parse failure.
 *       The caller decides whether to close the tunnel.</li>
 * </ul>
 *
 * <p>All callbacks default to no-op, so implementors override only the cases
 * they handle.
 */
public interface EventSink {

    /** A reference at the top of the stream (any prefix variant). */
    default void onRef(HashID ref) {}

    /** A bare text string at the top of the stream. */
    default void onText(String text) {}

    /** A bare numeric value at the top of the stream. */
    default void onNumber(Number number) {}

    /** A bare boolean at the top of the stream. */
    default void onBool(boolean value) {}

    /** An unsigned datum body. */
    default void onBody(Body body) {}

    /** A signed datum record. */
    default void onRecord(Record record) {}

    /**
     * An encrypted envelope. Bytes are codec-defined; the recipient inspects
     * the envelope's structure to attempt decryption.
     */
    default void onEncrypted(byte[] envelope) {}

    /**
     * A codec-level parse failure. The stream may still be salvageable
     * depending on the codec; the protocol layer decides whether to continue
     * or to close the underlying tunnel.
     */
    default void onMalformed(Throwable cause) {}
}
