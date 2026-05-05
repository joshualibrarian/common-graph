package dev.everydaythings.graph.library;

import dev.everydaythings.graph.frame.Datum;
import dev.everydaythings.graph.item.id.ContentID;
import lombok.Getter;

import java.util.Objects;
import java.util.Optional;

/**
 * The new Library — composes a {@link DataStore} (primary truth) with an
 * {@link IndexStore} (derived, rebuildable). Owned by a {@code Librarian} as the
 * local-storage backing for a Common Graph node.
 *
 * <p>Library's job is to:
 * <ul>
 *   <li>Persist Datums (and content blobs) to the DataStore's OBJECTS column.</li>
 *   <li>Walk each newly-stored Datum's bindings and write index entries to the
 *       IndexStore's three indexes (forward bindings, reverse bindings, type).</li>
 *   <li>Expose query methods backed by those indexes.</li>
 *   <li>Allow indexes to be dropped and rebuilt by walking OBJECTS.</li>
 * </ul>
 *
 * <p>Minimal first cut: just persist + retrieve, with the indexing logic and
 * query methods to be added piece by piece. The Librarian uses Library through
 * a clean facade — callers (Items) never reach past the Librarian to Library
 * directly.
 *
 * <p>For the storage architecture, see
 * <a href="../../../../../../../../../docs/storage.md">storage.md</a>.
 */
@Getter
public class Library {

    private final DataStore dataStore;
    private final IndexStore indexStore;

    public Library(DataStore dataStore, IndexStore indexStore) {
        this.dataStore = Objects.requireNonNull(dataStore, "dataStore");
        this.indexStore = Objects.requireNonNull(indexStore, "indexStore");
    }

    // ==================================================================================
    // Object persistence
    // ==================================================================================

    /**
     * Persist a Datum's encoded bytes into OBJECTS, returning the CID.
     *
     * <p>Indexing has not yet been implemented; this method only writes to OBJECTS.
     * Index entries will be added in a subsequent migration step.
     */
    public ContentID put(Datum datum) {
        Objects.requireNonNull(datum, "datum");
        ContentID cid = datum.cid();
        byte[] bytes = datum.encodeBinary(dev.everydaythings.graph.Canonical.Scope.BODY);
        dataStore.db(DataStore.Column.OBJECTS).key(cid).put(bytes);
        return cid;
    }

    /**
     * Persist arbitrary content bytes (a content blob, not a Datum) into OBJECTS,
     * returning the CID computed from the bytes.
     */
    public ContentID putContent(byte[] bytes) {
        Objects.requireNonNull(bytes, "bytes");
        ContentID cid = ContentID.of(bytes);
        dataStore.db(DataStore.Column.OBJECTS).key(cid).put(bytes);
        return cid;
    }

    /**
     * Retrieve raw bytes from OBJECTS by CID.
     *
     * @return the encoded bytes, or empty if not found locally
     */
    public Optional<byte[]> get(ContentID cid) {
        Objects.requireNonNull(cid, "cid");
        byte[] bytes = dataStore.db(DataStore.Column.OBJECTS).key(cid).get();
        return Optional.ofNullable(bytes);
    }

    /**
     * Whether the local OBJECTS store has bytes for the given CID.
     */
    public boolean has(ContentID cid) {
        Objects.requireNonNull(cid, "cid");
        return dataStore.db(DataStore.Column.OBJECTS).key(cid).exists();
    }
}
