package dev.everydaythings.graph.library;

import dev.everydaythings.graph.encoding.Encoding;
import dev.everydaythings.graph.datum.Body;
import dev.everydaythings.graph.datum.Datum;
import dev.everydaythings.graph.datum.Frame;
import dev.everydaythings.graph.datum.Record;
import dev.everydaythings.graph.item.Manifest;
import dev.everydaythings.graph.id.ContentRef;
import dev.everydaythings.graph.id.DatumRef;
import dev.everydaythings.graph.id.ItemRef;
import dev.everydaythings.graph.library.data.DataByteStore;
import dev.everydaythings.graph.library.data.DataStore;
import dev.everydaythings.graph.library.index.RefIndexStore;
import dev.everydaythings.graph.library.index.TokenIndexStore;
import dev.everydaythings.graph.library.index.TokenPosting;
import dev.everydaythings.graph.library.puremap.PureMapDataStore;
import dev.everydaythings.graph.library.puremap.PureMapRefIndexStore;
import dev.everydaythings.graph.library.puremap.PureMapTokenIndexStore;
import dev.everydaythings.graph.library.rocksdb.RocksDataStore;
import dev.everydaythings.graph.library.rocksdb.RocksRefIndexStore;
import dev.everydaythings.graph.library.rocksdb.RocksTokenIndexStore;
import dev.everydaythings.graph.library.skiplist.SkipListDataStore;
import dev.everydaythings.graph.library.skiplist.SkipListRefIndexStore;
import dev.everydaythings.graph.library.skiplist.SkipListTokenIndexStore;

import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * A Common Graph node's local storage. Composes three stores — a
 * {@link DataStore} (primary truth), a {@link RefIndexStore} (query
 * indexes), and a {@link TokenIndexStore} (text lookup) — and coordinates
 * write-side indexing across them on every Datum that lands.
 *
 * <p>Each store has multiple implementations (pure-map, SkipList, MapDB,
 * RocksDB...). Pick any combination via the {@link Builder}; static factories
 * cover common cases:
 *
 * <ul>
 *   <li>{@link #inMemory()} — all SkipList in RAM.</li>
 *   <li>{@link #atPath(Path)} — all SkipList plus a {@code .librarian/format}
 *       marker at the path.</li>
 *   <li>{@link #anonymous()} — pure-map data + SkipList indexes; cheapest
 *       for tests that don't need persistence or rich token resolution.</li>
 *   <li>{@link #builder()} — full control over the triple.</li>
 * </ul>
 *
 * <p>Library returns domain objects ({@link Datum}, {@link Body},
 * {@link Record}, {@link Frame}, {@link Manifest}) rather than bytes. The
 * decode boundary lives inside the {@link DataStore}.
 *
 * <p>For the storage architecture, see
 * <a href="../../../../../../../../../docs/storage.md">storage.md</a>.
 */
public final class Library implements AutoCloseable {

    private final DataStore data;
    private final RefIndexStore index;
    private final TokenIndexStore tokens;
    private final Optional<Path> rootPath;
    private volatile Set<ItemRef> knownLanguagesCache;

    private Library(DataStore data, RefIndexStore index, TokenIndexStore tokens,
                    Optional<Path> rootPath) {
        this.data = Objects.requireNonNull(data, "data");
        this.index = Objects.requireNonNull(index, "index");
        this.tokens = Objects.requireNonNull(tokens, "tokens");
        this.rootPath = Objects.requireNonNull(rootPath, "rootPath");
    }

    // ==================================================================================
    // Builder + static factories
    // ==================================================================================

    public static Builder builder() {
        return new Builder();
    }

    /** All-SkipList in-memory Library; no filesystem footprint. */
    public static Library inMemory() {
        return builder()
                .data(SkipListDataStore.create())
                .refIndex(SkipListRefIndexStore.create())
                .tokens(SkipListTokenIndexStore.create())
                .build();
    }

    /**
     * All-SkipList Library rooted at {@code path}. Writes the
     * {@code .librarian/format} marker on first open; validates on subsequent.
     * Data itself currently stays in RAM until persistent SkipList backing
     * lands.
     */
    public static Library atPath(Path path) {
        Objects.requireNonNull(path, "path");
        return builder()
                .data(SkipListDataStore.create())
                .refIndex(SkipListRefIndexStore.create())
                .tokens(SkipListTokenIndexStore.create())
                .path(path)
                .build();
    }

    /**
     * Pure-map data + SkipList indexes. No encoder, no filesystem footprint.
     * The cheapest Library for tests that don't need persistence or signing.
     */
    public static Library anonymous() {
        return builder()
                .data(PureMapDataStore.create())
                .refIndex(PureMapRefIndexStore.create())
                .tokens(PureMapTokenIndexStore.create())
                .build();
    }

    /**
     * All-RocksDB Library rooted at {@code path}. Writes the format marker and
     * opens three RocksDB column-family groups (one each for data, ref index,
     * token index) under {@code path/data}, {@code path/ref-index},
     * {@code path/token-index}.
     */
    public static Library rocksDb(Path path) {
        Objects.requireNonNull(path, "path");
        return builder()
                .data(RocksDataStore.atPath(path))
                .refIndex(RocksRefIndexStore.atPath(path))
                .tokens(RocksTokenIndexStore.atPath(path))
                .path(path)
                .build();
    }

    /**
     * All-MapDB Library rooted at {@code path}. Lightweight pure-Java embedded
     * persistence — good fit for tests with on-disk requirements without
     * RocksDB's native-library overhead, or for embedded deployments.
     * Creates {@code path/data.mapdb}, {@code path/ref-index.mapdb},
     * {@code path/token-index.mapdb}, plus the format marker.
     */
    public static Library mapDb(Path path) {
        Objects.requireNonNull(path, "path");
        return builder()
                .data(dev.everydaythings.graph.library.mapdb.MapDbDataStore.atPath(path))
                .refIndex(dev.everydaythings.graph.library.mapdb.MapDbRefIndexStore.atPath(path))
                .tokens(dev.everydaythings.graph.library.mapdb.MapDbTokenIndexStore.atPath(path))
                .path(path)
                .build();
    }

    public static final class Builder {
        private DataStore data;
        private RefIndexStore index;
        private TokenIndexStore tokens;
        private Path path;

        private Builder() {}

        public Builder data(DataStore store) { this.data = store; return this; }
        public Builder refIndex(RefIndexStore store) { this.index = store; return this; }
        public Builder tokens(TokenIndexStore store) { this.tokens = store; return this; }

        /**
         * Filesystem root for the Library. Optional. If set, the builder writes
         * the {@code .librarian/format} marker (or validates it if it exists)
         * using the data store's encoder. Ignored for stores with no encoder.
         */
        public Builder path(Path path) { this.path = path; return this; }

        public Library build() {
            Objects.requireNonNull(data, "data");
            Objects.requireNonNull(index, "index");
            Objects.requireNonNull(tokens, "tokens");
            Optional<Path> root = Optional.ofNullable(path);
            // Write or validate the format marker if a path is given and the
            // data store has an encoder.
            if (root.isPresent() && data.encoder().isPresent()) {
                FormatMarker.write(root.get(), data.encoder().get());
            }
            return new Library(data, index, tokens, root);
        }
    }

    // ==================================================================================
    // Encoding
    // ==================================================================================

    /** Delegates to the data store's encoder, if any. */
    public Optional<Encoding> encoder() {
        return data.encoder();
    }

    /** Filesystem root, if this Library was built with one. */
    public Optional<Path> rootPath() {
        return rootPath;
    }

    // ==================================================================================
    // Object persistence — compose the three stores
    // ==================================================================================

    public DatumRef put(Datum datum) {
        Objects.requireNonNull(datum, "datum");
        DatumRef id = data.put(datum);
        index.index(datum, id);
        tokens.index(datum, id);
        if (datum instanceof Body) knownLanguagesCache = null;
        return id;
    }

    public ContentRef putContent(byte[] bytes) {
        return data.putContent(bytes);
    }

    public Optional<byte[]> getContent(ContentRef cid) {
        return data.getContent(cid);
    }

    public boolean hasContent(ContentRef cid) {
        return data.hasContent(cid);
    }

    public boolean deleteContent(ContentRef cid) {
        // If the bytes decode to a Datum, unindex it first; then delete the
        // bytes. Otherwise it's a raw content blob; just delete.
        Optional<byte[]> bytesOpt = data.getContent(cid);
        if (bytesOpt.isEmpty()) return false;
        Datum d = data instanceof DataByteStore dbs ? dbs.decodeDatum(bytesOpt.get()) : null;
        if (d != null) {
            index.unindex(d, d.datumId());
            tokens.unindex(d, d.datumId());
            return data.delete(d.datumId());
        }
        return data.deleteContent(cid);
    }

    public boolean has(DatumRef datumId) {
        return data.has(datumId);
    }

    public boolean delete(DatumRef datumId) {
        Datum d = data.get(datumId).orElse(null);
        if (d == null) return false;
        index.unindex(d, datumId);
        tokens.unindex(d, datumId);
        return data.delete(datumId);
    }

    // ==================================================================================
    // Domain-object fetch API
    // ==================================================================================

    public Optional<Datum> fetchDatum(DatumRef datumId) {
        return data.get(datumId);
    }

    public Optional<Body> fetchBody(DatumRef datumId) {
        return data.get(datumId).filter(Body.class::isInstance).map(Body.class::cast);
    }

    public Optional<Record> fetchRecord(DatumRef datumId) {
        return data.get(datumId).filter(Record.class::isInstance).map(Record.class::cast);
    }

    public Optional<Frame> fetchFrame(DatumRef bodyId) {
        return fetchBody(bodyId).map(body -> Frame.of(body, loadRecords(bodyId)));
    }

    public Optional<Manifest> fetchManifest(DatumRef bodyId) {
        return fetchBody(bodyId).flatMap(body -> {
            try {
                return Optional.of(Manifest.of(body, loadRecords(bodyId)));
            } catch (IllegalArgumentException e) {
                return Optional.empty();
            }
        });
    }

    private List<Record> loadRecords(DatumRef bodyId) {
        return index.recordsForBody(bodyId).stream()
                .map(this::fetchRecord)
                .flatMap(Optional::stream)
                .toList();
    }

    // ==================================================================================
    // Index queries
    // ==================================================================================

    public List<DatumRef> recordCidsForBody(DatumRef bodyId) {
        return index.recordsForBody(bodyId);
    }

    public List<DatumRef> manifestCidsForItem(ItemRef itemIid) {
        return index.manifestsForItem(itemIid);
    }

    public List<DatumRef> manifestCidsForType(ItemRef typeIid) {
        return index.manifestsForType(typeIid);
    }

    public List<DatumRef> bodyCidsForReferenceBinding(ItemRef role, ItemRef target) {
        return index.bodiesByReferenceBinding(role, target);
    }

    // ==================================================================================
    // Token dictionary
    // ==================================================================================

    public List<TokenPosting> lookupToken(String token) {
        return tokens.lookup(token, this::fetchDatum)
                .map(this::enrichScope)
                .toList();
    }

    public List<TokenPosting> lookupTokenPrefix(String tokenPrefix, int limit) {
        return tokens.prefix(tokenPrefix, limit, this::fetchDatum)
                .map(this::enrichScope)
                .toList();
    }

    /**
     * Promote a Language-archetype qualifier from features to the {@code scope}
     * field. Caches the set of known languages on first call; cache is
     * invalidated whenever a new Body lands.
     */
    private TokenPosting enrichScope(TokenPosting p) {
        if (p.scope() != null || p.features().isEmpty()) return p;
        Set<ItemRef> languages = knownLanguages();
        for (ItemRef feature : p.features()) {
            if (languages.contains(feature)) {
                Set<ItemRef> remaining = new HashSet<>(p.features());
                remaining.remove(feature);
                return new TokenPosting(p.token(), p.target(), p.predicate(), feature,
                        remaining, p.weight(), p.source());
            }
        }
        return p;
    }

    private Set<ItemRef> knownLanguages() {
        Set<ItemRef> cached = knownLanguagesCache;
        if (cached != null) return cached;
        ItemRef langArchetype = ItemRef.iid(dev.everydaythings.graph.language.Language.KEY);
        Set<ItemRef> langs = new HashSet<>();
        for (DatumRef manifestId : index.manifestsForType(langArchetype)) {
            fetchManifest(manifestId).ifPresent(m -> {
                ItemRef iid = m.itemId();
                if (iid != null) langs.add(iid);
            });
        }
        cached = Set.copyOf(langs);
        knownLanguagesCache = cached;
        return cached;
    }

    // ==================================================================================
    // Byte-store passthroughs — for tests that need to inspect the realization layer.
    // ==================================================================================

    /**
     * Resolve a DatumRef to the ContentIDs of its locally-held wire-form
     * realizations. Byte-backed-specific; throws if the data store isn't a
     * {@link DataByteStore}.
     */
    public List<ContentRef> contentIdsForDatum(DatumRef datumId) {
        if (!(data instanceof DataByteStore bds)) {
            throw new UnsupportedOperationException(
                    "contentIdsForDatum requires a DataByteStore; data store is "
                            + data.getClass().getSimpleName());
        }
        return bds.contentIdsForDatum(datumId);
    }

    // ==================================================================================
    // Lifecycle
    // ==================================================================================

    @Override
    public void close() {
        closeQuietly(data);
        closeQuietly(index);
        closeQuietly(tokens);
    }

    private static void closeQuietly(Object o) {
        if (o instanceof AutoCloseable c) {
            try {
                c.close();
            } catch (Exception ignored) {
                // best-effort
            }
        }
    }
}
