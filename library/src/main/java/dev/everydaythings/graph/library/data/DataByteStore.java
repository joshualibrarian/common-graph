package dev.everydaythings.graph.library.data;


import dev.everydaythings.graph.encoding.Encoding;
import dev.everydaythings.graph.datum.Datum;
import dev.everydaythings.graph.ref.ContentRef;
import dev.everydaythings.graph.library.bytestore.ByteStore;

import java.util.Optional;

/**
 * Byte-backed {@link DataStore} — composes the DataStore content-blob
 * contract with a {@link ByteStore} keyed on {@link DataStore.Column}.
 *
 * <p>Concrete impls ({@code SkipListDataStore}, {@code MapDbDataStore},
 * {@code RocksDataStore}) only need to provide:
 * <ul>
 *   <li>An {@link Encoding} (via {@link #rawEncoder()}).</li>
 *   <li>The ByteStore backing (inherited from {@code ByteStore<DataStore.Column>}).</li>
 * </ul>
 *
 * <p>Internally, byte-backed DataStores use the {@link DataStore.Column#OBJECTS}
 * column for the bytes (keyed by ContentRef).  The DatumRef → ContentRef
 * mapping lives in the {@link dev.everydaythings.graph.library.index.RefIndexStore RefIndexStore}
 * (as the {@code CONTENT_BY_DATUM} column / map), composed by Library.
 */
public interface DataByteStore extends DataStore, ByteStore<DataStore.Column> {

    /** Empty value byte array for index entries (key encodes the entire lookup). */
    byte[] EMPTY_VALUE = new byte[0];

    /**
     * The raw encoder this byte-backed DataStore uses.  Library reads it via
     * {@link #encoder()} for encode/decode at the Library boundary.
     */
    Encoding rawEncoder();

    @Override
    default Optional<Encoding> encoder() {
        return Optional.of(rawEncoder());
    }

    /**
     * Decode stored bytes through the store's encoder and return the value
     * iff it's a {@link Datum}.  Returns null on either an unparseable blob
     * or a payload that decodes to a non-Datum (e.g., a stray primitive).
     *
     * <p>Convenience for callers (typically Library) that hold bytes from
     * {@link #getContent(ContentRef)} and want the Datum form.
     */
    default Datum decodeDatum(byte[] bytes) {
        try {
            Object decoded = rawEncoder().decode(bytes);
            return decoded instanceof Datum d ? d : null;
        } catch (RuntimeException e) {
            return null;
        }
    }

    // ==================================================================================
    // Content-blob API — direct byte access
    // ==================================================================================

    @Override
    default ContentRef putContent(byte[] bytes) {
        java.util.Objects.requireNonNull(bytes, "bytes");
        ContentRef cid = ContentRef.of(bytes);
        db(DataStore.Column.OBJECTS).key(cid).put(bytes);
        return cid;
    }

    @Override
    default Optional<byte[]> getContent(ContentRef cid) {
        java.util.Objects.requireNonNull(cid, "cid");
        return Optional.ofNullable(db(DataStore.Column.OBJECTS).key(cid).get());
    }

    @Override
    default boolean hasContent(ContentRef cid) {
        java.util.Objects.requireNonNull(cid, "cid");
        return db(DataStore.Column.OBJECTS).key(cid).exists();
    }

    @Override
    default boolean deleteContent(ContentRef cid) {
        java.util.Objects.requireNonNull(cid, "cid");
        if (!hasContent(cid)) return false;
        db(DataStore.Column.OBJECTS).key(cid).delete();
        return true;
    }
}
