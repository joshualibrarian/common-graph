package dev.everydaythings.graph.network.parley;

import dev.everydaythings.graph.id.HashID;
import dev.everydaythings.graph.id.ItemRef;
import lombok.extern.log4j.Log4j2;

import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Parley phase 1 — the codec point-and-grunt handshake.
 *
 * <p>Establishes which encoding (CG-CBOR, a hypothetical CG-JSON, …) two
 * Parley parties will use for application traffic on this tunnel. Runs over
 * the bare tunnel before any framed application data flows.
 *
 * <h2>Protocol</h2>
 *
 * <p>The wire shape is dead simple: each side writes raw {@code @<codec-iid>}
 * bytes (the same byte layout an {@code ItemRef} carries — prefix byte
 * {@code 0x40} followed by the codec's multihash). No CBOR wrapping, no
 * length prefix, no framing. The reference protocol's byte layout (see
 * {@code ref-scheme.md}) is the only shared vocabulary the handshake assumes.
 *
 * <p>Roles:
 * <ul>
 *   <li><b>Initiator</b> sends its preferred codec-id immediately, then waits.</li>
 *   <li><b>Responder</b> waits for the initiator's proposal, then either
 *       echoes it back (acceptance) or counters with its own preferred
 *       codec-id (a "counter-grunt").</li>
 * </ul>
 *
 * <p>Each side completes its future with the agreed codec when it sees the
 * codec-id it just sent come back from the counterparty. The "echo as
 * acknowledgment" is what lets the protocol be symmetric below the level of
 * application data — no separate ACK message, no protocol-state byte.
 *
 * <p>Sequence diagrams for the common cases:
 *
 * <pre>
 * Case 1: initiator's proposal accepted (1 round)
 *
 *   Initiator                          Responder
 *   ---------                          ---------
 *   send @X        ─────────────►
 *                                       (X is supported)
 *                  ◄─────────────       send @X
 *   (received @X, match what I sent
 *    → AGREED on X)
 *                                       (received @X, match what I sent
 *                                        → AGREED on X)
 *
 * Case 2: counter-proposal accepted (2 rounds)
 *
 *   Initiator                          Responder
 *   ---------                          ---------
 *   send @X        ─────────────►
 *                                       (X is not supported)
 *                  ◄─────────────       send @Y
 *   (received @Y, different from
 *    what I sent; I support Y)
 *   send @Y        ─────────────►
 *   (AGREED on Y)
 *                                       (received @Y, match what I sent
 *                                        → AGREED on Y)
 * </pre>
 *
 * <h2>Failure modes</h2>
 *
 * <ul>
 *   <li><b>No common codec.</b> Both sides counter-grunt with codecs the other
 *       doesn't support; convergence fails. The future completes
 *       exceptionally and the tunnel should be closed.</li>
 *   <li><b>Garbage bytes.</b> The first bytes don't start with the
 *       {@code 0x40} prefix. Indicates a misbehaving peer or a transport-layer
 *       error; the future completes exceptionally.</li>
 *   <li><b>Malformed multihash.</b> The bytes start with {@code 0x40} but
 *       don't parse as a valid ItemRef. Same outcome.</li>
 * </ul>
 *
 * <h2>What this layer doesn't do</h2>
 *
 * <ul>
 *   <li><b>No framing.</b> Each {@code send} call delivers one codec-id.
 *       This requires the underlying tunnel to preserve message boundaries
 *       (LoopbackTunnel does; real transports will need a framing layer or
 *       a guarantee about chunk size).</li>
 *   <li><b>No tunnel ownership.</b> The handshake registers a receive
 *       consumer for its duration. After the future completes, the caller is
 *       responsible for re-registering its own consumer to receive
 *       application bytes.</li>
 *   <li><b>No HELLO.</b> HELLO is a Parley application-layer concept (see
 *       {@link ParleyVocabulary.Hello}); the handshake only establishes
 *       which codec the HELLO will be encoded in.</li>
 * </ul>
 */
@Log4j2
public final class CodecHandshake {

    private CodecHandshake() {}

    private static final byte CODEC_REF_PREFIX = HashID.PREFIX_ITEM;

    // ==================================================================================
    // Public API
    // ==================================================================================

    /**
     * Run the handshake as the initiator. Sends {@code preferredCodec} immediately,
     * then waits for the counterparty's response. Completes when agreement is
     * reached (either the counterparty accepts our proposal, or counters with
     * a codec we also support).
     */
    public static CompletableFuture<ItemRef> initiate(
            Tunnel tunnel,
            ItemRef preferredCodec,
            Set<ItemRef> supportedCodecs) {
        validateInputs(tunnel, preferredCodec, supportedCodecs);
        CompletableFuture<ItemRef> result = new CompletableFuture<>();
        State state = new State(tunnel, preferredCodec, supportedCodecs, result);
        tunnel.onReceive(state::onBytes);
        state.proposeOurCodec();
        return result;
    }

    /**
     * Run the handshake as the responder. Waits for the initiator's proposal,
     * then either accepts (echoes the proposed codec back) or counter-proposes.
     * Completes when agreement is reached.
     */
    public static CompletableFuture<ItemRef> respond(
            Tunnel tunnel,
            ItemRef preferredCodec,
            Set<ItemRef> supportedCodecs) {
        validateInputs(tunnel, preferredCodec, supportedCodecs);
        CompletableFuture<ItemRef> result = new CompletableFuture<>();
        State state = new State(tunnel, preferredCodec, supportedCodecs, result);
        tunnel.onReceive(state::onBytes);
        // Don't send anything yet — wait for the initiator's first codec-id.
        return result;
    }

    private static void validateInputs(Tunnel tunnel, ItemRef preferredCodec, Set<ItemRef> supportedCodecs) {
        Objects.requireNonNull(tunnel, "tunnel");
        Objects.requireNonNull(preferredCodec, "preferredCodec");
        Objects.requireNonNull(supportedCodecs, "supportedCodecs");
        if (!supportedCodecs.contains(preferredCodec)) {
            throw new IllegalArgumentException(
                    "preferredCodec must be in supportedCodecs (got " + preferredCodec + ")");
        }
        if (!tunnel.isOpen()) {
            throw new IllegalStateException("tunnel is not open");
        }
    }

    // ==================================================================================
    // State machine
    // ==================================================================================

    /**
     * Per-handshake state. Tracks what we've sent so we can detect when the
     * counterparty echoes it back (the agreement signal).
     */
    private static final class State {
        private final Tunnel tunnel;
        private final ItemRef preferredCodec;
        private final Set<ItemRef> supportedCodecs;
        private final CompletableFuture<ItemRef> result;
        private final AtomicReference<ItemRef> sentCodec = new AtomicReference<>();

        State(Tunnel tunnel, ItemRef preferredCodec, Set<ItemRef> supportedCodecs,
              CompletableFuture<ItemRef> result) {
            this.tunnel = tunnel;
            this.preferredCodec = preferredCodec;
            this.supportedCodecs = supportedCodecs;
            this.result = result;
        }

        /** Initiator: send our preferred codec-id to kick things off. */
        void proposeOurCodec() {
            sendCodecId(preferredCodec);
        }

        void onBytes(byte[] bytes) {
            if (result.isDone()) return;  // already settled; ignore stray bytes
            ItemRef received;
            try {
                received = parseCodecId(bytes);
            } catch (Exception e) {
                failAndClose(e);
                return;
            }

            ItemRef weSent = sentCodec.get();

            // Echo of what we sent → agreement
            if (weSent != null && weSent.equals(received)) {
                logger.debug("Codec handshake agreed: {}", received);
                result.complete(received);
                return;
            }

            // Counterparty proposed (or counter-proposed). Do we support it?
            if (supportedCodecs.contains(received)) {
                // Yes — echo back as acknowledgment, and we're agreed.
                sendCodecId(received);
                logger.debug("Codec handshake agreed (accepted peer's codec): {}", received);
                result.complete(received);
                return;
            }

            // We don't support what they sent.
            if (weSent != null) {
                // We already proposed something; they came back with something we don't
                // support. Convergence has failed. Settle our result FIRST so the
                // peer's synchronous reply (their own rejection re-grunt) finds us
                // already-done and returns early. Then re-send our preferred as the
                // rejection signal, then drop the tunnel.
                IllegalStateException error = new IllegalStateException(
                        "No common codec: peer proposed " + received
                                + " (after we proposed " + weSent + ")");
                result.completeExceptionally(error);
                sendCodecId(preferredCodec);  // rejection signal — peer's no-common-codec branch
                tunnel.close();
                return;
            }

            // Responder branch: their proposal didn't match ours; counter with our preferred.
            sendCodecId(preferredCodec);
        }

        private void failAndClose(Throwable error) {
            result.completeExceptionally(error);
            tunnel.close();
        }

        private void sendCodecId(ItemRef codec) {
            sentCodec.set(codec);
            tunnel.send(codec.toRefBytes());
        }
    }

    // ==================================================================================
    // Parsing
    // ==================================================================================

    private static ItemRef parseCodecId(byte[] bytes) {
        if (bytes == null || bytes.length < 1) {
            throw new IllegalArgumentException("Empty handshake payload");
        }
        if (bytes[0] != CODEC_REF_PREFIX) {
            throw new IllegalArgumentException(
                    "Expected codec-id (prefix '@' / 0x40); got first byte 0x"
                            + String.format("%02X", bytes[0]));
        }
        HashID parsed = HashID.fromRefBytes(bytes);
        if (!(parsed instanceof ItemRef itemRef)) {
            throw new IllegalArgumentException(
                    "Codec-id must be an ItemRef; got " + parsed.getClass().getSimpleName());
        }
        return itemRef;
    }
}
