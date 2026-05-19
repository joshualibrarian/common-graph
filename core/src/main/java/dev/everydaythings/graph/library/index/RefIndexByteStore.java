package dev.everydaythings.graph.library.index;

import dev.everydaythings.graph.datum.Binding;
import dev.everydaythings.graph.datum.BindingTarget;
import dev.everydaythings.graph.datum.Body;
import dev.everydaythings.graph.datum.Datum;
import dev.everydaythings.graph.datum.Record;
import dev.everydaythings.graph.encoding.Encoding;
import dev.everydaythings.graph.item.Manifest;
import dev.everydaythings.graph.id.CompoundKey;
import dev.everydaythings.graph.id.CompoundKey.Qualifier;
import dev.everydaythings.graph.id.ContentRef;
import dev.everydaythings.graph.id.DatumRef;
import dev.everydaythings.graph.id.HashID;
import dev.everydaythings.graph.id.ItemRef;
import dev.everydaythings.graph.library.bytestore.ByteStore;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Byte-backed {@link RefIndexStore} — composes the RefIndexStore domain
 * interface with a {@link ByteStore} keyed on {@link RefIndexStore.Column}.
 * Default methods implement Datum-aware indexing in terms of byte primitives.
 *
 * <p>Concrete impls (SkipList / MapDB / RocksDB) only need to provide the
 * ByteStore backing.
 */
public interface RefIndexByteStore extends RefIndexStore, ByteStore<RefIndexStore.Column> {

    byte[] EMPTY_VALUE = new byte[0];

    /**
     * The encoder used to produce byte-stable key fragments (binding
     * qualifiers, etc.).  All replicas of an index must agree on the encoder
     * for keys to interoperate.
     */
    Encoding rawEncoder();

    // ==================================================================================
    // Write API
    // ==================================================================================

    @Override
    default void index(Datum datum, DatumRef id) {
        Objects.requireNonNull(datum, "datum");
        Objects.requireNonNull(id, "id");
        if (datum instanceof Record record) {
            DatumRef bodyId = record.headRef().bodyId();
            byte[] key = concat(bodyId.encodeBinary(), id.encodeBinary());
            db(RefIndexStore.Column.RECORDS_BY_BODY).key(key).put(EMPTY_VALUE);
        } else if (datum instanceof Body body) {
            body.binding(CompoundKey.of(Manifest.ITEM_ID)).ifPresent(itemIdBinding -> {
                byte[] key = composeTypeIndexKey(body, itemIdBinding, id);
                db(RefIndexStore.Column.TYPE_INDEX).key(key).put(EMPTY_VALUE);
            });
        }
        indexBindings(datum, id);
    }

    @Override
    default void unindex(Datum datum, DatumRef id) {
        Objects.requireNonNull(datum, "datum");
        Objects.requireNonNull(id, "id");
        if (datum instanceof Record record) {
            DatumRef bodyId = record.headRef().bodyId();
            byte[] key = concat(bodyId.encodeBinary(), id.encodeBinary());
            db(RefIndexStore.Column.RECORDS_BY_BODY).key(key).delete();
        } else if (datum instanceof Body body) {
            body.binding(CompoundKey.of(Manifest.ITEM_ID)).ifPresent(itemIdBinding -> {
                byte[] key = composeTypeIndexKey(body, itemIdBinding, id);
                db(RefIndexStore.Column.TYPE_INDEX).key(key).delete();
            });
        }
        unindexBindings(datum, id);
    }

    private void indexBindings(Datum datum, HashID id) {
        Encoding enc = rawEncoder();
        for (Binding b : datum.bindings()) {
            composeForwardKey(b, id, enc).ifPresent(key ->
                    db(RefIndexStore.Column.FORWARD_BINDINGS).key(key).put(EMPTY_VALUE));
        }
    }

    private void unindexBindings(Datum datum, HashID id) {
        Encoding enc = rawEncoder();
        for (Binding b : datum.bindings()) {
            composeForwardKey(b, id, enc).ifPresent(key ->
                    db(RefIndexStore.Column.FORWARD_BINDINGS).key(key).delete());
        }
    }

    // ==================================================================================
    // Query API
    // ==================================================================================

    @Override
    default List<DatumRef> recordsForBody(DatumRef bodyId) {
        Objects.requireNonNull(bodyId, "bodyId");
        byte[] prefix = bodyId.encodeBinary();
        List<DatumRef> recordIds = new ArrayList<>();
        forEach(RefIndexStore.Column.RECORDS_BY_BODY, prefix, (key, value) -> {
            byte[] suffix = Arrays.copyOfRange(key, prefix.length, key.length);
            recordIds.add(new DatumRef(suffix));
        });
        return List.copyOf(recordIds);
    }

    @Override
    default List<DatumRef> manifestsForItem(ItemRef itemIid) {
        return bodiesByReferenceBinding(Manifest.ITEM_ID, itemIid);
    }

    @Override
    default List<DatumRef> manifestsForType(ItemRef typeIid) {
        Objects.requireNonNull(typeIid, "typeIid");
        byte[] prefix = typeIid.encodeBinary();
        List<DatumRef> bodyIds = new ArrayList<>();
        forEach(RefIndexStore.Column.TYPE_INDEX, prefix, (key, value) -> {
            // Layout: head-IID | vid-slot | item-IID | body-CID
            int off = prefix.length;
            int vidLen = key[off] & 0xFF;
            off += 1 + vidLen;
            HashID.Slice itemIidSlice = HashID.splitLeadingMultihashFromByteArray(key, off);
            off = itemIidSlice.next();
            byte[] bodyIdBytes = Arrays.copyOfRange(key, off, key.length);
            bodyIds.add(new DatumRef(bodyIdBytes));
        });
        return List.copyOf(bodyIds);
    }

    @Override
    default List<DatumRef> bodiesByReferenceBinding(ItemRef role, ItemRef target) {
        Objects.requireNonNull(role, "role");
        Objects.requireNonNull(target, "target");
        byte[] roleBytes = role.encodeBinary();
        byte[] targetBytes = target.encodeBinary();
        byte[] prefix = concat(concat(roleBytes, new byte[]{0}), targetBytes);
        List<DatumRef> bodyIds = new ArrayList<>();
        forEach(RefIndexStore.Column.FORWARD_BINDINGS, prefix, (key, value) -> {
            byte[] bodyIdBytes = Arrays.copyOfRange(key, prefix.length, key.length);
            bodyIds.add(new DatumRef(bodyIdBytes));
        });
        return List.copyOf(bodyIds);
    }

    // ==================================================================================
    // Key encoding helpers
    // ==================================================================================

    private static Optional<byte[]> composeForwardKey(Binding binding, HashID datumId, Encoding enc) {
        Optional<byte[]> targetBytes = extractReferenceMultihash(binding.target());
        if (targetBytes.isEmpty()) return Optional.empty();
        int qualCount = binding.qualifiers().size();
        if (qualCount > 0xFF) {
            throw new IllegalStateException(
                    "Binding has more than 255 qualifiers; key layout assumes 1-byte count");
        }
        byte[] roleBytes = binding.role().encodeBinary();
        byte[] qualBytes = encodeQualifiers(binding.qualifiers(), enc);
        byte[] cidBytes = datumId.encodeBinary();
        return Optional.of(concatAll(roleBytes, new byte[]{(byte) qualCount}, qualBytes,
                targetBytes.get(), cidBytes));
    }

    private static Optional<byte[]> extractReferenceMultihash(Object target) {
        if (target instanceof ItemRef ir && !ir.isPinned()) {
            return Optional.of(ir.encodeBinary());
        }
        return Optional.empty();
    }

    private static byte[] encodeQualifiers(List<Qualifier> qualifiers, Encoding enc) {
        if (qualifiers.isEmpty()) return new byte[0];
        List<byte[]> parts = new ArrayList<>(qualifiers.size());
        int total = 0;
        for (Qualifier q : qualifiers) {
            byte[] qBytes = enc.encode(q);
            parts.add(qBytes);
            total += qBytes.length;
        }
        byte[] result = new byte[total];
        int offset = 0;
        for (byte[] p : parts) {
            System.arraycopy(p, 0, result, offset, p.length);
            offset += p.length;
        }
        return result;
    }

    private static byte[] composeTypeIndexKey(Body body, Binding itemIdBinding, HashID bodyId) {
        ItemRef head = body.headRef();
        byte[] headIid = head.iid().encodeBinary();
        byte[] vidSlot = encodeVidSlot(head.version());
        byte[] itemIid = extractItemId(itemIdBinding.target()).encodeBinary();
        byte[] bodyIdBytes = bodyId.encodeBinary();
        return concatAll(headIid, vidSlot, itemIid, bodyIdBytes);
    }

    private static byte[] encodeVidSlot(Optional<ContentRef> vid) {
        if (vid.isEmpty()) return new byte[]{0};
        byte[] vidBytes = vid.get().encodeBinary();
        if (vidBytes.length > 0xFF) {
            throw new IllegalStateException("VID exceeds 255 bytes; layout assumes 1-byte length prefix");
        }
        byte[] result = new byte[1 + vidBytes.length];
        result[0] = (byte) vidBytes.length;
        System.arraycopy(vidBytes, 0, result, 1, vidBytes.length);
        return result;
    }

    private static ItemRef extractItemId(Object target) {
        if (target instanceof ItemRef ir) return ir;
        throw new IllegalStateException(
                "ITEM_ID target must be an ItemRef, got " + target.getClass().getSimpleName());
    }

    private static byte[] concat(byte[] a, byte[] b) {
        byte[] out = new byte[a.length + b.length];
        System.arraycopy(a, 0, out, 0, a.length);
        System.arraycopy(b, 0, out, a.length, b.length);
        return out;
    }

    private static byte[] concatAll(byte[]... parts) {
        int total = 0;
        for (byte[] p : parts) total += p.length;
        byte[] out = new byte[total];
        int offset = 0;
        for (byte[] p : parts) {
            System.arraycopy(p, 0, out, offset, p.length);
            offset += p.length;
        }
        return out;
    }
}
