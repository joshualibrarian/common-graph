package dev.everydaythings.graph.network.transport;

import dev.everydaythings.graph.id.ItemRef;
import dev.everydaythings.graph.network.tunnel.Tunnel;
import dev.everydaythings.graph.value.Endpoint;

import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * Transport — the SPI a bridge implements to produce {@link Tunnel}s for a
 * particular transport protocol.
 *
 * <p>A Transport handles exactly one protocol — TCP, Unix sockets, Reticulum,
 * WebSocket, etc. — declared via {@link #protocol()} as the corresponding
 * {@code @cg.transport:*} sememe from
 * {@link dev.everydaythings.graph.network.NetworkVocabulary}.
 *
 * <p>Concrete implementations live in {@code :bridges}. Each bridge module
 * instantiates one or more Transports and exposes them however its host app
 * prefers (direct construction, dependency-injected, registered with the
 * Librarian, ...). There is deliberately <b>no central registry</b>: callers
 * know which transport they want because they know which protocol they're
 * speaking.
 *
 * <h2>Two operations</h2>
 * <ul>
 *   <li>{@link #connect(Endpoint)} — outbound: open a Tunnel to a remote
 *       endpoint.</li>
 *   <li>{@link #listen(Endpoint, Consumer)} — inbound: bind a listener at an
 *       endpoint; deliver incoming Tunnels to the handler as they arrive.</li>
 * </ul>
 *
 * <h2>What Transport doesn't do</h2>
 * <ul>
 *   <li><b>Encryption / authentication</b> — Transports produce plain byte
 *       channels. Security is added by wrapping the resulting Tunnel in a
 *       security decorator (CG-native Noise, or TLS in {@code :bridges}).
 *       Some transports — Reticulum — supply confidentiality and authentication
 *       intrinsically; those expose it via the produced Tunnel's
 *       {@link Tunnel#isConfidential()} / {@link Tunnel#isAuthenticated()}
 *       declarations rather than requiring a wrapper.</li>
 *   <li><b>Framing</b> — the codec's concern, atop the Tunnel.</li>
 *   <li><b>Discovery</b> — separate concern; may sit atop transports.</li>
 *   <li><b>Reconnection / retry</b> — higher-level connection-management
 *       concern.</li>
 * </ul>
 */
public interface Transport {

    /**
     * The transport protocol this implementation serves — one of the
     * {@code @cg.transport:*} sememes from
     * {@link dev.everydaythings.graph.network.NetworkVocabulary}.
     */
    ItemRef protocol();

    /**
     * Open a Tunnel to the given endpoint. The endpoint's PROTOCOL binding
     * must match this transport's {@link #protocol()}; implementations should
     * fail the returned future with {@link IllegalArgumentException} otherwise.
     */
    CompletableFuture<Tunnel> connect(Endpoint endpoint);

    /**
     * Bind a listener at the given endpoint. Each incoming connection produces
     * a Tunnel which is delivered to {@code onAccept} (on whatever thread the
     * transport's accept loop uses; implementations document their threading).
     *
     * <p>If the endpoint specifies an auto-assignable address (e.g., TCP port
     * 0), the returned {@link Listener}'s {@link Listener#actualEndpoint()}
     * reports the address actually bound.
     *
     * @throws IllegalArgumentException if {@code endpoint}'s protocol doesn't
     *     match {@link #protocol()}
     * @throws java.io.UncheckedIOException if the bind fails synchronously
     */
    Listener listen(Endpoint endpoint, Consumer<Tunnel> onAccept);

    /**
     * Handle to an active listener. {@link #close()} stops accepting new
     * connections and releases the bind; Tunnels already handed off to the
     * accept handler are unaffected.
     */
    interface Listener extends AutoCloseable {

        /**
         * The endpoint actually bound — relevant when the caller passed an
         * auto-assignable port (e.g., TCP {@code :0}) and wants to know what
         * was allocated.
         */
        Endpoint actualEndpoint();

        @Override
        void close();
    }
}
