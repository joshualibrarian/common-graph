package dev.everydaythings.graph.runtime.librarian;

import dev.everydaythings.graph.SchemaVocabulary;

import dev.everydaythings.graph.CoreVocabulary;
import dev.everydaythings.graph.canonical.HashTree;
import dev.everydaythings.graph.canonical.Node;
import dev.everydaythings.graph.identity.MultiKey;
import dev.everydaythings.graph.identity.VarSig;
import dev.everydaythings.graph.identity.vault.InMemoryVault;
import dev.everydaythings.graph.identity.vault.Vault;
import dev.everydaythings.graph.datum.*;
import dev.everydaythings.graph.datum.Record;
import dev.everydaythings.graph.identity.IdentityVocabulary;
import dev.everydaythings.graph.item.Item;
import dev.everydaythings.graph.runtime.*;
import dev.everydaythings.graph.item.Manifest;
import dev.everydaythings.graph.item.SeedProcessor;
import dev.everydaythings.graph.id.CompoundKey;
import dev.everydaythings.graph.id.ContentRef;
import dev.everydaythings.graph.id.DatumRef;
import dev.everydaythings.graph.id.ItemRef;
import dev.everydaythings.graph.identity.Signer;
import dev.everydaythings.graph.library.Library;
import dev.everydaythings.graph.library.index.TokenPosting;
import dev.everydaythings.graph.network.parley.Parley;
import dev.everydaythings.graph.Seed;
import dev.everydaythings.graph.language.ThematicRole;
import dev.everydaythings.graph.runtime.stage.ItemStage;
import lombok.Getter;

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
@lombok.extern.log4j.Log4j2
public class Librarian extends Signer {

    /** Canonical key for Librarian-the-archetype. */
    public static final String KEY = "cg.archetype:librarian";

    /** The archetype IID for Librarian instances. */
    public static final ItemRef IID = ItemRef.fromString(KEY);

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
        return IID;
    }

    /**
     * The execution substrate this Librarian runs on. Owns the polyglot
     * environment and (eventually) the handler-dispatch primitive. Provided at
     * construction time — Librarians don't create their own Stage.
     */
    @Getter
    private final dev.everydaythings.graph.runtime.stage.ItemStage stage;

    /** The local storage manager (always present). Owns the encoder. */
    @Getter
    private final Library library;

    /**
     * Filesystem footprint, if any. Empty for in-memory librarians; present for
     * persistent and viewer modes.
     */
    @Getter
    private final Optional<Path> rootPath;

    /**
     * The {@link Parley} protocol instance — the librarian's "talking to other
     * parties" surface. Owns connections (local + remote), handles codec
     * point-and-grunt, and dispatches incoming Datums to {@code @Handler} methods.
     *
     * <p>Always present; behaviour is currently stubbed (Parley is structural
     * scaffolding pending wire-up).
     */
    @Getter
    private final Parley parley;

    /**
     * Item cache: one canonical instance per IID. {@link #fetchItem} consults this
     * before hydrating from storage; {@link #register} adds explicit entries.
     * {@link Item#commit} auto-registers, so a committed item is always findable.
     */
    private final Map<ItemRef, Item> itemCache = new ConcurrentHashMap<>();

    /**
     * Identity-only constructor (no signing capability). Primarily for hydration
     * paths where the local node doesn't hold this Librarian's private key.
     *
     * <p>Convenience overload that defaults to a Java-only {@link
     * dev.everydaythings.graph.runtime.stage.ItemStage}.
     */
    public Librarian(ItemRef iid, Library library, Optional<Path> rootPath) {
        this(dev.everydaythings.graph.runtime.stage.ItemStage.javaOnly(),
                iid, library, rootPath);
    }

    /** Identity-only constructor with explicit Stage. */
    public Librarian(dev.everydaythings.graph.runtime.stage.ItemStage stage,
                     ItemRef iid, Library library, Optional<Path> rootPath) {
        super(iid);
        this.stage = Objects.requireNonNull(stage, "stage");
        this.library = Objects.requireNonNull(library, "library");
        this.rootPath = Objects.requireNonNull(rootPath, "rootPath");
        this.parley = new Parley(this);
    }

    /**
     * Full constructor — vault holding signing material, storage, and (optional)
     * filesystem footprint. Most callers should use a factory method
     * ({@link #ephemeral()}, etc.) rather than calling this directly.
     *
     * <p>The IID is derived from the vault's initial signing public key. During
     * construction this Librarian binds itself as its own librarian and then
     * publishes its four-datum genesis (INCEPTION body+record on the signing
     * track plus the Librarian's own item-manifest body+record). When the
     * constructor returns, the Librarian is a fully-published graph identity.
     *
     * <p>Convenience overload that defaults to a Java-only {@link
     * dev.everydaythings.graph.runtime.stage.ItemStage}.
     */
    public Librarian(Vault vault, Library library, Optional<Path> rootPath) {
        this(dev.everydaythings.graph.runtime.stage.ItemStage.javaOnly(),
                vault, library, rootPath);
    }

    /** Full constructor with explicit Stage. */
    public Librarian(dev.everydaythings.graph.runtime.stage.ItemStage stage,
                     Vault vault, Library library, Optional<Path> rootPath) {
        super(vault);
        this.stage = Objects.requireNonNull(stage, "stage");
        this.library = Objects.requireNonNull(library, "library");
        this.rootPath = Objects.requireNonNull(rootPath, "rootPath");
        this.parley = new Parley(this);
        bindLibrarian(this);
        selfIncept();
    }

    /**
     * Anonymous constructor — no identity, no vault, no inception. The Librarian
     * exists purely as a routing / fetch / cache context with no cryptographic
     * standing. Attempts to commit, sign, or self-incept will fail.
     */
    private Librarian(Library library) {
        super((ItemRef) null);
        this.stage = dev.everydaythings.graph.runtime.stage.ItemStage.javaOnly();
        this.library = Objects.requireNonNull(library, "library");
        this.rootPath = Optional.empty();
        this.parley = new Parley(this);
        bindLibrarian(this);
    }

    // ==================================================================================
    // Encoder convenience — delegates to the Library's encoder
    //
    // Library owns the encoder. These methods exist for callers that hold a
    // Librarian reference and want a one-liner. They throw if the underlying
    // Library has no encoder (i.e., pure-in-memory backends — once those land).
    // ==================================================================================

    /** The encoding this Librarian uses, if any. Delegates to {@link Library#encoder}. */
    public Optional<dev.everydaythings.graph.encoding.Encoding> encoder() {
        return library.encoder();
    }

    private dev.everydaythings.graph.encoding.Encoding requireEncoder() {
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
     * Create an ephemeral signed Librarian — full identity and signing capability,
     * but everything lives in memory.
     *
     * <p>Storage is backed by SkipList stores (zero-dependency, pure Java). No
     * filesystem footprint. Identity is a freshly-generated random ItemRef; signing
     * is wired with a fresh in-memory vault holding two Ed25519 keypairs
     * (current + pre-rotation next). Inception runs during construction.
     *
     * <p>For tests that need signing but not persistence; for one-shot tools that
     * sign transient frames and discard them.
     */
    public static Librarian ephemeral() {
        return ephemeral(dev.everydaythings.graph.runtime.stage.ItemStage.javaOnly());
    }

    /** Ephemeral Librarian on the given Stage. */
    public static Librarian ephemeral(dev.everydaythings.graph.runtime.stage.ItemStage stage) {
        // Still byte-backed: bootstrap + token-indexed parse pipelines rely on
        // the byte-store token dictionary. Once token indexing is wired into
        // PureMapLibrary (blocked on task #48 — Posting.source flip from
        // ContentRef to DatumRef), ephemeral will switch to Library.anonymous().
        return new Librarian(
                stage,
                InMemoryVault.generate(Signer.DEFAULT_ALGORITHM),
                Library.inMemory(),
                Optional.empty());
    }

    // (anonymous() factory below)

    /**
     * Backwards-compatible alias for {@link #ephemeral()}.
     *
     * @deprecated use {@link #ephemeral()} for signed in-memory mode, or
     *             {@link #anonymous()} for the no-identity / no-signing variant.
     */
    @Deprecated
    public static Librarian inMemory() {
        return ephemeral();
    }

    /**
     * Create an anonymous Librarian — no identity, no vault, no inception. The
     * cheapest possible runtime context: storage is in-memory only, nothing
     * gets signed, nothing requires identity.
     *
     * <p>For tests that don't need identity at all, or for one-shot tools that
     * only need to fetch / look up / route without ever attesting anything.
     * Attempting to sign, commit, or self-incept on an anonymous Librarian
     * throws {@link IllegalStateException}.
     */
    public static Librarian anonymous() {
        return new Librarian(Library.anonymous());
    }

    /**
     * Create a fresh persistent Librarian at the given filesystem path.
     *
     * <p>Full production startup: generates a fresh vault, opens a byte-backed
     * Library at the path (writing the {@code .librarian/format} marker on
     * first use), and publishes the four-datum genesis (INCEPTION body+record
     * on the signing track + the Librarian's own item manifest body+record).
     *
     * <p>Persistence is currently partial — the filesystem footprint is the
     * format marker only. RocksDB-backed data stores are pending in cleanup;
     * until they land, the data itself stays in-memory and is lost when the
     * Librarian closes. The factory shape and marker contract, however, are
     * the API the system commits to.
     *
     * @throws IllegalStateException if a marker already exists for a
     *                               different encoder
     */
    public static Librarian fresh(java.nio.file.Path path) {
        return fresh(dev.everydaythings.graph.runtime.stage.ItemStage.javaOnly(), path);
    }

    /** Fresh persistent Librarian at the given path, on the given Stage. */
    public static Librarian fresh(dev.everydaythings.graph.runtime.stage.ItemStage stage,
                                  java.nio.file.Path path) {
        Objects.requireNonNull(path, "path");
        return new Librarian(
                stage,
                InMemoryVault.generate(Signer.DEFAULT_ALGORITHM),
                Library.atPath(path),
                Optional.of(path));
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
        Objects.requireNonNull(path, "path");
        // Validate the marker file exists and matches the runtime encoder.
        // The Library would do this anyway when atPath() is called, but
        // surfacing the check here keeps the failure mode obvious.
        dev.everydaythings.graph.library.FormatMarker.verify(path, dev.everydaythings.graph.encoding.CgCbor.codec());
        throw new UnsupportedOperationException(
                "Librarian.load(path) requires vault disk-persistence, which is "
                        + "designed but not yet implemented. The format marker at "
                        + path + " is valid, but the vault holding the original "
                        + "signing identity cannot yet be re-loaded. See the "
                        + "Stage 4 follow-on in the librarian-startup-flow design memo.");
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
     * Direct CLI entry. Parses {@link LibrarianOptions} from {@code args},
     * brings up an ItemStage (probing GraalVM, degrading gracefully if absent),
     * constructs a Librarian on it, starts the Parley listener in daemon/foreground
     * mode, and blocks until interrupted.
     */
    public static void main(String[] args) {
        LibrarianOptions opts = new LibrarianOptions();
        new picocli.CommandLine(opts).parseArgs(args);

        ItemStage stage = new ItemStage();
        logger.info("ItemStage up. Polyglot: {}",
                stage.polyglotAvailable()
                        ? "GraalVM " + stage.polyglotLanguages()
                        : "Java-only");

        Librarian lib = opts.path != null
                ? Librarian.fresh(stage, opts.effectivePath())
                : Librarian.ephemeral(stage);

        if (opts.daemon || opts.foreground) {
            // TODO: hook this up once Parley.listen(port) is wired.
            // lib.parley().listen(opts.port);
        }

        logger.info("Librarian running. IID: {}", lib.iid().encodeText());
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
        // Self-inception now happens during construction (see
        // {@link #Librarian(Vault, Library, Optional)}); bootstrap is only
        // responsible for seed vocabulary discovery.
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
    public List<Frame> lookup(String token, Integer limit) {
        Objects.requireNonNull(token, "token");
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
        FrameBuilder fb = Frame.compose(LibrarianVocabulary.Lookup.IID)
                .with(ThematicRole.Value.IID, p.token());
        if (p.target() != null) {
            fb.theme(p.target());
        }
        if (p.predicate() != null) {
            fb.with(ThematicRole.Topic.IID, p.predicate());
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

        ItemRef archetype = readReferencedIid(createFrame, ThematicRole.Theme.IID);
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
        Frame constructFrame = Frame.compose(RuntimeVocabulary.Construct.IID)
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
                .binding(CompoundKey.of(ThematicRole.Instrument.IID));
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
        ItemRef targetIid = readReferencedIid(deleteFrame, ThematicRole.Theme.IID);
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
        for (MultiKey key : currentKeys(identityIid, IdentityVocabulary.Signing.IID)) {
            if (Signer.verify(key, message, varsig)) return true;
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
                && RuntimeVocabulary.Code.IID.equals(ref.iid())) {
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
        // Also run @Handler dispatch so callers that use the old assembleFrame
        // path still get full actor-model behavior. submit() does the same on
        // its newer entry path.
        ItemRef predicateIid = ((ItemRef) body.head()).iid();
        dispatchToHandlers(frame, predicateIid);
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
        ItemRef predicateIid = ((ItemRef) frame.body().head()).iid();
        boolean ephemeral = isEphemeral(predicateIid);

        if (!ephemeral) {
            persist(frame.body());
            for (Record r : frame.records()) {
                persist(r);
            }
        }

        notifyReferencedItems(frame);
        List<Frame> responses = dispatchToHandlers(frame, predicateIid);

        return SubmitResult.of(frame, responses);
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
        return predicate.endorsedFramesByPredicate(
                        CoreVocabulary.Config.IID)
                .anyMatch(this::isRetentionEphemeral);
    }

    private boolean isRetentionEphemeral(Frame configFrame) {
        return configFrame.body()
                .binding(CompoundKey.of(
                        ThematicRole.Value.IID,
                        SchemaVocabulary.Retention.IID))
                .map(b -> b.target() instanceof ItemRef ir
                        && !ir.isPinned()
                        && SchemaVocabulary.Ephemeral.IID.equals(ir))
                .orElse(false);
    }

    /**
     * Data-driven dispatch: fetch the {@link #CODE_IID CodeItem} for this
     * Librarian implementation, walk its endorsed HANDLES frames, find one
     * whose {@code THEME} matches the incoming predicate, read its {@code INSTRUMENT}
     * text literal as the Java method name to invoke, reflect on the method,
     * and call it.
     *
     * <p>The {@code @Handler} Java annotation is the seed-time hint that
     * generates the HANDLES frames + CodeItem manifest during {@link #bootstrap};
     * at runtime, the endorsed frames are the source of truth. A different
     * implementation (e.g., a Clojure Librarian) would publish its own
     * CodeItem with its own method names, and dispatch there would resolve
     * differently — but the predicate vocabulary is the shared protocol.
     */
    private List<Frame> dispatchToHandlers(Frame frame, ItemRef predicateIid) {
        Item codeItem = fetchItem(CODE_IID).orElse(null);
        if (codeItem == null) return List.of();

        Iterator<Frame> it = codeItem
                .endorsedFramesByPredicate(CoreVocabulary.Handles.IID)
                .iterator();
        while (it.hasNext()) {
            Frame handlesFrame = it.next();
            if (!themeMatches(handlesFrame, predicateIid)) continue;

            String methodName = readInstrumentText(handlesFrame);
            if (methodName == null) continue;

            java.lang.reflect.Method m = findHandlerMethod(methodName);
            if (m == null) continue;

            try {
                Object[] args = extractHandlerArgs(m, frame);
                Object result = m.invoke(this, args);
                if (result == null) return List.of();
                if (result instanceof List<?> list) {
                    List<Frame> out = new ArrayList<>(list.size());
                    for (Object o : list) {
                        if (o instanceof Frame f) out.add(f);
                    }
                    return out;
                }
                if (result instanceof Frame f) return List.of(f);
                return List.of();
            } catch (ReflectiveOperationException e) {
                throw new RuntimeException(
                        "Handler invocation failed for predicate " + predicateIid, e);
            }
        }
        return List.of();
    }

    /** Whether a HANDLES frame's THEME binding targets the given predicate. */
    private static boolean themeMatches(Frame handlesFrame, ItemRef predicateIid) {
        return handlesFrame.body()
                .binding(CompoundKey.of(ThematicRole.Theme.IID))
                .map(b -> extractReferencedIidFromTarget(b.target()))
                .filter(predicateIid::equals)
                .isPresent();
    }

    /** Read the INSTRUMENT binding's text literal from a HANDLES frame. */
    private static String readInstrumentText(Frame handlesFrame) {
        return handlesFrame.body()
                .binding(CompoundKey.of(ThematicRole.Instrument.IID))
                .map(Binding::target)
                .filter(t -> t instanceof String)
                .map(String.class::cast)
                .orElse(null);
    }

    /**
     * Find a {@link Seed.Handler}-annotated method on {@code Librarian.class} by
     * name. Walking the declared methods costs O(N) where N is small; for now
     * this is fine and avoids caching state.
     */
    private static java.lang.reflect.Method findHandlerMethod(String name) {
        for (java.lang.reflect.Method m : Librarian.class.getDeclaredMethods()) {
            if (m.isAnnotationPresent(Seed.Handler.class) && m.getName().equals(name)) {
                return m;
            }
        }
        return null;
    }

    /**
     * Phase 1 parameter extraction for handlers. Maps frame bindings to method
     * parameters by simple type-and-role conventions:
     * <ul>
     *   <li>{@code String} ← THEME binding's text literal</li>
     *   <li>{@code Integer} ← ATTRIBUTE[LIMIT] binding's integer literal (null if absent)</li>
     * </ul>
     * A more general scheme (role→position from HANDLES metadata) will land
     * later.
     */
    private static Object[] extractHandlerArgs(java.lang.reflect.Method m, Frame frame) {
        Class<?>[] paramTypes = m.getParameterTypes();
        Object[] args = new Object[paramTypes.length];
        for (int i = 0; i < paramTypes.length; i++) {
            Class<?> pt = paramTypes[i];
            if (pt == String.class) {
                args[i] = readThemeText(frame);
            } else if (pt == Integer.class) {
                args[i] = readLimitInteger(frame);
            } else if (pt == Frame.class) {
                args[i] = frame;
            } else {
                throw new IllegalStateException(
                        "Unsupported handler param type for now: " + pt.getName());
            }
        }
        return args;
    }

    private static String readThemeText(Frame frame) {
        return frame.body()
                .binding(CompoundKey.of(
                        ThematicRole.Theme.IID))
                .map(Binding::target)
                .filter(t -> t instanceof String)
                .map(String.class::cast)
                .orElse(null);
    }

    private static Integer readLimitInteger(Frame frame) {
        return frame.body()
                .binding(CompoundKey.of(
                        ThematicRole.Attribute.IID,
                        SchemaVocabulary.Limit.IID))
                .map(Binding::target)
                .filter(t -> t instanceof Long)
                .map(t -> ((Long) t).intValue())
                .orElse(null);
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
                // TODO: surface this through a proper logger when one is wired.
                System.err.println("onFrameAssembled threw on item "
                        + item.iid() + ": " + e);
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
