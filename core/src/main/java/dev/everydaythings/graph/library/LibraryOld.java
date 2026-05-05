package dev.everydaythings.graph.library;

import dev.everydaythings.graph.frame.ItemFrame;
import dev.everydaythings.graph.Canonical;
import dev.everydaythings.graph.item.*;
import dev.everydaythings.graph.item.ItemOld;
import dev.everydaythings.graph.item.id.*;
import dev.everydaythings.graph.frame.BindingTarget;
import dev.everydaythings.graph.frame.FrameBodyOld;
import dev.everydaythings.graph.frame.FrameRecordOld;
import dev.everydaythings.graph.language.Language;
import dev.everydaythings.graph.language.Posting;
import dev.everydaythings.graph.language.GrammaticalFeature;
import dev.everydaythings.graph.language.PartOfSpeech;
import dev.everydaythings.graph.language.SememeGloss;
import dev.everydaythings.graph.language.CoreVocabulary;
import dev.everydaythings.graph.language.ThematicRole;
import dev.everydaythings.graph.library.dictionary.TokenDictionary;
import dev.everydaythings.graph.library.dictionary.TokenExtractor;
import dev.everydaythings.graph.library.directory.ItemDirectory;
import dev.everydaythings.graph.crypt.AtRestEncryption;
import dev.everydaythings.graph.library.mapdb.*;
import dev.everydaythings.graph.library.rocksdb.*;
import dev.everydaythings.graph.library.skiplist.*;
import dev.everydaythings.graph.runtime.LibrarianOld;
import dev.everydaythings.graph.crypt.Vault;
import lombok.Getter;
import lombok.extern.log4j.Log4j2;

import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Library provides ALL local storage and indexing.
 *
 * <p>Library owns FIVE parts:
 * <ol>
 *   <li><b>Primary ItemStore</b>: Unified object store (manifests, frame bodies, records, content)</li>
 *   <li><b>Store Registry</b>: Additional stores in priority order</li>
 *   <li><b>LibraryIndex</b>: Frame fan-outs for queries, head tracking</li>
 *   <li><b>ItemDirectory</b>: Fast item location (which store has item X?)</li>
 *   <li><b>TokenDictionary</b>: Human text -> item resolution</li>
 * </ol>
 *
 * <p>Create using factory methods:
 * <ul>
 *   <li>{@link #memory()} - In-memory (SkipList backend, fast, zero deps)</li>
 *   <li>{@link #file(Path)} - Persistent (RocksDB backend, production)</li>
 *   <li>{@link #mapdb(Path)} - Persistent (MapDB backend, lightweight)</li>
 *   <li>{@link #mapdbMemory()} - In-memory (MapDB backend)</li>
 * </ul>
 *
 * <p>Example:
 * <pre>{@code
 * // In-memory for tests
 * try (Library lib = Library.memory()) {
 *     lib.storeFrameBody(myFrameBody);
 *     lib.byItemPredicate(subject, predicate);
 * }
 *
 * // Persistent for production
 * try (Library lib = Library.file(Paths.get("~/.common-graph/library"))) {
 *     // ...
 * }
 * }</pre>
 */
@Log4j2
@Implements(LibraryOld.KEY)
@ItemSeed(key = LibraryOld.KEY)
public final class LibraryOld implements Canonical, AutoCloseable {

    public static final String KEY = "cg.sememe:library";

    @ItemFrame(predicate = SememeGloss.KEY, fieldAs = @ItemFrame.Bind(role = ThematicRole.Value.KEY, qualifiers = {Language.ENGLISH_KEY}))
    static final String seedGloss = "local storage for items";

    @ItemFrame(predicate = CoreVocabulary.Lexeme.KEY, fieldAs = @ItemFrame.Bind(role = ThematicRole.Value.KEY, qualifiers = {Language.ENGLISH_KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}, index = true))
    static final String seedNoun = "library";

    // ==================================================================================
    // Backend Selection
    // ==================================================================================

    /**
     * Storage backend type.
     */
    public enum Backend {
        /** Pure Java in-memory (ConcurrentSkipListMap). Fast, zero dependencies. */
        SKIPLIST,

        /** MapDB backend. Supports both in-memory and file-based. */
        MAPDB,

        /** RocksDB backend. File-based only, production-grade. */
        ROCKS
    }

    // ==================================================================================
    // Fields
    // ==================================================================================

    @Getter
    @Canonical.Canon(order = 0)
    private final Backend backend;

    @Getter
    @Canonical.Canon(order = 1)
    private final Path rootPath;  // null for in-memory

    // Store registry (primary + additional stores)
    private final List<ItemStore> stores = new CopyOnWriteArrayList<>();

    // The 5 parts of a Library:
    @Canonical.Canon(order = 2)
    private final ItemStore store;           // Part 1: Primary store

    @Canonical.Canon(order = 3)
    private final LibraryIndex index;        // Part 3: Frame queries

    @Canonical.Canon(order = 4)
    private final ItemDirectory directory;   // Part 4: Item locations

    @Canonical.Canon(order = 5)
    private final TokenDictionary tokenDict; // Part 5: Text lookup

    // At-rest encryption (null if not encrypted)
    private AtRestEncryption atRestEncryption;

    // Librarian reference (set after construction)
    private LibrarianOld librarian;

    // ==================================================================================
    // Factory Methods
    // ==================================================================================

    /**
     * Create an in-memory library using SkipList backend.
     *
     * <p>Fast, zero dependencies. Perfect for unit tests.
     *
     * @return A new in-memory Library
     */
    @Factory(label = "In-Memory", glyph = "⚡",
             doc = "Fast, zero dependencies. Data lost on restart.")
    public static LibraryOld memory() {
        return new LibraryOld(Backend.SKIPLIST, null);
    }

    /**
     * Create a persistent library using RocksDB backend.
     *
     * <p>Production-grade persistent storage.
     *
     * @param rootPath The directory to store databases in
     * @return A new file-backed Library
     */
    @Factory(label = "Persistent (RocksDB)", glyph = "💾", primary = true,
             doc = "Production-grade persistent storage.")
    public static LibraryOld file(Path rootPath) {
        return new LibraryOld(Backend.ROCKS, rootPath);
    }

    /**
     * Create a persistent library using MapDB backend.
     *
     * <p>Lightweight alternative to RocksDB.
     *
     * @param rootPath The directory to store databases in
     * @return A new file-backed Library
     */
    @Factory(label = "MapDB (Persistent)", glyph = "📦",
             doc = "Lightweight persistent storage.")
    public static LibraryOld mapdb(Path rootPath) {
        return new LibraryOld(Backend.MAPDB, rootPath);
    }

    /**
     * Create an in-memory library using MapDB backend.
     *
     * <p>Use when you need MapDB-specific features in memory.
     *
     * @return A new in-memory Library
     */
    @Factory(label = "MapDB (In-Memory)", glyph = "📦",
             doc = "MapDB features without persistence.")
    public static LibraryOld mapdbMemory() {
        return new LibraryOld(Backend.MAPDB, null);
    }

    // --- Encrypted variants ---

    /**
     * Create an in-memory library with at-rest encryption.
     *
     * <p>Values are encrypted with AES-256-GCM. The encryption key is derived
     * from the Vault's encryption key via HKDF. Key is zeroized on close.
     *
     * @param vault Vault with X25519 encryption key for key derivation
     * @return A new encrypted in-memory Library
     */
    public static LibraryOld memoryEncrypted(Vault vault) {
        return new LibraryOld(Backend.SKIPLIST, null, vault);
    }

    /**
     * Create a persistent library with at-rest encryption.
     *
     * @param rootPath The directory to store databases in
     * @param vault    Vault with X25519 encryption key for key derivation
     * @return A new encrypted file-backed Library
     */
    public static LibraryOld fileEncrypted(Path rootPath, Vault vault) {
        return new LibraryOld(Backend.ROCKS, rootPath, vault);
    }

    /**
     * Create a persistent MapDB library with at-rest encryption.
     *
     * @param rootPath The directory to store databases in
     * @param vault    Vault with X25519 encryption key for key derivation
     * @return A new encrypted MapDB Library
     */
    public static LibraryOld mapdbEncrypted(Path rootPath, Vault vault) {
        return new LibraryOld(Backend.MAPDB, rootPath, vault);
    }

    // ==================================================================================
    // Constructor
    // ==================================================================================

    private LibraryOld(Backend backend, Path rootPath) {
        this(backend, rootPath, null);
    }

    private LibraryOld(Backend backend, Path rootPath, Vault encryptionVault) {
        this.backend = backend;
        this.rootPath = rootPath;

        // Derive at-rest encryption key if vault provided
        if (encryptionVault != null) {
            this.atRestEncryption = AtRestEncryption.fromVault(encryptionVault);
            logger.debug("Creating Library: backend={}, path={}, encrypted=true", backend, rootPath);
        } else {
            logger.debug("Creating Library: backend={}, path={}", backend, rootPath);
        }

        // Create backend-specific components
        ItemStore theStore;
        LibraryIndex theIndex;
        ItemDirectory theDirectory;
        TokenDictionary theTokenDict;

        switch (backend) {
            case SKIPLIST -> {
                theStore = SkipListItemStore.create();
                theIndex = SkipListLibraryIndex.create();
                theDirectory = SkipListItemDirectory.create();
                theTokenDict = SkipListTokenDictionary.create();

                // Register store with directory (using synthetic path for in-memory)
                theDirectory.registerStore(Path.of("/memory/store"), theStore);
            }
            case MAPDB -> {
                if (rootPath != null) {
                    theStore = MapDBItemStore.file(rootPath.resolve("store.mapdb"));
                    theIndex = MapDBLibraryIndex.file(rootPath.resolve("index.mapdb"));
                    theDirectory = MapDBItemDirectory.file(rootPath.resolve("directory.mapdb"));
                    theTokenDict = MapDBTokenDictionary.file(rootPath.resolve("token.mapdb"));
                } else {
                    theStore = MapDBItemStore.memory();
                    theIndex = MapDBLibraryIndex.memory();
                    theDirectory = MapDBItemDirectory.memory();
                    theTokenDict = MapDBTokenDictionary.memory();

                    // Register store with directory (using synthetic path for in-memory)
                    theDirectory.registerStore(Path.of("/memory/store"), theStore);
                }
            }
            case ROCKS -> {
                Objects.requireNonNull(rootPath, "RocksDB backend requires a path");
                theStore = RocksItemStore.open(rootPath.resolve("store.rocks"));
                theIndex = RocksLibraryIndex.open(rootPath.resolve("index.rocks"));
                theDirectory = RocksItemDirectory.open(rootPath.resolve("directory.rocks"));
                theTokenDict = RocksTokenDictionary.open(rootPath.resolve("token.rocks"));

                // Register store in directory's registry
                Path storePath = rootPath.resolve("store.rocks");
                theDirectory.registerStore(storePath, theStore);
            }
            default -> throw new IllegalArgumentException("Unknown backend: " + backend);
        }

        // Enable at-rest encryption on the store if configured
        if (atRestEncryption != null) {
            theStore.enableEncryption(atRestEncryption);
        }

        this.store = theStore;
        this.index = theIndex;
        this.directory = theDirectory;
        this.tokenDict = theTokenDict;

        // Register primary store
        stores.add(store);

        logger.debug("Library ready: backend={}", backend);
    }

    // ==================================================================================
    // Librarian Reference
    // ==================================================================================

    /**
     * Set the owning Librarian (called after construction).
     */
    public void setLibrarian(LibrarianOld librarian) {
        this.librarian = librarian;
    }

    /**
     * Get the owning Librarian (for hydration).
     */
    public LibrarianOld librarian() {
        return librarian;
    }

    // ==================================================================================
    // Store Management
    // ==================================================================================

    /**
     * Register a store (appends to end of list).
     */
    public void registerStore(ItemStore store) {
        stores.add(store);
    }

    /**
     * Register a store at a specific position.
     * Use position 0 to make it highest priority.
     */
    public void registerStore(int position, ItemStore store) {
        stores.add(position, store);
    }

    /**
     * Unregister a store.
     */
    public void unregisterStore(ItemStore store) {
        stores.remove(store);
    }

    /**
     * Get all registered stores.
     */
    public List<ItemStore> stores() {
        return List.copyOf(stores);
    }

    /**
     * Get the primary (first) store.
     */
    public Optional<ItemStore> primaryStore() {
        return stores.isEmpty() ? Optional.empty() : Optional.of(stores.getFirst());
    }

    /**
     * Get the primary store (non-Optional, for internal use).
     */
    public ItemStore store() {
        return store;
    }

    /**
     * Get the primary writable store (first writable one).
     */
    public Optional<ItemStore> writableStore() {
        for (ItemStore store : stores) {
            if (store.isWritable()) {
                return Optional.of(store);
            }
        }
        return Optional.empty();
    }

    // ==================================================================================
    // Directory
    // ==================================================================================

    /**
     * Get the item directory.
     *
     * <p>The directory tracks which store contains each item, enabling fast lookups
     * without scanning all stores.
     */
    public Optional<ItemDirectory> directory() {
        return Optional.of(directory);
    }

    // ==================================================================================
    // Token Dictionary
    // ==================================================================================

    /**
     * Get the token dictionary.
     *
     * <p>The token dictionary maps human-readable text (names, titles, keys)
     * to item IDs, enabling lookup by text.
     */
    public Optional<TokenDictionary> tokenDictionary() {
        return Optional.of(tokenDict);
    }

    // ==================================================================================
    // Library Index
    // ==================================================================================

    /**
     * Get the library index.
     *
     * <p>The index provides relation queries and item record tracking.
     * Without an index, query methods will return empty streams.
     */
    public Optional<LibraryIndex> index() {
        return Optional.of(index);
    }

    // ==================================================================================
    // Item Cache
    // ==================================================================================

    // ==================================================================================
    // Store API - Store AND Index together
    // ==================================================================================

    /**
     * Store a frame body: persist body bytes and index.
     *
     * <p>Use this for unsigned frames (e.g., seed vocabulary imports).
     * For signed frames, use {@link #storeFrame(FrameBodyOld, FrameRecordOld)}.
     *
     * @param body The frame body to store
     * @return The content CID of the stored body bytes
     */
    public ContentID storeFrameBody(FrameBodyOld body) {
        ItemStore targetStore = writableStore()
                .orElseThrow(() -> new LibraryException("No writable store available"));
        return storeFrameBody(body, targetStore);
    }

    /**
     * Store a frame body into a specific store, but still index in the library's index.
     *
     * @param body        The frame body to store
     * @param targetStore The store to persist into
     * @return The content CID of the stored body bytes
     */
    public ContentID storeFrameBody(FrameBodyOld body, ItemStore targetStore) {
        // Store the BODY-scope bytes (identity hash, content-addressed)
        ContentID bodyCid = ContentID.of(body.bodyBytes());
        targetStore.runInWriteTransaction(tx -> targetStore.persistContent(body.bodyBytes(), tx));

        // Also store RECORD-scope bytes (includes all bindings, needed for reconstruction)
        byte[] recordBytes = body.encodeBinary(Canonical.Scope.RECORD);
        targetStore.runInWriteTransaction(tx -> targetStore.persistContent(recordBytes, tx));

        // Index in library's index (includes theme as participant)
        index().ifPresent(idx -> {
            idx.runInWriteTransaction(tx -> {
                idx.indexFrameBody(body, bodyCid, tx);
            });
        });

        return bodyCid;
    }

    /**
     * Store a manifest: persist in OBJECTS AND index in ITEMS.
     *
     * <p>This is the ONE path for manifest storage. It stores the manifest
     * bytes in OBJECTS (keyed by VID), indexes the version in ITEMS
     * (IID|VID → timestamp), and indexes all endorsed frames.
     *
     * @param manifest The manifest to store
     * @return The version ID (hash of the body)
     */
    public ContentID manifest(ManifestOld manifest) {
        ItemStore store = writableStore()
                .orElseThrow(() -> new LibraryException("No writable store available"));

        // Store the manifest in OBJECTS (keyed by VID)
        ContentID vid = store.manifest(manifest);

        // Index: ITEMS[IID|VID] → timestamp, endorsed frames
        index().ifPresent(idx -> {
            idx.runInWriteTransaction(tx -> {
                idx.indexVersion(manifest.iid(), vid, System.currentTimeMillis(), tx);

                // Index all endorsed frames for cross-item discovery
                if (manifest.components() != null) {
                    for (var entry : manifest.components()) {
                        idx.indexEndorsedFrame(manifest.iid(), entry, tx);
                    }
                }
            });
        });

        // Register in directory so get(iid) can find this item
        directory().ifPresent(dir -> {
            dir.runInWriteTransaction(tx ->
                    dir.register(manifest.iid(), store, tx));
        });

        return vid;
    }

    // ==================================================================================
    // Frame Storage
    // ==================================================================================

    /**
     * Store a frame: persist body and record in OBJECTS, and index.
     *
     * <p>This is the canonical path for frame storage. Both the body and
     * record are stored content-addressed in OBJECTS (deduped). Both are indexed.
     *
     * @param body   the frame body (semantic assertion)
     * @param record the frame record (signed envelope)
     * @return the record CID
     */
    public ContentID storeFrame(
            FrameBodyOld body,
            FrameRecordOld record) {
        ItemStore targetStore = writableStore()
                .orElseThrow(() -> new LibraryException("No writable store available"));

        // Store body and record in OBJECTS (content-addressed, deduped)
        byte[] recordBytes = record.encodeBinary(dev.everydaythings.graph.Canonical.Scope.RECORD);
        var recordCid = new ContentID[1];
        targetStore.runInWriteTransaction(tx -> {
            targetStore.persistContent(body.bodyBytes(), tx);
            recordCid[0] = targetStore.persistContent(recordBytes, tx);
        });

        // Index the frame (includes theme as participant) and the record
        index().ifPresent(idx -> {
            idx.runInWriteTransaction(tx -> {
                idx.indexFrameBody(body, recordCid[0], tx);
                if (record.signer() != null && record.signer().keyId() != null) {
                    ContentID signerKeyId = ContentID.of(record.signer().keyId());
                    idx.indexRecord(body.hash(), signerKeyId, recordCid[0], tx);
                }
            });
        });

        return recordCid[0];
    }

    /**
     * Import all data from another store into this library.
     *
     * <p>Imports all objects (manifests, frame bodies, records, content) from the source
     * store, storing them in the primary store and indexing appropriately.
     *
     * <p>Manifests are imported before frame bodies so that predicate Sememes
     * are available for data-driven token indexing.
     *
     * @param source The store to import from
     * @param predicateWeightResolver resolves predicate IID → index weight (0 = don't index)
     */
    public void importFrom(ItemStore source, Function<ItemID, Float> predicateWeightResolver) {
        ItemStore primaryStore = writableStore()
                .orElseThrow(() -> new LibraryException("No writable store available"));

        // 1. Store all manifests (so items are locatable by IID)
        List<ManifestOld> allManifests = source.manifests(null).toList();
        logger.info("importFrom: {} manifests to import", allManifests.size());
        for (ManifestOld m : allManifests) {
            logger.debug("importFrom: importing manifest for iid={}, impl={}", m.iid().encodeText(), m.implementationName());
            manifest(m);
            // Register in directory
            directory().ifPresent(dir -> {
                dir.runInWriteTransaction(tx ->
                        dir.register(m.iid(), primaryStore, tx));
            });
            // Manifest token indexing handled by frame-backed indexing pipeline
        }

        // 2. Store all content (needed for item hydration during token indexing)
        List<byte[]> allContent = source.contents().toList();
        logger.info("importFrom: {} content blocks to import", allContent.size());
        for (byte[] bytes : allContent) {
            primaryStore.runInWriteTransaction(tx -> primaryStore.persistContent(bytes, tx));
        }

        // 3. Store all frame bodies AND index tokens
        //    IMPLEMENTED_BY frames first (needed for type hydration during token indexing)
        List<FrameBodyOld> allBodies = source.frameBodies().toList();
        logger.info("importFrom: {} frame bodies to import", allBodies.size());
        ItemID implByPred = CoreVocabulary.ImplementedBy.IID;
        List<FrameBodyOld> deferredBodies = new ArrayList<>();
        for (FrameBodyOld body : allBodies) {
            if (body.predicate().equals(implByPred)) {
                storeFrameBody(body);
            } else {
                deferredBodies.add(body);
            }
        }
        for (FrameBodyOld body : deferredBodies) {
            storeFrameBody(body);
            // Index tokens from this frame body using data-driven predicate weights
            tokenDictionary().ifPresent(tokenDict -> {
                tokenDict.runInWriteTransaction(tx ->
                        tokenDict.indexFromFrameBody(body, predicateWeightResolver, tx));
            });
        }

        // 4. Token indexing from cached items is now done by the Librarian
        //    after bootstrap via indexItemFrames(items).
    }

    /**
     * Index tokens from the given items' frames.
     *
     * <p>Extracts NAME-binding postings from each item's live Frame objects
     * and indexes them in the TokenDictionary.
     *
     * @param items the items whose frames should be indexed
     */
    public void indexItemFrames(Collection<ItemOld> items) {
        tokenDictionary().ifPresent(tokenDict -> {
            if (items == null || items.isEmpty()) return;

            // Ensure frame bodies are in the object store (needed for body-hash resolution)
            for (ItemOld item : items) {
                if (item.frames() != null) {
                    for (var frame : item.frames()) {
                        if (frame.body() != null) {
                            storeFrameBody(frame.body());
                        }
                    }
                }
            }

            tokenDict.runInWriteTransaction(tx -> {
                int count = 0;
                int totalFrames = 0;
                int framesWithBody = 0;
                for (ItemOld item : items) {
                    for (Posting p : TokenExtractor.fromItemFrames(item)) {
                        tokenDict.index(p, tx);
                        count++;
                        logger.debug("Indexed token: '{}' scope={} target={} features={}",
                            p.token(), p.scope() != null ? p.scope().encodeText() : "universal",
                            item.getClass().getSimpleName(), p.features());
                    }
                    if (item.frames() != null) {
                        for (var frame : item.frames()) {
                            totalFrames++;
                            if (frame.body() != null) framesWithBody++;
                        }
                    }
                }
                logger.info("Indexed {} token postings from {} items ({} frames, {} with body)",
                    count, items.size(), totalFrames, framesWithBody);
            });
        });
    }

    // ==================================================================================
    // Frame Query API
    // ==================================================================================

    /**
     * Query frame refs involving a specific item.
     */
    public Stream<LibraryIndex.FrameRef> framesByItem(ItemID item) {
        return index().map(idx -> idx.framesByItem(item)).orElse(Stream.empty());
    }

    /**
     * Query frame refs involving a specific item via a specific predicate.
     */
    public Stream<LibraryIndex.FrameRef> framesByItemPredicate(ItemID item, ItemID predicate) {
        return index().map(idx -> idx.framesByItemPredicate(item, predicate)).orElse(Stream.empty());
    }

    /**
     * Query frame refs by predicate only.
     */
    public Stream<LibraryIndex.FrameRef> framesByPredicate(ItemID predicate) {
        return index().map(idx -> idx.framesByPredicate(predicate)).orElse(Stream.empty());
    }

    /**
     * Query records attesting a specific frame body.
     */
    public Stream<LibraryIndex.RecordRef> recordsByBody(ContentID bodyHash) {
        return index().map(idx -> idx.recordsByBody(bodyHash)).orElse(Stream.empty());
    }

    /**
     * Count independent attestations for a frame body.
     */
    public long attestationCount(ContentID bodyHash) {
        return index().map(idx -> idx.attestationCount(bodyHash)).orElse(0L);
    }

    // ==================================================================================
    // Frame Body Query API (delegates to frame queries)
    // ==================================================================================

    /**
     * Query frame bodies involving a specific item (in any role).
     */
    public Stream<FrameBodyOld> byItem(ItemID item) {
        return framesByItem(item).map(this::hydrateFrameRef).flatMap(Optional::stream);
    }

    /**
     * Query frame bodies involving a specific item via a specific predicate.
     */
    public Stream<FrameBodyOld> byItemPredicate(ItemID item, ItemID predicate) {
        return framesByItemPredicate(item, predicate).map(this::hydrateFrameRef).flatMap(Optional::stream);
    }

    /**
     * Query frame bodies by predicate only.
     */
    public Stream<FrameBodyOld> byPredicate(ItemID predicate) {
        return framesByPredicate(predicate).map(this::hydrateFrameRef).flatMap(Optional::stream);
    }

    /**
     * Find items that co-occur with ALL the given items in the frame index.
     *
     * <p>For each pattern term, collects the home items from frames involving
     * that term. Returns the intersection — items that appear as the subject
     * of frames involving EVERY pattern term. Pattern items themselves are
     * excluded from results.
     *
     * <p>Example: {@code queryItems(chessIID, aliceIID)} finds items that have
     * frames involving the chess sememe AND frames involving Alice — e.g.,
     * chess games where Alice is a player.
     *
     * @param pattern the set of ItemIDs to match against
     * @return items whose frames involve all pattern terms
     */
    public Set<ItemID> queryItems(Set<ItemID> pattern) {
        if (pattern == null || pattern.isEmpty()) return Set.of();

        Set<ItemID> result = null;
        for (ItemID term : pattern) {
            Set<ItemID> homes = byItem(term)
                    .map(FrameBodyOld::homeId)
                    .filter(Objects::nonNull)
                    .filter(id -> !pattern.contains(id))
                    .collect(Collectors.toSet());

            if (result == null) {
                result = homes;
            } else {
                result.retainAll(homes);
            }

            if (result.isEmpty()) return Set.of();
        }
        return result != null ? result : Set.of();
    }

    /**
     * Find items that co-occur with ALL the given items in the frame index.
     *
     * <p>Varargs convenience for {@link #queryItems(Set)}.
     */
    public Set<ItemID> queryItems(ItemID... pattern) {
        return queryItems(new LinkedHashSet<>(Arrays.asList(pattern)));
    }

    /**
     * Hydrate a FrameRef into a FrameBody by looking up the stored bytes.
     *
     * <p>Tries the body hash first (body bytes are always stored at this CID),
     * then falls back to storageCid for backward compatibility. The bodyHash
     * path handles both {@link #storeFrameBody} and {@link #storeFrame} storage
     * paths, since both store the body's BODY-scope bytes at the bodyHash CID.
     */
    private Optional<FrameBodyOld> hydrateFrameRef(LibraryIndex.FrameRef ref) {
        return loadFrameBody(ref.bodyHash())
                .or(() -> store.frameBody(ref.storageCid()));
    }

    /**
     * Load a frame body from the object store by its body hash.
     *
     * <p>Body bytes (BODY-scope encoding) are always stored at the body hash CID.
     * This works for frames stored via both {@link #storeFrameBody} and
     * {@link #storeFrame}.
     *
     * @param bodyHash the body hash (CID of BODY-scope encoded bytes)
     * @return the decoded frame body, or empty if not found
     */
    public Optional<FrameBodyOld> loadFrameBody(ContentID bodyHash) {
        return store.content(bodyHash).map(bytes -> {
            try {
                return Canonical.decodeBinary(bytes, FrameBodyOld.class, Canonical.Scope.BODY);
            } catch (Exception e) {
                return null;
            }
        });
    }

    /**
     * Load a FrameRecord from the object store by its storage CID.
     *
     * @param storageCid the CID of the record's RECORD-scope bytes
     * @return the decoded frame record, or empty if not found or not a record
     */
    public Optional<FrameRecordOld> loadFrameRecord(ContentID storageCid) {
        return store.content(storageCid).map(bytes -> {
            try {
                return Canonical.decodeBinary(bytes, FrameRecordOld.class, Canonical.Scope.RECORD);
            } catch (Exception e) {
                return null;
            }
        });
    }

    /**
     * Load all attestation records for a frame body.
     *
     * @param bodyHash the body hash to find records for
     * @return list of FrameRecords attesting this body
     */
    public List<FrameRecordOld> loadRecords(ContentID bodyHash) {
        List<FrameRecordOld> result = new ArrayList<>();
        recordsByBody(bodyHash).forEach(ref ->
                loadFrameRecord(ref.storageCid()).ifPresent(result::add));
        return result;
    }

    // ==================================================================================
    // Item Record Access (for hydration)
    // ==================================================================================

    /**
     * Find the latest version ID for an item from the ITEMS index.
     *
     * @param iid The item ID
     * @return The latest VID, or empty if no versions exist
     */
    public Optional<ContentID> latestVersion(ItemID iid) {
        return index().flatMap(idx -> idx.latestVersion(iid));
    }

    /**
    // ==================================================================================
    // Item Access
    // ==================================================================================

    /**
     * Get an item by ID, hydrating from the stored manifest.
     *
     * <p>The process:
     * <ol>
     *   <li>Check directory for item location</li>
     *   <li>Check directory for fast location lookup</li>
     *   <li>Look up VID from index using IID</li>
     *   <li>Retrieve manifest bytes from store using VID</li>
     *   <li>Decode manifest</li>
     *   <li>Find the Item implementation class for the manifest's type</li>
     *   <li>Instantiate via hydration constructor (Librarian, Manifest)</li>
     *   <li>Cache the result</li>
     * </ol>
     *
     * @param iid The item ID to retrieve
     * @return The hydrated item, or empty if not found
     */
    public Optional<ItemOld> get(ItemID iid) {
        // If directory exists, check if item is known
        Optional<ItemDirectory> dir = directory();
        if (dir.isPresent()) {
            Optional<ItemDirectory.Entry> entry = dir.get().locate(iid);
            if (entry.isEmpty()) {
                return Optional.empty();  // Not in directory = not in our stores
            }
        }

        // Get latest VID from ITEMS index
        Optional<ContentID> vidOpt = latestVersion(iid);
        if (vidOpt.isEmpty()) {
            return Optional.empty();
        }

        ContentID vid = vidOpt.get();

        // Find the store that has this item
        ItemStore store = null;
        if (dir.isPresent()) {
            Optional<ItemDirectory.Entry> entry = dir.get().locate(iid);
            if (entry.isPresent() && entry.get().location() instanceof ItemDirectory.InStore inStore) {
                store = inStore.store();
            }
        }
        if (store == null) {
            store = primaryStore().orElse(null);
        }
        if (store == null) {
            return Optional.empty();
        }

        // Get manifest from store (consumer API)
        Optional<ManifestOld> manifestOpt = store.manifest(iid, vid);
        if (manifestOpt.isEmpty()) {
            return Optional.empty();
        }
        ManifestOld manifest = manifestOpt.get();

        // PHASE 6: resolve implementation class directly from manifest
        Class<? extends ItemOld> itemClass = ItemOld.class;
        Class<?> implClass = manifest.implementationClass();
        if (implClass != null && ItemOld.class.isAssignableFrom(implClass)) {
            itemClass = implClass.asSubclass(ItemOld.class);
        }

        // Instantiate via hydration constructor (Librarian, Manifest)
        try {
            java.lang.reflect.Constructor<? extends ItemOld> ctor =
                    itemClass.getDeclaredConstructor(LibrarianOld.class, ManifestOld.class);
            ctor.setAccessible(true);
            ItemOld item = ctor.newInstance(librarian, manifest);
            // Caching is handled by the Librarian
            return Optional.of(item);
        } catch (NoSuchMethodException e) {
            // Try base Item as fallback
            if (itemClass != ItemOld.class) {
                try {
                    java.lang.reflect.Constructor<ItemOld> baseCtor =
                            ItemOld.class.getDeclaredConstructor(LibrarianOld.class, ManifestOld.class);
                    baseCtor.setAccessible(true);
                    ItemOld item = baseCtor.newInstance(librarian, manifest);
                    // Caching is handled by the Librarian
                    return Optional.of(item);
                } catch (Exception e2) {
                    // Fall through
                }
            }
            return Optional.empty();
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    // ==================================================================================
    // Type Resolution
    // ==================================================================================

    /**
     * Find the implementing Java class for a type ID.
     *
     * <p>Queries the library's indexes for IMPLEMENTED_BY relations from
     * the type item. This is useful when the type item isn't loaded but
     * relations are indexed.
     *
     * @param typeId The type's ItemID
     * @return The implementing Java class, or empty if not found
     */
    public Optional<Class<?>> findImplementation(ItemID typeId) {
        return byItemPredicate(typeId, CoreVocabulary.ImplementedBy.IID)
                .findFirst()
                .map(body -> {
                    BindingTarget target = body.binding(ItemID.fromString("cg.role:goal"));
                    if (target instanceof Literal lit) {
                        return lit.asJavaClass();
                    }
                    return null;
                });
    }

    /**
     * Find the implementing class for a component/frame type ID.
     *
     * @param typeId The type's ItemID
     * @return The implementing class, or empty if not found
     */
    public Optional<Class<?>> findComponentImplementation(ItemID typeId) {
        return findImplementation(typeId);
    }

    /**
     * Find the implementing Item class for a type ID.
     *
     * @param typeId The item type's ItemID
     * @return The implementing Item class, or empty if not found
     */
    @SuppressWarnings("unchecked")
    public Optional<Class<? extends ItemOld>> findItemImplementation(ItemID typeId) {
        return findImplementation(typeId)
                .filter(ItemOld.class::isAssignableFrom)
                .map(c -> (Class<? extends ItemOld>) c);
    }

    /**
     * Find the implementing Value class for a value type ID.
     *
     * @param typeId The value type's ItemID
     * @return The implementing Value class, or empty if not found
     */
    @SuppressWarnings("unchecked")
    public Optional<Class<? extends dev.everydaythings.graph.value.Value>> findValueImplementation(ItemID typeId) {
        return findImplementation(typeId)
                .filter(dev.everydaythings.graph.value.Value.class::isAssignableFrom)
                .map(c -> (Class<? extends dev.everydaythings.graph.value.Value>) c);
    }

    // ==================================================================================
    // Write Operations (delegate to primary writable store)
    // ==================================================================================

    /**
     * Run a write transaction on the primary writable store.
     */
    public void runInWriteTransaction(Consumer<WriteTransaction> work) {
        ItemStore writable = writableStore()
                .orElseThrow(() -> new IllegalStateException("No writable store available"));
        writable.runInWriteTransaction(work);
    }

    /**
     * Get content from stores (tries in priority order).
     */
    public byte[] content(ContentID cid) {
        for (ItemStore store : stores) {
            byte[] data = store.retrieveContent(cid);
            if (data != null) return data;
        }
        return null;
    }

    // ==================================================================================
    // Lifecycle
    // ==================================================================================

    @Override
    public void close() {
        logger.debug("Closing Library: backend={}, path={}", backend, rootPath);
        // Close in reverse order of creation
        try { tokenDict.close(); } catch (Exception ignore) {}
        try { directory.close(); } catch (Exception ignore) {}
        try { index.close(); } catch (Exception ignore) {}
        try { store.close(); } catch (Exception ignore) {}
        stores.clear();

        // Zeroize at-rest encryption key material
        if (atRestEncryption != null) {
            atRestEncryption.destroy();
            logger.debug("At-rest encryption key zeroized");
        }
    }

    /**
     * Check if at-rest encryption is enabled.
     */
    public boolean isEncrypted() {
        return atRestEncryption != null && !atRestEncryption.isDestroyed();
    }

    // ==================================================================================
    // Display
    // ==================================================================================

    public String displaySubtitle() {
        // Show backend and path for useful context
        if (rootPath != null) {
            String pathStr = rootPath.toString();
            if (pathStr.length() > 40) {
                pathStr = "..." + pathStr.substring(pathStr.length() - 37);
            }
            return backend.name().toLowerCase() + " • " + pathStr;
        }
        return backend.name().toLowerCase() + " • in-memory";
    }

    @Override
    public String toString() {
        return "Library{" + backend.name().toLowerCase() +
               (rootPath != null ? ", " + rootPath : ", in-memory") + "}";
    }
}
