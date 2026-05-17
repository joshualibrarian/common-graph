package dev.everydaythings.graph.network.parley;

import dev.everydaythings.graph.encoding.Encoding;
import dev.everydaythings.graph.id.ItemRef;
import dev.everydaythings.graph.network.tunnel.LoopbackTunnel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CodecHandshakeTest {

    // CG-CBOR is the well-known default codec.
    private static final ItemRef CG_CBOR = ItemRef.iid(Encoding.CgCborV1.KEY);

    // Two synthetic codec IIDs used for negotiation tests. They don't correspond
    // to real encoder implementations; the handshake doesn't care.
    private static final ItemRef CODEC_A = ItemRef.fromString("test.codec:a");
    private static final ItemRef CODEC_B = ItemRef.fromString("test.codec:b");
    private static final ItemRef CODEC_C = ItemRef.fromString("test.codec:c");

    @Nested
    @DisplayName("Happy path: both sides agree")
    class HappyPath {

        @Test
        @DisplayName("both sides prefer CG-CBOR — agreement on initiator's proposal")
        void bothPreferCgCbor() throws Exception {
            LoopbackTunnel.Pair p = LoopbackTunnel.pair();

            CompletableFuture<ItemRef> responder =
                    CodecHandshake.respond(p.b(), CG_CBOR, Set.of(CG_CBOR));
            CompletableFuture<ItemRef> initiator =
                    CodecHandshake.initiate(p.a(), CG_CBOR, Set.of(CG_CBOR));

            assertThat(initiator.get()).isEqualTo(CG_CBOR);
            assertThat(responder.get()).isEqualTo(CG_CBOR);
        }

        @Test
        @DisplayName("initiator's preferred is among responder's supported — agreement on initiator's")
        void initiatorPreferredAccepted() throws Exception {
            LoopbackTunnel.Pair p = LoopbackTunnel.pair();

            CompletableFuture<ItemRef> responder =
                    CodecHandshake.respond(p.b(), CODEC_B, Set.of(CODEC_A, CODEC_B));
            CompletableFuture<ItemRef> initiator =
                    CodecHandshake.initiate(p.a(), CODEC_A, Set.of(CODEC_A));

            // Initiator proposed A; responder supports A; they agree on A.
            assertThat(initiator.get()).isEqualTo(CODEC_A);
            assertThat(responder.get()).isEqualTo(CODEC_A);
        }
    }

    @Nested
    @DisplayName("Counter-proposal: responder counters, initiator accepts")
    class CounterProposal {

        @Test
        @DisplayName("initiator's proposal rejected, responder counters with a codec initiator also supports")
        void counterAccepted() throws Exception {
            LoopbackTunnel.Pair p = LoopbackTunnel.pair();

            // Responder prefers B, only supports B.
            CompletableFuture<ItemRef> responder =
                    CodecHandshake.respond(p.b(), CODEC_B, Set.of(CODEC_B));
            // Initiator prefers A but also supports B.
            CompletableFuture<ItemRef> initiator =
                    CodecHandshake.initiate(p.a(), CODEC_A, Set.of(CODEC_A, CODEC_B));

            // Initiator proposes A; responder counter-proposes B; initiator accepts B.
            assertThat(initiator.get()).isEqualTo(CODEC_B);
            assertThat(responder.get()).isEqualTo(CODEC_B);
        }
    }

    @Nested
    @DisplayName("Failure: no common codec")
    class NoCommonCodec {

        @Test
        @DisplayName("initiator proposes a codec responder doesn't support; responder's counter not supported by initiator")
        void noOverlap() {
            LoopbackTunnel.Pair p = LoopbackTunnel.pair();

            // Responder prefers B, only supports B.
            CompletableFuture<ItemRef> responder =
                    CodecHandshake.respond(p.b(), CODEC_B, Set.of(CODEC_B));
            // Initiator prefers A, only supports A.
            CompletableFuture<ItemRef> initiator =
                    CodecHandshake.initiate(p.a(), CODEC_A, Set.of(CODEC_A));

            // Both futures should complete exceptionally.
            assertThatThrownBy(initiator::get)
                    .isInstanceOf(ExecutionException.class)
                    .hasMessageContaining("No common codec");
            assertThatThrownBy(responder::get)
                    .isInstanceOf(ExecutionException.class)
                    .hasMessageContaining("No common codec");
        }
    }

    @Nested
    @DisplayName("Input validation")
    class InputValidation {

        @Test
        @DisplayName("preferredCodec must be in supportedCodecs")
        void preferredMustBeSupported() {
            LoopbackTunnel.Pair p = LoopbackTunnel.pair();
            assertThatThrownBy(() ->
                    CodecHandshake.initiate(p.a(), CODEC_A, Set.of(CODEC_B)))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("preferredCodec");
        }

        @Test
        @DisplayName("tunnel must be open")
        void tunnelMustBeOpen() {
            LoopbackTunnel.Pair p = LoopbackTunnel.pair();
            p.a().close();
            assertThatThrownBy(() ->
                    CodecHandshake.initiate(p.a(), CG_CBOR, Set.of(CG_CBOR)))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("not open");
        }
    }

    @Nested
    @DisplayName("Malformed bytes")
    class MalformedBytes {

        @Test
        @DisplayName("receives bytes that don't start with @ prefix — handshake fails")
        void garbageFirstByte() {
            LoopbackTunnel.Pair p = LoopbackTunnel.pair();

            CompletableFuture<ItemRef> responder =
                    CodecHandshake.respond(p.b(), CG_CBOR, Set.of(CG_CBOR));

            // Send garbage bytes from a-side directly (bypassing the handshake).
            p.a().send(new byte[]{0x00, 0x01, 0x02});

            assertThatThrownBy(responder::get)
                    .isInstanceOf(ExecutionException.class)
                    .hasMessageContaining("prefix");
        }
    }
}
