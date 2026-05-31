package dev.everydaythings.graph.encoding;

import dev.everydaythings.graph.ref.ItemRef;

import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * EncodingRegistry — codec lookup by IID.  Owned by a Librarian (or a
 * standalone process that needs encoding lookups); the registry resolves a
 * codec's identity ({@link Encoding#encoding()}) back to a live
 * {@link Encoding} instance.
 *
 * <h2>Use cases</h2>
 *
 * <ul>
 *   <li><b>Materialized item open.</b> {@code MaterializedDataStore.open}
 *       reads {@code .item/codec} (an IID) and asks the registry for the
 *       matching Encoding to decode {@code .item/objects/}.</li>
 *   <li><b>Wire codec negotiation.</b> Parley's point-and-grunt agrees on
 *       a codec IID; the receiver resolves that IID through its registry
 *       to know how to decode the stream.</li>
 *   <li><b>Bundle interchange.</b> Receiving a foreign-codec item bundle
 *       requires looking up that codec by IID before any bytes can be
 *       parsed.</li>
 * </ul>
 *
 * <h2>Registration</h2>
 *
 * <p>Today: explicit — the librarian's bootstrap calls
 * {@link #register(Encoding)} for each built-in codec it ships with.
 * {@link #defaultRegistry()} returns a registry pre-populated with CG-CBOR.
 *
 * <p>Future: ServiceLoader-based discovery — codecs declare themselves via
 * {@code META-INF/services/dev.everydaythings.graph.encoding.Encoding} so
 * the registry auto-discovers anything on the classpath.  When the second
 * codec lands, switch the default to {@code discoverViaServiceLoader()}.
 *
 * <h2>Identity</h2>
 *
 * <p>Indexed by {@link Encoding#encoding()} — each codec's IID.  Two
 * registrations with the same IID:  the latest one wins.
 *
 * <p>Thread-safe.
 */
public final class EncodingRegistry {

    private final ConcurrentMap<ItemRef, Encoding> byIid = new ConcurrentHashMap<>();

    /** Construct an empty registry.  Use {@link #defaultRegistry()} for the standard set. */
    public EncodingRegistry() {}

    /**
     * Register an encoding.  Replaces any previous registration with the
     * same IID.  The encoding's {@link Encoding#encoding()} is abstract,
     * so every codec declares its own IID; this method simply indexes by
     * that IID.
     */
    public void register(Encoding encoding) {
        Objects.requireNonNull(encoding, "encoding");
        byIid.put(encoding.encoding(), encoding);
    }

    /** Look up a codec by IID.  Empty if not registered. */
    public Optional<Encoding> get(ItemRef encodingIid) {
        if (encodingIid == null) return Optional.empty();
        return Optional.ofNullable(byIid.get(encodingIid));
    }

    /** The set of currently-registered codec IIDs. */
    public Set<ItemRef> known() {
        return Set.copyOf(byIid.keySet());
    }

    /** Number of registered codecs. */
    public int size() {
        return byIid.size();
    }

    // ==================================================================================
    // Built-in defaults
    // ==================================================================================

    /**
     * Returns a registry with CG's built-in codecs pre-registered.  Today:
     * just {@link CgCbor}.  When more codecs ship, they get added here.
     */
    public static EncodingRegistry defaultRegistry() {
        EncodingRegistry r = new EncodingRegistry();
        r.register(CgCbor.codec());
        return r;
    }
}
