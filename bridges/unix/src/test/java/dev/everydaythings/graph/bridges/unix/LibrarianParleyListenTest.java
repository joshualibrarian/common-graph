package dev.everydaythings.graph.bridges.unix;

import dev.everydaythings.graph.encoding.Encoding;
import dev.everydaythings.graph.network.parley.Parley;
import dev.everydaythings.graph.network.parley.RemoteConnection;
import dev.everydaythings.graph.network.transport.Transport;
import dev.everydaythings.graph.network.transport.TransportRegistry;
import dev.everydaythings.graph.network.tunnel.Tunnel;
import dev.everydaythings.graph.ref.ItemRef;
import dev.everydaythings.graph.runtime.librarian.Librarian;
import dev.everydaythings.graph.runtime.librarian.LibrarianPresence;
import dev.everydaythings.graph.value.UnixEndpoint;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Full-stack smoke test for the librarian-side parley listener:
 * Librarian.fresh() at a temp data dir, binds Parley on the conventional
 * {@code <data-dir>/parley.sock}, a client connects via Parley → gets a
 * live RemoteConnection.
 *
 * <p>Exercises the exact composition {@code Librarian.main()} performs
 * (without the blocking-on-interrupt part).
 */
class LibrarianParleyListenTest {

    private static final ItemRef CG_CBOR = ItemRef.iid(Encoding.CgCborV1.KEY);

    @Test
    @DisplayName("Librarian-with-parley-listener accepts an incoming Parley connection")
    void librarianAcceptsClient(@TempDir Path dataDir) throws Exception {
        // Server-side: persistent librarian at the temp data dir.
        Librarian server = Librarian.fresh(dataDir);
        try {
            // LibrarianPresence is held — server claims this data dir.
            assertThat(LibrarianPresence.isAlive(dataDir)).isTrue();

            Path socketPath = dataDir.resolve("parley.sock");
            UnixEndpoint endpoint = UnixEndpoint.of(socketPath.toString());
            Transport transport = TransportRegistry.require(endpoint);
            Parley serverParley = new Parley(server);
            Transport.Listener listener = serverParley.listen(
                    transport, endpoint, CG_CBOR, Set.of(CG_CBOR));
            try {
                // Client-side: a separate in-memory librarian, dials the
                // server's parley.sock as a Parley initiator.
                Librarian client = Librarian.inMemory();
                Tunnel tunnel = transport.connect(endpoint).get(5, TimeUnit.SECONDS);
                Parley clientParley = new Parley(client);
                RemoteConnection conn = clientParley.connect(tunnel, CG_CBOR, Set.of(CG_CBOR))
                        .get(5, TimeUnit.SECONDS);

                assertThat(conn.agreedCodec()).isEqualTo(CG_CBOR);
                assertThat(conn.isOpen()).isTrue();
                conn.close();
            } finally {
                listener.close();
            }
        } finally {
            server.close();
        }
    }
}
