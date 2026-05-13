package dev.everydaythings.graph.library.puremap;

import dev.everydaythings.graph.datum.BindingTarget;
import dev.everydaythings.graph.datum.Body;
import dev.everydaythings.graph.datum.Datum;
import dev.everydaythings.graph.datum.Record;
import dev.everydaythings.graph.item.Manifest;
import dev.everydaythings.graph.item.id.CompoundKey;
import dev.everydaythings.graph.item.id.DatumID;
import dev.everydaythings.graph.item.id.ItemID;
import dev.everydaythings.graph.item.id.ItemRef;
import dev.everydaythings.graph.library.index.RefIndexStore;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Pure-in-memory {@link RefIndexStore} — answers queries by scanning a live
 * {@link Datum} map. {@link #index} maintains the local map (a parallel view
 * of the data that's been told about); {@link #unindex} drops entries.
 *
 * <p>Practical for in-memory tests and ephemeral runs. O(n) scans are fine at
 * test scale.
 */
public final class PureMapRefIndexStore implements RefIndexStore {

    private final Map<DatumID, Datum> datums = new ConcurrentHashMap<>();

    PureMapRefIndexStore() {}

    public static PureMapRefIndexStore create() {
        return new PureMapRefIndexStore();
    }

    // ==================================================================================
    // Write API
    // ==================================================================================

    @Override
    public void index(Datum datum, DatumID id) {
        Objects.requireNonNull(datum, "datum");
        Objects.requireNonNull(id, "id");
        datums.put(id, datum);
    }

    @Override
    public void unindex(Datum datum, DatumID id) {
        Objects.requireNonNull(id, "id");
        datums.remove(id);
    }

    // ==================================================================================
    // Query API
    // ==================================================================================

    @Override
    public List<DatumID> recordsForBody(DatumID bodyId) {
        Objects.requireNonNull(bodyId, "bodyId");
        return datums.values().stream()
                .filter(Record.class::isInstance)
                .map(Record.class::cast)
                .filter(r -> bodyId.equals(r.headRef().bodyId()))
                .map(Datum::datumId)
                .toList();
    }

    @Override
    public List<DatumID> manifestsForItem(ItemID itemIid) {
        return bodiesByReferenceBinding(Manifest.ITEM_ID, itemIid);
    }

    @Override
    public List<DatumID> manifestsForType(ItemID typeIid) {
        Objects.requireNonNull(typeIid, "typeIid");
        return datums.values().stream()
                .filter(Body.class::isInstance)
                .map(Body.class::cast)
                .filter(b -> b.head() instanceof ItemRef ref && typeIid.equals(ref.iid()))
                .filter(b -> b.binding(CompoundKey.of(Manifest.ITEM_ID)).isPresent())
                .map(Datum::datumId)
                .toList();
    }

    @Override
    public List<DatumID> bodiesByReferenceBinding(ItemID role, ItemID target) {
        Objects.requireNonNull(role, "role");
        Objects.requireNonNull(target, "target");
        CompoundKey key = CompoundKey.of(role);
        return datums.values().stream()
                .filter(Body.class::isInstance)
                .map(Body.class::cast)
                .filter(b -> b.binding(key)
                        .map(binding -> binding.target() instanceof BindingTarget.RefTarget r
                                && !r.isCompound()
                                && target.equals(r.asItemId()))
                        .orElse(false))
                .map(Datum::datumId)
                .toList();
    }

    // ==================================================================================
    // Lifecycle
    // ==================================================================================

    @Override
    public void close() {
        datums.clear();
    }
}
