package dev.everydaythings.graph.network.parley;

import dev.everydaythings.graph.runtime.Librarian;
import lombok.extern.log4j.Log4j2;

/**
 * The Parley protocol — Common Graph's universal wire for "talking to other
 * parties." Renamed from {@code PeerProtocol}; subsumes the former
 * peer/session split.
 *
 * <h2>Two phases, that's it</h2>
 * <ol>
 *   <li><b>Point-and-grunt</b> — codec handshake. Each side sends raw
 *       {@code @<codec-iid>} bytes; the receiver confirms by responding with a
 *       HELLO Datum in that codec. Mismatch → counter-grunt with a different
 *       codec IID. Zero presumed shared vocabulary beyond the reference-prefix
 *       primitives ({@code @}, {@code ~}, {@code #}, {@code ?}, {@code \}).</li>
 *   <li><b>Stream of anything</b> — once a codec is agreed, parties exchange a
 *       stream of self-describing bytes: Bodies, Records, content blobs,
 *       encrypted blobs. The codec tells the receiver what each chunk is.
 *       Parley itself doesn't distinguish — it's all just data.</li>
 * </ol>
 *
 * <p><b>The only protocols are social.</b> After codec handshake there's no
 * connection state machine, no request/response framework, no special-case
 * auth messages. Auth, subscription, sync, presence, capability negotiation —
 * all just frames the counterparty either understands and trusts, or doesn't.
 * The control plane collapses into the data plane.
 *
 * <h2>Peer-as-spectrum</h2>
 * Everyone connected via Parley is a "peer," weighted differently:
 * <ul>
 *   <li>A {@link Session} peer (UI/client) — ephemeral, single-identity,
 *       attached workspace. Low trust.</li>
 *   <li>A full Librarian peer — durable, multi-identity, sync-capable.</li>
 * </ul>
 * The discriminator from a Librarian's POV: <i>does this connection have a
 * Session attached?</i> If yes → client traffic. If no → peer-sync (or
 * anonymous probe).
 *
 * <h2>Transports</h2>
 * Orthogonal to peer-weight:
 * <ul>
 *   <li>{@link LocalConnection} — same VM, direct method calls, no
 *       serialization, no {@link Tunnel}.</li>
 *   <li>{@link RemoteConnection} — bytes across a {@link Tunnel} (Noise or
 *       TLS).</li>
 * </ul>
 *
 * <p>STUB — structure only, no behavior yet. The codec handshake state
 * machine, the connection registry, the receive-dispatch path are all TBD.
 * See {@code memory/design-parley-protocol.md} for the locked design.
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

    // TODO: open(target) → CompletableFuture<Connection>
    // TODO: accept(incoming-tunnel) → Connection (after handshake completes)
    // TODO: codec negotiation state machine (point-and-grunt)
    // TODO: connection registry (active connections, by counterparty IID)
    // TODO: dispatch loop — incoming Datum → predicate → @Handler on Librarian
}
