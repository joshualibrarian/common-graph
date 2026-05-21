package dev.everydaythings.graph.bridges.unix;

import dev.everydaythings.graph.network.NetworkVocabulary;
import dev.everydaythings.graph.network.transport.Transport;
import dev.everydaythings.graph.network.transport.TransportRegistry;
import dev.everydaythings.graph.ref.ItemRef;
import dev.everydaythings.graph.value.UnixEndpoint;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies that {@link TransportRegistry} picks up the
 * {@link UnixSocketTransport} via its
 * {@code META-INF/services/dev.everydaythings.graph.network.transport.Transport}
 * declaration.
 */
class TransportRegistryTest {

    @Test
    @DisplayName("registry resolves UnixSocketTransport for a UnixEndpoint")
    void resolvesUnixTransport() {
        UnixEndpoint endpoint = UnixEndpoint.of("/tmp/test.sock");
        Transport transport = TransportRegistry.require(endpoint);
        assertThat(transport).isInstanceOf(UnixSocketTransport.class);
        assertThat(transport.transport()).isEqualTo(ItemRef.iid(NetworkVocabulary.Unix.KEY));
    }

    @Test
    @DisplayName("registry's all() contains UnixSocketTransport")
    void allIncludesUnix() {
        assertThat(TransportRegistry.all())
                .anyMatch(t -> t instanceof UnixSocketTransport);
    }
}
