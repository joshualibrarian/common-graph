package dev.everydaythings.graph.item;

import dev.everydaythings.graph.datum.Binding;
import dev.everydaythings.graph.datum.Body;
import dev.everydaythings.graph.datum.BodyBuilder;
import dev.everydaythings.graph.datum.Record;
import dev.everydaythings.graph.id.ItemID;
import dev.everydaythings.graph.id.ItemRef;

import java.util.List;
import java.util.Objects;

/**
 * Fluent builder for a {@link Manifest} — body's head is an archetype IID,
 * and body must carry an {@code ITEM_ID} binding (auto-injected here).
 *
 * <p>Returned by {@link Manifest#compose(ItemID, ItemID)}.
 */
public final class ManifestBuilder extends BodyBuilder<ManifestBuilder, Manifest> {

    private final ItemID archetype;

    public ManifestBuilder(ItemID archetype, ItemID itemId) {
        this.archetype = Objects.requireNonNull(archetype, "archetype");
        Objects.requireNonNull(itemId, "itemId");
        // Auto-inject the ITEM_ID binding so callers don't have to spell it.
        bindings.add(Binding.ref(Manifest.ITEM_ID, itemId));
    }

    @Override
    protected Body buildBody() {
        return Body.of(ItemRef.of(archetype), bindings);
    }

    @Override
    protected Manifest finishBuild(Body body, List<Record> records) {
        return records.isEmpty() ? Manifest.of(body) : Manifest.of(body, records);
    }
}
