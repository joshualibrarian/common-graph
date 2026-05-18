package dev.everydaythings.graph.network.transport;

import dev.everydaythings.graph.id.ItemRef;
import dev.everydaythings.graph.network.NetworkVocabulary;
import dev.everydaythings.graph.network.tunnel.LoopbackTunnel;
import dev.everydaythings.graph.network.tunnel.Tunnel;
import dev.everydaythings.graph.value.Endpoint;
import dev.everydaythings.graph.value.LoopbackEndpoint;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * In-VM loopback transport — produces paired {@link LoopbackTunnel}s
 * between {@link LoopbackEndpoint}s registered on the same instance.
 *
 * <p>Lives in {@code :core} (no third-party deps) because every other
 * networking concern needs a transport to test against; loopback is
 * the test scaffold that lets two librarians talk in a single JVM.
 *
 * <h2>Addressing</h2>
 *
 * <p>LoopbackEndpoints are keyed by their optional {@code name} binding.
 * Multiple listeners coexist on one transport instance keyed by
 * distinct names; an unnamed endpoint is the single default slot.
 *
 * <h2>Lifecycle</h2>
 *
 * <p>{@link #listen(Endpoint, Consumer)} registers a handler for an
 * endpoint and returns a {@link Listener}; closing the Listener
 * unregisters.  {@link #connect(Endpoint)} looks up the matching
 * registered handler, constructs a {@link LoopbackTunnel.Pair}, hands
 * the {@code b}-side to the accept handler, returns the {@code a}-side
 * to the caller.  Both sides are fully wired tunnels — there's no
 * handshake at this layer (Parley does its own codec point-and-grunt
 * above).
 *
 * <h2>Failure modes</h2>
 *
 * <ul>
 *   <li>Connecting to a name with no registered listener fails the
 *       returned future with {@link IllegalStateException}.</li>
 *   <li>Listening on a name that already has a registered listener
 *       throws {@link IllegalStateException} — one listener per name.</li>
 *   <li>Endpoints of the wrong subarchetype (not LoopbackEndpoint) fail
 *       the operation with {@link IllegalArgumentException}.</li>
 * </ul>
 */
public final class LoopbackTransport implements Transport {

    /** Name key used in the registry for unnamed endpoints. */
    private static final String UNNAMED_KEY = "";

    private final Map<String, Consumer<Tunnel>> listeners = new ConcurrentHashMap<>();

    @Override
    public ItemRef transport() {
        return ItemRef.iid(NetworkVocabulary.Loopback.KEY);
    }

    @Override
    public CompletableFuture<Tunnel> connect(Endpoint endpoint) {
        Objects.requireNonNull(endpoint, "endpoint");
        if (!(endpoint instanceof LoopbackEndpoint lb)) {
            return CompletableFuture.failedFuture(new IllegalArgumentException(
                    "LoopbackTransport.connect requires a LoopbackEndpoint, got "
                            + endpoint.getClass().getName()));
        }
        String key = nameKey(lb);
        Consumer<Tunnel> onAccept = listeners.get(key);
        if (onAccept == null) {
            return CompletableFuture.failedFuture(new IllegalStateException(
                    "No loopback listener registered for " + endpoint));
        }
        LoopbackTunnel.Pair pair = LoopbackTunnel.pair();
        // Hand the accept-side to the listener; return the connect-side.
        onAccept.accept(pair.b());
        return CompletableFuture.completedFuture(pair.a());
    }

    @Override
    public Listener listen(Endpoint endpoint, Consumer<Tunnel> onAccept) {
        Objects.requireNonNull(endpoint, "endpoint");
        Objects.requireNonNull(onAccept, "onAccept");
        if (!(endpoint instanceof LoopbackEndpoint lb)) {
            throw new IllegalArgumentException(
                    "LoopbackTransport.listen requires a LoopbackEndpoint, got "
                            + endpoint.getClass().getName());
        }
        String key = nameKey(lb);
        Consumer<Tunnel> prior = listeners.putIfAbsent(key, onAccept);
        if (prior != null) {
            throw new IllegalStateException(
                    "Loopback listener already registered for " + endpoint);
        }
        // The actualEndpoint is the same endpoint we bound — loopback has no
        // auto-assignment, no ephemeral-port concept.
        return new LoopbackListener(key, lb);
    }

    private static String nameKey(LoopbackEndpoint lb) {
        Optional<String> name = lb.name();
        return name.orElse(UNNAMED_KEY);
    }

    /** Snapshot of the names currently being listened on. */
    public java.util.Set<String> activeNames() {
        return java.util.Set.copyOf(listeners.keySet());
    }

    /** Listener handle returned by {@link #listen}. */
    private final class LoopbackListener implements Listener {
        private final String key;
        private final LoopbackEndpoint endpoint;
        private volatile boolean closed = false;

        LoopbackListener(String key, LoopbackEndpoint endpoint) {
            this.key = key;
            this.endpoint = endpoint;
        }

        @Override
        public Endpoint actualEndpoint() {
            return endpoint;
        }

        @Override
        public void close() {
            if (closed) return;
            closed = true;
            listeners.remove(key);
        }
    }
}
