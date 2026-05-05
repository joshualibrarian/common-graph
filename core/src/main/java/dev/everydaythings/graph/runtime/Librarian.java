package dev.everydaythings.graph.runtime;

import dev.everydaythings.graph.frame.Body;
import dev.everydaythings.graph.frame.Datum;
import dev.everydaythings.graph.frame.Frame;
import dev.everydaythings.graph.item.Manifest;
import dev.everydaythings.graph.item.id.ContentID;
import dev.everydaythings.graph.item.id.ItemID;
import dev.everydaythings.graph.item.user.Signer;
import dev.everydaythings.graph.library.Library;
import dev.everydaythings.graph.library.skiplist.SkipListDataStore;
import dev.everydaythings.graph.library.skiplist.SkipListIndexStore;
import com.upokecenter.cbor.CBORObject;
import lombok.Getter;

import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;

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
     * Primary constructor. Most callers should use one of the factory methods
     * ({@link #inMemory()}, etc.).
     */
    public Librarian(ItemID iid, Library library, Optional<Path> rootPath) {
        super(iid);
        this.library = Objects.requireNonNull(library, "library");
        this.rootPath = Objects.requireNonNull(rootPath, "rootPath");
    }

    // ==================================================================================
    // Factory methods
    // ==================================================================================

    /**
     * Create an in-memory Librarian for tests, demos, or ephemeral runs.
     *
     * <p>Storage is backed by SkipList stores (zero-dependency, pure Java).
     * No filesystem footprint. Identity is a freshly-generated random ItemID.
     * Signing capability not yet wired (will be added in a later migration step).
     */
    public static Librarian inMemory() {
        Library library = new Library(SkipListDataStore.create(), SkipListIndexStore.create());
        return new Librarian(ItemID.random(), library, Optional.empty());
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
     * something other than a 2-element body array.
     *
     * <p>TODO: records associated with this body are not yet loaded. Once the
     * RECORDS_BY_BODY head-index is wired, this method will look up record CIDs
     * by the body's CID and decode each into the returned Frame's records list.
     * Today every returned Frame has empty records.
     */
    public Optional<Frame> fetchFrame(ContentID cid) {
        return fetchBody(cid).map(Frame::of);
    }

    /**
     * Fetch and decode a Manifest (archetypal body + records aggregate) by its body CID.
     *
     * <p>Returns empty if the body bytes aren't found locally, decode as a Record
     * rather than a Body, or decode as a non-archetypal body (no ITEM_ID binding).
     *
     * <p>TODO: records — same caveat as {@link #fetchFrame}. Empty records list
     * until the RECORDS_BY_BODY head-index lands.
     */
    public Optional<Manifest> fetchManifest(ContentID cid) {
        return fetchBody(cid).flatMap(body -> {
            try {
                return Optional.of(Manifest.of(body));
            } catch (IllegalArgumentException e) {
                return Optional.empty();
            }
        });
    }

    /** Whether the local Library has bytes for the given CID. */
    public boolean has(ContentID cid) {
        return library.has(cid);
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
}
