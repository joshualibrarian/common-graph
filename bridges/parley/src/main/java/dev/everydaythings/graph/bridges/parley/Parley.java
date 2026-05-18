package dev.everydaythings.graph.bridges.parley;

import dev.everydaythings.graph.encoding.CgCbor;
import dev.everydaythings.graph.encoding.Encoding;
import dev.everydaythings.graph.id.ItemRef;
import dev.everydaythings.graph.network.tunnel.Tunnel;
import dev.everydaythings.graph.runtime.librarian.Librarian;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import lombok.extern.log4j.Log4j2;

import java.util.Set;
import java.util.concurrent.CompletableFuture;

/**
 * The Parley protocol — Common Graph's native peer conversation.  Composes
 * a {@link Tunnel} (byte channel), a {@link CodecHandshake} (codec
 * point-and-grunt), and a Netty inbound pipeline ({@link ParleyFramer} +
 * {@link ParleyDispatcher}) that delivers typed events into a
 * {@link Librarian}.
 *
 * <h2>Two phases</h2>
 * <ol>
 *   <li><b>Point-and-grunt</b> — {@link CodecHandshake}.  Each side sends
 *       raw {@code @<codec-iid>} bytes; the receiver confirms by echoing.
 *       Mismatch → counter-grunt with a different codec IID.</li>
 *   <li><b>Stream of anything</b> — once a codec is agreed, parties
 *       exchange a stream of self-describing values.  Inbound bytes flow
 *       through a Netty pipeline:
 *       {@code Tunnel → EmbeddedChannel → ParleyFramer → ParleyDispatcher}.
 *       Outbound values go directly through {@link RemoteConnection}'s
 *       encoder; no pipeline is needed for writes since the caller knows
 *       what's being sent.</li>
 * </ol>
 *
 * <h2>Why an EmbeddedChannel</h2>
 *
 * <p>The framer is a Netty {@code ByteToMessageDecoder} — the standard
 * pattern for "parse one self-delimited value at a time from a stream of
 * bytes."  Hosting it in an {@link EmbeddedChannel} lets us use that
 * pattern without owning a real Netty {@link io.netty.channel.Channel
 * Channel}; the Tunnel SPI is the byte source/sink.  Same shape as
 * {@link dev.everydaythings.graph.bridges.tls.TlsTunnel TlsTunnel} and the
 * primitive layer of {@link dev.everydaythings.graph.bridges.http.HttpExchange
 * HttpExchange}.
 *
 * <h2>Entry points</h2>
 * <ul>
 *   <li>{@link #connect(Tunnel, ItemRef, Set)} — initiate from this side</li>
 *   <li>{@link #accept(Tunnel, ItemRef, Set)} — respond to an inbound tunnel</li>
 * </ul>
 *
 * <p>Both return a {@link CompletableFuture} that completes once the codec
 * handshake succeeds and the Netty pipeline is wired; the value is a live
 * {@link RemoteConnection} the caller can send through.
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
     * wires the Netty inbound pipeline onto the tunnel and returns a
     * {@link RemoteConnection}.
     */
    public CompletableFuture<RemoteConnection> connect(
            Tunnel tunnel, ItemRef preferredCodec, Set<ItemRef> supportedCodecs) {
        return CodecHandshake.initiate(tunnel, preferredCodec, supportedCodecs)
                .thenApply(agreed -> attach(tunnel, agreed));
    }

    /**
     * Accept an inbound Parley conversation on the given tunnel.  Runs the
     * codec handshake as the responder; on success, wires the Netty inbound
     * pipeline onto the tunnel and returns a {@link RemoteConnection}.
     */
    public CompletableFuture<RemoteConnection> accept(
            Tunnel tunnel, ItemRef preferredCodec, Set<ItemRef> supportedCodecs) {
        return CodecHandshake.respond(tunnel, preferredCodec, supportedCodecs)
                .thenApply(agreed -> attach(tunnel, agreed));
    }

    /**
     * Post-handshake wiring: resolve the agreed codec, build a
     * {@link RemoteConnection}, construct the inbound EmbeddedChannel
     * pipeline, and register the tunnel's onReceive to feed it.
     */
    private RemoteConnection attach(Tunnel tunnel, ItemRef agreedCodec) {
        Encoding encoder = codecFor(agreedCodec);
        RemoteConnection conn = new RemoteConnection(tunnel, agreedCodec, encoder);

        EmbeddedChannel pipeline = new EmbeddedChannel(
                new ParleyFramer(encoder),
                new ParleyDispatcher(librarian, conn));

        // Single lock guards the embedded pipeline.  Inbound bytes arrive on
        // whatever thread the tunnel delivers them on; one tunnel → one
        // serialized pipeline.
        Object lock = new Object();
        tunnel.onReceive(bytes -> {
            synchronized (lock) {
                if (!pipeline.isOpen()) return;
                pipeline.writeInbound(Unpooled.wrappedBuffer(bytes));
            }
        });

        logger.debug("Parley attached: codec={}", agreedCodec);
        return conn;
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
