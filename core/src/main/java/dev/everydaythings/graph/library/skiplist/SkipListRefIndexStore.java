package dev.everydaythings.graph.library.skiplist;

import dev.everydaythings.graph.library.index.RefIndexByteStore;
import dev.everydaythings.graph.library.index.RefIndexStore;
import lombok.Getter;

/**
 * In-memory {@link RefIndexByteStore} backed by SkipList byte stores.
 *
 * <p>Zero dependencies; pure Java. Use {@link #create()} to obtain an instance.
 */
public final class SkipListRefIndexStore
        implements RefIndexByteStore, SkipListStore<RefIndexStore.Column> {

    @Getter
    private final SkipListStore.Opened<RefIndexStore.Column> opened;

    public static SkipListRefIndexStore create() {
        return new SkipListRefIndexStore();
    }

    private SkipListRefIndexStore() {
        this.opened = SkipListStore.create(RefIndexStore.Column.class);
    }

    @Override
    public void close() {
        opened.close();
    }
}
