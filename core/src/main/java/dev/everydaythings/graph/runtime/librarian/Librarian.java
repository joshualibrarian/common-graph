package dev.everydaythings.graph.runtime.librarian;


import dev.everydaythings.graph.SchemaVocabulary;

import dev.everydaythings.graph.CoreVocabulary;
import dev.everydaythings.graph.canonical.HashTree;
import dev.everydaythings.graph.canonical.Node;
import dev.everydaythings.graph.cryptography.MultiKey;
import dev.everydaythings.graph.cryptography.VarSig;
import dev.everydaythings.graph.cryptography.vault.InMemoryVault;
import dev.everydaythings.graph.cryptography.vault.JwksFileVault;
import dev.everydaythings.graph.cryptography.vault.Vault;
import dev.everydaythings.graph.datum.*;
import dev.everydaythings.graph.datum.Record;
import dev.everydaythings.graph.cryptography.AlgorithmCache;
import dev.everydaythings.graph.cryptography.algorithm.Signing;
import dev.everydaythings.graph.cryptography.IdentityVocabulary;
import dev.everydaythings.graph.encoding.Encoding;
import dev.everydaythings.graph.encoding.EncodingRegistry;
import dev.everydaythings.graph.item.Item;
import dev.everydaythings.graph.network.parley.Parley;
import dev.everydaythings.graph.network.transport.Transport;
import dev.everydaythings.graph.network.transport.TransportRegistry;
import dev.everydaythings.graph.runtime.*;
import dev.everydaythings.graph.scene.ContextChain;
import dev.everydaythings.graph.item.Manifest;
import dev.everydaythings.graph.library.materialized.MaterializedDataStore;
import dev.everydaythings.graph.item.SeedProcessor;
import dev.everydaythings.graph.ref.CompoundKey;
import dev.everydaythings.graph.ref.ContentRef;
import dev.everydaythings.graph.ref.DatumRef;
import dev.everydaythings.graph.ref.ItemRef;
import dev.everydaythings.graph.cryptography.Signer;
import dev.everydaythings.graph.library.Library;
import dev.everydaythings.graph.library.MatcherOrchestrator;
import dev.everydaythings.graph.library.QueryWalker;
import dev.everydaythings.graph.library.SchemaWalker;
import dev.everydaythings.graph.library.ValidationResult;
import dev.everydaythings.graph.library.index.TokenPosting;
import dev.everydaythings.graph.Seed;
import dev.everydaythings.graph.language.ThematicRole;
import dev.everydaythings.graph.runtime.stage.ItemStage;
import dev.everydaythings.graph.value.UnixEndpoint;
import lombok.Getter;
import lombok.extern.log4j.Log4j2;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The new Librarian — runtime context that mediates access to a node's storage,
 * networking, trust matrix, and signing capabilities.
 *
 * <p>A Librarian is a {@link Signer} (and therefore an Item): it has identity,
 * persists across runs (its identity does, in storage), and signs activity on
 * its own behalf — channel heads, activity logs, its own manifest versions.
 * Items mediated by a Librarian call methods on it; they never reach past it
 * to stores or network directly.
 *
 * <p>The Librarian always has a {@link Library} (its local storage with derived
 * indexes) and optionally a {@code rootPath} indicating its filesystem footprint.
 * For in-memory mode, the rootPath is empty.
 *
 * <p>For the storage architecture, see
 * <a href="../../../../../../../../../docs/storage.md">storage.md</a>.
 */
@Seed.Item(key = Librarian.KEY)
@Seed.Embodies(key = Librarian.CODE_KEY, archetype = Librarian.KEY)
@Log4j2
public class Librarian extends Signer {

    /** Canonical key for Librarian-the-archetype. */
    public static final String KEY = "cg.archetype:librarian";

    /** The archetype IID for Librarian instances. */

    /**
     * Canonical key for the CodeItem representing THIS Java implementation of Librarian.
     *
     * <p>The CodeItem's manifest is minted at bootstrap by
     * {@link Seed.Embodies @Seed.Embodies}'s two-level mode. It carries the class
     * literal as IMPLEMENTATION and endorses one HANDLES frame per
     * {@link Seed.Handler @Handler}-annotated method on this class — attributing the
     * predicate→method-name mapping to this specific implementation. A different
     * Librarian implementation (e.g., Clojure, Python) would have its own CodeItem
     * with its own key and its own HANDLES in its own naming convention.
     */
    public static final String CODE_KEY = "cg.code:librarian-java-default";

    /** IID of the CodeItem for this Java implementation of Librarian. */
    public static final ItemRef CODE_IID = ItemRef.fromString(CODE_KEY);

    @Override
    public ItemRef archetype() {
        return ItemRef.iid(KEY);
    }

    /**
     * The execution substrate this Librarian runs on. Owns the polyglot
     * environment and (eventually) the handler-dispatch primitive. Provided at
     * construction time — Librarians don't create their own Stage.
     */
    @Getter
    private final ItemStage stage;

    /** The local storage manager (always present). Owns the encoder. */
    @Getter
    private final Library library;

    /**
     * Presence handle: holds the exclusive flock on
     * {@code <data-dir>/parley.pid}, guaranteeing no other librarian
     * process operates on this data dir.  Non-null for persistent
     * librarians ({@link #fresh} / {@link #load}); null for ephemeral or
     * anonymous librarians (no data dir to claim).
     */
    private LibrarianPresence presence;

    /**
     * Parley listener attached to this librarian's lifecycle, if any.  Bound
     * by {@link #startInVm(LibrarianOptions)} when the configuration has a
     * data dir (so other clients can attach over the Unix socket).  Closed
     * on {@link #close()} before the library, so new connections stop being
     * accepted before resources are released.
     */
    private Transport.Listener parleyListener;

    /**
     * Filesystem footprint, if any. Empty for in-memory librarians; present for
     * persistent and viewer modes.
     */
    @Getter
    private final Optional<Path> rootPath;


    /**
     * Cache of cryptographic algorithm handles, keyed by COSE id, varsig
     * codec, multikey codec, and sememe IID.  Populated at bootstrap and
     * lazily on first miss thereafter; see {@link AlgorithmCache}.
     */
    private final AlgorithmCache algorithms = new AlgorithmCache();

    /**
     * Codec registry — the set of encodings this librarian knows how to
     * read.  Used by {@code MaterializedDataStore.open} to resolve the
     * {@code .item/codec} IID back to a live {@link dev.everydaythings.graph.encoding.Encoding}
     * instance, and by Parley's codec point-and-grunt to dispatch on
     * incoming codec IIDs.  Pre-populated with CG-CBOR; additional codecs
     * register here as they ship.
     */
    @Getter
    private final EncodingRegistry encodings = EncodingRegistry.defaultRegistry();

    /**
     * Item cache: one canonical instance per IID. {@link #fetchItem} consults this
     * before hydrating from storage; {@link #register} adds explicit entries.
     * {@link Item#commit} auto-registers, so a committed item is always findable.
     */
    private final Map<ItemRef, Item> itemCache = new ConcurrentHashMap<>();

    /**
     * Identity-only constructor — no vault, no signing capability.  Used when
     * hydrating an observed Librarian whose private key isn't local.
     */
    public Librarian(ItemStage stage,
                     ItemRef iid, Library library, Optional<Path> rootPath) {
        super(iid);
        this.stage = Objects.requireNonNull(stage, "stage");
        this.library = Objects.requireNonNull(library, "library");
        this.rootPath = Objects.requireNonNull(rootPath, "rootPath");
        registerSelfIfIdentified();
    }

    /**
     * Vault-supplied constructor — vault, storage, and (optional) filesystem
     * footprint.  IID is derived from the vault's initial signing public key.
     * Constructor binds librarian-self and publishes the four-datum genesis
     * (INCEPTION body+record on the signing track + the Librarian's own
     * item-manifest body+record).  When this returns, the Librarian is a
     * fully-published graph identity.
     *
     * <p>Most callers should use a factory method ({@link #ephemeral(ItemStage)},
     * {@link #fresh(ItemStage, Path)}, {@link #fromVault}) rather than calling
     * this directly.
     */
    public Librarian(ItemStage stage,
                     Vault vault, Library library, Optional<Path> rootPath) {
        super(vault);
        this.stage = Objects.requireNonNull(stage, "stage");
        this.library = Objects.requireNonNull(library, "library");
        this.rootPath = Objects.requireNonNull(rootPath, "rootPath");
        bindLibrarian(this);
        selfIncept();
        registerSelfIfIdentified();
    }

    /**
     * Anonymous constructor — no identity, no vault, no inception.  The
     * Librarian exists purely as a routing / fetch / cache context with no
     * cryptographic standing.  Attempts to commit, sign, or self-incept fail.
     */
    private Librarian(ItemStage stage, Library library) {
        super((ItemRef) null);
        this.stage = Objects.requireNonNull(stage, "stage");
        this.library = Objects.requireNonNull(library, "library");
        this.rootPath = Optional.empty();
        bindLibrarian(this);
        // No registerSelfIfIdentified: anonymous librarian has null iid.
    }

    /**
     * Add this librarian to its own item cache so dispatch's
     * {@link #liveInstanceOf} and {@link ContextChain#findInstanceOf} can
     * find it as a live instance of {@code Librarian.KEY}.  No-op for
     * anonymous librarians (iid is null).
     */
    private void registerSelfIfIdentified() {
        if (iid() != null) register(this);
    }

    // ==================================================================================
    // Encoder convenience — delegates to the Library's encoder
    //
    // Library owns the encoder. These methods exist for callers that hold a
    // Librarian reference and want a one-liner. They throw if the underlying
    // Library has no encoder (i.e., pure-in-memory backends — once those land).
    // ==================================================================================

    /** The encoding this Librarian uses, if any. Delegates to {@link Library#encoder}. */
    public Optional<Encoding> encoder() {
        return library.encoder();
    }

    private Encoding requireEncoder() {
        return library.encoder().orElseThrow(() -> new IllegalStateException(
                "Librarian has no encoder (pure-in-memory Library); encode/decode not available"));
    }

    /** Encode a value to bytes. Throws if the Library has no encoder. */
    public byte[] encode(Object value) {
        return requireEncoder().encode(value);
    }

    /** Decode bytes back to a typed value. Throws if the Library has no encoder. */
    public Object decode(byte[] bytes) {
        return requireEncoder().decode(bytes);
    }

    /** Walk a value as a {@link Node} tree. Throws if no encoder. */
    public Node walk(Object value) {
        return requireEncoder().walk(value);
    }

    // ==================================================================================
    // Factory methods
    // ==================================================================================

    /**
     * Create an ephemeral signed Librarian on the given Stage — full identity
     * and signing capability, everything in memory.
     *
     * <p>Storage is backed by SkipList stores (zero-dependency, pure Java).
     * No filesystem footprint.  Identity is derived from a freshly-generated
     * vault holding two Ed25519 keypairs (current + pre-rotation next).
     * Inception runs during construction.
     *
     * <p>For tests that need signing but not persistence; for one-shot tools
     * that sign transient frames and discard them.
     */
    public static Librarian ephemeral(ItemStage stage) {
        // Still byte-backed: bootstrap + token-indexed parse pipelines rely on
        // the byte-store token dictionary. Once token indexing is wired into
        // PureMapLibrary (blocked on task #48 — Posting.source flip from
        // ContentRef to DatumRef), ephemeral will switch to Library.anonymous().
        return new Librarian(
                stage,
                InMemoryVault.generate(),
                Library.inMemory(),
                Optional.empty());
    }

    /**
     * Ephemeral signed Librarian on a Java-only Stage — the test-and-tooling
     * convenience.  Equivalent to {@code ephemeral(ItemStage.javaOnly())}.
     * Most call sites that don't care about polyglot want this.
     */
    public static Librarian inMemory() {
        return ephemeral(ItemStage.javaOnly());
    }

    /**
     * Create an anonymous Librarian on a Java-only Stage — no identity, no
     * vault, no inception.  Convenience for the test-and-tooling case;
     * equivalent to {@code anonymous(ItemStage.javaOnly())}.
     */
    public static Librarian anonymous() {
        return anonymous(ItemStage.javaOnly());
    }

    /**
     * Create an anonymous Librarian on the given Stage — no identity, no
     * vault, no inception.  The cheapest possible runtime context: storage is
     * in-memory only, nothing gets signed, nothing requires identity.
     *
     * <p>For tests that don't need identity at all, or for one-shot tools that
     * only need to fetch / look up / route without ever attesting anything.
     * Attempting to sign, commit, or self-incept on an anonymous Librarian
     * throws {@link IllegalStateException}.
     */
    public static Librarian anonymous(ItemStage stage) {
        return new Librarian(stage, Library.anonymous());
    }

    /**
     * Create a Librarian from an existing vault — the caller supplies the
     * vault (loaded from disk, hardware-backed, test fixture, etc.) and the
     * Librarian inherits its IID and incepts during construction.
     *
     * <p>Use this when you already hold the vault material; use
     * {@link #ephemeral(ItemStage)} or {@link #fresh(ItemStage, Path)} when
     * you want the Librarian to mint a fresh vault for itself.
     */
    public static Librarian fromVault(ItemStage stage, Vault vault,
                                      Library library, Optional<Path> rootPath) {
        return new Librarian(stage, vault, library, rootPath);
    }

    /**
     * Create a fresh persistent Librarian at the given path on a Java-only
     * Stage.  Convenience for the test-and-tooling case; equivalent to
     * {@code fresh(ItemStage.javaOnly(), path)}.  See {@link
     * #fresh(ItemStage, Path)} for full bring-up details.
     */
    public static Librarian fresh(java.nio.file.Path path) {
        return fresh(ItemStage.javaOnly(), path);
    }

    /**
     * Fresh persistent Librarian at the given path, on the given Stage.
     *
     * <p>Creates {@code <path>/.item/} (materializing the Librarian-as-item:
     * iid, codec) and opens the Library at {@code <path>/library/}.  Refuses
     * if {@code <path>/.item/} already exists — that path already has a
     * Librarian; use {@code load(path)} instead (still pending vault
     * disk-persistence).
     */
    public static Librarian fresh(ItemStage stage, java.nio.file.Path path) {
        Objects.requireNonNull(path, "path");
        if (java.nio.file.Files.isDirectory(path.resolve(".item"))) {
            throw new IllegalStateException(
                    "Path already contains a materialized Librarian at "
                            + path.resolve(".item")
                            + ".  Use Librarian.load(path) instead.");
        }
        // Claim the data dir exclusively before doing any work.  Fails fast
        // if another librarian process is already running here.
        LibrarianPresence presence = LibrarianPresence.acquire(path);
        try {
            // Persist the vault so Librarian.load(path) can re-acquire the same
            // signing identity later.  Vault lives at the data-dir root alongside
            // library/ and (eventually) parley.sock.  The .item/ metadir is for
            // item-content metadata only (iid, codec, head, objects/) — vault is
            // operational state, not content.
            java.nio.file.Path vaultDir = path.resolve("vault");
            dev.everydaythings.graph.cryptography.vault.JwksFileVault vault =
                    dev.everydaythings.graph.cryptography.vault.JwksFileVault.create(vaultDir);
            Librarian librarian = new Librarian(stage, vault, Library.atPath(path), Optional.of(path));
            librarian.presence = presence;
            materializeLibrarian(path, librarian);
            return librarian;
        } catch (RuntimeException e) {
            presence.close();
            throw e;
        }
    }

    /**
     * Materialize the librarian-as-item to its filesystem root via
     * {@link MaterializedDataStore}.  Mints {@code <path>/.item/}, builds the
     * librarian's slim manifest body, signs a MATERIALIZED record, persists
     * both bodies into the store, and sets the boot pointer.
     *
     * <p>The librarian-specific work is purely the manifest construction +
     * signing; the filesystem-layout knowledge lives in MaterializedDataStore.
     */
    private static void materializeLibrarian(Path path, Librarian librarian) {
        Encoding encoding = librarian.encoder()
                .orElseThrow(() -> new IllegalStateException("Librarian has no encoder"));
        try (MaterializedDataStore store = MaterializedDataStore.mint(
                path, librarian.iid(), encoding)) {
            commitMaterializedManifest(librarian, store, encoding);
        } catch (IOException e) {
            throw new UncheckedIOException(
                    "Failed to mint .item/ at " + path.resolve(".item"), e);
        }
    }

    /**
     * Build + sign + persist the librarian's MATERIALIZED manifest into the
     * given store, then update the store's head pointer.  The MATERIALIZED ACT
     * marks the record as a byte-level commitment to the body's exact stored
     * form, not its semantic Merkle identity.  Boot walks this chain back
     * without needing a DatumRef→ContentRef index.
     */
    private static void commitMaterializedManifest(
            Librarian librarian,
            MaterializedDataStore store,
            Encoding encoding) {

        Body body = Body.of(
                Librarian.ARCHETYPE,
                List.of(Binding.ref(Manifest.ITEM_ID, librarian.iid())));

        byte[] bodyBytes = encoding.encode(body);
        ContentRef bodyCid = store.putContent(bodyBytes);

        VarSig sig = librarian.vault().orElseThrow(
                () -> new IllegalStateException(
                        "Librarian has no vault — cannot materialize manifest"))
                .sign(HashTree.signingPayload(body));

        Binding actBinding = Binding.ref(
                ItemRef.iid(dev.everydaythings.graph.cryptography.RecordVocabulary.Act.KEY),
                ItemRef.iid(dev.everydaythings.graph.cryptography.RecordVocabulary.Materialized.KEY));
        Record record = Record.of(bodyCid, List.of(actBinding), sig);

        ContentRef recordCid = store.putContent(encoding.encode(record));
        store.setHead(recordCid);
    }

    /**
     * Walk the materialized manifest at {@code <path>/.item/}: open via
     * {@link MaterializedDataStore#open}, follow head → record → body,
     * verify the record's ACT is MATERIALIZED, return the body.  Signature
     * verification is deferred until index-bootstrap is wired into load
     * (see task #276 follow-up).
     */
    private static Body loadMaterializedManifest(Path path, Encoding encoding) {
        try (MaterializedDataStore store = MaterializedDataStore.open(
                path, dev.everydaythings.graph.encoding.EncodingRegistry.defaultRegistry())) {

            ContentRef recordCid = store.head().orElseThrow(() -> new IllegalStateException(
                    "No .item/head at " + path.resolve(".item")
                            + " — librarian materialization incomplete"));

            byte[] recordBytes = store.getContent(recordCid).orElseThrow(() -> new IllegalStateException(
                    "Materialized record " + recordCid + " is missing from " + path.resolve(".item/objects")));
            Object decodedRecord = encoding.decode(recordBytes);
            if (!(decodedRecord instanceof Record record)) {
                throw new IllegalStateException(
                        ".item/head points at " + recordCid + " which is not a Record");
            }

            ItemRef actRole = ItemRef.iid(dev.everydaythings.graph.cryptography.RecordVocabulary.Act.KEY);
            ItemRef materialized = ItemRef.iid(dev.everydaythings.graph.cryptography.RecordVocabulary.Materialized.KEY);
            boolean isMaterialized = record.bindings().stream()
                    .anyMatch(b -> actRole.equals(b.role()) && materialized.equals(b.target()));
            if (!isMaterialized) {
                throw new IllegalStateException(
                        "Manifest record at " + recordCid + " is not a MATERIALIZED record");
            }

            ContentRef bodyCid = record.contentRefHead();
            byte[] bodyBytes = store.getContent(bodyCid).orElseThrow(() -> new IllegalStateException(
                    "Materialized body " + bodyCid + " is missing from " + path.resolve(".item/objects")));
            Object decodedBody = encoding.decode(bodyBytes);
            if (!(decodedBody instanceof Body body)) {
                throw new IllegalStateException(
                        "Body at " + bodyCid + " is not a Body");
            }
            return body;
        } catch (IOException e) {
            throw new UncheckedIOException(
                    "Failed to load materialized manifest at " + path.resolve(".item"), e);
        }
    }

    /**
     * Load an existing persistent Librarian from the given filesystem path.
     *
     * <p>Reads the {@code .librarian/format} marker, validates the encoder
     * matches the runtime, and opens the byte-backed Library at the path.
     *
     * <p>Vault loading (and therefore re-acquiring the original signing
     * identity) is deferred to a follow-on stage — the design memo flags
     * encrypted-vault disk persistence as a separate concern. Until vault
     * loading lands, {@code load(path)} throws — call {@link #fresh(java.nio.file.Path)}
     * instead, or use {@link #ephemeral()} for transient signing.
     */
    public static Librarian load(java.nio.file.Path path) {
        return load(ItemStage.javaOnly(), path);
    }

    /**
     * Load an existing persistent Librarian from the given filesystem path,
     * on the given Stage.
     *
     * <p>Reads the persisted vault at {@code <path>/.item/vault/keys.jwks} to
     * re-acquire the original signing identity, then opens the byte-backed
     * Library at {@code <path>/library/}.
     *
     * @throws IllegalStateException if no materialized librarian is at the path
     */
    public static Librarian load(ItemStage stage, java.nio.file.Path path) {
        Objects.requireNonNull(path, "path");
        java.nio.file.Path itemDir = path.resolve(".item");
        if (!java.nio.file.Files.isDirectory(itemDir)) {
            throw new IllegalStateException(
                    "No materialized Librarian at " + itemDir
                            + " (use Librarian.fresh(path) to create one).");
        }
        // Claim the data dir exclusively.  Fails fast if another librarian
        // process is already running here.
        LibrarianPresence presence = LibrarianPresence.acquire(path);
        try {
            java.nio.file.Path vaultDir = path.resolve("vault");
            JwksFileVault vault = JwksFileVault.load(vaultDir);
            Librarian librarian = new Librarian(stage, vault, Library.atPath(path), Optional.of(path));
            librarian.presence = presence;

            // Walk the materialized manifest chain — verifies head→record→body
            // shape with the loaded vault's identity.  Throws if the chain
            // doesn't verify.
            Encoding encoding = librarian.encoder()
                    .orElseThrow(() -> new IllegalStateException("Librarian has no encoder"));
            loadMaterializedManifest(path, encoding);

            return librarian;
        } catch (RuntimeException e) {
            presence.close();
            throw e;
        }
    }

    /**
     * The presence handle this librarian holds on its data directory.
     * Empty for ephemeral or anonymous librarians.
     */
    public Optional<LibrarianPresence> presence() {
        return Optional.ofNullable(presence);
    }

    /**
     * The Parley listener attached to this librarian, if one was bound via
     * {@link #setParleyListener(Transport.Listener)} (typically by
     * {@link #startInVm(LibrarianOptions)}).  Null for librarians that
     * never bound a listener (ephemeral, anonymous, in-memory test
     * harnesses).
     */
    public Transport.Listener parleyListener() {
        return parleyListener;
    }

    /**
     * Attach a Parley listener to this librarian's lifecycle.  The listener
     * is closed on {@link #close()} before the library, so new connections
     * are refused before resources go away.  Pass {@code null} to detach.
     */
    public void setParleyListener(Transport.Listener listener) {
        this.parleyListener = listener;
    }

    /**
     * Close this librarian: closes the attached Parley listener if any
     * (refusing new connections), then the library, then releases the
     * presence lock so another process can take over the data dir.
     * Idempotent.
     */
    public void close() {
        if (parleyListener != null) {
            try { parleyListener.close(); } catch (RuntimeException ignored) {}
            parleyListener = null;
        }
        try { library.close(); } catch (RuntimeException ignored) {}
        if (presence != null) {
            presence.close();
            presence = null;
        }
    }

    // ==================================================================================
    // CLI entry — `java -cp ... Librarian [opts]`
    // ==================================================================================
    //
    // Bring up an {@link dev.everydaythings.graph.runtime.stage.ItemStage}, instantiate
    // a Librarian on it via the existing factories, optionally start the Parley
    // listener (in daemon/foreground mode), and block until interrupted.
    //
    // For service-manager-driven deployment (jsvc, Procrun, etc.) use
    // {@link LibrarianDaemon} as the daemon class — same flow, split across the
    // Apache Commons Daemon lifecycle methods.

    /**
     * Start a Librarian in this VM from {@link LibrarianOptions}.  Bundles
     * every step needed to go from raw options to a fully running librarian:
     *
     * <ol>
     *   <li>Bring up an {@link ItemStage} (polyglot detection).</li>
     *   <li>Decide ephemeral vs. persistent based on {@code opts.path}.</li>
     *   <li>For persistent: {@link #load(ItemStage, Path) load} if
     *       {@code .item/} exists, else {@link #fresh(ItemStage, Path) fresh}.</li>
     *   <li>{@link #bootstrap()} so seed vocab is available regardless of
     *       fresh-vs-loaded (idempotent on loaded — re-runs produce identical
     *       content-addressed bodies).</li>
     *   <li>For persistent: bind a Parley {@link Transport.Listener} on the
     *       configured Unix socket and attach it via
     *       {@link #setParleyListener(Transport.Listener)} so it closes with
     *       the librarian.</li>
     * </ol>
     *
     * <p>This is the shared bring-up that both {@link #main(String[])} and
     * {@code Graph}'s embedded mode call.  All librarian-side startup logic
     * lives here so Graph stays a thin composer of librarian + session.
     *
     * <p>The returned Librarian owns its listener (if any); closing the
     * Librarian closes the listener too.  Callers just register a single
     * shutdown hook on {@link #close()}.
     *
     * <p>Requires a Transport implementation on the classpath at runtime
     * (currently {@code :bridges:unix} for the default Unix-socket endpoint),
     * discovered via {@link TransportRegistry} (ServiceLoader).
     */
    public static Librarian startInVm(LibrarianOptions opts) {
        ItemStage stage = new ItemStage();
        logger.info("ItemStage up. Polyglot: {}",
                stage.polyglotAvailable()
                        ? "GraalVM " + stage.polyglotLanguages()
                        : "Java-only");

        if (opts.path == null) {
            // No --lib-path: ephemeral, in-memory librarian.  No parley
            // listener — there's no data dir to publish a socket in.
            Librarian lib = Librarian.ephemeral(stage);
            lib.bootstrap();
            logger.info("Librarian (ephemeral) running. IID: {}", lib.iid().encodeText());
            return lib;
        }

        // Persistent librarian: load if .item/ exists, else fresh.
        Path dataDir = opts.effectivePath();
        boolean existing = Files.isDirectory(dataDir.resolve(".item"));
        Librarian lib = existing ? Librarian.load(stage, dataDir) : Librarian.fresh(stage, dataDir);
        // Bootstrap on both fresh and loaded.  Fresh needs it.  Loaded is a
        // no-op via content-addressing (the seed bodies are already persisted
        // from the first run), so calling unconditionally keeps the contract
        // simple: "a started librarian has seed vocab available."
        lib.bootstrap();
        logger.info("Librarian ({}) at {}. IID: {}",
                existing ? "loaded" : "fresh", dataDir, lib.iid().encodeText());

        // Bind Parley listener on the configured Unix socket; attach to the
        // librarian's lifecycle so close() takes it down.
        Path socketPath = opts.effectiveSocketPath();
        UnixEndpoint endpoint = UnixEndpoint.of(socketPath.toString());
        Transport transport = TransportRegistry.require(endpoint);
        Parley parley = new Parley(lib);
        ItemRef cgCbor = ItemRef.iid(Encoding.CgCborV1.KEY);
        Transport.Listener listener = parley.listen(transport, endpoint, cgCbor, java.util.Set.of(cgCbor));
        lib.setParleyListener(listener);
        logger.info("Parley listening at {}", socketPath);

        return lib;
    }

    /**
     * Direct CLI entry.  Parses {@link LibrarianOptions}, calls
     * {@link #startInVm(LibrarianOptions)}, and blocks until interrupted.
     * All librarian bring-up lives in {@code startInVm} — this method just
     * stitches options parsing, shutdown hook registration, and blocking.
     */
    public static void main(String[] args) {
        LibrarianOptions opts = new LibrarianOptions();
        new picocli.CommandLine(opts).parseArgs(args);

        Librarian lib = startInVm(opts);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            logger.info("Librarian shutting down.");
            lib.close();
        }, "Librarian-shutdown"));
        blockUntilInterrupted();
    }

    private static void blockUntilInterrupted() {
        try {
            Thread.currentThread().join();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.info("Librarian interrupted; shutting down.");
        }
    }

    // ==================================================================================
    // Bootstrap (seed vocabulary)
    // ==================================================================================

    /**
     * Discover and persist seed vocabulary by scanning the classpath for {@code @Seed},
     * {@code @Embodies}, and {@code @Mints} annotations, then publish this librarian's
     * own self-attested INCEPTION on the signing track. Called explicitly by tests
     * and applications that want the seed sememes available; not called automatically
     * from {@link #inMemory()} to avoid classpath-scan overhead on every Librarian
     * construction.
     *
     * <p>The self-INCEPTION step is what gives this librarian a real KEL anchor: a
     * signed event committing the current public key to the librarian's IID on the
     * signing track. Verification against this librarian's signatures (now and after
     * future ROTATION events) consults the published INCEPTION's committed key.
     *
     * <p>Idempotent via content-addressing — re-running on the same classpath produces
     * the same seed body bytes, same CIDs, no-op writes. The self-INCEPTION includes a
     * timestamp so repeated calls produce distinct frames; for production this should
     * be guarded by a "have I incepted yet?" check, deferred until we have key-state
     * cache infrastructure.
     */
    public void bootstrap() {
        SeedProcessor.bootstrap(this);
        // Warm the algorithm cache now that every algorithm sememe has been
        // seeded and indexed.  Anonymous librarians skip bootstrap and warm
        // their cache lazily on first miss (see algorithmBy* lookups below).
        algorithms.warm(this);
        // Self-inception now happens during construction (see
        // {@link #Librarian(Vault, Library, Optional)}); bootstrap is only
        // responsible for seed vocabulary discovery.
    }

    // ==================================================================================
    // Algorithm lookups
    //
    // Each lookup hits the AlgorithmCache.  Cache misses trigger a one-time
    // lazy warm — covers anonymous librarians (which never call bootstrap)
    // and the rare case where a polyglot algorithm was deployed after the
    // initial warm.
    // ==================================================================================

    /** Look up a signing algorithm by COSE identifier (e.g., -8 for Ed25519). */
    public Signing algorithmByCoseId(long coseId) {
        Signing a = algorithms.byCoseId(coseId);
        if (a == null && !algorithms.isWarmed()) {
            algorithms.warm(this);
            a = algorithms.byCoseId(coseId);
        }
        return a;
    }

    /** Look up a signing algorithm by multicodec varsig code. */
    public Signing algorithmByVarsigCode(long varsigCode) {
        Signing a = algorithms.byVarsigCode(varsigCode);
        if (a == null && !algorithms.isWarmed()) {
            algorithms.warm(this);
            a = algorithms.byVarsigCode(varsigCode);
        }
        return a;
    }

    /** Look up a signing algorithm by multicodec multikey code. */
    public Signing algorithmByMultikeyCode(long multikeyCode) {
        Signing a = algorithms.byMultikeyCode(multikeyCode);
        if (a == null && !algorithms.isWarmed()) {
            algorithms.warm(this);
            a = algorithms.byMultikeyCode(multikeyCode);
        }
        return a;
    }

    /** Look up a signing algorithm by its sememe IID. */
    public Signing algorithmByIid(ItemRef sememeIid) {
        Signing a = algorithms.byIid(sememeIid);
        if (a == null && !algorithms.isWarmed()) {
            algorithms.warm(this);
            a = algorithms.byIid(sememeIid);
        }
        return a;
    }

    // ==================================================================================
    // Storage delegation
    // ==================================================================================

    /**
     * Fetch raw bytes from the local Library by ContentRef.
     *
     * <p>Returns the bytes if the Library has them locally. Networking and
     * external-source resolution will be added later; this minimal version
     * is local-only.
     */
    public Optional<byte[]> fetch(ContentRef cid) {
        return library.getContent(cid);
    }

    /** Whether the local Library has any realization for the given DatumRef. */
    public boolean has(DatumRef datumId) {
        return library.has(datumId);
    }

    /**
     * Persist a Datum (Body or Record) to the local Library, returning its semantic
     * identity (DatumRef). Indexes are written as side-effects; storage internally
     * keys bytes by ContentRef, with DATUM_INDEX bridging DatumRef → ContentRef.
     */
    public DatumRef persist(Datum datum) {
        return library.put(datum);
    }

    /**
     * Resolve a token to ranked Postings — the entry point for the parsing /
     * input pipeline. Postings carry surface form, target item, predicate kind,
     * scope, features, weight, and source body CID — everything a parser or
     * scorer needs without reaching past the librarian to internal indexes.
     *
     * <p>Local-only today: searches just this librarian's own Library. Future
     * "dig deeper" / federated queries will extend this to consult peers
     * transparently.
     */
    public List<TokenPosting> lookupToken(String token) {
        return library.lookupToken(token);
    }

    /**
     * Prefix variant of {@link #lookupToken} for autocomplete. Returns up to
     * {@code limit} Postings whose tokens begin with {@code tokenPrefix},
     * ordered by descending weight.
     */
    public List<TokenPosting> lookupTokenPrefix(String tokenPrefix, int limit) {
        return library.lookupTokenPrefix(tokenPrefix, limit);
    }

    /**
     * Unified token lookup — the handler for {@link LibrarianVocabulary.Lookup#IID LOOKUP} frames.
     *
     * <p>If {@code limit} is {@code null}, performs an exact (point) lookup.
     * Otherwise performs a prefix (range) lookup capped at the given limit.
     *
     * <p>Direct in-VM callers can invoke this method straight; the same method
     * is also reachable through the frame-dispatch pipeline via the
     * {@link Seed.Handler} annotation, which keys it to the LOOKUP predicate.
     *
     * <p>Returns response frames (rather than raw Postings) so the result fits
     * the actor-model pipeline uniformly. Each posting becomes a frame body
     * carrying the surface form, target item, predicate kind, etc. — the same
     * fields a remote caller would receive.
     */
    @Seed.Handler(predicate = LibrarianVocabulary.Lookup.KEY)
    public List<Frame> lookup(Frame lookupFrame) {
        Objects.requireNonNull(lookupFrame, "lookupFrame");

        String token = lookupFrame.body()
                .binding(CompoundKey.of(ItemRef.iid(ThematicRole.Theme.KEY)))
                .map(Binding::target)
                .filter(t -> t instanceof String)
                .map(String.class::cast)
                .orElseThrow(() -> new IllegalArgumentException(
                        "LOOKUP frame missing required THEME (token) string binding"));

        Integer limit = lookupFrame.body()
                .binding(CompoundKey.of(
                        ItemRef.iid(ThematicRole.Attribute.KEY),
                        ItemRef.iid(SchemaVocabulary.Limit.KEY)))
                .map(Binding::target)
                .filter(t -> t instanceof Long)
                .map(t -> ((Long) t).intValue())
                .orElse(null);

        List<TokenPosting> postings = (limit == null)
                ? library.lookupToken(token)
                : library.lookupTokenPrefix(token, limit);
        List<Frame> responses = new ArrayList<>(postings.size());
        for (TokenPosting p : postings) {
            responses.add(postingToFrame(p));
        }
        return responses;
    }

    /**
     * Wrap a {@link TokenPosting} as a {@link Frame}
     * for return through the dispatch pipeline. The frame is ephemeral by
     * convention (no records, never persisted) — it's the answer to a LOOKUP,
     * relevant only to the asking client at this moment.
     */
    private static Frame postingToFrame(TokenPosting p) {
        FrameBuilder fb = Frame.compose(ItemRef.iid(LibrarianVocabulary.Lookup.KEY))
                .with(ItemRef.iid(ThematicRole.Value.KEY), p.token());
        if (p.target() != null) {
            fb.theme(p.target());
        }
        if (p.predicate() != null) {
            fb.with(ItemRef.iid(ThematicRole.Topic.KEY), p.predicate());
        }
        return fb.build();
    }

    /**
     * Persist arbitrary content bytes to the local Library, returning the CID
     * computed from the bytes.
     */
    public ContentRef persistContent(byte[] bytes) {
        return library.putContent(bytes);
    }

    /**
     * Handle a {@link LibrarianVocabulary.Create} frame — instantiate a fresh item, commit its
     * initial manifest, and fire the post-construct hook.
     *
     * <p>The CREATE frame's bindings:
     * <ul>
     *   <li>{@code THEME} (required) — archetype IID of the kind to create</li>
     *   <li>{@code INSTRUMENT} (optional) — specific implementation; if absent,
     *       falls back to the archetype's own {@code IMPLEMENTATION} binding</li>
     *   <li>Other bindings — carried forward as initial bindings on the new item</li>
     * </ul>
     *
     * <p>The new item's IID is deterministically derived from the CREATE frame's
     * body DatumRef. The initial manifest is signed by this librarian (Phase 1;
     * eventually we'll honor the AGENT identity if signing material is available).
     *
     * <p>No CREATED response frame is emitted — the CREATE frame itself is the
     * audit-worthy record of "this item was made." The new item is discoverable
     * via {@link #fetchItem} once created.
     */
    @Seed.Handler(predicate = LibrarianVocabulary.Create.KEY)
    public List<Frame> createItem(Frame createFrame) {
        Objects.requireNonNull(createFrame, "createFrame");

        ItemRef archetype = readReferencedIid(createFrame, ItemRef.iid(ThematicRole.Theme.KEY));
        if (archetype == null) {
            throw new IllegalArgumentException(
                    "CREATE frame missing required THEME (archetype) binding");
        }

        Class<? extends Item> implClass = resolveImplementationClass(createFrame, archetype);
        ItemRef newIid = mintIidFromCreateFrame(createFrame);

        Item item;
        try {
            item = implClass.getConstructor(ItemRef.class, Librarian.class)
                    .newInstance(newIid, this);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(
                    "Failed to instantiate " + implClass.getName()
                            + " for CREATE (requires public (ItemRef, Librarian) constructor)", e);
        }
        register(item);
        item.commit(List.of());   // librarian signs the initial manifest

        // Fire the post-construct hook. Any archetype with @Handler(predicate=Construct.KEY)
        // gets to set up domain-specific initial state. Default: no-op.
        Frame constructFrame = Frame.compose(ItemRef.iid(RuntimeVocabulary.Construct.KEY))
                .theme(newIid)
                .build();
        submit(constructFrame);

        return List.of();
    }

    /**
     * Resolve the Java class to instantiate for a CREATE. Priority:
     * <ol>
     *   <li>{@code INSTRUMENT} binding on the CREATE frame, if present.</li>
     *   <li>Otherwise, the archetype's own {@code IMPLEMENTATION} binding.</li>
     * </ol>
     *
     * <p>INSTRUMENT may target a Java-class name (text target) directly, or an item
     * reference. When an item ref, we fetch the implementation item and read its
     * own {@code IMPLEMENTATION} binding.
     */
    private Class<? extends Item> resolveImplementationClass(Frame createFrame, ItemRef archetype) {
        // Step 1: caller-provided INSTRUMENT?
        Optional<Binding> instrumentBinding = createFrame.body()
                .binding(CompoundKey.of(ItemRef.iid(ThematicRole.Instrument.KEY)));
        if (instrumentBinding.isPresent()) {
            return resolveImplementationFromBinding(
                    instrumentBinding.get(),
                    "INSTRUMENT on CREATE frame");
        }
        // Step 2: archetype's IMPLEMENTATION binding
        Item archetypeItem = fetchItem(archetype)
                .orElseThrow(() -> new IllegalStateException(
                        "CREATE archetype " + archetype + " has no local manifest; "
                                + "can't resolve implementation"));
        Manifest manifest = archetypeItem.current();
        if (manifest == null) {
            throw new IllegalStateException(
                    "CREATE archetype " + archetype + " has no current manifest");
        }
        Binding implBinding = manifest.implementation()
                .orElseThrow(() -> new IllegalStateException(
                        "CREATE archetype " + archetype + " has no IMPLEMENTATION binding "
                                + "and no INSTRUMENT was supplied"));
        return resolveImplementationFromBinding(
                implBinding,
                "IMPLEMENTATION on archetype " + archetype);
    }

    /**
     * Read a Java class from an implementation-shaped binding. Two cases:
     * <ul>
     *   <li>The binding's role is {@link RuntimeVocabulary.Java} with
     *       {@link RuntimeVocabulary.ClassName} as a qualifier and a text
     *       target — used directly as a class name.</li>
     *   <li>The binding's target is an item reference — fetch that item and
     *       follow its own implementation binding one level.</li>
     * </ul>
     */
    private Class<? extends Item> resolveImplementationFromBinding(
            Binding binding, String contextDescription) {
        // Direct Java implementation binding: role Java, qualifier ClassName, text target.
        if (Manifest.isJavaImplementation(binding)) {
            if (!(binding.target() instanceof String className)) {
                throw new IllegalStateException(contextDescription
                        + " is a Java implementation binding but target is not a String: "
                        + binding.target().getClass().getSimpleName());
            }
            return loadItemClass(className, contextDescription);
        }
        // Item reference — fetch the impl item and follow its implementation binding.
        ItemRef implItemIid = extractReferencedIidFromTarget(binding.target());
        if (implItemIid == null) {
            throw new IllegalStateException(contextDescription
                    + " is neither a Java implementation binding nor an item reference: "
                    + binding.target().getClass().getSimpleName());
        }
        Item implItem = fetchItem(implItemIid)
                .orElseThrow(() -> new IllegalStateException(contextDescription
                        + " references item " + implItemIid + " which has no local manifest"));
        Manifest manifest = implItem.current();
        if (manifest == null) {
            throw new IllegalStateException(contextDescription
                    + " references item " + implItemIid + " with no current manifest");
        }
        Binding nested = manifest.implementation()
                .orElseThrow(() -> new IllegalStateException(contextDescription
                        + " references item " + implItemIid
                        + " which lacks an IMPLEMENTATION binding"));
        if (!Manifest.isJavaImplementation(nested)
                || !(nested.target() instanceof String className)) {
            throw new IllegalStateException(contextDescription
                    + " via item " + implItemIid
                    + " ultimately did not resolve to a Java implementation binding");
        }
        return loadItemClass(className, contextDescription);
    }

    @SuppressWarnings("unchecked")
    private static Class<? extends Item> loadItemClass(String className, String ctx) {
        Class<?> raw;
        try {
            raw = Class.forName(className);
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException(ctx + " names class " + className
                    + " which is not on the classpath", e);
        }
        if (!Item.class.isAssignableFrom(raw)) {
            throw new IllegalStateException(ctx + " class " + className
                    + " does not extend Item");
        }
        return (Class<? extends Item>) raw;
    }

    /**
     * Mint a deterministic IID for a freshly-created item from the CREATE frame's
     * body DatumRef. Same CREATE frame → same IID (idempotent); different CREATEs
     * → different IIDs.
     */
    private static ItemRef mintIidFromCreateFrame(Frame createFrame) {
        return ItemRef.fromMultikeyBytes(createFrame.body().datumId().encodeBinary());
    }

    /**
     * Handle a {@link LibrarianVocabulary.Delete} frame —
     * a request to remove an item from local storage.
     *
     * <p>Phase 1 authorization: honor only DELETEs whose records carry a
     * signature verifiable against this librarian's own KEL. Self-signed →
     * cascade-delete the targeted item's manifests and records, evict from
     * cache. Other-signed → silent no-op (the DELETE frame is already in
     * storage as durable data; we just don't act on it).
     *
     * <p>Per-librarian sovereignty: each librarian decides independently. The
     * same DELETE frame propagating to multiple peers will be honored by
     * some and ignored by others based on each peer's trust matrix. Phase 2
     * extends authorization beyond self-signed (trust-graph-weighted).
     *
     * <p>Endorsed frames and other items referencing the target are NOT
     * cascaded — those may be referenced elsewhere; dangling references are
     * an accepted cost, reconciled by future GC.
     */
    @Seed.Handler(predicate = LibrarianVocabulary.Delete.KEY)
    public List<Frame> deleteItem(Frame deleteFrame) {
        Objects.requireNonNull(deleteFrame, "deleteFrame");
        ItemRef targetIid = readReferencedIid(deleteFrame, ItemRef.iid(ThematicRole.Theme.KEY));
        if (targetIid == null) return List.of();

        if (!isAuthorizedByThisLibrarian(deleteFrame)) return List.of();

        // Cascade: tear down the item's manifests + their records.
        for (DatumRef manifestId : library.manifestCidsForItem(targetIid)) {
            for (DatumRef recordId : library.recordCidsForBody(manifestId)) {
                library.delete(recordId);
            }
            library.delete(manifestId);
        }
        evict(targetIid);
        return List.of();
    }

    /**
     * Phase 1 authorization check for DELETE: returns true iff at least one of
     * the frame's records was signed by this librarian's own KEL-committed key.
     * Conceptually: "am I being asked to honor my own request?"
     */
    private boolean isAuthorizedByThisLibrarian(Frame frame) {
        byte[] signedBytes = HashTree.signingPayload(frame.body());
        for (Record record : frame.records()) {
            if (!record.isSigned()) continue;
            if (verifySignedAsIdentity(iid(), signedBytes, record.varsig())) {
                return true;
            }
        }
        return false;
    }

    private ItemRef readReferencedIid(Frame frame, ItemRef role) {
        return frame.body()
                .binding(CompoundKey.of(role))
                .map(b -> extractReferencedIidFromTarget(b.target()))
                .orElse(null);
    }

    private static ItemRef extractReferencedIidFromTarget(Object target) {
        if (target instanceof ItemRef ir && !ir.isPinned()) return ir;
        return null;
    }

    /**
     * Fetch a Frame (body + records aggregate) by its body's semantic identity.
     *
     * <p>Delegates to {@link Library#fetchFrame} — Library owns the decode
     * boundary; this method is a thin facade for callers that hold a Librarian
     * reference.
     */
    public Optional<Frame> fetchFrame(DatumRef bodyId) {
        return library.fetchFrame(bodyId);
    }

    /**
     * Walk the reverse-lookup index for bodies that carry a binding with the
     * given {@code role} pointing at {@code target}, and return the fetched
     * Body datums.  Useful for "find all frames addressed to iid X via role Y"
     * style queries — e.g., {@code ITEM_VIEW} frames where the session
     * (target) is referenced in their Location binding.
     *
     * <p>The caller filters by body head ({@code body.headRef()}) if a
     * specific predicate is required.  Bodies whose CID is in the index but
     * whose bytes are no longer available are silently skipped.
     */
    public List<Body> bodiesByReferenceBinding(ItemRef role, ItemRef target) {
        List<Body> out = new ArrayList<>();
        for (DatumRef bodyId : library.bodyCidsForReferenceBinding(role, target)) {
            library.fetchBody(bodyId).ifPresent(out::add);
        }
        return out;
    }

    /**
     * Fetch a Manifest (archetypal body + records aggregate) by its body's
     * semantic identity. Delegates to {@link Library#fetchManifest}.
     */
    public Optional<Manifest> fetchManifest(DatumRef bodyId) {
        return library.fetchManifest(bodyId);
    }

    /** Whether the local Library has bytes for the given CID. */
    public boolean has(ContentRef cid) {
        return library.hasContent(cid);
    }

    /**
     * Verify that the given signature is valid for the given message under one of
     * the signing keys committed by the given identity's KEL.
     *
     * <p>Identity-aware counterpart to {@link Signer#verify(MultiKey, byte[], VarSig)}:
     * instead of taking an explicit MultiKey, takes an IID and consults the
     * locally-known INCEPTION/ROTATION frames for that identity via the inherited
     * {@link Signer#currentKeys(ItemRef, ItemRef)}.
     *
     * @return true if the signature verifies against any currently-committed
     *         signing key for {@code identityIid}; false otherwise
     */
    public boolean verifySignedAsIdentity(ItemRef identityIid, byte[] message, VarSig varsig) {
        for (MultiKey key : currentKeys(identityIid, ItemRef.iid(IdentityVocabulary.Signing.KEY))) {
            if (verify(key, message, varsig)) return true;
        }
        return false;
    }

    /**
     * Load an Item by IID, returning the canonical instance for this IID.
     *
     * <p>Consults the in-memory item cache first; if not cached, hydrates from
     * local storage by fetching the current manifest. The hydrated item is
     * cached so subsequent calls return the same instance.
     *
     * <p>Returns empty if no manifest is locally indexed for the given IID and
     * no instance has been registered. When multiple manifest versions exist for
     * the same item, picks the first one the index returns (HEAD selection logic
     * is not yet wired).
     */
    public Optional<Item> fetchItem(ItemRef iid) {
        Objects.requireNonNull(iid, "iid");
        Item cached = itemCache.get(iid);
        if (cached != null) return Optional.of(cached);

        List<DatumRef> manifestCids = library.manifestCidsForItem(iid);
        if (manifestCids.isEmpty()) return Optional.empty();
        // TODO: when HEAD logic exists, pick the right manifest. For now, take the first.
        DatumRef chosen = manifestCids.getFirst();
        return fetchManifest(chosen).map(manifest -> {
            Item item = hydrateItem(iid, manifest);
            item.bindManifest(manifest);
            register(item);
            return item;
        });
    }

    /**
     * Construct an Item instance for hydration, dispatching to a Java subclass
     * if the manifest's IMPLEMENTATION binding declares one.
     *
     * <p>Behavior:
     * <ul>
     *   <li>No IMPLEMENTATION binding → bare {@link Item} (the manifest is
     *       semantically declaring "this is a generic item").</li>
     *   <li>IMPLEMENTATION binding present and resolves cleanly → instance of the
     *       declared class.</li>
     *   <li>IMPLEMENTATION binding present but unresolvable → {@link IllegalStateException}.
     *       Silently substituting a bare Item would honor a different identity
     *       than the manifest declares; the manifest's claim is the contract.</li>
     * </ul>
     *
     * <p>"Unresolvable" includes: target isn't a Java-class name string, class isn't
     * on the classpath, class doesn't extend Item, no public {@code (ItemRef, Librarian)}
     * constructor, or the constructor throws.
     */
    private Item hydrateItem(ItemRef iid, Manifest manifest) {
        // Code-archetype manifests are metadata about an implementation, not items
        // whose runtime form is the embodied class. Their IMPLEMENTATION binding
        // describes "what code I represent" (class literal), not "how to hydrate
        // me." Return a bare Item carrying the manifest; callers walk endorsements
        // for the actual API surface.
        if (manifest.body().head() instanceof ItemRef ref
                && ItemRef.iid(RuntimeVocabulary.Code.KEY).equals(ref.iid())) {
            return new Item(iid, this);
        }

        Optional<Binding> impl = manifest.implementation();
        if (impl.isEmpty()) {
            return new Item(iid, this);
        }
        Class<? extends Item> clazz = resolveImplementationClass(impl.get(), iid);
        try {
            return clazz.getConstructor(ItemRef.class, Librarian.class)
                    .newInstance(iid, this);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(
                    "Failed to hydrate " + iid + " as " + clazz.getName()
                            + " (requires a public (ItemRef, Librarian) constructor)", e);
        }
    }

    private static Class<? extends Item> resolveImplementationClass(Binding binding, ItemRef iid) {
        if (!Manifest.isJavaImplementation(binding)) {
            throw new IllegalStateException(
                    "Manifest for " + iid + " has implementation binding that is not "
                            + "Java+ClassName; cannot hydrate as Java");
        }
        if (!(binding.target() instanceof String className)) {
            throw new IllegalStateException(
                    "Manifest for " + iid + " has Java implementation binding whose target is "
                            + binding.target().getClass().getSimpleName()
                            + "; expected a class-name String");
        }
        Class<?> clazz;
        try {
            clazz = Class.forName(className, false,
                    Thread.currentThread().getContextClassLoader());
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException(
                    "Manifest for " + iid + " declares IMPLEMENTATION class "
                            + className + " which is not on the classpath", e);
        }
        if (!Item.class.isAssignableFrom(clazz)) {
            throw new IllegalStateException(
                    "Manifest for " + iid + " declares IMPLEMENTATION class "
                            + clazz.getName() + " which does not extend Item");
        }
        return clazz.asSubclass(Item.class);
    }

    /**
     * Register an item in the cache, making it the canonical instance for its IID.
     *
     * <p>Used by {@link Item#commit} to mark a freshly-committed item as canonical,
     * by {@link #fetchItem} to cache hydrated items, and by application code that
     * constructs {@link Item} subclasses directly (e.g., a {@code ChessGame}) and
     * wants frame routing to find them.
     *
     * <p>If an item is already registered for this IID, this replaces it. There
     * is no consistency check yet — we trust callers to maintain the
     * one-instance-per-IID rule.
     */
    public void register(Item item) {
        Objects.requireNonNull(item, "item");
        if (item.iid() == null) {
            throw new IllegalArgumentException("Anonymous item has no identity; cannot register");
        }
        itemCache.put(item.iid(), item);
    }

    /**
     * Remove an item from the cache. Used by the DELETE handler
     * after honoring a delete request — the cache must drop the now-deleted item
     * so future {@code fetchItem} calls return empty (storage is also gone).
     */
    public void evict(ItemRef iid) {
        Objects.requireNonNull(iid, "iid");
        itemCache.remove(iid);
    }

    /**
     * Find a live cached instance whose {@code archetype()} matches {@code archetype}.
     * Used by the dispatcher to find the "running instance" for an archetype before
     * falling through to materializing a fresh one from storage.
     *
     * <p>When {@code instanceIid} is non-null, only returns a match whose
     * {@link Item#iid() iid} equals it — for per-instance routing
     * (Session-with-specific-iid, Signer-with-specific-iid).
     *
     * <p>When {@code instanceIid} is null, returns the first cached instance
     * of the archetype (singleton-style routing — operators, the librarian).
     *
     * <p>Linear scan over {@code itemCache.values()}.  Cache is small in practice.
     */
    public Optional<Item> liveInstanceOf(ItemRef archetype, ItemRef instanceIid) {
        Objects.requireNonNull(archetype, "archetype");
        for (Item item : itemCache.values()) {
            if (!archetype.equals(item.archetype())) continue;
            if (instanceIid == null || instanceIid.equals(item.iid())) {
                return Optional.of(item);
            }
        }
        return Optional.empty();
    }

    // ==================================================================================
    // Frame assembly (publish + route)
    // ==================================================================================

    /**
     * Assemble a propositional frame: persist its body, sign and persist a record,
     * then notify every item referenced in the body's bindings via
     * {@link Item#onFrameAssembled}.
     *
     * <p>This is the "frame-creation-as-action" entry point. Building and publishing
     * a frame IS the action; subject items react via their {@code onFrameAssembled}
     * override. Routing is synchronous and single-threaded; an exception in one
     * item's handler is caught and logged, and the chain continues.
     *
     * <p>Each referenced item is notified at most once even if multiple bindings
     * reference it. Items not currently in the cache nor recoverable from storage
     * are silently skipped — they don't logically exist on this node.
     *
     * @param body the frame body (must be a propositional body, not a manifest)
     * @param signer the signer attesting the frame
     * @return the assembled Frame (body + the new record), already persisted
     */
    public Frame assembleFrame(Body body, Signer signer) {
        Objects.requireNonNull(body, "body");
        Objects.requireNonNull(signer, "signer");

        persist(body);
        VarSig signature = signer.sign(HashTree.signingPayload(body));
        Record record = Record.of(DatumRef.of(body.datumId()), List.of(), signature);
        persist(record);

        Frame frame = Frame.of(body, List.of(record));
        notifyReferencedItems(frame);
        // Run @Handler dispatch so callers using this older assembleFrame
        // entry get the same actor-model behavior submit() does.
        ItemRef predicateIid = ((ItemRef) body.head()).iid();
        dispatchToHandlers(frame, predicateIid, ContextChain.singleton(this));
        return frame;
    }

    // ==================================================================================
    // Submit (the unified entry point for the actor model)
    // ==================================================================================

    /**
     * Submit a {@link Frame} to the runtime. The frame is the message; this is
     * the send operation. Three phases happen in order:
     *
     * <ol>
     *   <li><b>Persist</b> — if the predicate is not marked ephemeral via
     *       {@code CONFIG[RETENTION] → @Ephemeral}, the body and any attached
     *       records are written to local storage.</li>
     *   <li><b>Notify</b> — items referenced by the frame learn it happened via
     *       {@link Item#onFrameAssembled}, regardless of retention.</li>
     *   <li><b>Dispatch</b> — any {@code @Handler}-annotated method on this
     *       Librarian whose predicate matches the frame's head is invoked. Its
     *       return value (if any) becomes response frames.</li>
     * </ol>
     *
     * <p>Response frames carry the handler's result back to the caller through
     * the {@link SubmitResult}. They are not themselves auto-submitted —
     * persisting query responses would be storage-toxic.
     *
     * @param frame the frame being sent
     * @return submitted frame + response frames from handlers (if any)
     */
    public SubmitResult submit(Frame frame) {
        Objects.requireNonNull(frame, "frame");

        // Routing decision: is this frame a query or an assertion?
        // A query carries TypeRef references (or, eventually, partially-applied
        // Bool-returning operators); QueryWalker scans the body structurally.
        if (QueryWalker.isQuery(frame.body())) {
            return submitQuery(frame);
        }

        // Assertion path — the historical flow.
        ItemRef predicateIid = ((ItemRef) frame.body().head()).iid();

        // Schema re-validation: even if the orchestrator already checked, the
        // librarian independently verifies the candidate against its head's
        // archetype manifest.  Soft mode for now: issues are logged but the
        // frame still flows through.  Tightening (reject-on-invalid) lands
        // once archetype manifests reliably carry their EXPECTS declarations.
        revalidateAgainstSchema(frame, predicateIid);

        boolean ephemeral = isEphemeral(predicateIid);

        if (!ephemeral) {
            persist(frame.body());
            for (Record r : frame.records()) {
                persist(r);
            }
        }

        notifyReferencedItems(frame);
        List<Frame> responses = dispatchToHandlers(
                frame, predicateIid, ContextChain.singleton(this));

        return SubmitResult.of(frame, responses);
    }

    /**
     * Chain-aware dispatch without persistence.  Builds no records, fires no
     * referenced-item notifications — just runs the frame through the same
     * two-hop {@code HANDLES → IMPLEMENTS} resolution that {@link #submit}
     * uses, with the supplied {@link ContextChain} available for chain-routed
     * handlers (those whose {@code @Seed.Handler} declares no {@code role=}).
     *
     * <p>This is the universal entry point reached via
     * {@link ContextChain#dispatch(Frame)}; {@link #submit(Frame)} is the
     * persist-then-dispatch convenience wrapper built on top.
     */
    public List<Frame> dispatch(Frame frame, ContextChain chain) {
        Objects.requireNonNull(frame, "frame");
        Objects.requireNonNull(chain, "chain");
        if (!(frame.body().head() instanceof ItemRef headRef)) return List.of();
        return dispatchToHandlers(frame, headRef.iid(), chain);
    }

    /**
     * Re-validate an incoming assertion against its head archetype's schema
     * declarations.  Issues are logged at WARN; persistence continues.  If the
     * archetype isn't locally known (no manifest fetched yet), validation is
     * skipped silently — we can't validate against a schema we haven't seen.
     */
    private void revalidateAgainstSchema(Frame frame, ItemRef predicateIid) {
        Optional<Item> archetype = fetchItem(predicateIid);
        if (archetype.isEmpty()) return;
        Manifest manifest = archetype.get().current();
        if (manifest == null) return;
        ValidationResult result = SchemaWalker.validate(frame.body(), manifest.body());
        if (!result.isValid()) {
            for (ValidationResult.Issue issue : result.issues()) {
                logger.warn("Schema validation issue on frame head={}: {}",
                        predicateIid, issue.message());
            }
        }
    }

    /**
     * Handle a query-shaped frame.  Routes it through the {@link MatcherOrchestrator}
     * which evaluates the query against the librarian's indexed graph and
     * returns RESULT frames for each match.
     *
     * <p>First-cut scope (per MatcherOrchestrator docs): TypeRef head +
     * concrete / TypeRef / literal binding targets.  Compound queries,
     * variables, partial-applied Bool operators, and the universal {@code @any}
     * sememe land as the matcher grows.  Query persistence (saved /
     * subscribed queries) is separate from this dispatch path.
     */
    private SubmitResult submitQuery(Frame frame) {
        MatcherOrchestrator matcher = new MatcherOrchestrator(this);
        List<Frame> results = matcher.match(frame.body());
        return SubmitResult.of(frame, results);
    }

    /**
     * Check whether the given predicate's manifest carries the ephemeral
     * retention CONFIG. Looks up the predicate item locally; if not found,
     * defaults to durable (non-ephemeral) — frames against unknown predicates
     * are persisted.
     */
    private boolean isEphemeral(ItemRef predicateIid) {
        return fetchItem(predicateIid)
                .map(this::hasEphemeralRetention)
                .orElse(false);
    }

    private boolean hasEphemeralRetention(Item predicate) {
        Manifest manifest = predicate.current();
        if (manifest == null) return false;
        CompoundKey retentionRole = CompoundKey.of(
                ItemRef.iid(SchemaVocabulary.Retention.KEY));
        ItemRef ephemeral = ItemRef.iid(SchemaVocabulary.Ephemeral.KEY);
        for (Record record : manifest.records()) {
            for (Binding b : record.bindings(retentionRole)) {
                if (b.target() instanceof ItemRef ir
                        && !ir.isPinned()
                        && ephemeral.equals(ir)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Dispatch a frame to whatever code implements its head predicate via
     * the universal IMPLEMENTS two-hop walk: archetypes that
     * {@code HANDLES → @predicate}, then code items that
     * {@code IMPLEMENTS → @archetype}, routed to a live instance (or freshly
     * materialized) via {@link ItemStage#deliver}.
     *
     * <p>Every concrete handler in the system — operators (Add, Between),
     * archetype methods (Session.handleItemView, Signer.encrypt), and the
     * librarian's own CREATE / DELETE / LOOKUP — reaches its code through
     * this one path.  See {@link #dispatchViaImplements} for the routing
     * variants (role-routed via Pivot, chain-routed, singleton fallback).
     */
    private List<Frame> dispatchToHandlers(Frame frame, ItemRef predicateIid, ContextChain chain) {
        return dispatchViaImplements(frame, predicateIid, chain);
    }

    /**
     * New-style dispatch using a two-hop walk: find archetypes that
     * {@code HANDLES} the frame's predicate, then find code items that
     * {@code IMPLEMENTS} those archetypes, then route to a live instance
     * (or materialize a fresh one) via {@link ItemStage#deliver}.
     *
     * <p><b>Self-handling case</b> (operators like Between): the archetype
     * declares {@code HANDLES → self}, so step 1 finds the archetype itself,
     * and step 2 finds its self-embodying code item.  Same result as the
     * old single-hop {@code IMPLEMENTS → predicate} lookup.
     *
     * <p><b>Item-archetype case</b> (Session, Signer): the archetype declares
     * {@code HANDLES → some-other-predicate}, and code items declare
     * {@code IMPLEMENTS → archetype}.  The two-hop walk follows that chain.
     *
     * <p><b>Instance routing</b> takes one of three shapes, picked per
     * handler:
     * <ul>
     *   <li><b>Role-routed</b> — the code item's HANDLES frame body declares
     *       {@code Pivot → role-iid}.  The dispatcher reads the incoming
     *       frame's {@code binding[role-iid]} for the target instance IID
     *       and looks up the live instance of the archetype at that IID via
     *       {@link #liveInstanceOf}, materializing fresh from the
     *       IMPLEMENTATION binding if no live one exists.</li>
     *   <li><b>Chain-routed</b> — no Pivot binding present.  The dispatcher
     *       walks {@code chain} (most-specific first) for a live instance of
     *       the handler's archetype.  This is what lets context-bound
     *       handlers reach their owner: e.g. a chess-game in the chain
     *       receives a Move frame because chess HANDLES Move.</li>
     *   <li><b>Singleton fallback</b> — chain-routed lookup yields nothing.
     *       Falls back to a cache scan ({@code liveInstanceOf(archetype,
     *       null)}) so operator code-items already cached are reused, then
     *       to materializing a fresh instance keyed by the archetype IID.
     *       Operators (self-handling, code-item singletons) live here.</li>
     * </ul>
     *
     * <p>Returns an empty list when no path leads to a deliverable item, or
     * when the chosen item's {@code receive} returns nothing.
     */
    private List<Frame> dispatchViaImplements(Frame frame, ItemRef predicateIid, ContextChain chain) {
        // Step 1: archetypes that HANDLES this predicate.
        List<DatumRef> archetypeManifestCids =
                library.bodyCidsForReferenceBinding(Manifest.HANDLES, predicateIid);
        if (archetypeManifestCids.isEmpty()) return List.of();

        for (DatumRef archetypeManifestCid : archetypeManifestCids) {
            Manifest archetypeManifest = fetchManifest(archetypeManifestCid).orElse(null);
            if (archetypeManifest == null) continue;
            ItemRef archetypeIid = archetypeManifest.itemId();
            if (archetypeIid == null) continue;

            // Step 2: code items that IMPLEMENTS this archetype.
            List<DatumRef> codeManifestCids =
                    library.bodyCidsForReferenceBinding(Manifest.IMPLEMENTS, archetypeIid);
            if (codeManifestCids.isEmpty()) continue;

            // Trust-matrix selection lands later; for now, first match wins.
            DatumRef chosenCodeCid = codeManifestCids.get(0);
            Manifest codeManifest = fetchManifest(chosenCodeCid).orElse(null);
            if (codeManifest == null) continue;

            // PIVOT present → role-routed; absent → chain-routed (with
            // singleton fallback when nothing in the chain matches).
            ItemRef pivotRole = readPivotFromCodeManifest(codeManifest, predicateIid);
            Item target;
            if (pivotRole != null) {
                ItemRef instanceIid = readBindingAsRef(frame, pivotRole).orElse(null);
                target = liveInstanceOf(archetypeIid, instanceIid).orElse(null);
                if (target == null) {
                    ItemRef ctorIid = instanceIid != null ? instanceIid : archetypeIid;
                    target = instantiateFromCodeManifest(codeManifest, ctorIid);
                }
            } else {
                target = chain.findInstanceOf(archetypeIid)
                        .or(() -> liveInstanceOf(archetypeIid, null))
                        .orElseGet(() -> instantiateFromCodeManifest(codeManifest, archetypeIid));
            }
            if (target == null) continue;

            Object result = stage.deliver(target, frame);
            List<Frame> responses = unwrapResponses(result);
            if (!responses.isEmpty()) return responses;
            return List.of();   // delivered, just no responses
        }
        return List.of();
    }

    /**
     * Walk a code item's ENDORSES bindings to find the HANDLES frame body
     * for the given predicate, then read its {@code Pivot} binding (if any)
     * and return the role-iid it names.  Returns {@code null} when no
     * matching HANDLES frame exists or it carries no Pivot.
     */
    private ItemRef readPivotFromCodeManifest(Manifest codeManifest, ItemRef predicateIid) {
        ItemRef themeRole = ItemRef.iid(ThematicRole.Theme.KEY);
        ItemRef pivotRole = ItemRef.iid(ThematicRole.Pivot.KEY);
        for (Binding b : codeManifest.body().bindings()) {
            if (!Manifest.ENDORSES.equals(b.roleIid())) continue;
            if (!(b.target() instanceof DatumRef handlesCid)) continue;
            Body handlesBody = library.fetchBody(handlesCid).orElse(null);
            if (handlesBody == null) continue;
            // The HANDLES frame's Theme binding names the predicate; the
            // optional Pivot binding names the role for instance routing.
            ItemRef theme = handlesBody.binding(CompoundKey.of(themeRole))
                    .map(bb -> bb.target() instanceof ItemRef ir ? ir : null)
                    .orElse(null);
            if (!predicateIid.equals(theme)) continue;
            return handlesBody.binding(CompoundKey.of(pivotRole))
                    .map(bb -> bb.target() instanceof ItemRef ir ? ir : null)
                    .orElse(null);
        }
        return null;
    }

    /**
     * Instantiate the Java class declared in {@code codeManifest}'s
     * IMPLEMENTATION binding, with the given {@code iid} as the new
     * instance's identity.  Used by the dispatcher when no live instance
     * exists at the target archetype/iid pair and we need to materialize
     * fresh.  Unlike {@link #fetchItem}, this does NOT short-circuit on
     * code-archetype manifests — we explicitly want the embodied class.
     */
    private Item instantiateFromCodeManifest(Manifest codeManifest, ItemRef iid) {
        Optional<Binding> impl = codeManifest.implementation();
        if (impl.isEmpty()) return new Item(iid, this);
        Class<? extends Item> clazz = resolveImplementationClass(impl.get(), iid);
        try {
            return clazz.getConstructor(ItemRef.class, Librarian.class)
                    .newInstance(iid, this);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(
                    "Failed to materialize " + clazz.getName() + " at " + iid
                            + " for dispatch (needs public (ItemRef, Librarian) constructor)", e);
        }
    }

    /**
     * Read a frame's binding for {@code role} and return the target if it's
     * an {@link ItemRef}.  Returns empty otherwise.
     */
    private static Optional<ItemRef> readBindingAsRef(Frame frame, ItemRef role) {
        return frame.body().binding(CompoundKey.of(role))
                .map(Binding::target)
                .filter(t -> t instanceof ItemRef)
                .map(t -> (ItemRef) t);
    }

    /** Coerce a {@code receive}-style return into a list of response frames. */
    private static List<Frame> unwrapResponses(Object result) {
        if (result == null) return List.of();
        if (result instanceof Frame f) return List.of(f);
        if (result instanceof List<?> list) {
            List<Frame> out = new ArrayList<>(list.size());
            for (Object o : list) {
                if (o instanceof Frame f) out.add(f);
            }
            return out;
        }
        return List.of();
    }

    private void notifyReferencedItems(Frame frame) {
        Set<ItemRef> notified = new HashSet<>();

        // Notify the predicate (head of the body). Predicates are sememes — items
        // that can react to their own invocation (CREATE-the-sememe handles the
        // CREATE frame's intent, etc.).
        ItemRef headIid = ((ItemRef) frame.body().head()).iid();
        notifyOne(headIid, frame, notified);

        // Notify each item referenced as a target in body bindings.
        for (Binding b : frame.body().bindings()) {
            extractReferencedIid(b.target()).ifPresent(iid -> notifyOne(iid, frame, notified));
        }
    }

    private void notifyOne(ItemRef iid, Frame frame, Set<ItemRef> notified) {
        if (!notified.add(iid)) return;
        fetchItem(iid).ifPresent(item -> {
            try {
                item.onFrameAssembled(frame);
            } catch (RuntimeException e) {
                // Log and continue; one item's failure must not prevent others.
                logger.warn("onFrameAssembled threw on item {}", item.iid(), e);
            }
        });
    }

    private static Optional<ItemRef> extractReferencedIid(Object target) {
        if (target instanceof ItemRef ir && !ir.isPinned()) {
            return Optional.of(ir);
        }
        return Optional.empty();
    }

}
