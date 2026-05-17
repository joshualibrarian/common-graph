package dev.everydaythings.graph.network.parley;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LoopbackTunnelTest {

    @Nested
    @DisplayName("Pair construction")
    class PairConstruction {

        @Test
        @DisplayName("pair() produces two open, distinct tunnels")
        void pairProducesTwo() {
            LoopbackTunnel.Pair p = LoopbackTunnel.pair();
            assertThat(p.a()).isNotNull();
            assertThat(p.b()).isNotNull();
            assertThat(p.a()).isNotSameAs(p.b());
            assertThat(p.a().isOpen()).isTrue();
            assertThat(p.b().isOpen()).isTrue();
        }

        @Test
        @DisplayName("each endpoint advertises no confidentiality, no auth, no counterparty")
        void noSecurityProperties() {
            LoopbackTunnel.Pair p = LoopbackTunnel.pair();
            assertThat(p.a().isConfidential()).isFalse();
            assertThat(p.a().isAuthenticated()).isFalse();
            assertThat(p.a().counterparty()).isEmpty();
            assertThat(p.b().isConfidential()).isFalse();
            assertThat(p.b().isAuthenticated()).isFalse();
            assertThat(p.b().counterparty()).isEmpty();
        }
    }

    @Nested
    @DisplayName("Byte delivery")
    class ByteDelivery {

        @Test
        @DisplayName("bytes sent from one end arrive at the other end's receiver")
        void oneWayDelivery() {
            LoopbackTunnel.Pair p = LoopbackTunnel.pair();
            AtomicReference<byte[]> received = new AtomicReference<>();
            p.b().onReceive(received::set);

            p.a().send(new byte[]{1, 2, 3, 4});

            assertThat(received.get()).containsExactly(1, 2, 3, 4);
        }

        @Test
        @DisplayName("bytes flow in both directions independently")
        void bidirectional() {
            LoopbackTunnel.Pair p = LoopbackTunnel.pair();
            AtomicReference<byte[]> aReceived = new AtomicReference<>();
            AtomicReference<byte[]> bReceived = new AtomicReference<>();
            p.a().onReceive(aReceived::set);
            p.b().onReceive(bReceived::set);

            p.a().send(new byte[]{10});
            p.b().send(new byte[]{20});

            assertThat(bReceived.get()).containsExactly(10);
            assertThat(aReceived.get()).containsExactly(20);
        }

        @Test
        @DisplayName("send returns a completed future")
        void sendReturnsCompletedFuture() {
            LoopbackTunnel.Pair p = LoopbackTunnel.pair();
            p.b().onReceive(bs -> {});

            var future = p.a().send(new byte[]{1});

            assertThat(future).isCompleted();
        }

        @Test
        @DisplayName("each send produces one onReceive invocation; no chunking")
        void oneSendOneReceive() {
            LoopbackTunnel.Pair p = LoopbackTunnel.pair();
            List<byte[]> received = new ArrayList<>();
            p.b().onReceive(received::add);

            p.a().send(new byte[]{1});
            p.a().send(new byte[]{2, 3});
            p.a().send(new byte[]{4, 5, 6});

            assertThat(received).hasSize(3);
            assertThat(received.get(0)).containsExactly(1);
            assertThat(received.get(1)).containsExactly(2, 3);
            assertThat(received.get(2)).containsExactly(4, 5, 6);
        }

        @Test
        @DisplayName("bytes sent before a receiver is registered are dropped")
        void droppedWithoutReceiver() {
            LoopbackTunnel.Pair p = LoopbackTunnel.pair();
            // No receiver on p.b()
            p.a().send(new byte[]{1, 2, 3});

            // Now register a receiver. The earlier bytes were dropped.
            AtomicReference<byte[]> received = new AtomicReference<>();
            p.b().onReceive(received::set);

            assertThat(received.get()).isNull();
        }

        @Test
        @DisplayName("re-registering a receiver replaces the prior one")
        void receiverReplacement() {
            LoopbackTunnel.Pair p = LoopbackTunnel.pair();
            AtomicReference<byte[]> firstReceiver = new AtomicReference<>();
            AtomicReference<byte[]> secondReceiver = new AtomicReference<>();

            p.b().onReceive(firstReceiver::set);
            p.b().onReceive(secondReceiver::set);

            p.a().send(new byte[]{7});

            assertThat(firstReceiver.get()).isNull();
            assertThat(secondReceiver.get()).containsExactly(7);
        }
    }

    @Nested
    @DisplayName("Lifecycle")
    class Lifecycle {

        @Test
        @DisplayName("closing one endpoint closes the other")
        void cascadeClose() {
            LoopbackTunnel.Pair p = LoopbackTunnel.pair();
            assertThat(p.a().isOpen()).isTrue();
            assertThat(p.b().isOpen()).isTrue();

            p.a().close();

            assertThat(p.a().isOpen()).isFalse();
            assertThat(p.b().isOpen()).isFalse();
        }

        @Test
        @DisplayName("close is idempotent")
        void closeIdempotent() {
            LoopbackTunnel.Pair p = LoopbackTunnel.pair();
            p.a().close();
            p.a().close();  // should not throw
            assertThat(p.a().isOpen()).isFalse();
        }

        @Test
        @DisplayName("send after close throws")
        void sendAfterCloseThrows() {
            LoopbackTunnel.Pair p = LoopbackTunnel.pair();
            p.a().close();

            assertThatThrownBy(() -> p.a().send(new byte[]{1}))
                    .isInstanceOf(IllegalStateException.class);
        }
    }
}
