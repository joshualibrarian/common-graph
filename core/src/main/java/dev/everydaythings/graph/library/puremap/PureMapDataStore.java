package dev.everydaythings.graph.library.puremap;

import dev.everydaythings.graph.datum.Datum;
import dev.everydaythings.graph.encoding.Encoding;
import dev.everydaythings.graph.ref.ContentRef;
import dev.everydaythings.graph.ref.DatumRef;
import dev.everydaythings.graph.library.data.DataStore;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Pure-in-memory {@link DataStore} — holds live {@link Datum} objects in a
 * {@code Map<DatumRef, Datum>}. No encoder, no bytes, no ContentIDs.
 *
 * <p>The content-blob API ({@link #putContent}, {@link #getContent}, etc.) is
 * supported via a separate in-memory map; pure-map mode CAN hold blobs, just
 * never serializes anything.
 */
public final class PureMapDataStore implements DataStore {

    private final Map<DatumRef, Datum> datums = new ConcurrentHashMap<>();
    private final Map<ContentRef, byte[]> blobs = new ConcurrentHashMap<>();

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
    public DatumRef put(Datum datum) {
        Objects.requireNonNull(datum, "datum");
        datums.put(datum.datumId(), datum);
        return datum.datumId();
    }

    @Override
    public Optional<Datum> get(DatumRef datumId) {
        Objects.requireNonNull(datumId, "datumId");
        return Optional.ofNullable(datums.get(datumId));
    }

    @Override
    public boolean has(DatumRef datumId) {
        Objects.requireNonNull(datumId, "datumId");
        return datums.containsKey(datumId);
    }

    @Override
    public boolean delete(DatumRef datumId) {
        Objects.requireNonNull(datumId, "datumId");
        return datums.remove(datumId) != null;
    }

    // ==================================================================================
    // Content-blob API
    // ==================================================================================

    @Override
    public ContentRef putContent(byte[] bytes) {
        Objects.requireNonNull(bytes, "bytes");
        ContentRef cid = ContentRef.of(bytes);
        blobs.put(cid, bytes.clone());
        return cid;
    }

    @Override
    public Optional<byte[]> getContent(ContentRef cid) {
        Objects.requireNonNull(cid, "cid");
        byte[] bytes = blobs.get(cid);
        return Optional.ofNullable(bytes == null ? null : bytes.clone());
    }

    @Override
    public boolean hasContent(ContentRef cid) {
        Objects.requireNonNull(cid, "cid");
        return blobs.containsKey(cid);
    }

    @Override
    public boolean deleteContent(ContentRef cid) {
        Objects.requireNonNull(cid, "cid");
        return blobs.remove(cid) != null;
    }

    // ==================================================================================
    // Lifecycle
    // ==================================================================================

    /** Read-only view of the held datums — used by sibling PureMap index stores. */
    Map<DatumRef, Datum> datumsView() {
        return datums;
    }

    @Override
    public void close() {
        datums.clear();
        blobs.clear();
    }
}
