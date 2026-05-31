package dev.everydaythings.graph.library.puremap;

import dev.everydaythings.graph.encoding.CgCbor;
import dev.everydaythings.graph.encoding.Encoding;
import dev.everydaythings.graph.ref.ContentRef;
import dev.everydaythings.graph.library.data.DataStore;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Pure-in-memory {@link DataStore} — content-blob storage in a
 * {@code Map<ContentRef, byte[]>}.  Uses an encoder for the cases when callers
 * (e.g. {@code Library}) need to encode/decode Datums; defaults to
 * {@link CgCbor}.
 */
public final class PureMapDataStore implements DataStore {

    private final Map<ContentRef, byte[]> blobs = new ConcurrentHashMap<>();
    private final Encoding encoding;

    private PureMapDataStore(Encoding encoding) {
        this.encoding = Objects.requireNonNull(encoding, "encoding");
    }

    public static PureMapDataStore create() {
        return new PureMapDataStore(CgCbor.codec());
    }

    public static PureMapDataStore create(Encoding encoding) {
        return new PureMapDataStore(encoding);
    }

    @Override
    public Optional<Encoding> encoder() {
        return Optional.of(encoding);
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

    @Override
    public void close() {
        blobs.clear();
    }
}
