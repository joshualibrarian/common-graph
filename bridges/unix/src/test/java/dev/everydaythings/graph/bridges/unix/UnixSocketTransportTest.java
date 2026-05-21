package dev.everydaythings.graph.bridges.unix;

import dev.everydaythings.graph.network.transport.Transport.Listener;
import dev.everydaythings.graph.network.tunnel.Tunnel;
import dev.everydaythings.graph.value.UnixEndpoint;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Round-trip smoke tests for {@link UnixSocketTransport}.
 */
class UnixSocketTransportTest {

    @Test
    @DisplayName("listen → connect → bidirectional byte exchange")
    void bidirectionalRoundTrip(@TempDir Path tempDir) throws Exception {
        Path socketPath = tempDir.resolve("test.sock");
        UnixSocketTransport transport = new UnixSocketTransport();
        try {
            // Inbound side: queue every byte[] the server tunnel receives.
            LinkedBlockingQueue<byte[]> serverReceived = new LinkedBlockingQueue<>();
            CompletableFuture<Tunnel> serverTunnelFuture = new CompletableFuture<>();
            Listener listener = transport.listen(
                    UnixEndpoint.of(socketPath.toString()),
                    tunnel -> {
                        tunnel.onReceive(serverReceived::offer);
                        serverTunnelFuture.complete(tunnel);
                    });
            try {
                // Outbound side.
                LinkedBlockingQueue<byte[]> clientReceived = new LinkedBlockingQueue<>();
                Tunnel client = transport.connect(UnixEndpoint.of(socketPath.toString()))
                        .get(2, TimeUnit.SECONDS);
                client.onReceive(clientReceived::offer);

                // Wait for server to see the connection.
                Tunnel server = serverTunnelFuture.get(2, TimeUnit.SECONDS);

                // Client → server.
                client.send("hello server".getBytes()).get(2, TimeUnit.SECONDS);
                byte[] gotByServer = serverReceived.poll(2, TimeUnit.SECONDS);
                assertThat(gotByServer).isNotNull();
                assertThat(new String(gotByServer)).isEqualTo("hello server");

                // Server → client.
                server.send("hello client".getBytes()).get(2, TimeUnit.SECONDS);
                byte[] gotByClient = clientReceived.poll(2, TimeUnit.SECONDS);
                assertThat(gotByClient).isNotNull();
                assertThat(new String(gotByClient)).isEqualTo("hello client");

                client.close();
                server.close();
            } finally {
                listener.close();
            }
        } finally {
            transport.close();
        }
    }

    @Test
    @DisplayName("listener unlinks stale socket file before binding")
    void listenerUnlinksStaleSocket(@TempDir Path tempDir) throws Exception {
        Path socketPath = tempDir.resolve("stale.sock");
        // Pre-create a fake file at the socket path.
        java.nio.file.Files.writeString(socketPath, "stale content");

        UnixSocketTransport transport = new UnixSocketTransport();
        try {
            Listener listener = transport.listen(
                    UnixEndpoint.of(socketPath.toString()),
                    tunnel -> {});
            try {
                // After bind, the path should be a socket — not the stale regular file.
                assertThat(java.nio.file.Files.exists(socketPath)).isTrue();
                // Connect to verify it's actually a working socket.
                Tunnel client = transport.connect(UnixEndpoint.of(socketPath.toString()))
                        .get(2, TimeUnit.SECONDS);
                client.close();
            } finally {
                listener.close();
            }
        } finally {
            transport.close();
        }
    }

    @Test
    @DisplayName("listener close unlinks the socket file")
    void listenerCloseUnlinks(@TempDir Path tempDir) throws Exception {
        Path socketPath = tempDir.resolve("cleanup.sock");
        UnixSocketTransport transport = new UnixSocketTransport();
        try {
            Listener listener = transport.listen(
                    UnixEndpoint.of(socketPath.toString()),
                    tunnel -> {});
            assertThat(java.nio.file.Files.exists(socketPath)).isTrue();
            listener.close();
            assertThat(java.nio.file.Files.exists(socketPath)).isFalse();
        } finally {
            transport.close();
        }
    }
}
