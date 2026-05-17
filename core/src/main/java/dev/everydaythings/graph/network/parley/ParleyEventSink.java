package dev.everydaythings.graph.network.parley;

import dev.everydaythings.graph.datum.Body;
import dev.everydaythings.graph.datum.Frame;
import dev.everydaythings.graph.datum.Record;
import dev.everydaythings.graph.encoding.EventSink;
import dev.everydaythings.graph.id.HashID;
import dev.everydaythings.graph.runtime.SubmitResult;
import dev.everydaythings.graph.runtime.librarian.Librarian;
import lombok.extern.log4j.Log4j2;

import java.util.Objects;

/**
 * Receive-side glue for a {@link Parley} conversation — implements
 * {@link EventSink}, routing each typed event from the encoder's streaming
 * parser into the {@link Librarian}.
 *
 * <p>One sink per {@link RemoteConnection}. The connection itself is the
 * "reply channel" — sink callbacks that want to respond to the peer (e.g.,
 * point-and-grunt {@code onRef} returning a fetched value) send through
 * the connection.
 *
 * <h2>Event routing</h2>
 *
 * <ul>
 *   <li>{@link #onBody} — wrap as {@link Frame#of(Body) Frame.of(body)} and
 *       submit to the Librarian. Response frames (if any) flow back to the
 *       peer over the connection.</li>
 *   <li>{@link #onRecord} — TODO. A Record references a Body by hash; if
 *       the body is locally known, build a Frame and submit. If not, fetch
 *       via point-and-grunt first.</li>
 *   <li>{@link #onRef} — TODO. Point-and-grunt "send me this": resolve the
 *       reference via the Librarian and send the result back over the
 *       connection.</li>
 *   <li>{@link #onText} — TODO. Token-dictionary lookup; respond with the
 *       resolved item reference.</li>
 *   <li>{@link #onNumber} / {@link #onBool} — TODO. Reserved for
 *       protocol-level signals; no handler today.</li>
 *   <li>{@link #onEncrypted} — TODO. Hand to the local decryption logic;
 *       re-feed cleartext through the parser.</li>
 *   <li>{@link #onMalformed} — log and close the connection. Malformed
 *       bytes mid-conversation are unrecoverable without framing.</li>
 * </ul>
 */
@Log4j2
final class ParleyEventSink implements EventSink {

    private final RemoteConnection connection;
    private final Librarian librarian;

    ParleyEventSink(RemoteConnection connection, Librarian librarian) {
        this.connection = Objects.requireNonNull(connection, "connection");
        this.librarian = Objects.requireNonNull(librarian, "librarian");
    }

    @Override
    public void onBody(Body body) {
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

    @Override
    public void onRecord(Record record) {
        // TODO: resolve the referenced body (locally if present, point-and-grunt
        // if not), then submit Frame(body, [record]).
        logger.debug("Parley onRecord (not yet dispatched): {}", record);
    }

    @Override
    public void onRef(HashID ref) {
        // TODO: point-and-grunt — librarian.fetch(ref) then connection.send(result).
        logger.debug("Parley onRef (not yet dispatched): {}", ref);
    }

    @Override
    public void onText(String text) {
        // TODO: token-dictionary lookup → connection.send(resolvedRef).
        logger.debug("Parley onText (not yet dispatched): {}", text);
    }

    @Override
    public void onNumber(Number number) {
        logger.debug("Parley onNumber (reserved): {}", number);
    }

    @Override
    public void onBool(boolean value) {
        logger.debug("Parley onBool (reserved): {}", value);
    }

    @Override
    public void onEncrypted(byte[] envelope) {
        // TODO: decrypt via local vault → re-feed cleartext through this sink's
        // parser. For now, just log and ignore.
        logger.debug("Parley onEncrypted (not yet dispatched): {} bytes", envelope.length);
    }

    @Override
    public void onMalformed(Throwable cause) {
        logger.warn("Parley parse failure; closing connection", cause);
        connection.close();
    }
}
