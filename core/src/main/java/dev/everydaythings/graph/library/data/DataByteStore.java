package dev.everydaythings.graph.library.data;


import dev.everydaythings.graph.encoding.Encoding;
import dev.everydaythings.graph.datum.Datum;
import dev.everydaythings.graph.id.ContentRef;
import dev.everydaythings.graph.id.DatumRef;
import dev.everydaythings.graph.library.bytestore.ByteStore;

import java.util.Arrays;
import java.util.Optional;

/**
 * Byte-backed {@link DataStore} — composes the DataStore domain interface
 * with a {@link ByteStore} keyed on {@link DataStore.Column}. Default methods
 * bridge between the Datum-shaped contract and the byte-KV primitives.
 *
 * <p>Concrete impls (e.g. {@code SkipListDataStore}, {@code MapDbDataStore},
 * {@code RocksDbDataStore}) only need to provide:
 * <ul>
 *   <li>An {@link Encoding} (via {@link #encoder()}).</li>
 *   <li>The ByteStore backing (inherited from {@code ByteStore<DataStore.Column>}).</li>
 * </ul>
 *
 * <p>Internally, byte-backed DataStores use the {@link DataStore.Column#OBJECTS}
 * column for the actual bytes (keyed by ContentRef) and the
 * {@link DataStore.Column#DATUM_INDEX} column as the DatumRef → ContentRef bridge
 * for semantic-identity lookups.
 */
public interface DataByteStore extends DataStore, ByteStore<DataStore.Column> {

    /** Empty value byte array for index entries (key encodes the entire lookup). */
    byte[] EMPTY_VALUE = new byte[0];

    /**
     * The raw encoder this byte-backed DataStore uses. Concrete impls provide it.
     * Wrapped to satisfy {@link DataStore#encoder()} via {@link #encoder()} below.
     */
    Encoding rawEncoder();

    @Override
    default Optional<Encoding> encoder() {
        return Optional.of(rawEncoder());
    }

    // ==================================================================================
    // Datum API — default impls over ByteStore primitives + internal index
    // ==================================================================================

    @Override
    default DatumRef put(Datum datum) {
        java.util.Objects.requireNonNull(datum, "datum");
        // Encode via the store's own encoder, not a hardcoded codec.
        byte[] bytes = rawEncoder().encode(datum);
        ContentRef cid = ContentRef.of(bytes);
        db(DataStore.Column.OBJECTS).key(cid).put(bytes);
        // Bridge: DatumRef → ContentRef. Key = DatumRef-bytes | ContentRef-bytes.
        byte[] bridgeKey = concat(datum.datumId().encodeBinary(), cid.encodeBinary());
        db(DataStore.Column.DATUM_INDEX).key(bridgeKey).put(EMPTY_VALUE);
        return datum.datumId();
    }

    @Override
    default Optional<Datum> get(DatumRef datumId) {
        java.util.Objects.requireNonNull(datumId, "datumId");
        for (ContentRef cid : contentIdsForDatum(datumId)) {
            Optional<byte[]> bytes = getContent(cid);
            if (bytes.isEmpty()) continue;
            Datum d = decodeDatum(bytes.get());
            if (d == null) continue;
            d.bindSource(cid);
            return Optional.of(d);
        }
        return Optional.empty();
    }

    /**
     * Decode stored bytes through the store's encoder and return the value
     * iff it's a {@link Datum}.  Returns null on either an unparseable blob
     * or a payload that decodes to a non-Datum (e.g., a stray primitive).
     */
    default Datum decodeDatum(byte[] bytes) {
        try {
            Object decoded = rawEncoder().decode(bytes);
            return decoded instanceof Datum d ? d : null;
        } catch (RuntimeException e) {
            return null;
        }
    }

    @Override
    default boolean has(DatumRef datumId) {
        return !contentIdsForDatum(datumId).isEmpty();
    }

    @Override
    default boolean delete(DatumRef datumId) {
        java.util.Objects.requireNonNull(datumId, "datumId");
        boolean any = false;
        for (ContentRef cid : contentIdsForDatum(datumId)) {
            byte[] bridgeKey = concat(datumId.encodeBinary(), cid.encodeBinary());
            db(DataStore.Column.DATUM_INDEX).key(bridgeKey).delete();
            db(DataStore.Column.OBJECTS).key(cid).delete();
            any = true;
        }
        return any;
    }

    /**
     * The ContentIDs of all locally-held wire-form realizations for the given
     * DatumRef. Most Datums have exactly one realization; multi-realization
     * arises only when the same Datum is held in multiple wire forms.
     */
    default java.util.List<ContentRef> contentIdsForDatum(DatumRef datumId) {
        java.util.Objects.requireNonNull(datumId, "datumId");
        byte[] prefix = datumId.encodeBinary();
        java.util.List<ContentRef> realizations = new java.util.ArrayList<>();
        forEach(DataStore.Column.DATUM_INDEX, prefix, (key, value) -> {
            byte[] suffix = Arrays.copyOfRange(key, prefix.length, key.length);
            realizations.add(new ContentRef(suffix));
        });
        return java.util.List.copyOf(realizations);
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

    private static byte[] concat(byte[] a, byte[] b) {
        byte[] out = new byte[a.length + b.length];
        System.arraycopy(a, 0, out, 0, a.length);
        System.arraycopy(b, 0, out, a.length, b.length);
        return out;
    }
}
