package dev.everydaythings.graph.library.puremap;

import dev.everydaythings.graph.datum.Datum;
import dev.everydaythings.graph.encoding.Encoding;
import dev.everydaythings.graph.item.id.ContentID;
import dev.everydaythings.graph.item.id.DatumID;
import dev.everydaythings.graph.library.data.DataStore;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Pure-in-memory {@link DataStore} — holds live {@link Datum} objects in a
 * {@code Map<DatumID, Datum>}. No encoder, no bytes, no ContentIDs.
 *
 * <p>The content-blob API ({@link #putContent}, {@link #getContent}, etc.) is
 * supported via a separate in-memory map; pure-map mode CAN hold blobs, just
 * never serializes anything.
 */
public final class PureMapDataStore implements DataStore {

    private final Map<DatumID, Datum> datums = new ConcurrentHashMap<>();
    private final Map<ContentID, byte[]> blobs = new ConcurrentHashMap<>();

    PureMapDataStore() {}

    public static PureMapDataStore create() {
        return new PureMapDataStore();
    }

    /** Pure-map DataStore has no encoder. */
    @Override
    public Optional<Encoding> encoder() {
        return Optional.empty();
    }

    // ==================================================================================
    // Datum API
    // ==================================================================================

    @Override
    public DatumID put(Datum datum) {
        Objects.requireNonNull(datum, "datum");
        datums.put(datum.datumId(), datum);
        return datum.datumId();
    }

    @Override
    public Optional<Datum> get(DatumID datumId) {
        Objects.requireNonNull(datumId, "datumId");
        return Optional.ofNullable(datums.get(datumId));
    }

    @Override
    public boolean has(DatumID datumId) {
        Objects.requireNonNull(datumId, "datumId");
        return datums.containsKey(datumId);
    }

    @Override
    public boolean delete(DatumID datumId) {
        Objects.requireNonNull(datumId, "datumId");
        return datums.remove(datumId) != null;
    }

    // ==================================================================================
    // Content-blob API
    // ==================================================================================

    @Override
    public ContentID putContent(byte[] bytes) {
        Objects.requireNonNull(bytes, "bytes");
        ContentID cid = ContentID.of(bytes);
        blobs.put(cid, bytes.clone());
        return cid;
    }

    @Override
    public Optional<byte[]> getContent(ContentID cid) {
        Objects.requireNonNull(cid, "cid");
        byte[] bytes = blobs.get(cid);
        return Optional.ofNullable(bytes == null ? null : bytes.clone());
    }

    @Override
    public boolean hasContent(ContentID cid) {
        Objects.requireNonNull(cid, "cid");
        return blobs.containsKey(cid);
    }

    @Override
    public boolean deleteContent(ContentID cid) {
        Objects.requireNonNull(cid, "cid");
        return blobs.remove(cid) != null;
    }

    // ==================================================================================
    // Lifecycle
    // ==================================================================================

    /** Read-only view of the held datums — used by sibling PureMap index stores. */
    Map<DatumID, Datum> datumsView() {
        return datums;
    }

    @Override
    public void close() {
        datums.clear();
        blobs.clear();
    }
}
