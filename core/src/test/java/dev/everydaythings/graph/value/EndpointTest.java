package dev.everydaythings.graph.value;

import dev.everydaythings.graph.datum.Binding;
import dev.everydaythings.graph.datum.Body;
import dev.everydaythings.graph.encoding.CgCbor;
import dev.everydaythings.graph.ref.DatumRef;
import dev.everydaythings.graph.ref.ItemRef;
import dev.everydaythings.graph.item.Manifest;
import dev.everydaythings.graph.network.NetworkVocabulary;
import dev.everydaythings.graph.runtime.librarian.Librarian;
import dev.everydaythings.graph.runtime.stage.ItemStage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Endpoint family — abstract parent + per-transport subarchetypes.  Covers
 * the subclass shape, the polymorphic {@link Endpoint#from(Body)} dispatcher,
 * the {@link Endpoint#transport()} accessor, and the manifest-level
 * {@code Addresses → @transport} binding each subarchetype declares.
 */
class EndpointTest {

    @Nested
    @DisplayName("Subclass construction and round-trip")
    class Roundtrip {

        @Test
        @DisplayName("TcpEndpoint carries @Host + @Port and round-trips through CBOR")
        void tcpRoundtrip() {
            TcpEndpoint endpoint = TcpEndpoint.of("127.0.0.1", 8080);
            assertThat(endpoint.host().toHostString()).isEqualTo("127.0.0.1");
            assertThat(endpoint.port()).isEqualTo(8080);
            assertThat(endpoint.transport()).isEqualTo(ItemRef.iid(NetworkVocabulary.Tcp.KEY));

            byte[] bytes = CgCbor.codec().encode(endpoint);
            Body decoded = (Body) CgCbor.codec().decode(bytes);
            TcpEndpoint roundtripped = TcpEndpoint.from(decoded);

            assertThat(roundtripped.host().bytes()).isEqualTo(endpoint.host().bytes());
            assertThat(roundtripped.port()).isEqualTo(endpoint.port());
        }

        @Test
        @DisplayName("UnixEndpoint carries @Path and round-trips")
        void unixRoundtrip() {
            UnixEndpoint endpoint = UnixEndpoint.of("/tmp/cg.sock");
            assertThat(endpoint.path()).isEqualTo("/tmp/cg.sock");
            assertThat(endpoint.transport()).isEqualTo(ItemRef.iid(NetworkVocabulary.Unix.KEY));

            UnixEndpoint roundtripped = UnixEndpoint.from((Body) CgCbor.codec().decode(CgCbor.codec().encode(endpoint)));
            assertThat(roundtripped.path()).isEqualTo(endpoint.path());
        }

        @Test
        @DisplayName("ReticulumEndpoint carries @Identity bytes and round-trips")
        void reticulumRoundtrip() {
            byte[] identity = new byte[]{0x01, 0x02, 0x03, 0x04};
            ReticulumEndpoint endpoint = ReticulumEndpoint.of(identity);
            assertThat(endpoint.identity()).isEqualTo(identity);
            assertThat(endpoint.transport()).isEqualTo(ItemRef.iid(NetworkVocabulary.Reticulum.KEY));

            ReticulumEndpoint roundtripped = ReticulumEndpoint.from((Body) CgCbor.codec().decode(CgCbor.codec().encode(endpoint)));
            assertThat(roundtripped.identity()).isEqualTo(endpoint.identity());
        }

        @Test
        @DisplayName("LoopbackEndpoint carries no bindings and round-trips")
        void loopbackRoundtrip() {
            LoopbackEndpoint endpoint = LoopbackEndpoint.of();
            assertThat(endpoint.bindings()).isEmpty();
            assertThat(endpoint.transport()).isEqualTo(ItemRef.iid(NetworkVocabulary.Loopback.KEY));

            LoopbackEndpoint roundtripped = LoopbackEndpoint.from((Body) CgCbor.codec().decode(CgCbor.codec().encode(endpoint)));
            assertThat(roundtripped.bindings()).isEmpty();
        }
    }

    @Nested
    @DisplayName("Polymorphic from(Body) dispatch")
    class PolymorphicView {

        @Test
        @DisplayName("Endpoint.from returns the appropriate subclass for each head")
        void dispatchesByHead() {
            Endpoint tcp = Endpoint.from(TcpEndpoint.of("10.0.0.1", 9000));
            Endpoint unix = Endpoint.from(UnixEndpoint.of("/var/run/x"));
            Endpoint reticulum = Endpoint.from(ReticulumEndpoint.of(new byte[]{0x42}));
            Endpoint loopback = Endpoint.from(LoopbackEndpoint.of());

            assertThat(tcp).isInstanceOf(TcpEndpoint.class);
            assertThat(unix).isInstanceOf(UnixEndpoint.class);
            assertThat(reticulum).isInstanceOf(ReticulumEndpoint.class);
            assertThat(loopback).isInstanceOf(LoopbackEndpoint.class);
        }

        @Test
        @DisplayName("Endpoint.from rejects bodies whose head isn't a known endpoint subarchetype")
        void rejectsUnknownHead() {
            Body alien = Body.of(ItemRef.iid(NetworkVocabulary.Tcp.KEY), List.of());
            assertThatThrownBy(() -> Endpoint.from(alien))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("not a known Endpoint subarchetype");
        }

        @Test
        @DisplayName("Subclass from(Body) rejects bodies with the wrong head")
        void subclassRejectsWrongHead() {
            Body mismatched = Body.of(ItemRef.iid(UnixEndpoint.KEY), List.of(
                    Binding.literal(ItemRef.iid(NetworkVocabulary.Path.KEY), "/a")));
            assertThatThrownBy(() -> TcpEndpoint.from(mismatched))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("not the TcpEndpoint archetype");
        }
    }

    @Nested
    @DisplayName("Manifest carries the Addresses binding")
    class ManifestBinding {

        @Test
        @DisplayName("each Endpoint subarchetype's manifest links to its Transport via @Addresses")
        void subarchetypesAddressTheirTransport() {
            Librarian librarian = Librarian.ephemeral(ItemStage.javaOnly());
            librarian.bootstrap();

            assertAddressesBinding(librarian, TcpEndpoint.KEY,       NetworkVocabulary.Tcp.KEY);
            assertAddressesBinding(librarian, UnixEndpoint.KEY,      NetworkVocabulary.Unix.KEY);
            assertAddressesBinding(librarian, ReticulumEndpoint.KEY, NetworkVocabulary.Reticulum.KEY);
            assertAddressesBinding(librarian, LoopbackEndpoint.KEY,  NetworkVocabulary.Loopback.KEY);
        }

        private static void assertAddressesBinding(Librarian librarian, String endpointKey, String transportKey) {
            ItemRef addresses = ItemRef.iid(NetworkVocabulary.Addresses.KEY);
            ItemRef expectedTransport = ItemRef.iid(transportKey);

            List<DatumRef> manifestCids = librarian.library().manifestCidsForItem(ItemRef.iid(endpointKey));
            assertThat(manifestCids).as("manifest for %s", endpointKey).isNotEmpty();

            Optional<Manifest> manifestOpt = librarian.fetchManifest(manifestCids.get(0));
            assertThat(manifestOpt).as("manifest body for %s", endpointKey).isPresent();

            boolean found = false;
            for (Binding b : manifestOpt.get().body().bindings()) {
                if (addresses.equals(b.role()) && expectedTransport.equals(b.target())) {
                    found = true;
                    break;
                }
            }
            assertThat(found)
                    .as("%s manifest carries @Addresses → @%s", endpointKey, transportKey)
                    .isTrue();
        }
    }
}
