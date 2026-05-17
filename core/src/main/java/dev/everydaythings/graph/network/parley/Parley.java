package dev.everydaythings.graph.network.parley;

import dev.everydaythings.graph.encoding.CgCbor;
import dev.everydaythings.graph.encoding.Encoding;
import dev.everydaythings.graph.id.ItemRef;
import dev.everydaythings.graph.network.tunnel.Tunnel;
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
 *       by echoing. Mismatch → counter-grunt with a different codec IID.</li>
 *   <li><b>Stream of anything</b> — once a codec is agreed, parties exchange
 *       a stream of self-describing values (Bodies, Records, references, text
 *       lookups, encrypted envelopes). The encoder fires typed callbacks on
 *       an {@link dev.everydaythings.graph.encoding.EventSink EventSink}
 *       (this class's {@link ParleyEventSink}); the sink routes each event
 *       into the Librarian.</li>
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
     * Initiate a Parley conversation on the given tunnel. Runs the codec
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
     * Accept an inbound Parley conversation on the given tunnel. Runs the
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
        ParleyEventSink sink = new ParleyEventSink(conn, librarian);
        tunnel.onReceive(encoder.parseStream(sink));
        logger.debug("Parley attached: codec={}", agreedCodec);
        return conn;
    }

    /**
     * Resolve a codec IID to its Java {@link Encoding} implementation. Only
     * CG-CBOR-v1 is wired today; other codecs throw. A real codec registry
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
