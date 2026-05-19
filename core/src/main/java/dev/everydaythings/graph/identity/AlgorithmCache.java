package dev.everydaythings.graph.identity;

import dev.everydaythings.graph.id.DatumRef;
import dev.everydaythings.graph.id.ItemRef;
import dev.everydaythings.graph.identity.algorithm.Algorithm;
import dev.everydaythings.graph.identity.algorithm.Signing;
import dev.everydaythings.graph.item.Item;
import dev.everydaythings.graph.item.Manifest;
import dev.everydaythings.graph.runtime.librarian.Librarian;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Librarian-held registry of cryptographic algorithm instances.
 *
 * <p>Populated by scanning the graph for items whose head is
 * {@link Algorithm}, fetching each as its embodied Item (a concrete
 * {@code Signing.Ed25519} or similar), and indexing by the wire
 * codes those algorithms carry on their bindings.
 *
 * <p>Indexed four ways for the lookups callers actually do:
 * <ul>
 *   <li>by COSE id (the IANA-standard integer)</li>
 *   <li>by varsig codec (multicodec identifier on signature wire-format)</li>
 *   <li>by multikey codec (multicodec identifier on public-key wire-format)</li>
 *   <li>by sememe IID (when a graph reference names the algorithm directly)</li>
 * </ul>
 *
 * <p>Population:
 * <ul>
 *   <li>Warmed by the librarian at the end of {@code bootstrap()}.</li>
 *   <li>Anonymous librarians populate lazily on first miss.</li>
 *   <li>Runtime-deployed algorithm sememes (added after warmup) are picked up
 *       via lazy re-scan on miss.</li>
 * </ul>
 *
 * <p>Today only signing algorithms are wired; key-agreement, AEAD, hash, and
 * ciphersuite sub-archetypes will land alongside this same cache when their
 * runtime forms migrate to {@code Algorithm} sub-archetypes.
 */
public final class AlgorithmCache {

    private final Map<Long, Signing>    byCoseId       = new ConcurrentHashMap<>();
    private final Map<Long, Signing>    byVarsigCode   = new ConcurrentHashMap<>();
    private final Map<Long, Signing>    byMultikeyCode = new ConcurrentHashMap<>();
    private final Map<ItemRef, Signing> byIid          = new ConcurrentHashMap<>();

    private volatile boolean warmed = false;

    /** Whether {@link #warm(Librarian)} has been called at least once. */
    public boolean isWarmed() {
        return warmed;
    }

    public Signing byCoseId(long id) {
        return byCoseId.get(id);
    }

    public Signing byVarsigCode(long code) {
        return byVarsigCode.get(code);
    }

    public Signing byMultikeyCode(long code) {
        return byMultikeyCode.get(code);
    }

    public Signing byIid(ItemRef iid) {
        return byIid.get(iid);
    }

    /**
     * Register an algorithm, indexing it by every wire code it exposes.
     * Codes equal to {@code 0} are treated as "not applicable" and skip the
     * corresponding index.
     */
    public void register(Signing algorithm) {
        byIid.put(algorithm.iid(), algorithm);
        if (algorithm.coseId() != 0)       byCoseId.put(algorithm.coseId(), algorithm);
        if (algorithm.varsigCode() != 0)   byVarsigCode.put(algorithm.varsigCode(), algorithm);
        if (algorithm.multikeyCode() != 0) byMultikeyCode.put(algorithm.multikeyCode(), algorithm);
    }

    /**
     * Walk every signing-algorithm sememe in the librarian's graph, fetch
     * its embodied Item, register it.  Safe to call repeatedly — later calls
     * overwrite existing entries.
     */
    public synchronized void warm(Librarian librarian) {
        ItemRef signingArchetype = ItemRef.iid(Signing.KEY);
        for (DatumRef manifestId : librarian.library().manifestCidsForType(signingArchetype)) {
            librarian.fetchManifest(manifestId).ifPresent(manifest -> {
                Signing algorithm = hydrate(librarian, manifest);
                if (algorithm != null) register(algorithm);
            });
        }
        warmed = true;
    }

    /**
     * Hydrate a signing-algorithm Item from its manifest.  Uses the
     * librarian's standard item-fetch path so {@code @Embodies} dispatch and
     * {@link dev.everydaythings.graph.item.BodyBinder BodyBinder} run as
     * usual, yielding a fully-populated concrete algorithm instance.
     */
    private static Signing hydrate(Librarian librarian, Manifest manifest) {
        ItemRef sememeIid = manifest.itemId();
        Item item = librarian.fetchItem(sememeIid).orElse(null);
        if (item instanceof Signing signing) return signing;
        return null;
    }
}
