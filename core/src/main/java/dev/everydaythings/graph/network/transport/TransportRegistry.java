package dev.everydaythings.graph.network.transport;

import dev.everydaythings.graph.network.Endpoint;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.ServiceLoader;
import java.util.stream.StreamSupport;

/**
 * TransportRegistry — runtime discovery of {@link Transport}
 * implementations via Java's {@link ServiceLoader}.
 *
 * <p>Each transport bridge ships a service-provider declaration at
 * {@code META-INF/services/dev.everydaythings.graph.network.transport.Transport}
 * listing its implementation classes.  At lookup time, the registry walks
 * the classpath's registered transports and returns the first one whose
 * {@link Transport#handles(Endpoint)} matches the requested endpoint.
 *
 * <h2>Why ServiceLoader</h2>
 *
 * <p>Transport implementations are environment-specific:
 * {@code UnixSocketTransport} only makes sense where the OS supports Unix
 * domain sockets; a Reticulum-native deployment only ships
 * {@code ReticulumTransport}; a Windows-only deployment doesn't carry
 * Unix-socket code at all.  By discovering transports at runtime through
 * the classpath, {@code :core} stays environment-agnostic — apps assemble
 * the transports they need from the available bridges, and
 * {@link #forEndpoint} picks the right one for each endpoint.
 *
 * <h2>Caching</h2>
 *
 * <p>{@link #forEndpoint} loads transports lazily once per JVM and caches
 * the result.  Lookups are O(n) over the (small) list of registered
 * transports — typically 1 to a handful per deployment.
 *
 * <h2>Lifecycle</h2>
 *
 * <p>Transports returned by this registry are SHARED instances.  Callers
 * should not call {@link Transport#close()} unless they uniquely own the
 * registry's lifecycle.  For tests or fine-grained control, construct
 * transports directly rather than going through this registry.
 */
public final class TransportRegistry {

    private TransportRegistry() {}

    /** Lazily-loaded cache of all registered transports. */
    private static volatile List<Transport> cached;

    /**
     * Find the registered transport that {@link Transport#handles} this
     * endpoint.  Returns empty if no transport claims it (typically: the
     * needed bridge isn't on the classpath).
     */
    public static Optional<Transport> forEndpoint(Endpoint endpoint) {
        Objects.requireNonNull(endpoint, "endpoint");
        for (Transport t : all()) {
            if (t.handles(endpoint)) return Optional.of(t);
        }
        return Optional.empty();
    }

    /**
     * Resolve a transport for the endpoint, or throw a helpful error.
     */
    public static Transport require(Endpoint endpoint) {
        return forEndpoint(endpoint).orElseThrow(() -> new IllegalStateException(
                "No Transport on classpath handles endpoint " + endpoint
                        + " (transport=" + endpoint.transport() + ").  "
                        + "Add the matching :bridges:* module to your runtime."));
    }

    /** Every transport currently registered via ServiceLoader. */
    public static List<Transport> all() {
        List<Transport> local = cached;
        if (local != null) return local;
        synchronized (TransportRegistry.class) {
            if (cached == null) {
                cached = loadFromServiceLoader();
            }
            return cached;
        }
    }

    /**
     * Force a reload of the registered transports.  Primarily for tests
     * that install or remove transport service-provider files at runtime.
     */
    public static synchronized void reload() {
        cached = null;
    }

    // ==================================================================================
    // Internals
    // ==================================================================================

    private static List<Transport> loadFromServiceLoader() {
        ServiceLoader<Transport> loader = ServiceLoader.load(Transport.class);
        List<Transport> result = new ArrayList<>(
                StreamSupport.stream(loader.spliterator(), false).toList());
        return List.copyOf(result);
    }
}
