package dev.everydaythings.graph.runtime;

import dev.everydaythings.graph.Canonical;
import dev.everydaythings.graph.crypt.VarSig;
import dev.everydaythings.graph.crypt.vault.InMemoryVault;
import dev.everydaythings.graph.crypt.vault.Vault;
import dev.everydaythings.graph.frame.Binding;
import dev.everydaythings.graph.frame.BindingTarget;
import dev.everydaythings.graph.frame.Body;
import dev.everydaythings.graph.frame.Datum;
import dev.everydaythings.graph.frame.Frame;
import dev.everydaythings.graph.frame.Record;
import dev.everydaythings.graph.item.Item;
import dev.everydaythings.graph.item.Literal;
import dev.everydaythings.graph.item.Manifest;
import dev.everydaythings.graph.item.id.ContentID;
import dev.everydaythings.graph.item.id.FrameRef;
import dev.everydaythings.graph.item.id.ItemID;
import dev.everydaythings.graph.item.id.ItemRef;
import dev.everydaythings.graph.item.user.Signer;
import dev.everydaythings.graph.library.Library;
import dev.everydaythings.graph.library.skiplist.SkipListDataStore;
import dev.everydaythings.graph.library.skiplist.SkipListIndexStore;
import com.upokecenter.cbor.CBORObject;
import lombok.Getter;

import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
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
public class Librarian extends Signer {

    /** Canonical key for Librarian-the-archetype. */
    public static final String KEY = "cg.archetype:librarian";

    /** The archetype IID for Librarian instances. */
    public static final ItemID ARCHETYPE = ItemID.fromString(KEY);

    @Override
    public ItemID archetype() {
        return ARCHETYPE;
    }

    /** The local storage manager (always present). */
    @Getter
    private final Library library;

    /**
     * Filesystem footprint, if any. Empty for in-memory librarians; present for
     * persistent and viewer modes.
     */
    @Getter
    private final Optional<Path> rootPath;

    /**
     * Item cache: one canonical instance per IID. {@link #fetchItem} consults this
     * before hydrating from storage; {@link #register} adds explicit entries.
     * {@link Item#commit} auto-registers, so a committed item is always findable.
     */
    private final Map<ItemID, Item> itemCache = new ConcurrentHashMap<>();

    /**
     * Identity-only constructor (no signing capability). Primarily for hydration
     * paths where the local node doesn't hold this Librarian's private key.
     */
    public Librarian(ItemID iid, Library library, Optional<Path> rootPath) {
        super(iid);
        this.library = Objects.requireNonNull(library, "library");
        this.rootPath = Objects.requireNonNull(rootPath, "rootPath");
    }

    /**
     * Full constructor — identity, vault holding signing material, storage, and
     * (optional) filesystem footprint. Most callers should use a factory method
     * ({@link #inMemory()}, etc.) rather than calling this directly.
     */
    public Librarian(ItemID iid, Vault vault, Library library, Optional<Path> rootPath) {
        super(iid, vault);
        this.library = Objects.requireNonNull(library, "library");
        this.rootPath = Objects.requireNonNull(rootPath, "rootPath");
    }

    // ==================================================================================
    // Factory methods
    // ==================================================================================

    /**
     * Create an in-memory Librarian for tests, demos, or ephemeral runs.
     *
     * <p>Storage is backed by SkipList stores (zero-dependency, pure Java). No
     * filesystem footprint. Identity is a freshly-generated random ItemID; signing
     * is wired with a fresh in-memory vault holding two Ed25519 keypairs
     * (current + pre-rotation next).
     */
    public static Librarian inMemory() {
        Library library = new Library(SkipListDataStore.create(), SkipListIndexStore.create());
        Vault vault = InMemoryVault.generate(Signer.DEFAULT_ALGORITHM);
        // Derive IID from the initial signing public key — cryptographically
        // binds identity to key (closes the IID-preemption gap).
        ItemID iid = ItemID.fromMultikeyBytes(
                vault.signingPublicKey().orElseThrow().encoded());
        Librarian lib = new Librarian(iid, vault, library, Optional.empty());
        // Self-register so frames referencing the librarian's own IID route correctly.
        lib.register(lib);
        return lib;
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
        dev.everydaythings.graph.item.SeedProcessor.bootstrap(this);
        // Publish self-INCEPTION via the inherited Signer.publishSelfInception() —
        // requires this Librarian to be its own librarian binding (set up by
        // bindLibrarian below) so the assembleFrame call has a target.
        if (this.librarian == null) bindLibrarian(this);
        publishSelfInception();
    }

    // ==================================================================================
    // Storage delegation
    // ==================================================================================

    /**
     * Fetch raw bytes from the local Library by CID.
     *
     * <p>Returns the bytes if the Library has them locally. Networking and
     * external-source resolution will be added later; this minimal version
     * is local-only.
     */
    public Optional<byte[]> fetch(ContentID cid) {
        return library.get(cid);
    }

    /**
     * Persist a Datum (Body or Record) to the local Library, returning its CID.
     *
     * <p>Indexing has not yet been wired; this currently writes only to OBJECTS.
     */
    public ContentID persist(Datum datum) {
        return library.put(datum);
    }

    /**
     * Persist arbitrary content bytes to the local Library, returning the CID
     * computed from the bytes.
     */
    public ContentID persistContent(byte[] bytes) {
        return library.putContent(bytes);
    }

    /**
     * Fetch and decode a Frame (body + records aggregate) by its body CID.
     *
     * <p>Returns empty if the body bytes aren't found locally OR if they decode as
     * something other than a 2-element body array. The Frame's records list is
     * populated from the RECORDS_BY_BODY index — any records persisted against
     * this body via {@link #persist} are included; missing record bytes are
     * silently dropped.
     */
    public Optional<Frame> fetchFrame(ContentID cid) {
        return fetchBody(cid).map(body -> Frame.of(body, loadRecords(cid)));
    }

    /**
     * Fetch and decode a Manifest (archetypal body + records aggregate) by its body CID.
     *
     * <p>Returns empty if the body bytes aren't found locally, decode as a Record
     * rather than a Body, or decode as a non-archetypal body (no ITEM_ID binding).
     * Records are loaded the same way as {@link #fetchFrame}.
     */
    public Optional<Manifest> fetchManifest(ContentID cid) {
        return fetchBody(cid).flatMap(body -> {
            try {
                return Optional.of(Manifest.of(body, loadRecords(cid)));
            } catch (IllegalArgumentException e) {
                return Optional.empty();
            }
        });
    }

    /** Whether the local Library has bytes for the given CID. */
    public boolean has(ContentID cid) {
        return library.has(cid);
    }

    /**
     * Return the currently-committed signing keys for the given identity, derived
     * from its KEL — i.e., the {@code INSTRUMENT} bindings on the most recent valid
     * INCEPTION (or ROTATION, when that's wired) frame published with
     * {@code THEME = iid} and {@code PURPOSE = Signing}.
     *
     * <p>Phase 1 simplification: only INCEPTION frames are considered (no rotation
     * chain replay yet). When multiple INCEPTIONs exist for one identity (which
     * shouldn't happen normally — the bootstrap-twice case), this picks the latest
     * by {@code TIME} binding. Returns empty list if no valid INCEPTION is found.
     *
     * <p>Used by verification: a signature on a frame from {@code @some-identity}
     * is honored only if it verifies against one of these committed keys.
     *
     * <p>Future: caches the result keyed by (identity, purpose); invalidates on new
     * INCEPTION/ROTATION assembly. ROTATION chain replay walks {@code FOLLOWS} back
     * to INCEPTION, validating preimage match at each step.
     */
    public List<dev.everydaythings.graph.crypt.MultiKey> signingKeysForIdentity(ItemID identityIid) {
        Objects.requireNonNull(identityIid, "identityIid");
        List<ContentID> candidateCids = library
                .bodyCidsForReferenceBinding(dev.everydaythings.graph.semantics.ThematicRole.Theme.IID, identityIid);

        Frame chosen = null;
        java.time.Instant chosenTime = java.time.Instant.MIN;
        for (ContentID cid : candidateCids) {
            Frame frame = fetchFrame(cid).orElse(null);
            if (frame == null) continue;
            if (!isInceptionForSigning(frame, identityIid)) continue;
            java.time.Instant frameTime = readTime(frame.body()).orElse(java.time.Instant.MIN);
            if (frameTime.isAfter(chosenTime)) {
                chosen = frame;
                chosenTime = frameTime;
            }
        }
        if (chosen == null) return List.of();
        return dev.everydaythings.graph.identity.Inception.currentKeys(chosen.body());
    }

    private static boolean isInceptionForSigning(Frame frame, ItemID identityIid) {
        if (!(frame.body().head() instanceof ItemRef ref)) return false;
        if (!dev.everydaythings.graph.identity.Inception.IID.equals(ref.iid())) return false;
        return dev.everydaythings.graph.identity.Inception.readTheme(frame.body())
                       .filter(identityIid::equals).isPresent()
                && dev.everydaythings.graph.identity.Inception.readPurpose(frame.body())
                       .filter(dev.everydaythings.graph.identity.IdentityVocabulary.Signing.IID::equals)
                       .isPresent();
    }

    /**
     * Verify that the given signature is valid for the given message under one of
     * the signing keys committed by the given identity's KEL.
     *
     * <p>This is the identity-aware counterpart to {@link Signer#verify(dev.everydaythings.graph.crypt.MultiKey, byte[], VarSig)}:
     * instead of taking an explicit MultiKey, takes an IID and consults the
     * locally-known INCEPTION/ROTATION frames for that identity.
     *
     * @return true if the signature verifies against any currently-committed
     *         signing key for {@code identityIid}; false otherwise
     */
    public boolean verifySignedAsIdentity(ItemID identityIid, byte[] message, VarSig varsig) {
        for (dev.everydaythings.graph.crypt.MultiKey key : signingKeysForIdentity(identityIid)) {
            if (Signer.verify(key, message, varsig)) return true;
        }
        return false;
    }

    private static Optional<java.time.Instant> readTime(Body body) {
        return body.binding(dev.everydaythings.graph.item.id.CompoundKey.of(
                        dev.everydaythings.graph.semantics.ThematicRole.Time.IID))
                .flatMap(b -> {
                    if (!(b.target() instanceof Literal lit)) return Optional.empty();
                    if (!Literal.TYPE_INSTANT.equals(lit.valueType())) return Optional.empty();
                    try {
                        return Optional.of(lit.asInstantMillis());
                    } catch (RuntimeException ignored) {
                        return Optional.empty();
                    }
                });
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
    public Optional<Item> fetchItem(ItemID iid) {
        Objects.requireNonNull(iid, "iid");
        Item cached = itemCache.get(iid);
        if (cached != null) return Optional.of(cached);

        List<ContentID> manifestCids = library.manifestCidsForItem(iid);
        if (manifestCids.isEmpty()) return Optional.empty();
        // TODO: when HEAD logic exists, pick the right manifest. For now, take the first.
        ContentID chosen = manifestCids.getFirst();
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
     * <p>"Unresolvable" includes: target isn't a Java-class Literal, class isn't
     * on the classpath, class doesn't extend Item, no public {@code (ItemID, Librarian)}
     * constructor, or the constructor throws.
     */
    private Item hydrateItem(ItemID iid, Manifest manifest) {
        Optional<Binding> impl = manifest.implementation();
        if (impl.isEmpty()) {
            return new Item(iid, this);
        }
        Class<? extends Item> clazz = resolveImplementationClass(impl.get(), iid);
        try {
            return clazz.getConstructor(ItemID.class, Librarian.class)
                    .newInstance(iid, this);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(
                    "Failed to hydrate " + iid + " as " + clazz.getName()
                            + " (requires a public (ItemID, Librarian) constructor)", e);
        }
    }

    private static Class<? extends Item> resolveImplementationClass(Binding binding, ItemID iid) {
        if (!(binding.target() instanceof Literal lit)) {
            throw new IllegalStateException(
                    "Manifest for " + iid + " has IMPLEMENTATION binding whose target is "
                            + binding.target().getClass().getSimpleName()
                            + "; expected a Java-class Literal");
        }
        if (!Literal.TYPE_JAVA_CLASS.equals(lit.valueType())) {
            throw new IllegalStateException(
                    "Manifest for " + iid + " has IMPLEMENTATION literal of type "
                            + lit.valueType() + "; expected " + Literal.TYPE_JAVA_CLASS);
        }
        Class<?> clazz;
        try {
            clazz = lit.asJavaClass();
        } catch (RuntimeException e) {
            throw new IllegalStateException(
                    "Manifest for " + iid + " declares IMPLEMENTATION class "
                            + lit.asJavaClassName() + " which is not on the classpath", e);
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
        itemCache.put(item.iid(), item);
    }

    /**
     * Remove an item from the cache. Used by {@link dev.everydaythings.graph.semantics.Delete}
     * after honoring a delete request — the cache must drop the now-deleted item
     * so future {@code fetchItem} calls return empty (storage is also gone).
     */
    public void evict(ItemID iid) {
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

        ContentID bodyCid = persist(body);
        VarSig signature = signer.sign(body.encodeBinary(Canonical.Scope.BODY));
        Record record = Record.of(FrameRef.of(bodyCid), List.of(), signature);
        persist(record);

        Frame frame = Frame.of(body, List.of(record));
        notifyReferencedItems(frame);
        return frame;
    }

    private void notifyReferencedItems(Frame frame) {
        Set<ItemID> notified = new HashSet<>();

        // Notify the predicate (head of the body). Predicates are sememes — items
        // that can react to their own invocation (CREATE-the-sememe handles the
        // CREATE frame's intent, etc.).
        ItemID headIid = ((ItemRef) frame.body().head()).iid();
        notifyOne(headIid, frame, notified);

        // Notify each item referenced as a target in body bindings.
        for (Binding b : frame.body().bindings()) {
            extractReferencedIid(b.target()).ifPresent(iid -> notifyOne(iid, frame, notified));
        }
    }

    private void notifyOne(ItemID iid, Frame frame, Set<ItemID> notified) {
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

    private static Optional<ItemID> extractReferencedIid(BindingTarget target) {
        if (target instanceof BindingTarget.IidTarget iid) {
            return Optional.of(iid.iid());
        }
        if (target instanceof BindingTarget.RefTarget ref && !ref.isCompound()) {
            return Optional.of(ref.asItemId());
        }
        return Optional.empty();
    }

    private Optional<Body> fetchBody(ContentID cid) {
        return fetch(cid).flatMap(bytes -> {
            try {
                CBORObject node = CBORObject.DecodeFromBytes(bytes);
                return Optional.of(Body.fromCborTree(node));
            } catch (RuntimeException e) {
                return Optional.empty();
            }
        });
    }

    private Optional<Record> fetchRecord(ContentID cid) {
        return fetch(cid).flatMap(bytes -> {
            try {
                CBORObject node = CBORObject.DecodeFromBytes(bytes);
                return Optional.of(Record.fromCborTree(node));
            } catch (RuntimeException e) {
                return Optional.empty();
            }
        });
    }

    private List<Record> loadRecords(ContentID bodyCid) {
        return library.recordCidsForBody(bodyCid).stream()
                .map(this::fetchRecord)
                .flatMap(Optional::stream)
                .toList();
    }
}
