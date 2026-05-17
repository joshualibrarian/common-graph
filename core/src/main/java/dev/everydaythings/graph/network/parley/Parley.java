package dev.everydaythings.graph.network.parley;

import dev.everydaythings.graph.datum.Body;
import dev.everydaythings.graph.datum.Frame;
import dev.everydaythings.graph.datum.Opaque;
import dev.everydaythings.graph.datum.Record;
import dev.everydaythings.graph.encoding.CgCbor;
import dev.everydaythings.graph.encoding.Encoding;
import dev.everydaythings.graph.id.HashID;
import dev.everydaythings.graph.id.ItemRef;
import dev.everydaythings.graph.network.tunnel.Tunnel;
import dev.everydaythings.graph.runtime.SubmitResult;
import dev.everydaythings.graph.runtime.librarian.Librarian;
import lombok.extern.log4j.Log4j2;

import java.util.Set;
import java.util.concurrent.CompletableFuture;

/**
 * The Parley protocol — Common Graph's universal wire for "talking to other
 * parties." Composes a {@link Tunnel} (byte channel), a
 * {@link CodecHandshake} (codec point-and-grunt), and an encoder's streaming
 * parser into a {@link RemoteConnection} that delivers typed events into
 * a {@link Librarian}.
 *
 * <h2>Two phases</h2>
 * <ol>
 *   <li><b>Point-and-grunt</b> — codec handshake (see {@link CodecHandshake}).
 *       Each side sends raw {@code @<codec-iid>} bytes; the receiver confirms
 *       by echoing.  Mismatch → counter-grunt with a different codec IID.</li>
 *   <li><b>Stream of anything</b> — once a codec is agreed, parties exchange
 *       a stream of self-describing values.  Each whole top-level value lands
 *       at {@link #handleValue}; failures land at {@link #handleParseError}.
 *       The conventions for what each value type means are Parley's, not the
 *       codec's: a top-level {@link Body} becomes a frame submission, a top-
 *       level {@link HashID} is a fetch request, a top-level {@link String}
 *       is a token-dictionary lookup, a top-level {@link Opaque} (Redacted /
 *       Compressed / Encrypted) is handed to the relevant local handler.</li>
 * </ol>
 *
 * <h2>Entry points</h2>
 * <ul>
 *   <li>{@link #connect(Tunnel, ItemRef, Set)} — initiate from this side</li>
 *   <li>{@link #accept(Tunnel, ItemRef, Set)} — respond to an inbound tunnel</li>
 * </ul>
 *
 * <p>Both return a {@link CompletableFuture} that completes once the codec
 * handshake succeeds and the streaming parser is wired onto the tunnel; the
 * value is a live {@link RemoteConnection} the caller can send through.
 */
@Log4j2
public class Parley {

    private final Librarian librarian;

    public Parley(Librarian librarian) {
        this.librarian = librarian;
    }

    public Librarian librarian() {
        return librarian;
    }

    /**
     * Initiate a Parley conversation on the given tunnel.  Runs the codec
     * handshake as the initiator with {@code preferredCodec}; on success,
     * wires the encoder's streaming parser onto the tunnel and returns a
     * {@link RemoteConnection}.
     */
    public CompletableFuture<RemoteConnection> connect(
            Tunnel tunnel, ItemRef preferredCodec, Set<ItemRef> supportedCodecs) {
        return CodecHandshake.initiate(tunnel, preferredCodec, supportedCodecs)
                .thenApply(agreed -> attach(tunnel, agreed));
    }

    /**
     * Accept an inbound Parley conversation on the given tunnel.  Runs the
     * codec handshake as the responder; on success, wires the streaming
     * parser onto the tunnel and returns a {@link RemoteConnection}.
     */
    public CompletableFuture<RemoteConnection> accept(
            Tunnel tunnel, ItemRef preferredCodec, Set<ItemRef> supportedCodecs) {
        return CodecHandshake.respond(tunnel, preferredCodec, supportedCodecs)
                .thenApply(agreed -> attach(tunnel, agreed));
    }

    /**
     * Post-handshake wiring: resolve the agreed codec's encoder, build a
     * {@link RemoteConnection}, register the encoder's streaming parser as
     * the tunnel's receive consumer, and return the connection.
     */
    private RemoteConnection attach(Tunnel tunnel, ItemRef agreedCodec) {
        Encoding encoder = codecFor(agreedCodec);
        RemoteConnection conn = new RemoteConnection(tunnel, agreedCodec, encoder);
        tunnel.onReceive(encoder.parseStream(
                value -> handleValue(conn, value),
                error -> handleParseError(conn, error)));
        logger.debug("Parley attached: codec={}", agreedCodec);
        return conn;
    }

    /**
     * Dispatch a single top-level value parsed off the wire.  Each value
     * type carries a Parley-level convention; this method is the one place
     * those conventions are spelled out.
     */
    private void handleValue(RemoteConnection conn, Object value) {
        switch (value) {
            case Body body      -> submitFrame(conn, body);
            case Record record  -> handleRecord(conn, record);
            case HashID ref     -> handleFetchRequest(conn, ref);
            case String text    -> handleTokenLookup(conn, text);
            case Opaque op      -> handleOpaque(conn, op);
            case Boolean b      -> logger.debug("Parley onBool (reserved): {}", b);
            case Number n       -> logger.debug("Parley onNumber (reserved): {}", n);
            default             -> logger.warn("Parley unexpected top-level value: {}",
                    value == null ? "null" : value.getClass().getName());
        }
    }

    private void submitFrame(RemoteConnection conn, Body body) {
        logger.debug("Parley onBody: head={}", body.headRef());
        try {
            SubmitResult result = librarian.submit(Frame.of(body));
            for (Frame response : result.responses()) {
                conn.send(response.body());
            }
        } catch (Exception e) {
            logger.warn("Submit failed for body {}: {}", body.headRef(), e.toString());
        }
    }

    private void handleRecord(RemoteConnection conn, Record record) {
        // TODO: resolve the referenced body (locally if present, point-and-grunt
        // if not), then submit Frame(body, [record]).
        logger.debug("Parley onRecord (not yet dispatched): {}", record);
    }

    private void handleFetchRequest(RemoteConnection conn, HashID ref) {
        // TODO: point-and-grunt — librarian.fetch(ref) then conn.send(result).
        logger.debug("Parley onRef (not yet dispatched): {}", ref);
    }

    private void handleTokenLookup(RemoteConnection conn, String text) {
        // TODO: token-dictionary lookup → conn.send(resolvedRef).
        logger.debug("Parley onText (not yet dispatched): {}", text);
    }

    private void handleOpaque(RemoteConnection conn, Opaque opaque) {
        // TODO: dispatch by variant.  Encrypted → decrypt via local vault and
        // re-feed cleartext through this connection's parser.  Compressed →
        // decompress and reinject.  Redacted at top-of-stream is unusual
        // (just a hash); save the recordRefs for later.  For now, just log
        // and ignore.
        logger.debug("Parley Opaque (not yet dispatched): {}", opaque);
    }

    private void handleParseError(RemoteConnection conn, Throwable error) {
        logger.warn("Parley parse failure; closing connection", error);
        conn.close();
    }

    /**
     * Resolve a codec IID to its Java {@link Encoding} implementation.  Only
     * CG-CBOR-v1 is wired today; other codecs throw.  A real codec registry
     * (probably driven by IMPLEMENTS frames on the encoding sememes) lands
     * when there's a second codec to ground it.
     */
    private static Encoding codecFor(ItemRef codecIid) {
        if (codecIid.equals(ItemRef.iid(Encoding.CgCborV1.KEY))) {
            return CgCbor.codec();
        }
        throw new IllegalStateException(
                "No codec implementation registered for " + codecIid);
    }
}
