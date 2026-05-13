package dev.everydaythings.graph.library.data;

import com.upokecenter.cbor.CBORObject;
import com.upokecenter.cbor.CBORType;
import dev.everydaythings.graph.encoding.Encoding;
import dev.everydaythings.graph.datum.Body;
import dev.everydaythings.graph.datum.Datum;
import dev.everydaythings.graph.datum.Record;
import dev.everydaythings.graph.item.id.ContentID;
import dev.everydaythings.graph.item.id.DatumID;
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
 * column for the actual bytes (keyed by ContentID) and the
 * {@link DataStore.Column#DATUM_INDEX} column as the DatumID → ContentID bridge
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
    default DatumID put(Datum datum) {
        java.util.Objects.requireNonNull(datum, "datum");
        byte[] bytes = datum.encodeBinary(dev.everydaythings.graph.encoding.Canonical.Scope.BODY);
        ContentID cid = ContentID.of(bytes);
        db(DataStore.Column.OBJECTS).key(cid).put(bytes);
        // Bridge: DatumID → ContentID. Key = DatumID-bytes | ContentID-bytes.
        byte[] bridgeKey = concat(datum.datumId().encodeBinary(), cid.encodeBinary());
        db(DataStore.Column.DATUM_INDEX).key(bridgeKey).put(EMPTY_VALUE);
        return datum.datumId();
    }

    @Override
    default Optional<Datum> get(DatumID datumId) {
        java.util.Objects.requireNonNull(datumId, "datumId");
        for (ContentID cid : contentIdsForDatum(datumId)) {
            Optional<byte[]> bytes = getContent(cid);
            if (bytes.isEmpty()) continue;
            Datum d = decodeDatum(bytes.get());
            if (d == null) continue;
            d.bindSource(cid);
            return Optional.of(d);
        }
        return Optional.empty();
    }

    @Override
    default boolean has(DatumID datumId) {
        return !contentIdsForDatum(datumId).isEmpty();
    }

    @Override
    default boolean delete(DatumID datumId) {
        java.util.Objects.requireNonNull(datumId, "datumId");
        boolean any = false;
        for (ContentID cid : contentIdsForDatum(datumId)) {
            byte[] bridgeKey = concat(datumId.encodeBinary(), cid.encodeBinary());
            db(DataStore.Column.DATUM_INDEX).key(bridgeKey).delete();
            db(DataStore.Column.OBJECTS).key(cid).delete();
            any = true;
        }
        return any;
    }

    /**
     * The ContentIDs of all locally-held wire-form realizations for the given
     * DatumID. Most Datums have exactly one realization; multi-realization
     * arises only when the same Datum is held in multiple wire forms.
     */
    default java.util.List<ContentID> contentIdsForDatum(DatumID datumId) {
        java.util.Objects.requireNonNull(datumId, "datumId");
        byte[] prefix = datumId.encodeBinary();
        java.util.List<ContentID> realizations = new java.util.ArrayList<>();
        forEach(DataStore.Column.DATUM_INDEX, prefix, (key, value) -> {
            byte[] suffix = Arrays.copyOfRange(key, prefix.length, key.length);
            realizations.add(new ContentID(suffix));
        });
        return java.util.List.copyOf(realizations);
    }

    // ==================================================================================
    // Content-blob API — direct byte access
    // ==================================================================================

    @Override
    default ContentID putContent(byte[] bytes) {
        java.util.Objects.requireNonNull(bytes, "bytes");
        ContentID cid = ContentID.of(bytes);
        db(DataStore.Column.OBJECTS).key(cid).put(bytes);
        return cid;
    }

    @Override
    default Optional<byte[]> getContent(ContentID cid) {
        java.util.Objects.requireNonNull(cid, "cid");
        return Optional.ofNullable(db(DataStore.Column.OBJECTS).key(cid).get());
    }

    @Override
    default boolean hasContent(ContentID cid) {
        java.util.Objects.requireNonNull(cid, "cid");
        return db(DataStore.Column.OBJECTS).key(cid).exists();
    }

    @Override
    default boolean deleteContent(ContentID cid) {
        java.util.Objects.requireNonNull(cid, "cid");
        if (!hasContent(cid)) return false;
        db(DataStore.Column.OBJECTS).key(cid).delete();
        return true;
    }

    // ==================================================================================
    // Decoding
    // ==================================================================================

    /**
     * Decode CBOR-encoded bytes as a Datum. Returns null if the bytes don't
     * parse as a 2-element Body or 3-element Record array.
     */
    static Datum decodeDatum(byte[] bytes) {
        try {
            CBORObject node = CBORObject.DecodeFromBytes(bytes);
            if (node.getType() != CBORType.Array) return null;
            int size = node.size();
            if (size == 2) return Body.fromCborTree(node);
            if (size == 3) return Record.fromCborTree(node);
            return null;
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static byte[] concat(byte[] a, byte[] b) {
        byte[] out = new byte[a.length + b.length];
        System.arraycopy(a, 0, out, 0, a.length);
        System.arraycopy(b, 0, out, a.length, b.length);
        return out;
    }
}
