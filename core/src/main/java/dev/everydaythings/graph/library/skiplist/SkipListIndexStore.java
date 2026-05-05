package dev.everydaythings.graph.library.skiplist;

import dev.everydaythings.graph.library.IndexStore;
import lombok.Getter;

/**
 * In-memory {@link IndexStore} for tests and ephemeral runs.
 *
 * <p>Zero dependencies; pure Java. Use {@link #create()} to obtain an instance.
 */
public class SkipListIndexStore implements IndexStore, SkipListStore<IndexStore.Column> {

    @Getter
    private final SkipListStore.Opened<Column> opened;

    public static SkipListIndexStore create() {
        return new SkipListIndexStore();
    }

    private SkipListIndexStore() {
        this.opened = SkipListStore.create(Column.class);
    }

    @Override
    public void close() {
        opened.close();
    }
}
