package dev.everydaythings.graph.library;

import dev.everydaythings.graph.Canonical;
import dev.everydaythings.graph.item.Manifest;
import dev.everydaythings.graph.item.Factory;
import dev.everydaythings.graph.item.Param;
import dev.everydaythings.graph.item.Picker;
import dev.everydaythings.graph.item.Type;
import dev.everydaythings.graph.item.Item;
import dev.everydaythings.graph.item.id.*;
import dev.everydaythings.graph.item.Literal;
import dev.everydaythings.graph.frame.BindingTarget;
import dev.everydaythings.graph.frame.FrameBody;
import dev.everydaythings.graph.language.Language;
import dev.everydaythings.graph.language.Posting;
import dev.everydaythings.graph.language.Sememe;
import dev.everydaythings.graph.language.VerbSememe;
import dev.everydaythings.graph.library.dictionary.TokenDictionary;
import dev.everydaythings.graph.library.dictionary.TokenExtractor;
import dev.everydaythings.graph.library.directory.ItemDirectory;
import dev.everydaythings.graph.crypt.AtRestEncryption;
import dev.everydaythings.graph.library.mapdb.*;
import dev.everydaythings.graph.library.rocksdb.*;
import dev.everydaythings.graph.library.skiplist.*;
import dev.everydaythings.graph.runtime.Librarian;
import dev.everydaythings.graph.crypt.Vault;
import lombok.Getter;
import lombok.extern.log4j.Log4j2;

import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Stream;

/**
 * Library provides ALL local storage and indexing.
 *
 * <p>Library owns FIVE parts:
 * <ol>
 *   <li><b>Primary ItemStore</b>: Block storage (manifests, relations, content)</li>
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
@Type(value = "cg:type/library", glyph = "📚")
public final class Library implements Canonical, AutoCloseable {

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
    private Librarian librarian;

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
    public static Library memory() {
        return new Library(Backend.SKIPLIST, null);
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
    public static Library file(
            @Param(label = "Storage Directory", doc = "Directory to store library data",
                   picker = Picker.DIRECTORY) Path rootPath) {
        return new Library(Backend.ROCKS, rootPath);
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
    public static Library mapdb(
            @Param(label = "Storage Directory", doc = "Directory to store library data",
                   picker = Picker.DIRECTORY) Path rootPath) {
        return new Library(Backend.MAPDB, rootPath);
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
    public static Library mapdbMemory() {
        return new Library(Backend.MAPDB, null);
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
    public static Library memoryEncrypted(Vault vault) {
        return new Library(Backend.SKIPLIST, null, vault);
    }

    /**
     * Create a persistent library with at-rest encryption.
     *
     * @param rootPath The directory to store databases in
     * @param vault    Vault with X25519 encryption key for key derivation
     * @return A new encrypted file-backed Library
     */
    public static Library fileEncrypted(Path rootPath, Vault vault) {
        return new Library(Backend.ROCKS, rootPath, vault);
    }

    /**
     * Create a persistent MapDB library with at-rest encryption.
     *
     * @param rootPath The directory to store databases in
     * @param vault    Vault with X25519 encryption key for key derivation
     * @return A new encrypted MapDB Library
     */
    public static Library mapdbEncrypted(Path rootPath, Vault vault) {
        return new Library(Backend.MAPDB, rootPath, vault);
    }

    // ==================================================================================
    // Constructor
    // ==================================================================================

    private Library(Backend backend, Path rootPath) {
        this(backend, rootPath, null);
    }

    private Library(Backend backend, Path rootPath, Vault encryptionVault) {
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
    public void setLibrarian(Librarian librarian) {
        this.librarian = librarian;
    }

    /**
     * Get the owning Librarian (for hydration).
     */
    public Librarian librarian() {
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

    /**
     * Get the item cache, if caching is enabled.
     *
     * <p>The cache holds already-instantiated Items by IID. This avoids
     * re-hydration for frequently accessed items and keeps seed items live.
     *
     * @return The cache map, or empty if caching is disabled
     */
    public Optional<Map<ItemID, Item>> itemCache() {
        return Optional.of(DefaultCache.INSTANCE);
    }

    /**
     * Cache an item.
     *
     * <p>Adds the item to the cache if caching is enabled.
     *
     * @param item The item to cache
     */
    public void cache(Item item) {
        itemCache().ifPresent(cache -> cache.put(item.iid(), item));
    }

    /**
     * Get an item from the cache only (no hydration).
     *
     * @param iid The item ID
     * @return The cached item, or empty if not cached
     */
    public Optional<Item> getCached(ItemID iid) {
        return itemCache().map(cache -> cache.get(iid));
    }

    /**
     * Remove an item from the cache.
     *
     * @param iid The item ID to remove
     */
    public void uncache(ItemID iid) {
        itemCache().ifPresent(cache -> cache.remove(iid));
    }

    /**
     * Clear the entire cache.
     */
    public void clearCache() {
        itemCache().ifPresent(Map::clear);
    }

    /**
     * Default shared cache for Library implementations.
     * Using a class holder for lazy initialization.
     */
    private static final class DefaultCache {
        static final Map<ItemID, Item> INSTANCE = new ConcurrentHashMap<>();
        private DefaultCache() {}
    }

    // ==================================================================================
    // Store API - Store AND Index together
    // ==================================================================================

    /**
     * Store a frame body: persist body bytes and index.
     *
     * <p>Use this for unsigned frames (e.g., seed vocabulary imports).
     * For signed frames, use {@link #storeFrame(FrameBody, dev.everydaythings.graph.frame.FrameRecord)}.
     *
     * @param body The frame body to store
     * @return The content CID of the stored body bytes
     */
    public ContentID storeFrameBody(FrameBody body) {
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
    public ContentID storeFrameBody(FrameBody body, ItemStore targetStore) {
        // Store the body bytes (content-addressed)
        ContentID bodyCid = ContentID.of(body.bodyBytes());
        targetStore.runInWriteTransaction(tx -> targetStore.persistContent(body.bodyBytes(), tx));

        // Index in library's index
        index().ifPresent(idx -> {
            idx.runInWriteTransaction(tx -> {
                idx.indexFrame(body.predicate(), body.bindings(), body.hash(), bodyCid, tx);
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
    public ContentID manifest(Manifest manifest) {
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
            dev.everydaythings.graph.frame.FrameBody body,
            dev.everydaythings.graph.frame.FrameRecord record) {
        ItemStore targetStore = writableStore()
                .orElseThrow(() -> new LibraryException("No writable store available"));

        // Store body and record in OBJECTS (content-addressed, deduped)
        byte[] recordBytes = record.encodeBinary(dev.everydaythings.graph.Canonical.Scope.RECORD);
        var recordCid = new ContentID[1];
        targetStore.runInWriteTransaction(tx -> {
            targetStore.persistContent(body.bodyBytes(), tx);
            recordCid[0] = targetStore.persistContent(recordBytes, tx);
        });

        // Index the frame and the record
        index().ifPresent(idx -> {
            idx.runInWriteTransaction(tx -> {
                idx.indexFrame(body.predicate(), body.bindings(),
                        body.hash(), recordCid[0], tx);
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
     * <p>Imports all blocks (relations, manifests, payloads) from the source store,
     * storing them in the primary store and indexing appropriately.
     *
     * <p>Manifests are imported before relations so that predicate Sememes
     * are available for data-driven token indexing.
     *
     * @param source The store to import from
     * @param predicateWeightResolver resolves predicate IID → index weight (0 = don't index)
     */
    public void importFrom(ItemStore source, Function<ItemID, Float> predicateWeightResolver) {
        ItemStore primaryStore = writableStore()
                .orElseThrow(() -> new LibraryException("No writable store available"));

        // 1. Store all manifests (so items are locatable by IID)
        List<Manifest> allManifests = source.manifests(null).toList();
        logger.info("importFrom: {} manifests to import", allManifests.size());
        for (Manifest m : allManifests) {
            logger.debug("importFrom: importing manifest for iid={}, type={}", m.iid().encodeText(), m.type());
            manifest(m);
            // Register in directory
            directory().ifPresent(dir -> {
                dir.runInWriteTransaction(tx ->
                        dir.register(m.iid(), primaryStore, tx));
            });
            // Index tokens from manifest (component handles)
            tokenDictionary().ifPresent(tokenDict -> {
                tokenDict.runInWriteTransaction(tx ->
                        tokenDict.indexFromManifest(m, tx));
            });
        }

        // 2. Store all content (needed for item hydration during token indexing)
        List<byte[]> allContent = source.contents().toList();
        logger.info("importFrom: {} content blocks to import", allContent.size());
        for (byte[] bytes : allContent) {
            primaryStore.runInWriteTransaction(tx -> primaryStore.persistContent(bytes, tx));
        }

        // 3. Store all frame bodies AND index tokens
        //    IMPLEMENTED_BY frames first (needed for type hydration during token indexing)
        List<FrameBody> allBodies = source.relations().toList();
        logger.info("importFrom: {} frame bodies to import", allBodies.size());
        ItemID implByPred = VerbSememe.ImplementedBy.SEED.iid();
        List<FrameBody> deferredBodies = new ArrayList<>();
        for (FrameBody body : allBodies) {
            if (body.predicate().equals(implByPred)) {
                storeFrameBody(body);
            } else {
                deferredBodies.add(body);
            }
        }
        for (FrameBody body : deferredBodies) {
            storeFrameBody(body);
            // Index tokens from this frame body using data-driven predicate weights
            tokenDictionary().ifPresent(tokenDict -> {
                tokenDict.runInWriteTransaction(tx ->
                        tokenDict.indexFromFrameBody(body, predicateWeightResolver, tx));
            });
        }

        // 4. Index seed Sememe tokens (for vocabulary lookups)
        // Seed tokens are English lexemes, scoped to the English Language Item.
        // This enables verb dispatch: "create"/"new"/"make" → CREATE Sememe
        tokenDictionary().ifPresent(tokenDict -> {
            tokenDict.runInWriteTransaction(tx -> {
                for (Sememe sememe : Sememe.sememesWithTokens()) {
                    List<Posting> postings = TokenExtractor.fromSememe(sememe, Language.ENGLISH);
                    for (Posting p : postings) {
                        tokenDict.index(p, tx);
                    }
                }
            });
            logger.debug("importFrom: indexed tokens for {} seed Sememes", Sememe.sememesWithTokens().size());
        });

        // 5. Index seed Item tokens (for unit resolution, type lookups, etc.)
        // Most are English names/labels, scoped to the English Language Item.
        // Language items get special handling: 3-letter codes are universal postings.
        tokenDictionary().ifPresent(tokenDict -> {
            List<Item> seedItems = SeedVocabulary.seedItemsWithTokens();
            tokenDict.runInWriteTransaction(tx -> {
                for (Item item : seedItems) {
                    if (item instanceof Language lang && lang.languageCode() != null) {
                        // Language codes are universal (resolve for everyone)
                        tokenDict.index(Posting.universal(lang.languageCode(), item.iid()), tx);
                    }
                    item.extractTokens().forEach(entry -> {
                        Posting p = Posting.scoped(entry.token(), Language.ENGLISH, item.iid(), entry.weight());
                        tokenDict.index(p, tx);
                    });
                }
            });
            logger.debug("importFrom: indexed tokens for {} seed Items", seedItems.size());
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
    public Stream<FrameBody> byItem(ItemID item) {
        return framesByItem(item).map(this::hydrateFrameRef).flatMap(Optional::stream);
    }

    /**
     * Query frame bodies involving a specific item via a specific predicate.
     */
    public Stream<FrameBody> byItemPredicate(ItemID item, ItemID predicate) {
        return framesByItemPredicate(item, predicate).map(this::hydrateFrameRef).flatMap(Optional::stream);
    }

    /**
     * Query frame bodies by predicate only.
     */
    public Stream<FrameBody> byPredicate(ItemID predicate) {
        return framesByPredicate(predicate).map(this::hydrateFrameRef).flatMap(Optional::stream);
    }

    /**
     * Hydrate a FrameRef into a FrameBody by looking up the stored bytes.
     *
     * <p>The storageCid in the FrameRef is used to look up the body bytes.
     * Returns empty if the storageCid doesn't correspond to a stored frame body
     * (e.g., it's an endorsed component frame, not a relation).
     */
    private Optional<FrameBody> hydrateFrameRef(LibraryIndex.FrameRef ref) {
        return store.relation(ref.storageCid());
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
     * Get the opaque record bytes (typically VID) for an item.
     *
     * @deprecated Use {@link #latestVersion(ItemID)}
     */
    @Deprecated
    public Optional<byte[]> getItemRecord(ItemID iid) {
        return latestVersion(iid).map(ContentID::encodeBinary);
    }

    // ==================================================================================
    // Item Access
    // ==================================================================================

    /**
     * Get an item by ID, hydrating from the stored manifest.
     *
     * <p>The process:
     * <ol>
     *   <li>Check cache for already-instantiated item</li>
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
    public Optional<Item> get(ItemID iid) {
        // Fast path: check cache first
        Optional<Item> cached = getCached(iid);
        if (cached.isPresent()) {
            return cached;
        }

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
        Optional<Manifest> manifestOpt = store.manifest(iid, vid);
        if (manifestOpt.isEmpty()) {
            return Optional.empty();
        }
        Manifest manifest = manifestOpt.get();

        // Find the Item implementation class for the manifest's type
        ItemID typeId = manifest.type();
        if (typeId == null) {
            typeId = ItemID.fromString(Item.KEY);  // Default to base Item type
        }

        Class<? extends Item> itemClass = findItemImplementation(typeId)
                .orElse(Item.class);

        // Instantiate via hydration constructor (Librarian, Manifest)
        try {
            java.lang.reflect.Constructor<? extends Item> ctor =
                    itemClass.getDeclaredConstructor(Librarian.class, Manifest.class);
            ctor.setAccessible(true);
            Item item = ctor.newInstance(librarian, manifest);
            cache(item);  // Cache for future lookups
            return Optional.of(item);
        } catch (NoSuchMethodException e) {
            // Try base Item as fallback
            if (itemClass != Item.class) {
                try {
                    java.lang.reflect.Constructor<Item> baseCtor =
                            Item.class.getDeclaredConstructor(Librarian.class, Manifest.class);
                    baseCtor.setAccessible(true);
                    Item item = baseCtor.newInstance(librarian, manifest);
                    cache(item);  // Cache for future lookups
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
        return byItemPredicate(typeId, VerbSememe.ImplementedBy.SEED.iid())
                .findFirst()
                .map(body -> {
                    BindingTarget target = body.binding(ItemID.fromString("cg.role:target"));
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
    public Optional<Class<? extends Item>> findItemImplementation(ItemID typeId) {
        return findImplementation(typeId)
                .filter(Item.class::isAssignableFrom)
                .map(c -> (Class<? extends Item>) c);
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
