package dev.everydaythings.graph.identity;

import dev.everydaythings.graph.datum.Binding;
import dev.everydaythings.graph.datum.Body;
import dev.everydaythings.graph.id.CompoundKey;
import dev.everydaythings.graph.id.DatumRef;
import dev.everydaythings.graph.id.ItemRef;
import dev.everydaythings.graph.item.Manifest;
import dev.everydaythings.graph.runtime.librarian.Librarian;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Librarian-held registry of cryptographic algorithm handles.
 *
 * <p>Populated by scanning the graph for all items whose head is the
 * {@link AlgorithmVocabulary.Algorithm} archetype, reading each algorithm
 * sememe's metadata bindings (COSE id, varsig codec, multikey codec,
 * JCA names, etc.), and building one {@link AlgorithmHandle} per algorithm.
 *
 * <p>Indexed three ways for the lookups callers actually do:
 * <ul>
 *   <li>by COSE id (the IANA-standard integer)</li>
 *   <li>by varsig codec (multicodec identifier on signature wire-format)</li>
 *   <li>by multikey codec (multicodec identifier on public-key wire-format)</li>
 *   <li>by sememe IID (when a graph reference names the algorithm directly)</li>
 * </ul>
 *
 * <p>Population:
 * <ul>
 *   <li>Warmed by the librarian at the end of {@code bootstrap()} — all
 *       seeded algorithm sememes are then ready for hot-path lookup with no
 *       further graph access.</li>
 *   <li>Anonymous librarians (no bootstrap) populate lazily: the first miss
 *       triggers a full scan, after which subsequent hits are direct.</li>
 *   <li>Runtime-deployed algorithm sememes (added after warmup) can call
 *       {@link #register(AlgorithmHandle)} directly, or the cache picks them
 *       up via lazy re-scan on miss.</li>
 * </ul>
 *
 * <p>Handle construction reads metadata off each algorithm sememe's manifest
 * bindings — the same {@code @Seed.Property} fields that put the data into
 * the graph.  Built-in algorithms get a {@link JcaAlgorithmHandle}; future
 * polyglot-deployed algorithms will get a {@code PolyglotAlgorithmHandle}
 * driven by their associated code item.
 */
public final class AlgorithmCache {

    private final Map<Long, AlgorithmHandle> byCoseId       = new ConcurrentHashMap<>();
    private final Map<Long, AlgorithmHandle> byVarsigCode   = new ConcurrentHashMap<>();
    private final Map<Long, AlgorithmHandle> byMultikeyCode = new ConcurrentHashMap<>();
    private final Map<ItemRef, AlgorithmHandle> byIid       = new ConcurrentHashMap<>();

    private volatile boolean warmed = false;

    /** Whether {@link #warm(Librarian)} has been called at least once. */
    public boolean isWarmed() {
        return warmed;
    }

    public AlgorithmHandle byCoseId(long id) {
        return byCoseId.get(id);
    }

    public AlgorithmHandle byVarsigCode(long code) {
        return byVarsigCode.get(code);
    }

    public AlgorithmHandle byMultikeyCode(long code) {
        return byMultikeyCode.get(code);
    }

    public AlgorithmHandle byIid(ItemRef iid) {
        return byIid.get(iid);
    }

    /**
     * Register a handle, indexing it by every key it exposes.  Codecs equal
     * to {@code 0} are treated as "not applicable" (e.g., AEAD algorithms
     * have no varsig code) and skip the corresponding index.
     */
    public void register(AlgorithmHandle handle) {
        byIid.put(handle.sememeIid(), handle);
        if (handle.coseId() != 0)       byCoseId.put(handle.coseId(), handle);
        if (handle.varsigCode() != 0)   byVarsigCode.put(handle.varsigCode(), handle);
        if (handle.multikeyCode() != 0) byMultikeyCode.put(handle.multikeyCode(), handle);
    }

    /**
     * Walk every algorithm sememe in the librarian's graph, read its
     * metadata, build a handle, register it.  Safe to call repeatedly —
     * later calls overwrite existing entries (e.g., when a polyglot
     * implementation appears for an algorithm that previously had only a
     * JCA backing).
     */
    public synchronized void warm(Librarian librarian) {
        ItemRef algorithmArchetype = ItemRef.iid(AlgorithmVocabulary.Algorithm.KEY);
        for (DatumRef manifestId : librarian.library().manifestCidsForType(algorithmArchetype)) {
            librarian.fetchManifest(manifestId).ifPresent(manifest -> {
                AlgorithmHandle handle = buildHandle(manifest);
                if (handle != null) register(handle);
            });
        }
        warmed = true;
    }

    /**
     * Construct an {@link AlgorithmHandle} from an algorithm sememe's
     * manifest.  Reads the {@code @Seed.Property}-derived bindings and
     * (for now) always builds a {@link JcaAlgorithmHandle}.  Returns
     * {@code null} if the manifest is missing the bindings needed for any
     * handle to function.
     *
     * <p>Polyglot algorithms (code items implementing the algorithm sememe
     * in a non-Java language) will route through a different handle factory
     * here once the polyglot dispatch path lands.
     */
    private static AlgorithmHandle buildHandle(Manifest manifest) {
        ItemRef sememeIid = manifest.itemId();
        long coseId       = readLong(manifest.body(), AlgorithmVocabulary.CoseId.KEY).orElse(0L);
        long varsigCode   = readLong(manifest.body(), AlgorithmVocabulary.VarsigCode.KEY).orElse(0L);
        long multikeyCode = readLong(manifest.body(), AlgorithmVocabulary.MultikeyCode.KEY).orElse(0L);
        String signatureName = readString(manifest.body(), AlgorithmVocabulary.SignatureName.KEY).orElse(null);
        String keyFactoryName = readString(manifest.body(), AlgorithmVocabulary.KeyFactory.KEY).orElse(null);
        int rawKeyBytes = readLong(manifest.body(), AlgorithmVocabulary.RawKeyBytes.KEY).orElse(0L).intValue();

        if (sememeIid == null) return null;
        return new JcaAlgorithmHandle(sememeIid, coseId, varsigCode, multikeyCode,
                signatureName, keyFactoryName, rawKeyBytes);
    }

    private static Optional<Long> readLong(Body body, String roleKey) {
        return body.binding(CompoundKey.of(ItemRef.iid(roleKey)))
                .map(Binding::target)
                .filter(t -> t instanceof Long)
                .map(t -> (Long) t);
    }

    private static Optional<String> readString(Body body, String roleKey) {
        return body.binding(CompoundKey.of(ItemRef.iid(roleKey)))
                .map(Binding::target)
                .filter(t -> t instanceof String)
                .map(t -> (String) t);
    }
}
